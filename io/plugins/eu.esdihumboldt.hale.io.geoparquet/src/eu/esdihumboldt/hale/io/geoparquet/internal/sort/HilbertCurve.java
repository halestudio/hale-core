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

import org.locationtech.jts.geom.Envelope;

/**
 * Hilbert curve index on a square grid with 2^order cells per axis. Cells that
 * are close on the curve are close in space.
 */
public class HilbertCurve {

	/** The maximum supported order, indices then fit into 32 bits. */
	public static final int MAX_ORDER = 16;

	/** The curve with the default order of 16 (65536 x 65536 grid). */
	public static final HilbertCurve DEFAULT = new HilbertCurve(MAX_ORDER);

	private final int order;
	private final int n;

	/**
	 * Create a curve.
	 *
	 * @param order the order, the grid has 2^order cells per axis
	 */
	public HilbertCurve(int order) {
		if (order < 1 || order > MAX_ORDER) {
			throw new IllegalArgumentException("Order must be between 1 and " + MAX_ORDER);
		}
		this.order = order;
		this.n = 1 << order;
	}

	/**
	 * @return the order of the curve
	 */
	public int getOrder() {
		return order;
	}

	/**
	 * Determine the position of a grid cell on the curve.
	 *
	 * @param x the cell column, between 0 and 2^order - 1
	 * @param y the cell row, between 0 and 2^order - 1
	 * @return the index on the curve
	 */
	public long index(int x, int y) {
		long d = 0;
		for (int s = n / 2; s > 0; s /= 2) {
			int rx = (x & s) > 0 ? 1 : 0;
			int ry = (y & s) > 0 ? 1 : 0;
			d += (long) s * s * ((3 * rx) ^ ry);
			if (ry == 0) {
				if (rx == 1) {
					x = s - 1 - x;
					y = s - 1 - y;
				}
				int t = x;
				x = y;
				y = t;
			}
		}
		return d;
	}

	/**
	 * Determine the position of a point on the curve, after normalizing it into the
	 * grid covering the given extent.
	 *
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @param extent the extent covered by the grid; an axis without extent maps to
	 *            cell 0
	 * @return the index on the curve
	 */
	public long index(double x, double y, Envelope extent) {
		return index(cell(x, extent.getMinX(), extent.getWidth()),
				cell(y, extent.getMinY(), extent.getHeight()));
	}

	private int cell(double value, double min, double size) {
		if (!(size > 0)) {
			return 0;
		}
		int cell = (int) ((value - min) / size * n);
		return Math.max(0, Math.min(n - 1, cell));
	}

}
