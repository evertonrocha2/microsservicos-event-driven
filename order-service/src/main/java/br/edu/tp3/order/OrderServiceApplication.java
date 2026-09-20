package br.edu.tp3.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * order-service
 *
 * Responsabilidades:
 *  1. Expor a API REST sincrona de pedidos.
 *  2. Ser o ORQUESTRADOR da saga de criacao de pedido.
 *  3. Publicar eventos e comandos de forma transacional (padrao Outbox).
 *
 * @EnableScheduling e necessario para o OutboxPoller rodar de tempos em tempos.
 */
@SpringBootApplication
@EnableScheduling
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
