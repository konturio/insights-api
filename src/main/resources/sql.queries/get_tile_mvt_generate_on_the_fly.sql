with res as (select sg.geom, sg.h3, st.indicator_uuid, st.indicator_value
             from stat_h3_geom sg
                      join stat_h3_transposed st on (sg.h3 = st.h3)
             where sg.geom && ST_TileEnvelope(:z, :x, :y)
               and sg.resolution = :resolution
               and indicator_uuid IN (%s))
select ST_AsMVT(q, 'stats', 8192, 'geom', 'h3ind') as tile
from (select
                  %s,
                  ST_AsMVTGeom(geom, ST_TileEnvelope(:z, :x, :y), 8192, 64, true) as geom,
                  h3index_to_bigint(h3) as h3ind
      from res
      group by geom, h3) q;