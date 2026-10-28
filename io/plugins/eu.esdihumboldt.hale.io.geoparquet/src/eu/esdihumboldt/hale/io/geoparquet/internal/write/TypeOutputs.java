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
package eu.esdihumboldt.hale.io.geoparquet.internal.write;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;

/**
 * The output files of the types written for one target. Types are written to
 * temporary files next to the target, which are moved to their final names (see
 * {@link #getFinalFiles()}) when all instances have been written.
 */
public class TypeOutputs {

	/** The file extension of Parquet files. */
	public static final String EXTENSION = ".parquet";

	private final Path target;
	private final String base;

	/** In order of first occurrence. */
	private final Map<TypeDefinition, Output> outputs = new LinkedHashMap<>();

	/**
	 * Create the outputs for a target.
	 *
	 * @param target the target file
	 */
	public TypeOutputs(Path target) {
		this.target = target;
		String name = target.getFileName().toString();
		if (name.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
			name = name.substring(0, name.length() - EXTENSION.length());
		}
		this.base = name;
	}

	/**
	 * @param type the type
	 * @return the writer for the type, <code>null</code> if there is none yet
	 */
	public TypeFileWriter getWriter(TypeDefinition type) {
		Output output = outputs.get(type);
		return output == null ? null : output.writer;
	}

	/**
	 * Create the temporary file a type is written to. The file is located in the
	 * directory of the target and deleted by {@link #deleteAll()}, even if no
	 * writer is set.
	 *
	 * @param type the type
	 * @return the temporary file for the type
	 * @throws IOException if the file cannot be created
	 */
	public Path allocate(TypeDefinition type) throws IOException {
		Path file = Files.createTempFile(target.toAbsolutePath().getParent(), ".geoparquet-",
				".tmp");
		outputs.put(type, new Output(file));
		return file;
	}

	/**
	 * Set the writer for an allocated file.
	 *
	 * @param type the type
	 * @param writer the writer
	 */
	public void setWriter(TypeDefinition type, TypeFileWriter writer) {
		outputs.get(type).writer = writer;
	}

	/**
	 * @return the temporary files, in order of first occurrence
	 */
	public List<Path> getTempFiles() {
		return outputs.values().stream().map(o -> o.file).toList();
	}

	/**
	 * Determine the final file names: the target if there is only one type,
	 * otherwise <code>&lt;base&gt;_&lt;type name&gt;.parquet</code> next to the
	 * target (type names sanitized, duplicates get a numeric suffix; names that
	 * only differ in case are considered duplicates).
	 *
	 * @return the final files, in the order of {@link #getTempFiles()}
	 */
	public List<Path> getFinalFiles() {
		if (outputs.size() == 1) {
			return List.of(target);
		}
		Set<String> usedNames = new HashSet<>();
		List<Path> result = new ArrayList<>();
		for (TypeDefinition type : outputs.keySet()) {
			String name = type.getName().getLocalPart().replaceAll("[^A-Za-z0-9_-]", "_");
			String candidate = name;
			// compared case insensitively, for case insensitive file systems
			for (int i = 2; !usedNames.add(candidate.toLowerCase(Locale.ROOT)); i++) {
				candidate = name + "_" + i;
			}
			result.add(target.resolveSibling(base + "_" + candidate + EXTENSION));
		}
		return result;
	}

	/**
	 * Close all writers.
	 *
	 * @throws IOException if closing a writer fails
	 */
	public void closeAll() throws IOException {
		IOException failure = null;
		for (Output output : outputs.values()) {
			try {
				output.close();
			} catch (IOException e) {
				if (failure == null) {
					failure = e;
				}
			}
		}
		if (failure != null) {
			throw failure;
		}
	}

	/**
	 * Close all writers, ignoring failures.
	 */
	public void closeQuietly() {
		for (Output output : outputs.values()) {
			try {
				output.close();
			} catch (IOException | RuntimeException e) {
				// ignore, already handling a failure
			}
		}
	}

	/**
	 * Delete the temporary files (best effort).
	 */
	public void deleteAll() {
		for (Output output : outputs.values()) {
			try {
				Files.deleteIfExists(output.file);
			} catch (IOException e) {
				// ignore
			}
		}
	}

	private static class Output {

		private final Path file;
		private TypeFileWriter writer;

		Output(Path file) {
			this.file = file;
		}

		void close() throws IOException {
			if (writer != null) {
				TypeFileWriter w = writer;
				writer = null;
				w.close();
			}
		}
	}

}
