package dev.kkorolyov.sqlob;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

import dev.kkorolyov.simpleprops.Properties;

@SuppressWarnings("javadoc")
public class TestAssets {
	private static final String SQLITE_FILE = "test/sqlite.db";
	private static final String HOST = "HOST",
															DATABASE = "DATABASE",
															USER = "USER",
															PASSWORD = "PASSWORD";	
	private static final Properties props = initProps();
	
	static {
	//	Logger.getLogger("dev.kkorolyov.sqlob", Level.DEBUG, new PrintWriter(System.err));	// Enable logging
	}
	
	public static String host() {
		return props.get(HOST);
	}
	public static String database() {
		return props.get(DATABASE);
	}
	public static String user() {
		return props.get(USER);
	}
	public static String password() {
		return props.get(PASSWORD);
	}
	
	public static Iterable<DataSource> dataSources() {
		SQLiteConfig config = new SQLiteConfig();
		config.enforceForeignKeys(true);
		SQLiteDataSource sqliteDS = new SQLiteDataSource(config);
		sqliteDS.setUrl("jdbc:sqlite:" + SQLITE_FILE);
		
		MysqlDataSource mysqlDS = new MysqlDataSource();
		mysqlDS.setServerName(TestAssets.host());
		mysqlDS.setDatabaseName(TestAssets.database());
		mysqlDS.setUser(TestAssets.user());
		mysqlDS.setPassword(TestAssets.password());
		mysqlDS.setRewriteBatchedStatements(true);
		
		PGSimpleDataSource pgDS = new PGSimpleDataSource();
		pgDS.setServerName(TestAssets.host());
		pgDS.setDatabaseName(TestAssets.database());
		pgDS.setUser(TestAssets.user());
		pgDS.setPassword(TestAssets.password());
		
		return Arrays.asList(sqliteDS, mysqlDS, pgDS);
	}
	
	public static void cleanUp() throws FileNotFoundException, IOException {
		System.out.println((new File(SQLITE_FILE).delete() ? "Deleted " : "Failed to delete ") + "test SQLite file: " + SQLITE_FILE);
	}
	
	private static Properties initProps() {
		try {
			URI uri = TestAssets.class.getClassLoader().getResource("TestSQLOb.ini").toURI();
			Path file = Paths.get(uri);
			Properties props = new Properties(file);
			props.put(buildDefaults(), false);
			props.save(file);

			return props;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}
	private static Properties buildDefaults() {
		Properties defaults = new Properties();
		defaults.put(HOST, "");
		defaults.put(DATABASE, "");
		defaults.put(USER, "");
		defaults.put(PASSWORD, "");
		
		return defaults;
	}
}
