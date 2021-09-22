--liquibase formatted sql
--changeset insights-api:calculate_population_and_gdp_all_geometries splitStatements:false stripComments:false endDelimiter:; runOnChange:true
drop function if exists calculate_population_and_gdp_for_wkt(text);

drop function if exists calculate_population_and_gdp_all_geometries(text, text);

create function calculate_population_and_gdp_all_geometries(geometry text, geometry_type text)
    returns table
            (
                population double precision,
                urban      double precision,
                gdp        double precision,
                type       text
            )
    returns null on null input
as
'
    begin
        return query with subdivide_geometry as (
            select ST_Subdivide(ST_CollectionExtract(
                                        ST_MakeValid(
                                                ST_Transform(ST_SetSRID(create_geometry(geometry, geometry_type), 4326),
                                                             3857)),
                                        3), 150) as geom
        ),
                          project_area as materialized (
                              select ((case
                                           when (ST_Intersects(r.geom, ST_Boundary(tr.geom)))
                                               then ST_Area(ST_Intersection(r.geom, tr.geom)) / ST_Area(r.geom)
                                           else 1 end))
                                         as area,
                                     r.population,
                                     r.residential,
                                     r.gdp
                              from stat_h3 r
                                       join subdivide_geometry tr on ST_Intersects(r.geom, tr.geom)
                              where r.zoom = 8
                                and r.population > 0
                          )
                     select sum(a.population * a.area)                 as population,
                            sum(a.population * a.residential * a.area) as urban,
                            sum(a.gdp * a.area)                        as gdp,
                            ''population''                             as type
                     from project_area a;
    end;
'
    language plpgsql