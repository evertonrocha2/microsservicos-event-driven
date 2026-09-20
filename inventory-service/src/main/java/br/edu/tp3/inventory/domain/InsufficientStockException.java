package br.edu.tp3.inventory.domain;

/**
 * Falha de NEGOCIO, nao falha tecnica.
 *
 * A diferenca importa muito no consumo de mensagens:
 *
 *  - falha de negocio  -> a mensagem foi processada corretamente e a resposta e
 *                         "nao deu". Da ACK e responde FAILURE para a saga compensar.
 *  - falha tecnica     -> banco fora do ar, bug, timeout. Nao da ACK, porque
 *                         tentar de novo mais tarde pode funcionar.
 *
 * Confundir as duas e um erro classico: manda para a DLQ algo que era so um "nao",
 * ou fica retentando para sempre algo que nunca vai dar certo.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String sku, int requested, int available) {
        super("estoque insuficiente para " + sku
                + ": pedido " + requested + ", disponivel " + available);
    }
}
