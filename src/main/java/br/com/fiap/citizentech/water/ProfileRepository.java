package br.com.fiap.citizentech.water;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ProfileRepository extends JpaRepository<AgronomicProfile,Long> {
    boolean existsByCropAreaId(Long cropAreaId);
    Optional<AgronomicProfile> findFirstByCropAreaIdOrderByRevisionDesc(Long cropAreaId);
}
