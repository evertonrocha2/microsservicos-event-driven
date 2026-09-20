package br.edu.tp3.payment.infrastructure.messaging;

import br.edu.tp3.contracts.Exchanges;
import br.edu.tp3.contracts.RoutingKeys;
import br.edu.tp3.contracts.replies.SagaReply;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Publica a resposta assincrona de volta ao orquestrador.
 *
 * Detalhe importante de ordem: esta publicacao acontece DEPOIS do commit da
 * transacao do handler (ver PaymentCommandListener). O motivo e simples: se a
 * publicacao viesse antes e a transacao desse rollback, o order-service avancaria
 * a saga com base em um fato que nunca existiu.
 *
 * Se a publicacao falhar depois do commit, o listener nao da ACK. O broker reentrega
 * o comando, a guarda de idempotencia reconhece a duplicata e o handler responde de
 * novo, sem repetir a cobranca. Nenhum caminho perde a saga.
 */
@Component
public class SagaReplyPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public SagaReplyPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(SagaReply reply) throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(reply);

        MessageProperties props = new MessageProperties();
        props.setMessageId(reply.messageId());
        props.setCorrelationId(reply.correlationId());
        props.setType(SagaReply.class.getSimpleName());
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);

        rabbitTemplate.send(Exchanges.REPLIES, RoutingKeys.SAGA_REPLY, new Message(body, props));
    }
}
