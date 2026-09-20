package br.edu.tp3.order.infrastructure.outbox;

import br.edu.tp3.contracts.AsyncMessage;
import br.edu.tp3.order.domain.outbox.OutboxEntry;
import br.edu.tp3.order.domain.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Grava a mensagem na tabela outbox.
 *
 * IMPORTANTE: este metodo NAO publica no broker. Ele apenas insere uma linha.
 * Como ele e chamado de dentro de um metodo @Transactional que tambem salva o
 * agregado, o insert do outbox participa da MESMA transacao do banco.
 *
 * Resultado: e impossivel o pedido existir sem a mensagem, ou a mensagem existir
 * sem o pedido. O dual write deixa de ser um problema.
 */
@Component
public class OutboxRecorder {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxRecorder(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void record(String aggregateType, String aggregateId,
                       String exchange, String routingKey, AsyncMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            OutboxEntry entry = new OutboxEntry(
                    message.messageId(),
                    aggregateType,
                    aggregateId,
                    exchange,
                    routingKey,
                    message.getClass().getSimpleName(),
                    payload);
            outboxRepository.save(entry);
        } catch (JsonProcessingException e) {
            // Falha de serializacao derruba a transacao inteira. E o correto:
            // melhor nao criar o pedido do que criar um pedido mudo.
            throw new IllegalStateException("falha ao serializar mensagem do outbox", e);
        }
    }
}
