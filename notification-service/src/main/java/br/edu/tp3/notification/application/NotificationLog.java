package br.edu.tp3.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * "Caixa de entrada" em memoria das notificacoes geradas.
 *
 * E proposital que este servico nao tenha banco. Ele mostra que nem todo
 * assinante de evento precisa persistir estado. Em producao aqui entraria um
 * disparo de e-mail, push ou SMS.
 */
@Component
public class NotificationLog {

    private static final Logger log = LoggerFactory.getLogger(NotificationLog.class);
    private static final int MAX_ENTRIES = 200;

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    public void record(String orderId, String text) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.remove(0);
        }
        entries.add(new Entry(orderId, text, Instant.now()));
        log.info("NOTIFICACAO orderId={} texto={}", orderId, text);
    }

    public List<Entry> all() {
        return List.copyOf(entries);
    }

    public record Entry(String orderId, String text, Instant at) {
    }
}
