package br.edu.tp3.contracts.events;

import br.edu.tp3.contracts.AsyncMessage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/** EVENTO: a saga terminou com sucesso, o pedido esta confirmado. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderConfirmedEvent(
        String messageId,
        String correlationId,
        Instant occurredAt,
        String orderId,
        String customerId,
        BigDecimal totalAmount
) implements AsyncMessage {
}
