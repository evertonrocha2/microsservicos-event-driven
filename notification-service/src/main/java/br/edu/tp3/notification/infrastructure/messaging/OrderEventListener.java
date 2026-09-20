package br.edu.tp3.notification.infrastructure.messaging;

import br.edu.tp3.contracts.Queues;
import br.edu.tp3.contracts.events.OrderCancelledEvent;
import br.edu.tp3.contracts.events.OrderConfirmedEvent;
import br.edu.tp3.contracts.events.OrderCreatedEvent;
import br.edu.tp3.notification.application.NotificationLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Assinante 1 dos eventos de pedido.
 *
 * Um unico listener atende os tres eventos porque a fila esta ligada com o padrao
 * "order.#". A routing key da mensagem recebida diz qual evento chegou.
 *
 * Repare no que NAO tem aqui: nenhuma referencia ao order-service. Este servico
 * nao sabe o endereco dele, nao sabe se ele esta no ar, nao sabe nem se ele existe.
 * Sabe apenas ler o contrato da mensagem. Esse e o desacoplamento do event-driven.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final ObjectMapper objectMapper;
    private final NotificationLog notificationLog;

    public OrderEventListener(ObjectMapper objectMapper, NotificationLog notificationLog) {
        this.objectMapper = objectMapper;
        this.notificationLog = notificationLog;
    }

    @RabbitListener(queues = Queues.NOTIFICATION_ORDER_EVENTS)
    public void onOrderEvent(Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        String json = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            switch (routingKey) {
                case "order.created" -> {
                    OrderCreatedEvent event =
                            objectMapper.readValue(json, OrderCreatedEvent.class);
                    notificationLog.record(event.orderId(),
                            "Recebemos seu pedido no valor de " + event.totalAmount()
                                    + ". Estamos processando.");
                }
                case "order.confirmed" -> {
                    OrderConfirmedEvent event =
                            objectMapper.readValue(json, OrderConfirmedEvent.class);
                    notificationLog.record(event.orderId(),
                            "Pedido confirmado. Pagamento aprovado e estoque reservado.");
                }
                case "order.cancelled" -> {
                    OrderCancelledEvent event =
                            objectMapper.readValue(json, OrderCancelledEvent.class);
                    notificationLog.record(event.orderId(),
                            "Pedido cancelado. Motivo: " + event.reason());
                }
                default -> log.warn("evento desconhecido routingKey={}", routingKey);
            }

            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("falha ao notificar routingKey={}", routingKey, e);
            channel.basicNack(tag, false, false);
        }
    }
}
