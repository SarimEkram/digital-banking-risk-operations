package com.sarim.digitalbanking.transfers;

import com.sarim.digitalbanking.accounts.AccountEntity;
import com.sarim.digitalbanking.accounts.AccountRepository;
import com.sarim.digitalbanking.ledger.LedgerDirection;
import com.sarim.digitalbanking.ledger.LedgerEntryEntity;
import com.sarim.digitalbanking.ledger.LedgerEntryRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransferSettlementService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public TransferSettlementService(
            AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository
    ) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public void applyHeldTransferReserve(
            TransferEntity transfer,
            AccountEntity from,
            long amountCents,
            String currency
    ) {
        LedgerEntryEntity debit = new LedgerEntryEntity();
        debit.setTransfer(transfer);
        debit.setAccount(from);
        debit.setDirection(LedgerDirection.DEBIT);
        debit.setAmountCents(amountCents);
        debit.setCurrency(currency);

        ledgerEntryRepository.save(debit);

        from.setBalanceCents(from.getBalanceCents() - amountCents);
        accountRepository.save(from);
    }

    public void applyHeldTransferApprovalCredit(
            TransferEntity transfer,
            AccountEntity to,
            long amountCents,
            String currency
    ) {
        LedgerEntryEntity credit = new LedgerEntryEntity();
        credit.setTransfer(transfer);
        credit.setAccount(to);
        credit.setDirection(LedgerDirection.CREDIT);
        credit.setAmountCents(amountCents);
        credit.setCurrency(currency);

        ledgerEntryRepository.save(credit);

        to.setBalanceCents(to.getBalanceCents() + amountCents);
        accountRepository.save(to);
    }

    public void applyHeldTransferRefund(
            TransferEntity transfer,
            AccountEntity from,
            long amountCents,
            String currency
    ) {
        LedgerEntryEntity refund = new LedgerEntryEntity();
        refund.setTransfer(transfer);
        refund.setAccount(from);
        refund.setDirection(LedgerDirection.CREDIT);
        refund.setAmountCents(amountCents);
        refund.setCurrency(currency);

        ledgerEntryRepository.save(refund);

        from.setBalanceCents(from.getBalanceCents() + amountCents);
        accountRepository.save(from);
    }

    public void applyLedgerAndBalances(
            TransferEntity transfer,
            AccountEntity from,
            AccountEntity to,
            long amountCents,
            String currency
    ) {
        LedgerEntryEntity debit = new LedgerEntryEntity();
        debit.setTransfer(transfer);
        debit.setAccount(from);
        debit.setDirection(LedgerDirection.DEBIT);
        debit.setAmountCents(amountCents);
        debit.setCurrency(currency);

        LedgerEntryEntity credit = new LedgerEntryEntity();
        credit.setTransfer(transfer);
        credit.setAccount(to);
        credit.setDirection(LedgerDirection.CREDIT);
        credit.setAmountCents(amountCents);
        credit.setCurrency(currency);

        ledgerEntryRepository.saveAll(List.of(debit, credit));

        from.setBalanceCents(from.getBalanceCents() - amountCents);
        to.setBalanceCents(to.getBalanceCents() + amountCents);
        accountRepository.saveAll(List.of(from, to));
    }
}