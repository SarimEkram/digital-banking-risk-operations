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
    @Query("select a from AccountEntity a where a.id in :ids order by a.id")
    List<AccountEntity> findByIdInForUpdate(@Param("ids") List<Long> ids);

    // Id-only lookup so no AccountEntity enters the persistence context before the row lock is taken.
    @Query("""
            select a.id from AccountEntity a
            where a.user.id = :userId and a.accountType = :accountType
              and upper(a.currency) = upper(:currency) and upper(a.status) = upper(:status)
            """)
    Optional<Long> findIdByUserIdAndAccountTypeAndCurrencyAndStatus(
            @Param("userId") Long userId,
            @Param("accountType") AccountType accountType,
            @Param("currency") String currency,
            @Param("status") String status
    );

    // Rule A helper (for later transfer-by-payee-email): payee's ACTIVE CHEQUING account in a currency
    Optional<AccountEntity> findByUserIdAndAccountTypeAndCurrencyIgnoreCaseAndStatusIgnoreCase(
            Long userId,
            AccountType accountType,
            String currency,
            String status
    );
}
