# REPORTS.01 · Public space reports

> **REPORTS.01** · state **Agreed** (revision 2, identity, limits and geolocation) · traces from [REPORTS-US-001](../user-stories/REPORTS-US-001.md)
> and [REPORTS-US-002](../user-stories/REPORTS-US-002.md)

## Description

A register of problems in public space reported by residents to the local
government, limited to road damage and litter. A resident submits a report with a
category, an address, an optional map point and a description, receives a
reference number and can look up the report's status with it. Each report is
automatically assigned to the municipal unit responsible for its category. A
municipal coordinator lists and filters all reports, sees how many days each has
been open, and moves each report through its statuses. Every status change is
recorded with who made it and when.

The address is checked live against the address register through the
address-register adapter (mock in this course, X-Road-shaped in production).
Reporting does not require a name. Contact details are optional and used only to
follow up on that report. A resident chooses how to report, as a guest, with a device
identifier or signed with Smart-ID. The section Identity and limits defines the three
ways. No personal identification code is stored.

The municipality is found from the map point or the address, anywhere in Estonia, and
the report goes to the unit responsible for the category in that municipality.

The scope is grounded in public procurement practice. The Šiauliai district
municipality (Lithuania) tender for a littering-report app asks for a map point
with coordinates and a comment, and automatic routing to the responsible body. A
US city's 311 citizen request system tender asks for status tracking, audit
logging, role-based access and WCAG 2.2 AA. FixMyStreet Pro (UK G-Cloud) offers
categories, map location, status updates and the Open311 API. This component
implements their common core.

Excluded are photos, video and audio (in TEHIK's rules files belong in object
storage, beyond this course), a public map of reports, nearby waste collection
points, notifications by email or SMS, assigning work to individual crews,
duplicate detection, statistics dashboards, problem types other than road damage
and litter, a production Smart-ID contract (the demo environment only), and real
sign-in of coordinators (mock auth only).

## Roles

| Role | Who | May do |
| --- | --- | --- |
| Resident | any signed-in mock user | submit a report, look up a report by reference number, read the categories |
| Coordinator | mock users listed in the configuration key `reports.coordinators` | everything a resident may, and also list and filter reports, open the report page with the contact, change status, see history |

A resident calling a coordinator endpoint gets 403. The status lookup by reference
number never returns the contact to a resident.

## Identity and limits

| Way | What is stored about the reporter | Limit at the start |
| --- | --- | --- |
| Guest | a keyed hash of the IP address | 1 report a day from one IP address |
| Device | a keyed hash of a random identifier kept in the browser | trust level 0 |
| Signed | a keyed hash of the Smart-ID person | trust level 0 of the signed scale |

| Trust level | Reached with | Device limit | Signed limit |
| --- | --- | --- | --- |
| 0 | start | 1 in 10 minutes, 5 a day | 1 a minute, 50 a day |
| 1 | 3 resolved reports | 1 in 5 minutes, 10 a day | 1 a minute, 100 a day |
| 2 | 10 resolved reports | 1 in 2 minutes, 20 a day | 1 a minute, 200 a day |

The level is calculated from the reporter's resolved reports. A normal rejection does
not change it. When a coordinator marks a rejected report as false, the count starts
again from zero at that moment. Guest and device reports together are also limited to
20 a day from one IP address, so clearing the browser does not remove every limit.
A day is the last 24 hours. A refused report answers 429 with Retry-After and tells the
resident to wait or to sign. The limit is checked and the report stored under one
database lock per reporter and IP address, so parallel requests cannot pass it.

The first screen explains in plain words what is stored and why. A resident who does
not want the device identifier reports as a guest or signs. Nobody is forced to agree.
The device identifier is not a phone number or a hardware number, which a browser does
not reveal. It is pseudonymous personal data. Device records are deleted after 6 months
without a report. Signed records are kept while the resident uses the service, because
they hold the trust history, and an administrator deletes them on request. IP hashes are cleared after
24 hours. The reports themselves stay.

**Smart-ID.** The resident signs the text of the report. The application sends the
SHA-512 digest to the Smart-ID demo environment, shows the verification code, and
stores the report only when the signature is complete and verified with the public key
of the returned certificate, and the certificate names the demo authority as issuer
and the chosen test person as subject. The answer comes over TLS from the provider.
Checking the certificate chain and revocation is left for production.
The certificate holds a personal code, so it is discarded after the check and only
the digest, the time and the keyed hash are kept. The demo offers a fixed list of
test persons. Nobody types a real personal code or PIN into this application.

