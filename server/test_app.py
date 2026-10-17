import asyncio
import base64
import json
import unittest
from unittest.mock import patch

from app import ApiError, Budget, Settings, create_app, validate_query, validate_result


def food():
    return {"food_detected": True, "items": [
        {"name": "米饭", "grams": 200, "calories_kcal": 232,
         "protein_g": 5.2, "carbs_g": 51.8, "fat_g": 0.6}],
        "assumptions": ["按熟米饭200克估算"]}


async def request(app, data, token="x" * 32, content_type="application/json", path="/v1/calories"):
    body = data if isinstance(data, bytes) else json.dumps(data).encode()
    messages = []
    delivered = False

    async def receive():
        nonlocal delivered
        if delivered:
            return {"type": "http.disconnect"}
        delivered = True
        return {"type": "http.request", "body": body, "more_body": False}

    async def send(message):
        messages.append(message)

    await app({"type": "http", "asgi": {"version": "3.0"}, "http_version": "1.1",
               "method": "POST", "scheme": "https", "path": path,
               "raw_path": path.encode(), "query_string": b"", "root_path": "",
               "server": ("test", 443), "client": ("127.0.0.1", 1234),
               "headers": [(b"authorization", ("Bearer " + token).encode()),
                           (b"content-type", content_type.encode())]}, receive, send)
    status = next(m["status"] for m in messages if m["type"] == "http.response.start")
    response = b"".join(m.get("body", b"") for m in messages if m["type"] == "http.response.body")
    return status, json.loads(response)


class CalorieTests(unittest.TestCase):
    def test_requires_food_or_image(self):
        for value in ({}, {"text": " "}, [], {"text": 12}, {"text": "a" * 1001}):
            with self.subTest(value=str(value)[:40]), self.assertRaises(ApiError):
                validate_query(value)

    def test_text_preserves_portion(self):
        self.assertEqual(validate_query({"text": "  米饭200克 "}), ("米饭200克", ""))

    def test_images_must_be_embedded_valid_type(self):
        for value in ("https://example.com/a.jpg", "data:image/jpeg;base64,invalid",
                      "data:image/jpeg;base64," + base64.b64encode(b"not jpeg").decode()):
            with self.subTest(value=value), self.assertRaises(ApiError):
                validate_query({"image_data": value})

    def test_accepts_image_without_text(self):
        image = "data:image/jpeg;base64," + base64.b64encode(b"\xff\xd8\xfftest").decode()
        self.assertEqual(validate_query({"image_data": image}), ("", image))

    def test_totals_are_computed_not_trusted(self):
        value = food()
        value["items"].append(dict(value["items"][0]))
        value["totals"] = {"calories_kcal": 1}
        result = validate_result(value)
        self.assertEqual(result["totals"]["calories_kcal"], 464)
        self.assertEqual(result["totals"]["grams"], 400)

    def test_nonfood_rejected(self):
        with self.assertRaises(ApiError) as error:
            validate_result({"food_detected": False, "items": []})
        self.assertEqual(error.exception.status, 422)

    def test_bad_model_numbers_rejected(self):
        for number in (float("nan"), float("inf"), -1, True, "200", None, 100001):
            value = food()
            value["items"][0]["calories_kcal"] = number
            with self.subTest(number=number), self.assertRaises(ApiError):
                validate_result(value)

    def test_zero_portion_rejected(self):
        value = food()
        value["items"][0]["grams"] = 0
        with self.assertRaises(ApiError):
            validate_result(value)

    def test_rate_and_daily_budget(self):
        budget = Budget(200)
        for _ in range(10):
            budget.reserve()
        with self.assertRaises(ApiError) as error:
            budget.reserve()
        self.assertEqual(error.exception.status, 429)
        budget = Budget(1)
        budget.reserve()
        with self.assertRaises(ApiError):
            budget.reserve()

    def test_endpoint_authentication_precedes_model(self):
        app = create_app(Settings("provider-secret", "x" * 32))
        with patch("app.call_provider") as provider:
            status, body = asyncio.run(request(app, {"text": "米饭"}, token="wrong"))
            self.assertEqual(status, 401)
            provider.assert_not_called()

    def test_endpoint_valid_text_and_image(self):
        app = create_app(Settings("provider-secret", "x" * 32))
        for body in ({"text": "米饭200克"}, {"image_data": "data:image/jpeg;base64," +
                     base64.b64encode(b"\xff\xd8\xfftest").decode()}):
            with patch("app.call_provider", return_value=validate_result(food())) as provider:
                status, result = asyncio.run(request(app, body))
                self.assertEqual(status, 200)
                self.assertTrue(result["estimated"])
                provider.assert_called_once()

    def test_unconfigured_service(self):
        status, body = asyncio.run(request(create_app(Settings("", "")), {"text": "米饭"}))
        self.assertEqual(status, 503)

    def test_oversized_and_malformed_requests(self):
        app = create_app(Settings("provider-secret", "x" * 32))
        for data, expected in ((b"x" * 3_000_001, 413), (b"{bad", 400)):
            status, _ = asyncio.run(request(app, data))
            self.assertEqual(status, expected)

    def test_provider_error_is_actionable(self):
        app = create_app(Settings("provider-secret", "x" * 32))
        with patch("app.call_provider", side_effect=ApiError(504, "AI服务连接超时，请稍后重试")):
            status, body = asyncio.run(request(app, {"text": "米饭"}))
            self.assertEqual(status, 504)
            self.assertIn("超时", body["error"])
            self.assertNotIn("provider-secret", json.dumps(body))


if __name__ == "__main__":
    unittest.main()
