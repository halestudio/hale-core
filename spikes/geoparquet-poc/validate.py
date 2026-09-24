import duckdb
import json
import sys
import urllib.request
import jsonschema

path = sys.argv[1] if len(sys.argv) > 1 else "geoparquet-poc-output.parquet"
con = duckdb.connect()

print("=== strict JSON Schema validation against the real published GeoParquet 2.0.0-rc.1 schema ===")
schema_url = "https://geoparquet.org/releases/v2.0.0-rc.1/schema.json"
schema = json.loads(urllib.request.urlopen(schema_url).read())
raw_geo = con.execute(
    f"SELECT value FROM parquet_kv_metadata('{path}') WHERE key = 'geo'"
).fetchone()[0]
geo_doc = json.loads(bytes(raw_geo).decode("utf-8"))
print("geo metadata as written:", json.dumps(geo_doc, indent=2))
try:
    jsonschema.validate(instance=geo_doc, schema=schema)
    print("=> VALID against the official GeoParquet 2.0.0-rc.1 schema")
except jsonschema.ValidationError as e:
    print(f"=> INVALID: {e.message}")

print("\n=== parquet_kv_metadata ===")
con.sql(f"SELECT * FROM parquet_kv_metadata('{path}')").show()

print("\n=== parquet_schema ===")
con.sql(f"SELECT name, type FROM parquet_schema('{path}')").show()

print("\n=== rows, geometry auto-decoded as WKT (DuckDB recognized the file as GeoParquet) ===")
geom_col = geo_doc["primary_column"]
con.sql(
    f'SELECT * EXCLUDE ("{geom_col}"), ST_AsText("{geom_col}") AS geom_wkt '
    f"FROM '{path}' LIMIT 5"
).show()
row_count = con.sql("SELECT count(*) FROM '" + path + "'").fetchone()[0]
print(f"(total rows: {row_count})")

print("\n=== st_read (native GeoParquet recognition, if supported by this duckdb build) ===")
try:
    con.sql("INSTALL spatial")
    con.sql("LOAD spatial")
except Exception as e:
    print(f"could not install/load the spatial extension, skipping st_read: {e}")
else:
    try:
        con.sql(f"SELECT * FROM st_read('{path}')").show()
    except Exception as e:
        print(f"st_read failed: {e}")
        parquet_drivers = con.sql(
            "SELECT count(*) FROM st_drivers() WHERE short_name ILIKE '%parquet%' "
            "OR long_name ILIKE '%parquet%'"
        ).fetchone()[0]
        total_drivers = con.sql("SELECT count(*) FROM st_drivers()").fetchone()[0]
        print(
            f"(this duckdb build's bundled GDAL has {total_drivers} drivers, "
            f"{parquet_drivers} of them Parquet-capable - if 0, that's an environment/"
            "build gap, not a finding about the file; see NOTES.md)"
        )
