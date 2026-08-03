"""Transaction endpoints."""
from datetime import date
from typing import Annotated

from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.db import get_db
from app.models.user import User
from app.schemas.transaction import TransactionOut, TransactionPage, TransactionUpdate
from app.services.transaction_service import TransactionService

router = APIRouter(prefix="/transactions", tags=["transactions"])


@router.get("", response_model=TransactionPage)
def list_transactions(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
    page: int = Query(1, ge=1),
    page_size: int = Query(50, ge=1, le=100),
    category: str | None = Query(None),
    transaction_type: str | None = Query(None),
    start_date: date | None = Query(None),
    end_date: date | None = Query(None),
) -> TransactionPage:
    items, total = TransactionService(db).list_transactions(
        current_user.id,
        page=page,
        page_size=page_size,
        category=category,
        transaction_type=transaction_type,
        start_date=start_date,
        end_date=end_date,
    )
    return TransactionPage(
        items=[TransactionOut.model_validate(t) for t in items],
        total=total,
        page=page,
        page_size=page_size,
    )


@router.patch("/{transaction_id}/category", response_model=TransactionOut)
def update_transaction_category(
    transaction_id: int,
    payload: TransactionUpdate,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> TransactionOut:
    updated = TransactionService(db).update_category(current_user.id, transaction_id, payload.category)
    return TransactionOut.model_validate(updated)
