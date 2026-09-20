package br.edu.tp3.order.domain.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * TRANSACTIONAL OUTBOX.
 *
 * O problema: gravar no banco e publicar no broker sao dois recursos diferentes.
 * Sem cuidado, acontece o "dual write":
 *   - commit no banco, queda antes do publish -> o mundo nunca fica sabendo;
 *   - publish, rollback no banco              -> o mundo sabe de algo que nao existe.
 *
 * A solucao: NAO publicar direto. A mensagem e gravada nesta tabela dentro da MESMA
 * transacao do agregado. Ou as duas coisas existem, ou nenhuma existe. Depois, um
 * processo separado (OutboxPoller) le as linhas PENDING e publica no broker.
 *
 * Isso garante entrega AT LEAST ONCE. O preco e a duplicata, resolvida no consumidor
 * com idempotencia (ver ProcessedMessage nos servicos consumidores).
 */
@Entity
@Table(name = "outbox", indexes = {
        @Index(name = "idx_outbox_status_created", columnList = "status,createdAt")
})
public class OutboxEntry {

    @Id
    private String id;

    /** Vira a messageId da mensagem publicada. E a chave da idempotencia no consumidor. */
    @Column(nullable = false, unique = true)
    private String messageId;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String exchange;

    @Column(nullable = false)
    private String routingKey;

    @Column(nullable = false)
    private String messageType;

    /** Payload ja serializado em JSON. */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant sentAt;

    protected OutboxEntry() {
    }

    public OutboxEntry(String messageId, String aggregateType, String aggregateId,
                       String exchange, String routingKey, String messageType, String payload) {
        this.id = UUID.randomUUID().toString();
        this.messageId = messageId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.messageType = messageType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.createdAt = Instant.now();
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = Instant.now();
    }

    public void markAttemptFailed(int maxAttempts) {
        this.attempts++;
        if (this.attempts >= maxAttempts) {
            this.status = OutboxStatus.FAILED;
        }
    }

    public String getId() {
        return id;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getExchange() {
        return exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
