# Migration Plan: Java to Python

This document outlines a phased approach for rewriting the Insights API from Java to Python using Starlette for the web framework, `asyncpg` for database access, and `aiohttp` for outgoing HTTP requests.

## Step-by-step Plan

1. **Generate project skeleton**
   - Create a new Python package in a `python/` directory with a minimal Starlette application.
   - Provide a `README_PYTHON.md` describing environment setup and how to run the server.

2. **Recreate REST endpoints**
   - Implement routes with Starlette to mirror existing Java endpoints.
   - Use asynchronous request handlers.

3. **Port business logic**
   - Translate service classes and utilities to Python modules.
   - Use `aiohttp` for any external HTTP calls.

4. **Replace data access layer**
   - Replace Java persistence logic with `asyncpg` for asynchronous PostgreSQL queries.
   - Migrate repository logic and SQL scripts.

5. **Introduce testing framework**
   - Set up pytest and `pytest-asyncio` for unit and integration tests.
   - Reimplement existing Java tests in Python.

6. **Incremental migration**
   - Gradually replace Java components with Python modules while keeping both stacks operational.
   - Maintain interoperability via HTTP APIs during the transition.

7. **Update CI/CD pipeline**
   - Configure Python tooling and dependency management.
   - Ensure new code passes linting and tests in CI.

## Status

A Python skeleton exists under `python/` with a basic Starlette application.
