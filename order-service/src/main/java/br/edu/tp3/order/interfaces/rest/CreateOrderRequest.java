package br.edu.tp3.order.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Contrato de ENTRADA da API REST sincrona.
 *
 * A validacao acontece na borda, antes de chegar ao dominio. O agregado tambem
 * valida por conta propria, porque ele nao pode confiar em quem o chama.
 */
@Schema(description = "Dados para criacao de um pedido")
public record CreateOrderRequest(

        @Schema(example = "cliente-001")
        @NotBlank(message = "customerId e obrigatorio")
        String customerId,

        @NotEmpty(message = "o pedido precisa de pelo menos um item")
        @Valid
        List<Item> items
) {

    @Schema(description = "Linha do pedido")
    public record Item(

            @Schema(example = "SKU-NOTEBOOK")
            @NotBlank(message = "sku e obrigatorio")
            String sku,

            @Schema(example = "2")
            @Min(value = 1, message = "quantidade deve ser maior que zero")
            int quantity,

            @Schema(example = "3500.00")
            @NotNull(message = "unitPrice e obrigatorio")
            @DecimalMin(value = "0.01", message = "preco deve ser positivo")
            BigDecimal unitPrice
    ) {
    }
}
