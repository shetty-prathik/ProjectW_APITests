# Attendance Module — Functional Test Scenarios for Automation

**Source:** Project_W_BE attendance module analysis  
**Scope:** All attendance endpoints except shifts  
**Base path:** `POST/GET/PUT /accounts/api/attendances/...`  
**Auth:** All endpoints require `Authorization: Bearer <token>`

---

## Module Overview

The attendance module manages the complete employee attendance lifecycle:

```
Check-In → [Break Start → Break End] (repeatable) → Check-Out
                    ↓
         Auto-Checkout Job (cron, background)
                    ↓
         Regularization Request → Admin Approve/Reject
                    ↓
         Admin Edit / Admin Create / Admin Approve
```

Supporting sub-modules: **Geofences**, **Attendance Settings**, **Audit Logs**

---

## Endpoint Reference

| # | Method | Path | Description |
|---|---|---|---|
| 1 | POST | `/checkin` | Employee check-in |
| 2 | POST | `/checkout` | Employee check-out |
| 3 | POST | `/break/start` | Start a break |
| 4 | POST | `/break/end` | End a break |
| 5 | GET | `/today` | Today's attendance summary |
| 6 | GET | `/history` | Historical attendance records |
| 7 | GET | `/settings` | Get user attendance settings |
| 8 | PUT | `/settings` | Update user attendance settings |
| 9 | POST | `/regularization` | Submit a correction request |
| 10 | GET | `/regularization` | List correction requests |
| 11 | PUT | `/regularization/:id/approve` | Admin approve/reject correction |
| 12 | PUT | `/:id/approve` | Admin approve attendance record |
| 13 | PUT | `/:id/admin-edit` | Admin edit attendance record |
| 14 | POST | `/admin-create/:user_id` | Admin create attendance for absent day |

---

## Section 1 — Check-In

### Happy Path

---

**AT-CI-001** — Successful check-in without geofence  
**Endpoint:** `POST /checkin`  
**Priority:** P0 — Blocker  
**Preconditions:** User is authenticated; user has no active attendance session  
**Request Body:**
```json
{
  "timezone": "Asia/Kolkata"
}
```
**Expected Response:** HTTP 200  
```json
{
  "code": 200,
  "data": {
    "_id": "<objectId>",
    "user_id": "<userId>",
    "status": "in_progress",
    "check_in_at": "<timestamp>",
    "check_out_at": null,
    "eid": "<eid>",
    "timezone": "Asia/Kolkata",
    "flags": {
      "geofence_check_in_valid": null
    }
  }
}
```
**Assertions:**
- HTTP status = 200
- `data._id` is not null
- `data.status` = `"in_progress"`
- `data.check_in_at` is a valid ISO timestamp
- `data.check_out_at` is null
- `data.eid` matches the authenticated user's enterprise

---

**AT-CI-002** — Successful check-in with valid location inside circular geofence  
**Endpoint:** `POST /checkin`  
**Priority:** P1 — Critical  
**Preconditions:** User has an active circular geofence assigned; user is not checked in  
**Request Body:**
```json
{
  "timezone": "Asia/Kolkata",
  "latitude": 19.0596,
  "longitude": 72.8295
}
```
**Expected Response:** HTTP 200  
**Assertions:**
- `data.status` = `"in_progress"`
- `data.flags.geofence_check_in_valid` = `true`
- `data.flags.emergency_check_in` = `false`

---

**AT-CI-003** — Successful check-in with valid location inside polygonal geofence  
**Endpoint:** `POST /checkin`  
**Priority:** P1 — Critical  
**Preconditions:** User has an active polygonal geofence assigned; coordinates inside polygon  
**Request Body:**
```json
{
  "timezone": "Asia/Kolkata",
  "latitude": 19.0595,
  "longitude": 72.8295
}
```
**Expected Response:** HTTP 200  
**Assertions:**
- `data.flags.geofence_check_in_valid` = `true`

---

**AT-CI-004** — Emergency check-in outside geofence when `allow_outside_check_in = true`  
**Endpoint:** `POST /checkin`  
**Priority:** P1 — Critical  
**Preconditions:** User has geofence with `rules.allow_outside_check_in = true`; user's location is outside the geofence  
**Request Body:**
```json
{
  "timezone": "Asia/Kolkata",
  "latitude": 28.6139,
  "longitude": 77.2090
}
```
**Expected Response:** HTTP 200  
**Assertions:**
- `data.status` = `"in_progress"`
- `data.flags.geofence_check_in_valid` = `false`
- `data.flags.emergency_check_in` = `true`

---

**AT-CI-005** — Check-in with source metadata (IP, device ID, GPS)  
**Endpoint:** `POST /checkin`  
**Priority:** P2 — Normal  
**Preconditions:** User is not checked in  
**Request Body:**
```json
{
  "timezone": "Asia/Kolkata",
  "latitude": 19.0596,
  "longitude": 72.8295,
  "ip": "192.168.1.100",
  "device_id": "device-abc-001",
  "location_name": "Factory Floor A"
}
```
**Expected Response:** HTTP 200  
**Assertions:**
- `data.source.check_in_ip` = `"192.168.1.100"`
- `data.source.device_id` = `"device-abc-001"`
- `data.location_name` = `"Factory Floor A"`

---

### Negative / Validation Scenarios

---

**AT-CI-006** — Double check-in returns 400  
**Endpoint:** `POST /checkin`  
**Priority:** P0 — Blocker  
**Preconditions:** User is already checked in (active session exists)  
**Request Body:** `{ "timezone": "Asia/Kolkata" }`  
**Expected Response:** HTTP 400  
**Assertions:**
- HTTP status = 400
- `message` contains `"already"` (case-insensitive)
- `data` is null

---

**AT-CI-007** — Check-in blocked when outside geofence and `allow_outside_check_in = false`  
**Endpoint:** `POST /checkin`  
**Priority:** P1 — Critical  
**Preconditions:** User has geofence with `rules.allow_outside_check_in = false`; user's location is outside  
**Request Body:**
```json
{
  "timezone": "Asia/Kolkata",
  "latitude": 28.6139,
  "longitude": 77.2090
}
```
**Expected Response:** HTTP 403  
**Assertions:**
- HTTP status = 403
- `message` contains `"outside"` or `"not allowed"` (case-insensitive)
- `data.reason` is not null

