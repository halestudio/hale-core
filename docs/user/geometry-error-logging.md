# Identifying invalid geometries during a transformation

When hale exports transformed data (for example to GML, WFS, or other geometry-aware
formats), it may encounter an invalid geometry in the source data. A common example is a
polygon ring that is degenerate (collapsed to too few points) and whose winding order
therefore cannot be determined.

Since these are errors in the *source data*, the transformation stops and the export
fails, exactly as before. What has improved is the information the log/report gives you
to find the problematic element.

## What the error message now contains

Where the information is available, an error message for an invalid geometry includes:

- **Feature type** — the type of the feature being written when the error occurred.
- **Feature gml:id** — the identifier of the feature, if one is set on the source data.
- **Geometry excerpt** — a WKT (Well-Known Text) excerpt of the offending geometry
  (truncated to roughly 200 characters for readability).

Any piece of information that isn't available for a given error is simply left out -
this never causes the export to fail for a different reason.

### Before

```
ERROR e.e.h.c.c.r.i.DefaultReporter(1271) - Ring has fewer than 4 points, so orientation cannot be determined
java.lang.IllegalArgumentException: Ring has fewer than 4 points, so orientation cannot be determined
        at org.locationtech.jts.algorithm.CGAlgorithms.isCCW(CGAlgorithms.java:216)
        ...
```

### After

```
ERROR e.e.h.c.c.r.i.DefaultReporter(1271) - Could not unify winding order of geometry: Ring has fewer than 4 points, so orientation cannot be determined [feature type: MyFeatureType] [feature gml:id: MyFeature.42] [geometry: POLYGON ((0 0, 1 1, 0 0))]
java.lang.IllegalArgumentException: Ring has fewer than 4 points, so orientation cannot be determined
        at org.locationtech.jts.algorithm.CGAlgorithms.isCCW(CGAlgorithms.java:216)
        ...
```

## How to use this to fix your data

1. Note the **feature type** and **feature gml:id** from the error message.
2. Search your source data for that feature (e.g. by its `gml:id`).
3. Use the **geometry excerpt** to identify which specific geometry/ring on that feature
   is invalid — for example, a ring with too few distinct points, as in the example
   above.
4. Correct or remove the invalid geometry in the source data and re-run the
   transformation.

## Scope

This improved logging applies to geometry-related errors encountered while writing
output (for example during winding-order unification, or CRS conversion of a geometry),
not only the ring/winding-order case shown above.

## What did not change

- The transformation still stops on an invalid geometry; it is not skipped
  automatically. Only the diagnostic information around the failure has improved.
