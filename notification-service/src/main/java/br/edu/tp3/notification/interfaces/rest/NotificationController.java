package br.edu.tp3.notification.interfaces.rest;

import br.edu.tp3.notification.application.NotificationLog;
import br.edu.tp3.notification.infrastructure.messaging.AnalyticsEventListener;
import br.edu.tp3.notification.infrastructure.sync.SyncOrderClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Endpoints de demonstracao.
 *
 * O experimento que vale a pena rodar na apresentacao:
 *
 *   1. derrube o order-service;
 *   2. chame GET /api/notifications/sync/{orderId} -> falha em segundos (503);
 *   3. crie pedidos (nao vai conseguir, o order-service esta fora);
 *   4. suba o order-service de novo, crie pedidos, derrube o notification-service;
 *   5. crie mais pedidos: eles funcionam, e as mensagens se acumulam na fila;
 *   6. suba o notification-service: ele consome tudo o que ficou acumulado.
 *
 * Os passos 2 e 6 mostram a diferenca entre os dois modelos melhor que qualquer
 * diagrama: o sincrono falha junto, o assincrono espera.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notificacoes", description = "Assinante de eventos e demo de chamada sincrona")
public class NotificationController {

    private final NotificationLog notificationLog;
    private final AnalyticsEventListener analytics;
    private final SyncOrderClient syncOrderClient;

    public NotificationController(NotificationLog notificationLog,
                                  AnalyticsEventListener analytics,
                                  SyncOrderClient syncOrderClient) {
        this.notificationLog = notificationLog;
        this.analytics = analytics;
        this.syncOrderClient = syncOrderClient;
    }

    @GetMapping
    @Operation(summary = "ASSINCRONO: notificacoes geradas a partir dos eventos consumidos")
    public List<NotificationLog.Entry> notifications() {
        return notificationLog.all();
    }

    @GetMapping("/analytics")
    @Operation(summary = "ASSINCRONO: contagem de eventos do segundo assinante",
            description = "Prova do publish/subscribe: os mesmos eventos chegaram "
                    + "em duas filas independentes.")
    public Map<String, Long> analytics() {
        return analytics.snapshot();
    }

    @GetMapping("/sync/{orderId}")
    @Operation(summary = "SINCRONO: busca o pedido chamando o order-service por HTTP",
            description = "Bloqueia esperando a resposta. Se o order-service estiver "
                    + "fora do ar, esta chamada falha imediatamente.")
    public ResponseEntity<?> syncFetch(@PathVariable String orderId) {
        try {
            return ResponseEntity.ok(syncOrderClient.fetchOrder(orderId));
        } catch (RestClientException e) {
            // A falha do outro servico virou falha desta requisicao.
            // Acoplamento temporal na pratica.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "erro", "order-service indisponivel ou pedido inexistente",
                            "detalhe", String.valueOf(e.getMessage())));
        }
    }
}
