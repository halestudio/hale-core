/*
 * Copyright (c) 2013 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.io.csv.reader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

import au.com.bytecode.opencsv.CSVReader;
import eu.esdihumboldt.hale.common.core.io.Value;

/**
 * Default lookup table reader for csv files
 *
 * @author Patrick Lieb
 */
public class DefaultCSVLookupReader {

	/**
	 * Reads a csv lookup table file. The selected columns specified by parameters
	 * keyColumn and valueColumn are mapped together.
	 *
	 * @param input the inputstream of the csv file
	 * @param charset specific charset of the csv file
	 * @param separator used separator char in csv file
	 * @param quote used quote char in csv file
	 * @param escape used escape char in csv file
	 * @param skipFirst true, if first line should be skipped
	 * @param keyColumn source column of the lookup table
	 * @param valueColumn target column of the lookup table
	 * @return lookup table as map
	 * @throws IOException if inputstream is not readable
	 */
	public Map<Value, Value> read(InputStream input, Charset charset, char separator, char quote,
			char escape, boolean skipFirst, int keyColumn, int valueColumn) throws IOException {
		Reader streamReader = new BufferedReader(new InputStreamReader(input, charset));
		CSVReader reader = new CSVReader(streamReader, separator, quote, escape);
		String[] nextLine;

		Map<Value, Value> values = new LinkedHashMap<Value, Value>();
		if (skipFirst)
			reader.readNext();
		while ((nextLine = reader.readNext()) != null) {
			if (nextLine.length >= 2)
				values.put(Value.of(nextLine[keyColumn]), Value.of(nextLine[valueColumn]));
		}
		reader.close();
		return values;
	}

}
