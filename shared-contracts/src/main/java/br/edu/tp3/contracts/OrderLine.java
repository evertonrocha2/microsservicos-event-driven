package br.edu.tp3.contracts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Item de pedido trafegado nas mensagens.
 *
 * Repare que este NAO e a entidade OrderItem do order-service. E uma copia magra,
 * so com o que o outro contexto precisa saber. No DDD isso e a Published Language:
 * o modelo interno pode mudar sem quebrar quem consome as mensagens.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderLine(
        String sku,
        int quantity,
        BigDecimal unitPrice
) {
    public BigDecimal total() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
