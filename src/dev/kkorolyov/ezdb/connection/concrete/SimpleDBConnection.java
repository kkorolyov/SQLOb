package dev.kkorolyov.ezdb.connection.concrete;
import java.sql.*;
import java.util.LinkedList;
import java.util.List;

import dev.kkorolyov.ezdb.column.Column;
import dev.kkorolyov.ezdb.connection.DBConnection;
import dev.kkorolyov.ezdb.connection.TableConnection;
import dev.kkorolyov.ezdb.exceptions.DuplicateTableException;
import dev.kkorolyov.ezdb.exceptions.NullTableException;
import dev.kkorolyov.ezdb.logging.DBLogger;
import dev.kkorolyov.ezdb.strings.Strings;

/**
 * A simple {@code DBConnection} implementation.
 * @see DBConnection
 */
public class SimpleDBConnection implements DBConnection {	// TODO ClosedException
	private static final DBLogger log = DBLogger.getLogger(SimpleDBConnection.class.getName());

	private static final String jdbcDriverClassName = "org.postgresql.Driver";
	private static final String jdbcHeader = "jdbc:postgresql:";
		
	private String URL, DB;
	private Connection conn;
	private List<Statement> openStatements = new LinkedList<>();
	
	/**
	 * Opens a new connection to the specified host and database residing on it.
	 * @param host IP or hostname of host containing database
	 * @param db name of database to connect to
	 * @throws SQLException if the URL is faulty or {@code null}
	 */
	public SimpleDBConnection(String host, String db) throws SQLException {
		URL = formatURL(host, db);
		DB = db;
		initDriver();
		initConnection();
			
		log.debug("Successfully initialized " + getClass().getSimpleName() + " for database at: " + URL);
	}
	private static String formatURL(String host, String db) {
		return jdbcHeader + "//" + host + "/" + db;
	}
	private static void initDriver() {
		try {
			Class.forName(jdbcDriverClassName);
		} catch (ClassNotFoundException e) {
			log.exceptionSevere(e);
		}
	}
	private void initConnection() throws SQLException {
		conn = DriverManager.getConnection(URL, Strings.USER, Strings.PASSWORD);
	}
	
	@Override
	public TableConnection connect(String table) {
		try {
			return new SimpleTableConnection(this, table);
		} catch (NullTableException e) {
			return null;
		}
	}
	
	@Override
	public void close() {
		if (isClosed())	// Already closed
			return;
		
		try {
			conn.close();	// Release JDBC resources
		} catch (SQLException e) {
			log.exceptionWarning(e);
		}
		conn = null;
		openStatements = null;
		
		log.debug("Closed " + getClass().getSimpleName() + " at URL: " + URL);
	}
	
	@Override
	public boolean isClosed() {
		if (conn == null && openStatements == null)
			return true;
		return false;
	}
	
	@Override
	public ResultSet execute(String statement) throws SQLException {
		return execute(statement, (Object[]) null);
	}
	@Override
	public ResultSet execute(String baseStatement, Object[] parameters) throws SQLException {
		PreparedStatement s = setupStatement(baseStatement, parameters);
		
		ResultSet rs = s.execute() ? s.getResultSet() : null;	// ResultSet if returns one, null if otherwise
		return rs;
	}
	
	@Override
	public int update(String statement) throws SQLException {	// TODO May be useless
		return update(statement, null);
	}
	@Override
	public int update(String baseStatement, Object[] parameters) throws SQLException {
		PreparedStatement s = setupStatement(baseStatement, parameters);
		
		int updated = !s.execute() ? s.getUpdateCount() : 0;	// If false, returns an update count instead of result set
		return updated;
	}
	
	private PreparedStatement setupStatement(String baseStatement, Object[] parameters) throws SQLException {
		PreparedStatement statement = conn.prepareStatement(baseStatement);
		openStatements.add(statement);	// Add to flushable list
		
		buildParameters(statement, parameters);	// Add appropriate types
		
		return statement;
	}
	private static PreparedStatement buildParameters(PreparedStatement statement, Object[] parameters) throws SQLException {	// Inserts appropriate type into statement
		if (parameters != null && parameters.length > 0) {
			for (int i = 0; i < parameters.length; i++) {	// Prepare with appropriate types
				if (parameters[i] instanceof Boolean)
					statement.setBoolean(i + 1, (boolean) parameters[i]);
				else if (parameters[i] instanceof Character)
					statement.setString(i + 1, String.valueOf((char) parameters[i]));
				else if (parameters[i] instanceof Double)
					statement.setDouble(i + 1, (double) parameters[i]);
				else if (parameters[i] instanceof Float)
					statement.setFloat(i + 1, (float) parameters[i]);
				else if (parameters[i] instanceof Integer)
					statement.setInt(i + 1, (int) parameters[i]);
				else if (parameters[i] instanceof String)
					statement.setString(i + 1, (String) parameters[i]);
			}
		}
		return statement;
	}
	
	@Override
	public void flush() {
		int closedStatements = 0;	// Count closed statements for debugging
		for (Statement openStatement : openStatements) {
			try {
				openStatement.close();
				closedStatements++;
			} catch (SQLException e) {
				log.exceptionWarning(e);;
			}
		}
		openStatements.clear();
		
		log.debug("Closed " + closedStatements + " statements");
	}
	
	@Override
	public TableConnection createTable(String name, Column[] columns) throws DuplicateTableException, SQLException {
		if (containsTable(name))
			throw new DuplicateTableException(DB, name);
		
		execute(buildCreateTableStatement(name, columns));
		
		TableConnection newTable = null;
		try {
			newTable = new SimpleTableConnection(this, name);
		} catch (NullTableException e) {	// Should not be a null table
			log.exceptionWarning(e);
		}
		return newTable;
	}
	private String buildCreateTableStatement(String name, Column[] columns) {	// TODO Move to StatementBuilder
		String createTableStatement = "CREATE TABLE " + name + " (";	// Initial part of create statement
		
		for (int i = 0; i < columns.length - 1; i++) {	// Build all but last column names + types
			createTableStatement += columns[i].getName()+ " " + columns[i].getTypeName() + ", ";
		}
		createTableStatement += columns[columns.length - 1].getName() + " " + columns[columns.length - 1].getTypeName() + ")";	// End columns
		return createTableStatement;
	}
	
	@Override
	public void dropTable(String table) throws NullTableException, SQLException {
		if (!containsTable(table))	// No such table to drop
			throw new NullTableException(DB, table);
		
		String dropTableStatement = "DROP TABLE " + table;
		execute(dropTableStatement);
	}
	
	@Override
	public boolean containsTable(String table) {
		boolean contains = false;
		
		for (String dbTable : getTables()) {
			if (dbTable.equalsIgnoreCase(table)) {
				contains = true;
				break;
			}
		}
		return contains;
	}
	
	@Override
	public String[] getTables() {
		List<String> tables = new LinkedList<>();
		try {
			ResultSet tableSet = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"});
			while (tableSet.next()) {
				tables.add(tableSet.getString(3));
			}
			tableSet.close();
		} catch (SQLException e) {
			log.exceptionSevere(e);
		}
		return tables.toArray(new String[tables.size()]);
	}
	
	@Override
	public String getDBName() {
		return DB;
	}
}