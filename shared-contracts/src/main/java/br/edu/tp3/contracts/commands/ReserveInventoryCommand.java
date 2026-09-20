package br.edu.tp3.contracts.commands;

import br.edu.tp3.contracts.OrderLine;
import br.edu.tp3.contracts.OrderedMessage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * COMANDO ORDENADO: reservar estoque.
 *
 * Implementa OrderedMessage porque reservar e liberar o mesmo pedido fora de ordem
 * corrompe o saldo de estoque. O aggregateId e o orderId, e o sequenceNumber e 1.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReserveInventoryCommand(
        String messageId,
        String correlationId,
        Instant occurredAt,
        String orderId,
        long sequenceNumber,
        List<OrderLine> items
) implements OrderedMessage {

    @Override
    public String aggregateId() {
        return orderId;
    }
}
