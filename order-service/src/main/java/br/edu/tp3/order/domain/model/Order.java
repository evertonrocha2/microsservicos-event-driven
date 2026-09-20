package br.edu.tp3.order.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AGREGADO Order.
 *
 * E a raiz de consistencia local: tudo aqui dentro muda em uma unica transacao ACID
 * no banco do order-service. O que esta FORA deste agregado (pagamento, estoque)
 * so muda por mensagem, e a consistencia passa a ser EVENTUAL.
 *
 * Essa fronteira e exatamente o motivo de existir a Saga.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private String id;

    @Column(nullable = false)
    private String customerId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    /** Amarra todas as mensagens desta saga. Vai junto em cada comando e resposta. */
    @Column(nullable = false)
    private String correlationId;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    /**
     * Lock otimista. Se duas respostas da saga chegarem ao mesmo tempo para o mesmo
     * pedido, uma das transacoes falha e a mensagem volta para a fila em vez de
     * sobrescrever o estado da outra.
     */
    @Version
    private Long version;

    protected Order() {
    }

    private Order(String customerId, List<OrderItem> items) {
        this.id = UUID.randomUUID().toString();
        this.correlationId = UUID.randomUUID().toString();
        this.customerId = customerId;
        this.items = new ArrayList<>(items);
        this.totalAmount = items.stream()
                .map(OrderItem::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
    }

    /** Fabrica do agregado. Toda regra de criacao vive aqui, nao no controller. */
    public static Order place(String customerId, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("pedido precisa de pelo menos um item");
        }
        return new Order(customerId, items);
    }

    // ---------------- Transicoes de estado da saga ----------------

    public void inventoryReserved() {
        requireStatus(OrderStatus.PENDING);
        this.status = OrderStatus.INVENTORY_RESERVED;
        touch();
    }

    public void paymentAuthorized() {
        requireStatus(OrderStatus.INVENTORY_RESERVED);
        this.status = OrderStatus.PAYMENT_AUTHORIZED;
        touch();
    }

    public void confirm() {
        requireStatus(OrderStatus.PAYMENT_AUTHORIZED);
        this.status = OrderStatus.CONFIRMED;
        touch();
    }

    /** Entra em compensacao: os passos ja concluidos precisam ser desfeitos. */
    public void startCompensation(String reason) {
        if (status.isFinal()) {
            return;
        }
        this.status = OrderStatus.COMPENSATING;
        this.failureReason = reason;
        touch();
    }

    public void cancel(String reason) {
        if (this.status == OrderStatus.CANCELLED) {
            return;
        }
        this.status = OrderStatus.CANCELLED;
        if (this.failureReason == null) {
            this.failureReason = reason;
        }
        touch();
    }

    private void requireStatus(OrderStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "transicao invalida: esperado " + expected + " mas o pedido esta em " + this.status);
        }
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    // ---------------- Getters ----------------

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
