package br.com.fiap.citizentech.activity;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface ActivityRepository extends JpaRepository<FieldActivity,Long>, JpaSpecificationExecutor<FieldActivity> {
    boolean existsByCropAreaId(Long cropAreaId);
    Optional<FieldActivity> findByIdAndCropAreaId(Long id, Long cropAreaId);
}
