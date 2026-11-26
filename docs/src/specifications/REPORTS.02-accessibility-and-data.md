# REPORTS.02 · Accessibility and personal data

> **REPORTS.02** · state **Proposed** · belongs to [REPORTS.01](REPORTS.01-public-space-reports.md)

## Accessibility

The target is WCAG 2.2 AA. What is in place and what is not

| Need | How it is met | Not checked yet |
| --- | --- | --- |
| Colour is not the only cue | every status on the map has a sign, every status has a word | |
| Works without a map | the map has a list of the same points, an address can be typed | |
| Works without location | the resident may refuse and type the address | |
| Keyboard | links, buttons and fields are native elements, focus goes to the heading of a new screen | a full run by keyboard on the final pages |
| Screen reader | headings, labels and status messages are marked | a run with a screen reader |
| Movement | animations are off when the device asks for less motion | |
| Phone width | used at 375 px | a real phone |
| Camera | the camera works inside the page, with a large shutter button and a Cancel button; when the camera does not open, the page says so and offers to choose a photo | a run with a screen reader on a real phone |
| Pages of the unit | the same menu, list and report page as the coordinator, built from the same components | a full run by keyboard |
| Languages | English, Estonian, Russian, Ukrainian on the phone | a check by native speakers |

## Personal data

| Data | Why | Where | How long |
| --- | --- | --- | --- |
| IP address | limits against spam | keyed hash in the report | 24 hours |
| Device number | limits, points | random number on the device, keyed hash on the server | 6 months without a report |
| Smart-ID person | limits, points | keyed hash, the certificate is discarded after the check | while the service is used, an administrator deletes it on request |
| Name from Smart-ID | shown in the profile | only on the resident's device | until sign out |
| Contact | questions about this report | report, seen only by the coordinator | no deletion rule yet, an open question |
| Description, address | handling the report | report, public only after publication | no deletion rule yet, an open question |
| Photos | handling the report | database, redrawn without the data of the file | 6 months after closing |
| Map point | finding the place | report, rounded on the public map | with the report |
| Camera picture | the photo of the problem | only the taken frame is kept, as a photo; the video of the camera is never sent or stored | as photos |
| Letter to the unit | solving the problem | sent over X-Road to the unit: reference number, category, address, map point, description; never the contact or who sent it | with the report |

The device number and the hashes are pseudonymous personal data, not anonymous data. A
resident who does not want the device number can still report as a guest. The legal basis
was not checked by a lawyer. A municipality would probably rely on its public task, and
would need its own data protection assessment before real use.
