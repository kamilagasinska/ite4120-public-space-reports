# Work diary: Jevgeni Orehhov

| Date | Hours | What I did | What I asked the agent | What I had to fix |
| :- | :- | :- | :- | :- |
| 25.11 | not recorded | Installed the toolchain (JDK 25, Docker, WSL, GitHub CLI) and ran the template. Reviewed REPORTS.01 and decided the four open questions on reference number, repeated submit, roles and anonymity. | Set up the environment, write REPORTS.01 from my notes, fix the story file name and the summary, write the migrations for the reports schema. | The agent gave me a token command of several lines that could break when pasted, and it was replaced with one line. JAVA_HOME still pointed at JDK 21 after the install because my user variable hid the system one. |
| 25.11 | not recorded | Business tests first, then the address register adapter, service and REST API for reports. | Write the 13 business tests over HTTP and see them fail, then implement until they pass. | Review of the code still to be done by me. |
