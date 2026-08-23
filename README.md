# 15 Rajputana Rifles — Battalion Intranet & ERP Portal

## 1. Bugs found in a full code review (this pass) and how they were fixed

Requested: "review every code for any error." Read every backend file line by line and found 7 real bugs — not stylistic nitpicks, things that would have broken or silently under-secured the system:

| # | Bug | Fix |
|---|---|---|
| 1 | `ReadOnlyOrHasEditRights.has_permission` only checked `is_authenticated` — object-level checks never run on `POST` since there's no object yet, so **any authenticated user (including Read Only) could create Personnel/Company records.** | Added explicit create-time capability check in `has_permission`. |
| 2 | Company `state` endpoint counted anyone with an *ever-approved* leave application as "on leave," not leave active today — inconsistent with the Dashboard endpoint. | Added the same `start_date__lte=today, end_date__gte=today` filter used elsewhere. |
| 3 | `CompanyViewSet` had no write permission at all — any authenticated user could create/delete companies. | Restricted create/update/delete to Adjutant/superuser via `get_permissions()`. |
| 4 | `PersonnelViewSet.get_serializer_class()` used the lightweight list serializer for `create`, which is missing required fields (`father_name`, `date_of_birth`, `date_of_enrollment`) — **creating a personnel record would have failed outright.** | Full detail serializer now used for every action except `list`. |
| 5 | Leave approval only checked the approver's *role*, not that they belonged to the *same company* as the applicant — a Clerk from B Company could approve A Company's leave. | Added a company match check for the Clerk/Company Commander stages. |
| 6 | Leave cancellation compared `Personnel.army_number` to `User.service_number` as strings — two unrelated fields on different models with no enforced link. | Added a real `User.linked_personnel` FK; cancel now checks that FK. |
| 7 | The generic company-scoping permission assumed objects have a direct `.company` field — broke for `Course` (which only has `.personnel.company`), silently blocking JCO/Clerk from managing courses for their own company. | Generalized to resolve company via either `.company` or `.personnel.company`. |

Also hardened while reviewing: a race condition in inventory stock issue (two concurrent issues could both pass the stock check and drive quantity negative — added `select_for_update`); missing input validation on weapon issue and training reports; and I caught myself **repeating bug-pattern #: `get_queryset()` returning a Python list instead of a QuerySet** a second time in the new Operations module (same class of bug as the Documents fix from the previous pass) — fixed before it shipped.

**Still true: none of this has run against a live Django/Postgres instance.** No network access in this sandbox for `pip install`/`docker build`. Every file is syntax-clean (`ast.parse`) and manually cross-checked import-by-import, but treat your first `docker compose up` as a real test, not a formality. Run `python manage.py makemigrations --check` first.

## 1b. Gap-fixing pass — the six newest modules, checked with the same scrutiny

The previous pass built Directory, Calendar, Messaging, Maps, Operations, and MIS quickly and didn't subject them to the same bug hunt as the original modules. This pass did. Found 6 more real issues:

