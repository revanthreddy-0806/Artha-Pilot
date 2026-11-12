"""Transaction endpoints."""
from datetime import date
from typing import Annotated

from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.db import get_db
from app.models.user import User
from app.schemas.transaction import TransactionCreate, TransactionOut, TransactionPage, TransactionUpdate
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
    q: str | None = Query(None),
    min_amount: float | None = Query(None, ge=0),
    max_amount: float | None = Query(None, ge=0),
    statement_id: int | None = Query(None, ge=1),
    sort_by: str = Query("date"),
    sort_dir: str = Query("desc"),
) -> TransactionPage:
    items, total = TransactionService(db).list_transactions(
        current_user.id,
        page=page,
        page_size=page_size,
        category=category,
        transaction_type=transaction_type,
        start_date=start_date,
        end_date=end_date,
        search=q,
        min_amount=min_amount,
        max_amount=max_amount,
        statement_id=statement_id,
        sort_by=sort_by,
        sort_dir=sort_dir,
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


@router.post("", response_model=TransactionOut, status_code=status.HTTP_201_CREATED)
def create_transaction(
    payload: TransactionCreate,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> TransactionOut:
    created = TransactionService(db).create_transaction(current_user.id, payload)
    return TransactionOut.model_validate(created)


@router.patch("/{transaction_id}", response_model=TransactionOut)
def update_transaction(
    transaction_id: int,
    payload: TransactionUpdate,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> TransactionOut:
    updated = TransactionService(db).update_transaction(current_user.id, transaction_id, payload)
    return TransactionOut.model_validate(updated)


@router.delete("/{transaction_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_transaction(
    transaction_id: int,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> None:
    TransactionService(db).delete_transaction(current_user.id, transaction_id)
