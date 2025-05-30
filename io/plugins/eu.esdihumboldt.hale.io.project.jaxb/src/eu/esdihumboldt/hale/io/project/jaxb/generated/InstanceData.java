/*
 * Copyright (c) 2011 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.io.project.jaxb.generated;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * <p>
 * Java class for InstanceData complex type.
 *
 * <p>
 * The following schema fragment specifies the expected content contained within
 * this class.
 *
 * <pre>
 * &lt;complexType name="InstanceData">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="path" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="wkt" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="epsgcode" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="type" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 *
 *
 */
@SuppressWarnings("all")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InstanceData", propOrder = { "path", "wkt", "epsgcode", "type" })
public class InstanceData {

	@XmlElement(required = true)
	protected String path;
	protected String wkt;
	protected String epsgcode;
	protected String type;

	/**
	 * Gets the value of the path property.
	 *
	 * @return possible object is {@link String }
	 *
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Sets the value of the path property.
	 *
	 * @param value allowed object is {@link String }
	 *
	 */
	public void setPath(String value) {
		this.path = value;
	}

	/**
	 * Gets the value of the wkt property.
	 *
	 * @return possible object is {@link String }
	 *
	 */
	public String getWkt() {
		return wkt;
	}

	/**
	 * Sets the value of the wkt property.
	 *
	 * @param value allowed object is {@link String }
	 *
	 */
	public void setWkt(String value) {
		this.wkt = value;
	}

	/**
	 * Gets the value of the epsgcode property.
	 *
	 * @return possible object is {@link String }
	 *
	 */
	public String getEpsgcode() {
		return epsgcode;
	}

	/**
	 * Sets the value of the epsgcode property.
	 *
	 * @param value allowed object is {@link String }
	 *
	 */
	public void setEpsgcode(String value) {
		this.epsgcode = value;
	}

	/**
	 * Gets the value of the type property.
	 *
	 * @return possible object is {@link String }
	 *
	 */
	public String getType() {
		return type;
	}

	/**
	 * Sets the value of the type property.
	 *
	 * @param value allowed object is {@link String }
	 *
	 */
	public void setType(String value) {
		this.type = value;
	}

}
