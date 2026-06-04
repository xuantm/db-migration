package com.bank.migration.load.conversion;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface SourceValueConverter {
    Object convert(ResultSet rs, String columnName, Object rawValue) throws SQLException;
}
