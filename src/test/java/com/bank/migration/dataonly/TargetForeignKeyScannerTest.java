package com.bank.migration.dataonly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bank.migration.identifier.IdentifierRenderer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class TargetForeignKeyScannerTest {
    @Mock JdbcTemplate targetJdbc;
    @Mock IdentifierRenderer targetRenderer;

    @Test
    void scanNormalizesSchemaAndFiltersTables() throws SQLException {
        when(targetRenderer.physicalName("BANK_CORE")).thenReturn("bank_core");

        when(targetJdbc.query(anyString(), any(RowMapper.class), eq("bank_core")))
            .thenAnswer(inv -> {
                RowMapper<?> mapper = inv.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("conname")).thenReturn("fk_acc_cust");
                when(rs.getString("child_schema")).thenReturn("bank_core");
                when(rs.getString("child_table")).thenReturn("ACCOUNT");
                when(rs.getString("parent_schema")).thenReturn("bank_core");
                when(rs.getString("parent_table")).thenReturn("CUSTOMER");
                when(rs.getString("child_column")).thenReturn("customer_id");
                when(rs.getString("parent_column")).thenReturn("id");
                when(rs.getString("confmatchtype")).thenReturn("s");
                return List.of(mapper.mapRow(rs, 0));
            });

        TargetForeignKeyScanner scanner = new TargetForeignKeyScanner(targetJdbc, targetRenderer);
        List<TargetForeignKeyMetadata> result = scanner.scan("BANK_CORE", Set.of("ACCOUNT", "CUSTOMER"));

        assertThat(result).hasSize(1);
        TargetForeignKeyMetadata fk = result.get(0);
        assertThat(fk.constraintName()).isEqualTo("fk_acc_cust");
        assertThat(fk.childSchema()).isEqualTo("bank_core");
        assertThat(fk.childTable()).isEqualTo("ACCOUNT");
        assertThat(fk.parentSchema()).isEqualTo("bank_core");
        assertThat(fk.parentTable()).isEqualTo("CUSTOMER");
        assertThat(fk.childColumns()).containsExactly("customer_id");
        assertThat(fk.parentColumns()).containsExactly("id");
    }

    @Test
    void scanFiltersBasedOnChildTableOnly() throws SQLException {
        when(targetRenderer.physicalName("BANK_CORE")).thenReturn("bank_core");

        when(targetJdbc.query(anyString(), any(RowMapper.class), eq("bank_core")))
            .thenAnswer(inv -> {
                RowMapper<?> mapper = inv.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("conname")).thenReturn("fk_acc_cust");
                when(rs.getString("child_schema")).thenReturn("bank_core");
                when(rs.getString("child_table")).thenReturn("ACCOUNT");
                when(rs.getString("parent_schema")).thenReturn("external_schema");
                when(rs.getString("parent_table")).thenReturn("CUSTOMER");
                when(rs.getString("child_column")).thenReturn("customer_id");
                when(rs.getString("parent_column")).thenReturn("id");
                when(rs.getString("confmatchtype")).thenReturn("s");
                return List.of(mapper.mapRow(rs, 0));
            });

        TargetForeignKeyScanner scanner = new TargetForeignKeyScanner(targetJdbc, targetRenderer);
        // Customer is not in included set (it's external/pre-existing)
        List<TargetForeignKeyMetadata> result = scanner.scan("BANK_CORE", Set.of("ACCOUNT"));

        assertThat(result).hasSize(1);
        TargetForeignKeyMetadata fk = result.get(0);
        assertThat(fk.constraintName()).isEqualTo("fk_acc_cust");
        assertThat(fk.childSchema()).isEqualTo("bank_core");
        assertThat(fk.childTable()).isEqualTo("ACCOUNT");
        assertThat(fk.parentSchema()).isEqualTo("external_schema");
        assertThat(fk.parentTable()).isEqualTo("CUSTOMER");
    }

    @Test
    void scanKeepsSameNamedConstraintsOnDifferentTablesSeparate() throws SQLException {
        when(targetRenderer.physicalName("bank_core")).thenReturn("bank_core");

        when(targetJdbc.query(anyString(), any(RowMapper.class), eq("bank_core")))
            .thenAnswer(inv -> {
                RowMapper<?> mapper = inv.getArgument(1);

                ResultSet rs1 = mock(ResultSet.class);
                when(rs1.getString("conname")).thenReturn("fk_shared_name");
                when(rs1.getString("child_schema")).thenReturn("bank_core");
                when(rs1.getString("child_table")).thenReturn("TABLE_A");
                when(rs1.getString("parent_schema")).thenReturn("bank_core");
                when(rs1.getString("parent_table")).thenReturn("PARENT");
                when(rs1.getString("child_column")).thenReturn("p_id");
                when(rs1.getString("parent_column")).thenReturn("id");
                when(rs1.getString("confmatchtype")).thenReturn("s");

                ResultSet rs2 = mock(ResultSet.class);
                when(rs2.getString("conname")).thenReturn("fk_shared_name");
                when(rs2.getString("child_schema")).thenReturn("bank_core");
                when(rs2.getString("child_table")).thenReturn("TABLE_B");
                when(rs2.getString("parent_schema")).thenReturn("bank_core");
                when(rs2.getString("parent_table")).thenReturn("PARENT");
                when(rs2.getString("child_column")).thenReturn("p_id");
                when(rs2.getString("parent_column")).thenReturn("id");
                when(rs2.getString("confmatchtype")).thenReturn("s");

                return List.of(mapper.mapRow(rs1, 0), mapper.mapRow(rs2, 1));
            });

        TargetForeignKeyScanner scanner = new TargetForeignKeyScanner(targetJdbc, targetRenderer);
        List<TargetForeignKeyMetadata> result = scanner.scan("bank_core", Set.of("TABLE_A", "TABLE_B", "PARENT"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).childTable()).isEqualTo("TABLE_A");
        assertThat(result.get(0).childSchema()).isEqualTo("bank_core");
        assertThat(result.get(1).childTable()).isEqualTo("TABLE_B");
        assertThat(result.get(1).childSchema()).isEqualTo("bank_core");
    }

    @Test
    void scanRetainsMatchFullConfmatchtype() throws SQLException {
        when(targetRenderer.physicalName("BANK_CORE")).thenReturn("bank_core");

        when(targetJdbc.query(anyString(), any(RowMapper.class), eq("bank_core")))
            .thenAnswer(inv -> {
                RowMapper<?> mapper = inv.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("conname")).thenReturn("fk_acc_cust_full");
                when(rs.getString("child_schema")).thenReturn("bank_core");
                when(rs.getString("child_table")).thenReturn("ACCOUNT");
                when(rs.getString("parent_schema")).thenReturn("bank_core");
                when(rs.getString("parent_table")).thenReturn("CUSTOMER");
                when(rs.getString("child_column")).thenReturn("customer_id");
                when(rs.getString("parent_column")).thenReturn("id");
                when(rs.getString("confmatchtype")).thenReturn("f");
                return List.of(mapper.mapRow(rs, 0));
            });

        TargetForeignKeyScanner scanner = new TargetForeignKeyScanner(targetJdbc, targetRenderer);
        List<TargetForeignKeyMetadata> result = scanner.scan("BANK_CORE", Set.of("ACCOUNT", "CUSTOMER"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).confmatchtype()).isEqualTo("f");
    }

    @Test
    void scanWorksWithPhysicalTargetNamesForRenamedTables() throws SQLException {
        when(targetRenderer.physicalName("BANK_CORE")).thenReturn("bank_core");

        when(targetJdbc.query(anyString(), any(RowMapper.class), eq("bank_core")))
            .thenAnswer(inv -> {
                RowMapper<?> mapper = inv.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("conname")).thenReturn("fk_user_role");
                when(rs.getString("child_schema")).thenReturn("bank_core");
                when(rs.getString("child_table")).thenReturn("user_");
                when(rs.getString("parent_schema")).thenReturn("bank_core");
                when(rs.getString("parent_table")).thenReturn("role_");
                when(rs.getString("child_column")).thenReturn("role_id");
                when(rs.getString("parent_column")).thenReturn("id");
                when(rs.getString("confmatchtype")).thenReturn("s");
                return List.of(mapper.mapRow(rs, 0));
            });

        TargetForeignKeyScanner scanner = new TargetForeignKeyScanner(targetJdbc, targetRenderer);
        List<TargetForeignKeyMetadata> result = scanner.scan("BANK_CORE", Set.of("USER_", "ROLE_"));

        assertThat(result).hasSize(1);
        TargetForeignKeyMetadata fk = result.get(0);
        assertThat(fk.constraintName()).isEqualTo("fk_user_role");
        assertThat(fk.childTable()).isEqualTo("user_");
        assertThat(fk.parentTable()).isEqualTo("role_");
    }
}
