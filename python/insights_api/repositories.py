from pathlib import Path
from typing import List

from .models import AxisOverridesRequest

SQL_DIR = Path(__file__).resolve().parents[1] / "sql"


def _load_sql(filename: str) -> str:
    return (SQL_DIR / filename).read_text()


SQL_TILE_HIGH_RES = _load_sql("get_tile_mvt_generate_high_res.sql")
SQL_TILE_ON_THE_FLY = _load_sql("get_tile_mvt_generate_on_the_fly.sql")
SQL_TILE_LIST_V2 = _load_sql("get_tile_mvt_indicators_list_v2.sql")

SQL_POP_GDP = _load_sql("calculate_population_and_gdp_v2.sql")
SQL_HUM_IMPACT = _load_sql("population_humanitarian_impact_v2.sql")
SQL_OSM = _load_sql("population_osm_v2.sql")
SQL_URBAN_CORE = _load_sql("population_urbancore_v2.sql")


async def _fetch_indicator_rows(pool, names: List[str]) -> List[dict]:
    sql = """
        select param_id as id, internal_id
        from bivariate_indicators_metadata
        where param_id = any($1::text[])
    """
    rows = await pool.fetch(sql, names)
    return [dict(r) for r in rows]


def _build_columns(indicators: List[dict]) -> tuple[list[str], list[str]]:
    columns = []
    uuids = []
    for row in indicators:
        uid = row["internal_id"]
        key = row["id"]
        if key == "one":
            columns.append("1.0::float as \"one\"")
        else:
            columns.append(
                f"coalesce(avg(indicator_value) filter (where indicator_uuid = '{uid}'), 0) as \"{key}\""
            )
        uuids.append(uid)
    return columns, uuids


def _build_cte(
    resolution: str, indicators: List[dict], additional: str, is_tile: bool
) -> str:
    columns, uuids = _build_columns(indicators)
    if is_tile:
        hexes = f"""
hexes as (
    select h3
    from h3_polygon_to_cells(
            st_transform(ST_TileEnvelope(:z, :x, :y, margin := 0.08), 4326), {resolution}) h3
),
"""
    else:
        hexes = f"""
hexes as materialized (
    select distinct sh.h3
    from stat_h3_geom sh, subdivision sb
    where sh.resolution = {resolution}
      and sh.geom && (select bbox from boxinput)
      and ST_Intersects(sh.geom, sb.geom)
),
"""

    cte = (
        hexes
        + f"""
h3_list(arr) as (
    select array_agg(h3 order by h3) from hexes
),
res as (
    select h3, indicator_uuid, indicator_value
    from stat_h3_transposed
    where h3 = any((select arr from h3_list limit 1)::h3index[])
      and indicator_uuid in ({', '.join("'" + u + "'" for u in uuids)})
),
indicators_as_columns as (
    select
        h3,
        {', '.join(columns)}
        {additional}
    from res
    group by h3)
"""
    )
    return cte


class AxisRepository:
    def __init__(self, pool):
        self.pool = pool

    async def insert_overrides(self, request: AxisOverridesRequest, owner: str):
        sql = """
            insert into bivariate_axis_overrides
                (numerator_id, denominator_id, label, min, max,
                 p25, p75, min_label, p25_label, p75_label, max_label, owner)
            values
                ($1::uuid, $2::uuid, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
            on conflict (numerator_id, denominator_id) do update
            set label = excluded.label,
                min = excluded.min,
                max = excluded.max,
                p25 = excluded.p25,
                p75 = excluded.p75,
                min_label = excluded.min_label,
                max_label = excluded.max_label,
                p25_label = excluded.p25_label,
                p75_label = excluded.p75_label
        """
        await self.pool.execute(
            sql,
            request.numerator_id,
            request.denominator_id,
            request.label,
            request.min,
            request.max,
            request.p25,
            request.p75,
            request.min_label,
            request.p25_label,
            request.p75_label,
            request.max_label,
            owner,
        )


