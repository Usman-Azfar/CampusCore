"""Fictional demo data for the CampusCore live demo (and the README screenshots).

Writes one SQL file that rebuilds the demo database: the full database_schema.sql (it drops and
recreates every table, with its seed rows), then the demo data below. The database itself is kept,
not dropped, so the running application's pooled connections stay valid during the nightly reset. Every date is worked out
from the run date, so the demo always looks current:

- the current term started on the 1st of last month (so "today" is 4-8 weeks into it);
- the four earlier terms each started 6 months before the next one;
- a term is named after the season its middle falls in: August-January "Fall", February-July
  "Spring" (6 months apart, so names never repeat).

The data was designed around 1 Oct 2026 with Fall 2026 as the current term (see DESIGN_* below);
design dates are moved to real dates with real_dt(). All names and records are made up.

Usage:  python tools/demo/demo_data.py [--database cms_ead] [--today YYYY-MM-DD] [--out demo.sql]
"""
import argparse
import os
import random
from datetime import date, datetime, timedelta

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))

ap = argparse.ArgumentParser(description="Generate the CampusCore demo database script.")
ap.add_argument("--database", default="cms_ead", help="database name to (re)create (default cms_ead)")
ap.add_argument("--today", help="pretend today is this date (YYYY-MM-DD), for testing")
ap.add_argument("--out", default=os.path.join(HERE, "demo.sql"), help="output file (default tools/demo/demo.sql)")
ap.add_argument("--schema", default=os.path.join(ROOT, "database_schema.sql"), help="path to database_schema.sql")
args = ap.parse_args()
DB = args.database
assert DB.replace("_", "").isalnum(), "database name: letters, digits and _ only"

TODAY = date.fromisoformat(args.today) if args.today else date.today()

# ---------------- terms ----------------


