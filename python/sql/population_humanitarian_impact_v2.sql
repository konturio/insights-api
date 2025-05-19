with resolution as (select calculate_area_resolution_v2(ST_SetSRID(:geometry::geometry, 4326)) as resolution),
     validated_input
         as (select (:transformed_geometry)::geometry as geom),
     boxinput as (select st_envelope(v.geom) as bbox from validated_input as v),
     subdivision as (select st_subdivide(v.geom) geom from validated_input v),
     %s,
     stat_in_area as (select s.*, sum(population) over (order by population desc) as sum_pop from indicators_as_columns s),
     total as (select sum(population) as population, round(sum(populated_area_km2)::numeric, 2) as area from stat_in_area)
select sum(s.population)                                as population,
       case
           when sum_pop <= t.population * 0.68 then '0-68'
           else '68-100'
           end                                          as percentage,
       case
           when sum_pop <= t.population * 0.68 then 'Kontur Urban Core'
           else 'Kontur Settled Periphery'
           end                                          as name,
       round(sum(populated_area_km2)::numeric, 2)       as areaKm2,
       ST_AsGeoJSON(ST_Transform(ST_Union(geom), 4326)) as geometry,
       t.population                                     as totalPopulation,
       t.area                                           as totalAreaKm2
from stat_in_area s,
     total t
group by t.population, t.area, 2, 3;
