# REPORTS-US-001 · Report a problem in public space

> **REPORTS-US-001** · state **Proposed**

**As** a resident of the municipality
**I want** to tell the local government about a problem I see in public space, such as a pothole in the road or rubbish left in a park, with its location and a short description
**so that** the problem gets fixed, and I can see that my report was received and what is happening with it.

## Context

Today residents who notice a problem in public space do not know whom to contact, so most problems are never reported. Those that are reported arrive by phone or email without a precise location, and the resident never hears back. The municipality therefore does not know where the problems are, and residents cannot tell whether reporting makes any difference.

This mirrors the municipal public space maintenance process: a resident's notice is registered, reviewed by the municipality and resolved by a maintenance crew.

Reporting is possible without giving a name. Contact details are optional and used only to follow up on that report. No personal identification code (isikukood) is collected.

Out of scope: photos and file uploads, notifications by email or SMS, problem types other than road damage and litter, and more than one municipality.

## Acceptance criteria

- A resident can submit a report with a category (road damage or litter), a location and a description.
- A report without a category or a location is refused, with a message saying which field is missing.
- A description shorter than 10 characters is refused.
- Each accepted report gets a unique reference number and the status "Received".
- Submitting the same report twice by pressing the button again does not create a duplicate report.
- A resident can look up the current status of a report by its reference number.
- Looking up a reference number that does not exist returns a clear "not found" message.