---

**AT-CI-008** — Check-in blocked by checkout-to-checkin gap enforcement  
**Endpoint:** `POST /checkin`  
**Priority:** P1 — Critical  
**Preconditions:** User checked out less than `checkout_to_checkin_gap_minutes` ago (e.g., 60 min gap configured, checked out 10 min ago)  
**Request Body:** `{ "timezone": "Asia/Kolkata" }`  
**Expected Response:** HTTP 400  
**Assertions:**
- HTTP status = 400
- `message` contains `"wait"` or `"gap"` (case-insensitive)
- `data.remaining_minutes` > 0
- `data.required_gap_minutes` matches configured gap

---

**AT-CI-009** — Check-in without auth token returns 401  
**Endpoint:** `POST /checkin`  
**Priority:** P0 — Blocker  
**Preconditions:** No Authorization header  
**Expected Response:** HTTP 401  
**Assertions:** HTTP status = 401

---

**AT-CI-010** — Check-in with missing timezone defaults to Asia/Kolkata  
**Endpoint:** `POST /checkin`  
**Priority:** P2 — Normal  
**Preconditions:** User is not checked in  
**Request Body:** `{}`  
**Expected Response:** HTTP 200  
**Assertions:**
- HTTP status = 200
- `data.timezone` = `"Asia/Kolkata"`

---

### Stale Session Handling

---

**AT-CI-011** — Stale session (>24h) auto-closed on new check-in  
**Endpoint:** `POST /checkin`  
**Priority:** P1 — Critical  
**Preconditions:** User has an active attendance with `check_in_at` more than 24 hours ago (simulate by directly inserting a stale record in DB, or use a test user with a pre-seeded stale record)  
**Request Body:** `{ "timezone": "Asia/Kolkata" }`  
**Expected Response:** HTTP 200 (new check-in succeeds)  
**Assertions:**
- HTTP status = 200
- New attendance record is created with `status = "in_progress"`
- Old stale record: `status = "auto_checked_out_expired"`, `flags.auto_checked_out = true`, `check_out_at` is set
- An `AttendanceLog` with type `"auto_check_out_expired"` exists for the stale record

---

## Section 2 — Check-Out

### Happy Path

---

**AT-CO-001** — Successful check-out after check-in  
**Endpoint:** `POST /checkout`  
**Priority:** P0 — Blocker  
**Preconditions:** User is checked in (active session)  
**Request Body:**
```json
{
  "timezone": "Asia/Kolkata"
}
```
**Expected Response:** HTTP 200  
**Assertions:**
- HTTP status = 200
- `data.check_out_at` is a valid ISO timestamp
- `data.status` = `"present"`
- `data.total_work_minutes` ≥ 0
- `data.regular_minutes` ≥ 0
- `data.overtime_minutes` ≥ 0
- `data.total_work_minutes` = `data.regular_minutes` + `data.overtime_minutes`

---

**AT-CO-002** — Check-out calculates work minutes correctly  
**Endpoint:** `POST /checkout`  
**Priority:** P1 — Critical  
**Preconditions:** User checked in exactly 60 minutes ago; no breaks taken; shift has `overtime_after_minutes = 480`  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.total_work_minutes` ≈ 60 (±2 min tolerance for test execution time)
- `data.regular_minutes` = `data.total_work_minutes` (no overtime since < 480 min)
- `data.overtime_minutes` = 0

---

**AT-CO-003** — Check-out deducts break time from work minutes  
**Endpoint:** `POST /checkout`  
**Priority:** P1 — Critical  
**Preconditions:** User checked in 120 minutes ago; took one 30-minute break (break started and ended)  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.break_minutes` = 30
- `data.total_work_minutes` ≈ 120
- Net work = `total_work_minutes - break_minutes` ≈ 90 min
- `data.regular_minutes` ≈ 90

---

**AT-CO-004** — Check-out auto-closes any open break  
**Endpoint:** `POST /checkout`  
**Priority:** P1 — Critical  
**Preconditions:** User is checked in; user started a break but did NOT end it  
**Expected Response:** HTTP 200  
**Assertions:**
- HTTP status = 200 (checkout succeeds despite open break)
- `data.breaks[last].end_at` is not null
- `data.breaks[last].auto_closed` = `true`
- `data.breaks[last].duration_minutes` > 0

---

**AT-CO-005** — Check-out with source metadata  
**Endpoint:** `POST /checkout`  
**Priority:** P2 — Normal  
**Preconditions:** User is checked in  
**Request Body:**
```json
{
  "timezone": "Asia/Kolkata",
  "latitude": 19.0596,
  "longitude": 72.8295,
  "ip": "192.168.1.101",
  "device_id": "device-abc-001"
}
```
**Expected Response:** HTTP 200  
**Assertions:**
- `data.source.check_out_ip` = `"192.168.1.101"`

---

**AT-CO-006** — Check-out inside geofence sets `geofence_check_out_valid = true`  
**Endpoint:** `POST /checkout`  
**Priority:** P1 — Critical  
**Preconditions:** User is checked in; user has an active geofence; checkout coordinates are inside geofence  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.flags.geofence_check_out_valid` = `true`
- `data.flags.emergency_check_out` = `false`

---

**AT-CO-007** — Check-out outside geofence when `allow_outside_check_out = true` (emergency)  
**Endpoint:** `POST /checkout`  
**Priority:** P1 — Critical  
**Preconditions:** User has geofence with `allow_outside_check_out = true`; checkout coordinates outside geofence  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.flags.geofence_check_out_valid` = `false`
- `data.flags.emergency_check_out` = `true`

---

