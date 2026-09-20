package br.edu.tp3.inventory.infrastructure.ordering;

import br.edu.tp3.contracts.Exchanges;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Coloca a mensagem adiantada em ESPERA, em vez de devolvê-la para a fila.
 *
 * Por que isto existe: devolver com requeue = true parece a solução óbvia, mas
 * tem um defeito sério. A mensagem volta para a CABECA da fila e, com prefetch 1,
 * o consumidor pega ela de novo imediatamente. Enquanto ela continuar adiantada,
 * nenhuma outra mensagem daquela fila e entregue. Uma unica mensagem fora de
 * ordem congela o processamento de todos os outros pedidos.
 *
 * Isso se chama HEAD-OF-LINE BLOCKING, e foi observado na pratica durante os
 * testes deste projeto: uma mensagem de teste em requeue infinito travou a fila
 * inventory.release.queue e deixou uma saga parada em COMPENSATING.
 *
 * A solucao correta: tirar a mensagem da fila principal e guardá-la em uma fila
 * de espera com TTL. Quando o tempo expira, o proprio RabbitMQ a devolve para a
 * fila original por dead lettering. Enquanto ela espera, a fila principal segue
 * atendendo todo mundo normalmente.
 *
 *     fila principal ──(adiantada)──► tp3.retry ──► fila de espera (TTL 3s)
 *            ▲                                              │
 *            └──────────── dead letter automatico ──────────┘
 *
 * O contador de tentativas no cabecalho evita espera eterna: se a mensagem
 * anterior nunca chegar, depois de MAX_RETRIES a mensagem vai para a DLQ e
 * alguem precisa olhar.
 */
@Component
public class OutOfOrderRetryPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutOfOrderRetryPublisher.class);

    /** Quantas rodadas de espera antes de desistir. Com TTL de 3s, cerca de 30s. */
    private static final int MAX_RETRIES = 10;

    private static final String HEADER_RETRY_COUNT = "x-out-of-order-retries";

    private final RabbitTemplate rabbitTemplate;

    public OutOfOrderRetryPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * @return true se a mensagem foi colocada em espera (o listener deve dar ACK),
     *         false se o limite de tentativas estourou (o listener deve mandar para a DLQ).
     */
    public boolean scheduleRetry(Message original, String retryRoutingKey) {
        int attempt = currentRetryCount(original) + 1;

        if (attempt > MAX_RETRIES) {
            log.error("mensagem adiantada desistiu apos {} tentativas messageId={}",
                    MAX_RETRIES, original.getMessageProperties().getMessageId());
            return false;
        }

        MessageProperties source = original.getMessageProperties();
        MessageProperties props = new MessageProperties();
        props.setMessageId(source.getMessageId());
        props.setCorrelationId(source.getCorrelationId());
        props.setType(source.getType());
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setHeader(HEADER_RETRY_COUNT, attempt);

        rabbitTemplate.send(Exchanges.RETRY, retryRoutingKey,
                new Message(original.getBody(), props));

        log.warn("mensagem adiantada colocada em espera tentativa={}/{} messageId={}",
                attempt, MAX_RETRIES, source.getMessageId());
        return true;
    }

    private int currentRetryCount(Message message) {
        Object header = message.getMessageProperties().getHeader(HEADER_RETRY_COUNT);
        return header instanceof Number number ? number.intValue() : 0;
    }
}
