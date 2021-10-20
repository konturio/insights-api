--liquibase formatted sql
--changeset insights-api:calculate_population_and_gdp_for_wkt_performance_fix_v3 splitStatements:false stripComments:false endDelimiter:; runOnChange:true
drop function if exists calculate_population_and_gdp_for_wkt(text);

create or replace function calculate_population_and_gdp_for_wkt(wkt text)
    returns table
            (
                population double precision,
                urban      double precision,
                gdp        double precision,
                type       text
            )
    language sql
    stable
    strict
    parallel safe
    cost 10000
as
$$
with validated_input as (
    select ST_MakeValid(ST_Transform(
            ST_WrapX(ST_WrapX(
                             ST_Union(ST_MakeValid(
                                     d.geom
                                 )),
                             180, -360), -180, 360),
            3857)) "geom"
    from ST_Dump(ST_CollectionExtract(ST_SetSRID(ST_GeomFromText(
                                                         wkt
                                                     ), 4326))) d
),
     subdivided_polygons as (
         select ST_Subdivide(v.geom, 32) geom
         from validated_input v
     ),
     subdivided_boundary as (
         select ST_Subdivide(ST_Boundary(v.geom), 32) "geom"
         from validated_input v
     ),
     all_h3 as (
         select distinct on (sh.h3) sh.h3,
                                    sh.population,
                                    sh.residential,
                                    sh.gdp,
                                    sh.geom
         from stat_h3 sh,
              subdivided_polygons p
         where ST_Intersects(p.geom, sh.geom)
           and sh.zoom = 8
           and sh.population > 0
         order by sh.h3
     ),
     boundary_h3 as (
         select distinct on (sh.h3) sh.*
         from all_h3 sh,
              subdivided_boundary b
         where ST_Intersects(sh.geom, b.geom)
         order by sh.h3
     ),
     distinct_h3 as (
         select ah.*,
                1::double precision "area"
         from all_h3 ah
                  left outer join
              boundary_h3 bh
              on (ah.h3 = bh.h3)
         where bh.h3 is null
         union all
         select bh.*,
                ST_Area(ST_Intersection(bh.geom, ST_ClipByBox2D(v.geom, bh.geom))) /
                ST_Area(bh.geom) "area"
         from boundary_h3 bh,
              validated_input v
     )
select sum(dh.population * dh.area)                  as population,
       sum(dh.population * dh.residential * dh.area) as urban,
       sum(dh.gdp * dh.area)                         as gdp,
       'population'                                  as type
from distinct_h3 dh;
$$;