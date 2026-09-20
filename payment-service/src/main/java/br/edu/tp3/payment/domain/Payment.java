package br.edu.tp3.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Agregado Payment, dono do seu proprio banco (payment_db).
 *
 * O order-service NUNCA le esta tabela. Se quiser saber do pagamento, pergunta
 * por mensagem ou por API. Esse isolamento e o que permite trocar o banco daqui
 * sem quebrar ninguem, e e a regra basica do "database per service".
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 300)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt;

    protected Payment() {
    }

    private Payment(String orderId, String customerId, BigDecimal amount,
                    PaymentStatus status, String reason) {
        this.id = UUID.randomUUID().toString();
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.status = status;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public static Payment authorized(String orderId, String customerId, BigDecimal amount) {
        return new Payment(orderId, customerId, amount, PaymentStatus.AUTHORIZED, null);
    }

    public static Payment declined(String orderId, String customerId,
                                   BigDecimal amount, String reason) {
        return new Payment(orderId, customerId, amount, PaymentStatus.DECLINED, reason);
    }

    /**
     * COMPENSACAO. Repare que nao apagamos o registro da cobranca.
     * O estorno e um novo fato de negocio, e o historico continua auditavel.
     */
    public void refund(String reason) {
        this.status = PaymentStatus.REFUNDED;
        this.reason = reason;
    }

    public boolean isAuthorized() {
        return status == PaymentStatus.AUTHORIZED;
    }

    public String getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
