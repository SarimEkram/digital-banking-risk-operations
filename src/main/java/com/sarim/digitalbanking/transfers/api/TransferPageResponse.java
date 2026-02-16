package com.sarim.digitalbanking.transfers.api;

import java.util.List;

public record TransferPageResponse(
        List<TransferResponse> items,
        String nextCursor
) {}
