# REPORTS-US-004 · Choose how to identify myself

> **REPORTS-US-004** · state **Proposed** · traces to [REPORTS.01](../specifications/REPORTS.01-public-space-reports.md)

**As** a resident who reports a problem
**I want** to choose between reporting as a guest, with a device number or signed with Smart-ID
**so that** I decide how much the municipality knows about me, and honest reporters are not stopped by rules against spam.

## Context

A report without any identity is easy to misuse. A report that demands full identity keeps people away. Three ways with different limits balance both. The limits grow with trust, which comes from confirmed and solved reports.

Out of scope: a production Smart-ID contract, rewards from the municipality, sign-in of the coordinator.

## Acceptance criteria

- Before the first report the resident is told what is stored and why.
- A guest can send one report a day.
- A device and a signed resident can send more, and the limit grows with points.
- A resident over the limit is told when to try again.
- A signed report is stored only when the signature has been verified.
- No personal code is stored.
- A false report sets the points back to zero.
