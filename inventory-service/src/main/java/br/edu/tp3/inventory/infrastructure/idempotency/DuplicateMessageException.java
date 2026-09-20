package br.edu.tp3.inventory.infrastructure.idempotency;

/**
 * Sinaliza que outra thread ou outra replica ganhou a corrida e ja processou
 * esta mensagem.
 *
 * Nao e um erro de verdade. O listener trata como sucesso: da ACK e segue.
 * A transacao desta thread sofre rollback, o que esta correto, porque quem
 * comitou o efeito de negocio foi a outra.
 */
public class DuplicateMessageException extends RuntimeException {

    public DuplicateMessageException(String consumer, String messageId) {
        super("mensagem duplicada em corrida consumer=" + consumer + " messageId=" + messageId);
    }
}
