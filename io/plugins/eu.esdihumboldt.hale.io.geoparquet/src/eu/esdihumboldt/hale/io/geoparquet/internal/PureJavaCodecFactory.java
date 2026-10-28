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
package eu.esdihumboldt.hale.io.geoparquet.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.GZIPOutputStream;

import org.apache.parquet.bytes.BytesInput;
import org.apache.parquet.compression.CompressionCodecFactory;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

import io.airlift.compress.Compressor;
import io.airlift.compress.snappy.SnappyCompressor;
import io.airlift.compress.zstd.ZstdCompressor;

/**
 * Parquet compression codec factory that only uses pure Java implementations
 * and does not require Hadoop or native libraries. Supports writing only.
 */
public class PureJavaCodecFactory implements CompressionCodecFactory {

	@Override
	public BytesInputCompressor getCompressor(CompressionCodecName codecName) {
		switch (codecName) {
		case UNCOMPRESSED:
			return new Passthrough();
		case ZSTD:
			return new AirCompressor(codecName, new ZstdCompressor());
		case SNAPPY:
			// raw (unframed) Snappy as required by Parquet
			return new AirCompressor(codecName, new SnappyCompressor());
		case GZIP:
			return new Gzip();
		default:
			throw new UnsupportedOperationException(
					"Compression codec not supported: " + codecName);
		}
	}

	@Override
	public BytesInputDecompressor getDecompressor(CompressionCodecName codecName) {
		throw new UnsupportedOperationException("Decompression is not supported");
	}

	@Override
	public void release() {
		// nothing to release
	}

	private static class Passthrough implements BytesInputCompressor {

		@Override
		public BytesInput compress(BytesInput bytes) {
			return bytes;
		}

		@Override
		public CompressionCodecName getCodecName() {
			return CompressionCodecName.UNCOMPRESSED;
		}

		@Override
		public void release() {
			// nothing to release
		}
	}

	private static class AirCompressor implements BytesInputCompressor {

		private final CompressionCodecName name;
		private final Compressor compressor;

		AirCompressor(CompressionCodecName name, Compressor compressor) {
			this.name = name;
			this.compressor = compressor;
		}

		@Override
		public BytesInput compress(BytesInput bytes) {
			byte[] input;
			try {
				input = bytes.toByteArray();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			byte[] output = new byte[compressor.maxCompressedLength(input.length)];
			int length = compressor.compress(input, 0, input.length, output, 0, output.length);
			return BytesInput.from(output, 0, length);
		}

		@Override
		public CompressionCodecName getCodecName() {
			return name;
		}

		@Override
		public void release() {
			// nothing to release
		}
	}

	private static class Gzip implements BytesInputCompressor {

		@Override
		public BytesInput compress(BytesInput bytes) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
				bytes.writeAllTo(gzip);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			return BytesInput.from(out.toByteArray());
		}

		@Override
		public CompressionCodecName getCodecName() {
			return CompressionCodecName.GZIP;
		}

		@Override
		public void release() {
			// nothing to release
		}
	}

}
