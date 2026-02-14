package com.sarim.digitalbanking.accounts;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {
    List<AccountEntity> findByUserIdOrderByIdAsc(Long userId);

    Optional<AccountEntity> findByIdAndUser_Id(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where a.id in :ids")
    List<AccountEntity> findByIdInForUpdate(@Param("ids") List<Long> ids);

    // Rule A helper (for later transfer-by-payee-email): payee's ACTIVE CHEQUING account in a currency
    Optional<AccountEntity> findByUserIdAndAccountTypeAndCurrencyIgnoreCaseAndStatusIgnoreCase(
            Long userId,
            AccountType accountType,
            String currency,
            String status
    );
}
