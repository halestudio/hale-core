/*
 * Copyright (c) 2015 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.io.wfs.capabilities

import groovy.transform.CompileStatic
import groovy.transform.Immutable

import javax.annotation.Nullable
import javax.xml.namespace.QName

import eu.esdihumboldt.hale.io.wfs.WFSVersion


/**
 * Encapsulates information we need from WFS capabilities
 * @author Simon Templer
 */
@CompileStatic
@Immutable
class WFSCapabilities {
	WFSVersion version
	Map<String, WFSOperation> operations

	/**
	 * Qualified names mapped to exact name from list.
	 */
	Map<QName, FeatureTypeInfo> featureTypes

	@Nullable
	WFSOperation getTransactionOp() {
		operations['Transaction']
	}

	@Nullable
	WFSOperation getDescribeFeatureOp() {
		operations['DescribeFeatureType']
	}

	@Nullable
	WFSOperation getGetFeatureOp() {
		operations['GetFeature']
	}
}

@CompileStatic
@Immutable(knownImmutableClasses = [QName.class])
class FeatureTypeInfo {
	QName name
	String defaultCrs
	BBox wgs84BBox //TODO support multiple BBs?
}

@CompileStatic
@Immutable
class BBox {
	double x1
	double y1
	double x2
	double y2
}

@CompileStatic
@Immutable
class WFSOperation {
	String name
	String httpPostUrl
	String httpGetUrl
}
