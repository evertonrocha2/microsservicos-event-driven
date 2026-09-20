package br.edu.tp3.order.domain.outbox;

public enum OutboxStatus {
    /** Gravada na mesma transacao do agregado, ainda nao entregue ao broker. */
    PENDING,
    /** Confirmada pelo broker (publisher confirm). */
    SENT,
    /** Estourou o numero de tentativas. Exige intervencao. */
    FAILED
}
