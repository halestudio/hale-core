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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.instance.model.Filter;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.instance.model.MetaFilter;
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator;
import eu.esdihumboldt.hale.common.instance.model.ext.InstanceCollection2;
import eu.esdihumboldt.hale.common.instance.model.ext.InstanceIterator;
import eu.esdihumboldt.hale.common.instance.model.impl.FilteredInstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.impl.InstanceDecorator;
import eu.esdihumboldt.hale.common.instance.model.impl.InstanceReferenceDecorator;
import eu.esdihumboldt.hale.common.instance.store.sqlite.codec.StoredInstance;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;

/**
 * Instance collection on a SQLite instance store. Iterates committed instances
 * of mapping relevant types in id order.
 */
public class StoreInstanceCollection implements InstanceCollection2 {

	static final int CHUNK_SIZE = 1000;
	static final int MAX_FILTER_VALUES = 30_000;

	private static final ALogger log = ALoggerFactory.getLogger(StoreInstanceCollection.class);

	private record Row(long id, int typeId, byte[] payload) {
	}

	private final StoreContext ctx;
	private final TypeIndex types;
	/** allowed types by name */
	private final Map<QName, TypeDefinition> allowedTypes;
	/** metadata condition, may be <code>null</code> */
	private final String metaKey;
	private final List<String> metaValues;

	StoreInstanceCollection(StoreContext ctx, TypeIndex types) {
		this(ctx, types, mappingRelevant(types), null, null);
	}

	private StoreInstanceCollection(StoreContext ctx, TypeIndex types,
			Map<QName, TypeDefinition> allowedTypes, String metaKey, List<String> metaValues) {
		this.ctx = ctx;
		this.types = types;
		this.allowedTypes = allowedTypes;
		this.metaKey = metaKey;
		this.metaValues = metaValues;
	}

	private static Map<QName, TypeDefinition> mappingRelevant(TypeIndex types) {
		if (types == null) {
			return Collections.emptyMap();
		}
		Map<QName, TypeDefinition> result = new LinkedHashMap<>();
		for (TypeDefinition type : types.getMappingRelevantTypes()) {
			result.put(type.getName(), type);
		}
		return result;
	}

	/**
	 * @return the allowed types present in the store, by type id
	 */
	private Map<Integer, TypeDefinition> resolveTypeIds() {
		Map<Integer, TypeDefinition> result = new HashMap<>();
		for (Map.Entry<QName, TypeDefinition> entry : allowedTypes.entrySet()) {
			Integer id = ctx.types.findId(entry.getKey());
			if (id != null) {
				result.put(id, entry.getValue());
			}
		}
		return result;
	}

	@Override
	public ResourceIterator<Instance> iterator() {
		return new StoreIterator(resolveTypeIds());
	}

	@Override
	public boolean hasSize() {
		return metaKey == null;
	}

	@Override
	public int size() {
		if (!hasSize()) {
			return UNKNOWN_SIZE;
		}
		long sum = 0;
		for (Integer typeId : resolveTypeIds().keySet()) {
			sum += ctx.committedCount(typeId);
		}
		return (int) Math.min(Integer.MAX_VALUE, sum);
	}

	@Override
	public boolean isEmpty() {
		if (hasSize()) {
			return size() == 0;
		}
		try (ResourceIterator<Instance> it = iterator()) {
			return !it.hasNext();
		}
	}

	@Override
	public boolean supportsFanout() {
		return metaKey == null;
	}

	@Override
	public Map<TypeDefinition, InstanceCollection> fanout() {
		if (!supportsFanout()) {
			return null;
		}
		Map<TypeDefinition, InstanceCollection> result = new LinkedHashMap<>();
		for (TypeDefinition type : resolveTypeIds().values()) {
			result.put(type, new StoreInstanceCollection(ctx, types,
					Collections.singletonMap(type.getName(), type), null, null));
		}
		return result;
	}

	@Override
	public InstanceCollection select(Filter filter) {
		if (filter instanceof MetaFilter && metaKey == null) {
			InstanceCollection result = selectMeta((MetaFilter) filter);
			if (result != null) {
				return result;
			}
		}
		// TypeFilter and TypeAwareFilter are handled via fan-out
		return FilteredInstanceCollection.applyFilter(this, filter);
	}

