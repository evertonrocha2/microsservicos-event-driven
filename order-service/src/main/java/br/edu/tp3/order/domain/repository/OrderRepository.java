package br.edu.tp3.order.domain.repository;

import br.edu.tp3.order.domain.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String> {

    /** Reencontra a saga a partir do correlationId que veio na resposta. */
    Optional<Order> findByCorrelationId(String correlationId);
}
