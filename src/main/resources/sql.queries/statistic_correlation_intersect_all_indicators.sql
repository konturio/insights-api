-- with validated_input as (
--     select (:polygon)::geometry as geom
-- ), boxinput as (select ST_Envelope(v.geom) as bbox
--                 from validated_input as v
-- ), subdivision as (
--     select ST_Subdivide(v.geom) geom
--     from validated_input v),
--     res as (select st.h3, indicator_uuid, indicator_value
-- from boxinput bi
--     cross join subdivision sb
--     join stat_h3_geom sh on (sh.geom && bi.bbox and ST_Intersects(sh.geom, sb.geom))
--     join stat_h3_transposed st on (indicator_uuid in (select internal_id from bivariate_indicators_metadata) and sh.h3 = st.h3)
-- order by st.h3, indicator_uuid
-- ),
--     normalized_indicators as (
-- select a.indicator_uuid as numerator_uuid,
--     b.indicator_uuid as denominator_uuid,
--     (a.indicator_value / b.indicator_value) as normalized_value,
--     a.h3
-- from res a, res b, %s as bi_b
-- where b.indicator_value != 0 and bi_b.is_base and bi_b.internal_id = b.indicator_uuid and a.h3 = b.h3
-- order by a.h3
--     )
-- select a.numerator_uuid as xNumUuid,
--        a.denominator_uuid as xDenUuid,
--        b.numerator_uuid as yNumUuid,
--        b.denominator_uuid as yDenUuid,
--        corr(a.normalized_value, b.normalized_value) as metrics
-- from normalized_indicators a,
--      normalized_indicators b
-- where a.h3 = b.h3
-- group by 1, 2, 3, 4;

-- temporary fix; remove after migrating to correlation view
select 
x_numerator_id xnumuuid,
x_denominator_id xdenuuid,
y_numerator_id ynumuuid,
y_denominator_id ydenuuid,
correlation metrics
from bivariate_axis_correlation_v2
where correlation is not null
order by quality desc
limit 500
--currently there's ~71122 records in bivariate_axis_correlation_v2
