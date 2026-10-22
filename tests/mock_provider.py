"""Local Stripe-style test double. Never contacts a payment network."""
import json
import os
import sqlite3
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

lock = threading.Lock()
db = sqlite3.connect(os.getenv('PROVIDER_DB', '/data/provider.db'), check_same_thread=False)
db.execute('CREATE TABLE IF NOT EXISTS charges(key TEXT PRIMARY KEY, payload TEXT, reference TEXT)')
mode = 'success'
attempts = []


class Handler(BaseHTTPRequestHandler):
    def send(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        with lock:
            self.send(200, {'mode': mode, 'charges': [{'key': r[0], 'payload': json.loads(r[1]), 'reference': r[2]} for r in db.execute('SELECT * FROM charges')], 'attempts': attempts})

    def do_POST(self):
        global mode
        try:
            if self.headers.get('Transfer-Encoding', '').lower() == 'chunked':
                chunks = []
                total = 0
                while True:
                    size = int(self.rfile.readline().split(b';')[0], 16)
                    if size < 0:
                        raise ValueError('Invalid chunk size')
                    if size == 0:
                        while self.rfile.readline().strip():
                            pass
                        break
                    total += size
                    if total > 16384:
                        raise ValueError('Body too large')
                    chunks.append(self.rfile.read(size))
                    self.rfile.read(2)
                raw = b''.join(chunks)
            else:
                length = int(self.headers.get('Content-Length', 0))
                if length > 16384:
                    raise ValueError('Body too large')
                raw = self.rfile.read(length)
            body = json.loads(raw)
        except (ValueError, TypeError):
            self.send(400, {'error': 'Invalid JSON'})
            return
        with lock:
            if self.path == '/control':
                if body.get('mode') not in ['success', 'decline', 'unavailable', 'lost-response']:
                    return self.send(400, {'error': 'Invalid mode'})
                mode = body['mode']
                return self.send(200, {'mode': mode})
            if self.path != '/charges':
                return self.send(404, {})
            key = self.headers.get('Idempotency-Key')
            if not key or body.get('amountMinor', 0) <= 0:
                return self.send(400, {'error': 'Invalid charge'})
            attempts.append({'key': key, 'correlationId': self.headers.get('X-Correlation-ID'), 'mode': mode, 'timeMs': int(time.time()*1000)})
            previous = db.execute('SELECT payload,reference FROM charges WHERE key=?', (key,)).fetchone()
            serialized = json.dumps(body, sort_keys=True)
            if previous:
                if previous[0] != serialized:
                    return self.send(409, {'error': 'Idempotency payload mismatch'})
                return self.send(200, {'id': previous[1]})
            if mode == 'unavailable':
                return self.send(503, {'error': 'Injected provider outage'})
            if mode == 'decline':
                return self.send(402, {'error': 'Synthetic card declined'})
            reference = 'mock-' + str(uuid.uuid4())
            db.execute('INSERT INTO charges VALUES(?,?,?)', (key, serialized, reference))
            db.commit()
            if mode == 'lost-response':
                return self.send(503, {'error': 'Charge stored, response lost'})
            return self.send(201, {'id': reference})


if __name__ == '__main__':
    ThreadingHTTPServer(('0.0.0.0', 8090), Handler).serve_forever()
