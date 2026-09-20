package br.edu.tp3.payment.interfaces.rest;

import br.edu.tp3.payment.domain.Payment;
import br.edu.tp3.payment.domain.PaymentRepository;
import br.edu.tp3.payment.infrastructure.idempotency.ProcessedMessage;
import br.edu.tp3.payment.infrastructure.idempotency.ProcessedMessageRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API de leitura do payment-service.
 *
 * Existe por dois motivos:
 *  - dar ao avaliador uma forma simples de conferir o que aconteceu;
 *  - expor a tabela de inbox, onde da para ver as mensagens ja processadas e
 *    comprovar na pratica que uma duplicata nao virou cobranca nova.
 */
@RestController
@RequestMapping("/api/payments")
@Tag(name = "Pagamentos", description = "Consulta de pagamentos e da tabela de idempotencia")
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final ProcessedMessageRepository processedMessageRepository;

    public PaymentController(PaymentRepository paymentRepository,
                             ProcessedMessageRepository processedMessageRepository) {
        this.paymentRepository = paymentRepository;
        this.processedMessageRepository = processedMessageRepository;
    }

    @GetMapping
    @Operation(summary = "Lista todos os pagamentos")
    public List<Payment> findAll() {
        return paymentRepository.findAll();
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Busca o pagamento de um pedido")
    public ResponseEntity<Payment> findByOrder(@PathVariable String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/processed-messages")
    @Operation(summary = "Mensagens ja processadas (tabela de inbox)",
            description = "Prova de que a idempotencia esta ativa: cada messageId "
                    + "aparece uma unica vez, mesmo se a mensagem chegou varias vezes.")
    public List<ProcessedMessage> processedMessages() {
        return processedMessageRepository.findAll();
    }
}
