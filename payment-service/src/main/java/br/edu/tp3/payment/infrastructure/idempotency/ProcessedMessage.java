package br.edu.tp3.payment.infrastructure.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * TABELA DE INBOX (Idempotent Consumer).
 *
 * Por que ela existe: a entrega do broker e AT LEAST ONCE. A mesma mensagem pode
 * chegar duas vezes por varios motivos legitimos:
 *
 *   - o consumidor processou mas caiu antes do ACK;
 *   - o ACK se perdeu na rede;
 *   - o OutboxPoller do produtor republicou antes de marcar SENT;
 *   - uma politica de retry reenviou.
 *
 * Sem protecao, o cliente seria cobrado duas vezes. Cobranca duplicada nao e um
 * detalhe tecnico, e um incidente de negocio.
 *
 * A protecao: antes de executar, o consumidor tenta INSERIR a messageId aqui.
 * Se a linha ja existe, a mensagem e duplicata e o efeito colateral nao se repete.
 *
 * A chave primaria e "consumidor#messageId". Incluir o consumidor importa porque
 * o mesmo evento pode ser legitimamente processado por dois handlers diferentes
 * dentro do mesmo servico. Um nao pode bloquear o outro.
 */
@Entity
@Table(name = "processed_message")
public class ProcessedMessage {

    @Id
    @Column(length = 200)
    private String id;

    @Column(nullable = false)
    private String consumer;

    @Column(nullable = false)
    private String messageId;

    private String aggregateId;

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedMessage() {
    }

    public ProcessedMessage(String consumer, String messageId, String aggregateId) {
        this.id = keyOf(consumer, messageId);
        this.consumer = consumer;
        this.messageId = messageId;
        this.aggregateId = aggregateId;
        this.processedAt = Instant.now();
    }

    public static String keyOf(String consumer, String messageId) {
        return consumer + "#" + messageId;
    }

    public String getId() {
        return id;
    }

    public String getConsumer() {
        return consumer;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
