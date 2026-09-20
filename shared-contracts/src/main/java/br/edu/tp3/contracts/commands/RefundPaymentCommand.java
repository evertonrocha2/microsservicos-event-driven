package br.edu.tp3.contracts.commands;

import br.edu.tp3.contracts.AsyncMessage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * COMANDO DE COMPENSACAO da saga.
 *
 * Em uma transacao distribuida nao existe ROLLBACK. O que existe e desfazer o efeito
 * com uma nova transacao de negocio. Estornar nao apaga a cobranca: cria um estorno.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RefundPaymentCommand(
        String messageId,
        String correlationId,
        Instant occurredAt,
        String orderId,
        BigDecimal amount,
        String reason
) implements AsyncMessage {
}
