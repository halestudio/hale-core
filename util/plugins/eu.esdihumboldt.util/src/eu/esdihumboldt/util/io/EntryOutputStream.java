
/*
 * Copyright (c) 2024 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.util.io;

import java.io.IOException;
import java.util.zip.ZipOutputStream;

/**
 * Output stream for a ZIP entry.
 */
public class EntryOutputStream extends OutputStreamDecorator {

	private final ZipOutputStream zip;

	/**
	 * Create an output stream for a ZIP entry
	 *
	 * @param zip the ZIP output stream
	 */
	public EntryOutputStream(ZipOutputStream zip) {
		super(zip);

		this.zip = zip;
	}

	/**
	 * @see OutputStreamDecorator#close()
	 */
	@Override
	public void close() throws IOException {
		// instead of closing the stream close the entry
		zip.closeEntry();
	}

}
