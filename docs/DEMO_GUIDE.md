# Demo walkthrough (8–12 minutes)

Use a fresh local database and synthetic sample data only. Do not enter real student information.

## Student

1. Sign in as `student@skillbridge.local`; show a student-scoped overview and role suggestions sorted by prerequisite coverage. State that coverage is not hiring probability.
2. Open My profile; update fictional department, CGPA, graduating batch, bio and proficiency. Show skill level definitions.
3. Find opportunities; search “Backend”. Open Meridian Systems internship. Explain deadline, compensation, academic gate and Java/SQL/Git requirements. Save, return via Saved opportunities and optionally remove.
4. Apply with a fictional note. Re-submit to show server duplicate protection. Inspect frozen skill score/snapshot, initial event and notification.
5. As office, shortlist with a respectful note; return as student and show the event. Schedule a future interview with venue; return to student to show details. Advance to offer; as student accept or decline. Explain that invalid skipped stages and stale tabs are rejected.
6. Change profile after applying; old snapshot/score remain fixed. Withdraw at an early stage; same role still cannot be applied to twice.

## Placement office

1. Show pipeline counts, directory and skill catalogue.
2. Create a fictitious role with future deadline, CGPA and batch; choose required skills. Edit/close it. Explain server-side checks do not rely on hiding buttons.
3. Inspect an application timeline and enter a factual decision note. Avoid sensitive information.
4. Export `applications.csv`: office has all rows, student gets their own only.
5. Probability lab: declare scope and sample size; distinguish population variance and event frequency from historical offer rates; discuss selection bias and uncertain denominator. Set a hypothetical p in simulator and say it is not estimated from a skill score.

## Viva prompts

- Why do student skills and role requirements each require a bridge relation?
- What do FK, CHECK, composite PK and unique student-role key protect?
- Why preserve a submission-time snapshot separate from mutable profile?
- Which writes must roll back if notification insert fails?
- Why is application uniqueness checked in both Java and SQL?
- When should variance divide by N or N−1?
- Why does skill coverage not imply calibrated offer probability?
- What does a fixed simulation seed establish—and what does it not?

## Hygiene

Change demo passwords if any fictional profile data is entered. To reset, stop service, back up first, and remove the generated demo DB only if losing its records is acceptable. Never expose demo credentials, bind publicly, or enter real student records.