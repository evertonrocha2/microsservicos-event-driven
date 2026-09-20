package br.edu.tp3.order.domain.model;

/**
 * Maquina de estados do pedido. E tambem o estado da SAGA.
 *
 * Fluxo feliz:
 *   PENDING -> INVENTORY_RESERVED -> PAYMENT_AUTHORIZED -> CONFIRMED
 *
 * Fluxo de falha (compensacao):
 *   qualquer estado -> COMPENSATING -> CANCELLED
 *
 * Persistir esse estado e o que torna a saga confiavel: se o servico cair no meio,
 * ao subir de novo ele sabe exatamente em que passo parou.
 */
public enum OrderStatus {
    PENDING,
    INVENTORY_RESERVED,
    PAYMENT_AUTHORIZED,
    CONFIRMED,
    COMPENSATING,
    CANCELLED;

    public boolean isFinal() {
        return this == CONFIRMED || this == CANCELLED;
    }
}
