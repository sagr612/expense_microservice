from unittest.mock import MagicMock, patch
from app.classifier.gemini_classifier import classify_sms

def create_mock_client(label, confidence, category="OTHERS"):
    mock_client = MagicMock()
    response = MagicMock()
    response.text = f"""
    {{
        "label": "{label}",
        "confidence": {confidence},
        "category": "{category}"
    }}
    """
    mock_client.models.generate_content.return_value = response
    return mock_client


@patch("app.classifier.gemini_classifier.get_client")
def test_classify_expense(mock_get_client):
    mock_get_client.return_value = create_mock_client("expense", 0.98)

    result = classify_sms("Rs.500 debited")

    assert result["label"] == "expense"
    assert result["confidence"] == 0.98


@patch("app.classifier.gemini_classifier.get_client")
def test_classify_income(mock_get_client):
    mock_get_client.return_value = create_mock_client("income", 0.95)

    result = classify_sms("Rs.500 credited")

    assert result["label"] == "income"
    assert result["confidence"] == 0.95


@patch("app.classifier.gemini_classifier.get_client")
def test_classify_irrelevant(mock_get_client):
    mock_get_client.return_value = create_mock_client("irrelevant", 0.99)

    result = classify_sms("Your OTP is 123456")

    assert result["label"] == "irrelevant"
    assert result["confidence"] == 0.99


@patch("app.classifier.gemini_classifier.get_client")
def test_invalid_label_defaults_to_irrelevant(mock_get_client):
    mock_get_client.return_value = create_mock_client("banana", 0.85)
    result = classify_sms("Random message")
    assert result["label"] == "irrelevant"
    assert result["confidence"] == 0.0
    assert "error" in result

@patch("app.classifier.gemini_classifier.get_client")
def test_classify_returns_category(mock_get_client):
    """Valid category from LLM is returned in the result dict."""
    mock_get_client.return_value = create_mock_client("expense", 0.95, "FOOD")
    result = classify_sms("Rs.299 spent at Swiggy")
    assert result["category"] == "FOOD"


@patch("app.classifier.gemini_classifier.get_client")
def test_classify_returns_category_income(mock_get_client):
    """Income categories flow through correctly."""
    mock_get_client.return_value = create_mock_client("income", 0.97, "SALARY")
    result = classify_sms("Rs.50000 salary credited")
    assert result["label"] == "income"
    assert result["category"] == "SALARY"


@patch("app.classifier.gemini_classifier.get_client")
def test_classify_irrelevant_returns_others_category(mock_get_client):
    """When label=irrelevant, category=OTHERS should pass through."""
    mock_get_client.return_value = create_mock_client("irrelevant", 0.99, "OTHERS")
    result = classify_sms("Your OTP is 123456")
    assert result["label"] == "irrelevant"
    assert result["category"] == "OTHERS"


@patch("app.classifier.gemini_classifier.get_client")
def test_classify_missing_category_field_fails_closed(mock_get_client):
    """Response missing 'category' field should fail closed."""
    mock_client = MagicMock()
    response = MagicMock()
    response.text = '{"label": "expense", "confidence": 0.9}'
    mock_client.models.generate_content.return_value = response
    mock_get_client.return_value = mock_client

    result = classify_sms("Rs.500 spent")

    assert result["label"] == "irrelevant"
    assert result["confidence"] == 0.0
    assert result["category"] is None
    assert "error" in result


@patch("app.classifier.gemini_classifier.get_client")
def test_classify_invalid_category_fails_closed(mock_get_client):
    """Category outside the Literal set should be rejected by Pydantic → fail closed."""
    mock_client = MagicMock()
    response = MagicMock()
    response.text = '{"label": "expense", "confidence": 0.9, "category": "GROCERIES"}'
    mock_client.models.generate_content.return_value = response
    mock_get_client.return_value = mock_client

    result = classify_sms("Rs.500 spent at BigBasket")

    # Since GROCERIES isn't in the Literal set, Pydantic rejects → except path
    assert result["label"] == "irrelevant"
    assert result["confidence"] == 0.0
    assert result["category"] is None
    assert "error" in result


@patch("app.classifier.gemini_classifier.get_client")
def test_classify_lowercase_category_uppercased(mock_get_client):
    """LLM returning lowercase category should still work if Literal is case-sensitive.
    
    NOTE: Literal in Pydantic is case-sensitive. Since our Literals are UPPERCASE,
    a lowercase value like 'food' will be rejected → fail closed.
    """
    mock_client = MagicMock()
    response = MagicMock()
    response.text = '{"label": "expense", "confidence": 0.9, "category": "food"}'
    mock_client.models.generate_content.return_value = response
    mock_get_client.return_value = mock_client

    result = classify_sms("Rs.299 spent at Swiggy")

    # "food" (lowercase) not in Literal → parse fails → except path
    assert result["label"] == "irrelevant"
    assert "error" in result


@patch("app.classifier.gemini_classifier.get_client")
def test_classify_exception_returns_none_category(mock_get_client):
    """On exception, category should be None (sentinel for main.py fallback)."""
    mock_client = MagicMock()
    mock_client.models.generate_content.side_effect = Exception("500")
    mock_get_client.return_value = mock_client

    result = classify_sms("Rs.500 spent")

    assert result["category"] is None
    assert "error" in result

