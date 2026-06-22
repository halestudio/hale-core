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
package eu.esdihumboldt.hale.common.lookup.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.google.common.collect.ListMultimap;

import eu.esdihumboldt.hale.common.core.io.Value;
import eu.esdihumboldt.hale.common.lookup.LookupTable;
import eu.esdihumboldt.util.test.AbstractPlatformTest;

/**
 * Tests for {@link LookupTableImpl}, in particular the handling of entries with
 * a <code>null</code> target value.
 *
 * @author Simon Templer
 */
public class LookupTableImplTest extends AbstractPlatformTest {

	/**
	 * An entry whose target value is <code>null</code> (i.e. a source key with no
	 * mapped target) must be preserved by the table instead of being silently
	 * dropped.
	 */
	@Test
	public void testNullTargetValueIsPreserved() {
		Value key = Value.of("source");

		Map<Value, Value> values = new LinkedHashMap<Value, Value>();
		values.put(key, null);

		LookupTable table = new LookupTableImpl(values);

		assertTrue("Key with null target value must be retained in the key set",
				table.getKeys().contains(key));
		assertEquals("Key with null target value must be retained in the map", 1,
				table.asMap().size());
		assertTrue(table.asMap().containsKey(key));
		assertNull("Lookup of a key with no mapped target must return null", table.lookup(key));
	}

	/**
	 * Entries with non-null target values must keep behaving exactly as before -
	 * they are retained and resolvable - even when the table also contains an entry
	 * with a null target value.
	 */
	@Test
	public void testNonNullValuesUnaffectedByNullEntry() {
		Value mappedKey = Value.of("a");
		Value mappedValue = Value.of("1");
		Value unmappedKey = Value.of("b");

		Map<Value, Value> values = new LinkedHashMap<Value, Value>();
		values.put(mappedKey, mappedValue);
		values.put(unmappedKey, null);

		LookupTable table = new LookupTableImpl(values);

		assertEquals(2, table.getKeys().size());
		assertEquals(mappedValue, table.lookup(mappedKey));
		assertNull(table.lookup(unmappedKey));
	}

	/**
	 * {@link LookupTableImpl#reverse()} must skip entries with a null value (a null
	 * key is not permitted by the underlying multimap) while still reversing all
	 * regular entries.
	 */
	@Test
	public void testReverseSkipsNullValues() {
		Value mappedKey = Value.of("a");
		Value mappedValue = Value.of("1");
		Value unmappedKey = Value.of("b");

		Map<Value, Value> values = new LinkedHashMap<Value, Value>();
		values.put(mappedKey, mappedValue);
		values.put(unmappedKey, null);

		// must not throw despite the null value
		ListMultimap<Value, Value> reverse = new LookupTableImpl(values).reverse();

		assertEquals("Only the non-null entry must appear in the reverse table", 1, reverse.size());
		assertTrue(reverse.get(mappedValue).contains(mappedKey));
		assertFalse("A null value must not become a key in the reverse table",
				reverse.containsKey(null));
	}

	/**
	 * Regression check: multiple keys mapping to the same value must be grouped
	 * under that value in the reverse representation.
	 */
	@Test
	public void testReverseGroupsKeysBySharedValue() {
		Value sharedValue = Value.of("1");

		Map<Value, Value> values = new LinkedHashMap<Value, Value>();
		values.put(Value.of("a"), sharedValue);
		values.put(Value.of("b"), sharedValue);

		ListMultimap<Value, Value> reverse = new LookupTableImpl(values).reverse();

		assertEquals(2, reverse.get(sharedValue).size());
		assertTrue(reverse.get(sharedValue).contains(Value.of("a")));
		assertTrue(reverse.get(sharedValue).contains(Value.of("b")));
	}

}
