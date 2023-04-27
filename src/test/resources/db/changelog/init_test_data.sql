--liquibase formatted sql
--changeset insights-api:init-test-data splitStatements:false stripComments:false endDelimiter:;
CREATE TABLE IF NOT EXISTS stat_h3
(
    population          FLOAT,
    residential         FLOAT,
    gdp                 FLOAT,
    geom                GEOMETRY,
    zoom                INTEGER
);

DELETE FROM stat_h3;

INSERT INTO stat_h3 (population, residential, gdp, geom, zoom)
VALUES (100.0, 1.0, 200000.0, 'SRID=3857;POLYGON((111319.490793274 111325.142866385,111319.490793274 222684.208505545,222638.981586547 222684.208505545,222638.981586547 111325.142866385,111319.490793274 111325.142866385))', 8);

INSERT INTO stat_h3 (population, residential, gdp, geom, zoom)
VALUES (200.0, 0.5, 300000.0, 'SRID=3857;POLYGON((222638.981586547 222684.208505545,222638.981586547 334111.17140196,333958.472379821 334111.17140196,333958.472379821 222684.208505545,222638.981586547 222684.208505545))', 8);

INSERT INTO stat_h3 (population, residential, gdp, geom, zoom)
VALUES (400.0, 0.5, 400000.0, 'SRID=3857;POLYGON((333958.472379821 334111.17140196,333958.472379821 445640.109656027,445277.963173094 445640.109656027,445277.963173094 334111.17140196,333958.472379821 334111.17140196))', 8);

INSERT INTO stat_h3 (population, residential, gdp, geom, zoom)
VALUES (500.0, 1.0, 500000.0, 'SRID=3857;POLYGON((667916.944759641 669141.057044245,667916.944759641 781182.214188249,779236.435552915 781182.214188249,779236.435552915 669141.057044245,667916.944759641 669141.057044245))', 8);

create or replace function h3_get_hexagon_edge_length_avg
(
    resolution integer,
    unit text default 'km'::text
)
    returns double precision
    returns null on null input
    language plpgsql immutable parallel safe
as
$func$
begin

return (select 461 as edge_length);
end;
$func$;


create or replace function tile_zoom_level_to_h3_resolution
(
    z numeric,                           -- input tile zoom level
    max_h3_resolution integer default 15,-- for cases when there are limits on max h3 resolution allowed
    min_h3_resolution integer default 0,  -- for cases when there are limits on min h3 resolution allowed
    hex_edge_pixels numeric default 44,  -- how many pixels should be presented by the average hex edge
    tile_size integer default 512       -- which tile size in pixels is used
)
    returns integer                      -- output optimal h3 resolution
    returns null on null input
    language plpgsql immutable parallel safe
as
$func$
begin
    -- Negative tile zoom levels are not supported
    if z < 0 then
        raise exception 'Negative tile zoom levels are not supported';
end if;

return (
    with h3_resolutions as (
        -- list of average hexagon edge lengths at all h3 resolutions (0-15)
        select i as id, h3_get_hexagon_edge_length_avg(i, 'm') as edge_length
        from generate_series(min_h3_resolution, max_h3_resolution) i
    )
    select greatest(least((
        select id
        from h3_resolutions
             -- calculate single pixel length at given tile zoom level, multiply it on desired hex edge size in pixels
             -- and compare with hexagon edge length at each resolution. Select optimal
        order by abs(40075016.6855785 / (tile_size * 2 ^ (z)) * hex_edge_pixels - edge_length)
        limit 1),
                        -- force given max_h3_resolution if calculated is greater
                        max_h3_resolution), min_h3_resolution) as h3_resolution);
end;
$func$;
