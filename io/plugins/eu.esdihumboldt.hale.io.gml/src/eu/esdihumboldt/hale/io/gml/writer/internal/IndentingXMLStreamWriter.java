/*
 * Copyright (c) 2001-2009 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.io.gml.writer.internal;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * This class is a pretty print wrapper for XMLStreamWriter.
 *
 * @author <a href="mailto:tonnhofer@lat-lon.de">Oliver Tonnhofer</a>
 * @author last edited by: $Author$
 *
 * @version $Revision$, $Date$
 */
public class IndentingXMLStreamWriter extends PrefixAwareStreamWriterDecorator {

	private final String indent;

	private int level = 0;

	private boolean lastWasStart = false;

	/**
	 * Create a new {@link IndentingXMLStreamWriter} instance with default
	 * indentation and no whitespace stripping.
	 *
	 * @param xmlStreamWriter the internal writer
	 */
	public IndentingXMLStreamWriter(XMLStreamWriter xmlStreamWriter) {
		super(xmlStreamWriter);
		this.indent = "  "; //$NON-NLS-1$
	}

	/**
	 * Create a new {@link IndentingXMLStreamWriter} instance with the specified
	 * indentation and whitespace stripping policy.
	 *
	 * @param xmlWriter the internal writer
	 * @param indent the indent string for each indent level, must not be
	 *            <code>null</code>
	 */
	public IndentingXMLStreamWriter(XMLStreamWriter xmlWriter, String indent) {
		super(xmlWriter);
		this.indent = indent;
	}

	@Override
	public void writeComment(String data) throws XMLStreamException {
		indent();
		super.writeComment(data);
		unindent();
	}

	@Override
	public void writeEmptyElement(String localName) throws XMLStreamException {
		indent();
		super.writeEmptyElement(localName);
		unindent();
	}

	@Override
	public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
		indent();
		super.writeEmptyElement(namespaceURI, localName);
		unindent();
	}

	@Override
	public void writeEmptyElement(String prefix, String localName, String namespaceURI)
			throws XMLStreamException {
		indent();
		super.writeEmptyElement(prefix, localName, namespaceURI);
		unindent();
	}

	@Override
	public void writeStartDocument() throws XMLStreamException {
		super.writeStartDocument();
		super.writeCharacters("\n"); //$NON-NLS-1$
	}

	@Override
	public void writeStartDocument(String version) throws XMLStreamException {
		super.writeStartDocument(version);
		super.writeCharacters("\n"); //$NON-NLS-1$
	}

	@Override
	public void writeStartDocument(String encoding, String version) throws XMLStreamException {
		super.writeStartDocument(encoding, version);
		super.writeCharacters("\n"); //$NON-NLS-1$
	}

	@Override
	public void writeStartElement(String localName) throws XMLStreamException {
		indent();
		super.writeStartElement(localName);
	}

	@Override
	public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
		indent();
		super.writeStartElement(namespaceURI, localName);
	}

	@Override
	public void writeStartElement(String prefix, String localName, String namespaceURI)
			throws XMLStreamException {
		indent();
		super.writeStartElement(prefix, localName, namespaceURI);
	}

	@Override
	public void writeEndDocument() throws XMLStreamException {
		super.writeEndDocument();
		super.writeCharacters("\n"); //$NON-NLS-1$
	}

	@Override
	public void writeEndElement() throws XMLStreamException {
		unindent();
		super.writeEndElement();
	}

	private final void unindent() throws XMLStreamException {
		level -= 1;
		if (!lastWasStart) {
			writeIndent(level);
		}
		if (level == 0) {
			super.writeCharacters("\n"); //$NON-NLS-1$
		}
		lastWasStart = false;
	}

	private final void indent() throws XMLStreamException {
		lastWasStart = true;
		writeIndent(level);
		level += 1;
	}

	private final void writeIndent(int level) throws XMLStreamException {
		if (level > 0) {
			StringBuilder b = new StringBuilder(level + 1);
			b.append('\n');
			for (int i = 0; i < level; i++) {
				b.append(indent);
			}
			super.writeCharacters(b.toString());
		}
	}
}
