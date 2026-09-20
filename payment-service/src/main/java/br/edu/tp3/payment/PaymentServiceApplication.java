package br.edu.tp3.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * payment-service
 *
 * Participante da saga. Consome comandos de cobranca e estorno e devolve
 * SagaReply ao orquestrador. Demonstra CONSUMIDOR IDEMPOTENTE com tabela de inbox.
 */
@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
