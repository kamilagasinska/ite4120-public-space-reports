# Tasks for the group

Each person works in an own folder and an own branch, so nobody edits the same file.
Routes are found automatically from `frontend/src/pages/<folder>/routes.tsx`, so
`App.tsx` is not touched. The calls to the server are ready in `frontend/src/reportsApi.ts`.

| Part | Who | Folder | Branch |
| --- | --- | --- | --- |
| Resident on the phone | Jevgeni | `frontend/src/pages/reports/` | `reports-ui-resident` |
| Coordinator on the web | Kamila (proposal) | `frontend/src/pages/reports-coordinator/` | `reports-ui-coordinator` |
| Public map and status lookup | third member (proposal) | `frontend/src/pages/reports-map/` | `reports-ui-map` |

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
