package com.bank.migration.load.conversion;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.stereotype.Component;

@Component
public class JdbcSourceValueConverter implements SourceValueConverter {
    @Override
    public Object convert(ResultSet rs, String columnName, Object rawValue) throws SQLException {
        if (rawValue == null) {
            return null;
        }

        if (rawValue instanceof NClob nclob) {
            try {
                long length = nclob.length();
                if (length > Integer.MAX_VALUE) {
                    throw new LobConversionException("NCLOB length " + length + " exceeds maximum allowed", null);
                }
                return nclob.getSubString(1, (int) length);
            } finally {
                nclob.free();
            }
        }

        if (rawValue instanceof Clob clob) {
            try {
                long length = clob.length();
                if (length > Integer.MAX_VALUE) {
                    throw new LobConversionException("CLOB length " + length + " exceeds maximum allowed", null);
                }
                return clob.getSubString(1, (int) length);
            } finally {
                clob.free();
            }
        }

        if (rawValue instanceof Blob blob) {
            try {
                long length = blob.length();
                if (length > Integer.MAX_VALUE) {
                    throw new LobConversionException("BLOB length " + length + " exceeds maximum allowed", null);
                }
                return blob.getBytes(1, (int) length);
            } finally {
                blob.free();
            }
        }

        String className = rawValue.getClass().getName();
        if (className.startsWith("oracle.sql.INTERVAL")) {
            return rawValue.toString();
        }
        if (className.startsWith("oracle.sql.TIMESTAMP") || className.startsWith("oracle.sql.DATE")) {
            return rs.getTimestamp(columnName);
        }

        return rawValue;
    }
}
