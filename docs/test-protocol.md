# Test protocol

Every business test of REPORTS.01, with its automated test and a check by hand. The columns
of the check by hand are filled in by the person who did it. Run the automated tests with
`gradlew test` in `backend`.

| No | Rule | Automated test | Checked by hand, how | Result | Who, when |
| :- | :- | :- | :- | :- | :- |
| 1 | A report needs a category and an address. | ReportBusinessRulesIT |  |  |  |
| 2 | A description must be at least 10 characters. | ReportBusinessRulesIT |  |  |  |
| 3 | A new report is Received and has a reference number. | ReportBusinessRulesIT |  |  |  |
| 4 | A repeated submit does not create a duplicate. | ReportBusinessRulesIT |  |  |  |
| 5 | A map point is both coordinates or none. | ReportBusinessRulesIT |  |  |  |
| 6 | Each report goes to the responsible unit. | ReportBusinessRulesIT |  |  |  |
| 7 | A resident can look up a report by its reference number. | ReportBusinessRulesIT |  |  |  |
| 8 | Only allowed status changes are accepted. | ReportBusinessRulesIT |  |  |  |
| 9 | Rejecting needs a reason. | ReportBusinessRulesIT |  |  |  |
| 10 | Every status change is recorded. | ReportBusinessRulesIT |  |  |  |
| 11 | A report is accepted even when the address register is down. | ReportBusinessRulesIT |  |  |  |
| 12 | The list can be filtered. | ReportBusinessRulesIT |  |  |  |
| 13 | Only a coordinator can change a status. | ReportBusinessRulesIT |  |  |  |
| 14 | A guest may send one report a day. | ReportIdentityRulesIT |  |  |  |
| 15 | A device is limited by its trust level. | ReportIdentityRulesIT |  |  |  |
| 16 | A false report resets trust. | ReportIdentityRulesIT |  |  |  |
| 17 | Changing the device does not remove every limit. | ReportIdentityRulesIT |  |  |  |
| 18 | A signed report is stored only after a verified signature. | ReportIdentityRulesIT |  |  |  |
| 19 | The municipality comes from the place. | ReportIdentityRulesIT |  |  |  |
| 20 | A resident sees why a report was rejected. | ReportIdentityRulesIT |  |  |  |
| 21 | Road damage on a state road goes to the state. | ReportRoutingRulesIT |  |  |  |
| 22 | The road map being down does not block a report. | ReportRoutingRulesIT |  |  |  |
| 23 | A resident sees the recipient before sending. | ReportRoutingRulesIT |  |  |  |
| 24 | A report that is not for its recipient is forwarded. | ReportRoutingRulesIT |  |  |  |
| 25 | X-Road is used and its failure is handled. | ReportRoutingRulesIT |  |  |  |
| 26 | There are nine categories. | ReportDialogueRulesIT |  |  |  |
| 27 | A photo is cleaned. | ReportDialogueRulesIT |  |  |  |
| 28 | Text and photos are public only after publication. | ReportDialogueRulesIT |  |  |  |
| 29 | The public map shows no personal data. | ReportDialogueRulesIT |  |  |  |
| 30 | The resident gets steps, updates and a date. | ReportDialogueRulesIT |  |  |  |
| 31 | The sender can remind. | ReportDialogueRulesIT |  |  |  |
| 32 | The sender can say that the problem remains. | ReportDialogueRulesIT |  |  |  |
| 33 | Points are given for confirmed and solved reports. | ReportDialogueRulesIT |  |  |  |
| 34 | Others can say that they see it too. | ReportDialogueRulesIT |  |  |  |
