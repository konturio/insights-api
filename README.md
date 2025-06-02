# Insights API

Insights API is a GraphQL service for calculating correlations between
geospatial indicators. It accepts polygons in GeoJSON format and lists of
numerators and denominators for the X and Y axes.

For more details on available endpoints and database schema see the
[docs](docs/README.md) directory.

## Test UI
A basic GraphiQL interface is available at `http://url/graphiql`.

## Example requests
### Get all correlations
```graphql
{ allStatistic(defaultParam: 1) { fields } }
```

### Calculate correlation for a polygon
```graphql
{ polygonStatistic(polygonStatisticRequest: {
    polygon: "{\"type\":\"Polygon\",\"coordinates\":[[[27.2021484375,54.13347814286039],[26.9989013671875,53.82335438174398],[27.79541015625,53.70321053273598],[27.960205078125,53.90110181472825],[28.004150390625,54.081951104880396],[27.6470947265625,54.21707306629117],[27.2021484375,54.13347814286039]]]}",
    xNumeratorList: ["count", "area_km2"],
    yNumeratorList: ["population", "area_km2"]
  }) { fields } }
```
Fields are described in [docs/stat_json_spec.md](docs/stat_json_spec.md).

## Performance tests
A simple Locust test script is provided under `scripts/test.py`. It uses
several geometry samples from the development environment.

**How to run:**
```
locust -f scripts/test.py
```

## Notes
1. `CorrelationRateResolver` is currently used. In the future `CovarianceRateResolver` should replace it.
2. Two H3 data structures are supported. The `calculations.useStatSeparateTables` flag in `application.yml` toggles between them.
3. Resolver classes correspond to root GraphQL values.
4. Review the "Optimize performance of data uploading in insights-api" story before uploading indicators.
5. Java version: 16.
