"""Budget-related Pydantic schemas."""
from pydantic import BaseModel, ConfigDict, Field


class BudgetUpsert(BaseModel):
    monthly_limit: float = Field(gt=0, le=100_000_000)


class BudgetOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    category: str
    monthly_limit: float
    spent: float
    percentage: float
    overrun: bool


class BudgetListResponse(BaseModel):
    items: list[BudgetOut]
    total: int
