package br.edu.tp3.contracts.commands;

import br.edu.tp3.contracts.AsyncMessage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * COMANDO: "autorize este pagamento".
 *
 * Caracteristicas de um comando (diferente de evento):
 *  - verbo no imperativo;
 *  - tem UM destinatario conhecido (payment-service);
 *  - o remetente espera uma resposta (SagaReply);
 *  - vai por exchange DIRECT, fila ponto a ponto.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthorizePaymentCommand(
        String messageId,
        String correlationId,
        Instant occurredAt,
        String orderId,
        String customerId,
        BigDecimal amount
) implements AsyncMessage {
}
