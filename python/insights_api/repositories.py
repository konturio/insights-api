from .models import AxisOverridesRequest


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

    async def fetch_tile_v1(self, h3res: int, z: int, x: int, y: int, indicators: list[str]):
        return b""

    async def fetch_tile_v2(self, h3res: int, z: int, x: int, y: int, indicators: list[str]):
        return b""


class PopulationRepository:
    def __init__(self, pool):
        self.pool = pool

    async def calculate_population(self, geometry: str):
        return {"population": 0}

    async def humanitarian_impact(self, geometry: str):
        return {"impact": []}


class IndicatorRepository:
    def __init__(self, pool):
        self.pool = pool

    async def upload_csv(self, metadata, file_path: str):
        return "id"

    async def list_indicators(self) -> list[dict]:
        return []

    async def get_indicator(self, indicator_id: str) -> dict:
        return {"id": indicator_id}

    async def upload_status(self, upload_id: str) -> str:
        return "processing"
