package br.edu.tp3.contracts.replies;

/** Passos da saga do pedido. A resposta sempre diz a qual passo ela se refere. */
public enum SagaStep {
    RESERVE_INVENTORY,
    AUTHORIZE_PAYMENT,
    RELEASE_INVENTORY,
    REFUND_PAYMENT
}
