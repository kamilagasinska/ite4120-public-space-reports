# REPORTS.01 · Public space reports

> **REPORTS.01** · state **Agreed** (revision 5, handover to the responsible unit) · traces from [REPORTS-US-001](../user-stories/REPORTS-US-001.md)
> and [REPORTS-US-002](../user-stories/REPORTS-US-002.md)

## Description

A register of problems in public space reported by residents to the local
government, in nine categories from road damage to litter and lighting. A resident submits a report with a
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

Excluded are video and audio, nearby waste collection points, notifications by email or SMS, assigning work to individual crews,
duplicate detection, statistics dashboards, a production Smart-ID contract (the demo environment only), and real
sign-in of coordinators (mock auth only).

## Roles

| Role | Who | May do |
| --- | --- | --- |
| Resident | any signed-in mock user | submit a report, look up a report by reference number, read the categories |
| Coordinator | mock users listed in the configuration key `reports.coordinators` | everything a resident may, and also list and filter reports, open the report page with the contact, accept, reject and forward, see history |
| Unit | a mock user named as the inbox address of a unit | list and open the reports handed to it, set IN_PROGRESS and RESOLVED, write updates, add photos of the result, return a report |

A resident calling a coordinator endpoint gets 403. The status lookup by reference
number never returns the contact to a resident.

## Identity and limits

| Way | What is stored about the reporter | Limit at the start |
| --- | --- | --- |
| Guest | a keyed hash of the IP address | 1 report a day from one IP address |
| Device | a keyed hash of a random identifier kept in the browser | trust level 0 |
| Signed | a keyed hash of the Smart-ID person | trust level 0 of the signed scale |

| Trust level | From | Device limit | Signed limit |
| --- | --- | --- | --- |
| 0 | 0 points | 1 in 10 minutes, 5 a day | 1 a minute, 50 a day |
| 1 | 50 points | 1 in 5 minutes, 10 a day | 1 a minute, 100 a day |
| 2 | 150 points | 1 in 2 minutes, 20 a day | 1 a minute, 200 a day |
| 3 | 300 points | 1 a minute, 30 a day | 1 a minute, 300 a day |

The level is calculated from the reporter's points. A normal rejection does
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
The name in the certificate is told once to the signing device, which shows it in the
profile. The server does not store it.

**Failure policy** for Smart-ID is **fail closed**. If it is unreachable or the person
refuses, nothing is stored as signed. The form keeps its content and offers to send as
device or guest instead.

## Categories

| Code | Name | Responsible unit | On the public map |
| --- | --- | --- | --- |
| ROAD_DAMAGE | Road or street | Roads Department | yes |
| LITTER | Litter | Environment Department | yes |
| LIGHTING | Street lighting | Lighting Department | yes |
| TREES | Trees and parks | Parks Department | yes |
| DANGEROUS_OBJECT | Dangerous object | Safety Department | yes |
| DEAD_ANIMAL | Dead animal | Environment Department | yes |
| VANDALISM | Graffiti and vandalism | Property Department | yes |
| PUBLIC_ORDER | Public order | Public Order Department | no, it is about people |
| OTHER | Something else | Coordinator | yes |

The first seven municipal topics of Anna teada are covered. The form tells the resident
to call 112 first when there is danger to life. OTHER goes to the coordinator.

## Photos

A resident may add up to 5 photos, JPEG or PNG, at most 8 MB each. The server decodes
every photo and stores a new JPEG of at most 1600 px, so the location and the camera
data inside the file are dropped and a file that is not a picture is refused (400).
Photos can show faces
and number plates, therefore only the sender and the coordinator see them until the
coordinator publishes the report. They are deleted 6 months after the report is closed.
The unit the report was handed to can add up to 5 photos of the result. Photos are kept
in the database behind a storage interface.

On a phone the page takes the photo itself with the camera of the browser and does not open
the camera app of the phone. A phone may close the browser tab while its own camera app is
open, and the report being written would be lost. If the browser cannot open the camera,
the page says so and the resident chooses a photo from the phone. Without a photo the page
says that checking may take longer, and the report can still be sent. A photo changes
neither the order of the list nor the trust of the sender. Production would use object storage (TEHIK).

