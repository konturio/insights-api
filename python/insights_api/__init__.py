from starlette.applications import Starlette
from starlette.responses import JSONResponse, Response
from starlette.routing import Route


async def homepage(request):
    return JSONResponse({"message": "Hello from Python API"})


async def clean_caches(request):
    """Placeholder implementation of cache eviction endpoint."""
    return JSONResponse({"status": "caches evicted"})


async def get_bivariate_tile_v1(request):
    """Return empty vector tile placeholder."""
    return Response(b"", media_type="application/vnd.mapbox-vector-tile")


async def calculate_population(request):
    """Calculate population statistic (stub)."""
    await request.body()
    return JSONResponse({"statistic": "todo"})


async def focus_humanitarian_impact(request):
    """Return humanitarian impact result (stub)."""
    await request.body()
    return JSONResponse({"impact": "todo"})


async def calculate_population_several(request):
    """Calculate population for several polygons (stub)."""
    await request.body()
    return JSONResponse([])


async def get_bivariate_tile_v2(request):
    """Return empty vector tile placeholder for API v2."""
    return Response(b"", media_type="application/vnd.mapbox-vector-tile")


async def graphql_endpoint(request):
    """Placeholder for GraphQL queries."""
    await request.body()
    return JSONResponse({"data": None})


async def upload_indicator(request):
    """Stub for indicator upload."""
    await request.body()
    return JSONResponse({"uploadId": "todo"})


async def update_indicator(request):
    """Stub for indicator update."""
    await request.body()
    return JSONResponse({"uploadId": "todo"})


async def upload_status(request):
    """Return upload status (stub)."""
    upload_id = request.path_params["upload_id"]
    return JSONResponse({"uploadId": upload_id, "status": "processing"})


async def list_indicators(request):
    """Return list of indicators (stub)."""
    return JSONResponse([])


async def get_indicator(request):
    """Return indicator metadata (stub)."""
    indicator_id = request.path_params["id"]
    return JSONResponse({"id": indicator_id})


async def upload_axis_overrides(request):
    """Stub for uploading custom axis labels."""
    await request.body()
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
        Route("/graphql", graphql_endpoint, methods=["POST"]),
    ],
)
