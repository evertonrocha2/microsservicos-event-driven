package br.edu.tp3.contracts;

/**
 * Routing keys usadas no roteamento das mensagens.
 *
 * Convencao adotada:
 *  - comandos: {contexto}.{acao} no imperativo   -> "payment.authorize"
 *  - eventos : {agregado}.{fato} no passado      -> "order.created"
 *
 * A diferenca nao e so de nome. Um COMANDO pede que algo aconteca e tem um dono.
 * Um EVENTO comunica que algo ja aconteceu e nao sabe quem escuta.
 */
public final class RoutingKeys {

    // ---- Comandos (exchange DIRECT tp3.commands) ----
    public static final String PAYMENT_AUTHORIZE = "payment.authorize";
    public static final String PAYMENT_REFUND = "payment.refund";
    public static final String INVENTORY_RESERVE = "inventory.reserve";
    public static final String INVENTORY_RELEASE = "inventory.release";

    // ---- Espera por ordem (exchange DIRECT tp3.retry) ----
    public static final String INVENTORY_RESERVE_RETRY = "inventory.reserve.retry";
    public static final String INVENTORY_RELEASE_RETRY = "inventory.release.retry";

    // ---- Respostas da saga (exchange DIRECT tp3.replies) ----
    public static final String SAGA_REPLY = "saga.reply";

    // ---- Eventos (exchange TOPIC tp3.events) ----
    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_CONFIRMED = "order.confirmed";
    public static final String ORDER_CANCELLED = "order.cancelled";

    /** Padrao com curinga: casa com order.created, order.confirmed, order.cancelled. */
    public static final String ORDER_ALL = "order.#";

    private RoutingKeys() {
    }
}
