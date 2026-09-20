package br.edu.tp3.order.application;

import br.edu.tp3.contracts.Exchanges;
import br.edu.tp3.contracts.RoutingKeys;
import br.edu.tp3.contracts.commands.ReserveInventoryCommand;
import br.edu.tp3.contracts.events.OrderCreatedEvent;
import br.edu.tp3.order.domain.model.Order;
import br.edu.tp3.order.domain.model.OrderItem;
import br.edu.tp3.order.domain.repository.OrderRepository;
import br.edu.tp3.order.infrastructure.outbox.OutboxRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Caso de uso: criar pedido e disparar a saga.
 *
 * Repare no que acontece dentro de UMA unica @Transactional:
 *   1. salva o agregado Order;
 *   2. grava o EVENTO order.created no outbox (pub/sub, N assinantes);
 *   3. grava o COMANDO inventory.reserve no outbox (ponto a ponto, 1 destinatario).
 *
 * Nada e publicado no broker aqui. Ou tudo comita junto, ou nada comita.
 * Essa e a essencia do tratamento de transacoes com mensagens.
 */
@Service
public class CreateOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateOrderUseCase.class);

    /** Primeiro passo da saga para este pedido. Ver MessageSequenceGuard no inventory. */
    private static final long FIRST_STEP_SEQUENCE = 1L;

    private final OrderRepository orderRepository;
    private final OutboxRecorder outbox;

    public CreateOrderUseCase(OrderRepository orderRepository, OutboxRecorder outbox) {
        this.orderRepository = orderRepository;
        this.outbox = outbox;
    }

    @Transactional
    public Order handle(String customerId, List<OrderItem> items) {
        Order order = Order.place(customerId, items);
        orderRepository.save(order);

        // (a) EVENTO: "aconteceu". Publish/subscribe, o order-service nao sabe quem ouve.
        outbox.record("Order", order.getId(), Exchanges.EVENTS, RoutingKeys.ORDER_CREATED,
                new OrderCreatedEvent(
                        UUID.randomUUID().toString(),
                        order.getCorrelationId(),
                        Instant.now(),
                        order.getId(),
                        order.getCustomerId(),
                        order.getTotalAmount(),
                        OrderMessageMapper.toLines(order)));

        // (b) COMANDO: "faca". Ponto a ponto, destinatario unico (inventory-service).
        outbox.record("Order", order.getId(), Exchanges.COMMANDS, RoutingKeys.INVENTORY_RESERVE,
                new ReserveInventoryCommand(
                        UUID.randomUUID().toString(),
                        order.getCorrelationId(),
                        Instant.now(),
                        order.getId(),
                        FIRST_STEP_SEQUENCE,
                        OrderMessageMapper.toLines(order)));

        log.info("saga iniciada orderId={} correlationId={} total={}",
                order.getId(), order.getCorrelationId(), order.getTotalAmount());

        return order;
    }
}
