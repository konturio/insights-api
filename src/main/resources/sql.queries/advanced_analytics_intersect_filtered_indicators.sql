with pairs(nominator, denominator) as (values %s),
     validated_input
         as (select (:polygon)::geometry as geom),
    boxinput as (select ST_Envelope(v.geom) as bbox from validated_input as v),
    subdivision as (select ST_Subdivide(v.geom) geom from validated_input v),
     hexes as materialized (
             select distinct sh.h3
             from boxinput bi
                      cross join subdivision sb
                     join stat_h3_geom sh on (sh.geom && bi.bbox and ST_Intersects(sh.geom, sb.geom) and sh.resolution = 8)),
     ids(internal_id) as materialized (select nominator from pairs union all select denominator from pairs),
     res as (select st.h3, st.indicator_uuid, st.indicator_value
             from stat_h3_transposed st
             join hexes h on (h.h3 = st.h3)
             where indicator_uuid in (select distinct internal_id::uuid from ids)),
     normalized_indicators as (select a.indicator_uuid                        as numerator_uuid,
                                      b.indicator_uuid                        as denominator_uuid,
                                      (a.indicator_value / b.indicator_value) as normalized_value,
                                      a.h3                                    as h3,
                                      h3_get_resolution(a.h3)                 as resolution
                               from res a,
                                    res b,
                                    pairs p
                               where b.indicator_value != 0
                                 and p.nominator::uuid = a.indicator_uuid
                                 and p.denominator::uuid = b.indicator_uuid
                                 and a.h3 = b.h3
                               order by a.h3)
select h.numerator_uuid,
       h.denominator_uuid,
       avg(sum) filter (where resolution = 8)    as sum_value,
       case
           --if value is null, no need to calculate quality
           when avg(sum) filter (where resolution = 8) is null then null
           when (nullif(max(sum), 0) / nullif(min(sum), 0)) > 0
               then log10(nullif(max(sum), 0) / nullif(min(sum), 0))
           else log10((nullif(max(sum), 0) - nullif(min(sum), 0)) /
                      least(abs(nullif(min(sum), 0)), abs(nullif(max(sum), 0))))
           end                                   as sum_quality,
       avg(min) filter (where resolution = 8)    as min_value,
       case
           --if value is null, no need to calculate quality
           when avg(min) filter (where resolution = 8) is null then null
           when (nullif(max(min), 0) / nullif(min(min), 0)) > 0
               then log10(nullif(max(min), 0) / nullif(min(min), 0))
           else log10((nullif(max(min), 0) - nullif(min(min), 0)) /
                      least(abs(nullif(min(min), 0)), abs(nullif(max(min), 0))))
           end
                                                 as min_quality,
       avg(max) filter (where resolution = 8)    as max_value,
       case
           --if value is null, no need to calculate quality
           when avg(max) filter (where resolution = 8) is null then null
           when (nullif(max(max), 0) / nullif(min(max), 0)) > 0
               then log10(nullif(max(max), 0) / nullif(min(max), 0))
           else log10((nullif(max(max), 0) - nullif(min(max), 0)) /
                      least(abs(nullif(min(max), 0)), abs(nullif(max(max), 0))))
           end
                                                 as max_quality,
       avg(mean) filter (where resolution = 8)   as mean_value,
       case
           --if value is null, no need to calculate quality
           when avg(mean) filter (where resolution = 8) is null then null
           when (nullif(max(mean), 0) / nullif(min(mean), 0)) > 0
               then log10(nullif(max(mean), 0) / nullif(min(mean), 0))
           else log10((nullif(max(mean), 0) - nullif(min(mean), 0)) /
                      least(abs(nullif(min(mean), 0)), abs(nullif(max(mean), 0))))
           end
                                                 as mean_quality,
       avg(stddev) filter (where resolution = 8) as stddev_value,
       case
           --if value is null, no need to calculate quality
           when avg(stddev) filter (where resolution = 8) is null then null
           when (nullif(max(stddev), 0) / nullif(min(stddev), 0)) > 0
               then log10(nullif(max(stddev), 0) / nullif(min(stddev), 0))
           else log10((nullif(max(stddev), 0) - nullif(min(stddev), 0)) /
                      least(abs(nullif(min(stddev), 0)), abs(nullif(max(stddev), 0))))
           end
                                                 as stddev_quality,
       avg(median) filter (where resolution = 8) as median_value,
       case
           --if value is null, no need to calculate quality
           when avg(median) filter (where resolution = 8) is null then null
           when (nullif(max(median), 0) / nullif(min(median), 0)) > 0
               then log10(nullif(max(median), 0) / nullif(min(median), 0))
           else log10((nullif(max(median), 0) - nullif(min(median), 0)) /
                      least(abs(nullif(min(median), 0)), abs(nullif(max(median), 0))))
           end
                                                 as median_quality

from (select a.resolution,
             a.numerator_uuid,
             a.denominator_uuid,
             nullif(sum(a.normalized_value), 0)                                         as sum,
             min(a.normalized_value) filter (where a.normalized_value != 0),
             max(a.normalized_value) filter (where a.normalized_value != 0),
             nullif(avg(a.normalized_value), 0)                                         as mean,
             nullif(stddev(a.normalized_value), 0)                                      as stddev,
             nullif(percentile_cont(0.5) within group (order by a.normalized_value), 0) as median
      from normalized_indicators a
      group by a.resolution, a.numerator_uuid, a.denominator_uuid
      order by a.resolution) h
group by h.numerator_uuid, h.denominator_uuid;
