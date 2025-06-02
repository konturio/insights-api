# Application Endpoints

This document summarizes the available HTTP and GraphQL endpoints implemented in the project.

## REST Endpoints

| Method | Path | Description | Source |
|-------|------|-------------|-------|
| GET | `/cache/cleanUp` | Clean all caches | `CacheController` |
| POST | `/indicators/upload` | Create indicator by uploading CSV data and metadata | `IndicatorController` |
| PUT | `/indicators/upload` | Update indicator data | `IndicatorController` |
| GET | `/indicators/upload/status/{uploadId}` | Get indicator upload status | `IndicatorController` |
| GET | `/indicators` | Get indicators metadata for the current user | `IndicatorController` |
| GET | `/indicators/{id}` | Get indicators metadata by ID for the current user | `IndicatorController` |
| POST | `/indicators/axis/custom` | Create or update custom labels and stops for a bivariate axis | `IndicatorController` |
| POST | `/population` | Calculate population statistic inside a polygon (deprecated) | `PopulationController` |
| POST | `/population/humanitarian_impact` | Calculate humanitarian impact for given geometries (deprecated) | `PopulationController` |
| POST | `/population/several` | Calculate statistics for several polygons asynchronously (deprecated) | `PopulationController` |
| GET | `/tiles/bivariate/v1/{z}/{x}/{y}.mvt` | Retrieve bivariate vector tile by tile coordinates and indicator class | `TileController` |
| GET | `/tiles/bivariate/v2/{z}/{x}/{y}.mvt` | Retrieve bivariate vector tile using a list of indicators | `TileController` |

## GraphQL Endpoint

GraphQL queries are served under the standard `/graphql` endpoint. The root `Query` type exposes the following operations:

- `allStatistic(defaultParam: Int): Statistic`
- `polygonStatistic(polygonStatisticRequest: PolygonStatisticRequest): PolygonStatistic`
- `getTransformations(numerator: String, denominator: String): TransformationInfo`
- `getAxes: AxisInfo`

GraphQL schema definitions are located under `src/main/resources/graphql`.

