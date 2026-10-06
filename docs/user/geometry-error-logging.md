# Identifying invalid geometries during a transformation

When hale exports transformed data (for example to GML, WFS, or other geometry-aware
formats), it may encounter an invalid geometry in the source data. A common example is a
polygon ring that is degenerate (collapsed to too few points) and whose winding order
therefore cannot be determined.

Since these are errors in the _source data_, the transformation stops and the export
fails, exactly as before. What has improved is the information the log/report gives you
to find the problematic element.

## What the error message now contains

Where the information is available, an error message for an invalid geometry includes:

- **Invalid ring** — for winding order errors, the specific ring that could not be
  oriented, as WKT (truncated to roughly 200 characters). This pinpoints the problem even
  inside a large geometry, such as a MultiPolygon with many rings. For large rings (more
  than 200 points), a summary is shown instead, in the same format as for the geometry
  excerpt: geometry type, number of points, bounding box and first coordinate.
- **Feature type** — the type of the feature being written when the error occurred.
- **Feature gml:id** — the identifier of the feature being written (the transformed
  feature, not the source feature). It only matches the `gml:id` in your source data if
  the mapping carries the source identifier over unchanged. If the target feature has no
  identifier set by the mapping, this part is omitted.
- **Geometry excerpt** — a WKT (Well-Known Text) excerpt of the offending geometry
  (truncated to roughly 200 characters for readability). For large geometries (more than
  200 points), a summary is shown instead: geometry type, number of points, bounding box
  and first coordinate.

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
ERROR e.e.h.c.c.r.i.DefaultReporter(1279) - Could not unify winding order of geometry: Ring has fewer than 4 points, so orientation cannot be determined [ring: LINEARRING (0 0, 1 1, 0 0)] [feature type: MyFeatureType] [feature gml:id: MyFeature.42] [geometry: POLYGON ((0 0, 1 1, 0 0))]
eu.esdihumboldt.hale.common.instance.io.impl.GeometryProcessingException: Could not unify winding order of geometry: ...
        ...
Caused by: java.lang.IllegalArgumentException: Ring has fewer than 4 points, so orientation cannot be determined [ring: LINEARRING (0 0, 1 1, 0 0)]
        ...
Caused by: java.lang.IllegalArgumentException: Ring has fewer than 4 points, so orientation cannot be determined
        at org.locationtech.jts.algorithm.CGAlgorithms.isCCW(CGAlgorithms.java:216)
        ...
```

For a large geometry, the geometry part is a summary instead of WKT, e.g.:

```
... [ring: LINEARRING (506000 5506000, 506010 5506010, 506000 5506000)] [feature type: MyFeatureType] [feature gml:id: MyFeature.43] [geometry: MultiPolygon with 254 points, bbox: [504600.0, 5504600.0, 506010.0, 5506010.0], first coordinate: (505400.0, 5505000.0)]
```

## How to use this to fix your data

1. Note the **feature type** and **feature gml:id** from the error message.
2. Search your source data for that feature. Searching by `gml:id` only works if your
   mapping copies the source identifier to the target feature; otherwise search by the
   coordinates of the **invalid ring** or the **geometry excerpt**. Where these are shown
   as a summary (large rings or geometries), use the first coordinate and the bounding box.
3. Use the **invalid ring** (and the **geometry excerpt**) to identify which specific
   geometry/ring on that feature is invalid — for example, a ring with too few distinct
   points, as in the example above.
4. Correct or remove the invalid geometry in the source data and re-run the
   transformation.

## Scope

This improved logging applies to geometry-related errors encountered while writing
output (for example during winding-order unification, or CRS conversion of a geometry),
not only the ring/winding-order case shown above.

## What did not change

- The transformation still stops on an invalid geometry; it is not skipped
  automatically. Only the diagnostic information around the failure has improved.
