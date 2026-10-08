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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SqliteDatabaseTest {

	private Path dir;
	private SqliteDatabase db;

	@Before
	public void setUp() throws Exception {
		dir = Files.createTempDirectory("sqlite-db-test");
		db = new SqliteDatabase(dir);
	}

	@After
	public void tearDown() throws Exception {
		db.close();
		org.apache.commons.io.FileUtils.deleteDirectory(dir.toFile());
	}

	@Test
	public void testSchemaAndWal() {
		List<String> tables = db.withReader(c -> {
			List<String> names = new ArrayList<>();
			try (Statement s = c.createStatement();
					ResultSet rs = s.executeQuery(
							"SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")) {
				while (rs.next()) {
					names.add(rs.getString(1));
				}
			}
			return names;
		});
		assertEquals(List.of("instances", "metadata", "types"), tables);
		String mode = db.withReader(c -> {
			try (Statement s = c.createStatement();
					ResultSet rs = s.executeQuery("PRAGMA journal_mode")) {
				rs.next();
				return rs.getString(1);
			}
		});
		assertEquals("wal", mode);
		assertTrue(Files.exists(dir.resolve(SqliteDatabase.FILE_NAME)));
	}

	@Test
	public void testReadersSeeCommittedData() throws Exception {
		Connection w = db.writerConnection();
		w.setAutoCommit(false);
		try (PreparedStatement p = w.prepareStatement(
				"INSERT INTO instances (id, type, payload) VALUES (?, 1, x'00')")) {
			for (int i = 1; i <= 100; i++) {
				p.setLong(1, i);
				p.executeUpdate();
			}
		}
		w.commit();

		ExecutorService executor = Executors.newFixedThreadPool(4);
		List<Future<Long>> counts = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			counts.add(executor.submit(() -> db.withReader(c -> {
				try (Statement s = c.createStatement();
						ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM instances")) {
					rs.next();
					return rs.getLong(1);
				}
			})));
		}
		for (Future<Long> count : counts) {
			assertEquals(100L, (long) count.get());
		}
		executor.shutdown();
	}
}
