# Demo tools

Tools for the public demo and the README screenshots. All data they create is fictional.

| File | Purpose |
|---|---|
| `demo_data.py` | Writes one SQL script that recreates the demo database: `DROP DATABASE`, `database_schema.sql`, then realistic demo data (5 departments, 9 classes, 55 users, 20 courses, attendance, grades, fees, requests, messages). All dates are relative to the day it runs, so the demo always looks current. Used nightly by `.github/workflows/reset-demo.yml`. |
| `demo-proof.png` | Placeholder payment-proof image referenced by the demo challans (copied into the upload folder when the container starts). |
| `shoot.mjs` | Full-page screenshots with headless Chrome over the DevTools protocol (Node 22+, no packages). |
| `*-pages.json` | The pages captured for each portal's README screenshots. |
| `logo-card.mjs` | Renders the logo on a white rounded card for the README. |

## Usage

```bash
# Demo database script (dates relative to today, or pass --today YYYY-MM-DD)
python tools/demo/demo_data.py --database cms_ead --out demo.sql
mysql -u root -p < demo.sql

# Screenshots (app running at the base URL in the JSON file, demo data loaded)
node tools/demo/shoot.mjs tools/demo/teacher-pages.json
```

Demo logins: `ADMIN` / `ADMIN123`, `TEACHER1` / `Teacher123`, `BCSF22M512` / `Usman123`.
