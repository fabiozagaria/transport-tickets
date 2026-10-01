"""Integration checks against a real HTTP server, using only Python and JDK 17+."""
import concurrent.futures
import datetime
import json
import pathlib
import subprocess
import urllib.error
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
classes = ROOT / 'target' / 'classes'
classes.mkdir(parents=True, exist_ok=True)
subprocess.run(['javac', '--release', '17', '-d', str(classes),
                *map(str, (ROOT / 'src/main/java').rglob('*.java')),
                *map(str, (ROOT / 'src/test/java').rglob('*.java'))], check=True)
subprocess.run(['java', '-cp', str(classes),
                'it.fabio.transport.application.TicketServiceCheck'], check=True)
process = subprocess.Popen(['java', '-cp', str(classes), 'it.fabio.transport.api.TicketApi', '0'],
                           stdout=subprocess.PIPE, text=True)

try:
    base = process.stdout.readline().strip().split()[-1]

    def request(path, token='cut-demo', data=None, method=None, expected=200, raw=None, content_type=None):
        headers = {'Authorization': 'Bearer ' + token} if token else {}
        body = urllib.parse.urlencode(data).encode() if data is not None else raw
        if body is not None:
            headers['Content-Type'] = content_type or 'application/x-www-form-urlencoded'
        req = urllib.request.Request(base + path, data=body, headers=headers, method=method)
        try:
            response = urllib.request.urlopen(req, timeout=5)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            value = json.load(response)
            assert response.status == expected, (path, response.status, expected, value)
            assert response.headers['Cache-Control'] == 'no-store'
            return value

    form = dict(patientCode='FAKE-"\\\n😀', originId='00000000-0000-0000-0000-000000000001',
                destinationId='00000000-0000-0000-0000-000000000002', priority='URGENT')

    def create():
        return request('/api/tickets', 'department-demo', form, expected=201)

    request('/api/tickets', token=None, expected=401)
    request('/api/tickets', 'cut-demo', form, expected=403)
    request('/api/tickets', 'department-demo', {**form, 'createdAt': '2000-01-01T00:00:00Z'}, expected=400)
    request('/api/tickets', 'department-demo', {**form, 'scheduledAt': 'nonsense'}, expected=400)
    request('/api/tickets', 'department-demo', raw=b'{}', method='POST', content_type='application/json', expected=415)
    request('/api/tickets', 'department-demo', raw=b'x' * 8193, method='POST', expected=413)
    request('/api/tickets', 'department-demo', raw=b'priority=NORMAL&priority=URGENT', method='POST', expected=400)
    request('/api/tickets', method='DELETE', expected=405)
    request('/api/operators', 'operator-demo', expected=403)
    assert len(request('/api/departments')) == 2
    operators = request('/api/operators')
    op1, op2 = [u['id'] for u in operators]
    ticket = create()
    assert ticket['patientCode'] == form['patientCode']
    assert ticket['status'] == 'UNASSIGNED' and len(ticket['history']) == 1
    parse = lambda value: datetime.datetime.fromisoformat(value.replace('Z', '+00:00'))
    assert parse(ticket['assignmentDeadline']) - parse(ticket['createdAt']) == datetime.timedelta(minutes=20)
    path = '/api/tickets/' + ticket['id']
    request(path, 'operator-demo', expected=404)
    assert request('/api/tickets', 'operator-demo') == []
    ticket = request(path + '/assign', data={'operatorId': op1})
    first_assignment = ticket['firstAssignedAt']
    request(path + '/start', 'operator-demo', {}, expected=409)
    request(path + '/accept', 'operator-demo', {})
    ticket = request(path + '/assign', data={'operatorId': op2})
    assert ticket['status'] == 'ASSIGNED' and ticket['firstAssignedAt'] == first_assignment
    request(path + '/accept', 'operator-demo', {}, expected=404)
    request(path + '/accept', 'operator2-demo', {})
    request(path + '/start', 'operator2-demo', {})
    before = request(path)
    request(path + '/identify', 'operator2-demo', {'patientCode': 'WRONG'}, expected=400)
    request(path + '/assign', data={'operatorId': op1}, expected=409)
    assert request(path) == before
    request(path + '/identify', 'operator2-demo', {'patientCode': form['patientCode']})
    for action in ['depart', 'arrive', 'complete']:
        ticket = request(path + '/' + action, 'operator2-demo', {})
    assert ticket['status'] == 'COMPLETED' and ticket['completedAt']
    assert len(ticket['history']) == 10
    request(path + '/complete', 'operator2-demo', {}, expected=409)
    assert request(path) == ticket
    request(path + '/unknown', data={}, expected=404)

    # Two simultaneous accepts: exactly one succeeds and one history event is added.
    second = create()
    path2 = '/api/tickets/' + second['id']
    request(path2 + '/assign', data={'operatorId': op1})

    def accept_concurrently(_):
        req = urllib.request.Request(base + path2 + '/accept', data=b'',
                                     headers={'Authorization': 'Bearer operator-demo'})
        try:
            with urllib.request.urlopen(req, timeout=5) as response:
                return response.status
        except urllib.error.HTTPError as error:
            error.close()
            return error.code

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        assert sorted(pool.map(accept_concurrently, range(2))) == [200, 409]
    assert len(request(path2)['history']) == 3
    print('API integration checks passed: lifecycle, validation, roles, visibility, history, deadlines, concurrency.')
finally:
    process.terminate()
    process.wait(timeout=10)