**Failure policy** for Smart-ID is **fail closed**. If it is unreachable or the person
refuses, nothing is stored as signed. The form keeps its content and offers to send as
device or guest instead.

## Data model

| Table | Field | Type | Rules |
| --- | --- | --- | --- |
| `reports.category` | `code` PK | text | reference list ROAD_DAMAGE, LITTER |
| | `name` | text | display name |
| | `responsible_unit` | text | required, ≤100; ROAD_DAMAGE → Roads Department, LITTER → Environment Department |
| `reports.report` | `id` PK | bigint | from `core.seq_id` |
| | `reference_number` | text | required, **unique among active rows**, generated by the system, never typed by the user; `R-<year>-` plus 6 random characters A-Z and 0-9, see Decisions |
| | `category_code` | text | required, FK → category |
| | `address` | text | required, ≤255 |
| | `address_code` | text | optional, ≤30; the address register's identifier when the address was found |
| | `address_verified` | boolean | required, default false; true only when the register confirms the address |
| | `latitude` | numeric(9,6) | optional, -90 to 90, together with longitude or not at all |
| | `longitude` | numeric(9,6) | optional, -180 to 180, together with latitude or not at all |
| | `description` | text | required, 10 to 1000 characters |
| | `reporter_contact` | text | optional, ≤255; email or phone, only for follow-up |
| | `municipality_code` | text | optional, ≤10; the register's code of the municipality |
| | `municipality_name` | text | optional, ≤100; empty means not determined |
| | `channel` | text | required; GUEST, DEVICE or SIGNED |
| | `reporter_id` | bigint | FK → reporter; empty for a guest |
| | `ip_hash` | text | optional; cleared after 24 hours |
| | `signed_digest` | text | required when the channel is SIGNED |
| | `status` | text | required, default RECEIVED; one of RECEIVED, IN_PROGRESS, RESOLVED, REJECTED |
| | `submission_key` | text | optional, ≤64, unique among active rows; sent by the form so a repeated submit cannot create a duplicate |
| | `sys_*` | platform | trigger-managed; `sys_created_at` and `sys_created_by` are the submission time and user |
| `reports.status_change` | `id` PK | bigint | from `core.seq_id` |
| | `report_id` | bigint | required, FK → report |
| | `from_status` | text | required |
| | `to_status` | text | required |
| | `reason` | text | ≤500; required and non-blank when `to_status` is REJECTED; shown to the resident |
| | `false_report` | boolean | required, default false; only with REJECTED |
| | `sys_*` | platform | `sys_created_at` and `sys_created_by` record when and by whom the status changed |
| `reports.reporter` | `id` PK | bigint | from `core.seq_id` |
| | `kind` | text | required; DEVICE or SIGNED |
| | `key_hash` | text | required, **unique among active rows** per kind |
| | `last_report_at` | timestamptz | required |
| | `sys_*` | platform | trigger-managed |
| `reports.signing` | `id` PK | bigint | from `core.seq_id` |
| | `token` | text | required, unique; random, used by the form to ask for the result |
| | `session_id` | text | required; the Smart-ID session |
| | `payload` | jsonb | required until finished; the report being signed, then cleared |
| | `state` | text | required; PENDING, SIGNED, REFUSED, EXPIRED, FAILED |
| | `report_id` | bigint | FK → report; set when SIGNED |
| | `sys_*` | platform | trigger-managed |

Allowed status changes are RECEIVED → IN_PROGRESS, IN_PROGRESS → RESOLVED and
RECEIVED → REJECTED. Any other change is refused.

`status_change` records **post-creation transitions only**. Creating a report
writes no history row, because the creation time and user live on the report's
`sys_*` columns. Days open is calculated from the creation time and not stored.
The responsible unit is read from the category and not stored on the report.

The database repeats the service validation with CHECK constraints on

- the coordinate pair, `(latitude is null and longitude is null) or (latitude is not null and longitude is not null)`
- the latitude and longitude ranges
- the status values
- the description length
- the rejection reason, `to_status <> 'REJECTED' or (reason is not null and char_length(btrim(reason)) > 0)`

