package br.edu.tp3.order.interfaces.rest;

import br.edu.tp3.order.domain.model.Order;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Contrato de SAIDA da API REST.
 *
 * O campo status e o que o cliente consulta enquanto a saga roda. Como a resposta
 * do POST e 202 Accepted (e nao 201 Created com tudo pronto), o cliente precisa
 * de um jeito de acompanhar. Aqui isso e feito por polling neste endpoint.
 * Em producao daria para melhorar com webhook, Server-Sent Events ou WebSocket.
 */
@Schema(description = "Estado atual do pedido e da saga")
public record OrderResponse(

        @Schema(example = "3f2b1c9e-0f6a-4f3a-9d1e-2f7c5a8b1d33")
        String orderId,

        @Schema(example = "cliente-001")
        String customerId,

        @Schema(example = "PENDING",
                description = "PENDING, INVENTORY_RESERVED, PAYMENT_AUTHORIZED, "
                        + "COMPENSATING, CONFIRMED ou CANCELLED")
        String status,

        @Schema(example = "7000.00")
        BigDecimal totalAmount,

        @Schema(description = "Id que amarra todas as mensagens desta saga")
        String correlationId,

        @Schema(description = "Preenchido quando a saga foi compensada")
        String failureReason,

        Instant createdAt,
        Instant updatedAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCorrelationId(),
                order.getFailureReason(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
