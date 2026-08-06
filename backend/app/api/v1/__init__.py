"""API v1 router aggregation."""
from fastapi import APIRouter

from app.api.v1 import auth, budgets, health, insights, meta, statements, transactions

api_router = APIRouter(prefix="/api/v1")
api_router.include_router(auth.router)
api_router.include_router(statements.router)
api_router.include_router(transactions.router)
api_router.include_router(budgets.router)
api_router.include_router(insights.router)
api_router.include_router(meta.router)
api_router.include_router(health.router)
