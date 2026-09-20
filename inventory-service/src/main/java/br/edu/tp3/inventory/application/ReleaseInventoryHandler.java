package br.edu.tp3.inventory.application;

import br.edu.tp3.contracts.OrderLine;
import br.edu.tp3.contracts.commands.ReleaseInventoryCommand;
import br.edu.tp3.contracts.replies.SagaReply;
import br.edu.tp3.contracts.replies.SagaStep;
import br.edu.tp3.inventory.domain.StockItemRepository;
import br.edu.tp3.inventory.infrastructure.idempotency.IdempotencyGuard;
import br.edu.tp3.inventory.infrastructure.ordering.MessageSequenceGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TRANSACAO COMPENSATORIA do estoque.
 *
 * Este e o handler onde a ordenacao realmente importa. Ele chega com
 * sequenceNumber = 2 e so pode rodar depois que a reserva (sequenceNumber = 1)
 * tiver sido processada.
 *
 * O cenario ruim que isso evita: a liberacao ser processada antes da reserva.
 * O estoque ganharia unidades que nunca sairam dele, e depois a reserva tiraria
 * de novo. O saldo final ficaria errado e ninguem perceberia ate o inventario
 * fisico nao bater.
 *
 * Compensacao nunca recusa. Ela sempre responde SUCCESS, porque a saga precisa
 * conseguir chegar ao estado CANCELLED de qualquer jeito.
 */
@Service
public class ReleaseInventoryHandler {

    private static final Logger log = LoggerFactory.getLogger(ReleaseInventoryHandler.class);
    private static final String CONSUMER = "inventory.release";

    private final StockItemRepository stockRepository;
    private final IdempotencyGuard idempotencyGuard;
    private final MessageSequenceGuard sequenceGuard;

    public ReleaseInventoryHandler(StockItemRepository stockRepository,
                                   IdempotencyGuard idempotencyGuard,
                                   MessageSequenceGuard sequenceGuard) {
        this.stockRepository = stockRepository;
        this.idempotencyGuard = idempotencyGuard;
        this.sequenceGuard = sequenceGuard;
    }

    @Transactional
    public SagaReply handle(ReleaseInventoryCommand command) {

        // Se a reserva ainda nao foi processada, isto lanca OutOfOrderMessageException
        // e a mensagem volta para a fila para tentar de novo daqui a pouco.
        boolean inOrder = sequenceGuard.checkAndAdvance(command);
        if (!inOrder) {
            return SagaReply.success(command.correlationId(), command.orderId(),
                    SagaStep.RELEASE_INVENTORY);
        }

        boolean isNew = idempotencyGuard.registerIfNew(
                CONSUMER, command.messageId(), command.orderId());
        if (!isNew) {
            log.info("liberacao duplicada ignorada orderId={}", command.orderId());
            return SagaReply.success(command.correlationId(), command.orderId(),
                    SagaStep.RELEASE_INVENTORY);
        }

        for (OrderLine line : command.items()) {
            stockRepository.findById(line.sku())
                    .ifPresent(item -> item.release(line.quantity()));
        }

        log.info("estoque LIBERADO orderId={} motivo={}", command.orderId(), command.reason());

        return SagaReply.success(command.correlationId(), command.orderId(),
                SagaStep.RELEASE_INVENTORY);
    }
}
