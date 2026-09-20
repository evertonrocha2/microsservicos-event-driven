package br.edu.tp3.payment.infrastructure.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String> {
}
