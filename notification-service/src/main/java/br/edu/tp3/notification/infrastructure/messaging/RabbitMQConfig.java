package br.edu.tp3.notification.infrastructure.messaging;

import br.edu.tp3.contracts.Exchanges;
import br.edu.tp3.contracts.Queues;
import br.edu.tp3.contracts.RoutingKeys;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PUBLISH / SUBSCRIBE na pratica.
 *
 * Duas filas diferentes ligadas ao MESMO exchange com o MESMO padrao "order.#".
 * O order-service publica o evento uma vez. O broker coloca uma copia em cada fila.
 *
 *     order-service -> [tp3.events] --order.#--> notification.order-events.queue
 *                                   --order.#--> analytics.order-events.queue
 *
 * Tres consequencias que valem citar na defesa do trabalho:
 *
 *  1. Cada assinante tem SUA fila. Se o analytics estiver fora do ar, as mensagens
 *     dele se acumulam na fila dele, sem atrapalhar o notification em nada.
 *
 *  2. Adicionar um terceiro assinante (antifraude, por exemplo) nao exige mudar
 *     nenhuma linha do order-service. E so criar fila e binding. Isso e o
 *     acoplamento fraco que o event-driven promete.
 *
 *  3. O padrao "order.#" pega order.created, order.confirmed e order.cancelled.
 *     Filtrar no binding e mais barato que receber tudo e descartar no codigo.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(Exchanges.EVENTS, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(Exchanges.DEAD_LETTER, true, false);
    }

    // ------------------------------------------------------------------
    // Assinante 1: notificacoes ao cliente
    // ------------------------------------------------------------------

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(Queues.NOTIFICATION_ORDER_EVENTS)
                .deadLetterExchange(Exchanges.DEAD_LETTER)
                .deadLetterRoutingKey(Queues.deadLetterOf(Queues.NOTIFICATION_ORDER_EVENTS))
                .build();
    }

    @Bean
    Queue notificationDlq() {
        return QueueBuilder.durable(
                Queues.deadLetterOf(Queues.NOTIFICATION_ORDER_EVENTS)).build();
    }

    @Bean
    Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(eventsExchange())
                .with(RoutingKeys.ORDER_ALL);
    }

    @Bean
    Binding notificationDlqBinding() {
        return BindingBuilder.bind(notificationDlq())
                .to(deadLetterExchange())
                .with(Queues.deadLetterOf(Queues.NOTIFICATION_ORDER_EVENTS));
    }

    // ------------------------------------------------------------------
    // Assinante 2: analytics. Recebe os MESMOS eventos, de forma independente.
    // ------------------------------------------------------------------

    @Bean
    Queue analyticsQueue() {
        return QueueBuilder.durable(Queues.ANALYTICS_ORDER_EVENTS)
                .deadLetterExchange(Exchanges.DEAD_LETTER)
                .deadLetterRoutingKey(Queues.deadLetterOf(Queues.ANALYTICS_ORDER_EVENTS))
                .build();
    }

    @Bean
    Queue analyticsDlq() {
        return QueueBuilder.durable(
                Queues.deadLetterOf(Queues.ANALYTICS_ORDER_EVENTS)).build();
    }

    @Bean
    Binding analyticsBinding() {
        return BindingBuilder.bind(analyticsQueue())
                .to(eventsExchange())
                .with(RoutingKeys.ORDER_ALL);
    }

    @Bean
    Binding analyticsDlqBinding() {
        return BindingBuilder.bind(analyticsDlq())
                .to(deadLetterExchange())
                .with(Queues.deadLetterOf(Queues.ANALYTICS_ORDER_EVENTS));
    }
}