**AT-CO-008** — Overtime calculated correctly when work exceeds threshold  
**Endpoint:** `POST /checkout`  
**Priority:** P1 — Critical  
**Preconditions:** User checked in 600 minutes ago; shift has `overtime_after_minutes = 480`; no breaks  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.total_work_minutes` ≈ 600
- `data.regular_minutes` = 480
- `data.overtime_minutes` ≈ 120

---

### Negative / Validation Scenarios

---

**AT-CO-009** — Check-out without prior check-in returns 400  
**Endpoint:** `POST /checkout`  
**Priority:** P0 — Blocker  
**Preconditions:** User has no active attendance session  
**Request Body:** `{ "timezone": "Asia/Kolkata" }`  
**Expected Response:** HTTP 400  
**Assertions:**
- HTTP status = 400
- `message` contains `"not checked in"` (case-insensitive)

---

**AT-CO-010** — Check-out blocked when outside geofence and `allow_outside_check_out = false`  
**Endpoint:** `POST /checkout`  
**Priority:** P1 — Critical  
**Preconditions:** User is checked in; geofence has `allow_outside_check_out = false`; checkout coordinates outside  
**Expected Response:** HTTP 403  
**Assertions:**
- HTTP status = 403
- `message` contains `"outside"` or `"not allowed"` (case-insensitive)

---

**AT-CO-011** — Check-out blocked when session exceeds max shift length  
**Endpoint:** `POST /checkout`  
**Priority:** P1 — Critical  
**Preconditions:** User has `max_shift_length_hours = 12` in settings; user checked in 13 hours ago  
**Expected Response:** HTTP 400  
**Assertions:**
- HTTP status = 400
- `data.hours_since_check_in` > `data.max_allowed_hours`

---

**AT-CO-012** — Check-out without auth token returns 401  
**Endpoint:** `POST /checkout`  
**Priority:** P0 — Blocker  
**Expected Response:** HTTP 401

---

---

## Section 3 — Break Management

### Break Start

---

**AT-BK-001** — Start break while checked in returns 200  
**Endpoint:** `POST /break/start`  
**Priority:** P1 — Critical  
**Preconditions:** User is checked in with `status = "in_progress"`; no active break  
**Request Body:** `{}`  
**Expected Response:** HTTP 200  
**Assertions:**
- HTTP status = 200
- `data.breaks` array has a new entry
- Last break entry: `start_at` is set, `end_at` is null, `duration_minutes` = 0, `auto_closed` = false

---

**AT-BK-002** — Double break start returns 400  
**Endpoint:** `POST /break/start`  
**Priority:** P1 — Critical  
**Preconditions:** User is checked in; user already started a break (break is active)  
**Expected Response:** HTTP 400  
**Assertions:**
- HTTP status = 400
- `message` contains `"already on break"` (case-insensitive)

---

**AT-BK-003** — Break start without check-in returns 400  
**Endpoint:** `POST /break/start`  
**Priority:** P1 — Critical  
**Preconditions:** User has no active attendance session  
**Expected Response:** HTTP 400  
**Assertions:**
- HTTP status = 400

---

**AT-BK-004** — Break start without auth token returns 401  
**Endpoint:** `POST /break/start`  
**Priority:** P0 — Blocker  
**Expected Response:** HTTP 401

---

### Break End

---

**AT-BK-005** — End break calculates duration correctly  
**Endpoint:** `POST /break/end`  
**Priority:** P1 — Critical  
**Preconditions:** User is checked in; user started a break at least 1 minute ago  
**Request Body:** `{}`  
**Expected Response:** HTTP 200  
**Assertions:**
- HTTP status = 200
- Last break entry: `end_at` is set, `duration_minutes` ≥ 1
- `data.break_minutes` = sum of all break `duration_minutes`

---

**AT-BK-006** — End break without active break returns 400  
**Endpoint:** `POST /break/end`  
**Priority:** P1 — Critical  
**Preconditions:** User is checked in but has no active break (either no breaks, or last break already ended)  
**Expected Response:** HTTP 400  
**Assertions:**
- HTTP status = 400
- `message` contains `"no active break"` (case-insensitive)

---

**AT-BK-007** — End break without check-in returns 400  
**Endpoint:** `POST /break/end`  
**Priority:** P1 — Critical  
**Preconditions:** User has no active attendance session  
**Expected Response:** HTTP 400

---

**AT-BK-008** — Multiple breaks in a single session accumulate correctly  
**Endpoint:** `POST /break/end` (after multiple start/end cycles)  
**Priority:** P2 — Normal  
**Preconditions:** User is checked in; user has completed 2 breaks (e.g., 5 min + 10 min)  
**Expected Response:** HTTP 200 (on second break end)  
**Assertions:**
- `data.breaks` array has 2 entries
- Both entries have `end_at` set
- `data.break_minutes` = sum of both break durations (≈ 15 min)

---

**AT-BK-009** — Break end without auth token returns 401  
**Endpoint:** `POST /break/end`  
**Priority:** P0 — Blocker  
**Expected Response:** HTTP 401

---

---

## Section 4 — Today's Attendance Summary

---

**AT-TD-001** — Get today's summary while checked in shows active session  
**Endpoint:** `GET /today`  
**Priority:** P1 — Critical  
**Preconditions:** User is currently checked in  
**Query Params:** `timezone=Asia/Kolkata`  
**Expected Response:** HTTP 200  
**Assertions:**
- HTTP status = 200
- `data.current_session` is not null
- `data.current_session.status` = `"in_progress"`
- `data.current_session.check_out_at` is null
- `data.total_work_minutes` ≥ 0 (includes elapsed time of active session)
- `data.sessions_completed` = 0 (no completed sessions yet)

---

**AT-TD-002** — Get today's summary after check-out shows completed session  
**Endpoint:** `GET /today`  
**Priority:** P1 — Critical  
**Preconditions:** User checked in and checked out today  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.current_session` is null
- `data.sessions_completed` = 1
- `data.total_work_minutes` > 0
- `data.regular_minutes` ≥ 0
- `data.overtime_minutes` ≥ 0
- `data.break_minutes` ≥ 0

---

**AT-TD-003** — Get today's summary with no activity returns empty summary  
**Endpoint:** `GET /today`  
**Priority:** P2 — Normal  
**Preconditions:** User has no attendance records for today  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.current_session` is null
- `data.total_work_minutes` = 0
- `data.sessions_completed` = 0

---

**AT-TD-004** — Get today's summary without auth token returns 401  
**Endpoint:** `GET /today`  
**Priority:** P0 — Blocker  
**Expected Response:** HTTP 401

---

**AT-TD-005** — Today's summary includes overnight session from previous day  
**Endpoint:** `GET /today`  
**Priority:** P2 — Normal  
**Preconditions:** User checked in yesterday evening and checked out today morning (overnight shift)  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.all_sessions` contains the overnight record
- `data.total_work_minutes` includes the overnight session's work time

