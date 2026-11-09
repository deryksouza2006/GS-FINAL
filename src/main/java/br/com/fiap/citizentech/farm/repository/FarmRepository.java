package br.com.fiap.citizentech.farm.repository;

import br.com.fiap.citizentech.farm.domain.Farm;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FarmRepository extends JpaRepository<Farm, Long> {

    Page<Farm> findAllByOwnerId(Long ownerId, Pageable pageable);

    Optional<Farm> findByIdAndOwnerId(Long id, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Farm f where f.id = :id and f.owner.id = :ownerId")
    Optional<Farm> findOwnedForUpdate(Long id, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Farm f where f.id in :ids and f.owner.id = :ownerId order by f.id")
    List<Farm> findOwnedForUpdate(Collection<Long> ids, Long ownerId);
}
