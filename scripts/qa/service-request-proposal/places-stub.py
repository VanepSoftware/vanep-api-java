"""Local stand-in for Google Places during the rule 55 QA: serves the recorded fixtures under
src/test/resources/fixtures/places, so the run never spends Places quota (constitution rule 50).

Usage: python3 -I places-stub.py <port>. GET /v1/places/<fixture-name> returns that fixture."""

import json
import pathlib
import re
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

FIXTURES = pathlib.Path(__file__).resolve().parents[3] / "src/test/resources/fixtures/places"
PLACE_PATH = re.compile(r"^/v1/places/([A-Za-z0-9_-]+)(\?.*)?$")


class PlacesStubHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        match = PLACE_PATH.match(self.path)
        fixture = FIXTURES / f"{match.group(1)}.json" if match else None
        if fixture is None or not fixture.is_file():
            self.send_json(404, {"error": {"code": 404, "status": "NOT_FOUND"}})
            return
        self.send_json(200, json.loads(fixture.read_text(encoding="utf-8")))

    def send_json(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), PlacesStubHandler).serve_forever()
