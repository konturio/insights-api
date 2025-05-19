with resolution as (select calculate_area_resolution_v2(ST_SetSRID(:polygon::geometry, 4326)) as resolution),
     validated_input
         as (select (:transformed_polygon)::geometry as geom),
     boxinput as (select st_envelope(v.geom) as bbox from validated_input as v),
     subdivision as (select st_subdivide(v.geom) geom from validated_input v),
     %s,
     stat_pop as (select s.*, sum(population) over (order by population desc) as sum_pop
                  from indicators_as_columns s),
     total as (select sum(population)                  as population,
                      round(sum(area_km2)::numeric, 2) as area
               from stat_pop)
select sum(s.population)                as urbanCorePopulation,
       round(sum(area_km2)::numeric, 2) as urbanCoreAreaKm2,
       t.area                           as totalPopulatedAreaKm2
from stat_pop s,
     total t
where sum_pop <= t.population * 0.68
group by t.population, t.area;
