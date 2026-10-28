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
package eu.esdihumboldt.hale.io.geoparquet.internal.sort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.locationtech.jts.geom.Envelope;

import eu.esdihumboldt.hale.common.instance.model.InstanceReference;

/**
 * Collects instance references with the center of their geometry and provides
 * them ordered along a Hilbert curve. Memory use is about 8 bytes per geometry
 * for the center coordinates, 8 bytes for the sort key and the reference itself
 * (plus 4-8 bytes for the list slot).
 */
public class SortIndex {

	private static final int INDEX_BITS = 31;

	private final HilbertCurve curve;
	private final Envelope extent = new Envelope();
	private final List<InstanceReference> refs = new ArrayList<>();

	/** Centers, NaN for instances without geometry. */
	private double[] xs = new double[16];
	private double[] ys = new double[16];

	/**
	 * Create a sort index using the default curve.
	 */
	public SortIndex() {
		this(HilbertCurve.DEFAULT);
	}

	/**
	 * Create a sort index.
	 *
	 * @param curve the curve to sort by
	 */
	public SortIndex(HilbertCurve curve) {
		this.curve = curve;
	}

	/**
	 * Add an instance with a geometry.
	 *
	 * @param centerX the x coordinate of the geometry center
	 * @param centerY the y coordinate of the geometry center
	 * @param ref the reference to the instance
	 */
	public void add(double centerX, double centerY, InstanceReference ref) {
		store(centerX, centerY, ref);
		extent.expandToInclude(centerX, centerY);
	}

	/**
	 * Add an instance without geometry, it is sorted after all others.
	 *
	 * @param ref the reference to the instance
	 */
	public void addWithoutGeometry(InstanceReference ref) {
		store(Double.NaN, Double.NaN, ref);
	}

	private void store(double x, double y, InstanceReference ref) {
		int size = refs.size();
		if (size == Integer.MAX_VALUE >> 1) {
			throw new IllegalStateException("Too many instances to sort");
		}
		if (size == xs.length) {
			xs = Arrays.copyOf(xs, size * 2);
			ys = Arrays.copyOf(ys, size * 2);
		}
		xs[size] = x;
		ys[size] = y;
		refs.add(ref);
	}

	/**
	 * @return the extent of the centers added, null envelope if there are none
	 */
	public Envelope getExtent() {
		return new Envelope(extent);
	}

	/**
	 * @return the number of references added
	 */
	public int size() {
		return refs.size();
	}

	/**
	 * Get the references ordered by Hilbert index of the geometry center, ties in
	 * insertion order, followed by the instances without geometry in insertion
	 * order.
	 *
	 * @return the sorted references
	 */
	public List<InstanceReference> sorted() {
		int size = refs.size();
		// key (32 bits) and insertion position (31 bits) packed into one long, so a
		// primitive sort is stable with respect to insertion order
		long[] keys = new long[size];
		int count = 0;
		for (int i = 0; i < size; i++) {
			if (!Double.isNaN(xs[i])) {
				keys[count++] = (curve.index(xs[i], ys[i], extent) << INDEX_BITS) | i;
			}
		}
		Arrays.sort(keys, 0, count);

		long mask = (1L << INDEX_BITS) - 1;
		List<InstanceReference> result = new ArrayList<>(size);
		for (int i = 0; i < count; i++) {
			result.add(refs.get((int) (keys[i] & mask)));
		}
		for (int i = 0; i < size; i++) {
			if (Double.isNaN(xs[i])) {
				result.add(refs.get(i));
			}
		}
		return result;
	}

}