@patch("app.classifier.gemini_classifier.get_client")
def test_confidence_above_one(mock_get_client):
    mock_get_client.return_value = create_mock_client("expense", 1.8)
    result = classify_sms("Rs.100 spent")
    assert result["confidence"] == 1.0


@patch("app.classifier.gemini_classifier.get_client")
def test_confidence_below_zero(mock_get_client):
    mock_get_client.return_value = create_mock_client("expense", -5)
    result = classify_sms("Rs.100 spent")
    assert result["confidence"] == 0.0


@patch("app.classifier.gemini_classifier.get_client")
def test_gemini_exception(mock_get_client):
    mock_client = MagicMock()
    mock_client.models.generate_content.side_effect = Exception("429")
    mock_get_client.return_value = mock_client
    result = classify_sms("Rs.500 spent")
    assert result["label"] == "irrelevant"
    assert result["confidence"] == 0.0
    assert "error" in result


@patch("app.classifier.gemini_classifier.get_client")
def test_invalid_json(mock_get_client):
    mock_client = MagicMock()
    response = MagicMock()
    response.text = "not a json"
    mock_client.models.generate_content.return_value = response
    mock_get_client.return_value = mock_client

    result = classify_sms("Rs.500 spent")

    assert result["label"] == "irrelevant"
    assert result["confidence"] == 0.0
    assert "error" in result


@patch("app.classifier.gemini_classifier.get_client")
def test_missing_confidence_field(mock_get_client):
    """Response missing 'confidence' should fail closed."""
    mock_client = MagicMock()
    response = MagicMock()
    response.text = '{"label": "expense"}'

    mock_client.models.generate_content.return_value = response
    mock_get_client.return_value = mock_client

    result = classify_sms("Rs.500 spent")

    assert result["label"] == "irrelevant"
    assert result["confidence"] == 0.0
    assert result["category"] is None
    assert "error" in result


@patch("app.classifier.gemini_classifier.get_client")
def test_missing_label_field(mock_get_client):
    """Response missing 'label' should fail closed."""
    mock_client = MagicMock()
    response = MagicMock()
    response.text = '{"confidence": 0.9}'

    mock_client.models.generate_content.return_value = response
    mock_get_client.return_value = mock_client

    result = classify_sms("Rs.500 spent")

    assert result["label"] == "irrelevant"
    assert result["confidence"] == 0.0
    assert "error" in result


@patch("app.classifier.gemini_classifier.get_client")
def test_empty_response_text(mock_get_client):
    """Empty response text should fail closed."""
    mock_client = MagicMock()
    response = MagicMock()
    response.text = ""

    mock_client.models.generate_content.return_value = response
    mock_get_client.return_value = mock_client

    result = classify_sms("Rs.500 spent")

    assert result["label"] == "irrelevant"
    assert result["confidence"] == 0.0
    assert "error" in result


@patch("app.classifier.gemini_classifier.get_client")
def test_confidence_at_boundary_zero(mock_get_client):
    """Confidence exactly 0.0 is valid and preserved."""
    mock_get_client.return_value = create_mock_client("expense", 0.0)

    result = classify_sms("Rs.100 spent")

    assert result["label"] == "expense"
    assert result["confidence"] == 0.0


@patch("app.classifier.gemini_classifier.get_client")
def test_confidence_at_boundary_one(mock_get_client):
    """Confidence exactly 1.0 is valid and preserved."""
    mock_get_client.return_value = create_mock_client("expense", 1.0)

    result = classify_sms("Rs.100 spent")

    assert result["label"] == "expense"
    assert result["confidence"] == 1.0


@patch("app.classifier.gemini_classifier.get_client")
def test_confidence_nan(mock_get_client):
    """NaN confidence: Pydantic accepts it, and min/max clamping yields 1.0."""
    mock_client = MagicMock()
    response = MagicMock()
    response.text = '{"label": "expense", "confidence": NaN, "category": "OTHERS"}'


    mock_client.models.generate_content.return_value = response
    mock_get_client.return_value = mock_client

    result = classify_sms("Rs.500 spent")

    # Pydantic v2 allows NaN; min(1.0, nan) -> 1.0, max(0.0, 1.0) -> 1.0
    assert result["label"] == "expense"
    assert result["confidence"] == 1.0
    assert result["category"] == "OTHERS"


@patch("app.classifier.gemini_classifier.get_client")
def test_confidence_string_value(mock_get_client):
    """Confidence as a string: Pydantic v2 coerces it to a float."""
    mock_client = MagicMock()
    response = MagicMock()
    response.text = '{"label": "expense", "confidence": "0.95", "category": "OTHERS"}'

    mock_client.models.generate_content.return_value = response
    mock_get_client.return_value = mock_client

    result = classify_sms("Rs.500 spent")

    # Pydantic v2 coerces "0.95" -> 0.95
    assert result["label"] == "expense"
    assert result["confidence"] == 0.95
    assert result["category"] == "OTHERS"