def add_months(d, n):
    m = d.month - 1 + n
    return date(d.year + m // 12, m % 12 + 1, 1)


def term_name(start):
    mid = add_months(start, 2)
    if mid.month >= 8:
        return "Fall %d" % mid.year
    if mid.month == 1:
        return "Fall %d" % (mid.year - 1)
    return "Spring %d" % mid.year


CURRENT_START = add_months(date(TODAY.year, TODAY.month, 1), -1)
REAL_START = {k: add_months(CURRENT_START, -6 * (5 - k)) for k in range(1, 6)}
REAL_END = {k: add_months(REAL_START[k], 4) + timedelta(days=14) for k in range(1, 6)}
NAME = {k: term_name(REAL_START[k]) for k in range(1, 6)}

DESIGN_TODAY = datetime(2026, 10, 1)
DESIGN_START = {1: date(2024, 9, 1), 2: date(2025, 2, 1), 3: date(2025, 9, 1), 4: date(2026, 2, 1), 5: date(2026, 9, 1)}
RECENT_FROM = datetime(2026, 7, 1)  # design dates from here on are placed relative to today


def real_dt(design):
    """A design date/time ('2026-09-28 16:00:00' or '2024-09-15') moved to the real calendar."""
    d = datetime.fromisoformat(design)
    if d >= RECENT_FROM:  # current term and just before it: same distance from today
        r = datetime.combine(TODAY, datetime.min.time()) + (d - DESIGN_TODAY)
    else:  # earlier terms: same distance from the start of the matching term
        k = max([k for k in DESIGN_START if DESIGN_START[k] <= d.date()] or [1])
        r = datetime.combine(REAL_START[k], datetime.min.time()) + (d - datetime.combine(DESIGN_START[k], datetime.min.time()))
    return r.strftime("%Y-%m-%d") if len(design) == 10 else r.strftime("%Y-%m-%d %H:%M:%S")


def real_date(design):
    return date.fromisoformat(real_dt(design[:10]))


def nice(d):  # 15 October 2026
    return "%d %s %d" % (d.day, d.strftime("%B"), d.year)


# Batch years: designed for Fall 2026 (BS Computer Science-2022 in its 7th semester)
_season, _year = NAME[5].split()
BASE_YEAR = int(_year) if _season == "Fall" else int(_year) - 1

# ---------------- output helpers ----------------

R = random.Random(42)
TEACHER_PW = 'pbkdf2_sha256$600000$UbRW9R+Iz5YJzwDMfXQ2Lw==$/o7DimjeYC/sQL7t5VXeCCamHAeB/FgjucdRwe+lnMc='   # Teacher123
STUDENT_PW = 'pbkdf2_sha256$600000$hR3qIvAeUDoaJaSAds5gZw==$odNmOuk93/nYC05Jx1oOKEYa319ji55MFznTIJ1kAEM='   # Usman123
out = []


def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "TRUE" if v else "FALSE"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def ins(table, cols, rows):
    if rows:
        out.append("INSERT INTO `%s` (%s) VALUES\n%s;" % (
            table, ", ".join("`%s`" % c for c in cols),
            ",\n".join("(" + ", ".join(q(v) for v in r) + ")" for r in rows)))


def sql(statement):
    out.append(statement)


# ---------------- the schema and its seed rows ----------------
schema = open(args.schema, encoding="utf-8").read()
assert "CREATE DATABASE IF NOT EXISTS cms_ead;" in schema and "USE cms_ead;" in schema
schema = schema.replace("CREATE DATABASE IF NOT EXISTS cms_ead;", "CREATE DATABASE IF NOT EXISTS `%s`;" % DB) \
               .replace("USE cms_ead;", "USE `%s`;" % DB)

header = ["-- CampusCore demo database: generated by tools/demo/demo_data.py for %s." % TODAY.isoformat(),
          "-- Fictional data. Drops and recreates every table of `%s` (the database itself is kept)." % DB,
          "SET time_zone = '+00:00';"]

sql("USE `%s`;" % DB)
sql("SET time_zone = '+00:00';")
sql("SET FOREIGN_KEY_CHECKS = 0;")

# Seed rows of database_schema.sql moved to the real calendar
sql("UPDATE semesters SET name = %s, start_date = %s, end_date = %s, is_active = FALSE WHERE semester_id = 1;"
    % (q(NAME[1]), q(REAL_START[1].isoformat()), q(REAL_END[1].isoformat())))
for lid, d in zip(range(1, 6), ["2024-09-02", "2024-09-04", "2024-09-09", "2024-09-11", "2024-09-16"]):
    sql("UPDATE lectures SET lecture_date = %s, created_at = %s WHERE lecture_id = %d;" % (q(real_dt(d)), q(real_dt(d + " 10:00:00")), lid))
sql("UPDATE grades SET updated_at = %s WHERE enrollment_id = 1;" % q(real_dt("2025-01-20 10:00:00")))
sql("UPDATE grades SET updated_at = %s WHERE enrollment_id = 2;" % q(real_dt("2024-11-15 10:00:00")))
sql("UPDATE challans SET title = %s, upload_date = %s, due_date = %s, paid_at = %s WHERE challan_id = 1;"
    % (q("Semester Fee - " + NAME[1]), q(real_dt("2024-08-20 09:00:00")), q(real_dt("2024-09-15")), q(real_dt("2024-09-10 10:00:00"))))
sql("UPDATE challans SET upload_date = %s, due_date = %s WHERE challan_id = 2;" % (q(real_dt("2024-09-20 09:00:00")), q(real_dt("2024-10-01"))))
sql("UPDATE announcements SET title = %s, content = %s, created_at = %s WHERE announcement_id = 1;"
    % (q("Welcome to " + NAME[1]), q("Welcome back students! Classes commence on %s." % nice(REAL_START[1])), q(real_dt("2024-08-25 09:00:00"))))
sql("UPDATE announcements SET created_at = %s WHERE announcement_id = 2;" % q(real_dt("2024-09-03 10:00:00")))
sql("UPDATE messages SET timestamp = %s, is_read = TRUE WHERE message_id = 1;" % q(real_dt("2026-09-24 11:00:00")))
sql("UPDATE messages SET timestamp = %s WHERE message_id = 2;" % q(real_dt("2026-09-24 18:30:00")))
sql("UPDATE support_tickets SET created_at = %s WHERE ticket_id = 1;" % q(real_dt("2025-02-10 12:00:00")))
sql("UPDATE support_tickets SET created_at = %s WHERE ticket_id = 2;" % q(real_dt("2026-09-28 10:00:00")))
sql("UPDATE users SET created_at = %s WHERE user_id IN (1, 2, 3);" % q(real_dt("2024-08-10 09:00:00")))

# ---------------- departments (1 = Computer Science exists) ----------------
DEPTS = {1: "Computer Science", 2: "Software Engineering", 3: "Information Technology", 4: "Mathematics", 5: "Data Science"}
ins("departments", ["name"], [[DEPTS[i]] for i in range(2, 6)])

# ---------------- terms 2-5 (1 exists) ----------------
ins("semesters", ["name", "start_date", "end_date", "is_active"],
    [[NAME[k], REAL_START[k].isoformat(), REAL_END[k].isoformat(), k == 5] for k in range(2, 6)])

# ---------------- classes (1 = BS Computer Science-2022 exists) ----------------
# id: (degree, program, years before the base year, department, roll prefix letters, semester number now or None = graduated)
CLASS_DEF = {
    1: ("BS", "Computer Science", 4, 1, "BCS", 7),
    2: ("BS", "Computer Science", 3, 1, "BCS", 5),
    3: ("BS", "Computer Science", 2, 1, "BCS", 3),
    4: ("BS", "Software Engineering", 3, 2, "BSE", 5),
    5: ("BS", "Software Engineering", 2, 2, "BSE", 3),
    6: ("BS", "Information Technology", 2, 3, "BIT", 3),
    7: ("BS", "Data Science", 1, 5, "BDS", 3),
    8: ("BS", "Computer Science", 0, 1, "BCS", 1),
    9: ("BS", "Information Technology", 4, 3, "BIT", None),  # finished its 8th semester last term
}
CLASSES = {}
for cid, (deg, prog, back, dept, letters, num) in CLASS_DEF.items():
    batch = BASE_YEAR - back
    CLASSES[cid] = (deg, prog, batch, dept, "%sF%02dM" % (letters, batch % 100), num)
sql("UPDATE classes SET batch_year = %d WHERE class_id = 1;" % CLASSES[1][2])
ins("classes", ["degree", "program_name", "batch_year", "department_id"], [list(CLASSES[i][:4]) for i in range(2, 10)])


def class_name(cid):
    c = CLASSES[cid]
    return "%s %s-%d" % (c[0], c[1], c[2])


sql("DELETE FROM class_semesters;")
cs_rows = []
for cid, c in CLASSES.items():
    if c[5] is None:
        for k in range(1, 5):  # last term = 8th
            cs_rows.append([cid, k, 8 - (4 - k), False])
        continue
    for k in range(1, 6):
        n = c[5] - (5 - k)
        if n >= 1:
            cs_rows.append([cid, k, n, k == 5])
ins("class_semesters", ["class_id", "semester_id", "semester_number", "is_current"], cs_rows)

# ---------------- teachers (3 = TEACHER1 Dr. Sarah Ahmed exists) ----------------
TEACHERS = [  # username, name, gender, dept, active
    ("TCH-1002", "Dr. Ayesha Malik", "Female", 1, True),
    ("TCH-1003", "Prof. Imran Qureshi", "Male", 1, True),
    ("TCH-1004", "Dr. Hamza Siddiqui", "Male", 2, True),
    ("TCH-1005", "Ms. Sana Javed", "Female", 2, True),
    ("TCH-1006", "Mr. Bilal Ahmed", "Male", 3, True),
    ("TCH-1007", "Dr. Fatima Noor", "Female", 4, True),
    ("TCH-1008", "Mr. Usama Tariq", "Male", 5, True),
    ("TCH-1009", "Ms. Hira Aslam", "Female", 1, False),
]
next_user = 4
T = {"Sarah": 3}
users, profiles = [], []
for un, name, g, d, active in TEACHERS:
    uid = next_user; next_user += 1
    T[name.split()[1]] = uid
    users.append([un, TEACHER_PW, "TEACHER", None, d, active, real_dt("2024-08-15 09:00:00")])
    profiles.append([uid, name, g, None, "+92 300 %07d" % R.randint(1000000, 9999999), None, "Lahore", "Pakistan",
                     un.lower().replace("-", "") + "@campuscore.edu.pk"])

# ---------------- students (2 = BCSF22M512 Usman, the demo student, exists in class 1) ----------------
MALE = ["Ahmed", "Ali", "Hassan", "Hamza", "Bilal", "Usman", "Zain", "Fahad", "Saad", "Talha", "Hamid", "Omar",
        "Danish", "Shahzaib", "Arslan", "Rehan", "Waleed", "Huzaifa", "Abdullah", "Moiz", "Haris", "Taimoor"]
FEMALE = ["Ayesha", "Fatima", "Zainab", "Maryam", "Hira", "Sana", "Iqra", "Amna", "Laiba", "Areeba", "Mahnoor",
          "Eman", "Noor", "Hafsa", "Khadija", "Rida", "Alishba", "Anaya", "Mehwish", "Komal"]
LAST = ["Khan", "Malik", "Butt", "Chaudhry", "Sheikh", "Qureshi", "Siddiqui", "Rana", "Mirza", "Abbasi", "Javed",
        "Raza", "Iqbal", "Hussain", "Anwar", "Aslam", "Farooq", "Nawaz", "Tariq", "Ashraf"]
CITIES = ["Lahore", "Lahore", "Lahore", "Gujranwala", "Faisalabad", "Sialkot", "Sheikhupura", "Kasur"]
STUDENTS = {cid: [] for cid in CLASSES}
STUDENTS[1].append(2)
per_class = {1: 4, 2: 6, 3: 6, 4: 5, 5: 5, 6: 5, 7: 5, 8: 6, 9: 2}
used_names = set()
inactive_done = False
for cid, n in per_class.items():
    prefix = CLASSES[cid][4]
    num = R.randint(11, 40)
    for i in range(n):
        num += R.randint(1, 9)  # strictly increasing: roll numbers never repeat
        g = R.choice(["Male", "Female"])
        while True:
            first = R.choice(MALE if g == "Male" else FEMALE)
            name = first + " " + R.choice(LAST)
            if name not in used_names:
                used_names.add(name); break
        roll = "%s%03d" % (prefix, num)
        uid = next_user; next_user += 1
        active = True
        if cid == 2 and i == n - 1 and not inactive_done:
            active = False; inactive_done = True
        STUDENTS[cid].append(uid)
        users.append([roll, STUDENT_PW, "STUDENT", cid, None, active, real_dt("2024-08-20 10:00:00")])
        father = R.choice(MALE) + " " + name.split()[1]
        profiles.append([uid, name, g, father, "+92 3%02d %07d" % (R.randint(0, 49), R.randint(1000000, 9999999)),
                         None, R.choice(CITIES), "Pakistan", roll.lower() + "@campuscore.edu.pk"])
ins("users", ["username", "password", "role", "class_id", "department_id", "is_active", "created_at"], users)
ins("profiles", ["user_id", "full_name", "gender", "father_name", "phone", "address", "city", "country", "email"], profiles)
INACTIVE_STUDENT = STUDENTS[2][-1]
DEMO_STUDENT = 2

# ---------------- courses (1-5 exist) ----------------
sql("UPDATE courses SET department_id = 4 WHERE course_code = 'MATH-101';")
COURSES = {1: "CS-101", 2: "CS-102", 3: "CS-201", 4: "CS-301", 5: "MATH-101"}
NEW_COURSES = [
    ("CS-202", "Database Systems", 4, 1, "Relational model, SQL, normalisation and transactions"),
    ("CS-203", "Computer Networks", 3, 1, "Layered architecture, TCP/IP, routing and network security basics"),
    ("CS-302", "Operating Systems", 4, 1, "Processes, scheduling, memory management and file systems"),
    ("CS-401", "Artificial Intelligence", 3, 1, "Search, knowledge representation and machine learning foundations"),
    ("CS-402", "Information Security", 3, 1, "Cryptography, access control and secure system design"),
    ("SE-201", "Software Requirements Engineering", 3, 2, "Elicitation, specification and validation of requirements"),
    ("SE-301", "Software Design & Architecture", 3, 2, "Design principles, patterns and architectural styles"),
    ("SE-302", "Software Quality Assurance", 3, 2, "Testing techniques, reviews and quality metrics"),
    ("IT-201", "Web Technologies", 3, 3, "HTML, CSS, JavaScript and server-side web development"),
    ("IT-202", "Cloud Computing", 3, 3, "Virtualisation, cloud services and deployment models"),
    ("DS-101", "Introduction to Data Science", 3, 5, "Data wrangling, visualisation and exploratory analysis"),
    ("DS-201", "Machine Learning", 3, 5, "Supervised and unsupervised learning with evaluation"),
    ("MATH-201", "Linear Algebra", 3, 4, "Vectors, matrices, eigenvalues and linear transformations"),
    ("MATH-202", "Probability & Statistics", 3, 4, "Probability, distributions, estimation and hypothesis testing"),
    ("ENG-101", "Communication Skills", 2, None, "Academic writing, presentations and professional communication"),
]
ins("courses", ["course_code", "course_name", "credit_hours", "department_id", "description"], [list(c) for c in NEW_COURSES])
for i, c in enumerate(NEW_COURSES):
    COURSES[6 + i] = c[0]
CID = {code: cid for cid, code in COURSES.items()}

# Current-term offerings: course code -> (teacher key, classes)
CURRENT = {
    "CS-401": ("Ayesha", [1]), "CS-402": ("Imran", [1]), "SE-302": ("Sana", [1]),
    "CS-302": ("Sarah", [2, 4]), "CS-203": ("Imran", [2]), "MATH-202": ("Fatima", [2, 4]),
    "CS-201": ("Sarah", [3, 5]), "CS-202": ("Ayesha", [3, 6]), "MATH-201": ("Fatima", [3, 5, 7]),
    "SE-301": ("Hamza", [4]), "SE-201": ("Sana", [5]), "IT-201": ("Bilal", [6]), "IT-202": ("Bilal", [6]),
    "DS-201": ("Usama", [7]), "CS-101": ("Sarah", [8]), "CS-102": ("Imran", [8]), "MATH-101": ("Fatima", [8]),
    "ENG-101": ("Sana", [8]),
}
# Last term's offerings with published results (for transcripts)
LAST_TERM = {"CS-202": ("Ayesha", [1]), "CS-203": ("Imran", [1]), "CS-102": ("Imran", [3]), "MATH-101": ("Fatima", [3]),
             "SE-201": ("Sana", [4]), "IT-201": ("Bilal", [9]), "IT-202": ("Bilal", [9])}

course_classes = {(3, 1), (4, 1)}  # seed: OOP and DSA for class 1 (DSA has no teacher this term: the admin dashboard shows it)
for code, (_, cls) in list(CURRENT.items()) + list(LAST_TERM.items()):
    for c in cls:
        course_classes.add((CID[code], c))
sql("DELETE FROM course_classes;")
ins("course_classes", ["course_id", "class_id"], sorted(course_classes))

next_alloc = 3
ALLOC = {}  # (term, code) -> (allocation id, teacher id, classes)
alloc_rows = []
for term, table in ((4, LAST_TERM), (5, CURRENT)):
    for code, (tkey, cls) in table.items():
        ALLOC[(term, code)] = (next_alloc, T[tkey], cls)
        alloc_rows.append([CID[code], T[tkey], term])
        next_alloc += 1
ins("course_allocations", ["course_id", "teacher_id", "semester_id"], alloc_rows)


def number(cid, term):
    c = CLASSES[cid]
    if c[5] is None:
        return 8 - (4 - term) if term <= 4 else None
    n = c[5] - (5 - term)
    return n if n >= 1 else None


# ---------------- enrollments, grades ----------------
DEMO_RESULTS = {"CS-202": (21, 29, 31), "CS-203": (21, 27, 26)}  # the demo student's last-term results (A-, B)
next_enr = 3
enr_rows, grade_rows = [], []
CURRENT_ENR = {}  # allocation id -> [(enrollment id, student id)]
DEMO_ENR = {}     # code -> the demo student's current enrollment id
for (term, code), (aid, tid, cls) in ALLOC.items():
    for cid in cls:
        for sid in STUDENTS[cid]:
            eid = next_enr; next_enr += 1
            enr_rows.append([sid, aid, "ENROLLED", number(cid, term)])
            if term == 5:
                CURRENT_ENR.setdefault(aid, []).append((eid, sid))
                s = R.choice([None, None, round(R.randint(28, 48) / 2, 1)])  # a month in: some sessional marks
                published = False
                if sid == DEMO_STUDENT:
                    DEMO_ENR[code] = eid
                    if code == "CS-401":
                        s, published = 23.5, True  # shows "In progress" in the demo student's gradebook
                grade_rows.append([eid, s, None, None, None, published, tid if s is not None else None,
                                   real_dt("2026-09-28 16:00:00") if s is not None else None])
            else:
                if sid == DEMO_STUDENT and code in DEMO_RESULTS:
                    s, m, f = DEMO_RESULTS[code]
                else:
                    total_target = R.choice([92, 86, 81, 78, 74, 71, 68, 63, 59, 56, 52, 45])
                    s = min(25, round(total_target * 0.25 + R.choice([-1, 0, 1]), 0))
                    m = min(35, round(total_target * 0.35 + R.choice([-2, -1, 0, 1]), 0))
                    f = max(0, min(40, total_target - s - m))
                tot = s + m + f
                letter = next(l for lo, l in [(85, "A"), (80, "A-"), (75, "B+"), (70, "B"), (65, "B-"), (61, "C+"),
                                               (58, "C"), (55, "C-"), (50, "D"), (0, "F")] if tot >= lo)
                grade_rows.append([eid, s, m, f, letter, True, tid, real_dt("2026-06-25 11:00:00")])
ins("enrollments", ["student_id", "allocation_id", "status", "semester_number"], enr_rows)
ins("grades", ["enrollment_id", "sessional_marks", "mid_marks", "final_marks", "grade_letter", "is_published",
               "updated_by", "updated_at"], grade_rows)

# ---------------- lectures and attendance: twice a week from the first week until yesterday ----------------
TOPICS = ["Course overview and outline", "Fundamentals and terminology", "Core concepts", "Worked examples",
          "Case study", "Problem-solving session", "Quiz and discussion", "Advanced topics", "Lab session"]
lecture_rows, att = [], []
next_lecture = 6
low_students = set(R.sample([s for c in (2, 3, 6) for s in STUDENTS[c]], 4))
first_day = REAL_START[5] + timedelta(days=6)
last_day = TODAY - timedelta(days=1)
for (term, code), (aid, tid, cls) in ALLOC.items():
    if term != 5:
        continue
    days = (0, 2) if R.random() < 0.5 else (1, 3)  # Mon/Wed or Tue/Thu
    d, k = first_day, 0
    while d <= last_day and k < 30:
        if d.weekday() in days:
            lid = next_lecture; next_lecture += 1
            lecture_rows.append([aid, d.isoformat(), TOPICS[k % len(TOPICS)] if R.random() < 0.8 else None, tid,
                                 d.isoformat() + " 11:30:00"])
            for eid, sid in CURRENT_ENR[aid]:
                p_absent = 0.45 if sid in low_students else 0.08
                r = R.random()
                att.append([lid, eid, "Absent" if r < p_absent else ("Leave" if r < p_absent + 0.04 else "Present")])
            k += 1
        d += timedelta(days=1)
ins("lectures", ["allocation_id", "lecture_date", "topic", "created_by", "created_at"], lecture_rows)
ins("attendance", ["lecture_id", "enrollment_id", "status"], att)

# ---------------- announcements ----------------
classes_begin = REAL_START[5] + timedelta(days=6)
mids_from, mids_to = real_date("2026-10-26"), real_date("2026-10-30")
fee_deadline = real_date("2026-10-15")
ann = [
    ["%s classes begin" % NAME[5], "Welcome to %s! Classes begin on %s, %s. Please check your timetable and course list on the dashboard."
     % (NAME[5], classes_begin.strftime("%A"), nice(classes_begin)), None, None, "STUDENTS", 1, real_dt("2026-09-01 09:00:00")],
    ["Mid-term examination schedule", "Mid-term examinations will be held from %s to %s. The detailed date sheet will be shared by each department."
     % (nice(mids_from), nice(mids_to)), None, None, "ALL", 1, real_dt("2026-09-25 10:00:00")],
    ["Faculty meeting", "All faculty members are requested to attend the semester planning meeting on Friday at 2:00 PM in the main conference room.",
     None, None, "TEACHERS", 1, real_dt("2026-09-03 12:00:00")],
    ["Fee submission deadline extended", "The deadline for the %s semester fee has been extended to %s. Late fee will apply after this date."
     % (NAME[5], nice(fee_deadline)), None, None, "STUDENTS", 1, real_dt("2026-09-20 15:30:00")],
]
for code, title, text, when in [("CS-201", "Lab 1 posted", "Lab 1 on classes and objects is posted. Submit it before next Monday's lecture.", "2026-09-10 17:00:00"),
                                ("CS-202", "Project groups", "Form groups of three for the semester project and share member names by Friday.", "2026-09-14 12:30:00"),
                                ("CS-401", "Reading for week 3", "Read chapter 3 (informed search) before Wednesday's class.", "2026-09-18 09:15:00")]:
    aid, tid, _ = ALLOC[(5, code)]
    ann.append([title, text, CID[code], aid, "ALL", tid, real_dt(when)])
ins("announcements", ["title", "content", "course_id", "allocation_id", "audience", "created_by", "created_at"], ann)


# ---------------- messages ----------------
def stu(cid, i):
    return STUDENTS[cid][i]


msgs = [
    [T["Ayesha"], 1, "Please add CS-402 to the %s timetable for the lab on Thursdays." % class_name(1), real_dt("2026-09-08 10:12:00"), True],
    [stu(3, 1), 1, "Assalam o Alaikum, my fee challan shows the wrong amount. Could you please check?", real_dt("2026-09-22 14:05:00"), False],
    [stu(5, 0), 1, "I have uploaded my payment proof for the semester fee. Kindly verify it.", real_dt("2026-09-29 11:40:00"), False],
    [T["Hamza"], 1, "Requesting projector repair in room 204 before next week's lectures.", real_dt("2026-09-30 09:20:00"), False],
    [1, T["Sarah"], "Please submit the course files for CS-201 and CS-302 by %s." % nice(real_date("2026-10-10")), real_dt("2026-09-26 16:00:00"), True],
    [T["Ayesha"], DEMO_STUDENT, "Your project proposal for CS-401 looks good. Please add a short evaluation plan before Friday.", real_dt("2026-09-29 15:10:00"), False],
    [T["Imran"], DEMO_STUDENT, "Reminder: CS-402 lab 2 (symmetric encryption) is due on Monday.", real_dt("2026-09-30 09:05:00"), False],
]
orientation = real_date("2026-09-05")
msgs += [[1, s, "Welcome to CampusCore! Orientation for new students is on %s at 10:00 AM in the main auditorium." % nice(orientation),
          real_dt("2026-09-02 09:00:00"), True] for s in STUDENTS[8]]
ins("messages", ["sender_id", "receiver_id", "model_message", "timestamp", "is_read"], msgs)

# ---------------- support tickets ----------------
tickets = [
    [stu(2, 0), "I cannot see my attendance for CS-203 even though lectures have started.", "OPEN", None, real_dt("2026-09-24 13:10:00")],
    [stu(6, 2), "Request for an official transcript for a scholarship application.", "OPEN", None, real_dt("2026-09-27 10:45:00")],
    [T["Bilal"], "Two students in IT-201 are missing from my course list.", "OPEN", None, real_dt("2026-09-29 15:00:00")],
    [stu(7, 1), "My profile shows the wrong city. How can I update it?", "OPEN", None, real_dt("2026-09-30 08:30:00")],
    [stu(4, 2), "Unable to log in after changing my password.", "CLOSED", "Your password has been reset. Please log in and change it again from your profile.", real_dt("2026-09-12 09:00:00")],
    [T["Usama"], "Please create the %s class in the system." % class_name(7), "CLOSED",
     "The class %s has been created and its students assigned." % class_name(7), real_dt("2026-08-28 11:00:00")],
]
ins("support_tickets", ["user_id", "query_text", "status", "admin_reply", "created_at"], tickets)

# ---------------- add / drop requests ----------------
reqs = [
    [stu(1, 1), CID["ENG-101"], "ADD", "PENDING", real_dt("2026-09-21 10:00:00"), None, None],
    [stu(2, 2), CID["MATH-202"], "DROP", "PENDING", real_dt("2026-09-23 12:30:00"), None, None],
    [stu(3, 3), CID["MATH-201"], "WITHDRAW", "PENDING", real_dt("2026-09-28 09:45:00"), None, None],
    [stu(5, 1), CID["SE-302"], "ADD", "PENDING", real_dt("2026-09-29 16:20:00"), None, None],
    [stu(6, 0), CID["CS-203"], "ADD", "APPROVED", real_dt("2026-09-10 11:00:00"), real_dt("2026-09-11 10:00:00"), 1],
    [stu(4, 1), CID["CS-302"], "DROP", "REJECTED", real_dt("2026-09-12 14:00:00"), real_dt("2026-09-13 09:30:00"), 1],
    [stu(7, 0), CID["DS-101"], "ADD", "APPROVED", real_dt("2026-09-09 10:30:00"), real_dt("2026-09-09 15:00:00"), 1],
    [DEMO_STUDENT, CID["SE-302"], "DROP", "PENDING", real_dt("2026-09-29 10:15:00"), None, None],
    [DEMO_STUDENT, CID["CS-203"], "ADD", "APPROVED", real_dt("2026-02-03 11:00:00"), real_dt("2026-02-04 09:30:00"), 1],
]
sql("DELETE FROM course_requests;")
ins("course_requests", ["student_id", "course_id", "type", "status", "request_date", "processed_at", "processed_by"], reqs)

# ---------------- fee challans (current term) ----------------
FEE = {1: 45000, 2: 42000, 3: 40000, 5: 44000}
ISSUED, DUE = real_dt("2026-08-20 09:00:00"), real_dt("2026-09-15")
ch = []
for cid, sids in STUDENTS.items():
    if CLASSES[cid][5] is None:
        continue
    fee = FEE[CLASSES[cid][3]]
    for sid in sids:
        if sid == INACTIVE_STUDENT:
            continue
        r = R.random()
        base = [sid, 5, "Semester Fee - " + NAME[5], fee, ISSUED, DUE]
        if sid == DEMO_STUDENT or r < 0.55:
            ch.append(base + ["PAID", None, real_dt("2026-09-%02d 12:00:00" % R.randint(2, 14)), 1, None, None, "NONE", None, None, None])
        elif r < 0.70:
            ch.append(base + ["UNPAID", None, None, 1, "proofs/demo-proof.png", "HBL-TRX-%06d" % R.randint(100000, 999999),
                              "SUBMITTED", real_dt("2026-09-%02d 18:00:00" % R.randint(20, 30)), None, None])
        elif r < 0.78:
            ch.append(base + ["UNPAID", None, None, 1, "proofs/demo-proof.png", "MCB-%06d" % R.randint(100000, 999999),
                              "REJECTED", real_dt("2026-09-18 19:00:00"), "The receipt is not readable. Please upload a clearer copy.",
                              real_dt("2026-09-19 10:00:00")])
        else:
            ch.append(base + ["UNPAID", None, None, 1, None, None, "NONE", None, None, None])
ch.append([stu(3, 0), 5, "Library Late Return Fine", 1500, real_dt("2026-09-25 10:00:00"), real_dt("2026-10-15"), "UNPAID",
           "Book returned 12 days late", None, 1, None, None, "NONE", None, None, None])
ch.append([stu(6, 1), 5, "Hostel Fee - " + NAME[5], 30000, ISSUED, real_dt("2026-09-30"), "PAID", None, real_dt("2026-09-29 11:00:00"),
           1, "proofs/demo-proof.png", "UBL-554210", "ACCEPTED", real_dt("2026-09-28 20:00:00"), None, real_dt("2026-09-29 11:00:00")])
ch.append([DEMO_STUDENT, 5, "Hostel Fee - " + NAME[5], 30000, ISSUED, real_dt("2026-10-10"), "UNPAID", None, None, 1,
           "proofs/demo-proof.png", "HBL-TRX-482913", "SUBMITTED", real_dt("2026-09-30 19:20:00"), None, None])
ins("challans", ["student_id", "semester_id", "title", "amount", "upload_date", "due_date", "status", "remarks", "paid_at",
                 "created_by", "proof_path", "proof_reference", "proof_status", "proof_submitted_at", "proof_review_note",
                 "proof_reviewed_at"], ch)

sql("SET FOREIGN_KEY_CHECKS = 1;")

with open(args.out, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(header) + "\n\n" + schema + "\n\n-- ---------------- demo data ----------------\n" + "\n".join(out) + "\n")
print("today %s | terms: %s | users %d, offerings %d, enrollments %d, lectures %d, attendance %d, challans %d -> %s"
      % (TODAY, ", ".join("%s (%s..%s)" % (NAME[k], REAL_START[k], REAL_END[k]) for k in range(1, 6)),
         next_user - 1, next_alloc - 1, next_enr - 1, next_lecture - 1, len(att), len(ch), args.out))
