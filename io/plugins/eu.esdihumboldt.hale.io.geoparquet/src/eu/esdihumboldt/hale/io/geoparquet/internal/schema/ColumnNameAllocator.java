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
package eu.esdihumboldt.hale.io.geoparquet.internal.schema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import eu.esdihumboldt.hale.common.core.report.SimpleLog;

/**
 * Allocates unique field names for the properties mapped into one Parquet
 * struct (or the top level of a message).
 */
final class ColumnNameAllocator {

	/**
	 * Prefix for XML attributes whose name collides with an element name.
	 */
	static final String ATTRIBUTE_PREFIX = "attr_";

	private static final String GML_NAMESPACE_PREFIX = "http://www.opengis.net/gml";

	/**
	 * A property that needs a field name.
	 *
	 * @param name the property name
	 * @param attribute if the property is an XML attribute
	 */
	record Candidate(QName name, boolean attribute) {
	}

	private ColumnNameAllocator() {
	}

	/**
	 * Allocate names. The first candidate for a name keeps it (preferring
	 * candidates not in a GML namespace), the others get the suffixes
	 * <code>_2</code>, <code>_3</code>, ... in definition order. Reserved names are
	 * never given to a candidate as is. XML attributes that collide with an element
	 * name are prefixed with {@value #ATTRIBUTE_PREFIX}.
	 *
	 * @param candidates the candidates in definition order
	 * @param reserved the reserved names
	 * @param log the log for reporting renamed fields
	 * @return the names, in the order of the candidates
	 */
	static List<String> allocate(List<Candidate> candidates, Set<String> reserved, SimpleLog log) {
		Set<String> elementNames = new HashSet<>();
		for (Candidate c : candidates) {
			if (!c.attribute()) {
				elementNames.add(c.name().getLocalPart());
			}
		}

		String[] bases = new String[candidates.size()];
		Map<String, List<Integer>> byBase = new LinkedHashMap<>();
		for (int i = 0; i < bases.length; i++) {
			Candidate c = candidates.get(i);
			String local = c.name().getLocalPart();
			bases[i] = c.attribute() && elementNames.contains(local) ? ATTRIBUTE_PREFIX + local
					: local;
			byBase.computeIfAbsent(bases[i], k -> new ArrayList<>()).add(i);
		}

		String[] names = new String[bases.length];
		Set<String> used = new HashSet<>(reserved);
		// first pass: names that are kept
		byBase.forEach((base, indices) -> {
			if (reserved.contains(base)) {
				return;
			}
			int winner = indices.stream().filter(i -> !isGml(candidates.get(i).name())).findFirst()
					.orElse(indices.get(0));
			names[winner] = base;
			used.add(base);
		});
		// second pass: suffixes in definition order
		for (int i = 0; i < names.length; i++) {
			if (names[i] == null) {
				int n = 2;
				while (used.contains(bases[i] + "_" + n)) {
					n++;
				}
				names[i] = bases[i] + "_" + n;
				used.add(names[i]);
				log.warn("Column name {0} is already used, using {1} instead", bases[i], names[i]);
			}
		}
		return List.of(names);
	}

	private static boolean isGml(QName name) {
		String ns = name.getNamespaceURI();
		return ns != null && ns.startsWith(GML_NAMESPACE_PREFIX);
	}

}
