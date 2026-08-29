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
package eu.esdihumboldt.hale.common.instance.io.impl;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;

import eu.esdihumboldt.hale.common.core.io.IOProviderConfigurationException;
import eu.esdihumboldt.hale.common.core.io.ProgressIndicator;
import eu.esdihumboldt.hale.common.core.io.report.IOReport;
import eu.esdihumboldt.hale.common.core.io.report.IOReporter;
import eu.esdihumboldt.hale.common.instance.io.impl.AbstractGeoInstanceWriter.FeatureContext;
import eu.esdihumboldt.hale.common.instance.io.util.EnumWindingOrderTypes;
import eu.esdihumboldt.util.test.AbstractPlatformTest;

/**
 * Tests for {@link AbstractGeoInstanceWriter}, in particular that
 * geometry-related errors encountered while unifying the winding order are
 * enriched with information identifying the affected source element, while
 * the current behavior (the error still fails the write) is preserved.
 */
public class AbstractGeoInstanceWriterTest extends AbstractPlatformTest {

	/**
	 * Minimal concrete writer used only to invoke the protected geometry
	 * handling methods under test.
	 */
	private static class TestWriter extends AbstractGeoInstanceWriter {

		@Override
		protected IOReport execute(ProgressIndicator progress, IOReporter reporter)
				throws IOProviderConfigurationException, IOException {
			throw new UnsupportedOperationException("not needed for this test");
		}

		@Override
		public String getDefaultTypeName() {
			return "Test Writer";
		}

		@Override
		public boolean isCancelable() {
			return false;
		}

		@Override
		public boolean isPassthrough() {
			return false;
		}

		@Override
		protected EnumWindingOrderTypes getDefaultWindingOrder() {
			return EnumWindingOrderTypes.counterClockwise;
		}

	}

	/**
	 * Build a "collapsed" ring with only 3 points (2 distinct points plus the
	 * closing point). JTS allows constructing such a ring, as it can occur in
	 * (invalid) source data and needs to be representable so it can be
	 * reported/repaired - see {@link LinearRing}'s class documentation.
	 * Determining its orientation is not possible though, and fails exactly
	 * like the invalid geometry from SVC-2232/ING-5109 with
	 * "Ring has fewer than 4 points, so orientation cannot be determined".
	 *
	 * @return a collapsed, 3-point ring
	 */
	private static LinearRing createCollapsedRing() {
		GeometryFactory factory = new GeometryFactory();
		return factory.createLinearRing(new Coordinate[] { new Coordinate(0, 0),
				new Coordinate(1, 1), new Coordinate(0, 0) });
	}

	/**
	 * On an invalid source geometry, unifying the winding order must still
	 * fail (no change in behavior), but the error message must be enriched
	 * with the identifying information of the affected feature and a WKT
	 * excerpt of the geometry, where available.
	 */
	@Test
	public void testGeometryErrorIncludesFeatureContext() {
		TestWriter writer = new TestWriter();
		LinearRing invalid = createCollapsedRing();

		FeatureContext context = new FeatureContext("test:ExampleFeatureType", "feature-42");
		writer.setCurrentFeatureContext(context);

		GeometryProcessingException error = assertThrows(GeometryProcessingException.class,
				() -> writer.unifyGeometry(invalid, null, null));

		assertTrue(error.getMessage().contains("test:ExampleFeatureType"));
		assertTrue(error.getMessage().contains("feature-42"));
		assertTrue(error.getMessage().contains("LINEARRING"));
		assertTrue(error.getCause() instanceof IllegalArgumentException);
	}

	/**
	 * Without a feature context set (e.g. for writers that don't provide
	 * one), the error is still raised, just without the feature-identifying
	 * parts of the message.
	 */
	@Test
	public void testGeometryErrorWithoutFeatureContext() {
		TestWriter writer = new TestWriter();
		LinearRing invalid = createCollapsedRing();

		GeometryProcessingException error = assertThrows(GeometryProcessingException.class,
				() -> writer.unifyGeometry(invalid, null, null));

		assertTrue(error.getMessage().contains("LINEARRING"));
	}

}
