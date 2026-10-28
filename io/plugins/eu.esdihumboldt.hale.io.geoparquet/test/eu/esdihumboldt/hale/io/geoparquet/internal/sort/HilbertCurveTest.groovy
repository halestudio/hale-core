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
package eu.esdihumboldt.hale.io.geoparquet.internal.sort

import static org.junit.Assert.*

import org.junit.Test
import org.locationtech.jts.geom.Envelope

/**
 * Tests for {@link HilbertCurve}.
 */
class HilbertCurveTest {

	@Test
	void testOrder2Grid() {
		def curve = new HilbertCurve(2)
		def cells = [
			[0, 0],
			[0, 1],
			[1, 1],
			[1, 0],
			[2, 0],
			[3, 0],
			[3, 1],
			[2, 1],
			[2, 2],
			[3, 2],
			[3, 3],
			[2, 3],
			[1, 3],
			[1, 2],
			[0, 2],
			[0, 3]
		]
		// the sequence is given as (row, column), the index takes (x, y) = (column, row)
		cells.eachWithIndex { cell, d ->
			assertEquals("cell $cell", d, curve.index(cell[1], cell[0]))
		}
	}

	/** Inverse mapping, d to x/y (standard algorithm). */
	private static int[] d2xy(int n, long d) {
		int x = 0, y = 0
		long t = d
		for (int s = 1; s < n; s *= 2) {
			int rx = (int) ((t >> 1) & 1L)
			int ry = (int) ((t ^ rx) & 1L)
			if (ry == 0) {
				if (rx == 1) {
					x = s - 1 - x
					y = s - 1 - y
				}
				int tmp = x
				x = y
				y = tmp
			}
			x += s * rx
			y += s * ry
			t = t >> 2
		}
		[x, y] as int[]
	}

	@Test
	void testLocality() {
		def curve = HilbertCurve.DEFAULT
		int n = 1 << 16
		def random = new Random(42)
		1000.times {
			long d = (long) (random.nextDouble() * (n * (long) n - 1))
			def a = d2xy(n, d)
			def b = d2xy(n, d + 1)
			assertEquals(d, curve.index(a[0], a[1]))
			assertEquals(d + 1, curve.index(b[0], b[1]))
			// consecutive indices are adjacent cells
			assertEquals(1, Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]))
		}
	}

	@Test
	void testNormalization() {
		def curve = HilbertCurve.DEFAULT
		def extent = new Envelope(10, 20, 100, 200)
		assertEquals(curve.index(0, 0), curve.index(10d, 100d, extent))
		assertEquals(curve.index(65535, 65535), curve.index(20d, 200d, extent))
		// degenerate axis
		def line = new Envelope(5, 5, 0, 10)
		assertEquals(curve.index(0, 65535), curve.index(5d, 10d, line))
		assertEquals(0, curve.index(1d, 1d, new Envelope(1, 1, 1, 1)))
	}
}
