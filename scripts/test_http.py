"""Real HTTP session/CSRF/lifecycle checks against the Docker application."""
import http.cookiejar
import json
import os
import urllib.request
import urllib.parse
import urllib.error
import time
BASE = os.getenv('API_BASE_URL', 'http://127.0.0.1:8080')
PASSWORD = os.getenv('DEMO_PASSWORD', 'local-demo-change-me')
class Client:
    def __init__(self):
        self.cookies = http.cookiejar.CookieJar()
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))
        self.token = None
    def request(self, path, data=None, expected=200, csrf=True, extra_headers=None):
        headers = dict(extra_headers or {})
        if data is not None and csrf and self.token:
            headers['X-CSRF-TOKEN'] = self.token
        req = urllib.request.Request(BASE + path, data=None if data is None else urllib.parse.urlencode(data).encode(), headers=headers)
        try:
            response = self.http.open(req, timeout=5)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            body = json.load(response)
            assert response.status == expected, (path, response.status, expected, body)
            return body
    def refresh(self):
        self.token = self.request('/api/auth/csrf')['token']
    def login(self, name):
        self.refresh()
        previous = next(c.value for c in self.cookies if c.name == 'JSESSIONID')
        self.request('/api/auth/login', dict(username=name, password=PASSWORD))
        current = next(c.value for c in self.cookies if c.name == 'JSESSIONID')
        assert current != previous
        self.refresh()
        me = self.request('/api/auth/me')
        assert 'passwordHash' not in me and me['username'] == name
        return self

for attempt in range(30):
    try:
        Client().request('/api/auth/csrf')
        break
    except (OSError, urllib.error.URLError):
        time.sleep(1)
else:
    raise RuntimeError('API did not become ready')

anonymous = Client()
anonymous.request('/api/tickets', expected=401)
anonymous.request('/api/tickets', expected=401, extra_headers={'Authorization': 'Bearer cut-demo'})
anonymous.refresh()
anonymous.request('/api/auth/login', dict(username='cut-demo', password='wrong'), expected=401)
department = Client().login('department-demo')
colleague = Client().login('department2-demo')
radiology = Client().login('radiology-demo')
cut = Client().login('cut-demo')
operator = Client().login('operator-demo')
other = Client().login('operator2-demo')
form = dict(patientCode='FAKE-HTTP-😀', originId='00000000-0000-0000-0000-000000000001', destinationId='00000000-0000-0000-0000-000000000002', priority='URGENT')
cut.request('/api/tickets', form, expected=403)
department.request('/api/tickets', form, expected=403, csrf=False)
ticket = department.request('/api/tickets', form, expected=201)
path = '/api/tickets/' + ticket['id']
colleague.request(path)
radiology.request(path, expected=404)
cut.request(path + '/assign', dict(operatorId='00000000-0000-0000-0000-000000000003'))
other.request(path + '/accept', {}, expected=404)
operator.request(path + '/accept', {})
operator.request(path + '/start', {})
before = cut.request(path)
operator.request(path + '/identify', dict(patientCode='WRONG'), expected=400)
assert cut.request(path) == before
operator.request(path + '/identify', dict(patientCode=form['patientCode']))
for step in ['depart', 'arrive', 'complete']:
    completed = operator.request(path + '/' + step, {})
assert completed['status'] == 'COMPLETED' and len(completed['history']) == 8
cut.request('/api/auth/logout', {})
cut.request('/api/tickets', expected=401)
print('Real HTTP checks passed: persistent login, fixation protection, CSRF, roles, department access, lifecycle, logout.')