## Public map

Anyone signed in sees reports as points on a map. A point shows the category, the
status, the municipality and the date, rounded to 4 decimals, about 10 metres. The
description, the address and the photos appear when the coordinator has looked at them
and published the report, because the text of a stranger can name a person. The
reference number, the contact and the sender are never shown. PUBLIC_ORDER is never on
the map. A rejected report stays for 3 days without text, a resolved one for 30 days.
Colours are yellow for received, blue for accepted, orange for in progress, green for
resolved and red for rejected, always together with a sign so colour is not the only
cue. The background is the map of the Estonian Land and Spatial Development Board.

## Steps and dialogue

| Step for the resident | Meaning |
| --- | --- |
| Reported | RECEIVED |
| Accepted | ACCEPTED, the coordinator has confirmed it and handed it to the unit |
| In progress | IN_PROGRESS, set by the unit |
| Update | a message of the coordinator or the unit, with a day to expect something |
| Done | RESOLVED, with photos of the result, or REJECTED with the reason |

The resident sees how many days are left for the first answer, 15 days from sending,
following the general deadline for answering people in Estonia. In this course it is
only a counter and the legal side was not checked. The counter ends with the first
reaction of the coordinator.

The sender is known by the submission key of the report, which only the sending device
has. The sender can remind once in 3 days, and within 30 days of RESOLVED say that the
problem remains, which reopens the report as ACCEPTED.

**Points.** A confirmed report gives 10, 5 more with a photo, and 20 more when it is
solved. A reopened report loses the 20 until it is solved again. A false report sets
the points to 0. Levels begin at 0, 50, 150 and 300 points and are the trust levels.
Rewards are not promised.

## Recipient

| Category and place | Recipient |
| --- | --- |
| Road damage, nearest road within 30 m is a main, basic or side highway | Transport Administration |
| Road damage, nearest road is a street, or no road within 30 m | the municipality of the point, unit of the category |
| Road damage, nearest road is another road, or the road map is down | coordinator decides |
| Litter | the municipality, unit of the category |
| No municipality known | coordinator decides |

The road type comes from the public road layer of the topographic database ETAK. It has
no owner field, so this is our rule and the recipient is a proposal. The unit is a
general name, because no open list of municipal departments was found. The resident sees
the recipient before sending and afterwards. If the recipient says the report is not
theirs, the coordinator forwards it with a reason, and the resident sees where it went
and why. In this course nothing is delivered to a real authority.

## Handover to the unit

The coordinator only checks reports. Solving is the work of the responsible unit, so an
accepted report is handed over to it.

1. When the coordinator accepts a report, the application hands it to the recipient
   through a **unit channel**. Forwarding an accepted report and reopening a resolved one
   hand it over again, to the current recipient. A report whose recipient is
   "coordinator decides" has no unit yet. It can be accepted, and is handed over when the
   coordinator forwards it.
2. The unit channel is an adapter (`reports.unit-channel.mode`). `xroad` sends the report
   as an X-Road message (protocol for REST, `POST`) from our client subsystem to the
   service `receiveReport` of the subsystem `reports-inbox` of the recipient. Locally it
   goes to the imitated security server of this application, which delivers it to an
   imitated inbox of the unit and answers with an X-Road message id. `outbox` only stores
   the handover. A real version needs a real security server and a recipient that
   offers such a service, the rest stays.
3. The X-Road Playground cannot be used here, as it only offers its own test services
   and we are not a member who can register one.
4. The handover keeps the unit, its inbox address, the X-Road service, the message id and
   a state: SENT, OPENED by the unit, RETURNED by the unit with a reason, or FAILED when
   the channel did not answer. A failed handover does not undo the acceptance, the
   coordinator sees it and sends it again.
5. A unit worker signs in with a mock sign-in as the inbox address of the unit, for
   example `roads@tallinn.example`. The unit sees only the reports handed to it, and
   may set IN_PROGRESS, write an update for the resident, add photos of the result, set
   RESOLVED, or return the report as not theirs with a reason. A returned report waits
   for the coordinator, who forwards or rejects it.
