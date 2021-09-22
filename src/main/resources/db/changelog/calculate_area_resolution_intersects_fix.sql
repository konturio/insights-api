--liquibase formatted sql
--changeset insights-api:calculate_area_resolution_intersects_fix splitStatements:false stripComments:false endDelimiter:; runOnChange:true
drop function if exists calculate_area_resolution(geometry);

create function calculate_area_resolution(geometry geometry)
    returns int
    returns null on null input
as
'
    declare
        area_limit     bigint := 10000;
        resolution     int    := 8;
        geom_area      bigint := ST_Area(ST_UnaryUnion(ST_MakeValid(geometry))::geography) / 1000000;
        populated_area bigint;
    begin
        select sum(populated_area_km2)
        into populated_area
        from stat_h3 sh
        where zoom = 4
          and population > 0
          and st_intersects(sh.geom, ST_UnaryUnion(ST_MakeValid(st_transform(geometry, 3857))));

        select least(populated_area, geom_area)
        into geom_area;

        while area_limit < geom_area and resolution > 1
            loop
                resolution = resolution - 1;
                area_limit = area_limit * 7;
            end loop;
        return resolution;
    end;
'
    language plpgsql;