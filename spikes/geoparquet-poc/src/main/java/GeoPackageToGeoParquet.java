import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.parquet.conf.PlainParquetConfiguration;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.io.OutputFile;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Types;
import org.apache.parquet.schema.Types.MessageTypeBuilder;

/**
 * Spike PoC, part 2: same write path as {@link GeoParquetPoc} (parquet-java direct,
 * WKB via JTS, native GEOMETRY logical type, GeoParquet "geo" file metadata), but fed
 * from a real GeoPackage layer instead of synthetic sample geometries - closing the
 * "synthetic sample data" gap from the ticket's Definition of Done.
 *
 * Reading the GeoPackage here uses plain JDBC (org.xerial:sqlite-jdbc) plus a small
 * hand-rolled GeoPackageBinary (GPB) header parser to get at the raw WKB. This
 * dependency is native/JNI-backed and is NOT part of the write-path dependency
 * footprint the ticket is evaluating - it exists only to load this PoC's test input.
 * A real hale plugin would read GeoPackage input the way hale-core already does
 * elsewhere (e.g. via its existing GeoPackage instance reader), not via this shortcut.
 */
public class GeoPackageToGeoParquet {

  public static void main(String[] args) throws Exception {
    Path gpkgPath = Paths.get(args.length > 0 ? args[0] : "data/013_Grindelwald.gpkg");
    String table = args.length > 1 ? args[1] : "Bedrock_PLG";
    Path outputPath = Paths.get(args.length > 2 ? args[2] : "geoparquet-from-gpkg.parquet");
    CompressionCodecName codec = CompressionCodecName
        .valueOf(args.length > 3 ? args[3].toUpperCase(Locale.ROOT) : "UNCOMPRESSED");

    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + gpkgPath.toAbsolutePath())) {

      GeometryColumnInfo geomInfo = readGeometryColumnInfo(conn, table);
      List<AttributeColumn> attributeColumns = readAttributeColumns(conn, table, geomInfo.columnName);

      System.out.println("Table: " + table + ", geometry column: " + geomInfo.columnName
          + " (" + geomInfo.geometryTypeName + "), srs_id: " + geomInfo.srsId
          + ", attribute columns: " + attributeColumns.size());

      String crsIdentifier = "EPSG:" + geomInfo.srsId;
      String crsJson = CrsUtil.fetchProjJson(geomInfo.srsId).trim();

      MessageTypeBuilder schemaBuilder = Types.buildMessage();
      for (AttributeColumn col : attributeColumns) {
        var fieldBuilder = col.required
            ? schemaBuilder.required(col.primitiveType)
            : schemaBuilder.optional(col.primitiveType);
        if (col.primitiveType == PrimitiveTypeName.BINARY) {
          fieldBuilder.as(LogicalTypeAnnotation.stringType());
        }
        fieldBuilder.named(col.name);
      }
      schemaBuilder.required(PrimitiveTypeName.BINARY)
          .as(LogicalTypeAnnotation.geometryType(crsIdentifier)).named(geomInfo.columnName);
      MessageType schema = schemaBuilder.named("feature");

      String geometryTypeGeoParquet = normalizeGeometryTypeName(geomInfo.geometryTypeName);
      String geoMetadata = "{"
          + "\"version\":\"2.0.0\","
          + "\"primary_column\":\"" + geomInfo.columnName + "\","
          + "\"columns\":{"
          + "\"" + geomInfo.columnName + "\":{"
          + "\"encoding\":\"WKB\","
          + "\"geometry_types\":[\"" + geometryTypeGeoParquet + "\"],"
          + "\"crs\":" + crsJson
          + "}"
          + "}"
          + "}";
      Map<String, String> extraMetadata = new LinkedHashMap<>();
      extraMetadata.put("geo", geoMetadata);

      OutputFile outputFile = new LocalOutputFile(outputPath);

      int written = 0;
      try (ParquetWriter<Group> writer = ExampleParquetWriter.builder(outputFile)
          .withType(schema)
          .withConf(new PlainParquetConfiguration())
          // parquet-hadoop's stock CodecFactory constructs a real
          // org.apache.hadoop.conf.Configuration for any non-UNCOMPRESSED codec (see
          // NOTES.md's "compression codec correction"), so compression goes through our
          // own Hadoop-free PureJavaCodecFactory instead (aircompressor + java.util.zip).
          .withCodecFactory(new PureJavaCodecFactory())
          .withCompressionCodec(codec)
          .withExtraMetaData(extraMetadata)
          .build()) {

        SimpleGroupFactory factory = new SimpleGroupFactory(schema);

        String sql = "SELECT * FROM \"" + table + "\"";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
          while (rs.next()) {
            Group group = factory.newGroup();
            for (AttributeColumn col : attributeColumns) {
              Object value = rs.getObject(col.name);
              if (value == null) {
                continue;
              }
              switch (col.primitiveType) {
                case INT64 -> group.append(col.name, ((Number) value).longValue());
                case DOUBLE -> group.append(col.name, ((Number) value).doubleValue());
                default -> group.append(col.name, value.toString());
              }
            }
            byte[] gpb = rs.getBytes(geomInfo.columnName);
            group.append(geomInfo.columnName, Binary.fromConstantByteArray(stripGeoPackageHeader(gpb)));
            writer.write(group);
            written++;
          }
        }
      }

      System.out.println("Wrote " + written + " features to " + outputPath.toAbsolutePath());
    }
  }

  private record GeometryColumnInfo(String columnName, String geometryTypeName, int srsId) {
  }

  private record AttributeColumn(String name, PrimitiveTypeName primitiveType, boolean required) {
  }

  private static GeometryColumnInfo readGeometryColumnInfo(Connection conn, String table)
      throws Exception {
    String sql = "SELECT column_name, geometry_type_name, srs_id FROM gpkg_geometry_columns "
        + "WHERE table_name = '" + table + "'";
    try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
      if (!rs.next()) {
        throw new IllegalArgumentException("No geometry column registered for table " + table);
      }
      return new GeometryColumnInfo(rs.getString("column_name"), rs.getString("geometry_type_name"),
          rs.getInt("srs_id"));
    }
  }

  private static List<AttributeColumn> readAttributeColumns(Connection conn, String table,
      String geometryColumn) throws Exception {
    List<AttributeColumn> columns = new ArrayList<>();
    try (Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("PRAGMA table_info(\"" + table + "\")")) {
      while (rs.next()) {
        String name = rs.getString("name");
        if (name.equals(geometryColumn)) {
          continue;
        }
        String sqliteType = rs.getString("type").toUpperCase(Locale.ROOT);
        boolean notNull = rs.getInt("notnull") != 0 || rs.getInt("pk") != 0;

        PrimitiveTypeName primitiveType;
        if (sqliteType.startsWith("INTEGER")) {
          primitiveType = PrimitiveTypeName.INT64;
        }
        else if (sqliteType.startsWith("REAL") || sqliteType.startsWith("DOUBLE")
            || sqliteType.startsWith("FLOAT")) {
          primitiveType = PrimitiveTypeName.DOUBLE;
        }
        else {
          // TEXT and anything else -> string; keeps the PoC's column mapping simple,
          // real BLOB/other columns are not expected in this sample layer
          primitiveType = PrimitiveTypeName.BINARY;
        }
        columns.add(new AttributeColumn(name, primitiveType, notNull));
      }
    }
    return columns;
  }

  /**
   * GeoParquet's geometry_types values follow WKT-style capitalization (e.g. "Polygon",
   * "LineString"), while GeoPackage's gpkg_geometry_columns.geometry_type_name is all
   * upper case (e.g. "POLYGON"). Normalize between the two.
   */
  private static String normalizeGeometryTypeName(String gpkgTypeName) {
    String lower = gpkgTypeName.toLowerCase(Locale.ROOT);
    return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
  }

  /**
   * Strip a GeoPackageBinary (GPB) header off a geometry blob, per the OGC GeoPackage
   * spec (clause 2.1.3), returning the standard WKB payload underneath - which is all
   * JTS's WKBReader (or, here, passed straight through as the GEOMETRY logical type's
   * raw bytes) understands. Layout: magic "GP" (2 bytes), version (1 byte), flags (1
   * byte: bit 0 = byte order, bits 1-3 = envelope indicator code, bit 4 = empty flag),
   * srs_id (int32), an optional envelope (0/32/48/48/64 bytes depending on the
   * indicator code), then the WKB itself.
   */
  private static byte[] stripGeoPackageHeader(byte[] gpb) {
    if (gpb.length < 8 || gpb[0] != 'G' || gpb[1] != 'P') {
      throw new IllegalArgumentException("Not a GeoPackageBinary geometry blob");
    }
    int flags = gpb[3] & 0xFF;
    ByteOrder order = (flags & 0x01) != 0 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
    int envelopeIndicator = (flags >> 1) & 0x07;
    int envelopeLength = switch (envelopeIndicator) {
      case 0 -> 0;
      case 1 -> 32;
      case 2, 3 -> 48;
      case 4 -> 64;
      default -> throw new IllegalArgumentException(
          "Invalid GeoPackageBinary envelope indicator: " + envelopeIndicator);
    };
    // order only matters for the envelope/srs_id fields we're skipping past here, not
    // for the WKB payload itself (which carries its own byte-order marker) - read
    // anyway to fail fast on a malformed header rather than silently mis-slicing.
    ByteBuffer.wrap(gpb, 4, 4).order(order).getInt();
    int wkbOffset = 8 + envelopeLength;
    return java.util.Arrays.copyOfRange(gpb, wkbOffset, gpb.length);
  }
}