class HelperRepository:
    def __init__(self, pool):
        self.pool = pool

    async def transform_geometry_to_wkt(self, geometry: str) -> str:
        query = "select ST_AsText(map_to_geometry_obj($1))"
        result = await self.pool.fetchval(query, geometry)
        return f"SRID=3857;{result}"


class TileRepository:
    """Data access for vector tiles."""

    def __init__(self, pool):
        self.pool = pool


    async def fetch_tile_v1(
        self, h3res: int, z: int, x: int, y: int, indicators: List[str]
    ) -> bytes:
        rows = await _fetch_indicator_rows(self.pool, indicators)
        if not rows:
            return b""
        columns, uuids = _build_columns(rows)
        query = SQL_TILE_HIGH_RES % (", ".join(f"'{u}'" for u in uuids), ", ".join(f"'{u}'" for u in uuids), ", ".join(columns))
        row = await self.pool.fetchrow(query, z=z, x=x, y=y, resolution=h3res)
        return row["tile"] if row else b""

    async def fetch_tile_v2(
        self, h3res: int, z: int, x: int, y: int, indicators: List[str]
    ) -> bytes:
        if len(indicators) != 4:
            return b""
        row = await self.pool.fetchrow(
            SQL_TILE_LIST_V2,
            z=z,
            x=x,
            y=y,
            resolution=h3res,
            ind0=indicators[0],
            ind1=indicators[1],
            ind2=indicators[2],
            ind3=indicators[3],
        )
        return row["tile"] if row else b""


class PopulationRepository:
    def __init__(self, pool):
        self.pool = pool

    async def calculate_population(self, geometry: str):
        indicators = await _fetch_indicator_rows(self.pool, ["population", "gdp", "residential"])
        cte = _build_cte("8", indicators, "", False)
        query = SQL_POP_GDP % cte
        row = await self.pool.fetchrow(query, geometry=geometry)
        if not row:
            return {}
        return {
            "population": row["population"],
            "urban": row["urban"],
            "gdp": row["gdp"],
        }

    async def humanitarian_impact(self, geometry: str):
        indicators = await _fetch_indicator_rows(self.pool, ["population", "populated_area_km2"])
        cte = _build_cte("(select resolution from resolution)", indicators, ", h3_cell_to_boundary_geometry(h3) as geom", False)
        query = SQL_HUM_IMPACT % cte
        rows = await self.pool.fetch(query, geometry=geometry, transformed_geometry=await HelperRepository(self.pool).transform_geometry_to_wkt(geometry))
        return [dict(r) for r in rows]


class IndicatorRepository:
    def __init__(self, pool):
        self.pool = pool

    async def upload_csv(self, metadata: dict, file_path: str) -> str:
        query = """
            insert into bivariate_indicators_metadata (param_label, direction, external_id)
            values ($1, $2::jsonb, gen_random_uuid())
            returning internal_id
        """
        return await self.pool.fetchval(
            query,
            metadata.get("label"),
            metadata.get("direction"),
        )

    async def update_csv(self, indicator_id: str, metadata: dict) -> str:
        query = """
            update bivariate_indicators_metadata
            set param_label = $2,
                direction = $3::jsonb
            where internal_id = $1::uuid
            returning internal_id
        """
        return await self.pool.fetchval(
            query,
            indicator_id,
            metadata.get("label"),
            metadata.get("direction"),
        )

    async def list_indicators(self) -> list[dict]:
        query = """
            select internal_id as id, param_label as label
            from bivariate_indicators_metadata
            order by param_label
        """
        rows = await self.pool.fetch(query)
        return [dict(row) for row in rows]

    async def get_indicator(self, indicator_id: str) -> dict:
        query = """
            select internal_id as id, param_label as label, direction
            from bivariate_indicators_metadata
            where internal_id = $1::uuid
        """
        row = await self.pool.fetchrow(query, indicator_id)
        return dict(row) if row else {}

    async def upload_status(self, upload_id: str) -> str:
        row = await self.pool.fetchrow(
            "select state from bivariate_indicators_metadata where internal_id = $1::uuid",
            upload_id,
        )
        return row["state"] if row else "unknown"
