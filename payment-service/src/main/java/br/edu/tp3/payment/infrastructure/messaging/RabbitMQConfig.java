package br.edu.tp3.payment.infrastructure.messaging;

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
 * Cada servico declara as filas que ELE consome.
 *
 * O payment-service nao sabe quem produz os comandos. Ele so diz ao broker:
 * "me entregue tudo que chegar em tp3.commands com a routing key payment.authorize".
 *
 * Esse e o desacoplamento que o broker traz. O produtor conhece o exchange,
 * o consumidor conhece a fila, e nenhum dos dois conhece o outro.
 */
@Configuration
public class RabbitMQConfig {

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

    // ------------------------------------------------------------------
    // Fila de autorizacao
    // ------------------------------------------------------------------

    @Bean
    Queue authorizeQueue() {
        return QueueBuilder.durable(Queues.PAYMENT_AUTHORIZE)
                // durable: sobrevive a restart do broker.
                .deadLetterExchange(Exchanges.DEAD_LETTER)
                .deadLetterRoutingKey(Queues.deadLetterOf(Queues.PAYMENT_AUTHORIZE))
                .build();
    }

    @Bean
    Queue authorizeDlq() {
        return QueueBuilder.durable(Queues.deadLetterOf(Queues.PAYMENT_AUTHORIZE)).build();
    }

    @Bean
    Binding authorizeBinding() {
        return BindingBuilder.bind(authorizeQueue())
                .to(commandsExchange())
                .with(RoutingKeys.PAYMENT_AUTHORIZE);
    }

    @Bean
    Binding authorizeDlqBinding() {
        return BindingBuilder.bind(authorizeDlq())
                .to(deadLetterExchange())
                .with(Queues.deadLetterOf(Queues.PAYMENT_AUTHORIZE));
    }

    // ------------------------------------------------------------------
    // Fila de estorno (compensacao)
    // ------------------------------------------------------------------

    @Bean
    Queue refundQueue() {
        return QueueBuilder.durable(Queues.PAYMENT_REFUND)
                .deadLetterExchange(Exchanges.DEAD_LETTER)
                .deadLetterRoutingKey(Queues.deadLetterOf(Queues.PAYMENT_REFUND))
                .build();
    }

    @Bean
    Queue refundDlq() {
        return QueueBuilder.durable(Queues.deadLetterOf(Queues.PAYMENT_REFUND)).build();
    }

    @Bean
    Binding refundBinding() {
        return BindingBuilder.bind(refundQueue())
                .to(commandsExchange())
                .with(RoutingKeys.PAYMENT_REFUND);
    }

    @Bean
    Binding refundDlqBinding() {
        return BindingBuilder.bind(refundDlq())
                .to(deadLetterExchange())
                .with(Queues.deadLetterOf(Queues.PAYMENT_REFUND));
    }
}
