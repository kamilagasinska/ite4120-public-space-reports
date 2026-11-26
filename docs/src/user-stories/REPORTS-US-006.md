# REPORTS-US-006 · Solve the reports handed to my unit

> **REPORTS-US-006** · state **Proposed** · traces to [REPORTS.01](../specifications/REPORTS.01-public-space-reports.md)

**As** a worker of a municipal unit, for example the Environment Department of Tallinn
**I want** to see the reports the coordinator handed to my unit and mark what we did
**so that** the resident sees real progress from the people who fix the problem.

## Context

The coordinator checks a report and accepts it. The report is then handed to the unit as
an X-Road message. In this course the security server and the inbox of the unit are
imitated, and the unit signs in with a mock sign-in as its inbox address.

Out of scope: a real document system of a municipality, real accounts of unit workers.

## Acceptance criteria

- The unit sees only the reports handed to it, new ones marked.
- The unit can start the work, write news for the resident and add photos of the result.
- Only the unit marks a report as resolved, the coordinator cannot.
- The unit can return a report that is not theirs, with a reason, and then no longer sees it.
- The resident sees the unit and the day of the handover, not the inbox address.