---

---

## Section 5 — Attendance History

---

**AT-HI-001** — Get history returns list of past records  
**Endpoint:** `GET /history`  
**Priority:** P1 — Critical  
**Preconditions:** User has at least one completed attendance record  
**Query Params:** `timezone=Asia/Kolkata`  
**Expected Response:** HTTP 200  
**Assertions:**
- HTTP status = 200
- `data.attendances` is an array
- Each record has `_id`, `check_in_at`, `status`, `total_work_minutes`
- Records sorted by `check_in_at` descending

---

**AT-HI-002** — Filter history by date range  
**Endpoint:** `GET /history`  
**Priority:** P1 — Critical  
**Preconditions:** User has records spanning multiple days  
**Query Params:** `start_date=<epoch_ms_7_days_ago>&end_date=<epoch_ms_today>&timezone=Asia/Kolkata`  
**Expected Response:** HTTP 200  
**Assertions:**
- All returned records have `date` within the specified range
- Records outside the range are not included

---

**AT-HI-003** — Filter history by `use_work_date = true`  
**Endpoint:** `GET /history`  
**Priority:** P2 — Normal  
**Preconditions:** User has overnight shift records where `work_date` differs from `date`  
**Query Params:** `use_work_date=true&start_date=<epoch>&end_date=<epoch>&timezone=Asia/Kolkata`  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.date_field_used` = `"work_date"`
- Records are filtered on `work_date`, not `date`

---

**AT-HI-004** — History response includes `grouped_by_work_day` map  
**Endpoint:** `GET /history`  
**Priority:** P2 — Normal  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.grouped_by_work_day` is an object
- Keys are date strings in `YYYY-MM-DD` format
- Values are arrays of attendance records for that work day

---

**AT-HI-005** — Admin can fetch history for another user  
**Endpoint:** `GET /history`  
**Priority:** P1 — Critical  
**Preconditions:** Authenticated user has `role.hierarchy >= 80` (Super Admin / Admin); target user has records  
**Query Params:** `user_id=<targetUserId>&timezone=Asia/Kolkata`  
**Expected Response:** HTTP 200  
**Assertions:**
- All returned records have `user_id` = target user's ID

---

**AT-HI-006** — Get history without auth token returns 401  
**Endpoint:** `GET /history`  
**Priority:** P0 — Blocker  
**Expected Response:** HTTP 401

---

---

## Section 6 — Attendance Settings

---

**AT-ST-001** — Get settings returns 200 with default or existing settings  
**Endpoint:** `GET /settings`  
**Priority:** P1 — Critical  
**Expected Response:** HTTP 200  
**Assertions:**
- HTTP status = 200
- `data.auto_checkout_grace_minutes` is a number
- `data.required_daily_hours_minutes` is a number
- `data.timezone` is a string

---

**AT-ST-002** — Update `auto_checkout_grace_minutes` setting  
**Endpoint:** `PUT /settings`  
**Priority:** P1 — Critical  
**Request Body:** `{ "auto_checkout_grace_minutes": 45 }`  
**Expected Response:** HTTP 200  
**Assertions:**
- HTTP status = 200
- `data.auto_checkout_grace_minutes` = 45

---

**AT-ST-003** — Update `required_daily_hours_minutes` setting  
**Endpoint:** `PUT /settings`  
**Priority:** P1 — Critical  
**Request Body:** `{ "required_daily_hours_minutes": 480 }`  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.required_daily_hours_minutes` = 480

---

**AT-ST-004** — Update `checkout_to_checkin_gap_minutes` setting  
**Endpoint:** `PUT /settings`  
**Priority:** P2 — Normal  
**Request Body:** `{ "checkout_to_checkin_gap_minutes": 60 }`  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.checkout_to_checkin_gap_minutes` = 60

---

**AT-ST-005** — Update `max_shift_length_hours` setting  
**Endpoint:** `PUT /settings`  
**Priority:** P2 — Normal  
**Request Body:** `{ "max_shift_length_hours": 14 }`  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.max_shift_length_hours` = 14

---

**AT-ST-006** — Update `timezone` setting  
**Endpoint:** `PUT /settings`  
**Priority:** P2 — Normal  
**Request Body:** `{ "timezone": "UTC" }`  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.timezone` = `"UTC"`

---

**AT-ST-007** — Update multiple settings in one call  
**Endpoint:** `PUT /settings`  
**Priority:** P2 — Normal  
**Request Body:**
```json
{
  "auto_checkout_grace_minutes": 30,
  "required_daily_hours_minutes": 510,
  "break_auto_close_minutes": 60,
  "allow_overnight_shifts": true,
  "notifications": {
    "auto_checkout_warning": true,
    "break_reminders": false
  }
}
```
**Expected Response:** HTTP 200  
**Assertions:**
- All updated fields reflect the new values in the response

---

**AT-ST-008** — Get settings without auth token returns 401  
**Endpoint:** `GET /settings`  
**Priority:** P0 — Blocker  
**Expected Response:** HTTP 401

---

**AT-ST-009** — Updated settings affect subsequent check-in gap enforcement  
**Endpoint:** `PUT /settings` then `POST /checkin`  
**Priority:** P1 — Critical  
**Preconditions:** User sets `checkout_to_checkin_gap_minutes = 120`; user checks out; user immediately tries to check in  
**Expected Response (check-in):** HTTP 400  
**Assertions:**
- Check-in blocked with `data.required_gap_minutes` = 120

---

---

## Section 7 — Regularization Requests

---

