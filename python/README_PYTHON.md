# Insights API - Python Implementation

This directory contains the Python rewrite of the Insights API.

## Environment Setup

1. Install Python 3.10 or higher.
2. Create a virtual environment:
   ```bash
   python -m venv venv
   source venv/bin/activate
   ```
3. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```
4. Apply database migrations (optional):
   ```bash
   alembic upgrade head
   ```
5. Run the development server:
   ```bash
   python -m insights_api.main
   ```
