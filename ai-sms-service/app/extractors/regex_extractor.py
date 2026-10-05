# app/extractors/regex_extractor.py
import re

VPA_PATTERN = re.compile(r"VPA\s+([a-zA-Z0-9.\-_]+)@", re.IGNORECASE)

MERCHANT_AT_TO_PATTERN = re.compile(
    r"(?i:\b(?:at|to)\s+)([A-Z][A-Za-z0-9&\s]{1,30}?)(?=[.,\n]|\son\b|\svia\b|\bfor\b|\bRef\b|$)"
)
MERCHANT_FROM_PATTERN = re.compile(
    r"(?i:\b(?:from)\s+)([A-Z][A-Za-z0-9&\s]{1,30}?)(?=[.,\n]|\son\b|\svia\b|\bfor\b|\bRef\b|$)"
)

# MERCHANT_PATTERN = re.compile(r"\b(?:at|to|from)\s+([A-Z][A-Za-z0-9&\s]{2,30}?)(?:[.,]|\son|\svia|\bRef)", re.IGNORECASE)

# AMOUNT_PATTERN = re.compile(
#     r"(?:debited|credited|spent|paid|received|transferred)?\s*(?:by|of)?\s*"
#     r"(?:Rs\.?|INR|₹)\s*(\d{1,3}(?:,\d{2,3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)(?!\d)",
#     re.IGNORECASE,
# )

AMOUNT_PATTERN = re.compile(
    r"(?:Rs\.?|INR|₹)\s*(\d{1,3}(?:,\d{2,3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)(?!\d)",
    re.IGNORECASE,
)
TRANSACTION_VERB = re.compile(
    r"\b(?:debited|credited|spent|paid|received|transferred|withdrawn|purchased)\b",
    re.IGNORECASE,
)
BALANCE_CONTEXT = re.compile(r"\b(?:balance|bal|avl|available)\b", re.IGNORECASE)

CATEGORY_KEYWORDS = {
    # ---------- EXPENSE CATEGORIES ----------
    "FOOD": [
        "swiggy", "zomato", "restaurant", "cafe", "coffee",
        "starbucks", "mcdonald", "kfc", "dominos", "pizza",
        "burger", "biryani", "eatery", "dhaba", "food",
        "dineout", "eatsure", "faasos", "box8", "behrouz",
    ],
    "TRANSPORT": [
        "uber", "ola", "rapido", "namma yatri", "meru",
        "petrol", "diesel", "fuel", "hp petroleum", "iocl",
        "bpcl", "shell", "reliance petrol",
        "metro", "irctc", "railway", "toll", "fastag",
        "parking", "cab", "taxi", "auto fare",
    ],
    "SHOPPING": [
        "amazon", "flipkart", "myntra", "ajio", "meesho",
        "nykaa", "tatacliq", "snapdeal", "shoppers stop",
        "lifestyle", "westside", "reliance retail", "croma",
        "dmart", "big bazaar", "decathlon", "ikea",
        "mall", "store", "purchase", "order",
    ],
    "BILLS": [
        "electricity", "recharge", "broadband", "wifi",
        "postpaid", "prepaid", "airtel", "jio", "vi ",
        "vodafone", "bsnl", "mtnl", "tata power",
        "adani electricity", "torrent power", "bescom",
        "mseb", "kseb", "pspcl", "gas bill", "water bill",
        "dth", "tata sky", "d2h", "sun direct",
        "rent", "maintenance", "society",
    ],
    "ENTERTAINMENT": [
        "netflix", "spotify", "hotstar", "prime video",
        "amazon prime", "sony liv", "zee5", "voot",
        "bookmyshow", "pvr", "inox", "cinepolis",
        "youtube premium", "apple music", "gaana", "wynk",
        "playstation", "xbox", "steam", "epic games",
    ],
    "HEALTH": [
        "apollo", "medplus", "1mg", "pharmeasy", "netmeds",
        "practo", "pharmacy", "hospital", "clinic", "chemist",
        "doctor", "medicine", "diagnostic", "lab test",
        "cult.fit", "cultfit", "gym", "fitness",
        "healthkart", "tata 1mg", "wellness",
    ],

    # ---------- INCOME CATEGORIES ----------
    "SALARY": [
        "salary", "sal credit", "monthly salary", "payroll",
        "wages", "stipend", "sal cr",
    ],
    "FREELANCE": [
        "freelance", "upwork", "fiverr", "toptal",
        "consulting", "contract payment", "invoice paid",
        "gig payment", "project payment",
    ],
    "INVESTMENT": [
        "dividend", "interest credited", "mutual fund",
        "sip", "zerodha", "groww", "upstox", "kite",
        "coin", "stock", "equity", "bonds", "fd matured",
        "fixed deposit", "ppf", "nsc",
        "capital gain", "redemption",
    ],
    "GIFT": [
        "gift", "cashback", "reward", "bonus received",
        "referral bonus", "prize", "lottery",
    ],
}


def extract_amount(text: str):
    matches = list(AMOUNT_PATTERN.finditer(text))
    valid_amounts = []
    for match in matches:
        preceding = re.split(r"[.,\n;]", text[:match.start()])[-1]
        following = re.split(r"[.,\n;]", text[:match.end()])[0]
        has_verb = bool(TRANSACTION_VERB.search(preceding) or TRANSACTION_VERB.search(following))
        is_balance = bool(BALANCE_CONTEXT.search(preceding) and not has_verb)

        if is_balance:
            continue
        amount = float(match.group(1).replace(",", ""))
        if has_verb:
            return amount
        valid_amounts.append(amount)
    return valid_amounts[0] if valid_amounts else None


def extract_merchant(text: str):
    vpa_match = VPA_PATTERN.search(text)
    if vpa_match:
        return vpa_match.group(1).strip()
    at_to_match = MERCHANT_AT_TO_PATTERN.search(text)
    if at_to_match:
        return at_to_match.group(1).strip()

    from_match = MERCHANT_FROM_PATTERN.search(text)
    if from_match:
        return from_match.group(1).strip()
    return None


def guess_category(text: str):
    lowered = text.lower()
    for category, keywords in CATEGORY_KEYWORDS.items():
        pattern = r"\b(?:" + "|".join(re.escape(k) for k in keywords) + r")\b"
        if re.search(pattern, lowered):
            return category
    return "OTHERS"