6. Only the unit of the current handover sets IN_PROGRESS and RESOLVED. The coordinator
   accepts, rejects, forwards and watches the 15 days.
7. The resident sees the unit and the day of the handover, never a user name or the
   inbox address.

The inbox addresses and X-Road member codes are invented from the recipient name
(`roads@tallinn.example`, `ee-test/GOV/tallinn/reports-inbox/receiveReport`), because no
open list of the units and their systems was found.

## Data model

| Table | Field | Type | Rules |
| --- | --- | --- | --- |
| `reports.category` | `code` PK | text | reference list, see Categories |
| | `name` | text | display name |
| | `responsible_unit` | text | required, ≤100, see Categories |
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
| | `recipient_kind` | text | required; STATE_ROADS, MUNICIPALITY, OTHER or UNDETERMINED |
| | `recipient_name` | text | required, ≤220 |
| | `channel` | text | required; GUEST, DEVICE or SIGNED |
| | `reporter_id` | bigint | FK → reporter; empty for a guest |
| | `ip_hash` | text | optional; cleared after 24 hours |
| | `signed_digest` | text | required when the channel is SIGNED |
| | `status` | text | required, default RECEIVED; one of RECEIVED, ACCEPTED, IN_PROGRESS, RESOLVED, REJECTED |
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
| `reports.forwarding` | `id` PK | bigint | from `core.seq_id` |
| | `report_id` | bigint | required, FK → report |
| | `from_name`, `to_kind`, `to_name` | text | required; kind is STATE_ROADS, MUNICIPALITY or OTHER |
| | `reason` | text | required, ≤500 |
| | `sys_*` | platform | trigger-managed |
| `reports.photo` | `id` PK | bigint | from `core.seq_id` |
| | `report_id` | bigint | required, FK → report |
| | `kind` | text | required; BEFORE or AFTER |
| | `content` | bytea | required, JPEG |
| | `sys_*` | platform | trigger-managed |
| `reports.message` | `id` PK | bigint | from `core.seq_id` |
| | `report_id` | bigint | required, FK → report |
| | `kind` | text | required; UPDATE, REMINDER or REMAINS |
| | `text` | text | ≤500, required except for REMINDER |
| | `expected_on` | date | optional |
| | `sys_*` | platform | trigger-managed |
| `reports.sighting` | `id` PK | bigint | from `core.seq_id` |
| | `report_id` | bigint | required, FK → report |
| | `key_hash` | text | required, unique per report among active rows |
| | `sys_*` | platform | trigger-managed |
| `reports.handover` | `id` PK | bigint | from `core.seq_id` |
| | `report_id` | bigint | FK → report |
| | `recipient_name`, `unit` | text | required; the recipient and the unit at the time of the handover |
| | `inbox` | text | required; the invented inbox address, also the mock user of the unit |
| | `channel` | text | required; XROAD or OUTBOX |
| | `service` | text | the X-Road service it was sent to |
| | `state` | text | required; SENT, OPENED, RETURNED, FAILED |
| | `message_id` | text | the X-Road message id of the answer |
| | `detail`, `return_reason` | text | ≤500; why the channel failed, why the unit returned it (required when RETURNED) |
| | `sys_*` | platform | trigger-managed |
| `reports.signing` | `id` PK | bigint | from `core.seq_id` |
| | `token` | text | required, unique; random, used by the form to ask for the result |
| | `session_id` | text | required; the Smart-ID session |
| | `payload` | jsonb | required until finished; the report being signed, then cleared |
| | `state` | text | required; PENDING, SIGNED, REFUSED, EXPIRED, FAILED |
| | `report_id` | bigint | FK → report; set when SIGNED |
| | `sys_*` | platform | trigger-managed |

