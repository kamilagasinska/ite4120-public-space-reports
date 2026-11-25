# REPORTS-US-001 · Report a problem in public space

> **REPORTS-US-001** · state **Proposed** · traces to [REPORTS.01](../specifications/REPORTS.01-public-space-reports.md)

**As** a resident of the municipality
**I want** to tell the local government about a problem I see in public space, such as a pothole in the road or rubbish left in a park, with its address, an optional map point and a short description
**so that** the problem gets fixed, and I can see that my report was received and what is happening with it.

## Context

Today residents who notice a problem in public space do not know whom to contact, so most problems are never reported. Those that are reported arrive by phone or email without a precise location, and the resident never hears back. The municipality therefore does not know where the problems are, and residents cannot tell whether reporting makes any difference.

This mirrors the municipal public space maintenance process: a resident's notice is registered, reviewed by the municipality and resolved by a maintenance crew.

Reporting is possible without giving a name. Contact details are optional and used only to follow up on that report. A resident may report as a guest, with a device identifier, or signed with Smart-ID, and gets a higher limit for being identifiable. No personal identification code (isikukood) is stored.

Out of scope: photos and file uploads, notifications by email or SMS, problem types other than road damage and litter, and anything outside Estonia.

## Acceptance criteria

- A resident can submit a report with a category (road damage or litter), an address, an optional map point and a description.
- A report without a category or an address is refused, with a message saying which field is missing.
- A description shorter than 10 characters is refused.
- Each accepted report gets a unique reference number and the status "Received".
- Submitting the same report twice by pressing the button again does not create a duplicate report.
- A resident can look up the current status of a report by its reference number.
- Looking up a reference number that does not exist returns a clear "not found" message.
- A resident is told before the first report what is stored about them and why.
- A guest can send one report a day. A device and a signed resident can send more, and the limit grows as their reports are resolved.
- A resident over the limit is told when to try again or to sign.
- The place can be taken from the device's location with the resident's permission, and corrected.
- A resident can see the reason when a report was rejected.
