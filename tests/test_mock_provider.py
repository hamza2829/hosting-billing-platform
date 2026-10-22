import http.client
import json
import os
import threading
import unittest
from http.server import ThreadingHTTPServer

os.environ['PROVIDER_DB'] = ':memory:'
import mock_provider


class ProviderTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(('127.0.0.1', 0), mock_provider.Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def setUp(self):
        with mock_provider.lock:
            mock_provider.db.execute('DELETE FROM charges')
            mock_provider.db.commit()
            mock_provider.attempts.clear()
            mock_provider.mode = 'success'

    def charge(self, amount=1200, chunked=False):
        connection = http.client.HTTPConnection('127.0.0.1', self.server.server_port)
        payload = json.dumps({'invoiceId': 'synthetic-invoice', 'amountMinor': amount, 'currency': 'EUR'}).encode()
        body = iter([payload[:12], payload[12:]]) if chunked else payload
        connection.request('POST', '/charges', body, {'Content-Type': 'application/json', 'Idempotency-Key': 'invoice-test'}, encode_chunked=chunked)
        response = connection.getresponse()
        status, data = response.status, json.loads(response.read())
        connection.close()
        return status, data

    def test_chunked_body(self):
        self.assertEqual(self.charge(chunked=True)[0], 201)

    def test_replay_returns_one_charge(self):
        first = self.charge()
        second = self.charge()
        self.assertEqual(first[1], second[1])
        self.assertEqual(mock_provider.db.execute('SELECT count(*) FROM charges').fetchone()[0], 1)

    def test_ambiguous_response_is_idempotent(self):
        mock_provider.mode = 'lost-response'
        self.assertEqual(self.charge()[0], 503)
        self.assertEqual(self.charge()[0], 200)
        self.assertEqual(mock_provider.db.execute('SELECT count(*) FROM charges').fetchone()[0], 1)

    def test_same_key_different_payload_conflicts(self):
        self.charge()
        self.assertEqual(self.charge(amount=999)[0], 409)


if __name__ == '__main__':
    unittest.main()
