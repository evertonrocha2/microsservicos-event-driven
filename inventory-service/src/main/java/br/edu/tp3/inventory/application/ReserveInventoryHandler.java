package br.edu.tp3.inventory.application;

import br.edu.tp3.contracts.OrderLine;
import br.edu.tp3.contracts.commands.ReserveInventoryCommand;
import br.edu.tp3.contracts.replies.SagaReply;
import br.edu.tp3.contracts.replies.SagaStep;
import br.edu.tp3.inventory.domain.InsufficientStockException;
import br.edu.tp3.inventory.domain.StockItem;
import br.edu.tp3.inventory.domain.StockItemRepository;
import br.edu.tp3.inventory.infrastructure.idempotency.IdempotencyGuard;
import br.edu.tp3.inventory.infrastructure.ordering.MessageSequenceGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passo da saga: reservar estoque.
 *
 * Este handler junta as TRES protecoes do consumo assincrono, nesta ordem:
 *
 *   1. ORDEM        -> MessageSequenceGuard: e a minha vez de processar?
 *   2. IDEMPOTENCIA -> IdempotencyGuard: eu ja processei exatamente esta mensagem?
 *   3. NEGOCIO      -> o agregado StockItem decide se tem saldo.
 *
 * As tres rodam na MESMA transacao. Se o passo 3 falhar por erro tecnico, o
 * rollback desfaz tambem o avanco da sequencia e a marca de idempotencia. Nada
 * fica inconsistente, e a reentrega volta tudo ao ponto de partida.
 */
@Service
public class ReserveInventoryHandler {

    private static final Logger log = LoggerFactory.getLogger(ReserveInventoryHandler.class);
    private static final String CONSUMER = "inventory.reserve";

    private final StockItemRepository stockRepository;
    private final IdempotencyGuard idempotencyGuard;
    private final MessageSequenceGuard sequenceGuard;

    public ReserveInventoryHandler(StockItemRepository stockRepository,
                                   IdempotencyGuard idempotencyGuard,
                                   MessageSequenceGuard sequenceGuard) {
        this.stockRepository = stockRepository;
        this.idempotencyGuard = idempotencyGuard;
        this.sequenceGuard = sequenceGuard;
    }

    @Transactional
    public SagaReply handle(ReserveInventoryCommand command) {

        // ---- 1. Esta mensagem esta na vez? ---------------------------------
        // Se estiver adiantada, o guard lanca OutOfOrderMessageException e o
        // listener devolve a mensagem para a fila.
        boolean inOrder = sequenceGuard.checkAndAdvance(command);
        if (!inOrder) {
            log.info("comando antigo ignorado orderId={}", command.orderId());
            return SagaReply.success(command.correlationId(), command.orderId(),
                    SagaStep.RESERVE_INVENTORY);
        }

        // ---- 2. Ja processei exatamente esta mensagem? ----------------------
        boolean isNew = idempotencyGuard.registerIfNew(
                CONSUMER, command.messageId(), command.orderId());
        if (!isNew) {
            log.info("reserva duplicada, nao reserva de novo orderId={}", command.orderId());
            return SagaReply.success(command.correlationId(), command.orderId(),
                    SagaStep.RESERVE_INVENTORY);
        }

        // ---- 3. Regra de negocio -------------------------------------------
        try {
            for (OrderLine line : command.items()) {
                StockItem item = stockRepository.findById(line.sku())
                        .orElseThrow(() -> new InsufficientStockException(line.sku(),
                                line.quantity(), 0));
                item.reserve(line.quantity());
            }

            log.info("estoque RESERVADO orderId={} itens={}",
                    command.orderId(), command.items().size());

            return SagaReply.success(command.correlationId(), command.orderId(),
                    SagaStep.RESERVE_INVENTORY);

        } catch (InsufficientStockException e) {
            // Falha de NEGOCIO. A mensagem foi processada. A resposta e "nao deu".
            // Quem decide o que fazer com isso e a saga, nao este servico.
            log.warn("reserva NEGADA orderId={} motivo={}", command.orderId(), e.getMessage());

            // rollback manual do que ja foi reservado neste comando: como tudo
            // esta na mesma transacao, basta sinalizar para nao comitar.
            throw new BusinessRejection(SagaReply.failure(command.correlationId(),
                    command.orderId(), SagaStep.RESERVE_INVENTORY, e.getMessage()));
        }
    }
}