## API

| Method + path | Does | Failure answers |
| --- | --- | --- |
| `GET /api/reports` | coordinator; paged list (`categoryCode`, `status`, `municipalityCode`, `channel`, `limit`, `offset`, `sort`), each row with days open and responsible unit. **This is the reporting operation.** | 400 unknown category, status or sort · 403 |
| `GET /api/reports/{referenceNumber}` | one report with status, category, responsible unit, address, verification, map point and submission time; description, contact and `sys*` metadata only for a coordinator | 404 |
| `POST /api/reports` | submit; answers **201 + Location** with the reference number; the same `submissionKey` again answers 200 with the existing report; the header `X-Device-Id` makes it a device report, without it a guest report | 400 validation · 429 limit |
| `POST /api/reports/signings` | start signing a report with a demo test person; answers 201 with a token and the verification code | 400 · 429 limit · 502 Smart-ID unreachable |
| `GET /api/reports/signings/{token}` | state of the signing, with the reference number when signed | 404 |
| `GET /api/reports/place` | municipality and address of a map point (`latitude`, `longitude`) | 400 · 502 |
| `POST /api/reports/{referenceNumber}/status` | coordinator; change status (`toStatus`, `reason`, `falseReport`) | 400 missing reason · 403 · 404 · 409 change not allowed |
| `GET /api/reports/{referenceNumber}/history` | coordinator; status changes, oldest first | 403 · 404 |
| `GET /api/reports/categories` | reference list with responsible units | none |

Every error is `application/problem+json` via `ApiErrorHandler`. All endpoints
require `Authorization: Bearer <user>` (mock auth) and answer 401 without it. In mock auth
a resident is any user name, so it says nothing about who reports. The
API contract is live at `/swagger-ui.html`. Field names follow Open311 GeoReport
(category, address, latitude, longitude, description, status).

**Failure policy** for the address lookup is **fail open**. If the address register
is unreachable or errors, the report is still accepted with
`address_verified = false` and the coordinator sees "address not verified". An
address the register answers "not found" for is also accepted unverified. A
resident must never be blocked from reporting a hazard because another system is
down, and the coordinator can check the address by hand.

The address is **verified** when the register returns exactly one address for it.
Several matches are ambiguous and leave the report unverified. The lookup waits at
most 3 seconds. The transport is configuration (`reports.address-register.mode`,
`http` or `xroad`). Locally the application imitates the register, and an address
containing "register-down" makes the imitation answer 503 to show the policy live.

The list without `sort` shows the longest waiting report first. Sort keys are
`referenceNumber`, `categoryCode`, `address`, `status`, `submittedAt` and `daysOpen`.

## UI

| Screen | Element | Component | Rules |
| --- | --- | --- | --- |
| Start `/reports/start` | explanation and choice | `AppCard` + `Radio.Group` + `AppButtonPrimary` | what is stored and why; guest, device or Smart-ID, with the limit of each |
| Smart-ID step | verification code | `AppCard` + `Select` of test persons | labelled as demo; shows the code, waiting, refused and unreachable states |
| Report form `/reports/new` | category | `Select` | required, options from `/categories` |
| | address | `Input` | required, ≤255 |
| | place | `Button` "Use my location" + `InputNumber` × 2 | the browser asks for permission; fills the point, address and municipality; every value can be corrected, because the problem may be elsewhere; without permission the address is typed |
| | description | `Input.TextArea` | required, 10-1000, character counter |
| | contact | `Input` | optional, ≤255, hint "only used to follow up on this report" |
| | submit | `AppButtonPrimary` | disabled while sending; generates `submissionKey` once per form; on success shows the reference number; 400 problem text shown in a notification |
| Status lookup `/reports/lookup` | reference number | `Input` | required |
| | search | `AppButtonPrimary` | shows status, category, address and submitted date; 404 → "No report with this reference number" |
| Report list `/reports` | table | `ResourceList` | coordinator; columns reference number (link), category tag, responsible unit, address, status tag, days open, submitted at; filters category and status; "address not verified" tag |
| Report page `/reports/{referenceNumber}` | view | `ResourceForm` + `useDataController` | coordinator; read-only fields incl. contact and verification |
| | status change | `Select` + `Input.TextArea` + `AppButtonPrimary` | `Select` offers only allowed next statuses; reason required for Rejected; 400/409 text in a notification |
| | history | `AppCard` | from, to, reason, when, by whom |
| | metadata | `ResourceForm.sidebar` | created and modified at and by, version |

