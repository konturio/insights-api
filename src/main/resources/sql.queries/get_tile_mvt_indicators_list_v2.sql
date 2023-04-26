with res as (select sg.geom, sg.h3, st.indicator_uuid, st.indicator_value
             from stat_h3_geom sg
                      join stat_h3_transposed st on (sg.h3 = st.h3)
             where sg.geom && ST_TileEnvelope(:z, :x, :y)
    and sg.zoom = :z
    and indicator_uuid IN (select param_uuid
    from %s
    where param_id IN
    (:ind0, :ind1, :ind2, :ind3)))
select ST_AsMVT(q, 'stats', 8192, 'geom') as tile
from (
         select coalesce(a.indicator_value, 0)                                                 as var1,
                coalesce(b.indicator_value, 0)                                                 as var2,
                coalesce(c.indicator_value, 0)                                                 as var3,
                coalesce(d.indicator_value, 0)                                                 as var4,
                ST_AsMVTGeom(a.geom, ST_TileEnvelope(:z, :x, :y), 8192, 64, true) as geom
         from res a,
              res b,
              res c,
              res d,
              %s bi_a,
              %s bi_b,
              %s bi_c,
              %s bi_d
         where a.h3 = b.h3
           and a.h3 = c.h3
           and a.h3 = d.h3
           and a.indicator_uuid = bi_a.param_uuid
           and b.indicator_uuid = bi_b.param_uuid
           and c.indicator_uuid = bi_c.param_uuid
           and d.indicator_uuid = bi_d.param_uuid
           and bi_a.param_id = :ind0
           and bi_b.param_id = :ind1
           and bi_c.param_id = :ind2
           and bi_d.param_id = :ind3) q;