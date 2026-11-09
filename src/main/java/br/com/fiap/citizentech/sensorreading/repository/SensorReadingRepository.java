package br.com.fiap.citizentech.sensorreading.repository;

import br.com.fiap.citizentech.sensorreading.domain.SensorReading;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {
    boolean existsBySensorId(Long sensorId);

    @EntityGraph(attributePaths = {"sensor", "sensor.cropArea", "sensor.cropArea.farm"})
    Page<SensorReading> findAllBySensorCropAreaFarmOwnerId(Long ownerId, Pageable pageable);
    @EntityGraph(attributePaths = {"sensor", "sensor.cropArea", "sensor.cropArea.farm"})
    Page<SensorReading> findAllBySensorIdAndSensorCropAreaFarmOwnerId(Long sensorId, Long ownerId, Pageable pageable);
    @EntityGraph(attributePaths = {"sensor", "sensor.cropArea", "sensor.cropArea.farm"})
    Page<SensorReading> findAllBySensorCropAreaIdAndSensorCropAreaFarmOwnerId(Long cropAreaId, Long ownerId, Pageable pageable);
    @EntityGraph(attributePaths = {"sensor", "sensor.cropArea", "sensor.cropArea.farm"})
    Optional<SensorReading> findByIdAndSensorCropAreaFarmOwnerId(Long id, Long ownerId);
    @EntityGraph(attributePaths = {"sensor", "sensor.cropArea", "sensor.cropArea.farm"})
    Optional<SensorReading> findFirstBySensorCropAreaIdAndSensorCropAreaFarmOwnerIdOrderByMeasuredAtDescIdDesc(Long cropAreaId, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from SensorReading r where r.id = :id and r.sensor.id in
              (select s.id from Sensor s where s.cropArea.farm.owner.id = :ownerId)
            """)
    Optional<SensorReading> findOwnedForUpdate(Long id, Long ownerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SensorReading r where r.id = :id")
    Optional<SensorReading> findForUpdate(Long id);
}
