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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import eu.esdihumboldt.hale.common.instance.model.DataSet;

/**
 * Tests for {@link SqliteInstanceStore}.
 */
public class SqliteInstanceStoreTest {

	@Test(timeout = 30000)
	public void testOpenWriterWaitsForClosingWriter() throws Exception {
		Path dir = Files.createTempDirectory("sqlite-store-test");
		SqliteInstanceStore store = new SqliteInstanceStore(DataSet.SOURCE, dir);
		try {
			SqliteInstanceWriter first = (SqliteInstanceWriter) store.openWriter();
			for (int i = 0; i < 5000; i++) {
				first.add(SqliteInstanceWriterTest.instance("i" + i));
			}

			Thread closer = new Thread(() -> {
				try {
					first.close();
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
			closer.start();
			while (!first.isClosed()) {
				Thread.onSpinWait();
			}

			// reopen while the first writer may still be committing
			SqliteInstanceWriter second = (SqliteInstanceWriter) store.openWriter();
			assertNotSame(first, second);
			assertFalse("a new writer was opened while the previous writer thread was running",
					first.isWriterThreadAlive());

			closer.join();
			second.add(SqliteInstanceWriterTest.instance("x"));
			second.close();
		} finally {
			store.close();
		}
	}

}
