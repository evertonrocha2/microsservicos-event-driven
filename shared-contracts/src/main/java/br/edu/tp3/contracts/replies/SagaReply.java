package br.edu.tp3.contracts.replies;

import br.edu.tp3.contracts.AsyncMessage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Resposta assincrona de um participante da saga para o orquestrador.
 *
 * Este e o estilo de interacao REQUEST/ASYNC RESPONSE:
 * o order-service manda o comando e NAO fica bloqueado esperando. Ele volta a
 * trabalhar. Quando a resposta chega nesta fila, a saga continua de onde parou.
 * O correlationId e o que permite reencontrar a saga certa.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SagaReply(
        String messageId,
        String correlationId,
        Instant occurredAt,
        String orderId,
        SagaStep step,
        ReplyOutcome outcome,
        String reason
) implements AsyncMessage {

    public boolean succeeded() {
        return outcome == ReplyOutcome.SUCCESS;
    }

    public static SagaReply success(String correlationId, String orderId, SagaStep step) {
        return new SagaReply(java.util.UUID.randomUUID().toString(), correlationId,
                Instant.now(), orderId, step, ReplyOutcome.SUCCESS, null);
    }

    public static SagaReply failure(String correlationId, String orderId, SagaStep step, String reason) {
        return new SagaReply(java.util.UUID.randomUUID().toString(), correlationId,
                Instant.now(), orderId, step, ReplyOutcome.FAILURE, reason);
    }
}
