# Normalization and history

`users(id,email,role,...)` has one account per row; email is unique. `skills(id,name)` catalogs each skill once. `student_skills(user_id,skill_id,level)` records a student-skill association; `opportunity_skills(opportunity_id,skill_id,min_level)` represents role requirements. The two bridge tables normalize many-to-many relationships and prevent comma-separated multivalued data. `applications` is one student-role submission, uniquely constrained. Every status change is a separate `application_events` row. Notifications and audit events have independent primary keys.

`applications.profile_snapshot` and `score_at_apply` are intentionally retained **submission-time historical facts**. They should not update when the current student profile changes. This temporal-history trade-off is explicit and separate from current normalized attributes.

See [`DATABASE.md`](DATABASE.md), [`ER_DIAGRAM.md`](ER_DIAGRAM.md), [`../src/main/resources/schema.sql`](../src/main/resources/schema.sql).