# Running in the cloud

GitHub Codespaces is a computer in the cloud made from our repository. It is free up to
120 core hours a month (60 hours on two cores) and needs no card. It stops after 30 idle
minutes, which can be set up to 4 hours in the settings. Everyone can have an own one.

1. Open <https://github.com/settings/codespaces>, New secret, name `GPR_TOKEN`, value a
   classic token with only `read:packages`, repository this one.
2. In the repository press Code, Codespaces, Create codespace on main. Wait until it is ready.
3. In the terminal below run `./scripts/cloud-start.sh`.
4. Tab Ports, line 18640, press the globe. For a phone or another person set
   Port Visibility to Public, and back to Private afterwards.

Public means anyone with the address can open it and, because sign-in is a mock, act as
the coordinator. Use invented data only and stop the codespace after showing. The token
is never written into the repository.
