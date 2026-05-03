package ca.uhn.fhir.jpa.starter.custom;

import java.sql.Connection;

import org.flywaydb.database.postgresql.PostgreSQLDatabaseType;

/**
 * Custom DatabaseType so Flyway accepts Aurora PostgreSQL engine names and versions.
 */
public class AuroraPostgreSQLDatabaseType extends PostgreSQLDatabaseType {

  @Override
  public String getName() {
    return "AuroraPostgreSQL";
  }

  @Override
  public boolean handlesDatabaseProductNameAndVersion(
      String databaseProductName, String databaseProductVersion, Connection connection) {
    if (databaseProductName == null) {
      return false;
    }
    return databaseProductName.contains("PostgreSQL");
  }
}