Allowed status changes are RECEIVED → ACCEPTED by the coordinator, ACCEPTED → IN_PROGRESS
and IN_PROGRESS → RESOLVED by the unit of the current handover, RECEIVED or ACCEPTED → REJECTED, and RESOLVED → ACCEPTED by
the sender. Any other change is refused.

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
| `GET /api/reports` | coordinator; paged list (`categoryCode`, `status`, `municipalityCode`, `channel`, `late`, `q`, `limit`, `offset`, `sort`), each row with days open and responsible unit. **This is the reporting operation.** | 400 unknown category, status or sort · 403 |
| `GET /api/reports/{referenceNumber}` | one report with status, category, responsible unit, address, verification, map point and submission time; description, contact and `sys*` metadata only for a coordinator | 404 |
| `POST /api/reports` | submit; answers **201 + Location** with the reference number; the same `submissionKey` again answers 200 with the existing report; the header `X-Device-Id` makes it a device report, without it a guest report | 400 validation · 429 limit |
| `POST /api/reports/signings` | start signing a report with a demo test person; answers 201 with a token and the verification code | 400 · 429 limit · 502 Smart-ID unreachable |
| `GET /api/reports/signings/{token}` | state of the signing, with the reference number when signed | 404 |
| `GET /api/reports/place` | municipality and address of a map point (`latitude`, `longitude`), with `categoryCode` also the recipient | 400 · 404 · 502 |
| `POST /api/reports/{referenceNumber}/forward` | coordinator; send to another recipient (`kind`, `name`, `reason`) | 400 · 403 · 404 · 409 closed or already there |
| `GET /api/reports/{referenceNumber}/forwardings` | where it was forwarded, oldest first; user name only for a coordinator | 404 |
| `GET /api/reports/handed` | unit; the reports handed to it and not returned, open ones first | 403 |
| `GET /api/reports/units` | inboxes with open reports, only for the mock sign-in of a unit | none |
| `POST /api/reports/{referenceNumber}/handover` | coordinator; send a failed or returned handover again | 403 · 404 · 409 |
| `POST /api/reports/{referenceNumber}/handover/return` | unit; return the report as not theirs (`reason`) | 400 · 403 · 404 · 409 |
| `GET /api/reports/xroad-status` | coordinator; live call to the X-Road test environment, with the hash of the request made by the security server and the list of its services; a failure is shown as not available | 403 |
| `POST /api/reports/{referenceNumber}/status` | coordinator; change status (`toStatus`, `reason`, `falseReport`, `publish`) | 400 missing reason · 403 · 404 · 409 change not allowed |
| `GET /api/reports/{referenceNumber}/history` | coordinator; status changes, oldest first | 403 · 404 |
| `POST /api/reports/{referenceNumber}/photos` | add a photo (multipart `file`); the sender with `X-Submission-Key`, or the coordinator a photo of the result | 400 not a picture, too large or a sixth one · 404 |
| `POST /api/reports/{referenceNumber}/updates` | coordinator; message with optional day | 400 · 403 · 404 · 409 closed |
| `GET /api/reports/{referenceNumber}/messages` | updates, reminders and reopenings; user name only for a coordinator | 404 |
| `POST /api/reports/{referenceNumber}/reminders` | sender; remind | 404 not the sender · 409 too early or closed |
| `POST /api/reports/{referenceNumber}/remains` | sender; the problem remains (`text`) | 400 · 404 · 409 |
| `POST /api/reports/map/{id}/sightings` | I see it too, counted once per device `X-Device-Id`; no points, no priority | 400 · 404 |
| `GET /api/reports/me` | points and level of the device `X-Device-Id` | 400 |
| `GET /api/reports/map/{id}` and `/map/{id}/photos/{photoId}` | one point, text and photos once published | 404 |
| `GET /api/reports/{referenceNumber}/photos` and `/photos/{id}` | coordinator or sender | 403 · 404 |
| `GET /api/reports/map` | points inside `west`, `south`, `east`, `north`, optional `categoryCode`, at most 500 | 400 |
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
Several matches are ambiguous and leave the report unverified,
unless the address is the one the register gave for the map point. The lookup waits at
most 3 seconds. The transport is configuration (`reports.address-register.mode`,
`http` or `xroad`). Locally the mode is `xroad`. The application speaks the X-Road message
protocol for REST to an imitated security server, which passes the question to the public
address service of Estonia. An address containing "register-down" makes it answer with a
fault, to show the policy live. The identifiers are those of the real register
(70003098/aks-ads). The answer has the shape of the public address service, because the
service description is readable only inside X-Road.

