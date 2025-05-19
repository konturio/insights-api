with hexes as (
    select h3
     from h3_polygon_to_cells(
            st_transform(ST_TileEnvelope(:z, :x, :y, margin := 0.08), 4326), :resolution) h3
),
-- todo: currently assume that the most detailed hexes are only on 8 or :resolution res, no intermediate levels
    parents as (
        select distinct h3_cell_to_parent(h3, 8) h3 from hexes
),
    scale_factor(uuid, k) as (
        select
            internal_id,
            case downscale when 'equal' then 1 else pow(7, :resolution-8) end
        from bivariate_indicators_metadata
        where max_res <= 8
),
    sampled_values as (
        select h3_cell_to_children(h3, :resolution) h3, indicator_uuid, indicator_value / k indicator_value
        from stat_h3_transposed s
        join parents p using(h3)
        join scale_factor on (indicator_uuid=uuid)
        where indicator_uuid in (%s)
),
    true_values as (
        select h3, indicator_uuid, indicator_value
        from stat_h3_transposed s
        join hexes using(h3)
        where indicator_uuid in (%s)
),
    res as (
        select * from sampled_values 
        union all
        select * from true_values
)
select ST_AsMVT(q, 'stats', 8192, 'geom', 'h3ind') as tile
from (select
        %s,
        ST_AsMVTGeom(
            st_transform(h3_cell_to_boundary_geometry(h3), 3857),
            ST_TileEnvelope(:z, :x, :y), 8192, 64, true
        ) as geom,
        h3index_to_bigint(h3) as h3ind
      from res
      group by h3) q;
