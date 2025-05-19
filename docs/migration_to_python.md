# Migration Plan: Java to Python

This document outlines a phased approach for rewriting the Insights API from Java to Python using Starlette for the web framework, `asyncpg` for database access, and `aiohttp` for outgoing HTTP requests.

## Step-by-step Plan

1. **Generate project skeleton** ✅
   - Create a new Python package in a `python/` directory with a minimal Starlette application.
   - Provide a `README_PYTHON.md` describing environment setup and how to run the server.

2. **Recreate REST endpoints** ✅
   - Implement routes with Starlette to mirror existing Java endpoints.
   - Use asynchronous request handlers.

3. **Port business logic** ✅
   - Translate service classes and utilities to Python modules.
   - Use Pydantic models for request and response validation.
   - Utilize `aiohttp` for any external HTTP calls.
   - Evaluate Python GraphQL libraries (e.g. Strawberry, Ariadne) for replacing the current Java GraphQL layer.
   - Port tile, indicator and population services.
   - Create a minimal GraphQL schema using Strawberry.

4. **Replace data access layer** ✅
   - Implement repository classes using `asyncpg` for asynchronous PostgreSQL queries.
   - Prefer `row_factory=dict` and avoid rewriting Java mappers 1:1.
   - Provide database migrations and port existing SQL scripts using Alembic.
   - Create a connection pool in `insights_api.db` and share it across services.

5. **Introduce testing framework**
   - Add `pytest` and `pytest-asyncio` to dependencies.
   - Configure a basic `pytest.ini` with async settings.
   - Reimplement existing Java tests in Python modules.
   - Add integration tests with a temporary PostgreSQL instance.
   - Integrate coverage reporting and run tests in CI.

6. **Incremental migration**
   - Gradually replace Java components with Python modules while keeping both stacks operational.
   - Maintain interoperability via HTTP APIs during the transition.
   - Provide fallbacks to Java endpoints when functionality is missing in Python.
   - Continuously verify parity between Java and Python implementations.
   - Schedule deprecation of Java modules once parity is confirmed.

7. **Update CI/CD pipeline**
   - Configure Python tooling (formatters and linters).
   - Build and publish a Docker image for the Python service.
   - Ensure new code passes linting and tests in CI.
   - Deploy both Java and Python services via the existing pipeline until migration is complete.
   - Remove Java build steps once the migration finishes.


## Status

The Python package now calls repository methods directly from the route
handlers. Database access uses `asyncpg` with a shared connection pool and
initial schema migrations are managed via Alembic under `python/alembic`.

## Next Steps

- ☐ Finalize GraphQL schema and resolvers using Strawberry
- ☐ Implement CSV upload pipeline for indicators
- ☐ Port OSM quality and urban core calculations
- ☐ Introduce `pytest` with async support and write integration tests
- ☐ Configure CI to build Docker images and run tests
