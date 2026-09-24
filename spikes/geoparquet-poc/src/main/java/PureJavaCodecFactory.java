import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.parquet.bytes.BytesInput;
import org.apache.parquet.compression.CompressionCodecFactory;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

import io.airlift.compress.Compressor;
import io.airlift.compress.Decompressor;
import io.airlift.compress.lz4.Lz4Compressor;
import io.airlift.compress.lz4.Lz4Decompressor;
import io.airlift.compress.snappy.SnappyCompressor;
import io.airlift.compress.snappy.SnappyDecompressor;
import io.airlift.compress.zstd.ZstdCompressor;
import io.airlift.compress.zstd.ZstdDecompressor;

/**
 * Hadoop-free compression for parquet-java, plugged in via
 * {@code ParquetWriter.Builder.withCodecFactory(...)}.
 *
 * parquet-hadoop's stock CodecFactory resolves every codec except UNCOMPRESSED through
 * Hadoop's codec machinery (it constructs an org.apache.hadoop.conf.Configuration), which
 * is why compression pulled Hadoop back in (see NOTES.md, "Compression codec
 * correction"). This factory bypasses that entirely and calls the compression libraries
 * directly:
 *
 * - ZSTD, SNAPPY, LZ4_RAW: pure-Java implementations from io.airlift:aircompressor, which
 * is already a transitive dependency of parquet-hadoop (and pinned in hale-core)
 * - GZIP: the JDK's own java.util.zip
 *
 * The output is ordinary Parquet page compression (a Zstd frame, a raw Snappy block, a raw
 * LZ4 block, a gzip stream), so any Parquet reader can decompress it.
 */
public class PureJavaCodecFactory implements CompressionCodecFactory {

  @Override
  public BytesInputCompressor getCompressor(CompressionCodecName codecName) {
    return switch (codecName) {
      case UNCOMPRESSED -> new PassThrough(codecName);
      case ZSTD -> new AirliftCompressor(codecName, new ZstdCompressor());
      case SNAPPY -> new AirliftCompressor(codecName, new SnappyCompressor());
      case LZ4_RAW -> new AirliftCompressor(codecName, new Lz4Compressor());
      case GZIP -> new GzipCompressor();
      default -> throw new IllegalArgumentException("Codec not supported without Hadoop: " + codecName);
    };
  }

  @Override
  public BytesInputDecompressor getDecompressor(CompressionCodecName codecName) {
    return switch (codecName) {
      case UNCOMPRESSED -> new PassThrough(codecName);
      case ZSTD -> new AirliftDecompressor(new ZstdDecompressor());
      case SNAPPY -> new AirliftDecompressor(new SnappyDecompressor());
      case LZ4_RAW -> new AirliftDecompressor(new Lz4Decompressor());
      case GZIP -> new GzipDecompressor();
      default -> throw new IllegalArgumentException("Codec not supported without Hadoop: " + codecName);
    };
  }

  @Override
  public void release() {
    // nothing pooled
  }

  private static final class AirliftCompressor implements BytesInputCompressor {

    private final CompressionCodecName codecName;
    private final Compressor compressor;

    AirliftCompressor(CompressionCodecName codecName, Compressor compressor) {
      this.codecName = codecName;
      this.compressor = compressor;
    }

    @Override
    public BytesInput compress(BytesInput bytes) throws IOException {
      byte[] input = bytes.toByteArray();
      byte[] output = new byte[compressor.maxCompressedLength(input.length)];
      int length = compressor.compress(input, 0, input.length, output, 0, output.length);
      return BytesInput.from(output, 0, length);
    }

    @Override
    public CompressionCodecName getCodecName() {
      return codecName;
    }

    @Override
    public void release() {
      // stateless
    }
  }

  private static final class AirliftDecompressor implements BytesInputDecompressor {

    private final Decompressor decompressor;

    AirliftDecompressor(Decompressor decompressor) {
      this.decompressor = decompressor;
    }

    @Override
    public BytesInput decompress(BytesInput bytes, int uncompressedSize) throws IOException {
      byte[] input = bytes.toByteArray();
      byte[] output = new byte[uncompressedSize];
      decompressor.decompress(input, 0, input.length, output, 0, uncompressedSize);
      return BytesInput.from(output);
    }

    @Override
    public void decompress(ByteBuffer input, int compressedSize, ByteBuffer output, int uncompressedSize)
        throws IOException {
      ByteBuffer in = input.slice();
      in.limit(compressedSize);
      ByteBuffer out = output.slice();
      out.limit(uncompressedSize);
      decompressor.decompress(in, out);
      output.position(output.position() + uncompressedSize);
    }

    @Override
    public void release() {
      // stateless
    }
  }

  private static final class GzipCompressor implements BytesInputCompressor {

    @Override
    public BytesInput compress(BytesInput bytes) throws IOException {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
        bytes.writeAllTo(gzip);
      }
      return BytesInput.from(buffer);
    }

    @Override
    public CompressionCodecName getCodecName() {
      return CompressionCodecName.GZIP;
    }

    @Override
    public void release() {
      // stateless
    }
  }

  private static final class GzipDecompressor implements BytesInputDecompressor {

    @Override
    public BytesInput decompress(BytesInput bytes, int uncompressedSize) throws IOException {
      try (InputStream gzip = new GZIPInputStream(bytes.toInputStream())) {
        return BytesInput.from(gzip.readNBytes(uncompressedSize));
      }
    }

    @Override
    public void decompress(ByteBuffer input, int compressedSize, ByteBuffer output, int uncompressedSize)
        throws IOException {
      byte[] compressed = new byte[compressedSize];
      input.duplicate().get(compressed);
      output.put(decompress(BytesInput.from(compressed), uncompressedSize).toByteArray());
    }

    @Override
    public void release() {
      // stateless
    }
  }

  private static final class PassThrough implements BytesInputCompressor, BytesInputDecompressor {

    private final CompressionCodecName codecName;

    PassThrough(CompressionCodecName codecName) {
      this.codecName = codecName;
    }

    @Override
    public BytesInput compress(BytesInput bytes) {
      return bytes;
    }

    @Override
    public BytesInput decompress(BytesInput bytes, int uncompressedSize) {
      return bytes;
    }

    @Override
    public void decompress(ByteBuffer input, int compressedSize, ByteBuffer output, int uncompressedSize) {
      ByteBuffer in = input.duplicate();
      in.limit(in.position() + compressedSize);
      output.put(in);
    }

    @Override
    public CompressionCodecName getCodecName() {
      return codecName;
    }

    @Override
    public void release() {
      // stateless
    }
  }
}
