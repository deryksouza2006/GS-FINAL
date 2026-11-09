package br.com.fiap.citizentech.croparea.repository;

import br.com.fiap.citizentech.croparea.domain.CropArea;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Optional;

public interface CropAreaRepository extends JpaRepository<CropArea, Long> {

    boolean existsByFarmId(Long farmId);

    @EntityGraph(attributePaths = "farm")
    Page<CropArea> findAllByFarmOwnerId(Long ownerId, Pageable pageable);

    @EntityGraph(attributePaths = "farm")
    Optional<CropArea> findByIdAndFarmOwnerId(Long id, Long ownerId);

    @EntityGraph(attributePaths = "farm")
    Page<CropArea> findAllByFarmIdAndFarmOwnerId(Long farmId, Long ownerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from CropArea c where c.id = :id
              and c.farm.id in (select f.id from Farm f where f.owner.id = :ownerId)
            """)
    Optional<CropArea> findOwnedForUpdate(Long id, Long ownerId);

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN area_unit = 'HECTARE'
                THEN area_size * 10000 ELSE area_size END), 0)
            FROM crop_areas
            WHERE farm_id = :farmId
              AND (CAST(:excludedId AS BIGINT) IS NULL OR id <> :excludedId)
            """, nativeQuery = true)
    BigDecimal sumSquareMeters(Long farmId, Long excludedId);
}
