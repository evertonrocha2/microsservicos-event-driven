package br.edu.tp3.inventory;

import br.edu.tp3.inventory.domain.StockItem;
import br.edu.tp3.inventory.domain.StockItemRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * inventory-service
 *
 * Participante da saga. E o servico que demonstra:
 *  - GARANTIA DE ORDEM (MessageSequenceGuard + consumidor unico ativo);
 *  - IDEMPOTENCIA (tabela de inbox);
 *  - COMPENSACAO (liberar o estoque reservado).
 */
@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    /** Carga inicial para a demonstracao. */
    @Bean
    CommandLineRunner seedStock(StockItemRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            repository.saveAll(List.of(
                    new StockItem("SKU-NOTEBOOK", 50),
                    new StockItem("SKU-MOUSE", 200),
                    new StockItem("SKU-TECLADO", 120),
                    new StockItem("SKU-MONITOR", 30),
                    new StockItem("SKU-RARO", 1)));
        };
    }
}
