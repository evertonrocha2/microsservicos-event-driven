package br.edu.tp3.payment.infrastructure.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda de idempotencia.
 *
 * Usado assim, sempre DENTRO da transacao do handler:
 *
 *     if (!guard.registerIfNew(CONSUMER, cmd.messageId(), cmd.orderId())) {
 *         // duplicata: nao repete o efeito colateral
 *     }
 *
 * Dois niveis de protecao, e os dois sao necessarios:
 *
 *  1. A consulta existsById resolve o caso comum, que e a mesma mensagem chegando
 *     de novo depois de um tempo. Barata e suficiente na maioria das vezes.
 *
 *  2. A UNIQUE KEY do banco resolve a CORRIDA: duas replicas do payment-service
 *     recebendo copias da mesma mensagem no mesmo milissegundo. As duas passam pelo
 *     existsById (ainda nao existe linha), as duas tentam inserir, e apenas uma
 *     ganha. A outra leva DataIntegrityViolationException e desiste.
 *
 * Sem o item 2 a idempotencia seria so uma ilusao sob concorrencia. E por isso que
 * "verificar antes de gravar" nunca basta em sistema distribuido: quem garante a
 * unicidade precisa ser o banco, nao o codigo.
 *
 * Como o registro e a mudanca de negocio acontecem na MESMA transacao, se o handler
 * falhar depois, o rollback tambem apaga a marca de processado, e a mensagem pode
 * ser reprocessada com seguranca.
 */
@Component
public class IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyGuard.class);

    private final ProcessedMessageRepository repository;

    public IdempotencyGuard(ProcessedMessageRepository repository) {
        this.repository = repository;
    }

    /**
     * @return true se a mensagem e nova e deve ser processada,
     *         false se ja foi processada antes (duplicata).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean registerIfNew(String consumer, String messageId, String aggregateId) {
        String key = ProcessedMessage.keyOf(consumer, messageId);

        if (repository.existsById(key)) {
            log.warn("DUPLICATA detectada consumer={} messageId={}", consumer, messageId);
            return false;
        }

        try {
            repository.saveAndFlush(new ProcessedMessage(consumer, messageId, aggregateId));
            return true;
        } catch (DataIntegrityViolationException e) {
            // Outra thread ou outra replica inseriu primeiro.
            //
            // Nao da para simplesmente devolver false e seguir: apos uma violacao de
            // constraint o Hibernate marca a transacao como rollback-only, entao nada
            // do que viesse depois seria comitado mesmo. O honesto e abortar aqui.
            //
            // O listener trata esta excecao como sucesso e da ACK. Quem comitou o
            // efeito de negocio foi a replica que ganhou a corrida.
            log.warn("DUPLICATA em corrida consumer={} messageId={}", consumer, messageId);
            throw new DuplicateMessageException(consumer, messageId);
        }
    }
}
