package br.edu.tp3.contracts.events;

import br.edu.tp3.contracts.AsyncMessage;
import br.edu.tp3.contracts.OrderLine;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * EVENTO DE DOMINIO: um pedido foi criado.
 *
 * Diferenca central para um comando:
 *  - verbo no passado, o fato ja aconteceu e e imutavel;
 *  - o publicador NAO sabe quem consome;
 *  - vai por exchange TOPIC, e N assinantes recebem uma copia cada um.
 *
 * Adicionar um novo assinante (ex.: antifraude) nao muda uma linha do order-service.
 * Isso e o acoplamento fraco que justifica a arquitetura event-driven.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedEvent(
        String messageId,
        String correlationId,
        Instant occurredAt,
        String orderId,
        String customerId,
        BigDecimal totalAmount,
        List<OrderLine> items
) implements AsyncMessage {
}
