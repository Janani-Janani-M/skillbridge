# Entity relationship diagram

```mermaid
erDiagram
 USERS ||--o{ STUDENT_SKILLS : records
 SKILLS ||--o{ STUDENT_SKILLS : classifies
 OPPORTUNITIES ||--o{ OPPORTUNITY_SKILLS : requires
 SKILLS ||--o{ OPPORTUNITY_SKILLS : classifies
 USERS ||--o{ APPLICATIONS : submits
 OPPORTUNITIES ||--o{ APPLICATIONS : receives
 APPLICATIONS ||--o{ APPLICATION_EVENTS : records
 USERS ||--o{ APPLICATION_EVENTS : acts
 USERS ||--o{ SAVED_OPPORTUNITIES : saves
 OPPORTUNITIES ||--o{ SAVED_OPPORTUNITIES : saved_as
 USERS ||--o{ NOTIFICATIONS : receives
 USERS ||--o{ AUDIT_LOG : performs
 USERS { bigint id PK varchar full_name varchar email UK varchar password_hash varchar role decimal cgpa int graduation_year }
 SKILLS { bigint id PK varchar name UK }
 STUDENT_SKILLS { bigint user_id PK,FK bigint skill_id PK,FK int level }
 OPPORTUNITIES { bigint id PK varchar title varchar company varchar kind decimal min_cgpa int graduation_year date deadline varchar status }
 OPPORTUNITY_SKILLS { bigint opportunity_id PK,FK bigint skill_id PK,FK int min_level }
 APPLICATIONS { bigint id PK bigint user_id FK bigint opportunity_id FK varchar status double score_at_apply varchar profile_snapshot int version }
 APPLICATION_EVENTS { bigint id PK bigint application_id FK bigint actor_id FK varchar status varchar note timestamp created_at }
 SAVED_OPPORTUNITIES { bigint user_id PK,FK bigint opportunity_id PK,FK }
 NOTIFICATIONS { bigint id PK bigint user_id FK varchar message boolean is_read timestamp created_at }
 AUDIT_LOG { bigint id PK bigint actor_id FK varchar action bigint entity_id timestamp created_at }
```

`APPLICATIONS.user_id` references the applicant; Java limits this workflow to STUDENT. `APPLICATION_EVENTS.actor_id` identifies the student or office actor. Exact columns/constraints: [`../src/main/resources/schema.sql`](../src/main/resources/schema.sql).