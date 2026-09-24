import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Real CRS-to-PROJJSON translation for a given EPSG code, backed by spatialreference.org's
 * PROJJSON export (verified to satisfy https://proj.org/schemas/v0.7/projjson.schema.json,
 * the exact schema GeoParquet's "crs" field references). Results are cached to disk so
 * repeated runs/EPSG codes don't re-hit the network.
 *
 * PRODUCTION CAVEATS (tracked as follow-up, not resolved in this PoC):
 * - Runtime dependency on a third-party network service is not acceptable for a shipped
 * plugin; a real implementation should bundle/pre-generate PROJJSON for the CRS's hale
 * actually needs (e.g. common EPSG codes for INSPIRE/XPlanGML data), or vendor PROJ's own
 * EPSG-to-PROJJSON database, updating only if genuinely new CRS are needed.
 * - This only handles CRS with a plain EPSG code. A hale CRSDefinition backed by a custom/
 * WKT-only CRS with no EPSG identifier has no defined path here yet.
 */
public class CrsUtil {

  private CrsUtil() {
  }

  public static String fetchProjJson(int epsgCode) throws Exception {
    Path cacheDir = Paths.get("crs-cache");
    Files.createDirectories(cacheDir);
    Path cacheFile = cacheDir.resolve(epsgCode + ".projjson.json");

    if (Files.exists(cacheFile)) {
      System.out.println("Using cached PROJJSON for EPSG:" + epsgCode);
      return Files.readString(cacheFile, StandardCharsets.UTF_8);
    }

    String url = "https://spatialreference.org/ref/epsg/" + epsgCode + "/projjson.json";
    System.out.println("Fetching PROJJSON for EPSG:" + epsgCode + " from " + url);
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "Failed to fetch PROJJSON for EPSG:" + epsgCode + " - HTTP " + response.statusCode());
    }

    Files.writeString(cacheFile, response.body(), StandardCharsets.UTF_8);
    return response.body();
  }
}
