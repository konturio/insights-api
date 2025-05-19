with res as (select sg.geom, sg.h3, st.indicator_uuid, st.indicator_value
             from stat_h3_geom sg
                      join stat_h3_transposed st on (sg.h3 = st.h3)
             where sg.geom && ST_TileEnvelope(:z, :x, :y)
    and sg.resolution = :resolution
    and indicator_uuid IN (select internal_id
    from bivariate_indicators_metadata
    where param_id IN
    (:ind0, :ind1, :ind2, :ind3)))
select ST_AsMVT(q, 'stats', 8192, 'geom', 'h3ind') as tile
from (
         select coalesce(a.indicator_value, 0)                                                 as var1,
                coalesce(b.indicator_value, 0)                                                 as var2,
                coalesce(c.indicator_value, 0)                                                 as var3,
                coalesce(d.indicator_value, 0)                                                 as var4,
                ST_AsMVTGeom(a.geom, ST_TileEnvelope(:z, :x, :y), 8192, 64, true) as geom,
                h3index_to_bigint(a.h3) as h3ind
         from res a,
              res b,
              res c,
              res d,
              bivariate_indicators_metadata bi_a,
              bivariate_indicators_metadata bi_b,
              bivariate_indicators_metadata bi_c,
              bivariate_indicators_metadata bi_d
         where a.h3 = b.h3
           and a.h3 = c.h3
           and a.h3 = d.h3
           and a.indicator_uuid = bi_a.internal_id
           and b.indicator_uuid = bi_b.internal_id
           and c.indicator_uuid = bi_c.internal_id
           and d.indicator_uuid = bi_d.internal_id
           and bi_a.param_id = :ind0
           and bi_b.param_id = :ind1
           and bi_c.param_id = :ind2
           and bi_d.param_id = :ind3) q;