**AT-RG-001** — Create regularization request for own attendance  
**Endpoint:** `POST /regularization`  
**Priority:** P1 — Critical  
**Preconditions:** User has a completed attendance record  
**Request Body:**
```json
{
  "attendance_id": "<attendanceId>",
  "reason": "Forgot to check in on time due to system issue",
  "requested_changes": {
    "check_in_at": "2025-06-01T09:00:00.000Z",
    "check_out_at": "2025-06-01T18:00:00.000Z"
  }
}
```
**Expected Response:** HTTP 200  
**Assertions:**
- HTTP status = 200
- `data.status` = `"pending"`
- `data.user_id` = authenticated user's ID
- `data.attendance_id` = provided attendance ID
- `data.reason` = provided reason

---

**AT-RG-002** — Cannot create duplicate pending request for same attendance  
**Endpoint:** `POST /regularization`  
**Priority:** P1 — Critical  
**Preconditions:** A pending regularization request already exists for the attendance  
**Request Body:** Same attendance_id as existing pending request  
**Expected Response:** HTTP 400  
**Assertions:**
- HTTP status = 400
- `message` contains `"already pending"` (case-insensitive)

---

**AT-RG-003** — Cannot create regularization request for another user's attendance  
**Endpoint:** `POST /regularization`  
**Priority:** P1 — Critical  
**Preconditions:** `attendance_id` belongs to a different user  
**Expected Response:** HTTP 400 or 403  
**Assertions:**
- HTTP status = 400 or 403 (ownership check fails)

---

**AT-RG-004** — Regularization request with only break changes  
**Endpoint:** `POST /regularization`  
**Priority:** P2 — Normal  
**Request Body:**
```json
{
  "attendance_id": "<attendanceId>",
  "reason": "Break duration was recorded incorrectly",
  "requested_changes": {
    "breaks": [
      {
        "start_at": "2025-06-01T13:00:00.000Z",
        "end_at": "2025-06-01T13:30:00.000Z",
        "duration_minutes": 30
      }
    ]
  }
}
```
**Expected Response:** HTTP 200  
**Assertions:**
- `data.requested_changes.breaks` has 1 entry

---

**AT-RG-005** — Get regularization requests returns list for current user  
**Endpoint:** `GET /regularization`  
**Priority:** P1 — Critical  
**Preconditions:** At least one regularization request exists for the user  
**Expected Response:** HTTP 200  
**Assertions:**
- HTTP status = 200
- `data` is an array
- All records have `user_id` = authenticated user's ID
- Records sorted by `createdAt` descending

---

**AT-RG-006** — Approve regularization request updates attendance record  
**Endpoint:** `PUT /regularization/:id/approve`  
**Priority:** P1 — Critical  
**Preconditions:** A pending regularization request exists; caller has admin role  
**Request Body:** `{ "status": "approved" }`  
**Expected Response:** HTTP 200  
**Assertions:**
- HTTP status = 200
- `data.status` = `"approved"`
- `data.approved_by` = admin user's ID
- `data.approved_at` is set
- The linked attendance record: `status = "manually_adjusted"`, `flags.is_manual_edit = true`
- The attendance record's `check_in_at` / `check_out_at` / `breaks` reflect the requested changes

---

**AT-RG-007** — Reject regularization request with reason  
**Endpoint:** `PUT /regularization/:id/approve`  
**Priority:** P1 — Critical  
**Preconditions:** A pending regularization request exists  
**Request Body:**
```json
{
  "status": "rejected",
  "rejection_reason": "Insufficient evidence provided"
}
```
**Expected Response:** HTTP 200  
**Assertions:**
- `data.status` = `"rejected"`
- `data.rejection_reason` = `"Insufficient evidence provided"`
- `data.approved_by` is set
- The linked attendance record is NOT modified

---

**AT-RG-008** — Cannot process an already-approved request  
**Endpoint:** `PUT /regularization/:id/approve`  
**Priority:** P1 — Critical  
**Preconditions:** The regularization request has `status = "approved"`  
**Request Body:** `{ "status": "rejected" }`  
**Expected Response:** HTTP 400  
**Assertions:**
- HTTP status = 400
- `message` contains `"already processed"` (case-insensitive)

---

**AT-RG-009** — Cannot process an already-rejected request  
**Endpoint:** `PUT /regularization/:id/approve`  
**Priority:** P1 — Critical  
**Preconditions:** The regularization request has `status = "rejected"`  
**Request Body:** `{ "status": "approved" }`  
**Expected Response:** HTTP 400  
**Assertions:**
- HTTP status = 400

---

**AT-RG-010** — Get regularization requests without auth returns 401  
**Endpoint:** `GET /regularization`  
**Priority:** P0 — Blocker  
**Expected Response:** HTTP 401

---

---

## Section 8 — Admin Operations

### Admin Edit

---

**AT-AE-001** — Admin can edit check-in time  
**Endpoint:** `PUT /:id/admin-edit`  
**Priority:** P1 — Critical  
**Preconditions:** Caller has `role.hierarchy >= 80` (Super Admin / Plant Manager); attendance record exists  
**Request Body:**
```json
{
  "check_in_at": "2025-06-01T08:30:00.000Z"
}
```
**Expected Response:** HTTP 200  
**Assertions:**
- HTTP status = 200
- `data.check_in_at` = `"2025-06-01T08:30:00.000Z"`
- `data.status` = `"manually_adjusted"`
- `data.flags.is_manual_edit` = `true`

---

**AT-AE-002** — Admin can edit check-out time and work minutes are recalculated  
**Endpoint:** `PUT /:id/admin-edit`  
**Priority:** P1 — Critical  
**Preconditions:** Admin role; attendance has both check-in and check-out  
**Request Body:**
```json
{
  "check_in_at": "2025-06-01T09:00:00.000Z",
  "check_out_at": "2025-06-01T18:00:00.000Z"
}
```
**Expected Response:** HTTP 200  
**Assertions:**
- `data.total_work_minutes` ≈ 540 (9 hours)
- `data.regular_minutes` ≤ 540
- `data.overtime_minutes` ≥ 0
- `data.status` = `"manually_adjusted"`

---

