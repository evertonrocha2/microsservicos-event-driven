package br.edu.tp3.contracts.events;

import br.edu.tp3.contracts.AsyncMessage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/** EVENTO: a saga falhou, as compensacoes rodaram e o pedido foi cancelado. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCancelledEvent(
        String messageId,
        String correlationId,
        Instant occurredAt,
        String orderId,
        String customerId,
        String reason
) implements AsyncMessage {
}
