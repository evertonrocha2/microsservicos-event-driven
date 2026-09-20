package br.edu.tp3.order.application;

import br.edu.tp3.contracts.Exchanges;
import br.edu.tp3.contracts.RoutingKeys;
import br.edu.tp3.contracts.commands.AuthorizePaymentCommand;
import br.edu.tp3.contracts.commands.ReleaseInventoryCommand;
import br.edu.tp3.contracts.events.OrderCancelledEvent;
import br.edu.tp3.contracts.events.OrderConfirmedEvent;
import br.edu.tp3.contracts.replies.SagaReply;
import br.edu.tp3.order.domain.model.Order;
import br.edu.tp3.order.domain.model.OrderStatus;
import br.edu.tp3.order.domain.repository.OrderRepository;
import br.edu.tp3.order.infrastructure.outbox.OutboxRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * ORQUESTRADOR DA SAGA do pedido (Saga por orquestracao).
 *
 * Fluxo feliz:
 *   PENDING --reservar estoque--> INVENTORY_RESERVED --autorizar pagamento-->
 *   PAYMENT_AUTHORIZED --> CONFIRMED (evento order.confirmed)
 *
 * Fluxo de falha no pagamento (compensacao):
 *   INVENTORY_RESERVED --pagamento negado--> COMPENSATING
 *   --liberar estoque--> CANCELLED (evento order.cancelled)
 *
 * Tres decisoes de projeto que valem comentario:
 *
 *  1. NAO EXISTE ROLLBACK. Cada passo ja comitou no banco do seu proprio servico.
 *     Desfazer significa executar uma nova transacao de negocio (a compensacao).
 *
 *  2. O ESTADO DA SAGA E O STATUS DO PEDIDO, persistido no banco. Se este servico
 *     cair no meio da saga, ao voltar ele sabe exatamente onde parou.
 *
 *  3. IDEMPOTENCIA POR MAQUINA DE ESTADOS. A entrega e at least once, entao a mesma
 *     resposta pode chegar duas vezes. Antes de agir, o orquestrador confere se o
 *     pedido esta no estado que aquela resposta espera. Se nao estiver, ignora.
 */
@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    /** Compensacao do estoque e o segundo comando do pedido. Ver MessageSequenceGuard. */
    private static final long COMPENSATION_SEQUENCE = 2L;

    private final OrderRepository orderRepository;
    private final OutboxRecorder outbox;

    public OrderSagaOrchestrator(OrderRepository orderRepository, OutboxRecorder outbox) {
        this.orderRepository = orderRepository;
        this.outbox = outbox;
    }

    /**
     * Recebe a resposta de um participante e decide o proximo passo.
     *
     * Tudo dentro de uma transacao: a mudanca de estado do pedido e o proximo
     * comando (gravado no outbox) sao atomicos entre si.
     */
    @Transactional
    public void handleReply(SagaReply reply) {
        Order order = orderRepository.findById(reply.orderId()).orElse(null);

        if (order == null) {
            log.warn("resposta para pedido inexistente orderId={} step={}",
                    reply.orderId(), reply.step());
            return;
        }

        if (order.getStatus().isFinal()) {
            log.info("saga ja encerrada, resposta ignorada orderId={} status={} step={}",
                    order.getId(), order.getStatus(), reply.step());
            return;
        }

        log.info("saga avancando orderId={} step={} outcome={} statusAtual={}",
                order.getId(), reply.step(), reply.outcome(), order.getStatus());

        switch (reply.step()) {
            case RESERVE_INVENTORY -> onInventoryReply(order, reply);
            case AUTHORIZE_PAYMENT -> onPaymentReply(order, reply);
            case RELEASE_INVENTORY -> onCompensationFinished(order, reply);
            case REFUND_PAYMENT -> onCompensationFinished(order, reply);
        }
    }

    // ------------------------------------------------------------------
    // Passo 1: reserva de estoque
    // ------------------------------------------------------------------
    private void onInventoryReply(Order order, SagaReply reply) {
        if (!reply.succeeded()) {
            // Nenhum outro servico chegou a mudar estado, entao nao ha o que compensar.
            // Basta encerrar o pedido.
            order.startCompensation(reply.reason());
            order.cancel(reply.reason());
            publishCancelled(order, reply.reason());
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("resposta duplicada de reserva ignorada orderId={}", order.getId());
            return;
        }

        order.inventoryReserved();

        // Proximo comando da saga, gravado no outbox junto com a mudanca de estado.
        outbox.record("Order", order.getId(), Exchanges.COMMANDS, RoutingKeys.PAYMENT_AUTHORIZE,
                new AuthorizePaymentCommand(
                        UUID.randomUUID().toString(),
                        order.getCorrelationId(),
                        Instant.now(),
                        order.getId(),
                        order.getCustomerId(),
                        order.getTotalAmount()));
    }

    // ------------------------------------------------------------------
    // Passo 2: autorizacao do pagamento
    // ------------------------------------------------------------------
    private void onPaymentReply(Order order, SagaReply reply) {
        if (reply.succeeded()) {
            if (order.getStatus() != OrderStatus.INVENTORY_RESERVED) {
                log.info("resposta duplicada de pagamento ignorada orderId={}", order.getId());
                return;
            }
            order.paymentAuthorized();
            order.confirm();
            publishConfirmed(order);
            return;
        }

        // FALHA: o estoque ja foi reservado e precisa ser devolvido.
        // Esta e a TRANSACAO COMPENSATORIA da saga.
        if (order.getStatus() == OrderStatus.COMPENSATING) {
            log.info("compensacao ja em andamento, resposta ignorada orderId={}", order.getId());
            return;
        }

        order.startCompensation(reply.reason());

        outbox.record("Order", order.getId(), Exchanges.COMMANDS, RoutingKeys.INVENTORY_RELEASE,
                new ReleaseInventoryCommand(
                        UUID.randomUUID().toString(),
                        order.getCorrelationId(),
                        Instant.now(),
                        order.getId(),
                        COMPENSATION_SEQUENCE,
                        OrderMessageMapper.toLines(order),
                        reply.reason()));
    }

    // ------------------------------------------------------------------
    // Passo 3: compensacao concluida
    // ------------------------------------------------------------------
    private void onCompensationFinished(Order order, SagaReply reply) {
        String reason = order.getFailureReason() != null
                ? order.getFailureReason()
                : reply.reason();

        order.cancel(reason);
        publishCancelled(order, reason);
    }

    // ------------------------------------------------------------------
    // Eventos de saida (publish/subscribe)
    // ------------------------------------------------------------------
    private void publishConfirmed(Order order) {
        outbox.record("Order", order.getId(), Exchanges.EVENTS, RoutingKeys.ORDER_CONFIRMED,
                new OrderConfirmedEvent(
                        UUID.randomUUID().toString(),
                        order.getCorrelationId(),
                        Instant.now(),
                        order.getId(),
                        order.getCustomerId(),
                        order.getTotalAmount()));
        log.info("SAGA CONCLUIDA COM SUCESSO orderId={}", order.getId());
    }

    private void publishCancelled(Order order, String reason) {
        outbox.record("Order", order.getId(), Exchanges.EVENTS, RoutingKeys.ORDER_CANCELLED,
                new OrderCancelledEvent(
                        UUID.randomUUID().toString(),
                        order.getCorrelationId(),
                        Instant.now(),
                        order.getId(),
                        order.getCustomerId(),
                        reason));
        log.warn("SAGA COMPENSADA orderId={} motivo={}", order.getId(), reason);
    }
}
