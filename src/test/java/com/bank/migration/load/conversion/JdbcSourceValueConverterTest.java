package com.bank.migration.load.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.ResultSet;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;

class JdbcSourceValueConverterTest {
    private final JdbcSourceValueConverter converter = new JdbcSourceValueConverter();

    @Test
    void convertsClobToString() throws Exception {
        Clob clob = mock(Clob.class);
        when(clob.length()).thenReturn(5L);
        when(clob.getSubString(1, 5)).thenReturn("hello");

        Object result = converter.convert(null, "col", clob);
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void convertsNClobToString() throws Exception {
        NClob nclob = mock(NClob.class);
        when(nclob.length()).thenReturn(5L);
        when(nclob.getSubString(1, 5)).thenReturn("world");

        Object result = converter.convert(null, "col", nclob);
        assertThat(result).isEqualTo("world");
    }

    @Test
    void convertsBlobToBytes() throws Exception {
        Blob blob = mock(Blob.class);
        when(blob.length()).thenReturn(4L);
        when(blob.getBytes(1, 4)).thenReturn(new byte[]{1, 2, 3, 4});

        Object result = converter.convert(null, "col", blob);
        assertThat(result).isEqualTo(new byte[]{1, 2, 3, 4});
    }

    @Test
    void convertsNullToNull() throws Exception {
        Object result = converter.convert(null, "col", null);
        assertThat(result).isNull();
    }

    @Test
    void convertsOracleDateAndTimestamp() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        when(rs.getTimestamp("col")).thenReturn(ts);

        Object oracleDate = mock(Class.forName("oracle.sql.DATE"));
        Object oracleTimestamp = mock(Class.forName("oracle.sql.TIMESTAMP"));

        Object res1 = converter.convert(rs, "col", oracleDate);
        assertThat(res1).isEqualTo(ts);

        Object res2 = converter.convert(rs, "col", oracleTimestamp);
        assertThat(res2).isEqualTo(ts);
    }

    @Test
    void convertsOracleIntervalToString() throws Exception {
        Object oracleInterval = mock(Class.forName("oracle.sql.INTERVALDS"));
        when(oracleInterval.toString()).thenReturn("+00 01:00:00.00");

        Object result = converter.convert(null, "col", oracleInterval);
        assertThat(result).isEqualTo("+00 01:00:00.00");
    }
}
