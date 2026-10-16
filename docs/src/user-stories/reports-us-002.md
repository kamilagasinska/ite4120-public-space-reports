# REPORTS-US-002 · Review and prioritise incoming reports

> **REPORTS-US-002** · state **Proposed**

**As** a municipal maintenance coordinator
**I want** to see all incoming reports in one place, with their category, location, status and how long they have been open
**so that** I can decide which problems to send a crew to first, and spot the ones that have been waiting too long.

## Context

Today reports reach the municipality scattered across phone calls and emails. The coordinator cannot see how many problems are open, where they are, or which are overdue, so work is planned by whoever called last rather than by urgency.

This mirrors the municipality's internal handling of resident notices: each report is reviewed, its status is tracked until the problem is resolved or the report is rejected, and every decision is traceable.

Out of scope: assigning work to individual crews, scheduling, cost tracking, and reporting statistics beyond the list of reports.

## Acceptance criteria

- The coordinator can list all reports and filter them by category and by status.
- Each report in the list shows its reference number, category, location, status and the number of days since it was submitted.
- The coordinator can change a report's status from Received to In progress, from In progress to Resolved, or from Received to Rejected.
- Rejecting a report requires a reason; a rejection without a reason is refused.
- Any other status change (for example Resolved back to Received) is refused.
- Every status change is recorded with the time and the user who made it.
