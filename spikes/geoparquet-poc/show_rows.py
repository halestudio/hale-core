"""Show (or export) the rows of a GeoParquet file.

Usage:
  python show_rows.py FILE.parquet            -> print all rows
  python show_rows.py FILE.parquet 50         -> print the first 50 rows
  python show_rows.py FILE.parquet csv        -> write all rows to FILE.csv (open it in Excel)
"""
import json
import sys

import duckdb

path = sys.argv[1]
arg = sys.argv[2] if len(sys.argv) > 2 else None

con = duckdb.connect()
con.sql("INSTALL spatial")
con.sql("LOAD spatial")

# geometry column name, as declared in the file's "geo" metadata
raw_geo = con.execute(
    "SELECT value FROM parquet_kv_metadata(?) WHERE key = 'geo'", [path]
).fetchone()[0]
geom = json.loads(bytes(raw_geo).decode("utf-8"))["primary_column"]

query = (f'SELECT * EXCLUDE ("{geom}"), ST_AsText("{geom}") AS geom_wkt '
         f"FROM read_parquet('{path}')")

if arg == "csv":
    out = path.rsplit(".", 1)[0] + ".csv"
    con.sql(f"COPY ({query}) TO '{out}' (HEADER)")
    print(f"wrote {out}")
else:
    if arg:
        query += f" LIMIT {int(arg)}"
    con.sql(query).show(max_rows=100000, max_width=100000)
