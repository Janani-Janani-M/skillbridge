#!/usr/bin/env python3
"""End-to-end HTTP tests. Python 3.9+ standard library only; owns its isolated test DB."""
import os, re, sys, time, socket, tempfile, subprocess, pathlib
import urllib.request, urllib.parse, urllib.error, http.cookiejar
from datetime import datetime, timedelta

checks = 0

def check(ok, label):
    global checks
    checks += 1
    if not ok:
        raise AssertionError(label)
    print('PASS ' + label, flush=True)

class Client:
    def __init__(self, base):
        self.base = base
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    def request(self, path, data=None):
        encoded = None if data is None else urllib.parse.urlencode(data).encode()
        try:
            with self.opener.open(self.base + path, data=encoded, timeout=20) as r:
                return r.status, r.read().decode(), r.headers
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode(), e.headers
    def get(self, path):
        return self.request(path)
    def csrf(self, path='/'):
        code, html, _ = self.get(path)
        check(code == 200, 'Form page available: ' + path)
        match = re.search(r"name='csrf' value='([^']+)'", html)
        if not match:
            raise AssertionError('Missing CSRF form token on ' + path)
        return match.group(1)
    def post(self, path, fields, page='/'):
        return self.request(path, dict(fields, csrf=self.csrf(page)))
    def login(self, email, password):
        code, html, _ = self.post('/login', {'email': email, 'password': password}, '/login')
        check(code == 200 and 'Sign out' in html, 'Sign in: ' + email)


