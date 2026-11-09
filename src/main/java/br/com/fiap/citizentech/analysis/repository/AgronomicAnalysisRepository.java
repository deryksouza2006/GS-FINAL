package br.com.fiap.citizentech.analysis.repository;

import br.com.fiap.citizentech.analysis.domain.AgronomicAnalysis;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface AgronomicAnalysisRepository extends JpaRepository<AgronomicAnalysis,Long> {
    @Query("select a.reading.id from AgronomicAnalysis a where a.status=br.com.fiap.citizentech.analysis.domain.AnalysisStatus.PROCESSING and a.startedAt<:cutoff order by a.reading.id")
    java.util.List<Long> findAbandonedReadingIds(java.time.Instant cutoff,Pageable pageable);
    Optional<AgronomicAnalysis> findByReadingId(Long readingId);
    @EntityGraph(attributePaths={"reading","cropArea"})
    Optional<AgronomicAnalysis> findByIdAndCropAreaFarmOwnerId(Long id,Long ownerId);
    @EntityGraph(attributePaths={"reading","cropArea"})
    Optional<AgronomicAnalysis> findByReadingIdAndCropAreaFarmOwnerId(Long id,Long ownerId);
    @EntityGraph(attributePaths={"reading","cropArea"})
    Page<AgronomicAnalysis> findAllByCropAreaFarmOwnerId(Long ownerId,Pageable pageable);
    @EntityGraph(attributePaths={"reading","cropArea"})
    Page<AgronomicAnalysis> findAllByCropAreaIdAndCropAreaFarmOwnerId(Long cropId,Long ownerId,Pageable pageable);
}
