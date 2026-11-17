# micronaut-it-install-kit-service

BIAN-aligned Service Domain **it-install-kit** (Control Record: `InstallKit`), port `8115`.

The back end of the ThinkLab **Quick Install** module (journey 17). Quick Install is an unattended Ventoy install stick
([thinklab-quickinstall](https://github.com/fernan-89/thinklab-quickinstall)); this service keeps what the platform needs to know about it:

- **Install kits**: which system, in which language, for which class of machine the organisation installs (`InstallKit`).
- **Install reports**: what happened when a kit ran, which revision of the kit and which version of the add-on (`InstallReport`).
- **The stick definition**: the active kits grouped by system, for a stick builder.

## What it guarantees, and what it does not

- **No machines, no secrets** (ADR-030): a kit is a name, a `family` (`WINDOWS`, `LINUX`, `RECOVERY`), a `role` (`WORKSTATION`, `SERVER`, `TOOL`),
  a `language` (`EN`, `PT`), a `systemId` and a `machineClass` (short codes such as `W11` and `BASE`). No hostname, serial number, product key,
  password or person is kept; a report's only link to a machine is an optional `assetId`.
- **Servers are English only**: a kit with role `SERVER` and language `PT` is refused (400), the rule of the add-on.
- **A kit is never deleted**: it is retired (`control/retire`) and leaves the stick definition; `control/reinstate` brings it back. Every `update`
  raises its `revision`, and a report stores the revision that ran. A retired kit can still be reported on: a machine may have been installed with it.
- **A report is a fact**: it is only ever inserted, never changed or deleted.
- **Staff only** (ADR-032): every route refuses a `REQUESTER` with 403 `ERR-IKT-00403`; another tenant's kit or report answers 404.
- **Safe with several instances** (ADR-033): a kit name is unique per organisation and every change is guarded by the status loaded and appends
  its audit entry in the same update.
- **Included in every edition**: the module is not gated by billing; the same code serves homelab to global.
- **Not here yet:** a report feeding it-discovery (a machine installed by a kit appearing as a discovered asset), and a stick builder that reads
  the definition. Both are later slices of journey 17.

## BIAN Behavior Qualifier Contract

`X-Tenant-Id` is mandatory on every call; `X-Executor` is mandatory on the actions; `X-Role` is optional and, with platform security on, comes from
the verified token.

| Behavior Qualifier | Route |
|---|---|
| initiate | `POST /it-install-kit/v1/initiate` `{"name":"Windows 11 EN","family":"WINDOWS","role":"WORKSTATION","language":"EN","systemId":"W11","machineClass":"BASE"}` |
| retrieve | `GET /it-install-kit/v1/{id}/retrieve` |
| retrieve (collection) | `GET /it-install-kit/v1/retrieve?status=&family=` (oldest first) |
| update | `PUT /it-install-kit/v1/{id}/update` (the whole definition again, in any status; the revision goes up) |
| control/retire | `PUT /it-install-kit/v1/{id}/control/retire` (ACTIVE -> RETIRED) |
| control/reinstate | `PUT /it-install-kit/v1/{id}/control/reinstate` (RETIRED -> ACTIVE) |
| audit-log/retrieve | `GET /it-install-kit/v1/{id}/audit-log/retrieve` |
| stick-definition/retrieve | `GET /it-install-kit/v1/stick-definition/retrieve` (active kits by system, sorted by language then machine class) |
| install-report/initiate | `POST /it-install-kit/v1/install-report/initiate` `{"kitId":"<uuid>","addOnVersion":"1.2.2","outcome":"SUCCEEDED","warnings":0,"assetId":"<uuid, optional>","occurredAt":"2026-11-17T12:00:00Z"}` |
| install-report/retrieve | `GET /it-install-kit/v1/install-report/{id}/retrieve` |
| install-report/retrieve (collection) | `GET /it-install-kit/v1/install-report/retrieve?kitId=&outcome=&assetId=` (newest first) |

```text
kit:    ACTIVE <-> RETIRED          report: inserted once (SUCCEEDED | PARTIAL | FAILED)
```

```bash
curl -X POST "http://localhost:8115/it-install-kit/v1/initiate" -H "X-Tenant-Id: <organisationId>" -H "X-Executor: <userId>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Windows 11 EN","family":"WINDOWS","role":"WORKSTATION","language":"EN","systemId":"W11","machineClass":"BASE"}'
```

## Error catalog

| Code | HTTP | Meaning |
|---|---|---|
| `ERR-IKT-00403` | 403 | A REQUESTER used the service (ADR-032) |
| `ERR-IKT-00404` | 404 | Kit or report not found (another tenant's answers the same) |
| `ERR-IKT-00409` | 409 | Duplicate kit name, illegal transition (retire a retired kit), or the kit changed while the write was applied (retry) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure, a code that is not a short code, a server kit that is not English, a malformed add-on version, a negative or oversized warning count |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

## Configuration

`HASH_SERVICE_URL` (default `http://localhost:8080`, the sovereign ids), `MICRONAUT_SERVER_PORT` (default 8115), `mongodb.uri`,
`thinklab.mongo.create-indexes` (default on). The service calls no other service and has no scheduler.

Do not put personal data in a kit name: it is stored with the kit.

## Build and test

```
./gradlew test jacocoTestCoverageVerification   # unit tests with the 100% line and branch gate
./gradlew integrationTest                       # real MongoDB through Testcontainers (Docker)
```

## Architecture decisions

001 hexagonal architecture · 005 UUID identity sovereignty and audit tracing · 013 BIAN conventions · 019 HTTP 409 for state conflicts ·
030 install kits and reports, with no machines and no secrets.

## License

Licensed under the [PolyForm Strict License 1.0.0](LICENSE): you may read and use this software for noncommercial purposes only. Modifying it, creating derivative works, redistributing it and any commercial use are not permitted without a separate written license. This software is not open source.
