package br.edu.tp3.inventory.infrastructure.messaging;

import br.edu.tp3.contracts.Exchanges;
import br.edu.tp3.contracts.Queues;
import br.edu.tp3.contracts.RoutingKeys;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia do inventory-service.
 *
 * O ponto central deste arquivo e o x-single-active-consumer nas filas de comando.
 *
 * O que ele faz: mesmo com N replicas do inventory-service conectadas na mesma
 * fila, o RabbitMQ entrega mensagens para UMA de cada vez. As outras ficam de
 * prontidao. Se a ativa cair, o broker promove outra automaticamente.
 *
 * O que se ganha: ordem de processamento preservada, sem perder alta disponibilidade.
 * O que se perde: vazao, porque nao ha paralelismo dentro da fila.
 *
 * Quando vale a pena: quando a ordem importa mais que a vazao, como aqui, onde
 * reservar e liberar fora de ordem corrompe o saldo de estoque.
 *
 * Como escalar mesmo assim: particionar por agregado. Varias filas, cada uma com
 * consumidor unico ativo, e a mensagem roteada para a fila pelo hash do orderId.
 * E exatamente o que o Kafka faz com particoes e chave de particao.
 */
@Configuration
public class RabbitMQConfig {

    /** Argumento AMQP que liga o modo de consumidor unico ativo. */
    private static final String SINGLE_ACTIVE_CONSUMER = "x-single-active-consumer";

    /** Tempo que uma mensagem adiantada espera antes de voltar para a fila principal. */
    private static final int ORDER_RETRY_TTL_MS = 3000;

    @Bean
    DirectExchange commandsExchange() {
        return new DirectExchange(Exchanges.COMMANDS, true, false);
    }

    @Bean
    DirectExchange repliesExchange() {
        return new DirectExchange(Exchanges.REPLIES, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(Exchanges.DEAD_LETTER, true, false);
    }

    /** Exchange das filas de espera por ordem. */
    @Bean
    DirectExchange retryExchange() {
        return new DirectExchange(Exchanges.RETRY, true, false);
    }

    // ------------------------------------------------------------------
    // Fila de reserva (sequencia 1 do pedido)
    // ------------------------------------------------------------------

    @Bean
    Queue reserveQueue() {
        return QueueBuilder.durable(Queues.INVENTORY_RESERVE)
                .withArgument(SINGLE_ACTIVE_CONSUMER, true)
                .deadLetterExchange(Exchanges.DEAD_LETTER)
                .deadLetterRoutingKey(Queues.deadLetterOf(Queues.INVENTORY_RESERVE))
                .build();
    }

    @Bean
    Queue reserveDlq() {
        return QueueBuilder.durable(Queues.deadLetterOf(Queues.INVENTORY_RESERVE)).build();
    }

    @Bean
    Binding reserveBinding() {
        return BindingBuilder.bind(reserveQueue())
                .to(commandsExchange())
                .with(RoutingKeys.INVENTORY_RESERVE);
    }

    @Bean
    Binding reserveDlqBinding() {
        return BindingBuilder.bind(reserveDlq())
                .to(deadLetterExchange())
                .with(Queues.deadLetterOf(Queues.INVENTORY_RESERVE));
    }

    // ------------------------------------------------------------------
    // Fila de liberacao / compensacao (sequencia 2 do pedido)
    // ------------------------------------------------------------------

    @Bean
    Queue releaseQueue() {
        return QueueBuilder.durable(Queues.INVENTORY_RELEASE)
                .withArgument(SINGLE_ACTIVE_CONSUMER, true)
                .deadLetterExchange(Exchanges.DEAD_LETTER)
                .deadLetterRoutingKey(Queues.deadLetterOf(Queues.INVENTORY_RELEASE))
                .build();
    }

    @Bean
    Queue releaseDlq() {
        return QueueBuilder.durable(Queues.deadLetterOf(Queues.INVENTORY_RELEASE)).build();
    }

    @Bean
    Binding releaseBinding() {
        return BindingBuilder.bind(releaseQueue())
                .to(commandsExchange())
                .with(RoutingKeys.INVENTORY_RELEASE);
    }

    @Bean
    Binding releaseDlqBinding() {
        return BindingBuilder.bind(releaseDlq())
                .to(deadLetterExchange())
                .with(Queues.deadLetterOf(Queues.INVENTORY_RELEASE));
    }

    // ------------------------------------------------------------------
    // Filas de ESPERA POR ORDEM
    //
    // Uma mensagem adiantada vai para ca em vez de voltar para a fila
    // principal. Repare que nenhum consumidor escuta estas filas. Elas sao
    // apenas um cronometro:
    //
    //   x-message-ttl              = 3000  -> a mensagem "vence" em 3 segundos
    //   x-dead-letter-exchange     = tp3.commands
    //   x-dead-letter-routing-key  = inventory.reserve (ou release)
    //
    // Quando o TTL expira, o RabbitMQ dead-letter a mensagem de volta para a
    // fila original, sozinho. Enquanto ela espera, a fila principal continua
    // entregando as outras mensagens normalmente.
    //
    // Este e o truque que elimina o head-of-line blocking sem perder a ordem.
    // ------------------------------------------------------------------

    @Bean
    Queue reserveRetryQueue() {
        return QueueBuilder.durable(Queues.INVENTORY_RESERVE_RETRY)
                .ttl(ORDER_RETRY_TTL_MS)
                .deadLetterExchange(Exchanges.COMMANDS)
                .deadLetterRoutingKey(RoutingKeys.INVENTORY_RESERVE)
                .build();
    }

    @Bean
    Binding reserveRetryBinding() {
        return BindingBuilder.bind(reserveRetryQueue())
                .to(retryExchange())
                .with(RoutingKeys.INVENTORY_RESERVE_RETRY);
    }

    @Bean
    Queue releaseRetryQueue() {
        return QueueBuilder.durable(Queues.INVENTORY_RELEASE_RETRY)
                .ttl(ORDER_RETRY_TTL_MS)
                .deadLetterExchange(Exchanges.COMMANDS)
                .deadLetterRoutingKey(RoutingKeys.INVENTORY_RELEASE)
                .build();
    }

    @Bean
    Binding releaseRetryBinding() {
        return BindingBuilder.bind(releaseRetryQueue())
                .to(retryExchange())
                .with(RoutingKeys.INVENTORY_RELEASE_RETRY);
    }
}