| # | Module | Gap | Fix |
|---|---|---|---|
| 8 | Calendar | Company Commander (`view_all=True` but also company-assigned) was wrongly scoped to only their own company's events — the queryset checked "has a company_id" instead of the actual `view_all` capability. | Scoping now keyed off `caps(user).get("view_all")`. |
| 9 | Calendar | Any authenticated user could create/edit a **battalion-wide** event, and an invalid `?date=` param caused an unhandled 500. | Added scope-aware write permission (`view_all`/`edit_all` for battalion scope, own-company match for company scope); invalid dates now return an empty result instead of crashing. |
| 10 | Messaging | Broadcast conversations were supposed to be readable by "everyone" per the model's own docstring, but the queryset only returned conversations you were already a participant in — **broadcasts were invisible to almost everyone.** | `get_queryset()` now includes all broadcasts regardless of participation; broadcast creation auto-enrolls every active user. |
| 11 | Messaging | Any authenticated user could create a "company group" for a company that wasn't their own, auto-enrolling everyone in that company into a conversation an outsider controls. | Company-group creation now requires the creator's own company to match, or `edit_all`. |
| 12 | Documents | **Recurrence of the create-time bug from the first review pass, in a third module:** `perform_create` never checked whether the target `folder_id` in the upload payload was actually a folder the uploader could see — a role-restricted folder's contents could be added to directly, bypassing the visibility control entirely. Also, Folder create/edit (including setting `restricted_to_roles`, which controls access to everything inside) had no permission gate at all. | Folder create/update/delete restricted to command-level roles; document upload now explicitly validates the target folder is in the uploader's accessible set before saving. |
| 13 | Maps, Operations | Same missing-write-gate pattern as the Personnel/Company bugs from the first review pass — any authenticated user, including Read Only, could upload maps or author operational orders. | Both restricted to `view_all`/`edit_all` roles for writes; reads stay open. |

Flagging the pattern directly: **"filter the read queryset by access, forget to also gate the create/write path" is the single most common bug shape across this whole project** — it's now shown up five separate times (Personnel create, Documents upload, Operations, Maps, and the Messaging company-group case is a variant of the same root cause). If more modules get added later, that's the first thing to check.

## 2. Modules added in the previous pass (for reference)

| Module | What it covers |
|---|---|
| **Standalone Course management** | Previously only a read-only nested list inside a personnel profile. Now a full `CourseViewSet` with the same CRUD depth as Leave. |
| **Directory** | Read-only, searchable-by-rank-and-company contact list, deliberately exposing only name/rank/appointment/mobile — not the full Personnel record. |
| **Calendar & Events** | Training/Conference/Parade/Inspection/Leave event types, battalion-wide or company-scoped, daily/weekly/monthly view filtering, reminder window. |
| **Internal Messaging** | 1:1 DMs, group chats, auto-populated company groups, battalion broadcasts (posting restricted to `approve_notice` roles), attachments, read receipts. |
| **Maps** | Geo-tagged (lat/lng bounding box) file upload, offline-availability flag. |
| **Operations** | Area Information / Operational Orders / Reference Material / Situation Reports as one role-restricted model (see rationale in the model docstring). |
| **Reports / MIS Dashboard** | Real trend data needs data points over time, which live tables can't give you once records change — added a `DailySnapshot` model plus `take_daily_snapshot` management command (run nightly) and a `/api/mis/trends/` endpoint restricted to command-level roles. |
| **Self-service password reset** | Previously only admin-initiated reset existed. Added a token-based request/confirm flow (`RequestPasswordResetView`/`ConfirmPasswordResetView`). On a closed LAN with no guaranteed SMTP, it falls back to logging the token for the Adjutant to relay in person — documented in the code, not hidden. |

## 3. Project tree

```
battalion-portal/
├── docker-compose.yml
├── deploy/
│   ├── nginx.conf
│   ├── certs/generate-self-signed.sh   # LAN-only cert so nginx has something to start with
│   ├── backup.sh
│   └── restore.sh
├── frontend/
│   └── index.html                       # still mock data — see Limitations
└── backend/
    ├── config/{urls,wsgi}.py, settings/{base,development,production}.py
    └── apps/
        ├── accounts/     companies/   personnel/    leave/       training/
        ├── weapons/      vehicles/    inventory/    notices/     documents/
        ├── dashboard/    directory/   calendar_app/ messaging/   maps/
        ├── operations/   mis/
```
17 apps total, all registered in `INSTALLED_APPS` and routed in `config/urls.py` — verified by cross-checking the app directory listing against both (see review notes above).

## 4. Test credentials

