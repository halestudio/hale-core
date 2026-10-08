/*
 * Copyright (c) 2026 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.instance.store.sqlite;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;

/**
 * SQLite database of an instance store: one writer connection and a pool of
 * reader connections.
 */
public class SqliteDatabase implements Closeable {

	/**
	 * Name of the database file in the store directory.
	 */
	public static final String FILE_NAME = "instances.sqlite";

	private static final int MAX_IDLE_READERS = 8;
	private static final int BUSY_TIMEOUT_MILLIS = 60_000;

	private static final ALogger log = ALoggerFactory.getLogger(SqliteDatabase.class);

	/**
	 * Function using a connection.
	 *
	 * @param <T> the result type
	 */
	@FunctionalInterface
	public interface SqlFunction<T> {

		/**
		 * @param connection the connection
		 * @return the result
		 * @throws SQLException if a database error occurs
		 */
		T apply(Connection connection) throws SQLException;
	}

	private final SQLiteDataSource readSource;
	private final Connection writer;
	private final ConcurrentLinkedQueue<Connection> idleReaders = new ConcurrentLinkedQueue<>();
	private final AtomicInteger idleCount = new AtomicInteger();
	private volatile boolean closed;

	/**
	 * Create a new database in the given directory.
	 *
	 * @param directory the directory
	 * @throws IOException if the database cannot be created
	 */
	public SqliteDatabase(Path directory) throws IOException {
		Files.createDirectories(directory);
		String url = "jdbc:sqlite:" + directory.resolve(FILE_NAME).toAbsolutePath();

		SQLiteConfig writeConfig = new SQLiteConfig();
		writeConfig.setSynchronous(SQLiteConfig.SynchronousMode.OFF);
		writeConfig.setBusyTimeout(BUSY_TIMEOUT_MILLIS);
		writeConfig.setCacheSize(-65536); // 64 MB
		SQLiteDataSource writeSource = new SQLiteDataSource(writeConfig);
		writeSource.setUrl(url);

		SQLiteConfig readConfig = new SQLiteConfig();
		readConfig.setBusyTimeout(BUSY_TIMEOUT_MILLIS);
		readConfig.setCacheSize(-16384); // 16 MB
		readSource = new SQLiteDataSource(readConfig);
		readSource.setUrl(url);

		try {
			writer = writeSource.getConnection();
			try (Statement s = writer.createStatement()) {
				// page size must be set before WAL mode is enabled, SQLiteConfig does
				// not guarantee the order of these pragmas
				s.execute("PRAGMA page_size = 8192");
				s.execute("PRAGMA journal_mode = WAL");
				s.execute("CREATE TABLE types (id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE)");
				s.execute("CREATE TABLE instances (id INTEGER PRIMARY KEY, type INTEGER NOT NULL, "
						+ "payload BLOB NOT NULL)");
				s.execute("CREATE INDEX instances_type ON instances(type, id)");
				s.execute("CREATE TABLE metadata (key TEXT NOT NULL, value TEXT NOT NULL, "
						+ "instance INTEGER NOT NULL, PRIMARY KEY (key, value, instance)) WITHOUT ROWID");
			}
		} catch (SQLException e) {
			throw new IOException("Failed to create temporary instance database", e);
		}
	}

	/**
	 * @return the writer connection, only to be used by a single thread
	 */
	public Connection writerConnection() {
		return writer;
	}

	/**
	 * Run a function with a pooled reader connection.
	 *
	 * @param function the function
	 * @return the function result
	 * @throws IllegalStateException if a database error occurs
	 */
	public <T> T withReader(SqlFunction<T> function) {
		if (closed) {
			throw new IllegalStateException("Database is closed");
		}
		Connection connection = idleReaders.poll();
		try {
			if (connection == null) {
				connection = readSource.getConnection();
			}
			else {
				idleCount.decrementAndGet();
			}
			T result = function.apply(connection);
			release(connection);
			return result;
		} catch (SQLException e) {
			closeQuietly(connection);
			throw new IllegalStateException("Error reading temporary instance database", e);
		} catch (RuntimeException e) {
			release(connection);
			throw e;
		}
	}

	private void release(Connection connection) {
		if (connection == null) {
			return;
		}
		if (!closed && idleCount.incrementAndGet() <= MAX_IDLE_READERS) {
			idleReaders.offer(connection);
		}
		else {
			idleCount.decrementAndGet();
			closeQuietly(connection);
		}
	}

	private static void closeQuietly(Connection connection) {
		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException e) {
				log.warn("Failed to close database connection", e);
			}
		}
	}

	@Override
	public void close() {
		closed = true;
		Connection connection;
		while ((connection = idleReaders.poll()) != null) {
			closeQuietly(connection);
		}
		closeQuietly(writer);
	}

}
