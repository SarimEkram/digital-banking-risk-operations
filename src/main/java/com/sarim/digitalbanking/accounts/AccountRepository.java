package com.sarim.digitalbanking.accounts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {
    List<AccountEntity> findByUserIdOrderByIdAsc(Long userId);

    Optional<AccountEntity> findByIdAndUser_Id(Long id, Long userId);
}
