package br.edu.tp3.order.application;

import br.edu.tp3.contracts.OrderLine;
import br.edu.tp3.order.domain.model.Order;
import br.edu.tp3.order.domain.model.OrderItem;

import java.util.List;

/**
 * Traduz o modelo INTERNO do agregado para a linguagem PUBLICA das mensagens.
 *
 * Esta classe e a fronteira do Bounded Context. Sem ela, mudar um campo do
 * agregado quebraria todo mundo que consome as mensagens.
 */
public final class OrderMessageMapper {

    private OrderMessageMapper() {
    }

    public static List<OrderLine> toLines(Order order) {
        return order.getItems().stream()
                .map(OrderMessageMapper::toLine)
                .toList();
    }

    private static OrderLine toLine(OrderItem item) {
        return new OrderLine(item.getSku(), item.getQuantity(), item.getUnitPrice());
    }
}
