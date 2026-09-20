package br.edu.tp3.contracts;

import java.time.Instant;

/**
 * Contrato minimo que TODA mensagem assincrona deste sistema precisa cumprir.
 *
 * Os tres campos existem por motivos praticos:
 *
 *  - messageId     : identidade unica da mensagem. E a chave da IDEMPOTENCIA.
 *                    Se a mesma messageId chegar duas vezes, o consumidor descarta a segunda.
 *  - correlationId : amarra todas as mensagens de um mesmo fluxo de negocio (a saga inteira).
 *                    E o que permite rastrear a transacao distribuida nos logs.
 *  - occurredAt    : quando o fato aconteceu na origem, nao quando a mensagem foi lida.
 */
public interface AsyncMessage {

    String messageId();

    String correlationId();

    Instant occurredAt();
}
