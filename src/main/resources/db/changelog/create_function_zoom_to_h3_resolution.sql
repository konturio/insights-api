--liquibase formatted sql
--changeset insights-api:create_function_zoom_to_h3_resolution splitStatements:false stripComments:false endDelimiter:; runOnChange:true
drop function if exists tile_zoom_level_to_h3_resolution(numeric, integer, integer, numeric, integer);

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
