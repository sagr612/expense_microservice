import pytest

from app.extractors.regex_extractor import guess_category

class TestGuessCategory:
    @pytest.mark.parametrize("text, expected", [
        ("Payment to Swiggy", "FOOD"),
        ("Uber ride completed", "TRANSPORT"),
        ("Random SWIGGY", "FOOD"),
        ("Paid at Starbucks via UPI", "OTHER"),
        ("Train ticket booked", "OTHER"),
        ("Grocery shopping at Walmart", "OTHER"),
    ])
    def test_guess_category(self, text, expected):
        assert guess_category(text) == expected
    