	private InstanceCollection selectMeta(MetaFilter filter) {
		Set<? extends Object> values = filter.getValues();
		if (values.isEmpty() || values.size() > MAX_FILTER_VALUES
				|| !values.stream().allMatch(v -> v instanceof String)) {
			return null;
		}
		Map<QName, TypeDefinition> restricted = allowedTypes;
		if (filter.getType() != null) {
			TypeDefinition type = filter.getType();
			restricted = allowedTypes.containsKey(type.getName())
					? Collections.singletonMap(type.getName(), type)
					: Collections.emptyMap();
		}
		List<String> stringValues = values.stream().map(String.class::cast)
				.collect(Collectors.toList());
		return new StoreInstanceCollection(ctx, types, restricted, filter.getMetadataKey(),
				stringValues);
	}

	@Override
	public InstanceReference getReference(Instance instance) {
		Instance root = InstanceDecorator.getRoot(instance);
		if (root instanceof StoredInstance) {
			StoredInstance stored = (StoredInstance) root;
			if (stored.getStoreKey().equals(ctx.key)) {
				return new StoreInstanceReference(ctx.key, stored.getStoreId(), ctx.dataSet,
						stored.getDefinition());
			}
		}
		return null;
	}

	@Override
	public Instance getInstance(InstanceReference reference) {
		InstanceReference root = InstanceReferenceDecorator.getRootReference(reference);
		if (root instanceof StoreInstanceReference) {
			StoreInstanceReference ref = (StoreInstanceReference) root;
			if (ref.getStoreKey().equals(ctx.key)) {
				return ctx.load(ref.getStoreId(), ref.getType(), types);
			}
		}
		return null;
	}

	private String buildQuery(Set<Integer> typeIds) {
		StringBuilder sql = new StringBuilder(
				"SELECT id, type, payload FROM instances WHERE id > ? AND type IN (");
		sql.append(typeIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
		sql.append(")");
		if (metaKey != null) {
			sql.append(" AND id IN (SELECT instance FROM metadata WHERE key = ? AND value IN (");
			sql.append(String.join(",", Collections.nCopies(metaValues.size(), "?")));
			sql.append("))");
		}
		sql.append(" ORDER BY id LIMIT ").append(CHUNK_SIZE);
		return sql.toString();
	}

	private class StoreIterator implements InstanceIterator {

		private final Map<Integer, TypeDefinition> typeIds;
		private final String query;
		private final Deque<Row> buffer = new ArrayDeque<>();
		private long lastId = 0;
		private boolean exhausted;
		private Instance decoded;

		StoreIterator(Map<Integer, TypeDefinition> typeIds) {
			this.typeIds = typeIds;
			this.exhausted = typeIds.isEmpty();
			this.query = exhausted ? null : buildQuery(typeIds.keySet());
		}

		private Row peekRow() {
			if (buffer.isEmpty() && !exhausted) {
				fetch();
			}
			return buffer.peekFirst();
		}

		private void fetch() {
			List<Row> rows = ctx.database.withReader(c -> {
				List<Row> result = new ArrayList<>();
				try (PreparedStatement p = c.prepareStatement(query)) {
					int index = 1;
					p.setLong(index++, lastId);
					if (metaKey != null) {
						p.setString(index++, metaKey);
						for (String value : metaValues) {
							p.setString(index++, value);
						}
					}
					try (ResultSet rs = p.executeQuery()) {
						while (rs.next()) {
							result.add(new Row(rs.getLong(1), rs.getInt(2), rs.getBytes(3)));
						}
					}
				}
				return result;
			});
			if (rows.size() < CHUNK_SIZE) {
				exhausted = true;
			}
			if (!rows.isEmpty()) {
				lastId = rows.get(rows.size() - 1).id();
			}
			buffer.addAll(rows);
		}

		@Override
		public boolean hasNext() {
			if (decoded != null) {
				return true;
			}
			Row row;
			while ((row = peekRow()) != null) {
				buffer.pollFirst();
				try {
					decoded = ctx.codec.decode(row.payload(), typeIds.get(row.typeId()),
							ctx.dataSet, ctx.key, row.id(), types);
					return true;
				} catch (RuntimeException e) {
					log.error("Could not read instance " + row.id()
							+ " from the temporary database, skipping it", e);
				}
			}
			return false;
		}

		@Override
		public Instance next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			Instance result = decoded;
			decoded = null;
			return result;
		}

		@Override
		public TypeDefinition typePeek() {
			if (decoded != null) {
				return decoded.getDefinition();
			}
			Row row = peekRow();
			return row == null ? null : typeIds.get(row.typeId());
		}

		@Override
		public boolean supportsTypePeek() {
			return true;
		}

		@Override
		public void skip() {
			if (decoded != null) {
				decoded = null;
			}
			else if (peekRow() != null) {
				buffer.pollFirst();
			}
		}

		@Override
		public void close() {
			// no resources held between chunks
		}
	}

}
