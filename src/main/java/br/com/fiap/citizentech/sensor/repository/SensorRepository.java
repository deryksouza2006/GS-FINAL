package br.com.fiap.citizentech.sensor.repository;

import br.com.fiap.citizentech.sensor.domain.Sensor;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SensorRepository extends JpaRepository<Sensor, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sensor s where s.id=:id")
    Optional<Sensor> lockDevice(Long id);
    @Query("select distinct s.sensorType from Sensor s where s.cropArea.id=:cropId and s.status=br.com.fiap.citizentech.sensor.domain.SensorStatus.ACTIVE order by s.sensorType")
    java.util.List<br.com.fiap.citizentech.sensor.domain.SensorType> activeTypes(Long cropId);
    boolean existsByCropAreaId(Long cropAreaId);

    boolean existsByDeviceCodeIgnoreCase(String deviceCode);

    boolean existsByDeviceCodeIgnoreCaseAndIdNot(String deviceCode, Long id);

    @EntityGraph(attributePaths = {"cropArea", "cropArea.farm"})
    Page<Sensor> findAllByCropAreaFarmOwnerId(Long ownerId, Pageable pageable);

    @EntityGraph(attributePaths = {"cropArea", "cropArea.farm"})
    Page<Sensor> findAllByCropAreaIdAndCropAreaFarmOwnerId(Long cropAreaId, Long ownerId, Pageable pageable);

    @EntityGraph(attributePaths = {"cropArea", "cropArea.farm"})
    Optional<Sensor> findByIdAndCropAreaFarmOwnerId(Long id, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from Sensor s where s.id = :id
              and s.cropArea.id in (select c.id from CropArea c where c.farm.owner.id = :ownerId)
            """)
    Optional<Sensor> findOwnedForUpdate(Long id, Long ownerId);
}