```
docker compose exec backend python manage.py migrate
docker compose exec backend python manage.py seed_demo_data
```
Creates one account per role, password **`Battalion@2026`** (forced change on first login): `admin` (Adjutant), `bn_comd`, `2ic`, `coy_comd_a`, `jco_a`, `clerk_a`, `store_ic`, `vehicle_ic`, `training_nco`, `viewer`. Run `createsuperuser` separately for real Django-admin access.

## 5. API documentation (summary)

Base path `/api/`. Bearer JWT auth on everything except login/refresh/password-reset-request/password-reset-confirm.

**Auth:** `/auth/login/`, `/auth/logout/`, `/auth/token/refresh/`, `/auth/change-password/`, `/auth/password-reset/request/`, `/auth/password-reset/confirm/`, `/auth/users/<id>/reset-password/` (Adjutant only), `/auth/permission-matrix/`

**Core CRUD:** `/users/`, `/personnel/` (+ `/profile/`, `/export_pdf/`, `/export_excel/`), `/courses/`, `/companies/` (+ `/state/`), `/platoons/`, `/sections/`, `/leave-applications/` (+ `/approve/`, `/reject/`, `/cancel/`), `/weapons/` (+ `/issue/`, `/returned/`, `/dashboard/`), `/vehicles/` (+ `/dashboard/`, `/fuel_consumption_report/`), `/fuel-records/`, `/vehicle-service-records/`, `/inventory-items/` (+ `/shortage_report/`, `/<id>/ledger/`), `/stock-movements/`, `/vendors/`, `/notices/` (+ `/publish/`, `/archive/`), `/folders/`, `/documents/` (+ `/new_version/`, `/versions/`, `/search/`)

**Training:** `/training/firing-records/`, `/pt-records/`, `/battle-sessions/`, `/competitions/`, `/attendance/`, `/training/reports/{individual,company,battalion}/`

**New this pass:** `/directory/`, `/calendar-events/` (`?view=daily|weekly|monthly&date=`, + `/upcoming_reminders/`), `/conversations/` (+ `/send_message/`, `/messages/`, `/mark_read/`), `/maps/`, `/operations/`, `/mis/trends/?days=90`

**Dashboard:** `GET /api/dashboard/`

## 6. Deployment instructions

```
docker compose up -d --build
docker compose exec backend python manage.py migrate
docker compose exec backend python manage.py createsuperuser
docker compose exec backend python manage.py seed_demo_data
```
Generate a cert first: `bash deploy/certs/generate-self-signed.sh` (or drop your unit's real cert in `deploy/certs/`) — nginx won't start without one. Schedule `take_daily_snapshot` nightly (Windows Task Scheduler → WSL2) or MIS trends will stay empty. `deploy/backup.sh` similarly wants a nightly slot.

## 7. Remaining limitations — still honest, not glossed over

- **Frontend is still not wired to any of this.** `frontend/index.html` remains the original mock-data dashboard. This is the single biggest gap and hasn't shrunk across three passes now — every widget needs pointing at a real endpoint, plus an actual login screen. This is my standing recommendation for what to do next.
- **Nextcloud integration is not implemented** — Documents is a self-contained Django module, not a WebDAV/Nextcloud client.
- **No automated tests, nothing run against a live database.**
- **MIS trend data starts empty** until `take_daily_snapshot` has been running — no way to backfill history that was never captured.
- **Weapons has no dedicated Incharge role** in your 10-role list — readable by all, writable by Adjutant/superuser only. Flagged as a design choice across all three passes; still open if you want it changed.
- **Messaging has no real-time delivery** — request/response API only (send, list, mark-read); a production chat feature would want WebSockets, not wired up.
- **This pass focused specifically on write-permission gaps** (the "queryset filters reads but not writes" pattern documented in §1b) across the six newest modules, plus a few crash-on-bad-input cases. It was not a from-scratch re-audit of the 11 modules covered in the first two passes — those were reviewed then and not revisited now. If a specific module is about to hold real personnel data, worth naming it for one more targeted pass before trusting it.
