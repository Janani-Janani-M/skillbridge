# Database design

**Engine:** embedded file-backed H2 2.3.232 through JDBC. DDL: [`../src/main/resources/schema.sql`](../src/main/resources/schema.sql). It uses generated keys, foreign keys, `CHECK`, unique constraints, composite junction keys, indexes, dates/timestamps. `Database.java` seeds the demo only when the users table is empty.

## Relations

| Relation | Row grain and key | Integrity rules |
|---|---|---|
| `users` | Student or office account; `id` | Email unique; valid role; CGPA 0–10; year range |
| `skills` | One catalogue term; `id` | Name unique |
| `student_skills` | One user-skill pair; composite key | Both FKs; level 1–3 |
| `opportunities` | One role; `id` | type/status checks; nonnegative pay; CGPA/year ranges |
| `opportunity_skills` | One role-skill requirement; composite key | Both FKs; minimum level 1–3 |
| `applications` | One student-role submission; `id` | Unique student-role pair, valid status, score range, version |
| `application_events` | One transition note; `id` | application and actor FKs, timestamp |
| `saved_opportunities` | One saved pair; composite key | Both FKs |
| `notifications` | One recipient message; `id` | user FK and read flag |
| `audit_log` | One recorded action; `id` | actor FK and timestamp |

Two bridge relations normalize many-to-many student-skill and role-skill facts instead of storing comma-separated lists. Application events are separate from the current application state. Notifications and audit have independent keys. A submission-time profile snapshot is intentionally retained as a **historical** fact; editing the current profile must not rewrite old recruiter context. See [`ER_DIAGRAM.md`](ER_DIAGRAM.md) and [`NORMALIZATION.md`](NORMALIZATION.md).

## Transactional invariants

- Prepared statements bind user inputs. Service validation is reinforced by DB foreign keys, checks, unique constraints and composite keys.
- Applying locks student and opportunity, verifies status/deadline/CGPA/cohort, rejects a duplicate, captures profile and coverage, then inserts application, initial event, notification and audit atomically. Unique `(user_id,opportunity_id)` is final protection against concurrent duplicate submission.
- State transitions update a versioned row and append event/notification/audit in one transaction. `WHERE id=? AND version=?` rejects stale requests. Invalid state transitions do not write.
- Any exception rolls back the entire transaction. H2 lock timeout is part of the default JDBC URL.

## Useful SQL

```sql
SELECT status, COUNT(*) AS applications FROM applications GROUP BY status;

SELECT a.id,u.full_name,u.department,u.cgpa,a.status,a.score_at_apply
FROM applications a JOIN users u ON u.id=a.user_id
WHERE a.opportunity_id=? ORDER BY a.created_at;

SELECT s.name,ss.level FROM student_skills ss
JOIN skills s ON s.id=ss.skill_id WHERE ss.user_id=? ORDER BY s.name;

SELECT s.name,os.min_level FROM opportunity_skills os
JOIN skills s ON s.id=os.skill_id WHERE os.opportunity_id=? ORDER BY s.name;
```

Indexes cover application status/role, open role deadlines, notification recipient/read flag and application event order. Unique keys support account and application lookups.

## Reset / migration warning

Stop the server and back up `data/` first. Removing the generated H2 demo database file while the server is stopped permanently deletes local records; startup recreates the sample dataset. This clean demonstration's `CREATE TABLE IF NOT EXISTS` is not a versioned migration system.