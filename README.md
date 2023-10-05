# Insights Api
GraphQL API for calculating correlation between parameters in given polygon in geojson format and lists of numerators and denominators for x and y axes

## Test UI
Test UI is available on http://url/graphiql

## Requests
Get all Earth correlations:
```
{allStatistic(defaultParam: 1){fields}}
```
Get correlations in polygon in GeoJSON (single polygon, feature collection or feature) format and lists of numerators for x and y axes:
```
{polygonStatistic(polygonStatisticRequest:{
    polygon: "{\"type\":\"Polygon\",\"coordinates\":[[[27.2021484375,54.13347814286039],[26.9989013671875,53.82335438174398],[27.79541015625,53.70321053273598],[27.960205078125,53.90110181472825],[28.004150390625,54.081951104880396],[27.6470947265625,54.21707306629117],[27.2021484375,54.13347814286039]]]}",
    xNumeratorList: ["count", "area_km2"],
    yNumeratorList: ["population", "area_km2"]
  }){fields}
```
**Fields description** https://gist.github.com/Akiyamka/8ad19a8de3c955ac1f27f67281c12fdf#axis

## Performance tests
Simple performance test for calculating correlation is in scripts package. There are several geometries from dev 
environment and python script with test. More about test framework here http://docs.locust.io/en/stable/

**How to run**: locust -f PATH-TO-PROJECT\insights-api\scripts\test.py

**Notes**: 

1) CorrelationRateResolver is used now, in future CovarianceRateResolver class should replace it for different correlations calculation.
2) Now we have two different structures of H3 data stored in insights database. In application.yml there is a flag *calculations.useStatSeparateTables*. When flag is *false* (default) the old data structure is used: stat_h3 table, bivariate_indicators, bivariate_axis and all sql requests for these tables. When flag is *true* all requests work with another set of tables: stat_h3_transposed, bivariate_indicators_wrk, bivariate_axis_test. After all the system ready to use transposed table all TEST tables have to be replaced by old names and the logic with a flag have to be removed from the project.
3) Resolver package is used for dealing with GraphQL requests. Every resolver stands for one root requested value in GraphQL request
4) For indicators uploading **Optimize performance of data uploading in insigths-api** user story have to be reviewed to understand what is still have to be done
5) Some **TODOs** added among the code
6) Java version is 16 for Insights
7) All method names reflect their behavior