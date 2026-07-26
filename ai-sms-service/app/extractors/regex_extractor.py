# app/extractors/regex_extractor.py
import re

VPA_PATTERN = re.compile(r"VPA\s+([a-zA-Z0-9.\-_]+)@", re.IGNORECASE)
MERCHANT_PATTERN = re.compile(r"\b(?:at|to|from)\s+([A-Z][A-Za-z0-9&\s]{2,30}?)(?:[.,]|\son|\svia|\bRef)", re.IGNORECASE)

AMOUNT_PATTERN = re.compile(r"(?:Rs\.?|INR)\s?([\d,]+\.?\d*)", re.IGNORECASE)

CATEGORY_KEYWORDS = {
    "FOOD": ["swiggy", "zomato", "restaurant"],
    "TRANSPORT": ["uber", "ola", "rapido"],
    "SHOPPING": ["amazon", "flipkart", "myntra"],
    "ENTERTAINMENT": ["netflix", "spotify", "hotstar"],
    "UTILITIES": ["electricity", "recharge", "broadband"],
}

def extract_amount(text: str):
    match = AMOUNT_PATTERN.search(text)
    return float(match.group(1).replace(",", "")) if match else None

def extract_merchant(text: str):
    # Try VPA format first — "VPA swiggy@ybl" → "swiggy"
    vpa_match = VPA_PATTERN.search(text)
    if vpa_match:
        return vpa_match.group(1).strip()

    # Fall back to "at/to/from MERCHANT" format
    match = MERCHANT_PATTERN.search(text)
    return match.group(1).strip() if match else None

def guess_category(text: str):
    lowered = text.lower()
    for category, keywords in CATEGORY_KEYWORDS.items():
        if any(k in lowered for k in keywords):
            return category
    return "OTHER"