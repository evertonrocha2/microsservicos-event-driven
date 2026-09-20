package br.edu.tp3.contracts;

/**
 * Nomes das filas.
 *
 * Regra de ouro: a fila pertence ao CONSUMIDOR, nunca ao produtor.
 * O produtor publica em um exchange e nao sabe quantas filas existem do outro lado.
 */
public final class Queues {

    // Filas de comando (ponto a ponto: um comando, um consumidor logico)
    public static final String PAYMENT_AUTHORIZE = "payment.authorize.queue";
    public static final String PAYMENT_REFUND = "payment.refund.queue";
    public static final String INVENTORY_RESERVE = "inventory.reserve.queue";
    public static final String INVENTORY_RELEASE = "inventory.release.queue";

    // Fila de respostas consumida pelo orquestrador da saga
    public static final String SAGA_REPLIES = "saga.replies.queue";

    // Filas de evento (publish/subscribe: dois assinantes independentes do MESMO evento)
    public static final String NOTIFICATION_ORDER_EVENTS = "notification.order-events.queue";
    public static final String ANALYTICS_ORDER_EVENTS = "analytics.order-events.queue";

    /**
     * Filas de espera por ordem.
     *
     * A mensagem adiantada e movida para ca em vez de ficar em requeue. Isso
     * libera a fila principal na hora, e evita que uma mensagem fora de ordem
     * bloqueie todas as outras atras dela (head-of-line blocking).
     */
    public static final String INVENTORY_RESERVE_RETRY = "inventory.reserve.retry.queue";
    public static final String INVENTORY_RELEASE_RETRY = "inventory.release.retry.queue";

    /** Sufixo das dead letter queues. Ex.: payment.authorize.queue.dlq */
    public static final String DLQ_SUFFIX = ".dlq";

    public static String deadLetterOf(String queue) {
        return queue + DLQ_SUFFIX;
    }

    private Queues() {
    }
}
