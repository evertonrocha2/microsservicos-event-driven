package br.edu.tp3.contracts.commands;

import br.edu.tp3.contracts.OrderLine;
import br.edu.tp3.contracts.OrderedMessage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * COMANDO DE COMPENSACAO ORDENADO: devolver o estoque reservado.
 *
 * Chega com sequenceNumber = 2, logo depois da reserva (sequenceNumber = 1).
 * Se por qualquer motivo esta mensagem chegar antes da reserva, o consumidor
 * recusa e devolve para a fila ate que a ordem seja respeitada.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReleaseInventoryCommand(
        String messageId,
        String correlationId,
        Instant occurredAt,
        String orderId,
        long sequenceNumber,
        List<OrderLine> items,
        String reason
) implements OrderedMessage {

    @Override
    public String aggregateId() {
        return orderId;
    }
}
