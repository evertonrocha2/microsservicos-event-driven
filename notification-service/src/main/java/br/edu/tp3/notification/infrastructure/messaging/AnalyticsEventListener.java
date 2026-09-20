package br.edu.tp3.notification.infrastructure.messaging;

import br.edu.tp3.contracts.Queues;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Assinante 2 dos MESMOS eventos.
 *
 * Esta classe existe para provar o publish/subscribe na pratica. Ela le a mesma
 * mensagem que o OrderEventListener leu, de uma fila diferente, sem que nenhum
 * dos dois saiba da existencia do outro.
 *
 * E tambem um exemplo de por que o consumidor nao precisa entender o payload
 * inteiro: aqui so interessa CONTAR eventos por tipo. O corpo da mensagem nem
 * chega a ser desserializado.
 */
@Component
public class AnalyticsEventListener {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventListener.class);

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @RabbitListener(queues = Queues.ANALYTICS_ORDER_EVENTS)
    public void onOrderEvent(Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();

        try {
            long total = counters
                    .computeIfAbsent(routingKey, k -> new AtomicLong())
                    .incrementAndGet();

            log.info("ANALYTICS evento={} total={} tamanhoPayload={} bytes",
                    routingKey, total,
                    new String(message.getBody(), StandardCharsets.UTF_8).length());

            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("falha no analytics routingKey={}", routingKey, e);
            channel.basicNack(tag, false, false);
        }
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        counters.forEach((key, value) -> result.put(key, value.get()));
        return result;
    }
}
