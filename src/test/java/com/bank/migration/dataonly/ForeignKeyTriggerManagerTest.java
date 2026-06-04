package com.bank.migration.dataonly;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierRenderer;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class ForeignKeyTriggerManagerTest {
    @Mock JdbcTemplate targetJdbc;
    @Mock IdentifierRenderer targetRenderer;

    @BeforeEach
    void setUp() {
        lenient().when(targetRenderer.renderQualifiedName(anyString(), anyString())).thenAnswer(inv ->
            inv.getArgument(0, String.class).toLowerCase(Locale.ROOT) + "." + inv.getArgument(1, String.class).toLowerCase(Locale.ROOT)
        );
        lenient().when(targetRenderer.physicalName(anyString())).thenAnswer(inv ->
            inv.getArgument(0, String.class).toLowerCase(Locale.ROOT)
        );
    }

    @Test
    void disablesAndEnablesTriggersForIncludedTables() {
        TableMetadata account = table("ACCOUNT");
        TableMetadata tx = table("TXN");

        when(targetJdbc.query(anyString(), any(RowMapper.class), eq("bank_core"), eq("account")))
            .thenReturn(List.of(
                new ForeignKeyTriggerManager.TriggerInfo("trig_acc_fk1", "O"),
                new ForeignKeyTriggerManager.TriggerInfo("trig_acc_fk2", "O")
            ));
        when(targetJdbc.query(anyString(), any(RowMapper.class), eq("bank_core"), eq("txn")))
            .thenReturn(List.of(new ForeignKeyTriggerManager.TriggerInfo("trig_txn_fk", "O")));

        ForeignKeyTriggerManager manager = new ForeignKeyTriggerManager(targetJdbc, targetRenderer);

        ForeignKeyTriggerManager.DisableSnapshot snapshot = manager.disableAll("BANK_CORE", List.of(account, tx));
        manager.enableAll("BANK_CORE", snapshot);

        InOrder order = inOrder(targetJdbc);
        order.verify(targetJdbc).execute("alter table bank_core.account disable trigger \"trig_acc_fk1\"");
        order.verify(targetJdbc).execute("alter table bank_core.account disable trigger \"trig_acc_fk2\"");
        order.verify(targetJdbc).execute("alter table bank_core.txn disable trigger \"trig_txn_fk\"");

        order.verify(targetJdbc).execute("alter table bank_core.account enable trigger \"trig_acc_fk1\"");
        order.verify(targetJdbc).execute("alter table bank_core.account enable trigger \"trig_acc_fk2\"");
        order.verify(targetJdbc).execute("alter table bank_core.txn enable trigger \"trig_txn_fk\"");
    }

    @Test
    void noOpWhenNoFkTriggersExist() {
        TableMetadata account = table("ACCOUNT");
        when(targetJdbc.query(anyString(), any(RowMapper.class), eq("bank_core"), eq("account")))
            .thenReturn(List.of());

        ForeignKeyTriggerManager manager = new ForeignKeyTriggerManager(targetJdbc, targetRenderer);
        ForeignKeyTriggerManager.DisableSnapshot snapshot = manager.disableAll("BANK_CORE", List.of(account));
        manager.enableAll("BANK_CORE", snapshot);

        verify(targetJdbc, never()).execute(anyString());
    }

    @Test
    void skipsExcludedTables() {
        TableMetadata excluded = new TableMetadata(
            "BANK_CORE",
            "AUDIT_LOG",
            ObjectStatus.EXCLUDED,
            List.of(),
            List.of(),
            List.of(),
            "Excluded by configuration"
        );
        ForeignKeyTriggerManager manager = new ForeignKeyTriggerManager(targetJdbc, targetRenderer);

        ForeignKeyTriggerManager.DisableSnapshot snapshot = manager.disableAll("BANK_CORE", List.of(excluded));

        verifyNoInteractions(targetJdbc);
    }

    @Test
    void partialFailureDuringDisableStillReturnsSuccessfullyDisabledTriggers() {
        TableMetadata account = table("ACCOUNT");
        TableMetadata tx = table("TXN");

        lenient().when(targetJdbc.query(anyString(), any(RowMapper.class), eq("bank_core"), eq("account")))
            .thenReturn(List.of(
                new ForeignKeyTriggerManager.TriggerInfo("trig_acc_fk1", "O"),
                new ForeignKeyTriggerManager.TriggerInfo("trig_acc_fk2", "O")
            ));
        lenient().when(targetJdbc.query(anyString(), any(RowMapper.class), eq("bank_core"), eq("txn")))
            .thenReturn(List.of(new ForeignKeyTriggerManager.TriggerInfo("trig_txn_fk", "O")));

        ForeignKeyTriggerManager manager = new ForeignKeyTriggerManager(targetJdbc, targetRenderer);

        org.mockito.Mockito.doNothing().when(targetJdbc).execute("alter table bank_core.account disable trigger \"trig_acc_fk1\"");
        org.mockito.Mockito.doThrow(new RuntimeException("DB Error")).when(targetJdbc).execute("alter table bank_core.account disable trigger \"trig_acc_fk2\"");

        ForeignKeyTriggerManager.DisableSnapshot snapshot = null;
        try {
            manager.disableAll("BANK_CORE", List.of(account, tx));
        } catch (ForeignKeyTriggerManager.TriggerDisableException ex) {
            snapshot = ex.getSnapshot();
        }

        org.junit.jupiter.api.Assertions.assertNotNull(snapshot);
        List<ForeignKeyTriggerManager.DisabledTrigger> disabled = snapshot.getDisabledTriggers();
        org.junit.jupiter.api.Assertions.assertEquals(1, disabled.size());
        org.junit.jupiter.api.Assertions.assertEquals("bank_core.account", disabled.get(0).qualifiedTableName());
        org.junit.jupiter.api.Assertions.assertEquals("trig_acc_fk1", disabled.get(0).triggerName());
    }

    @Test
    void partialFailureDuringEnableAttemptsRemainingTriggersAndSurfacesFailure() {
        ForeignKeyTriggerManager manager = new ForeignKeyTriggerManager(targetJdbc, targetRenderer);
        ForeignKeyTriggerManager.DisableSnapshot snapshot = new ForeignKeyTriggerManager.DisableSnapshot();
        snapshot.record("bank_core.account", "trig_acc_fk1");
        snapshot.record("bank_core.account", "trig_acc_fk2");
        snapshot.record("bank_core.txn", "trig_txn_fk");

        org.mockito.Mockito.doThrow(new RuntimeException("Error 1")).when(targetJdbc).execute("alter table bank_core.account enable trigger \"trig_acc_fk1\"");
        org.mockito.Mockito.doNothing().when(targetJdbc).execute("alter table bank_core.account enable trigger \"trig_acc_fk2\"");
        org.mockito.Mockito.doThrow(new RuntimeException("Error 2")).when(targetJdbc).execute("alter table bank_core.txn enable trigger \"trig_txn_fk\"");

        try {
            manager.enableAll("BANK_CORE", snapshot);
            org.junit.jupiter.api.Assertions.fail("Expected exception not thrown");
        } catch (RuntimeException ex) {
            org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("Failed to re-enable one or more foreign key triggers"));
            org.junit.jupiter.api.Assertions.assertEquals(2, ex.getSuppressed().length);
            org.junit.jupiter.api.Assertions.assertEquals("Error 1", ex.getSuppressed()[0].getMessage());
            org.junit.jupiter.api.Assertions.assertEquals("Error 2", ex.getSuppressed()[1].getMessage());
        }

        verify(targetJdbc).execute("alter table bank_core.account enable trigger \"trig_acc_fk1\"");
        verify(targetJdbc).execute("alter table bank_core.account enable trigger \"trig_acc_fk2\"");
        verify(targetJdbc).execute("alter table bank_core.txn enable trigger \"trig_txn_fk\"");
    }

    private static TableMetadata table(String name) {
        return new TableMetadata("BANK_CORE", name, ObjectStatus.READY, List.of(), List.of(), List.of());
    }

    @Test
    void triggerDiscoveryQueryNarrowsToChildSideOnly() {
        TableMetadata account = table("ACCOUNT");
        org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        when(targetJdbc.query(sqlCaptor.capture(), any(RowMapper.class), eq("bank_core"), eq("account")))
            .thenReturn(List.of());

        ForeignKeyTriggerManager manager = new ForeignKeyTriggerManager(targetJdbc, targetRenderer);
        manager.disableAll("BANK_CORE", List.of(account));

        String query = sqlCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(query).contains("con.conrelid = t.tgrelid");
    }

    @Test
    void preservesPreexistingTriggerStatesDuringRestore() {
        TableMetadata account = table("ACCOUNT");

        when(targetJdbc.query(anyString(), any(RowMapper.class), eq("bank_core"), eq("account")))
            .thenReturn(List.of(
                new ForeignKeyTriggerManager.TriggerInfo("trig1_disabled", "D"),
                new ForeignKeyTriggerManager.TriggerInfo("trig2_replica", "R"),
                new ForeignKeyTriggerManager.TriggerInfo("trig3_always", "A"),
                new ForeignKeyTriggerManager.TriggerInfo("trig4_normal", "O")
            ));

        ForeignKeyTriggerManager manager = new ForeignKeyTriggerManager(targetJdbc, targetRenderer);

        ForeignKeyTriggerManager.DisableSnapshot snapshot = manager.disableAll("BANK_CORE", List.of(account));

        InOrder order = inOrder(targetJdbc);
        order.verify(targetJdbc).execute("alter table bank_core.account disable trigger \"trig1_disabled\"");
        order.verify(targetJdbc).execute("alter table bank_core.account disable trigger \"trig2_replica\"");
        order.verify(targetJdbc).execute("alter table bank_core.account disable trigger \"trig3_always\"");
        order.verify(targetJdbc).execute("alter table bank_core.account disable trigger \"trig4_normal\"");

        manager.enableAll("BANK_CORE", snapshot);

        order.verify(targetJdbc).execute("alter table bank_core.account disable trigger \"trig1_disabled\"");
        order.verify(targetJdbc).execute("alter table bank_core.account enable replica trigger \"trig2_replica\"");
        order.verify(targetJdbc).execute("alter table bank_core.account enable always trigger \"trig3_always\"");
        order.verify(targetJdbc).execute("alter table bank_core.account enable trigger \"trig4_normal\"");
    }
}
