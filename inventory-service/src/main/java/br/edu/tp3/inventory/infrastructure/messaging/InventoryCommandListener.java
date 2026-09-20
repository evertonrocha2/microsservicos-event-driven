package br.edu.tp3.inventory.infrastructure.messaging;

import br.edu.tp3.contracts.Queues;
import br.edu.tp3.contracts.RoutingKeys;
import br.edu.tp3.contracts.commands.ReleaseInventoryCommand;
import br.edu.tp3.contracts.commands.ReserveInventoryCommand;
import br.edu.tp3.contracts.replies.SagaReply;
import br.edu.tp3.inventory.application.BusinessRejection;
import br.edu.tp3.inventory.application.ReleaseInventoryHandler;
import br.edu.tp3.inventory.application.ReserveInventoryHandler;
import br.edu.tp3.inventory.infrastructure.idempotency.DuplicateMessageException;
import br.edu.tp3.inventory.infrastructure.ordering.OutOfOrderMessageException;
import br.edu.tp3.inventory.infrastructure.ordering.OutOfOrderRetryPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Listener do inventory-service.
 *
 * Aqui fica a tabela de decisao mais importante do consumo assincrono. Cada tipo
 * de problema pede um tratamento diferente, e trocar um pelo outro causa bug feio:
 *
 *  | Situacao                  | Acao no broker              | Resposta a saga |
 *  |---------------------------|-----------------------------|-----------------|
 *  | Sucesso                   | ACK                         | SUCCESS         |
 *  | Recusa de negocio         | ACK (foi processada)        | FAILURE         |
 *  | Duplicata em corrida      | ACK (outro ja fez)          | nenhuma         |
 *  | Mensagem adiantada        | move para a fila de espera  | nenhuma         |
 *  | Espera estourou o limite  | NACK com requeue = false    | nenhuma (DLQ)   |
 *  | Falha tecnica             | NACK com requeue = false    | nenhuma (DLQ)   |
 *
 * Os dois erros classicos que esta tabela evita:
 *
 *  1. Mandar recusa de negocio para a DLQ. A mensagem estava certa, a resposta e
 *     que foi "nao". Se ela for para a DLQ, a saga fica esperando para sempre.
 *
 *  2. Ficar retentando falha tecnica em loop apertado. Sem limite, uma mensagem
 *     que sempre falha consome o consumidor inteiro e trava a fila.
 *
 *  3. Devolver a mensagem adiantada com requeue = true. Ela volta para a cabeca
 *     da fila e, com prefetch 1, e reentregue na hora. Nenhuma outra mensagem
 *     passa enquanto isso. Por causa disso a mensagem adiantada e MOVIDA para
 *     uma fila de espera com TTL, e nao devolvida. Ver OutOfOrderRetryPublisher.
 */
@Component
public class InventoryCommandListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryCommandListener.class);

    private final ReserveInventoryHandler reserveHandler;
    private final ReleaseInventoryHandler releaseHandler;
    private final SagaReplyPublisher replyPublisher;
    private final OutOfOrderRetryPublisher retryPublisher;
    private final ObjectMapper objectMapper;

    public InventoryCommandListener(ReserveInventoryHandler reserveHandler,
                                    ReleaseInventoryHandler releaseHandler,
                                    SagaReplyPublisher replyPublisher,
                                    OutOfOrderRetryPublisher retryPublisher,
                                    ObjectMapper objectMapper) {
        this.reserveHandler = reserveHandler;
        this.releaseHandler = releaseHandler;
        this.replyPublisher = replyPublisher;
        this.retryPublisher = retryPublisher;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = Queues.INVENTORY_RESERVE)
    public void onReserve(Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();

        try {
            ReserveInventoryCommand command = objectMapper.readValue(
                    body(message), ReserveInventoryCommand.class);

            log.debug("reserva recebida messageId={} orderId={} seq={}",
                    messageId, command.orderId(), command.sequenceNumber());

            SagaReply reply = reserveHandler.handle(command);
            replyPublisher.publish(reply);
            channel.basicAck(tag, false);

        } catch (BusinessRejection e) {
            // Recusa de negocio. Transacao sofreu rollback, mas a mensagem
            // FOI processada. ACK, e a saga recebe FAILURE para compensar.
            replyPublisher.publish(e.getReply());
            channel.basicAck(tag, false);

        } catch (OutOfOrderMessageException e) {
            // Mensagem adiantada. Nao e erro, e so cedo demais.
            // Sai da fila principal e vai esperar a vez na fila de espera.
            log.warn("{}", e.getMessage());
            handleOutOfOrder(message, channel, tag, RoutingKeys.INVENTORY_RESERVE_RETRY);

        } catch (DuplicateMessageException e) {
            log.info("{}", e.getMessage());
            channel.basicAck(tag, false);

        } catch (Exception e) {
            // Falha tecnica. Vai para a DLQ para analise manual.
            log.error("falha tecnica na reserva messageId={}", messageId, e);
            channel.basicNack(tag, false, false);
        }
    }

    @RabbitListener(queues = Queues.INVENTORY_RELEASE)
    public void onRelease(Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();

        try {
            ReleaseInventoryCommand command = objectMapper.readValue(
                    body(message), ReleaseInventoryCommand.class);

            log.debug("liberacao recebida messageId={} orderId={} seq={}",
                    messageId, command.orderId(), command.sequenceNumber());

            SagaReply reply = releaseHandler.handle(command);
            replyPublisher.publish(reply);
            channel.basicAck(tag, false);

        } catch (OutOfOrderMessageException e) {
            // Caso classico: a liberacao (seq 2) chegou antes da reserva (seq 1).
            // Em vez de devolver para a fila, guarda na fila de espera.
            log.warn("{}", e.getMessage());
            handleOutOfOrder(message, channel, tag, RoutingKeys.INVENTORY_RELEASE_RETRY);

        } catch (DuplicateMessageException e) {
            log.info("{}", e.getMessage());
            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("falha tecnica na liberacao messageId={}", messageId, e);
            channel.basicNack(tag, false, false);
        }
    }

    /**
     * Move a mensagem adiantada para a fila de espera.
     *
     * O ACK aqui nao significa "processei". Significa "tirei da fila principal e
     * guardei em outro lugar". A mensagem nao se perde: ela volta sozinha daqui
     * a alguns segundos, por dead lettering do TTL.
     *
     * Se o limite de tentativas estourar, ela vai para a DLQ. A essa altura algo
     * esta realmente errado, porque a mensagem anterior deveria ter chegado.
     */
    private void handleOutOfOrder(Message message, Channel channel, long tag,
                                  String retryRoutingKey) throws Exception {
        if (retryPublisher.scheduleRetry(message, retryRoutingKey)) {
            channel.basicAck(tag, false);
        } else {
            channel.basicNack(tag, false, false);
        }
    }

    private String body(Message message) {
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }
}
