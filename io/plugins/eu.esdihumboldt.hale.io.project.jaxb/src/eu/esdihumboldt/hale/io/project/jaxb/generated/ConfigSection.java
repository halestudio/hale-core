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

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * <p>
 * Java class for configSection complex type.
 *
 * <p>
 * The following schema fragment specifies the expected content contained within
 * this class.
 *
 * <pre>
 * &lt;complexType name="configSection">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="data" type="{}configData" maxOccurs="unbounded"/>
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
@XmlType(name = "configSection", propOrder = { "name", "data" })
public class ConfigSection {

	@XmlElement(required = true)
	protected String name;
	@XmlElement(required = true)
	protected List<ConfigData> data;

	/**
	 * Gets the value of the name property.
	 *
	 * @return possible object is {@link String }
	 *
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the value of the name property.
	 *
	 * @param value allowed object is {@link String }
	 *
	 */
	public void setName(String value) {
		this.name = value;
	}

	/**
	 * Gets the value of the data property.
	 *
	 * <p>
	 * This accessor method returns a reference to the live list, not a snapshot.
	 * Therefore any modification you make to the returned list will be present
	 * inside the JAXB object. This is why there is not a <CODE>set</CODE> method
	 * for the data property.
	 *
	 * <p>
	 * For example, to add a new item, do as follows:
	 *
	 * <pre>
	 * getData().add(newItem);
	 * </pre>
	 *
	 *
	 * <p>
	 * Objects of the following type(s) are allowed in the list {@link ConfigData }
	 *
	 *
	 */
	public List<ConfigData> getData() {
		if (data == null) {
			data = new ArrayList<ConfigData>();
		}
		return this.data;
	}

}
