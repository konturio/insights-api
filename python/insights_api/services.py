from typing import List, Dict

import aiohttp

from .models import AxisOverridesRequest
from .repositories import AxisRepository, HelperRepository


class AxisService:
    def __init__(self, repo: AxisRepository):
        self.repo = repo

    async def insert_overrides(self, request: AxisOverridesRequest, owner: str):
        await self.repo.insert_overrides(request, owner)


class HelperService:
    def __init__(self, repo: HelperRepository):
        self.repo = repo

    def transform_field_list(self, field_list: List[str], query_map: Dict[str, str]) -> List[str]:
        result = []
        for key, value in query_map.items():
            if key in field_list:
                result.append(value)
            else:
                result.append(f"NULL as {key}")
        return result

    async def transform_geometry_to_wkt(self, geometry: str) -> str:
        return await self.repo.transform_geometry_to_wkt(geometry)


class HttpClient:
    """Example aiohttp-based client for external HTTP calls."""

    def __init__(self):
        self.session = aiohttp.ClientSession()

    async def fetch_json(self, url: str) -> Dict:
        async with self.session.get(url) as resp:
            resp.raise_for_status()
            return await resp.json()

    async def close(self):
        await self.session.close()


class TileService:
    """Service logic for vector tiles."""

    def __init__(self, repo):
        self.repo = repo

    async def get_bivariate_tile_v1(
        self, z: int, x: int, y: int, indicators_class: str, indicators: List[str] | None
    ) -> bytes:
        # Placeholder implementation, real SQL queries will be added in step 4
        return b""

    async def get_bivariate_tile_v2(
        self, z: int, x: int, y: int, indicators: List[str] | None
    ) -> bytes:
        # Placeholder implementation
        return b""


class PopulationService:
    """Business logic for population calculations."""

    def __init__(self, repo):
        self.repo = repo

    async def calculate_population(self, geometry: str):
        # Placeholder return structure
        return {"population": 0, "urban": 0, "gdp": 0}

    async def humanitarian_impact(self, geometry: str):
        return {"impact": []}

    async def calculate_several(self, data: List[dict]):
        return []


class IndicatorService:
    """Upload and query indicator metadata."""

    def __init__(self, repo):
        self.repo = repo

    async def upload(self, metadata, file_data):
        return "todo"

    async def update(self, metadata, file_data):
        return "todo"

    async def upload_status(self, upload_id: str) -> str:
        return "processing"

    async def list_indicators(self) -> List[dict]:
        return []

    async def get_indicator(self, indicator_id: str) -> dict:
        return {"id": indicator_id}
