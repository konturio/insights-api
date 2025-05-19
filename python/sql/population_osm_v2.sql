with validated_input
         as (select (:polygon)::geometry as geom),
     boxinput as (select st_envelope(v.geom) as bbox from validated_input as v),
     subdivision as (select st_subdivide(v.geom) geom from validated_input v),
     res as (select st.h3, indicator_uuid, indicator_value
             from boxinput bi
                      cross join subdivision sb
                      join stat_h3_geom sh on (sh.geom && bi.bbox and st_intersects(sh.geom, sb.geom))
                      join stat_h3_transposed st on (sh.h3 = st.h3)
             where sh.resolution = 8
               and indicator_uuid IN (select internal_id
                                      from bivariate_indicators_metadata
                                      where param_id IN ('count', 'building_count', 'highway_length', 'population',
                                                         'populated_area_km2', 'area_km2'))),
     stat_area as (select distinct on (h.h3) h.*
from (select a.h3,
    h3_cell_area(a.h3,'km^2') as area_km2,
    a.indicator_value as count,
    b.indicator_value as building_count,
    c.indicator_value as highway_length,
    d.indicator_value as population,
    e.indicator_value as populated_area_km2
    from res a, res b, res c, res d, res e,
    bivariate_indicators_metadata bi_a,
    bivariate_indicators_metadata bi_b,
    bivariate_indicators_metadata bi_c,
    bivariate_indicators_metadata bi_d,
    bivariate_indicators_metadata bi_e,
    bivariate_indicators_metadata bi_f  -- TODO: not used?
    where
    a.h3 = b.h3 and a.h3= c.h3 and a.h3=d.h3 and a.h3=e.h3 and a.indicator_uuid = bi_a.internal_id
    and b.indicator_uuid = bi_b.internal_id
    and c.indicator_uuid = bi_c.internal_id
    and d.indicator_uuid = bi_d.internal_id
    and e.indicator_uuid = bi_e.internal_id
    and bi_a.param_id = 'count'
    and bi_b.param_id = 'building_count'
    and bi_c.param_id = 'highway_length'
    and bi_d.param_id = 'population'
    and bi_e.param_id = 'populated_area_km2'
    and d.indicator_value > 0
    order by h3) h)
select %s from stat_area st
