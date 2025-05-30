/*
 * Copyright (c) 2012 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.io.gml.writer.internal.geometry.writers;

import java.text.DecimalFormat;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.Point;

import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.io.gml.writer.internal.geometry.Descent;
import eu.esdihumboldt.hale.io.gml.writer.internal.geometry.GeometryWriter;

/**
 * Write {@link MultiPoint}s
 *
 * @author Simon Templer
 * @partner 01 / Fraunhofer Institute for Computer Graphics Research
 */
public class MultiPointWriter extends AbstractGeometryWriter<MultiPoint> {

	private final PointWriter pointWriter = new PointWriter();

	/**
	 * Default constructor
	 */
	public MultiPointWriter() {
		super(MultiPoint.class);

		// compatible types to serve as entry point
		addCompatibleType(new QName(Pattern.GML_NAMESPACE_PLACEHOLDER, "MultiPointType")); //$NON-NLS-1$

		// patterns for matching inside compatible types
		addBasePattern("*/pointMember"); //$NON-NLS-1$

		// verification patterns (contained Point)
		addVerificationPattern("*/Point"); //$NON-NLS-1$
	}

	/**
	 * @see GeometryWriter#write(XMLStreamWriter, Geometry, TypeDefinition, QName,
	 *      String, DecimalFormat)
	 */
	@Override
	public void write(XMLStreamWriter writer, MultiPoint geometry, TypeDefinition elementType,
			QName elementName, String gmlNs, DecimalFormat decimalFormatter)
			throws XMLStreamException {
		for (int i = 0; i < geometry.getNumGeometries(); i++) {
			if (i > 0) {
				writer.writeStartElement(elementName.getNamespaceURI(), elementName.getLocalPart());
			}

			Descent descent = descend(writer, Pattern.parse("*/Point"), //$NON-NLS-1$
					elementType, elementName, gmlNs, false);

			Point point = (Point) geometry.getGeometryN(i);
			pointWriter.write(writer, point, descent.getPath().getLastType(),
					descent.getPath().getLastElement().getName(), gmlNs, decimalFormatter);

			descent.close();

			if (i < geometry.getNumGeometries() - 1) {
				writer.writeEndElement();
			}
		}
	}

}
