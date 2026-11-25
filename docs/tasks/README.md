# Tasks for the group

Each person works in an own folder and an own branch, so nobody edits the same file.
Routes are found automatically from `frontend/src/pages/<folder>/routes.tsx`, so
`App.tsx` is not touched. The calls to the server are ready in `frontend/src/reportsApi.ts`.

| Part | Who | Folder | Branch |
| --- | --- | --- | --- |
| Resident on the phone | Jevgeni | `frontend/src/pages/reports/` | `reports-ui-resident` |
| Coordinator on the web | Kamila (proposal) | `frontend/src/pages/reports-coordinator/` | `reports-ui-coordinator` |
| Public map and status lookup | Duc (proposal) | `frontend/src/pages/reports-map/` | `reports-ui-map` |

To run without installing anything see [cloud.md](cloud.md).

Everyone commits with the own GitHub account, writes the own diary and must be able to
explain the own part at the defence.

## Start, for everybody

1. `git pull` on `main`, then `git switch -c <branch>`.
2. Start the app with `scripts/run-backend` and `scripts/run-frontend`, open <http://localhost:18640>.
3. Give the agent the prompt of your part below.
4. Look at the result in the browser, change what you do not like, read the code.
5. `npx tsc -b`, commit, push, open a pull request, wait for green checks.

## Prompt · coordinator on the web

> Read AGENTS.md, docs/src/specifications/REPORTS.01-public-space-reports.md and
> frontend/src/pages/AnimalList.tsx and AnimalDetail.tsx. Build the coordinator pages in
> frontend/src/pages/reports-coordinator with a routes.tsx that exports `routes`. Use only
> frontend/src/reportsApi.ts for calls and only @helex/ui and antd components. Page 1
> /reports is a ResourceList with filters for category, status, municipality and channel,
> columns reference number (link), category, recipient, municipality, address with the tag
> "address not verified", status, days open, days to respond (late is marked), reminders.
> Page 2 /reports/:referenceNumber is a ResourceForm with useDataController, photos, status
> change with reason, "false report" and "publish on the map", update with a day, forward
> to another recipient, photos of the result, history, forwardings, messages. A small card
> shows the X-Road status. A user who is not a coordinator sees a clear "no access". The
> user `coordinator` has the role. Texts in English. Do not change other files. Explain every
> choice to me in simple words.

## Prompt · public map and status lookup

> Read AGENTS.md and docs/src/specifications/REPORTS.01-public-space-reports.md. Build in
> frontend/src/pages/reports-map with a routes.tsx that exports `routes`. Use only
> frontend/src/reportsApi.ts for calls. Page 1 /reports/map is a map with Leaflet (ask before
> adding the package), background https://tiles.maaamet.ee/tm/wmts/1.0.0/kaart/default/GMC/{z}/{y}/{x}.png
> with the credit "Maa- ja Ruumiamet". Colour and sign by status as in the specification, filter
> by category, a panel with the details and published photos, and a list view of the same
> points for people who cannot use a map. Page 2 /reports/lookup asks for the reference number
> and shows the five steps, days to respond, recipient, forwardings, updates and the rejection
> reason, "No report with this reference number" on 404. Other inputs are @helex/ui or antd.
> Must work at 360 px width. Texts in English. Do not change other files except package.json
> for Leaflet. Explain every choice to me in simple words.

## More parts, writing and checking

These need no installation. A file can be edited on github.com with the pencil. Choose
"Create a new branch and start a pull request" at the end, then the change counts as yours.

| Part | Result | Suggested for |
| --- | --- | --- |
| User stories | `REPORTS-US-003` public map for any resident, `REPORTS-US-004` identity and trust (guest, device, Smart-ID), `REPORTS-US-005` forwarding by the coordinator, from `docs/_templates/user-story.md`, and links in `SUMMARY.md` | Kamila |
| Specification | read REPORTS.01 as a stranger, write every unclear place as a comment in a pull request, then fix the agreed ones. Add "Accessibility" and "Personal data" sections | Duc |
| Interface review | try every screen on a phone and by keyboard, list what is hard, fix texts | Duc |
| Manual test protocol | `docs/test-protocol.md`, each business test by hand in Swagger with date and result | Kamila |
| Slides | 10 minutes, everyone presents the own part | all |
| Peer reviews | two other groups, after the defence, by 11.12 | all |
| Diary | `docs/diary-<name>.md`, own words | each |

How it counts. The commit must be made from the own account, GitHub shows it under
Insights, Contributors and in the pull request. A review also counts as visible work, so
please review each other's pull requests with at least one real question.
