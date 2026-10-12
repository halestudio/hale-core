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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.instance.model.DataSet;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.store.InstanceStore;
import eu.esdihumboldt.hale.common.instance.store.InstanceStoreWriter;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;

/**
 * Temporary instance store based on SQLite.
 */
public class SqliteInstanceStore implements InstanceStore {

	private static final ALogger log = ALoggerFactory.getLogger(SqliteInstanceStore.class);

	private final Path directory;
	private final StoreContext ctx;
	private SqliteInstanceWriter writer;

	/**
	 * Create a new store.
	 *
	 * @param dataSet the data set of the stored instances
	 * @param directory the store directory, deleted when the store is closed
	 * @throws IOException if the database cannot be created
	 */
	public SqliteInstanceStore(DataSet dataSet, Path directory) throws IOException {
		this.directory = directory;
		this.ctx = new StoreContext(dataSet);
		ctx.database = new SqliteDatabase(directory);
	}

	@Override
	public synchronized InstanceStoreWriter openWriter() {
		if (writer != null && writer.isClosed()) {
			// the previous writer may still be committing on the shared writer
			// connection - wait for it to finish before starting a new one
			closeWriter();
		}
		if (writer == null) {
			writer = new SqliteInstanceWriter(ctx);
		}
		return writer;
	}

	@Override
	public InstanceCollection getInstances(TypeIndex types) {
		return new StoreInstanceCollection(ctx, types);
	}

	@Override
	public Set<TypeDefinition> getStoredTypes(TypeIndex types) {
		Set<TypeDefinition> result = new LinkedHashSet<>();
		for (Integer typeId : ctx.types.ids()) {
			if (ctx.committedCount(typeId) > 0) {
				TypeDefinition type = types.getType(ctx.types.nameOf(typeId));
				if (type != null) {
					result.add(type);
				}
			}
		}
		return result;
	}

	@Override
	public synchronized void clear() {
		closeWriter();
		ctx.database.close();
		try {
			Files.deleteIfExists(directory.resolve(SqliteDatabase.FILE_NAME + "-wal"));
			Files.deleteIfExists(directory.resolve(SqliteDatabase.FILE_NAME + "-shm"));
			Files.deleteIfExists(directory.resolve(SqliteDatabase.FILE_NAME));
			ctx.reset();
			ctx.database = new SqliteDatabase(directory);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to recreate temporary instance database", e);
		}
	}

	@Override
	public synchronized void close() throws IOException {
		try {
			closeWriter();
		} finally {
			ctx.database.close();
			try {
				FileUtils.deleteDirectory(directory.toFile());
			} catch (IOException e) {
				log.warn("Could not delete temporary instance database, deleting on exit", e);
				FileUtils.forceDeleteOnExit(directory.toFile());
			}
		}
	}

	private void closeWriter() {
		if (writer != null) {
			try {
				writer.close();
			} catch (IOException e) {
				log.error("Error closing instance store writer", e);
			}
			writer = null;
		}
	}

}
