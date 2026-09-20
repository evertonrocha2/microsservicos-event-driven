package br.edu.tp3.inventory.application;

import br.edu.tp3.contracts.replies.SagaReply;

/**
 * Recusa de NEGOCIO carregando junto a resposta que deve ser enviada a saga.
 *
 * Por que uma excecao e nao um simples return: porque a recusa precisa DESFAZER
 * o que ja foi gravado nesta transacao. Um pedido com tres itens pode ter
 * reservado os dois primeiros e faltar saldo no terceiro. Reservar dois de tres
 * nao e um estado valido.
 *
 * Lancar a excecao faz o Spring dar rollback em tudo: as reservas parciais, o
 * avanco da sequencia e a marca de idempotencia. O listener captura, ACKa a
 * mensagem (ela foi processada, o veredito e "nao") e publica a resposta de falha
 * que vem carregada aqui dentro. A saga entao decide compensar.
 *
 * Resumo: rollback no banco, ACK no broker, FAILURE na saga. Tres coisas
 * diferentes que precisam acontecer juntas.
 */
public class BusinessRejection extends RuntimeException {

    private final transient SagaReply reply;

    public BusinessRejection(SagaReply reply) {
        super(reply.reason());
        this.reply = reply;
    }

    public SagaReply getReply() {
        return reply;
    }
}
