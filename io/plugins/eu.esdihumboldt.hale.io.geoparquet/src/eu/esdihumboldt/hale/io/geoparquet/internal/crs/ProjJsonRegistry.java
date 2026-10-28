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
package eu.esdihumboldt.hale.io.geoparquet.internal.crs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Registry of PROJJSON definitions for EPSG coordinate reference systems,
 * backed by a compressed resource bundled with the plugin (see
 * <code>scripts/generate-projjson.py</code>). The resource is scanned on demand
 * and only requested entries are kept in memory.
 */
public final class ProjJsonRegistry {

	private static final String RESOURCE = "/eu/esdihumboldt/hale/io/geoparquet/crs/epsg-projjson.tsv.gz";

	private static final ProjJsonRegistry INSTANCE = new ProjJsonRegistry();

	/**
	 * Cache of looked up codes. Missing codes are cached as empty strings.
	 */
	private final Map<Integer, String> cache = new ConcurrentHashMap<>();

	private ProjJsonRegistry() {
		super();
	}

	/**
	 * @return the registry instance
	 */
	public static ProjJsonRegistry getInstance() {
		return INSTANCE;
	}

	/**
	 * Get the PROJJSON for an EPSG code.
	 *
	 * @param epsgCode the EPSG code
	 * @return the minified PROJJSON or an empty optional if the code is not
	 *         contained in the registry
	 */
	public Optional<String> getProjJson(int epsgCode) {
		String json = cache.computeIfAbsent(epsgCode, this::scan);
		return json.isEmpty() ? Optional.empty() : Optional.of(json);
	}

	private String scan(int epsgCode) {
		try (InputStream in = ProjJsonRegistry.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("Bundled resource not found: " + RESOURCE);
			}
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					int tab = line.indexOf('\t');
					if (tab <= 0) {
						continue;
					}
					int code = Integer.parseInt(line.substring(0, tab));
					if (code == epsgCode) {
						return line.substring(tab + 1);
					}
					if (code > epsgCode) {
						// lines are sorted by code
						break;
					}
				}
			}
			return "";
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read bundled PROJJSON resource", e);
		}
	}

}
