"""Recommendation-engine and what-if simulator schemas."""
from datetime import datetime

from pydantic import BaseModel, Field


class AllocationItem(BaseModel):
    asset_class: str  # "equity" | "debt" | "gold"
    label: str
    percentage: int
    rationale: str


class MarketQuoteSchema(BaseModel):
    symbol: str
    name: str
    price: float
    change_pct: float | None
    as_of: datetime
    source: str
    status: str  # "live" | "cached" | "unavailable"


class RecommendationContext(BaseModel):
    months_used: int
    monthly_income: float
    monthly_expenses: float
    savings_rate: float | None
    investable_monthly: float


class RecommendationNote(BaseModel):
    kind: str  # "caution" | "info"
    text: str


class RecommendationResponse(BaseModel):
    status: str  # "ok" | "insufficient"
    risk_score: int
    risk_band: str
    tolerance_score: int | None
    behavior_score: int
    insufficient_reason: str | None
    context: RecommendationContext | None
    allocation: list[AllocationItem]
    allocation_summary: str | None
    notes: list[RecommendationNote]
    market: list[MarketQuoteSchema]
    educational_disclaimer: str


class WhatIfRequest(BaseModel):
    category: str = Field(min_length=1, max_length=64)
    monthly_reduction: float = Field(gt=0, description="Monthly reduction in rupees")


class WhatIfBlock(BaseModel):
    monthly_expenses: float
    monthly_savings: float
    savings_rate: float | None


class WhatIfImpact(BaseModel):
    monthly_expense_reduction: float
    additional_monthly_investable: float
    annualised_extra_investable: float


class WhatIfResponse(BaseModel):
    category: str
    monthly_reduction: float
    months_used: int
    current_monthly_spend: float
    monthly_income: float
    original: WhatIfBlock
    simulated: WhatIfBlock
    impact: WhatIfImpact
    narrative: str
    is_simulation: bool
    educational_disclaimer: str