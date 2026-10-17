import asyncio
import copy
import unittest
from unittest.mock import patch

from app import ApiError, Settings, create_app, validate_plan_query, validate_plan_result
from test_app import request


def query():
    return {"preferences": "家常菜、少油", "meals": [
        {"id": "breakfast", "target_kcal": 500}, {"id": "lunch", "target_kcal": 700},
        {"id": "dinner", "target_kcal": 600}, {"id": "snack", "target_kcal": 200}]}


def suggestion():
    return {"meals": [{"id": meal["id"], "items": [
        {"name": "测试餐", "ingredients": ["米饭", "鸡蛋"], "grams": 300, "calories_kcal": meal["target_kcal"]}]} for meal in query()["meals"]],
        "notes": ["按熟重估算"]}


class MealPlanTests(unittest.TestCase):
    def test_missing_ingredients_and_explicit_exclusions_are_rejected(self):
        missing = suggestion()
        del missing["meals"][0]["items"][0]["ingredients"]
        with self.assertRaises(ApiError):
            validate_plan_result(missing, validate_plan_query(query()))
        with self.assertRaises(ApiError):
            validate_plan_result(suggestion(), validate_plan_query(dict(query(), excluded_foods="鸡蛋")))

    def test_accepts_omitted_snack(self):
        value = query()
        value["meals"].pop()
        self.assertEqual(len(validate_plan_query(value)["meals"]), 3)

    def test_rejects_bad_or_duplicate_meals(self):
        for meal in ({"id": "unknown", "target_kcal": 200}, {"id": "breakfast", "target_kcal": 200},
                     {"id": [], "target_kcal": 200}, {"id": "snack", "target_kcal": True}):
            value = query()
            value["meals"][-1] = meal
            with self.subTest(meal=meal), self.assertRaises(ApiError):
                validate_plan_query(value)

    def test_rejects_extreme_and_non_integer_targets(self):
        for value in (100, 9000, float("nan"), "1500", 1500.1):
            with self.subTest(value=value), self.assertRaises(ApiError):
                validate_plan_query({"meals": [{"id": "lunch", "target_kcal": value}]})

    def test_preferences_and_body_types_are_checked(self):
        for value in ([], {"meals": []}, dict(query(), preferences="a" * 501), dict(query(), preferences=[])):
            with self.subTest(value=str(value)[:50]), self.assertRaises(ApiError):
                validate_plan_query(value)

    def test_totals_are_recomputed(self):
        value = suggestion()
        value["total_kcal"] = 10
        result = validate_plan_result(value, validate_plan_query(query()))
        self.assertEqual(result["total_kcal"], 2000)
        self.assertEqual(result["meals"][1]["total_kcal"], 700)

    def test_partial_or_duplicate_ai_meals_are_rejected(self):
        values = [suggestion(), suggestion()]
        values[0]["meals"].pop()
        values[1]["meals"][1]["id"] = "breakfast"
        for value in values:
            with self.assertRaises(ApiError):
                validate_plan_result(value, validate_plan_query(query()))

    def test_off_target_suggestion_is_not_saved_as_valid(self):
        value = suggestion()
        value["meals"][0]["items"][0]["calories_kcal"] = 1500
        with self.assertRaises(ApiError) as error:
            validate_plan_result(value, validate_plan_query(query()))
        self.assertEqual(error.exception.status, 502)

    def test_model_values_are_checked(self):
        for key, number in (("grams", 0), ("grams", 0.001), ("grams", float("inf")), ("calories_kcal", -1),
                            ("calories_kcal", float("nan")), ("grams", True)):
            value = suggestion()
            value["meals"][0]["items"][0][key] = number
            with self.subTest(number=number), self.assertRaises(ApiError):
                validate_plan_result(value, validate_plan_query(query()))

    def test_endpoint_uses_existing_auth(self):
        app = create_app(Settings("secret", "x" * 32))
        with patch("app.call_plan_provider") as provider:
            status, _ = asyncio.run(request(app, query(), token="wrong", path="/v1/meal-plan"))
            self.assertEqual(status, 401)
            provider.assert_not_called()

    def test_endpoint_does_not_forward_body_profile_or_arbitrary_keys(self):
        app = create_app(Settings("secret", "x" * 32))
        body = dict(query(), weight=70, password="not-forwarded")
        with patch("app.call_plan_provider", return_value=validate_plan_result(suggestion(), validate_plan_query(query()))) as provider:
            status, response = asyncio.run(request(app, body, path="/v1/meal-plan"))
            self.assertEqual(status, 200)
            self.assertEqual(response["total_kcal"], 2000)
            sent = provider.call_args.args[1]
            self.assertNotIn("weight", sent)
            self.assertNotIn("password", sent)

    def test_validation_happens_before_paid_model_call(self):
        app = create_app(Settings("secret", "x" * 32))
        with patch("app.call_plan_provider") as provider:
            status, _ = asyncio.run(request(app, {"meals": []}, path="/v1/meal-plan"))
            self.assertEqual(status, 400)
            provider.assert_not_called()


if __name__ == "__main__":
    unittest.main()
