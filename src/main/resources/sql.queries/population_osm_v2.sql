with validated_input as (
    select (:polygon)::geometry as geom
),
boxinput as (
    select st_envelope(v.geom) as bbox from validated_input v
),
subdivision as (
    select st_subdivide(v.geom) geom from validated_input v
),
res as (
    select st.h3, st.indicator_uuid, st.indicator_value
    from boxinput bi
             cross join subdivision sb
             join stat_h3_geom sh on (sh.geom && bi.bbox and st_intersects(sh.geom, sb.geom))
             join stat_h3_transposed st on (sh.h3 = st.h3)
    where sh.resolution = 8
      and indicator_uuid in (
          select internal_id
          from bivariate_indicators_metadata
          where param_id in ('count', 'building_count', 'highway_length', 'population', 'populated_area_km2', 'area_km2')
      )
),
stat_area as (
    select
        r.h3,
        h3_cell_area(r.h3, 'km^2') as area_km2,
        max(r.indicator_value) filter (where bi.param_id = 'count')             as count,
        max(r.indicator_value) filter (where bi.param_id = 'building_count')     as building_count,
        max(r.indicator_value) filter (where bi.param_id = 'highway_length')     as highway_length,
        max(r.indicator_value) filter (where bi.param_id = 'population')         as population,
        max(r.indicator_value) filter (where bi.param_id = 'populated_area_km2') as populated_area_km2
    from res r
             join bivariate_indicators_metadata bi on r.indicator_uuid = bi.internal_id
    group by r.h3
    having max(r.indicator_value) filter (where bi.param_id = 'population') > 0
)
select %s from stat_area st
