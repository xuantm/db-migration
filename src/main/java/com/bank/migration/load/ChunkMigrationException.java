package com.bank.migration.load;

import com.bank.migration.audit.ErrorRecord;

public class ChunkMigrationException extends RuntimeException {
    private final ErrorRecord errorRecord;

    public ChunkMigrationException(ErrorRecord errorRecord, RuntimeException cause) {
        super(cause.getMessage(), cause);
        this.errorRecord = errorRecord;
    }

    public ErrorRecord errorRecord() {
        return errorRecord;
    }
}
