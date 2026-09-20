package br.edu.tp3.order.domain.repository;

import br.edu.tp3.order.domain.outbox.OutboxEntry;
import br.edu.tp3.order.domain.outbox.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;

import jakarta.persistence.QueryHint;

public interface OutboxRepository extends JpaRepository<OutboxEntry, String> {

    /**
     * Le o proximo lote pendente em ORDEM DE CRIACAO.
     *
     * Tres detalhes importantes:
     *  - "order by createdAt asc" preserva a ordem em que as mensagens foram produzidas;
     *  - PESSIMISTIC_WRITE impede que duas instancias do order-service publiquem a
     *    mesma linha ao mesmo tempo, o que geraria duplicata desnecessaria;
     *  - o hint com valor -2 e o SKIP_LOCKED do Hibernate: em vez de esperar o lock
     *    da outra instancia, esta simplesmente pula a linha e pega a proxima.
     *    Assim varias instancias escalam em paralelo sem se atrapalhar.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select o from OutboxEntry o where o.status = :status order by o.createdAt asc")
    List<OutboxEntry> findBatchForPublishing(@Param("status") OutboxStatus status, Pageable pageable);
}
