package br.edu.tp3.payment.application;

import br.edu.tp3.contracts.commands.AuthorizePaymentCommand;
import br.edu.tp3.contracts.replies.SagaReply;
import br.edu.tp3.contracts.replies.SagaStep;
import br.edu.tp3.payment.domain.Payment;
import br.edu.tp3.payment.domain.PaymentRepository;
import br.edu.tp3.payment.infrastructure.idempotency.IdempotencyGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Passo da saga: autorizar o pagamento.
 *
 * Aqui esta o CONSUMIDOR IDEMPOTENTE completo. Vale acompanhar a ordem das coisas:
 *
 *   1. registra a messageId (se ja existir, e duplicata);
 *   2. aplica a regra de negocio e grava o Payment;
 *   3. os passos 1 e 2 estao na MESMA transacao, entao ou os dois valem ou nenhum vale.
 *
 * O detalhe que quase todo mundo erra: quando a mensagem e duplicata, o handler
 * NAO repete a cobranca, mas AINDA ASSIM responde. Motivo: o mais provavel e que a
 * primeira resposta tenha se perdido, e por isso o produtor reenviou. Se a duplicata
 * fosse apenas descartada em silencio, a saga ficaria travada para sempre esperando
 * uma resposta que nunca mais viria.
 *
 * Em resumo: idempotente no EFEITO COLATERAL, nao na RESPOSTA.
 */
@Service
public class AuthorizePaymentHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthorizePaymentHandler.class);

    /** Identifica este consumidor na tabela de inbox. */
    private static final String CONSUMER = "payment.authorize";

    private final PaymentRepository paymentRepository;
    private final IdempotencyGuard idempotencyGuard;

    @Value("${tp3.payment.decline-above:10000.00}")
    private BigDecimal declineAbove;

    public AuthorizePaymentHandler(PaymentRepository paymentRepository,
                                   IdempotencyGuard idempotencyGuard) {
        this.paymentRepository = paymentRepository;
        this.idempotencyGuard = idempotencyGuard;
    }

    @Transactional
    public SagaReply handle(AuthorizePaymentCommand command) {

        // ---- 1. Protecao contra mensagem duplicada -------------------------
        boolean isNew = idempotencyGuard.registerIfNew(
                CONSUMER, command.messageId(), command.orderId());

        if (!isNew) {
            // Nao cobra de novo. Apenas repete a resposta que ja foi dada antes.
            return replayPreviousReply(command);
        }

        // ---- 2. Regra de negocio -------------------------------------------
        if (command.amount().compareTo(declineAbove) > 0) {
            String reason = "limite excedido: valor " + command.amount()
                    + " acima do limite " + declineAbove;

            paymentRepository.save(Payment.declined(
                    command.orderId(), command.customerId(), command.amount(), reason));

            log.warn("pagamento RECUSADO orderId={} valor={}",
                    command.orderId(), command.amount());

            return SagaReply.failure(command.correlationId(), command.orderId(),
                    SagaStep.AUTHORIZE_PAYMENT, reason);
        }

        paymentRepository.save(Payment.authorized(
                command.orderId(), command.customerId(), command.amount()));

        log.info("pagamento AUTORIZADO orderId={} valor={}",
                command.orderId(), command.amount());

        return SagaReply.success(command.correlationId(), command.orderId(),
                SagaStep.AUTHORIZE_PAYMENT);
    }

    /**
     * Reconstroi a resposta a partir do estado ja gravado no banco.
     *
     * A resposta precisa ser a MESMA de antes. Se o pagamento foi recusado na
     * primeira vez, a duplicata tambem responde recusa. Responder coisa diferente
     * para a mesma mensagem deixaria a saga em um estado incoerente.
     */
    private SagaReply replayPreviousReply(AuthorizePaymentCommand command) {
        return paymentRepository.findByOrderId(command.orderId())
                .map(payment -> payment.isAuthorized()
                        ? SagaReply.success(command.correlationId(), command.orderId(),
                        SagaStep.AUTHORIZE_PAYMENT)
                        : SagaReply.failure(command.correlationId(), command.orderId(),
                        SagaStep.AUTHORIZE_PAYMENT, payment.getReason()))
                .orElseGet(() -> SagaReply.failure(command.correlationId(), command.orderId(),
                        SagaStep.AUTHORIZE_PAYMENT, "pagamento nao encontrado no replay"));
    }
}
