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
package eu.esdihumboldt.hale.io.geoparquet

import static eu.esdihumboldt.hale.io.geoparquet.GeoParquetTestUtil.*
import static org.junit.Assert.*

import org.junit.Test
import org.locationtech.jts.geom.Envelope

import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition
import eu.esdihumboldt.hale.common.instance.groovy.InstanceBuilder
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty
import eu.esdihumboldt.hale.common.schema.groovy.SchemaBuilder
import eu.esdihumboldt.hale.common.schema.model.Schema
import eu.esdihumboldt.hale.io.geoparquet.internal.sort.HilbertCurve
import eu.esdihumboldt.util.test.AbstractPlatformTest

/**
 * Tests for the spatial sorting of {@link GeoParquetInstanceWriter}.
 */
class GeoParquetSortTest extends AbstractPlatformTest {

	private static GeometryProperty point(double x, double y) {
		// projected CRS, coordinates are written as they are
		GeoParquetInstanceWriterTest.createGeometry("POINT($x $y)", new CodeDefinition('EPSG:25832', true))
	}

	private Schema pointSchema() {
		new SchemaBuilder().schema {
			feature {
				id(Integer)
				geom(GeometryProperty)
			}
		}
	}

	@Test
	void testRowsAreSpatiallySorted() {
		Schema schema = pointSchema()
		def cells = []
		100.times { x ->
			100.times { y ->
				cells << [x, y]
			}
		}
		Collections.shuffle(cells, new Random(1))
		InstanceCollection instances = new InstanceBuilder(types: schema).createCollection {
			cells.eachWithIndex { cell, i ->
				feature {
					id(i)
					geom(point(cell[0] * 1000d + 500000, cell[1] * 1000d + 5500000))
				}
			}
		}

		withNewGeoParquet(schema, instances, { List<File> files ->
			def rows = readRows(files[0])
			assertEquals(10000, rows.size())
			def points = rows.collect { wkb(it, 'geometry') }

			def extent = new Envelope()
			points.each { extent.expandToInclude(it.x, it.y) }
			long previous = -1
			points.each {
				long index = HilbertCurve.DEFAULT.index(it.x, it.y, extent)
				assertTrue("rows not ordered by Hilbert index", index >= previous)
				previous = index
			}

			// Portolan heuristic: row group bounding boxes of consecutive row groups
			// rarely overlap
			assertEquals(10, readFooter(files[0]).blocks.size())
			assertTrue(overlapRate(points) < 0.3)
		}, { it.setRowGroupRowLimit(1000) })

		// the measure tells sorted from unsorted data
		withNewGeoParquet(schema, instances, { List<File> files ->
			def points = readRows(files[0]).collect { wkb(it, 'geometry') }
			assertTrue(overlapRate(points) > 0.7)
		}, {
			it.setRowGroupRowLimit(1000)
			it.setSort(false)
		}, true, ['PTL-DAT-006']) // Portolan rightly reports the unsorted data
	}

	/**
	 * Fraction of pairs of consecutive row groups (1000 rows) that overlap
	 * substantially, i.e. where the intersection of the bounding boxes covers
	 * more than half of the smaller box. Bounding boxes of consecutive Hilbert
	 * curve segments always share some area where the segment boundaries are not
	 * aligned with the quadrants, so mere intersection is not a useful measure
	 * for a small data set.
	 */
	private static double overlapRate(List points) {
		def boxes = points.collate(1000).collect { group ->
			def box = new Envelope()
			group.each { box.expandToInclude(it.x, it.y) }
			box
		}
		int overlaps = (1..<boxes.size()).count {
			def a = boxes[it - 1]
			def b = boxes[it]
			def i = a.intersection(b)
			!i.isNull() && i.area > 0.5 * Math.min(a.area, b.area)
		}
		overlaps / (double) (boxes.size() - 1)
	}

	@Test
	void testFeaturesWithoutGeometrySortedLast() {
		Schema schema = pointSchema()
		InstanceCollection instances = new InstanceBuilder(types: schema).createCollection {
			feature {
				id(0)
			}
			feature {
				id(1)
				geom(point(900000, 5900000))
			}
			feature {
				id(2)
			}
			feature {
				id(3)
				geom(point(500000, 5500000))
			}
			feature {
				id(4)
				geom(point(700000, 5700000))
			}
			feature {
				id(5)
			}
		}

		withNewGeoParquet(schema, instances, { List<File> files ->
			def rows = readRows(files[0])
			assertEquals(6, rows.size())
			def ids = rows.collect { it.getInteger('id', 0) }
			// geometries first (along the curve), the others in input order
			assertEquals(3, ids.findAll { it in [1, 3, 4] }.size() as int)
			assertEquals([0, 2, 5], ids[3..5])
			assertEquals(3, ids[0])
			rows[0..2].each { assertTrue(it.getFieldRepetitionCount('geometry') > 0) }
			rows[3..5].each { assertEquals(0, it.getFieldRepetitionCount('geometry')) }
		})
	}

	@Test
	void testSortWithMultipleTypes() {
		Schema schema = new SchemaBuilder().schema {
			alpha {
				id(Integer)
				geom(GeometryProperty)
			}
			beta {
				id(Integer)
				geom(GeometryProperty)
			}
		}
		def positions = []
		10.times { x ->
			10.times { y ->
				positions << [x, y]
			}
		}
		Collections.shuffle(positions, new Random(2))
		InstanceCollection instances = new InstanceBuilder(types: schema).createCollection {
			positions.eachWithIndex { p, i ->
				alpha {
					id(i)
					geom(point(p[0] * 1000d + 500000, p[1] * 1000d + 5500000))
				}
				beta {
					id(i)
					geom(point(p[1] * 1000d + 500000, p[0] * 1000d + 5500000))
				}
			}
		}

		withNewGeoParquet(schema, instances, { List<File> files ->
			assertEquals([
				'out_alpha.parquet',
				'out_beta.parquet'
			], files*.name)
			files.each { file ->
				def points = readRows(file).collect { wkb(it, 'geometry') }
				assertEquals(100, points.size())
				def extent = new Envelope()
				points.each { extent.expandToInclude(it.x, it.y) }
				long previous = -1
				points.each {
					long index = HilbertCurve.DEFAULT.index(it.x, it.y, extent)
					assertTrue(file.name, index >= previous)
					previous = index
				}
			}
		})
	}

	@Test
	void testPassthroughFlag() {
		def writer = new GeoParquetInstanceWriter()
		assertFalse(writer.isPassthrough())
		writer.setSort(false)
		assertTrue(writer.isPassthrough())
		writer.setSort(true)
		assertFalse(writer.isPassthrough())
	}
}
