# Tasks for the group

Each person works in an own folder and an own branch, so nobody edits the same file.
Routes are found automatically from `frontend/src/pages/<folder>/routes.tsx`, so
`App.tsx` is not touched. The calls to the server are ready in `frontend/src/reportsApi.ts`.

| Part | Who | Folder | Branch |
| --- | --- | --- | --- |
| Resident on the phone | Jevgeni | `frontend/src/pages/reports/` | `reports-ui-resident` |
| Coordinator on the web, first version | Jevgeni, done | `frontend/src/pages/reports-coordinator/` | `reports-ui-coordinator` |
| Coordinator review and own improvements | Kamila (proposal) | same folder, after the first version is merged | `coordinator-review` |
| X-Road status page | Kamila (proposal) | `frontend/src/pages/reports-xroad/` | `reports-ui-xroad` |
| Web version for the resident on a computer | Duc (proposal) | `frontend/src/pages/reports-map/` | `reports-ui-map` |

To run without installing anything see [cloud.md](cloud.md).

Everyone commits with the own GitHub account, writes the own diary and must be able to
explain the own part at the defence.

## Start, for everybody

1. `git pull` on `main`, then `git switch -c <branch>`.
2. Start the app with `scripts/run-backend` and `scripts/run-frontend`, open <http://localhost:18640>.
3. Give the agent the prompt of your part below.
4. Look at the result in the browser, change what you do not like, read the code.
5. `npx tsc -b`, commit, push, open a pull request, wait for green checks.

## Prompt · X-Road status page

> Read AGENTS.md and the X-Road parts of docs/src/specifications/REPORTS.01-public-space-reports.md.
> Ask me first what the page should show and to whom, propose two options and wait for my
> choice. Then build /reports-xroad in frontend/src/pages/reports-xroad with a routes.tsx that
> exports `routes`, using reportsApi.xroadStatus() and @helex/ui and antd components only.
> Show loading, available and not available, and "no access" for a resident. Explain every
> part to me in simple words and ask me three questions about it at the end.

## Coordinator review

Work as a coordinator for half an hour, accept, forward, reject and solve invented reports,
write down what is hard or wrong, decide what to change, and change it with your agent.
Your decisions and your corrections go into your diary.

## Prompt · web version for the resident

> Read AGENTS.md, docs/src/specifications/REPORTS.01-public-space-reports.md and the phone
> pages in frontend/src/pages/reports. Build for a computer screen in
> frontend/src/pages/reports-map with a routes.tsx that exports `routes`. Use only
> frontend/src/reportsApi.ts for calls. Page 1 /reports/map is a wide map
> with MapView from ../reports/MapView (Leaflet is already installed). Colour and sign by status as in the specification, filter
> by category, a panel with the details and published photos, and a list view of the same
> points for people who cannot use a map. Page 2 /reports/lookup asks for the reference number
> and shows the five steps, days to respond, recipient, forwardings, updates and the rejection
> reason, "No report with this reference number" on 404. Other inputs are @helex/ui or antd.
> Must work at 360 px width. Texts in English. Do not change other files. Explain every choice to me in simple words.

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

## Example, the first contribution in ten minutes

1. Open github.com and sign in with your own account, open our repository.
2. Open the folder `docs`, press Add file, Create new file.
3. Name it `diary-<your name>.md`, paste the table header of `diary-jevgeni.md` and write
   what you have done so far in your own words.
4. Press Commit changes, choose Create a new branch, Propose changes, Create pull request.
5. A teammate opens the pull request, Files changed, writes a question, Review changes, Approve.
6. Press Merge. Your name is now in the history.

The same way works for every text part, for example a new user story. For code parts the
agent on your computer writes into your folder, then you commit with your account.
