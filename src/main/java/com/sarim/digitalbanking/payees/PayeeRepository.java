package com.sarim.digitalbanking.payees;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayeeRepository extends JpaRepository<PayeeEntity, Long> {

    List<PayeeEntity> findByOwnerUserIdAndStatusOrderByIdAsc(Long ownerUserId, String status);

    Optional<PayeeEntity> findByOwnerUserIdAndPayeeUserId(Long ownerUserId, Long payeeUserId);

    Optional<PayeeEntity> findByIdAndOwnerUser_Id(Long id, Long ownerUserId);
}