**AT-AE-003** — Admin edit creates audit log entry  
**Endpoint:** `PUT /:id/admin-edit`  
**Priority:** P1 — Critical  
**Preconditions:** Admin role; attendance exists  
**Request Body:** `{ "check_in_at": "2025-06-01T09:15:00.000Z" }`  
**Expected Response:** HTTP 200  
**Assertions:**
- An `AttendanceAuditLog` document is created with:
  - `action` = `"admin_edit"`
  - `admin_user_id` = admin's user ID
  - `changes` array contains an entry for `check_in_at` with `old_value` and `new_value`

---

**AT-AE-004** — Non-admin user cannot edit attendance  
**Endpoint:** `PUT /:id/admin-edit`  
**Priority:** P1 — Critical  
**Preconditions:** Caller has a regular employee role (hierarchy < 80)  
**Expected Response:** HTTP 403  
**Assertions:**
- HTTP status = 403

---

**AT-AE-005** — Admin edit on non-existent attendance returns 404  
**Endpoint:** `PUT /:id/admin-edit`  
**Priority:** P2 — Normal  
**Preconditions:** Admin role; ID does not exist  
**Expected Response:** HTTP 404 or 409  
**Assertions:**
- HTTP status = 404 or 409

---

**AT-AE-006** — Admin can edit breaks array  
**Endpoint:** `PUT /:id/admin-edit`  
**Priority:** P2 — Normal  
**Request Body:**
```json
{
  "breaks": [
    {
      "start_at": "2025-06-01T13:00:00.000Z",
      "end_at": "2025-06-01T13:45:00.000Z",
      "duration_minutes": 45
    }
  ]
}
```
**Expected Response:** HTTP 200  
**Assertions:**
- `data.breaks` has 1 entry with `duration_minutes` = 45
- `data.break_minutes` = 45

---

**AT-AE-007** — Admin edit recalculates `date` field from new check-in time  
**Endpoint:** `PUT /:id/admin-edit`  
**Priority:** P2 — Normal  
**Request Body:** `{ "check_in_at": "2025-06-02T09:00:00.000Z" }`  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.date` corresponds to `2025-06-02` in the record's timezone

---

---

### Admin Create

---

**AT-AC-001** — Admin creates attendance for a user on a specific date  
**Endpoint:** `POST /admin-create/:user_id`  
**Priority:** P1 — Critical  
**Preconditions:** Admin role; target user exists; no attendance exists for that date  
**Request Body:**
```json
{
  "check_in_at": "2025-06-01T09:00:00.000Z",
  "check_out_at": "2025-06-01T18:00:00.000Z",
  "timezone": "Asia/Kolkata",
  "attendance_type": "admin_created",
  "location_name": "Office"
}
```
**Expected Response:** HTTP 201  
**Assertions:**
- HTTP status = 201
- `data.user_id` = target user's ID
- `data.attendance_type` = `"admin_created"`
- `data.flags.is_admin_created` = `true`
- `data.admin_metadata.created_by` = admin's user ID
- `data.admin_metadata.approved_by` = admin's user ID
- `data.status` = `"present"` (since 9h ≥ 510 min threshold)

---

**AT-AC-002** — Admin create blocked if attendance already exists for that date  
**Endpoint:** `POST /admin-create/:user_id`  
**Priority:** P1 — Critical  
**Preconditions:** Admin role; attendance already exists for target user on that date  
**Expected Response:** HTTP 409  
**Assertions:**
- HTTP status = 409
- `message` contains `"already exists"` (case-insensitive)

---

**AT-AC-003** — Admin create without check_in_at returns 400  
**Endpoint:** `POST /admin-create/:user_id`  
**Priority:** P1 — Critical  
**Request Body:** `{ "timezone": "Asia/Kolkata" }`  
**Expected Response:** HTTP 400  
**Assertions:**
- HTTP status = 400

---

**AT-AC-004** — Non-admin cannot create attendance for another user  
**Endpoint:** `POST /admin-create/:user_id`  
**Priority:** P1 — Critical  
**Preconditions:** Caller has regular employee role  
**Expected Response:** HTTP 403  
**Assertions:**
- HTTP status = 403

---

**AT-AC-005** — Admin creates attendance with only check-in (no check-out)  
**Endpoint:** `POST /admin-create/:user_id`  
**Priority:** P2 — Normal  
**Request Body:**
```json
{
  "check_in_at": "2025-06-01T09:00:00.000Z",
  "timezone": "Asia/Kolkata"
}
```
**Expected Response:** HTTP 201  
**Assertions:**
- `data.check_out_at` is null
- `data.total_work_minutes` = 0

---

**AT-AC-006** — Admin create creates audit log with `action = "admin_create"`  
**Endpoint:** `POST /admin-create/:user_id`  
**Priority:** P1 — Critical  
**Expected Response:** HTTP 201  
**Assertions:**
- An `AttendanceAuditLog` exists with `action = "admin_create"` and `admin_user_id` = admin's ID

---

---

### Admin Approve

---

**AT-AA-001** — Approve attendance sets status to `manually_adjusted`  
**Endpoint:** `PUT /:id/approve`  
**Priority:** P1 — Critical  
**Preconditions:** Attendance record exists  
**Request Body:** `{}`  
**Expected Response:** HTTP 200  
**Assertions:**
- HTTP status = 200
- `data.status` = `"manually_adjusted"`
- `data.flags.is_manual_edit` = `true`

---

**AT-AA-002** — Approve non-existent attendance returns 404  
**Endpoint:** `PUT /:id/approve`  
**Priority:** P2 — Normal  
**Preconditions:** ID does not exist  
**Expected Response:** HTTP 404 or 409

---

---

## Section 9 — Geofence Management

---

**AT-GF-001** — Create circular geofence returns 201  
**Endpoint:** `POST /geofences`  
**Priority:** P1 — Critical  
**Request Body:**
```json
{
  "name": "Factory Main Gate",
  "type": "circular",
  "center": { "latitude": 19.0596, "longitude": 72.8295 },
  "radius_meters": 200,
  "rules": {
    "check_in_required": true,
    "check_out_required": true,
    "allow_outside_check_in": false,
    "allow_outside_check_out": true
  }
}
```
**Expected Response:** HTTP 201  
**Assertions:**
- `data.type` = `"circular"`
- `data.radius_meters` = 200
- `data.status` = `"active"`
- `data.rules.check_in_required` = `true`

---

**AT-GF-002** — Create polygonal geofence returns 201  
**Endpoint:** `POST /geofences`  
**Priority:** P1 — Critical  
**Request Body:**
```json
{
  "name": "Production Zone B",
  "type": "polygonal",
  "coordinates": [
    { "latitude": 19.058, "longitude": 72.828 },
    { "latitude": 19.058, "longitude": 72.831 },
    { "latitude": 19.061, "longitude": 72.831 },
    { "latitude": 19.061, "longitude": 72.828 }
  ],
  "rules": {
    "check_in_required": true,
    "check_out_required": false,
    "allow_outside_check_in": true,
    "allow_outside_check_out": true
  }
}
```
**Expected Response:** HTTP 201  
**Assertions:**
- `data.type` = `"polygonal"`
- `data.coordinates` has 4 entries

---

**AT-GF-003** — Create geofence without name returns error  
**Endpoint:** `POST /geofences`  
**Priority:** P2 — Normal  
**Request Body:** `{ "type": "circular", "radius_meters": 100 }`  
**Expected Response:** HTTP 400 or 409

---

**AT-GF-004** — Get all geofences returns list  
**Endpoint:** `GET /geofences`  
**Priority:** P1 — Critical  
**Expected Response:** HTTP 200  
**Assertions:**
- `data` is an array
- Each entry has `_id`, `name`, `type`, `status`

---

**AT-GF-005** — Get geofence by valid ID returns correct data  
**Endpoint:** `GET /geofences/:id`  
**Priority:** P1 — Critical  
**Expected Response:** HTTP 200  
**Assertions:**
- `data._id` matches requested ID

---

**AT-GF-006** — Get geofence by invalid ObjectId returns 400  
**Endpoint:** `GET /geofences/:id`  
**Priority:** P2 — Normal  
**Expected Response:** HTTP 400

---

**AT-GF-007** — Update geofence radius  
**Endpoint:** `PUT /geofences/:id`  
**Priority:** P2 — Normal  
**Request Body:** `{ "radius_meters": 500 }`  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.radius_meters` = 500

