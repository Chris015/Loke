package loke.db.athena;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class JdbcManager {

    private static final Logger log = LogManager.getLogger(JdbcManager.class);
    private String myUrl;
    private Properties myProps = new Properties();

    /**
     * Sets the JDBC url.
     */
    public void setUrl(String theUrl) {
        myUrl = theUrl;
    }

    /**
     * Setts additional JDBC connection properties.
     */
    public void setProperty(String theName, String theValue) {
        myProps.setProperty(theName, theValue);
    }

    /**
     * Executes a custom sql query to DB with callback support for resultset.
     */
    public void executeQuery(String theSql, RsAction theAction) throws SqlException {
        try (
                Connection aConn = DriverManager.getConnection(myUrl, myProps);
                Statement aStmt = aConn.createStatement();
        ) {
            log.trace("Execute:\n{}", theSql);
            theAction.onData(aStmt.executeQuery(theSql));
        } catch (Exception e) {
            throw new SqlException("Failed to execute sql: " + theSql, e);
        }
    }

    /**
     * Executes a SQL query and populates a custom pojo with the result.
     * <p>
     * E.g.
     * <p>
     * public class ApaCount {
     * int count;
     * }
     * <p>
     * Apa anApa = executeQuery(“Select count(*) as count from Apa)
     * .getSingleResult();
     * <p>
     * List<Apa> someApas = SqlService.getInstancce()
     * .executeQuery(true, “Select int as count from Apa)
     * .getResultList();
     * <p>
     * The column names can also be annoted if different than field name. E.g.
     * <p>
     * public class Apa {
     *
     * @Column(value = “animal_name”)
     * string name;
     * }
     */
    public <T> QueryResult executeQuery(final String theSql, final Class<T> theResultClass) throws SqlException {
        final QueryResult<T> aResult = new QueryResult<>();
        executeQuery(theSql, theResultSet -> {
            try {
                while (theResultSet.next()) {
                    T aPojo = getResultInstance(theResultSet, theResultClass);
                    aResult.addRow(aPojo);
                }
            } catch (Exception e) {
                throw new SqlException("Failed execute query", e);
            }
        });
        return aResult;
    }

    private <T> T getResultInstance(ResultSet theResultSet, final Class<T> theResultClass) throws Exception {
        T aPojo = theResultClass.newInstance();
        ResultSetMetaData aMeta = theResultSet.getMetaData();
        int aColumns = aMeta.getColumnCount();
        for (int i = 1; i <= aColumns; i++) {
            String aName = aMeta.getColumnLabel(i);
            Field aField = getField(theResultClass, aName);
            if (aField == null) {
                // Avoid NPE
                throw new RuntimeException("Did not recognize field "
                        + aName
                        + " in result class: "
                        + theResultClass + ", ignoring");
            }
            aField.setAccessible(true);
            Class aFieldType = aField.getType();
            if (aFieldType == String.class) aField.set(aPojo, theResultSet.getString(i));
            else if (aFieldType == java.util.Date.class) aField.set(aPojo, theResultSet.getTimestamp(i));
            else if (aFieldType == Integer.class) aField.set(aPojo, theResultSet.getInt(i));
            else if (aFieldType == int.class) aField.set(aPojo, theResultSet.getInt(i));
            else if (aFieldType == Long.class) aField.set(aPojo, theResultSet.getLong(i));
            else if (aFieldType == long.class) aField.set(aPojo, theResultSet.getLong(i));
            else if (aFieldType == Double.class) aField.set(aPojo, theResultSet.getDouble(i));
            else if (aFieldType == double.class) aField.set(aPojo, theResultSet.getDouble(i));
            else if (aFieldType == Boolean.class) aField.set(aPojo, theResultSet.getBoolean(i));
            else if (aFieldType == boolean.class) aField.set(aPojo, theResultSet.getBoolean(i));
            else if (aFieldType.isEnum()) aField.set(aPojo, Enum.valueOf((Class<Enum>) aFieldType,
                    theResultSet.getString(i)));
            else {
                throw new IllegalArgumentException("Field type "
                        + aField
                        + " is not supported yet, feel free to add it if you want...");
            }
            if (theResultSet.wasNull()) {
                aField.set(aPojo, null);
            }
        }
        return aPojo;
    }

    private Field getField(Class theClass, String theName) {
        while (theClass != null) {
            try {
                for (Field aField : theClass.getDeclaredFields()) {
                    Column aColumn = aField.getDeclaredAnnotation(Column.class);
                    if (aColumn != null && aColumn.value().equals(theName)) {
                        return aField;
                    }
                }
                return theClass.getDeclaredField(theName);
            } catch (NoSuchFieldException e) {
            }
            theClass = theClass.getSuperclass();
        }
        return null;
    }

    /**
     * Callback interface for result sets.
     */
    public interface RsAction {
        void onData(ResultSet theResultSet) throws Exception;
    }

    /**
     * Annotation interface for class fields to populate. Use this if field name differs from loke.db column name/label.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Column {
        String value();
    }

    /**
     * Container for query results.
     */
    public static class QueryResult<T> {
        private List<T> myRows = new ArrayList<>();

        private void addRow(T theRow) {
            myRows.add(theRow);
        }

        public T getSingleResult() {
            if (myRows.isEmpty()) {
                return null;
            }
            return myRows.get(0);
        }

        public List<T> getResultList() {
            return myRows;
        }

        public void setResultList(List<T> myRows) {
            this.myRows = myRows;
        }
    }

    public static class SqlException extends RuntimeException {
        public SqlException(String theMessage) {
            super(theMessage);
        }

        public SqlException(String theMessage, Throwable theCause) {
            super(theMessage, theCause);
        }
    }
}

