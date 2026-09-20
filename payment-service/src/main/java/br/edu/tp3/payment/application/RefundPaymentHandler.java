package br.edu.tp3.payment.application;

import br.edu.tp3.contracts.commands.RefundPaymentCommand;
import br.edu.tp3.contracts.replies.SagaReply;
import br.edu.tp3.contracts.replies.SagaStep;
import br.edu.tp3.payment.domain.Payment;
import br.edu.tp3.payment.domain.PaymentRepository;
import br.edu.tp3.payment.infrastructure.idempotency.IdempotencyGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TRANSACAO COMPENSATORIA do pagamento.
 *
 * Duas caracteristicas que toda compensacao de saga precisa ter:
 *
 *  1. Ser IDEMPOTENTE. Estornar duas vezes o mesmo pagamento devolveria dinheiro
 *     a mais. A guarda de inbox evita isso.
 *
 *  2. NAO PODER FALHAR por regra de negocio. Compensacao nao pergunta, executa.
 *     Se ela pudesse ser recusada, a saga ficaria presa em um estado sem saida.
 *     Por isso, quando o pagamento nem existe, o handler responde SUCCESS: nao ha
 *     nada para desfazer, entao o objetivo da compensacao ja esta cumprido.
 */
@Service
public class RefundPaymentHandler {

    private static final Logger log = LoggerFactory.getLogger(RefundPaymentHandler.class);
    private static final String CONSUMER = "payment.refund";

    private final PaymentRepository paymentRepository;
    private final IdempotencyGuard idempotencyGuard;

    public RefundPaymentHandler(PaymentRepository paymentRepository,
                                IdempotencyGuard idempotencyGuard) {
        this.paymentRepository = paymentRepository;
        this.idempotencyGuard = idempotencyGuard;
    }

    @Transactional
    public SagaReply handle(RefundPaymentCommand command) {

        boolean isNew = idempotencyGuard.registerIfNew(
                CONSUMER, command.messageId(), command.orderId());

        if (!isNew) {
            log.info("estorno duplicado ignorado orderId={}", command.orderId());
            return SagaReply.success(command.correlationId(), command.orderId(),
                    SagaStep.REFUND_PAYMENT);
        }

        Payment payment = paymentRepository.findByOrderId(command.orderId()).orElse(null);

        if (payment == null) {
            // Nada foi cobrado, entao nada precisa ser estornado. Compensacao concluida.
            log.info("nada a estornar orderId={}", command.orderId());
            return SagaReply.success(command.correlationId(), command.orderId(),
                    SagaStep.REFUND_PAYMENT);
        }

        payment.refund(command.reason());
        log.info("pagamento ESTORNADO orderId={} valor={}",
                command.orderId(), payment.getAmount());

        return SagaReply.success(command.correlationId(), command.orderId(),
                SagaStep.REFUND_PAYMENT);
    }
}
