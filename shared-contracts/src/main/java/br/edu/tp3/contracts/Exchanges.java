package br.edu.tp3.contracts;

/**
 * Nomes dos exchanges do RabbitMQ.
 *
 * Um exchange e o componente do broker que recebe a mensagem do produtor e decide
 * para quais filas ela vai. O tipo do exchange define o estilo de interacao:
 *
 *  - DIRECT  -> roteia pela routing key exata. Usado para COMANDOS (um destinatario).
 *  - TOPIC   -> roteia por padrao com curingas. Usado para EVENTOS (publish/subscribe).
 *  - FANOUT  -> copia para todas as filas ligadas, ignorando routing key.
 */
public final class Exchanges {

    /** DIRECT. Comandos da saga enviados pelo orquestrador aos participantes. */
    public static final String COMMANDS = "tp3.commands";

    /** TOPIC. Eventos de dominio do pedido. Qualquer servico pode assinar. */
    public static final String EVENTS = "tp3.events";

    /** DIRECT. Respostas dos participantes de volta ao orquestrador da saga. */
    public static final String REPLIES = "tp3.replies";

    /**
     * DIRECT. Exchange de espera.
     *
     * Recebe mensagens que chegaram cedo demais (fora de ordem). Elas ficam
     * paradas em uma fila com TTL e, quando o tempo expira, voltam sozinhas
     * para a fila original. Ver RabbitMQConfig do inventory-service.
     */
    public static final String RETRY = "tp3.retry";

    /** DIRECT. Dead Letter Exchange: recebe mensagens que falharam definitivamente. */
    public static final String DEAD_LETTER = "tp3.dlx";

    private Exchanges() {
    }
}
