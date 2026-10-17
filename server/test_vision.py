import copy
import unittest
from unittest.mock import patch
from app import ApiError, Settings, call_provider, validate_result, completion_endpoint, call_model


def result():
    return {"food_detected": True, "items": [
        {"name": "米饭", "grams": 200, "calories_kcal": 260,
         "protein_g": 5, "carbs_g": 56, "fat_g": 1,
         "calories_min_kcal": 220, "calories_max_kcal": 300}],
        "assumptions": ["按熟重估算，实际重量需核对"]}


class VisionTests(unittest.TestCase):
    def test_custom_endpoint_and_key_are_used_without_key_format_restrictions(self):
        settings = Settings('a!', 'x' * 32, provider_url='https://custom.example.cn/api/v8')
        envelope = {'choices': [{'finish_reason': 'stop', 'message': {'content': '{"ok":true}'}}]}
        import json
        with patch('app.urllib.request.build_opener') as opener:
            opener.return_value.open.return_value.__enter__.return_value.read.return_value = json.dumps(envelope).encode()
            self.assertEqual(call_model(settings, {'model': 'custom'}), {'ok': True})
        request = opener.return_value.open.call_args.args[0]
        self.assertEqual(request.full_url, 'https://custom.example.cn/api/v8/chat/completions')
        self.assertEqual(request.get_header('Authorization'), 'Bearer a!')

    def test_custom_models_and_text_content_are_preserved(self):
        with patch('app.call_model', return_value=result()) as model:
            call_provider(Settings('key', 'x' * 32, text_model='anything/text'), '米饭', '')
        payload = model.call_args.args[1]
        self.assertEqual(payload['model'], 'anything/text')
        self.assertEqual(payload['messages'][1]['content'], '米饭')

    def test_missing_vision_model_rejects_photo_without_network(self):
        with patch('app.call_model') as model:
            with self.assertRaises(ApiError):
                call_provider(Settings('key', 'x' * 32, vision_model=''), '米饭', 'photo')
            model.assert_not_called()

    def test_endpoint_normalization_and_rejection(self):
        self.assertEqual(completion_endpoint('api.deepseek.com'), 'https://api.deepseek.com/v1/chat/completions')
        self.assertEqual(completion_endpoint('https://example.cn/v1/chat/completions/'), 'https://example.cn/v1/chat/completions')
        for url in ('http://example.cn', 'https://user:pass@example.cn', 'https://example.cn?q=key', ''):
            with self.subTest(url=url), self.assertRaises(ApiError):
                completion_endpoint(url)

    def test_photo_payload_preserves_description_and_requests_detail(self):
        with patch('app.call_model', return_value=result()) as model:
            call_provider(Settings('test', 'x' * 32), '米饭200克，少油', 'data:image/jpeg;base64,test')
        payload = model.call_args.args[1]
        self.assertEqual(payload['model'], 'deepseek-flash')
        content = payload['messages'][1]['content']
        self.assertEqual(content[0]['text'], '米饭200克，少油')
        self.assertEqual(content[1]['image_url']['detail'], 'high')

    def test_ranges_are_summed_and_untrusted_totals_ignored(self):
        value = result()
        value['items'].append(copy.deepcopy(value['items'][0]))
        value['totals'] = {'calories_kcal': 1}
        clean = validate_result(value)
        self.assertEqual(clean['totals']['calories_kcal'], 520)
        self.assertEqual(clean['totals']['calories_min_kcal'], 440)
        self.assertEqual(clean['totals']['calories_max_kcal'], 600)

    def test_impossible_density_or_macros_are_rejected(self):
        for key, value in [('calories_kcal', 2000), ('protein_g', 300), ('grams', 10001)]:
            invalid = result()
            invalid['items'][0][key] = value
            with self.subTest(key=key), self.assertRaises(ApiError):
                validate_result(invalid)

    def test_bad_or_incomplete_ranges_are_rejected(self):
        for low, high in [(400, 500), (100, 200), (-1, 500), ('100', 500), (100, True)]:
            value = result()
            value['items'][0].update(calories_min_kcal=low, calories_max_kcal=high)
            with self.subTest(low=low, high=high), self.assertRaises(ApiError):
                validate_result(value)
        value = result()
        del value['items'][0]['calories_max_kcal']
        with self.assertRaises(ApiError):
            validate_result(value)

    def test_old_model_response_without_ranges_remains_compatible(self):
        value = result()
        del value['items'][0]['calories_min_kcal']
        del value['items'][0]['calories_max_kcal']
        self.assertNotIn('calories_min_kcal', validate_result(value)['totals'])


if __name__ == '__main__':
    unittest.main()
