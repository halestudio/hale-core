import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
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
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKBWriter;

/**
 * Spike PoC: write a GeoParquet-shaped file using a pure-Java parquet-hadoop
 * write path (no Hadoop runtime, no Arrow/GeoArrow), with JTS geometries
 * encoded as WKB, per the GeoParquet 2.0.0-rc.1 spec.
 *
 * As of GeoParquet 2.0, WKB in a plain BINARY column plus file-level "geo"
 * metadata (the 1.x approach, and this PoC's own first version) is NOT
 * sufficient: the spec now requires the geometry column itself to carry
 * Parquet's native GEOMETRY/GEOGRAPHY logical type annotation (added to the
 * Parquet format in 2.11.0 / March 2025, and present in parquet-java's schema
 * API since at least the 1.17.1 release used here). The "geo" file metadata
 * is layered on top of that, not a substitute for it.
 */
public class GeoParquetPoc {

  public static void main(String[] args) throws Exception {
    Path outputPath = Paths.get(args.length > 0 ? args[0] : "geoparquet-poc-output.parquet");
    // In the real hale-io.geoparquet plugin, this EPSG code is what
    // org.geotools.referencing.CRS.lookupEpsgCode(crsDefinition.getCRS(), true)
    // would return for the feature's CRSDefinition - standard, already-proven GeoTools
    // API, not re-demonstrated here. Default 4326; try e.g. 25832 (ETRS89 / UTM 32N,
    // common in German INSPIRE/XPlanGML data) via the second CLI argument.
    int epsgCode = args.length > 1 ? Integer.parseInt(args[1]) : 4326;

    // crs identifier passed to the native logical type annotation: the Parquet spec
    // leaves this string's exact format up to convention (defaults to "OGC:CRS84" if
    // omitted) - GeoParquet's own "geo" file metadata (built below) is what carries the
    // full PROJJSON. Using a plain "EPSG:<code>" identifier here as a reasonable, cheap
    // reading; open question for the write-up whether reference implementations expect
    // something else (e.g. the PROJJSON itself) in this field.
    String crsIdentifier = "EPSG:" + epsgCode;

    MessageType schema = Types.buildMessage()
        .required(PrimitiveTypeName.INT64).named("id")
        .optional(PrimitiveTypeName.BINARY).as(LogicalTypeAnnotation.stringType()).named("name")
        .required(PrimitiveTypeName.BINARY).as(LogicalTypeAnnotation.geometryType(crsIdentifier))
        .named("geometry")
        .named("feature");

    GeometryFactory gf = new GeometryFactory();
    WKBWriter wkbWriter = new WKBWriter();

    Geometry point = gf.createPoint(new Coordinate(7.6261, 51.9607));
    Geometry line = gf.createLineString(new Coordinate[] {
        new Coordinate(7.60, 51.95), new Coordinate(7.65, 51.97)
    });
    Geometry polygon = gf.createPolygon(new Coordinate[] {
        new Coordinate(7.60, 51.95), new Coordinate(7.65, 51.95),
        new Coordinate(7.65, 51.98), new Coordinate(7.60, 51.95)
    });

    String crsJson = CrsUtil.fetchProjJson(epsgCode).trim();

    // GeoParquet 2.0 file-level "geo" metadata, per the published JSON schema at
    // https://geoparquet.org/releases/v2.0.0-rc.1/schema.json - note the schema requires
    // the literal "version" value "2.0.0" (not "2.0.0-rc.1", which is only the docs/URL tag),
    // and "encoding" is a hard const "WKB" (GeoArrow is not a valid encoding in this schema).
    // "crs" is real PROJJSON for the given EPSG code (see fetchProjJson), not the null/default
    // shortcut from the earlier version of this PoC. The bbox covering column is still out of
    // scope for this minimal PoC (tracked as follow-up).
    String geoMetadata = "{"
        + "\"version\":\"2.0.0\","
        + "\"primary_column\":\"geometry\","
        + "\"columns\":{"
        + "\"geometry\":{"
        + "\"encoding\":\"WKB\","
        + "\"geometry_types\":[\"Point\",\"LineString\",\"Polygon\"],"
        + "\"crs\":" + crsJson
        + "}"
        + "}"
        + "}";

    Map<String, String> extraMetadata = new LinkedHashMap<>();
    extraMetadata.put("geo", geoMetadata);

    OutputFile outputFile = new LocalOutputFile(outputPath);

    // withConf(new PlainParquetConfiguration()) is the load-bearing call here:
    // it steers the writer away from the default HadoopParquetConfiguration()
    // fallback, so no org.apache.hadoop.* class is ever touched at runtime.
    try (ParquetWriter<Group> writer = ExampleParquetWriter.builder(outputFile)
        .withType(schema)
        .withConf(new PlainParquetConfiguration())
        .withCompressionCodec(CompressionCodecName.UNCOMPRESSED)
        .withExtraMetaData(extraMetadata)
        .build()) {

      SimpleGroupFactory factory = new SimpleGroupFactory(schema);

      writer.write(factory.newGroup()
          .append("id", 1L)
          .append("name", "Sample point")
          .append("geometry", Binary.fromConstantByteArray(wkbWriter.write(point))));

      writer.write(factory.newGroup()
          .append("id", 2L)
          .append("name", "Sample line")
          .append("geometry", Binary.fromConstantByteArray(wkbWriter.write(line))));

      writer.write(factory.newGroup()
          .append("id", 3L)
          .append("name", "Sample polygon")
          .append("geometry", Binary.fromConstantByteArray(wkbWriter.write(polygon))));
    }

    System.out.println("Wrote " + outputPath.toAbsolutePath());

    // NOTE: tried validating the footer in-process via ParquetFileReader.open(...),
    // but even the "Hadoop-free" ParquetReadOptions.builder(ParquetConfiguration)
    // path still transitively touches org.apache.hadoop.mapreduce.lib.input.FileInputFormat
    // inside parquet-hadoop 1.17.1's ParquetReadOptions.Builder constructor - a real
    // Hadoop coupling on the READ side that write-side code does not have.
    // Since the write path (proven above, on a Hadoop-free classpath) is this spike's
    // priority, output validation is instead done independently via DuckDB (validate.py).
  }
}
