from pydantic import BaseModel
from typing import Optional
from datetime import datetime

class TransactionRequest(BaseModel):
    id: str
    accountId: str
    amount: float
    currency: str
    merchant: Optional[str] = None
    transactionType: str
    createdAt: datetime

class ScoreResponse(BaseModel):
    transactionId: str
    anomalyScore: float
    isAnomalous: bool
    threshold: float
    modelVersion: str