def main():
    build = pathlib.Path('build')
    build.mkdir(exist_ok=True)
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    with tempfile.TemporaryDirectory(prefix='http-tests-', dir=build) as temp:
        env = dict(os.environ, PORT=str(port), SB_DB_URL='jdbc:h2:' + str(pathlib.Path(temp).resolve() / 'testdb') + ';DB_CLOSE_ON_EXIT=FALSE', SB_ADMIN_PASSWORD='Admin@12345', SB_HOST='127.0.0.1', SB_COOKIE_SECURE='false')
        java = os.environ.get('JAVA', 'java')
        cp = 'build/classes' + os.pathsep + 'lib/h2-2.3.232.jar'
        log_path = pathlib.Path(temp) / 'server.log'
        log = open(log_path, 'w+')
        def start():
            process = subprocess.Popen([java, '--add-modules', 'jdk.httpserver', '-cp', cp, 'skillbridge.App'], env=env, stdout=log, stderr=log)
            for _ in range(100):
                if process.poll() is not None:
                    log.flush()
                    raise RuntimeError('Server stopped: ' + log_path.read_text())
                try:
                    Client(f'http://127.0.0.1:{port}').get('/login')
                    return process
                except OSError:
                    time.sleep(.15)
            process.terminate()
            raise RuntimeError('Server did not become ready')
        def stop(process):
            process.terminate()
            try: process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill(); process.wait()
        process = start()
        base = f'http://127.0.0.1:{port}'
        try:
            anon, student, admin, other = [Client(base) for _ in range(4)]
            code, html, headers = anon.get('/applications')
            check(code == 200 and 'Welcome back' in html, 'Unauthenticated pages redirect to login')
            check("script-src 'none'" in headers['Content-Security-Policy'], 'Content security policy')
            check('HttpOnly' in headers.get('Set-Cookie', '') or 'name=' in html, 'Login page established')
            code, _, _ = anon.request('/login', {'email': 'student@skillbridge.local', 'password': 'Student@12345'})
            check(code == 403, 'Login requires CSRF token')
            code, _, _ = anon.post('/login', {'email': 'student@skillbridge.local', 'password': 'wrongpassword1'}, '/login')
            check(code == 400, 'Wrong password rejected')
            student.login('student@skillbridge.local', 'Student@12345')
            admin.login('admin@skillbridge.local', 'Admin@12345')
            other.login('rahul@skillbridge.local', 'Student@12345')
            for path in ['/', '/opportunities', '/opportunities?sort=match', '/opportunities?state=ALL', '/opportunities?q=backend', '/saved', '/profile', '/applications', '/notifications', '/analytics', '/analytics?n=5&k=2&p=0.2&trials=1000&threshold=70', '/reports', '/opportunity?id=1']:
                code, html, _ = student.get(path)
                check(code == 200 and '<html' in html, 'Student page: ' + path)
            for path in ['/', '/admin/students', '/admin/skills', '/admin/audit', '/admin/opportunity', '/admin/opportunity?id=1', '/applications', '/analytics', '/reports']:
                check(admin.get(path)[0] == 200, 'Admin page: ' + path)
            check(student.get('/admin/students')[0] == 403, 'Student cannot access admin directory')
            check(student.get('/missing-page')[0] == 404, 'Unknown route returns 404')
            check(student.get('/opportunity?id=99999')[0] == 404, 'Missing opportunity returns 404')
            check(student.get('/opportunity?id=bad')[0] == 400, 'Malformed ID returns 400')
            check(student.get('/analytics?n=1&k=5')[0] == 400, 'Impossible binomial request rejected')
            check(student.get('/analytics?p=NaN')[0] == 400, 'Non-finite probability rejected')
            code, _, _ = student.post('/save', {'opportunity': 1})
            check(code == 200 and 'Backend Engineering Intern' in student.get('/saved')[1], 'Save opportunity')
            student.post('/save', {'opportunity': 1})
            check('Nothing saved yet' in student.get('/saved')[1], 'Unsave opportunity')
            code, _, _ = student.request('/apply', {'csrf': 'invalid', 'opportunity': 1, 'cover_letter': 'Hello'})
            check(code == 403, 'Invalid CSRF rejected')
            code, html, _ = student.post('/apply', {'opportunity': 1, 'cover_letter': 'I enjoy backend engineering and relational databases. <script>not executable</script>'})
            check(code == 200 and 'Application submitted successfully' in html, 'Student submits application')
            check('&lt;script&gt;' in html and '<script>' not in html, 'Stored cover note is HTML-escaped')
            app_id = re.search(r'APPLICATION #(\d+)', html).group(1)
            app_page = '/application?id=' + app_id
            check(other.get(app_page)[0] == 404, 'Other student cannot read application')
            check(student.post('/apply', {'opportunity': 1, 'cover_letter': 'Duplicate'})[0] == 400, 'Duplicate application rejected by server')
            check(other.post('/status', {'application': app_id, 'version': 0, 'status': 'WITHDRAWN'})[0] == 400, 'Cross-student status change blocked')
            check(student.post('/admin/status', {'application': app_id, 'version': 0, 'status': 'SHORTLISTED'})[0] == 403, 'Student cannot use admin status endpoint')
            check(admin.post('/admin/status', {'application': app_id, 'version': 0, 'status': 'OFFERED'})[0] == 400, 'Admin cannot skip pipeline')
            check(admin.post('/admin/status', {'application': app_id, 'version': 0, 'status': 'SHORTLISTED', 'note': 'Selected for technical review'})[0] == 200, 'Admin shortlists applicant')
            check(admin.post('/admin/status', {'application': app_id, 'version': 0, 'status': 'REJECTED'})[0] == 400, 'Stale update rejected')
            check(admin.post('/admin/status', {'application': app_id, 'version': 1, 'status': 'INTERVIEW'})[0] == 400, 'Interview requires time and venue')
            at = (datetime.now() + timedelta(days=3)).strftime('%Y-%m-%dT%H:%M')
            check(admin.post('/admin/status', {'application': app_id, 'version': 1, 'status': 'INTERVIEW', 'interview_at': at, 'location': 'Placement room 201', 'note': 'Technical interview'})[0] == 200, 'Admin schedules interview')
            check('Placement room 201' in student.get(app_page)[1], 'Student sees interview details')
            check(admin.post('/admin/status', {'application': app_id, 'version': 2, 'status': 'OFFERED', 'note': 'Offer issued'})[0] == 200, 'Admin issues offer')
            check(student.post('/status', {'application': app_id, 'version': 3, 'status': 'ACCEPTED', 'note': 'Thank you, I accept.'})[0] == 200, 'Student accepts offer')
            check('Thank you, I accept.' in admin.get(app_page)[1], 'Admin sees student decision in timeline')
            check('now accepted' in student.get('/notifications')[1], 'Status notification delivered')
            check(student.post('/notifications/read', {})[0] == 200, 'Notifications can be marked read')
            code, csv, headers = student.get('/reports/applications.csv')
            check(code == 200 and 'ACCEPTED' in csv and 'attachment;' in headers['Content-Disposition'], 'Student CSV export')
            check('Ananya Rao' not in other.get('/reports/applications.csv')[1], 'CSV export is owner-scoped')
            check('Ananya Rao' in admin.get('/reports/applications.csv')[1], 'Admin CSV includes campus applications')
            check('100.0%' in admin.get('/analytics')[1], 'Observed outcome calculations render')
            check('application submitted' in admin.get('/admin/audit')[1], 'Application audit recorded')
            # Register a fresh account and complete its profile.
            fresh = Client(base)
            code, html, _ = fresh.post('/register', {'name': 'Test Student', 'email': 'test.student@example.edu', 'password': 'Teststudent123', 'department': 'Computer Science', 'year': 2027}, '/register')
            check(code == 200 and 'Complete your profile' in html, 'Student self-registration')
            profile = {'name': '=Test Student', 'department': 'Computer Science', 'cgpa': '8.5', 'year': 2027, 'bio': 'I build small Java systems.', 'portfolio': 'https://example.com/portfolio', 'skill_1': 3, 'skill_2': 2}
            check(fresh.post('/profile', profile)[0] == 200, 'Profile and proficiency save')
            bad = dict(profile, cgpa='11')
            check(fresh.post('/profile', bad)[0] == 400, 'Invalid CGPA rejected')
            bad = dict(profile, portfolio='javascript:alert(1)')
            check(fresh.post('/profile', bad)[0] == 400, 'Unsafe portfolio URL rejected')
            code, html, _ = fresh.post('/apply', {'opportunity': 2, 'cover_letter': 'I enjoy analysing data.'})
            check(code == 200, 'Fresh student applies after profile completion')
            fresh_id = re.search(r'APPLICATION #(\d+)', html).group(1)
            code, csv, _ = fresh.get('/reports/applications.csv')
            check("'=Test Student" in csv, 'CSV formula injection mitigation')
            check(fresh.post('/status', {'application': fresh_id, 'version': 0, 'status': 'WITHDRAWN', 'note': 'Plans changed'})[0] == 200, 'Student withdraws application')
            check(fresh.post('/apply', {'opportunity': 2, 'cover_letter': 'Again'})[0] == 400, 'Withdrawal does not bypass one-submission rule')
            # Admin catalogue and full opportunity creation / edit / closure.
            check(admin.post('/admin/skill', {'name': 'Database Design'})[0] == 200, 'Admin adds catalogue skill')
            check(admin.post('/admin/skill', {'name': 'database design'})[0] == 400, 'Catalogue deduplicates case-insensitively')
            role = {'title': 'Quality Engineer Intern', 'company': 'Oak Research', 'kind': 'INTERNSHIP', 'location': 'Remote', 'compensation': 16000, 'cgpa': 7, 'year': 2027, 'deadline': (datetime.now() + timedelta(days=20)).date().isoformat(), 'description': 'Write deterministic tests for Java services and maintain reliable SQL datasets.', 'status': 'OPEN', 'skill_1': 2, 'skill_2': 2}
            check(admin.post('/admin/opportunity', dict(role, skill_1=0, skill_2=0))[0] == 400, 'At least one required skill enforced')
            code, html, _ = admin.post('/admin/opportunity', role)
            check(code == 200 and 'Quality Engineer Intern' in html, 'Admin creates opportunity')
            oid = re.search(r'/admin/opportunity\?id=(\d+)', html).group(1)
            check(admin.post('/admin/opportunity', dict(role, id=oid, status='CLOSED'))[0] == 200, 'Admin closes opportunity')
            check(student.post('/apply', {'opportunity': oid, 'cover_letter': 'Interested'})[0] == 400, 'Closed opportunity blocked at HTTP layer')
            check(fresh.post('/password', {'current_password': 'wrong', 'new_password': 'Updatedstudent123'})[0] == 400, 'Password change checks current password')
            check(fresh.post('/password', {'current_password': 'Teststudent123', 'new_password': 'Updatedstudent123'})[0] == 200, 'Password change succeeds')
            fresh.post('/logout', {})
            check('Welcome back' in fresh.get('/')[1], 'Logout revokes session')
            fresh.login('test.student@example.edu', 'Updatedstudent123')
            # Restart the real server and confirm disk persistence.
            stop(process)
            process = start()
            again = Client(base)
            again.login('student@skillbridge.local', 'Student@12345')
            persisted = again.get(app_page)[1]
            check('Thank you, I accept.' in persisted and 'status-ACCEPTED' in persisted, 'Applications and timeline persist after server restart')
            check('Quality Engineer Intern' in again.get('/opportunities?state=CLOSED')[1], 'Opportunity changes persist after restart')
            log.flush()
            check('Request error' not in log_path.read_text() and 'Database error' not in log_path.read_text(), 'No unexpected server/database errors during suite')
            print('ALL ' + str(checks) + ' HTTP CHECKS PASSED', flush=True)
        finally:
            stop(process)
            log.close()

if __name__ == '__main__':
    main()
