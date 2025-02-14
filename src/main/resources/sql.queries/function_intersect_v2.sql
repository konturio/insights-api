with validated_input as (
    select (:polygon)::geometry as geom
),
     boxinput as (select st_envelope(v.geom) as bbox from validated_input as v),
     subdivision as (select st_subdivide(v.geom) geom from validated_input v),
     hexes_8 as (select distinct sh.h3
             from boxinput bi
                      cross join subdivision sb
                      join stat_h3_geom sh on (--sh.geom && bi.bbox and
                        st_intersects(sh.geom, sb.geom))
             where sh.resolution = 8),
    hexes(h3) as (select h3_compact_cells(array_agg(h3))
        from hexes_8
        ),
    res as (select %s
            from stat_h3_transposed st
            join hexes sg on (sg.h3 = st.h3)
                )
select %s
from res st;
