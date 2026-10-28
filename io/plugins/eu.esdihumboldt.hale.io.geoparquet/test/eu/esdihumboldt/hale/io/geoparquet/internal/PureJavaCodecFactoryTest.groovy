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
package eu.esdihumboldt.hale.io.geoparquet.internal

import java.util.zip.GZIPInputStream

import org.apache.parquet.bytes.BytesInput
import org.apache.parquet.hadoop.metadata.CompressionCodecName
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

import io.airlift.compress.snappy.SnappyDecompressor
import io.airlift.compress.zstd.ZstdDecompressor

/**
 * Tests for {@link PureJavaCodecFactory}.
 */
@RunWith(Parameterized)
class PureJavaCodecFactoryTest {

	@Parameters(name = '{0}')
	static Collection<Object[]> codecs() {
		[
			CompressionCodecName.ZSTD,
			CompressionCodecName.SNAPPY,
			CompressionCodecName.GZIP,
			CompressionCodecName.UNCOMPRESSED
		].collect { [it] as Object[] }
	}

	private final CompressionCodecName codec

	PureJavaCodecFactoryTest(CompressionCodecName codec) {
		this.codec = codec
	}

	@Test
	void testRoundTrip() {
		byte[] data = new byte[64 * 1024]
		byte[] pattern = 'GeoParquet row data 0123456789 '.bytes
		for (int i = 0; i < data.length; i++) {
			data[i] = pattern[i % pattern.length]
		}

		def factory = new PureJavaCodecFactory()
		def compressor = factory.getCompressor(codec)
		assert compressor.codecName == codec
		byte[] compressed = compressor.compress(BytesInput.from(data)).toByteArray()
		compressor.release()

		byte[] decompressed
		switch (codec) {
			case CompressionCodecName.ZSTD:
				decompressed = new byte[data.length]
				assert new ZstdDecompressor().decompress(compressed, 0, compressed.length, decompressed, 0, decompressed.length) == data.length
				break
			case CompressionCodecName.SNAPPY:
				decompressed = new byte[data.length]
				assert new SnappyDecompressor().decompress(compressed, 0, compressed.length, decompressed, 0, decompressed.length) == data.length
				break
			case CompressionCodecName.GZIP:
				decompressed = new GZIPInputStream(new ByteArrayInputStream(compressed)).bytes
				break
			default:
				decompressed = compressed
		}

		assert Arrays.equals(decompressed, data)
		if (codec != CompressionCodecName.UNCOMPRESSED) {
			assert compressed.length < data.length
		}
	}

	@Test(expected = UnsupportedOperationException)
	void testUnsupportedCodec() {
		new PureJavaCodecFactory().getCompressor(CompressionCodecName.LZ4_RAW)
	}
}
