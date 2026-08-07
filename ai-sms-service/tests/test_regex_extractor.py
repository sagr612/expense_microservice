"""
Tests for app.extractors.regex_extractor.

Pure functions, no mocking needed — these directly exercise the regex
patterns against representative SMS text.
"""
import pytest

from app.extractors.regex_extractor import extract_amount, extract_merchant, guess_category


class TestExtractAmount:
    @pytest.mark.parametrize(
        "text,expected",
        [
            ("Rs.500 debited from your account", 500.0),
            ("Rs 500 debited", 500.0),
            ("INR 1,234.50 credited", 1234.50),
            ("rs.99.99 spent at store", 99.99),
            ("INR2500 debited", 2500.0),
        ],
    )
    def test_extracts_amount_across_formats(self, text, expected):
        assert extract_amount(text) == expected

    def test_returns_none_when_no_amount_present(self):
        assert extract_amount("Your OTP is 482913") is None

    def test_returns_none_on_empty_string(self):
        assert extract_amount("") is None


class TestExtractMerchant:
    def test_extracts_merchant_from_vpa_format(self):
        assert extract_merchant("Paid via VPA swiggy@ybl successfully") == "swiggy"

    def test_vpa_pattern_takes_priority_over_at_to_from_pattern(self):
        # Both patterns could match here; VPA should win since it's checked first.
        text = "Paid at VPA amazonpay@icici for order"
        assert extract_merchant(text) == "amazonpay"

    def test_extracts_merchant_after_at_keyword(self):
        assert extract_merchant("Rs.500 debited at Starbucks.") == "Starbucks"

    def test_extracts_merchant_after_to_keyword_with_via_terminator(self):
        assert extract_merchant("Money sent to PayZone via UPI") == "PayZone"

    def test_extracts_merchant_after_from_keyword(self):
        assert extract_merchant("Rs.200 received from Rahul,") == "Rahul"

    def test_returns_none_when_no_merchant_pattern_matches(self):
        assert extract_merchant("Your account balance is low") is None

    def test_lowercase_merchant_name_does_not_match_at_to_from_pattern(self):
        # MERCHANT_PATTERN requires the merchant to start with an uppercase letter.
        assert extract_merchant("debited at starbucks today") is None


class TestGuessCategory:
    @pytest.mark.parametrize(
        "text,expected_category",
        [
            ("Order placed on Swiggy for dinner", "FOOD"),
            ("Your Zomato order is confirmed", "FOOD"),
            ("Uber trip completed, Rs.150 charged", "TRANSPORT"),
            ("Ola cab booking confirmed", "TRANSPORT"),
            ("Amazon order shipped", "SHOPPING"),
            ("Flipkart Big Billion Day sale", "SHOPPING"),
            ("Your Netflix subscription renewed", "ENTERTAINMENT"),
            ("Electricity bill payment received", "BILLS"),      # ← was UTILITIES
            ("Mobile recharge successful", "BILLS"),             # ← was UTILITIES
        ],
    )
    def test_matches_known_keywords_case_insensitively(self, text, expected_category):
        assert guess_category(text) == expected_category

    def test_defaults_to_other_when_no_keyword_matches(self):
        assert guess_category("Rs.500 debited at some random shop") == "OTHERS"

    def test_is_case_insensitive(self):
        assert guess_category("SWIGGY ORDER PLACED") == "FOOD"

class TestGuessCategoryIncomeCategories:
    def test_salary_keyword(self):
        assert guess_category("Salary credited to your account") == "SALARY"

    def test_freelance_keyword(self):
        assert guess_category("Upwork payment received") == "FREELANCE"

    def test_investment_keyword(self):
        assert guess_category("Dividend credited from mutual fund") == "INVESTMENT"

    def test_gift_keyword(self):
        assert guess_category("Rs.50 cashback received") == "GIFT"

    def test_health_keyword(self):
        assert guess_category("Payment to Apollo Pharmacy") == "HEALTH"


class TestGuessCategoryWordBoundary:
    """The new implementation uses \\b word boundaries — verify it prevents
    false positives from substring matches."""

    def test_ola_does_not_match_chocolate(self):
        assert guess_category("Bought chocolate at store") != "TRANSPORT"

    def test_ola_matches_standalone_word(self):
        assert guess_category("Ola ride completed") == "TRANSPORT"

    def test_gift_does_not_match_gifted(self):
        # "gift" is a keyword; word boundary should prevent match on "gifted"
        assert guess_category("Amazon gifted you a coupon") != "GIFT"
