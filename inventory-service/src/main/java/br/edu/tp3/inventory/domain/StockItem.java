package br.edu.tp3.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Agregado de estoque de um SKU.
 *
 * Invariante do agregado: available nunca pode ficar negativo. Essa regra vive
 * aqui dentro, nao no handler. O handler orquestra, o agregado protege.
 *
 * O @Version adiciona lock otimista. Se dois pedidos diferentes disputarem o mesmo
 * SKU ao mesmo tempo, uma das transacoes falha e a mensagem volta para a fila,
 * em vez de as duas gravarem por cima uma da outra e o saldo ficar errado.
 */
@Entity
@Table(name = "stock_items")
public class StockItem {

    @Id
    private String sku;

    @Column(nullable = false)
    private int available;

    @Column(nullable = false)
    private int reserved;

    @Version
    private Long version;

    protected StockItem() {
    }

    public StockItem(String sku, int available) {
        this.sku = sku;
        this.available = available;
        this.reserved = 0;
    }

    public boolean hasEnough(int quantity) {
        return available >= quantity;
    }

    public void reserve(int quantity) {
        if (!hasEnough(quantity)) {
            throw new InsufficientStockException(sku, quantity, available);
        }
        this.available -= quantity;
        this.reserved += quantity;
    }

    /**
     * COMPENSACAO: devolve ao disponivel o que estava reservado.
     *
     * Nao lanca excecao se a quantidade for maior que o reservado. Compensacao que
     * falha trava a saga, entao ela e deliberadamente tolerante: no pior caso
     * corrige para zero e segue.
     */
    public void release(int quantity) {
        int toRelease = Math.min(quantity, reserved);
        this.reserved -= toRelease;
        this.available += toRelease;
    }

    public String getSku() {
        return sku;
    }

    public int getAvailable() {
        return available;
    }

    public int getReserved() {
        return reserved;
    }
}
