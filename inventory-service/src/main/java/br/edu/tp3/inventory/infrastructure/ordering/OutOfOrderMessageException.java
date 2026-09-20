package br.edu.tp3.inventory.infrastructure.ordering;

/**
 * A mensagem chegou antes da hora.
 *
 * Exemplo concreto: chegou "liberar estoque" (sequencia 2) sem que
 * "reservar estoque" (sequencia 1) tivesse sido processada.
 *
 * Processar nessa ordem devolveria ao estoque uma quantidade que nunca saiu dele,
 * e o saldo ficaria inflado. Melhor esperar.
 *
 * O listener trata esta excecao devolvendo a mensagem para a fila (requeue),
 * em vez de mandar para a DLQ. Nao e uma mensagem ruim, e uma mensagem adiantada.
 */
public class OutOfOrderMessageException extends RuntimeException {

    private final long expected;
    private final long received;

    public OutOfOrderMessageException(String aggregateId, long expected, long received) {
        super("mensagem fora de ordem para " + aggregateId
                + ": esperava sequencia " + expected + " mas chegou " + received);
        this.expected = expected;
        this.received = received;
    }

    public long getExpected() {
        return expected;
    }

    public long getReceived() {
        return received;
    }
}
