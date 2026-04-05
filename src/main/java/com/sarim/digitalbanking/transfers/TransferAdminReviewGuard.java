package com.sarim.digitalbanking.transfers;

import com.sarim.digitalbanking.auth.UserEntity;
import com.sarim.digitalbanking.auth.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class TransferAdminReviewGuard {

    private final UserRepository userRepository;
    private final TransferRepository transferRepository;

    public TransferAdminReviewGuard(
            UserRepository userRepository,
            TransferRepository transferRepository
    ) {
        this.userRepository = userRepository;
        this.transferRepository = transferRepository;
    }

    public UserEntity requireAdminActor(Long adminUserId) {
        UserEntity adminActor = userRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (adminActor.getRole() == null || !"ADMIN".equalsIgnoreCase(adminActor.getRole().name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }

        return adminActor;
    }

    public TransferEntity requirePendingTransferForUpdate(Long transferId) {
        TransferEntity t = transferRepository.findByIdForUpdate(transferId)
                .orElseThrow(() -> new IllegalArgumentException("transfer not found"));

        if (t.getStatus() != TransferStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "transfer is not pending review"
            );
        }

        return t;
    }
}