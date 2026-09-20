package br.edu.tp3.payment.infrastructure.messaging;

import br.edu.tp3.contracts.Queues;
import br.edu.tp3.contracts.commands.AuthorizePaymentCommand;
import br.edu.tp3.contracts.commands.RefundPaymentCommand;
import br.edu.tp3.contracts.replies.SagaReply;
import br.edu.tp3.payment.application.AuthorizePaymentHandler;
import br.edu.tp3.payment.application.RefundPaymentHandler;
import br.edu.tp3.payment.infrastructure.idempotency.DuplicateMessageException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Ponto de entrada assincrono do payment-service.
 *
 * O listener faz apenas o trabalho de porta de entrada:
 *   desserializa -> chama o handler -> publica a resposta -> confirma (ACK).
 *
 * A regra de negocio fica no handler, que e testavel sem broker nenhum.
 *
 * A ORDEM aqui nao e enfeite:
 *
 *   1. handler.handle(...)   -> roda e COMITA a transacao do banco;
 *   2. replyPublisher        -> so entao a resposta vai para o broker;
 *   3. basicAck              -> so entao o comando sai da fila.
 *
 * Qualquer falha antes do ACK faz o broker reentregar. Como o handler e idempotente,
 * reentregar e seguro. Esse e o contrato de quem vive em at least once.
 */
@Component
public class PaymentCommandListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandListener.class);

    private final AuthorizePaymentHandler authorizeHandler;
    private final RefundPaymentHandler refundHandler;
    private final SagaReplyPublisher replyPublisher;
    private final ObjectMapper objectMapper;

    public PaymentCommandListener(AuthorizePaymentHandler authorizeHandler,
                                  RefundPaymentHandler refundHandler,
                                  SagaReplyPublisher replyPublisher,
                                  ObjectMapper objectMapper) {
        this.authorizeHandler = authorizeHandler;
        this.refundHandler = refundHandler;
        this.replyPublisher = replyPublisher;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = Queues.PAYMENT_AUTHORIZE)
    public void onAuthorize(Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();

        try {
            AuthorizePaymentCommand command = objectMapper.readValue(
                    body(message), AuthorizePaymentCommand.class);

            log.debug("comando recebido messageId={} orderId={} valor={}",
                    messageId, command.orderId(), command.amount());

            SagaReply reply = authorizeHandler.handle(command);
            replyPublisher.publish(reply);
            channel.basicAck(tag, false);

        } catch (DuplicateMessageException e) {
            // Outra replica processou. Nada a fazer, so tirar da fila.
            log.info("{}", e.getMessage());
            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("falha ao autorizar pagamento messageId={}", messageId, e);
            channel.basicNack(tag, false, false);
        }
    }

    @RabbitListener(queues = Queues.PAYMENT_REFUND)
    public void onRefund(Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();

        try {
            RefundPaymentCommand command = objectMapper.readValue(
                    body(message), RefundPaymentCommand.class);

            SagaReply reply = refundHandler.handle(command);
            replyPublisher.publish(reply);
            channel.basicAck(tag, false);

        } catch (DuplicateMessageException e) {
            log.info("{}", e.getMessage());
            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("falha ao estornar pagamento messageId={}", messageId, e);
            channel.basicNack(tag, false, false);
        }
    }

    private String body(Message message) {
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }
}
