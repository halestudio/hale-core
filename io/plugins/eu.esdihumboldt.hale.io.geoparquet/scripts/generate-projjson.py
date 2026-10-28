#!/usr/bin/env python
"""
Generates the bundled PROJJSON resource for all non-deprecated EPSG CRS.

Output (in the given directory):
  epsg-projjson.tsv.gz  lines of "<code>\t<minified PROJJSON>" sorted by numeric code
  epsg-projjson.version PROJ and EPSG database versions used

Invocation (from the bundle directory; use -I and do not run inside a directory with downloaded data):

  mise x uv -- uv run --with pyproj==3.8.0 python -I scripts/generate-projjson.py resources/main/eu/esdihumboldt/hale/io/geoparquet/crs

The gzip output is deterministic (fixed mtime) for identical inputs.
"""
import datetime
import gzip
import json
import os
import sys

import pyproj
from pyproj.database import get_database_metadata, query_crs_info


def main(outdir):
    os.makedirs(outdir, exist_ok=True)
    # query_crs_info may return the same code more than once, keep one entry per code
    by_code = {}
    for i in query_crs_info(auth_name="EPSG"):
        if not i.deprecated:
            by_code.setdefault(int(i.code), i)
    infos = [by_code[c] for c in sorted(by_code)]

    count = 0
    with open(os.path.join(outdir, "epsg-projjson.tsv.gz"), "wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, compresslevel=9, mtime=0) as gz:
            for info in infos:
                crs = pyproj.CRS.from_authority("EPSG", info.code)
                data = json.dumps(json.loads(crs.to_json()), separators=(",", ":"), ensure_ascii=False)
                gz.write(f"{int(info.code)}\t{data}\n".encode("utf-8"))
                count += 1

    with open(os.path.join(outdir, "epsg-projjson.version"), "w", encoding="utf-8") as f:
        f.write(f"proj={pyproj.proj_version_str}\n")
        f.write(f"epsg={get_database_metadata('EPSG.VERSION')}\n")
        f.write(f"pyproj={pyproj.__version__}\n")
        f.write(f"count={count}\n")
        f.write(f"generated={datetime.date.today().isoformat()}\n")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: generate-projjson.py <output directory>")
    main(sys.argv[1])
