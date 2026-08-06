"""Meta endpoints: category taxonomy as single source of truth."""
from typing import Annotated

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.api.deps import get_current_user
from app.ml.categories import CATEGORIES
from app.models.user import User

router = APIRouter(tags=["meta"])


class CategoryListResponse(BaseModel):
    categories: list[str]


@router.get("/categories", response_model=CategoryListResponse)
def list_categories(current_user: Annotated[User, Depends(get_current_user)]) -> CategoryListResponse:
    return CategoryListResponse(categories=CATEGORIES)