---

**AT-GF-008** — Assign geofence to enterprise-wide  
**Endpoint:** `POST /geofences/:id/assign`  
**Priority:** P2 — Normal  
**Request Body:** `{ "enterprise_wide": true }`  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.assigned_to.enterprise_wide` = `true`

---

**AT-GF-009** — Delete (deactivate) geofence  
**Endpoint:** `DELETE /geofences/:id`  
**Priority:** P2 — Normal  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.status` = `"deleted"` or `"inactive"`

---

**AT-GF-010** — Geofence validation: point inside circular geofence  
**Scenario:** Check-in with coordinates inside a circular geofence (radius 200m)  
**Priority:** P1 — Critical  
**Test Data:**
- Geofence center: `{ lat: 19.0596, lng: 72.8295 }`, radius: 200m
- User location: `{ lat: 19.0597, lng: 72.8296 }` (~15m away)
**Expected:** Check-in succeeds with `geofence_check_in_valid = true`

---

**AT-GF-011** — Geofence validation: point outside circular geofence  
**Scenario:** Check-in with coordinates outside a circular geofence  
**Priority:** P1 — Critical  
**Test Data:**
- Geofence center: `{ lat: 19.0596, lng: 72.8295 }`, radius: 200m
- User location: `{ lat: 19.0650, lng: 72.8400 }` (~1.2km away)
**Expected:** Check-in blocked (403) when `allow_outside_check_in = false`

---

**AT-GF-012** — Geofence validation: point inside polygonal geofence  
**Scenario:** Check-in with coordinates inside a polygon  
**Priority:** P1 — Critical  
**Test Data:**
- Polygon corners: `(19.058,72.828), (19.058,72.831), (19.061,72.831), (19.061,72.828)`
- User location: `{ lat: 19.0595, lng: 72.8295 }` (inside)
**Expected:** Check-in succeeds with `geofence_check_in_valid = true`

---

**AT-GF-013** — Geofence validation: point outside polygonal geofence  
**Scenario:** Check-in with coordinates outside a polygon  
**Priority:** P1 — Critical  
**Test Data:**
- Same polygon as AT-GF-012
- User location: `{ lat: 19.0700, lng: 72.8500 }` (outside)
**Expected:** Check-in blocked (403) when `allow_outside_check_in = false`

---

---

## Section 10 — End-to-End Workflow Scenarios

These scenarios test complete multi-step flows that span multiple API calls.

---

**AT-E2E-001** — Full attendance day: check-in → break → check-out  
**Priority:** P0 — Blocker  
**Steps:**
1. `POST /checkin` → assert 200, `status = "in_progress"`
2. `GET /today` → assert `current_session` not null
3. `POST /break/start` → assert 200
4. `POST /break/end` → assert 200, `duration_minutes` ≥ 0
5. `POST /checkout` → assert 200, `status = "present"`, `break_minutes` > 0
6. `GET /today` → assert `current_session` is null, `sessions_completed` = 1
**Assertions (final):**
- `total_work_minutes` > 0
- `break_minutes` > 0
- `regular_minutes` + `overtime_minutes` = net work minutes

---

**AT-E2E-002** — Full regularization flow: employee submits → admin approves → attendance updated  
**Priority:** P1 — Critical  
**Steps:**
1. Create a completed attendance record (via check-in + check-out)
2. `POST /regularization` with corrected `check_in_at` → assert `status = "pending"`
3. `GET /regularization` → assert pending request appears
4. `PUT /regularization/:id/approve` with `status = "approved"` (admin token)
5. Fetch attendance record → assert `check_in_at` = requested value, `status = "manually_adjusted"`
**Assertions:**
- Regularization `status` = `"approved"`
- Attendance `flags.is_manual_edit` = `true`

---

**AT-E2E-003** — Full regularization flow: employee submits → admin rejects  
**Priority:** P1 — Critical  
**Steps:**
1. Create completed attendance record
2. `POST /regularization` → assert `status = "pending"`
3. `PUT /regularization/:id/approve` with `status = "rejected"`, `rejection_reason = "No valid reason"`
4. Fetch regularization → assert `status = "rejected"`, `rejection_reason` set
5. Fetch attendance → assert attendance is UNCHANGED (not modified)

