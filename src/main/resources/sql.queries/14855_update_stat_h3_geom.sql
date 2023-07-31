INSERT INTO stat_h3_geom (h3, resolution, geom)
SELECT DISTINCT ON (t.h3) t.h3, h3_get_resolution(t.h3), ST_Transform(h3_cell_to_boundary_geometry(t.h3), 3857) as geom
FROM stat_h3_transposed t
         LEFT JOIN stat_h3_geom g ON t.h3 = g.h3
WHERE g.h3 IS NULL