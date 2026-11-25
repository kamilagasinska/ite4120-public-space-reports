# REPORTS-US-005 · Send a report to the right place

> **REPORTS-US-005** · state **Proposed** · traces to [REPORTS.01](../specifications/REPORTS.01-public-space-reports.md)

**As** a municipal maintenance coordinator
**I want** to forward a report that is not ours with a reason
**so that** the resident knows who is responsible instead of only reading that it was refused.

## Context

The recipient is proposed from the category, the municipality and the type of the nearest road. The public road map has no owner field, so the proposal can be wrong. In this course nothing is delivered to a real authority.

Out of scope: automatic delivery to other systems, contacts of municipal departments.

## Acceptance criteria

- The resident sees the recipient before sending.
- Road damage on a state road is proposed to the Transport Administration.
- The coordinator can forward an open report with a reason.
- The resident sees where the report went and why, not who forwarded it.
- A closed report cannot be forwarded.
