package br.edu.tp3.inventory.infrastructure.ordering;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface AggregateSequenceRepository extends JpaRepository<AggregateSequence, String> {

    /**
     * Le a linha do agregado com LOCK PESSIMISTA (select ... for update).
     *
     * Sem este lock, duas threads poderiam ler lastSequence = 1 ao mesmo tempo e
     * as duas aceitariam a mensagem 2. O lock serializa o acesso por pedido,
     * e so por pedido: pedidos diferentes continuam rodando em paralelo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AggregateSequence> findByAggregateId(String aggregateId);
}
