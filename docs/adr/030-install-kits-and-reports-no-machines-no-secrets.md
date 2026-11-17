# ADR 030: Install kits and reports, with no machines and no secrets

## Status
Accepted (2026-11-17)

## Context
The Quick Install add-on installs Windows, Linux and recovery tools unattended from a Ventoy stick. To be a module of the platform (journey 17) the
platform has to know which kits exist and what happened when one ran. The add-on, on the owner's own stick, holds machine names, drivers, product keys
and vendor artwork. None of that belongs in a multi-tenant service.

## Decision
- `InstallKit` is a recipe, not a machine: a name, a family, a role, a language, a `systemId` and a `machineClass` (short codes), plus a `revision`
  that rises with every update. It keeps no hostname, serial number, key, credential or person.
- `InstallReport` is a fact about one run: the kit and its revision, the add-on version, an outcome (`SUCCEEDED`, `PARTIAL`, `FAILED`), a warning
  count and, optionally, an `assetId`. That asset id is the only link to a machine, and the asset registry owns what a machine is.
- A server kit is English only and every other kit may be English or Portuguese: the rule of the add-on, enforced by the domain.
- Kits are retired, never deleted (like every registry). Reports are inserted once and never changed.
- The stick definition is a read over the active kits (grouped by system), so a stick builder needs no second source of truth.
- The module is included in every edition and is not gated by billing.

## Consequences
- **Positive:** the service stays free of personal and secret data, can be public-cloud hosted, and the report trail answers "which revision of which kit
  ran on this asset, and how did it end" without any machine detail.
- **Negative:** per-machine details (names, drivers, keys) are not visible in the platform; they stay in the owner's private stick configuration.
- **Follow-up:** a report may later create a discovered item in `it-discovery` with the kit as provenance (a separate slice and ADR).
