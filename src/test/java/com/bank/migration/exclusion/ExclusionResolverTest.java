package com.bank.migration.exclusion;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.domain.ViewMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExclusionResolverTest {

    @Test
    void resolvesExclusionsCaseInsensitivelyAndPreservesObjectsInManifest() {
        // Arrange
        ExclusionConfig config = new ExclusionConfig(
            List.of("eba_atm"),
            List.of("v_audit"),
            List.of("audit_seq")
        );
        ExclusionResolver resolver = new ExclusionResolver(config);

        TableMetadata ebaAtm = new TableMetadata(
            "BANK_CORE",
            "EBA_ATM",
            ObjectStatus.READY,
            List.of(),
            List.of(),
            List.of()
        );
        TableMetadata customers = new TableMetadata(
            "BANK_CORE",
            "CUSTOMERS",
            ObjectStatus.READY,
            List.of(),
            List.of(),
            List.of()
        );
        ViewMetadata vAudit = new ViewMetadata(
            "BANK_CORE",
            "V_AUDIT",
            ObjectStatus.READY,
            "SELECT * FROM AUDIT",
            List.of(),
            List.of()
        );
        ViewMetadata vCustomers = new ViewMetadata(
            "BANK_CORE",
            "V_CUSTOMERS",
            ObjectStatus.READY,
            "SELECT * FROM CUSTOMERS",
            List.of(),
            List.of()
        );

        MigrationManifest manifest = new MigrationManifest(
            "run-test",
            "BANK_CORE",
            List.of(ebaAtm, customers),
            List.of(vAudit, vCustomers)
        );

        // Act
        MigrationManifest resolved = resolver.resolve(manifest);

        // Assert
        // EBA_ATM is excluded when config contains eba_atm (case-insensitive)
        TableMetadata resolvedEbaAtm = resolved.tables().stream()
            .filter(t -> t.name().equals("EBA_ATM"))
            .findFirst()
            .orElseThrow();
        assertThat(resolvedEbaAtm.status()).isEqualTo(ObjectStatus.EXCLUDED);
        assertThat(resolvedEbaAtm.exclusionReason()).isEqualTo("Excluded by configuration");

        // CUSTOMERS is not excluded when absent
        TableMetadata resolvedCustomers = resolved.tables().stream()
            .filter(t -> t.name().equals("CUSTOMERS"))
            .findFirst()
            .orElseThrow();
        assertThat(resolvedCustomers.status()).isEqualTo(ObjectStatus.READY);
        assertThat(resolvedCustomers.exclusionReason()).isNull();

        // V_AUDIT is excluded when config contains v_audit (case-insensitive)
        ViewMetadata resolvedVAudit = resolved.views().stream()
            .filter(v -> v.name().equals("V_AUDIT"))
            .findFirst()
            .orElseThrow();
        assertThat(resolvedVAudit.status()).isEqualTo(ObjectStatus.EXCLUDED);
        assertThat(resolvedVAudit.exclusionReason()).isEqualTo("Excluded by configuration");

        // V_CUSTOMERS is not excluded when absent
        ViewMetadata resolvedVCustomers = resolved.views().stream()
            .filter(v -> v.name().equals("V_CUSTOMERS"))
            .findFirst()
            .orElseThrow();
        assertThat(resolvedVCustomers.status()).isEqualTo(ObjectStatus.READY);
        assertThat(resolvedVCustomers.exclusionReason()).isNull();

        // All objects remain visible in the manifest
        assertThat(resolved.tables()).hasSize(2);
        assertThat(resolved.views()).hasSize(2);
    }

    @Test
    void marksViewsReferencesExcludedTablesAsNeedsReviewWithReason() {
        ExclusionConfig config = new ExclusionConfig(
            List.of("EBA_ATM"),
            List.of(),
            List.of()
        );
        ExclusionResolver resolver = new ExclusionResolver(config);

        TableMetadata ebaAtm = new TableMetadata(
            "BANK_CORE",
            "EBA_ATM",
            ObjectStatus.READY,
            List.of(),
            List.of(),
            List.of()
        );
        ViewMetadata vAtm = new ViewMetadata(
            "BANK_CORE",
            "V_ATM",
            ObjectStatus.READY,
            "SELECT * FROM EBA_ATM",
            List.of("EBA_ATM"),
            List.of()
        );

        MigrationManifest manifest = new MigrationManifest(
            "run-test-2",
            "BANK_CORE",
            List.of(ebaAtm),
            List.of(vAtm)
        );

        MigrationManifest resolved = resolver.resolve(manifest);

        ViewMetadata resolvedVAtm = resolved.views().stream()
            .filter(v -> v.name().equals("V_ATM"))
            .findFirst()
            .orElseThrow();
        assertThat(resolvedVAtm.status()).isEqualTo(ObjectStatus.NEEDS_REVIEW);
        assertThat(resolvedVAtm.exclusionReason()).isEqualTo("References excluded table(s): EBA_ATM");
        assertThat(resolvedVAtm.notes()).contains("References excluded table(s): EBA_ATM");
    }

    @Test
    void resolvesSequenceExclusionsCaseInsensitivelyAndPreservesSchemaObjects() {
        // Arrange
        ExclusionConfig config = new ExclusionConfig(
            List.of(),
            List.of(),
            List.of("my_SEQ")
        );
        ExclusionResolver resolver = new ExclusionResolver(config);

        com.bank.migration.domain.SchemaObjectMetadata mySeq = new com.bank.migration.domain.SchemaObjectMetadata(
            "BANK_CORE",
            "MY_SEQ",
            com.bank.migration.domain.SchemaObjectType.SEQUENCE,
            ObjectStatus.READY,
            List.of("Initial test notes")
        );
        com.bank.migration.domain.SchemaObjectMetadata otherSeq = new com.bank.migration.domain.SchemaObjectMetadata(
            "BANK_CORE",
            "OTHER_SEQ",
            com.bank.migration.domain.SchemaObjectType.SEQUENCE,
            ObjectStatus.READY,
            List.of()
        );
        com.bank.migration.domain.SchemaObjectMetadata synonymObj = new com.bank.migration.domain.SchemaObjectMetadata(
            "BANK_CORE",
            "MY_SYN",
            com.bank.migration.domain.SchemaObjectType.SYNONYM,
            ObjectStatus.READY,
            List.of()
        );

        MigrationManifest manifest = new MigrationManifest(
            "run-seq-test",
            "BANK_CORE",
            List.of(),
            List.of(),
            List.of(mySeq, otherSeq, synonymObj)
        );

        // Act
        MigrationManifest resolved = resolver.resolve(manifest);

        // Assert
        assertThat(resolved.schemaObjects()).hasSize(3);

        com.bank.migration.domain.SchemaObjectMetadata resolvedMySeq = resolved.schemaObjects().stream()
            .filter(obj -> obj.name().equals("MY_SEQ"))
            .findFirst()
            .orElseThrow();
        assertThat(resolvedMySeq.status()).isEqualTo(ObjectStatus.EXCLUDED);
        assertThat(resolvedMySeq.exclusionReason()).isEqualTo("Excluded by configuration");
        assertThat(resolvedMySeq.notes()).containsExactly("Initial test notes");

        com.bank.migration.domain.SchemaObjectMetadata resolvedOtherSeq = resolved.schemaObjects().stream()
            .filter(obj -> obj.name().equals("OTHER_SEQ"))
            .findFirst()
            .orElseThrow();
        assertThat(resolvedOtherSeq.status()).isEqualTo(ObjectStatus.READY);
        assertThat(resolvedOtherSeq.exclusionReason()).isNull();

        com.bank.migration.domain.SchemaObjectMetadata resolvedSynonym = resolved.schemaObjects().stream()
            .filter(obj -> obj.name().equals("MY_SYN"))
            .findFirst()
            .orElseThrow();
        assertThat(resolvedSynonym.status()).isEqualTo(ObjectStatus.READY);
        assertThat(resolvedSynonym.exclusionReason()).isNull();
    }
}
