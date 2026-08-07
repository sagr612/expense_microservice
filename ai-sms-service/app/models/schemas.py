# app/models/schemas.py
from pydantic import BaseModel
from typing import Literal, Optional

class SmsClassifyRequest(BaseModel):
    smsText: str

class SmsExpenseEvent(BaseModel):
    externalId: str
    userId: str
    amount: float
    currency: str = "INR"
    type: str
    category:  Literal[
        "FOOD", "TRANSPORT", "SHOPPING", "BILLS",
        "ENTERTAINMENT", "HEALTH",
        "SALARY", "FREELANCE", "INVESTMENT", "GIFT",
        "OTHERS",
    ]
    title: str
    note: str
    transactionDate: str

class SmsClassifyResponse(BaseModel):
    label: str
    confidence: float
    autoCreated: bool
    event: Optional[SmsExpenseEvent] = None