---

**AT-E2E-004** — Admin creates attendance + employee submits regularization for it  
**Priority:** P2 — Normal  
**Steps:**
1. Admin: `POST /admin-create/:user_id` → assert 201
2. Employee: `POST /regularization` referencing admin-created attendance
3. Admin: `PUT /regularization/:id/approve` → assert approved
4. Verify attendance updated with employee's requested times

---

**AT-E2E-005** — Multiple breaks in one session — cumulative break time  
**Priority:** P1 — Critical  
**Steps:**
1. `POST /checkin`
2. `POST /break/start` → wait → `POST /break/end` (break 1 ≈ 5 min)
3. `POST /break/start` → wait → `POST /break/end` (break 2 ≈ 5 min)
4. `POST /checkout`
**Assertions:**
- `data.breaks` has 2 entries
- `data.break_minutes` ≈ sum of both break durations
- `data.total_work_minutes` = gross time (check-in to check-out)
- Net work = `total_work_minutes - break_minutes`

---

**AT-E2E-006** — Settings change affects next check-in gap enforcement  
**Priority:** P1 — Critical  
**Steps:**
1. `PUT /settings` → set `checkout_to_checkin_gap_minutes = 120`
2. `POST /checkin` → check in
3. `POST /checkout` → check out
4. Immediately `POST /checkin` again
**Assertions (step 4):**
- HTTP 400
- `data.remaining_minutes` > 0
- `data.required_gap_minutes` = 120

---

---

## Section 11 — Boundary & Edge Cases

---

**AT-ED-001** — Check-out immediately after check-in (0-minute session)  
**Endpoint:** `POST /checkout`  
**Priority:** P2 — Normal  
**Preconditions:** User just checked in  
**Expected Response:** HTTP 200  
**Assertions:**
- `data.total_work_minutes` = 0 or 1
- `data.regular_minutes` = 0 or 1
- `data.overtime_minutes` = 0

---

**AT-ED-002** — Break duration of 0 minutes (immediate start + end)  
**Priority:** P2 — Normal  
**Steps:** `POST /break/start` → immediately `POST /break/end`  
**Assertions:**
- `data.breaks[last].duration_minutes` = 0
- `data.break_minutes` = 0

---

**AT-ED-003** — Attendance record `date` field reflects check-in date in local timezone  
**Priority:** P2 — Normal  
**Preconditions:** Check-in at 11:59 PM IST (23:59 +05:30 = 18:29 UTC)  
**Assertions:**
- `data.date` = the IST date (not the UTC date, which would be next day)

---

**AT-ED-004** — Work minutes calculation: break time exceeds gross time returns 0 net  
**Priority:** P2 — Normal  
**Note:** This is an edge case in `calculateHours`. If `breakMinutes > totalWorkMinutes`, `netWorkMinutes = max(0, total - break) = 0`  
**Assertions:** `regular_minutes` = 0, `overtime_minutes` = 0

---

**AT-ED-005** — Overtime threshold from user settings used when no shift assigned  
**Priority:** P2 — Normal  
**Preconditions:** User has no shift assigned; user settings have `required_daily_hours_minutes = 300`; user works 400 minutes  
**Assertions:**
- `data.regular_minutes` = 300
- `data.overtime_minutes` ≈ 100

---

**AT-ED-006** — Shift overtime threshold takes priority over user settings  
**Priority:** P2 — Normal  
**Preconditions:** User has shift with `overtime_after_minutes = 480`; user settings have `required_daily_hours_minutes = 300`; user works 400 minutes  
**Assertions:**
- `data.regular_minutes` = 400 (all regular, since 400 < 480)
- `data.overtime_minutes` = 0

---

---

## Summary

| Section | Scenarios | P0 | P1 | P2 |
|---|---|---|---|---|
| 1. Check-In | 11 | 3 | 5 | 3 |
| 2. Check-Out | 12 | 3 | 6 | 3 |
| 3. Break Management | 9 | 2 | 4 | 3 |
| 4. Today's Summary | 5 | 1 | 2 | 2 |
| 5. History | 6 | 1 | 3 | 2 |
| 6. Settings | 9 | 1 | 3 | 5 |
| 7. Regularization | 10 | 1 | 7 | 2 |
| 8. Admin Operations | 14 | 0 | 9 | 5 |
| 9. Geofence | 13 | 0 | 7 | 6 |
| 10. End-to-End | 6 | 1 | 4 | 1 |
| 11. Edge Cases | 6 | 0 | 0 | 6 |
| **Total** | **101** | **13** | **50** | **38** |

---

## Known Bugs to Document as Defects (Not Automate as Pass)

The following are confirmed bugs found during analysis. Test cases for these should be written as **defect verification** tests (expected to fail until fixed):

| Bug ID | Description | Impact |
|---|---|---|
| BUG-ATT-001 | `determineStatus()` returns `"half_day"` which is not in the Attendance schema enum — Mongoose will reject saves with this status | High — half-day records cannot be saved |
| BUG-ATT-002 | Stale session (>24h) auto-close hardcodes `total_work_minutes = 480` instead of calculating actual work time | Medium — incorrect work time recorded |
| BUG-ATT-003 | `adminCreateAttendance` uses `total_work_minutes` (gross) instead of net minutes for status determination — breaks are not deducted | Medium — status may be incorrectly set to `"present"` |
| BUG-ATT-004 | `adminCreateAttendance` uses hardcoded 510-minute threshold ignoring shift and user settings | Medium — wrong overtime threshold for admin-created records |
| BUG-ATT-005 | `autoCheckOutJob` fires for anyone checked in more than `graceMinutes` (default 30 min) ago — not just end-of-shift | High — would auto-checkout active employees |
| BUG-ATT-006 | `getRegularizationRequests` allows any user to pass `user_id` to view another user's requests | Medium — authorization gap |
| BUG-ATT-007 | `approveAttendance` (`PUT /:id/approve`) has no role check — any authenticated user can approve any attendance | High — security gap |
| BUG-ATT-008 | `invalid_auto_checkout` status is defined in enum but never assigned — dead code | Low |
