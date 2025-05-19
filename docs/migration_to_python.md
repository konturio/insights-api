# Migration Plan: Java to Python

This document outlines a phased approach for rewriting the Insights API from Java to Python using Starlette for the web framework, `asyncpg` for database access, and `aiohttp` for outgoing HTTP requests.

## Step-by-step Plan

1. **Generate project skeleton** ✅
   - Create a new Python package in a `python/` directory with a minimal Starlette application.
   - Provide a `README_PYTHON.md` describing environment setup and how to run the server.

2. **Recreate REST endpoints** ✅
   - Implement routes with Starlette to mirror existing Java endpoints.
   - Use asynchronous request handlers.

3. **Port business logic**
   - Translate service classes and utilities to Python modules.
   - Use Pydantic models for request and response validation.
   - Utilize `aiohttp` for any external HTTP calls.

4. **Replace data access layer**
   - Implement repository classes using `asyncpg` for asynchronous PostgreSQL queries.
   - Provide database migrations and port existing SQL scripts.

5. **Introduce testing framework**
   - Set up pytest and `pytest-asyncio` for unit and integration tests.
   - Reimplement existing Java tests in Python.
   - Integrate coverage reporting and run tests in CI.

6. **Incremental migration**
   - Gradually replace Java components with Python modules while keeping both stacks operational.
   - Maintain interoperability via HTTP APIs during the transition.
   - Provide fallbacks to Java endpoints when functionality is missing in Python.

7. **Update CI/CD pipeline**
   - Configure Python tooling and dependency management.
   - Build and publish a Docker image for the Python service.
   - Ensure new code passes linting and tests in CI.

## Status

A Python skeleton exists under `python/` with a basic Starlette application.
REST and GraphQL endpoint stubs are now available in `insights_api.__init__`.
