package com.sarim.digitalbanking.accounts;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {
    List<AccountEntity> findByUserIdOrderByIdAsc(Long userId);
}
