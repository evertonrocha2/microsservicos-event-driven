package br.edu.tp3.inventory.infrastructure.ordering;

import br.edu.tp3.contracts.OrderedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * GARANTIA DE ORDEM no processamento concorrente.
 *
 * O problema, em uma frase: o broker entrega em ordem, mas o PROCESSAMENTO
 * concorrente desfaz essa ordem. Com tres consumidores na mesma fila, a mensagem 2
 * pode terminar antes da mensagem 1 simplesmente porque pegou uma thread mais
 * rapida ou porque a 1 sofreu um retry.
 *
 * A solucao usada aqui tem DUAS camadas, e as duas sao necessarias:
 *
 *  Camada 1, infraestrutura (ver RabbitMQConfig):
 *      a fila usa x-single-active-consumer. Mesmo com varias replicas conectadas,
 *      o RabbitMQ entrega para uma so por vez. Se ela cair, outra assume.
 *      Resolve a concorrencia, mas nao resolve retry nem reentrega.
 *
 *  Camada 2, aplicacao (esta classe):
 *      cada mensagem carrega um sequenceNumber crescente dentro do agregado.
 *      O consumidor guarda o ultimo numero processado e decide:
 *
 *         seq <= ultimo       -> ja processei, e duplicata. Descarta.
 *         seq == ultimo + 1   -> e a proxima. Processa e avanca.
 *         seq >  ultimo + 1   -> chegou adiantada. Devolve para a fila e espera.
 *
 * Por que nao confiar so na camada 1: porque o consumidor unico ainda pode fazer
 * retry de uma mensagem e, enquanto isso, a seguinte ja estar disponivel. A ordem
 * so e realmente garantida quando o proprio consumidor sabe dizer "essa ainda nao
 * e a minha vez".
 *
 * Observacao sobre escala: no Kafka o raciocinio e o mesmo, so muda o nome.
 * A chave de particao faz o papel do aggregateId, e a ordem e garantida dentro
 * da particao, nunca no topico inteiro.
 */
@Component
public class MessageSequenceGuard {

    private static final Logger log = LoggerFactory.getLogger(MessageSequenceGuard.class);

    private final AggregateSequenceRepository repository;

    public MessageSequenceGuard(AggregateSequenceRepository repository) {
        this.repository = repository;
    }

    /**
     * Valida a ordem e avanca o contador do agregado.
     *
     * @return true  se a mensagem esta na vez e deve ser processada
     *         false se ja foi processada antes (duplicata, pode dar ACK e ignorar)
     * @throws OutOfOrderMessageException se chegou adiantada (deve voltar para a fila)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean checkAndAdvance(OrderedMessage message) {
        String aggregateId = message.aggregateId();
        long incoming = message.sequenceNumber();

        AggregateSequence sequence = repository.findByAggregateId(aggregateId)
                .orElseGet(() -> repository.save(new AggregateSequence(aggregateId)));

        long last = sequence.getLastSequence();
        long expected = last + 1;

        if (incoming <= last) {
            log.warn("mensagem ANTIGA descartada aggregateId={} seq={} ultimoProcessado={}",
                    aggregateId, incoming, last);
            return false;
        }

        if (incoming > expected) {
            log.warn("mensagem ADIANTADA aggregateId={} seq={} esperada={}",
                    aggregateId, incoming, expected);
            throw new OutOfOrderMessageException(aggregateId, expected, incoming);
        }

        sequence.advanceTo(incoming);
        log.debug("ordem OK aggregateId={} seq={}", aggregateId, incoming);
        return true;
    }
}
