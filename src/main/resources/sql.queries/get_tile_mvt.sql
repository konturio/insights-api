select ST_AsMVT(q, 'stats', 8192, 'geom') as tile
from
    (
        select %s,
            ST_AsMVTGeom(geom, ST_TileEnvelope(:z, :x, :y), 8192, 64, true) as geom
        from stat_h3
        where zoom = :z
          and geom && ST_TileEnvelope(:z, :x, :y)
    ) as q;