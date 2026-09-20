package br.edu.tp3.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * notification-service
 *
 * Serve para comparar os dois mundos lado a lado:
 *
 *  ASSINCRONO -> OrderEventListener e AnalyticsEventListener, dois assinantes
 *                independentes do mesmo evento (publish/subscribe).
 *
 *  SINCRONO   -> SyncOrderClient, chamada HTTP bloqueante ao order-service.
 *
 * O contraste fica evidente ao derrubar o order-service: os listeners continuam
 * consumindo o que ja esta na fila, a chamada sincrona quebra na hora.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
