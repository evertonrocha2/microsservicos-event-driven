package br.edu.tp3.order.infrastructure.messaging;

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
 * TOPOLOGIA DO BROKER declarada em codigo.
 *
 * Tres blocos, tres estilos de interacao:
 *
 *  tp3.commands (DIRECT)  -> comando vai para UMA fila. Ponto a ponto.
 *  tp3.events   (TOPIC)   -> evento e copiado para TODAS as filas que casam com o
 *                            padrao. Publish/subscribe.
 *  tp3.replies  (DIRECT)  -> resposta assincrona volta para o orquestrador.
 *
 * A declaracao e idempotente: se o exchange ou a fila ja existem com os mesmos
 * parametros, o RabbitMQ so confirma. Se existirem com parametros diferentes,
 * ele recusa, o que protege contra topologia divergente entre servicos.
 */
@Configuration
public class RabbitMQConfig {

    // ------------------------------------------------------------------
    // Exchanges
    // ------------------------------------------------------------------

    @Bean
    DirectExchange commandsExchange() {
        return new DirectExchange(Exchanges.COMMANDS, true, false);
    }

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(Exchanges.EVENTS, true, false);
    }

    @Bean
    DirectExchange repliesExchange() {
        return new DirectExchange(Exchanges.REPLIES, true, false);
    }

    /**
     * Dead Letter Exchange.
     *
     * Quando um consumidor rejeita a mensagem sem recolocar na fila (basicNack com
     * requeue=false), o broker manda a mensagem para ca em vez de descartar.
     * E a rede de seguranca contra "poison message": uma mensagem que sempre falha
     * e que, em requeue infinito, travaria a fila inteira.
     */
    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(Exchanges.DEAD_LETTER, true, false);
    }

    // ------------------------------------------------------------------
    // Fila de respostas da saga (consumida por este servico)
    // ------------------------------------------------------------------

    @Bean
    Queue sagaRepliesQueue() {
        return QueueBuilder.durable(Queues.SAGA_REPLIES)
                .deadLetterExchange(Exchanges.DEAD_LETTER)
                .deadLetterRoutingKey(Queues.deadLetterOf(Queues.SAGA_REPLIES))
                .build();
    }

    @Bean
    Queue sagaRepliesDlq() {
        return QueueBuilder.durable(Queues.deadLetterOf(Queues.SAGA_REPLIES)).build();
    }

    @Bean
    Binding sagaRepliesBinding() {
        return BindingBuilder.bind(sagaRepliesQueue())
                .to(repliesExchange())
                .with(RoutingKeys.SAGA_REPLY);
    }

    @Bean
    Binding sagaRepliesDlqBinding() {
        return BindingBuilder.bind(sagaRepliesDlq())
                .to(deadLetterExchange())
                .with(Queues.deadLetterOf(Queues.SAGA_REPLIES));
    }
}
