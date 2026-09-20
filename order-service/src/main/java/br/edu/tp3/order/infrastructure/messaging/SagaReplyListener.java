package br.edu.tp3.order.infrastructure.messaging;

import br.edu.tp3.contracts.Queues;
import br.edu.tp3.contracts.replies.SagaReply;
import br.edu.tp3.order.application.OrderSagaOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Consumidor das respostas assincronas da saga.
 *
 * Este e o lado "response" do estilo REQUEST / ASYNC RESPONSE. O order-service
 * mandou o comando e seguiu a vida. A thread que atendeu o POST /api/orders ja
 * respondeu 202 ao cliente ha muito tempo. Quando a resposta chega aqui, a saga
 * e retomada em uma thread completamente diferente.
 *
 * ACK MANUAL. O fluxo e:
 *   processa -> deu certo   -> basicAck  (mensagem sai da fila)
 *            -> deu errado  -> basicNack (mensagem vai para a DLQ)
 *
 * Com ACK automatico a mensagem sairia da fila no instante da entrega. Uma queda
 * do servico no meio do processamento perderia a mensagem e travaria a saga para
 * sempre. Por isso o ACK so acontece depois do commit no banco.
 */
@Component
public class SagaReplyListener {

    private static final Logger log = LoggerFactory.getLogger(SagaReplyListener.class);

    private final OrderSagaOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    public SagaReplyListener(OrderSagaOrchestrator orchestrator, ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = Queues.SAGA_REPLIES)
    public void onSagaReply(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();

        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            SagaReply reply = objectMapper.readValue(json, SagaReply.class);

            log.debug("resposta recebida messageId={} orderId={} step={}",
                    messageId, reply.orderId(), reply.step());

            orchestrator.handleReply(reply);

            // Commit do banco ja aconteceu (o metodo do orquestrador e @Transactional).
            // So agora a mensagem pode sair da fila.
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("falha ao processar resposta da saga messageId={}", messageId, e);
            // requeue = false: vai para a DLQ em vez de voltar para a fila.
            // Reprocessar em loop uma mensagem que sempre falha travaria o consumidor.
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
