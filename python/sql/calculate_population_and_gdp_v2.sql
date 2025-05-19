WITH validated_input AS (
    -- Input geometry can be very complex in real scenarios, up to thousands of points.
    -- This is coming from user and we have no real control over it.
    select :geometry::geometry as geom
),
boxinput AS (
    -- For fast prefiltering using GIST index, generate a bbox for the input geometry
    SELECT ST_Envelope(geom) AS bbox
    FROM validated_input
),
subdivision AS (
    -- For fast filtering after the bbox check, subdivide the geometry.
    SELECT ST_Subdivide(geom, 64) AS geom
    FROM validated_input
),
%s
SELECT
    SUM(dh.population) AS population,
    SUM(dh.population * dh.residential) AS urban,
    SUM(dh.gdp) AS gdp,
    'population' AS type
FROM indicators_as_columns dh;