The list without `sort` shows the longest waiting report first. Sort keys are
`referenceNumber`, `categoryCode`, `address`, `status`, `submittedAt` and `daysOpen`.

## UI

The resident pages are drawn for this project (design Hole in One) and are not built from
`@helex/ui`, because they are a phone app. The pages of the coordinator and of the unit use
`@helex/ui` and antd, lists on `ResourceList` and the report page on `ResourceForm`.

| Screen | Path | Who | What it shows and does |
| --- | --- | --- | --- |
| Start | `/spot` | resident | sign in with Smart-ID, or go on without signing in |
| Before you start | `/spot/privacy` | resident | what is kept and why; device number, guest or Smart-ID, with the limit of each |
| What | `/spot/home` | resident | six pictures for the nine categories, points of the device |
| Where | `/spot/where` | resident | map with a pin, "I am here now" or a typed address, the recipient before sending |
| Words, photo, contact, check | `/spot/words` … `/spot/check` | resident | ready answers or own words, up to 5 photos with the camera inside the page, optional contact, a last check and sending |
| My reports | `/spot/mine` | resident | the reports sent on this device by this person, All, Open, Closed |
| Status | `/spot/status/{referenceNumber}` | resident | the five steps, the unit it was handed to and the day, news, remind, "the problem remains" |
| Map | `/spot/map` | resident | public points with a list of the same points, "I see it too" |
| Profile | `/spot/account` | resident | name and portrait of the demo person, points, level, language |
| Report list | `/reports` | coordinator | a card with a count for every status and for late reports, search, filters, `ResourceList` |
| Report page | `/reports/{referenceNumber}` | coordinator | the words, photos and place, Accept, Reject, Forward, Write news, the handover with its X-Road service and message id, Send to the unit again, history |
| Map | `/reports/map` | coordinator | all reports on a map |
| X-Road | `/reports-xroad` | coordinator | live call to the X-Road Playground with the request hash and the services it listed |
| Sign in as a unit | `/unit` | unit | mock sign-in, a choice of the units that have open reports |
| Handed to us | `/unit` | unit | the reports handed to the unit, new ones marked |
| Report page | `/unit/{referenceNumber}` | unit | Start work, Mark as resolved, Write news, a photo of the result, Not ours, return |

**Layout.** The resident pages are at `/spot` and have two layouts. Below 960 px it is
the phone layout. From 960 px the same steps are laid out for a computer, with the menu
on top, the steps beside the form and the list beside the map. The pages of the
municipal worker are for a computer only. The coordinator has a menu on the left with
Reports, Map and X-Road, the unit has Handed to us. The list has a card with a count for every status. The report page shows the
progress, the buttons of the allowed next steps, the words of the resident, the photos
and the place on a map. Status, news and forwarding open in a window.

**Showcase data.** The local profile also loads 71 invented past reports over eight
municipalities (Liquibase context `showcase`), a part of them sent by the
four Smart-ID demo persons. Their test certificates all carry the name OK TEST, so the
profile shows an invented name and a drawn portrait instead. The addresses are real, the problems and the senders are
invented and the pictures are drawn. Tests and a production profile do not load them.

**Languages.** The resident pages are in English, Estonian, Russian and Ukrainian, with
English first. The translations need a check by native speakers. The coordinator pages
are in English. Six pictures lead to the nine categories. Nature covers TREES and
DEAD_ANIMAL, Graffiti and damage is VANDALISM, Danger and other covers
DANGEROUS_OBJECT, PUBLIC_ORDER and OTHER.

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
    (90 points) the limit is 1 in 5 minutes.
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
21. **Road damage on a state road goes to the state.** Given the nearest road is a highway,
    when road damage is reported, then the recipient is the Transport Administration. On a
    street it is the municipality, on another road the coordinator decides. Litter beside a
    highway goes to the municipality.
22. **The road map being down does not block a report.** Given an unreachable map, when
    road damage is reported, then it is accepted and the coordinator decides.
