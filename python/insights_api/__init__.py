from starlette.applications import Starlette
from starlette.responses import JSONResponse, Response
from starlette.routing import Route, Mount

import strawberry
from strawberry.asgi import GraphQL

from .db import get_pool
from .models import AxisOverridesRequest
from .repositories import (
    AxisRepository,
    IndicatorRepository,
    TileRepository,
    PopulationRepository,
)


@strawberry.type
class Query:
    @strawberry.field
    def hello(self) -> str:
        return "Hello, GraphQL"


schema = strawberry.Schema(query=Query)
graphql_app = GraphQL(schema)


async def homepage(request):
    return JSONResponse({"message": "Hello from Python API"})


async def clean_caches(request):
    """Placeholder implementation of cache eviction endpoint."""
    return JSONResponse({"status": "caches evicted"})


async def get_bivariate_tile_v1(request):
    """Return bivariate tile."""
    z = int(request.path_params["z"])
    x = int(request.path_params["x"])
    y = int(request.path_params["y"])
    indicators_class = request.query_params.get("indicatorsClass", "all")
    indicators = request.query_params.getlist("indicators") or None

    pool = await get_pool()
    repo = TileRepository(pool)
    tile = await repo.fetch_tile_v1(z, z, x, y, indicators or [])
    return Response(tile, media_type="application/vnd.mapbox-vector-tile")


async def calculate_population(request):
    """Calculate population statistics."""
    body = await request.body()
    pool = await get_pool()
    repo = PopulationRepository(pool)
    result = await repo.calculate_population(body.decode())
    return JSONResponse(result)


async def focus_humanitarian_impact(request):
    """Return humanitarian impact result."""
    body = await request.body()
    pool = await get_pool()
    repo = PopulationRepository(pool)
    result = await repo.humanitarian_impact(body.decode())
    return JSONResponse(result)


async def calculate_population_several(request):
    """Calculate population for several polygons."""
    data = await request.json()
    pool = await get_pool()
    repo = PopulationRepository(pool)
    # Placeholder: real implementation will handle multiple polygons
    result = [await repo.calculate_population(g) for g in data]
    return JSONResponse(result)


async def get_bivariate_tile_v2(request):
    """Return bivariate tile for API v2."""
    z = int(request.path_params["z"])
    x = int(request.path_params["x"])
    y = int(request.path_params["y"])
    indicators = request.query_params.getlist("indicators") or None

    pool = await get_pool()
    repo = TileRepository(pool)
    tile = await repo.fetch_tile_v2(z, z, x, y, indicators or [])
    return Response(tile, media_type="application/vnd.mapbox-vector-tile")




async def upload_indicator(request):
    """Upload indicator data."""
    pool = await get_pool()
    repo = IndicatorRepository(pool)
    data = await request.json()
    upload_id = await repo.upload_csv(data, "")
    return JSONResponse({"uploadId": upload_id})


async def update_indicator(request):
    """Update indicator data."""
    pool = await get_pool()
    repo = IndicatorRepository(pool)
    data = await request.json()
    indicator_id = data.get("id")
    upload_id = await repo.update_csv(indicator_id, data)
    return JSONResponse({"uploadId": upload_id})


async def upload_status(request):
    """Return upload status."""
    upload_id = request.path_params["upload_id"]
    pool = await get_pool()
    repo = IndicatorRepository(pool)
    status = await repo.upload_status(upload_id)
    return JSONResponse({"uploadId": upload_id, "status": status})


async def list_indicators(request):
    """List indicators."""
    pool = await get_pool()
    repo = IndicatorRepository(pool)
    indicators = await repo.list_indicators()
    return JSONResponse(indicators)


async def get_indicator(request):
    """Return indicator metadata."""
    indicator_id = request.path_params["id"]
    pool = await get_pool()
    repo = IndicatorRepository(pool)
    indicator = await repo.get_indicator(indicator_id)
    return JSONResponse(indicator)


async def upload_axis_overrides(request):
    """Upload custom axis labels."""
    payload = await request.json()
    model = AxisOverridesRequest(**payload)
    owner = request.headers.get("X-Username", "anonymous")

    pool = await get_pool()
    repo = AxisRepository(pool)
    await repo.insert_overrides(model, owner)

    return JSONResponse({"status": "received"})


app = Starlette(
    debug=True,
    routes=[
        Route("/", homepage),
        Route("/cache/cleanUp", clean_caches, methods=["GET"]),
        Route(
            "/tiles/bivariate/v1/{z:int}/{x:int}/{y:int}.mvt",
            get_bivariate_tile_v1,
            methods=["GET"],
        ),
        Route(
            "/tiles/bivariate/v2/{z:int}/{x:int}/{y:int}.mvt",
            get_bivariate_tile_v2,
            methods=["GET"],
        ),
        Route("/population", calculate_population, methods=["POST"]),
        Route(
            "/population/humanitarian_impact",
            focus_humanitarian_impact,
            methods=["POST"],
        ),
        Route(
            "/population/several",
            calculate_population_several,
            methods=["POST"],
        ),
        Route("/indicators/upload", upload_indicator, methods=["POST"]),
        Route("/indicators/upload", update_indicator, methods=["PUT"]),
        Route(
            "/indicators/upload/status/{upload_id}",
            upload_status,
            methods=["GET"],
        ),
        Route("/indicators", list_indicators, methods=["GET"]),
        Route("/indicators/{id}", get_indicator, methods=["GET"]),
        Route(
            "/indicators/axis/custom",
            upload_axis_overrides,
            methods=["POST"],
        ),
        Mount("/graphql", graphql_app),
    ],
)


@app.on_event("startup")
async def startup_event():
    """Initialize database connection pool."""
    await get_pool()
