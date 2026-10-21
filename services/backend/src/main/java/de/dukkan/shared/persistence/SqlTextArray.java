package de.dukkan.shared.persistence;

import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.support.AbstractSqlTypeValue;

/** Binds a {@code text[]} parameter for JdbcClient / NamedParameterJdbcTemplate. */
public final class SqlTextArray extends AbstractSqlTypeValue {

  private final String[] values;

  public SqlTextArray(Collection<String> values) {
    this.values = values.toArray(String[]::new);
  }

  @Override
  protected Object createTypeValue(Connection connection, int sqlType, @Nullable String typeName) throws SQLException {
    return connection.createArrayOf("text", values);
  }

  /** Reads a {@code text[]} column (null → empty list). */
  public static List<String> read(@Nullable Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    return Arrays.asList((String[]) array.getArray());
  }
}
