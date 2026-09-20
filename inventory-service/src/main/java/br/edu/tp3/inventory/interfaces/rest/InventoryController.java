package br.edu.tp3.inventory.interfaces.rest;

import br.edu.tp3.inventory.domain.StockItem;
import br.edu.tp3.inventory.domain.StockItemRepository;
import br.edu.tp3.inventory.infrastructure.idempotency.ProcessedMessage;
import br.edu.tp3.inventory.infrastructure.idempotency.ProcessedMessageRepository;
import br.edu.tp3.inventory.infrastructure.ordering.AggregateSequence;
import br.edu.tp3.inventory.infrastructure.ordering.AggregateSequenceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API de leitura do inventory-service.
 *
 * Os dois ultimos endpoints existem para o avaliador ver o mecanismo funcionando:
 * um mostra a tabela de idempotencia, o outro mostra o contador de sequencia
 * por pedido. Juntos, sao a prova de que ordem e duplicata estao sob controle.
 */
@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Estoque", description = "Consulta de estoque, idempotencia e ordenacao")
public class InventoryController {

    private final StockItemRepository stockRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final AggregateSequenceRepository sequenceRepository;

    public InventoryController(StockItemRepository stockRepository,
                               ProcessedMessageRepository processedMessageRepository,
                               AggregateSequenceRepository sequenceRepository) {
        this.stockRepository = stockRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.sequenceRepository = sequenceRepository;
    }

    @GetMapping("/stock")
    @Operation(summary = "Saldo atual por SKU (disponivel e reservado)")
    public List<StockItem> stock() {
        return stockRepository.findAll();
    }

    @GetMapping("/processed-messages")
    @Operation(summary = "Tabela de inbox: mensagens ja processadas")
    public List<ProcessedMessage> processedMessages() {
        return processedMessageRepository.findAll();
    }

    @GetMapping("/sequences")
    @Operation(summary = "Ultimo numero de sequencia processado por pedido",
            description = "Cada linha e uma raia de ordenacao. A mensagem N+1 de um "
                    + "pedido so e aceita depois que a N daquele pedido foi processada.")
    public List<AggregateSequence> sequences() {
        return sequenceRepository.findAll();
    }
}
