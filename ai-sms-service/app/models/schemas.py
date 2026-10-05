from pydantic import BaseModel, Field
from typing import Literal, Optional

class SmsClassifyRequest(BaseModel):
    smsText: str =Field(min_length=1, max_length=2000)

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
    degraded: bool = False