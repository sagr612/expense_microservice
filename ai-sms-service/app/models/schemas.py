# app/models/schemas.py
from pydantic import BaseModel
from typing import Optional

class SmsClassifyRequest(BaseModel):
    smsText: str

class SmsExpenseEvent(BaseModel):
    externalId: str
    userId: str
    amount: float
    currency: str = "INR"
    type: str
    category: str
    title: str
    note: str
    transactionDate: str

class SmsClassifyResponse(BaseModel):
    label: str
    confidence: float
    autoCreated: bool
    event: Optional[SmsExpenseEvent] = None