with h3_resolutions as (
                        -- list of average hexagon edge lengths at all h3 resolutions (0-15)
                        select i as id, h3_get_hexagon_edge_length_avg(i, 'm') as edge_length
                            from generate_series(:min_h3_resolution, :max_h3_resolution) i ),
res as (select greatest(least((
                        select id
                        from h3_resolutions
                             -- calculate single pixel length at given tile zoom level, multiply it on desired hex edge size in pixels
                             -- and compare with hexagon edge length at each resolution. Select optimal
                        order by abs(40075016.6855785 / (:tile_size * 2 ^ (:z)) * :hex_edge_pixels - edge_length)
                        limit 1),
                     -- force given max_h3_resolution if calculated is greater
                     :max_h3_resolution), :min_h3_resolution) as h3_resolution)
select ST_AsMVT(q, 'stats', 8192, 'geom') as tile
from
    (
        select %s,
            ST_AsMVTGeom(geom, ST_TileEnvelope(res.h3_resolution, :x, :y), 8192, 64, true) as geom
        from stat_h3
            join res on res.h3_resolution = zoom
        where geom && ST_TileEnvelope(res.h3_resolution, :x, :y)
    ) as q;