23. **A resident sees the recipient before sending.** Given a point and a category, when
    the place is asked, then the recipient is returned.
24. **A report that is not for its recipient is forwarded.** Given a report, when a
    coordinator forwards it with a reason, then the resident sees the new recipient and the
    reason, not the user name. Without a reason it is 400, a closed report 409, a resident 403.
25. **X-Road is used and its failure is handled.** Given the xroad mode, when a report is
    submitted, then the register is asked with the X-Road client and service identifiers. A
    fault leaves the address unverified. The coordinator sees whether the test environment
    answers, with its request hash and services. An answer that is not a list of services counts
    as no answer.
26. **There are nine categories.** Given the category list, then it has the nine codes with
    their units, and a report in OTHER is decided by the coordinator.
27. **A photo is cleaned.** Given a JPEG with GPS data, when it is added, then the stored
    picture has no such data. A text file named .jpg is refused (400), as is a sixth photo.
28. **Text and photos are public only after publication.** Given a report with a photo, when
    a resident opens its point on the map, then there is no text and no photo. After the
    coordinator has published it they are there, without reference number and sender.
29. **The public map shows no personal data.** Given reports, when the map is read, then a
    point has only category, status, municipality, date and the rounded place. PUBLIC_ORDER is
    not there and a rejected report leaves after 3 days.
30. **The resident gets steps, updates and a date.** Given a new report, then 15 days are left
    to respond. After the coordinator's first reaction the counter is gone, and an update
    shows its text and day, not the user name.
31. **The sender can remind.** Given a report of 4 days, when its sender reminds, then the
    coordinator sees one reminder. Again within 3 days is 409, another person gets 404.
32. **The sender can say that the problem remains.** Given a resolved report, when its sender
    says so with a text, then it is ACCEPTED again and the history shows it. After 30 days it is 409.
33. **Points are given for confirmed and solved reports.** Given a device, when its report with
    a photo is accepted, then it has 15 points, when solved 35, when reopened 15, and after a
    false report 0.
34. **Others can say that they see it too.** Given a report on the map, when another device says
    so, then it is seen by 2. The same device again and the sender change nothing.

35. **The coordinator finds late reports and searches.** Given a report of 16 days without a
    reaction, when late reports are asked for, then only it is listed. A part of the number,
    the address or the text finds a report. A resident gets 403.
36. **The coordinator sees the trust of the sender.** Given a device with two reports, one
    confirmed, when the second is opened by the coordinator, then it shows 2 reports, 10
    points and no false report, and nothing that names the sender. A resident does not see
    it, and a guest has no such numbers.
37. **An accepted report is handed to the unit over X-Road.** Given the xroad channel, when
    the coordinator accepts a report, then an X-Road message with our client, the service of
    the recipient and the reference number is sent, and the coordinator sees the unit, the
    inbox and the message id. The resident sees the unit, not the inbox.
38. **Only the unit solves.** Given a handed report, when the coordinator sets IN_PROGRESS,
    then 403. Another unit gets 403 and does not see the report in its list. The unit sets
    IN_PROGRESS, adds a photo of the result, writes an update and sets RESOLVED.
39. **The unit returns a report that is not theirs.** Given a handed report, when the unit
    returns it without a reason, then 400; with a reason the handover is RETURNED and the
    unit can no longer change it. Forwarding hands it to the new recipient.
40. **A channel that does not answer does not undo the acceptance.** Given X-Road down,
    when a report is accepted, then it is ACCEPTED and the handover FAILED. Sending again
    when X-Road is back makes it SENT.

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
8. The coordinator only checks. Solving is the work of the responsible unit, and only the
   unit of the current handover sets In progress and Resolved.
9. The handover goes over X-Road through the imitated security server of this
   application. The X-Road Playground cannot be used, as we cannot register a service
   there. The member codes and inbox addresses are invented, as no open list of the
   units and their systems was found.
10. A failed handover does not undo the acceptance. The coordinator sends it again.
11. On a phone the page takes photos with the camera of the browser, because the camera
    app of the phone may close the browser tab and lose the report.
