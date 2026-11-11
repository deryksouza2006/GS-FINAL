package br.com.fiap.citizentech.water;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface RiskJobRepository extends JpaRepository<RiskJob,Long> {
    Optional<RiskJob> findByIdAndOwnerId(Long id,Long ownerId);
    Page<RiskJob> findAllByCropAreaIdAndOwnerId(Long area,Long owner,Pageable pageable);
    Page<RiskJob> findAllByOwnerIdAndAlertStatusIsNotNull(Long owner,Pageable pageable);
    Page<RiskJob> findAllByCropAreaIdAndOwnerIdAndAlertStatusIsNotNull(Long area, Long owner, Pageable pageable);
    boolean existsByCropAreaId(Long area);
    @Query(value="""
            SELECT j.* FROM risk_jobs j WHERE j.status='QUEUED'
              AND (SELECT max(e.published_at) FROM outbox_events e WHERE e.aggregate_type='RiskAnalysisJob'
                   AND e.aggregate_id=j.id) < :cutoff
            ORDER BY j.id LIMIT 20 FOR UPDATE SKIP LOCKED
            """,nativeQuery=true)
    java.util.List<RiskJob> lockAbandoned(java.time.Instant cutoff);
    Page<RiskJob> findAllByOwnerIdAndAlertStatusIn(Long owner, java.util.Collection<String> states, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select j from RiskJob j where j.id=:id")
    Optional<RiskJob> lock(Long id);
}