## Business tests

1. **A report needs a category and an address.** Given a report without a category,
   when it is submitted, then it is refused (400) naming the category. The same
   holds for a missing address.
2. **A description must be at least 10 characters.** Given a report with the
   description "hole", when it is submitted, then it is refused (400).
3. **A new report is Received and has a reference number.** Given a valid report,
   when it is submitted, then the answer is 201 + Location with a unique reference
   number and status RECEIVED.
4. **A repeated submit does not create a duplicate.** Given a report submitted with
   submission key K, when the same submission is sent again, then the existing
   report is returned (200) and there is still one report.
5. **A map point is both coordinates or none.** Given a latitude without a
   longitude, when the report is submitted, then it is refused (400). Latitude 95 is
   also refused (400).
6. **Each report goes to the responsible unit.** Given a LITTER report, when it is
   looked up, then it shows Environment Department.
7. **A resident can look up a report by its reference number.** Given a report R,
   when R is looked up by a resident, then its current status is returned without
   the contact. An unknown reference number answers 404.
8. **Only allowed status changes are accepted.** Given a RESOLVED report, when it is
   changed to RECEIVED, then the change conflicts (409) and the status stays RESOLVED.
9. **Rejecting needs a reason.** Given a RECEIVED report, when it is rejected without
   a reason, then the change is refused (400). With a reason it becomes REJECTED.
10. **Every status change is recorded.** Given a report moved to IN_PROGRESS and then
    to RESOLVED, when its history is read, then both changes are shown in order with
    the time and the coordinator's user name.
11. **A report is accepted even when the address register is down.** Given an
    unreachable register, when a valid report is submitted, then the answer is 201
    and `address_verified` is false.
12. **The list can be filtered.** Given one ROAD_DAMAGE and one LITTER report, when
    the list is filtered by LITTER, then only the LITTER report is returned.
13. **Only a coordinator can change a status.** Given a resident, when they change the
    status of a report, then the answer is 403 and the status is unchanged.
14. **A guest may send one report a day.** Given a guest report from an IP address, when
    a second one comes from the same address within 24 hours, then the answer is 429
    with Retry-After and nothing is stored.
15. **A device is limited by its trust level.** Given a new device with one report, when
    it sends another within 10 minutes, then the answer is 429. After 3 resolved reports
    the limit is 1 in 5 minutes.
16. **A false report resets trust.** Given a device at level 1, when a coordinator rejects
    one of its reports as false, then it is at level 0. A normal rejection changes nothing.
17. **Changing the device does not remove every limit.** Given 20 unsigned reports from
    one IP address within 24 hours, when a new device sends one more, then the answer is 429.
18. **A signed report is stored only after a verified signature.** Given a signing that
    the person refused, or whose signature does not match the report, when its state is
    read, then no report exists. A verified one creates a SIGNED report.
19. **The municipality comes from the place.** Given a map point in Saue vald, when the
    report is submitted, then it shows Saue vald and the responsible unit. When the
    register is down, the report is accepted without a municipality.
20. **A resident sees why a report was rejected.** Given a rejected report, when a
    resident looks it up, then the reason is shown and the user name is not.

## Decisions

1. The reference number is `R-<year of submission>-` plus 6 random alphanumeric
   characters. It is not sequential, so nobody can enumerate other people's reports.
2. The same `submissionKey` sent again with different content returns the existing
   report unchanged (200). This is a deliberate choice.
3. Coordinators are a configured list of user names. Residents get 403 on
   coordinator endpoints.
4. Mock auth requires a signed-in user, so `sys_created_by` stores the mock user name
   even though reporting needs no name. This is a course limitation, and the user
   name is never shown to residents.
5. Smart-ID is used in its public demo environment. It was called on 25.11.2026, three
   test persons signed and the signatures verified.
6. All municipalities of Estonia are open, as the public address service finds the
   municipality for any point. Outside Estonia the place answers 404.
7. A resident who refuses the device identifier may still report as a guest, once a day.
