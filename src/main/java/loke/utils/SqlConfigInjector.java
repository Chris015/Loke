package loke.utils;

public class SqlConfigInjector {
  private String databaseName;
  private String tableName;

    public SqlConfigInjector(String databaseName, String tableName) {
        this.databaseName = databaseName;
        this.tableName = tableName;
    }

    public String injectSqlConfig(String sql) {
        return sql.replace("databasename", databaseName)
                .replace("tablename", tableName);
    }
}
