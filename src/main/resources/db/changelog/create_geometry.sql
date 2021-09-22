--liquibase formatted sql
--changeset insights-api:create_geometry splitStatements:false stripComments:false endDelimiter:; runOnChange:true
drop function if exists create_geometry(text, text);

create function create_geometry(geometry text, geometry_type text)
    returns geometry
    returns null on null input
as
'
    declare
        result geometry;
    begin
        select case
                   when geometry_type = ''wkt'' then
                       ST_GeomFromEWKT(geometry)
                   when geometry_type = ''geojson'' then
                       ST_GeomFromGeoJSON(geometry::json)
                   end
        into result;
        return result;
    end;
'
    language plpgsql;