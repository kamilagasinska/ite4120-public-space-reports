# REPORTS-US-003 · See what others have reported

> **REPORTS-US-003** · state **Proposed** · traces to [REPORTS.01](../specifications/REPORTS.01-public-space-reports.md)

**As** a resident of any municipality in Estonia
**I want** to see on a map what other people have reported near me and say that I see it too
**so that** I do not report the same problem again and I can see whether the municipality reacts.

## Context

Today a resident does not know whether somebody has already reported a problem. The map shows reports without any personal data. The text and the photos of a stranger can name a person, so they appear only after the coordinator has checked and published the report.

Out of scope: comments and likes, a public list of senders, reports about people (public order).

## Acceptance criteria

- A resident sees points with category, status, municipality and date.
- The description, the address and the photos are shown only after publication.
- The reference number, the contact and the sender are never shown.
- A rejected report leaves the map after 3 days, a resolved one after 30 days.
- Another resident can say "I see it too", counted once per device.
- Before sending a new report the resident is shown similar reports nearby.
