package be.vlaanderen.omgeving.bezwaarschriften.config;

import com.pgvector.PGvector;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

/**
 * Hibernate 5 UserType dat float[] mapt op PostgreSQL vector (pgvector).
 */
public class VectorType implements UserType {

  @Override
  public int[] sqlTypes() {
    return new int[]{Types.OTHER};
  }

  @Override
  public Class<?> returnedClass() {
    return float[].class;
  }

  @Override
  public boolean equals(Object x, Object y) {
    if (x == y) {
      return true;
    }
    if (x == null || y == null) {
      return false;
    }
    return Arrays.equals((float[]) x, (float[]) y);
  }

  @Override
  public int hashCode(Object x) {
    return Arrays.hashCode((float[]) x);
  }

  @Override
  public Object nullSafeGet(ResultSet rs, String[] names,
      SharedSessionContractImplementor session, Object owner)
      throws SQLException {
    var value = rs.getString(names[0]);
    if (value == null) {
      return null;
    }
    var pgVector = new PGvector(value);
    return pgVector.toArray();
  }

  @Override
  public void nullSafeSet(PreparedStatement st, Object value, int index,
      SharedSessionContractImplementor session)
      throws SQLException {
    if (value == null) {
      st.setNull(index, Types.OTHER);
    } else {
      var pgVector = new PGvector((float[]) value);
      st.setObject(index, pgVector);
    }
  }

  @Override
  public Object deepCopy(Object value) {
    if (value == null) {
      return null;
    }
    return ((float[]) value).clone();
  }

  @Override
  public boolean isMutable() {
    return true;
  }

  @Override
  public Serializable disassemble(Object value) {
    return (float[]) deepCopy(value);
  }

  @Override
  public Object assemble(Serializable cached, Object owner) {
    return deepCopy(cached);
  }

  @Override
  public Object replace(Object original, Object target, Object owner) {
    return deepCopy(original);
  }
}
