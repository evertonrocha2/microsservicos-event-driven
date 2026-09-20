package br.edu.tp3.order.infrastructure.outbox;

import br.edu.tp3.order.domain.outbox.OutboxEntry;
import br.edu.tp3.order.domain.outbox.OutboxStatus;
import br.edu.tp3.order.domain.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * POLLING PUBLISHER do padrao Transactional Outbox.
 *
 * Roda separado do fluxo do usuario. A cada ciclo:
 *   1. le um lote de linhas PENDING em ordem de criacao;
 *   2. publica cada uma no exchange correspondente;
 *   3. marca como SENT.
 *
 * Se o processo cair entre publicar e marcar SENT, no proximo ciclo a mesma
 * mensagem sai de novo. Isso e entrega AT LEAST ONCE: a mensagem nunca se perde,
 * mas pode duplicar. Por isso todo consumidor precisa ser idempotente.
 *
 * Alternativa em producao: Change Data Capture lendo o WAL do banco (Debezium),
 * que elimina o polling. O padrao logico e o mesmo.
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int MAX_ATTEMPTS = 5;

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${tp3.outbox.batch-size:50}")
    private int batchSize;

    public OutboxPoller(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${tp3.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEntry> batch = outboxRepository.findBatchForPublishing(
                OutboxStatus.PENDING, PageRequest.of(0, batchSize));

        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEntry entry : batch) {
            try {
                MessageProperties props = new MessageProperties();
                // messageId no cabecalho AMQP: e o que o consumidor usa para
                // detectar duplicata sem precisar abrir o payload.
                props.setMessageId(entry.getMessageId());
                props.setCorrelationId(entry.getAggregateId());
                props.setType(entry.getMessageType());
                props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                props.setContentEncoding(StandardCharsets.UTF_8.name());
                // PERSISTENT: o broker grava a mensagem em disco. Sem isso,
                // um restart do RabbitMQ perderia tudo o que estava nas filas.
                props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);

                // O payload ja esta serializado em JSON na tabela. Enviamos os bytes
                // crus para nao passar pelo conversor de mensagem duas vezes.
                Message amqpMessage = new Message(
                        entry.getPayload().getBytes(StandardCharsets.UTF_8), props);

                rabbitTemplate.send(entry.getExchange(), entry.getRoutingKey(), amqpMessage);

                entry.markSent();
                log.debug("outbox publicado type={} routingKey={} messageId={}",
                        entry.getMessageType(), entry.getRoutingKey(), entry.getMessageId());

            } catch (Exception e) {
                entry.markAttemptFailed(MAX_ATTEMPTS);
                log.error("falha ao publicar outbox id={} tentativa={}",
                        entry.getId(), entry.getAttempts(), e);
            }
        }
    }
}
