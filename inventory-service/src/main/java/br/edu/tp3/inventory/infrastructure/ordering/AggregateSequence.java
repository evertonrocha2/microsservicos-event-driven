package br.edu.tp3.inventory.infrastructure.ordering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Guarda o ultimo numero de sequencia processado POR AGREGADO.
 *
 * Uma linha por pedido. O estoque so aceita a mensagem N+1 depois de ter
 * processado a mensagem N daquele mesmo pedido.
 *
 * Isso e o que permite dizer "ordenado" sem exigir que o broker inteiro seja
 * ordenado. A ordem global nao interessa. Reservar o pedido A antes ou depois de
 * reservar o pedido B da no mesmo. O que nao pode e liberar o pedido A antes de
 * ter reservado o pedido A.
 */
@Entity
@Table(name = "aggregate_sequence")
public class AggregateSequence {

    /** Id do agregado. Aqui, o orderId. E a "raia" de ordenacao. */
    @Id
    private String aggregateId;

    @Column(nullable = false)
    private long lastSequence;

    @Column(nullable = false)
    private Instant updatedAt;

    protected AggregateSequence() {
    }

    public AggregateSequence(String aggregateId) {
        this.aggregateId = aggregateId;
        this.lastSequence = 0L;
        this.updatedAt = Instant.now();
    }

    public void advanceTo(long sequence) {
        this.lastSequence = sequence;
        this.updatedAt = Instant.now();
    }

    public long getLastSequence() {
        return lastSequence;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
