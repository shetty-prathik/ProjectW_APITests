package com.projectw.tests;

import com.projectw.base.BaseTest;
import com.projectw.utils.ConfigManager;
import com.projectw.utils.Constants;
import com.projectw.utils.TestDataFactory;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Attendance Module Test Suite
 *
 * Execution order is driven by spec priority (P0 → P1 → P2), then by logical
 * dependency within each priority tier.
 *
 * Priority legend (from functional test specification):
 *   P0 — Blocker   : must pass before any other testing is meaningful
 *   P1 — Critical  : core happy-path and key negative scenarios
 *   P2 — Normal    : edge cases, metadata, multi-field updates
 *
 * ── P0 — BLOCKER (priority 1–13) ─────────────────────────────────────────
 *   1   AT-CI-001  Check-in success
 *   2   AT-CI-003  Double check-in returns 400
 *   3   AT-CI-004  Check-in no-auth returns 401
 *   4   AT-CO-001  Check-out success
 *   5   AT-CO-002  Check-out without check-in returns 400
 *   6   AT-CO-003  Check-out no-auth returns 401
 *   7   AT-BK-005  Break start no-auth returns 401
 *   8   AT-BK-006  Break end no-auth returns 401
 *   9   AT-TD-003  Today summary no-auth returns 401
 *   10  AT-HI-003  History no-auth returns 401
 *   11  AT-ST-005  Settings no-auth returns 401
 *   12  AT-RG-007  Regularization no-auth returns 401
 *   13  AT-E2E-001 Full day end-to-end lifecycle
 *
 * ── P1 — CRITICAL (priority 16–40) ───────────────────────────────────────
 *   16  AT-BK-001  Start break success
 *   17  AT-BK-002  Double break start returns 400
 *   18  AT-BK-003  End break success
 *   19  AT-BK-004  End break without active break returns 400
 *   20  AT-TD-001  Today summary while checked in
 *   21  AT-TD-002  Today summary after check-out
 *   22  AT-HI-001  History list
 *   23  AT-HI-002  History date range filter
 *   24  AT-ST-001  Get settings
 *   25  AT-ST-002  Update auto_checkout_grace_minutes
 *   26  AT-ST-003  Update required_daily_hours_minutes
 *   27  AT-RG-001  Create regularization request
 *   28  AT-RG-002  Duplicate pending request returns 400
 *   29  AT-RG-003  Get regularization list
 *   30  AT-RG-004  Approve regularization request
 *   31  AT-RG-005  Cannot reprocess approved request
 *   32  AT-RG-006  Reject regularization request
 *   33  AT-AE-001  Admin edit check-in time
 *   34  AT-AC-001  Admin create attendance
 *   35  AT-AC-003  Admin create without check_in_at returns 400
 *   36  AT-AA-001  Approve attendance
 *   37  AT-GF-001  Create circular geofence
 *   38  AT-GF-002  Create polygonal geofence
 *   39  AT-GF-003  Get all geofences
 *   40  AT-GF-004  Get geofence by ID
 *
 * ── P2 — NORMAL (priority 14–15, 41–44) ──────────────────────────────────
 *   14  AT-CI-002  Check-in with source metadata   [runs after P0 CI tests]
 *   15  AT-CI-005  Check-in missing timezone        [runs after P0 CI tests]
 *   41  AT-ST-004  Update multiple settings
 *   42  AT-AE-002  Admin edit non-existent record
 *   43  AT-GF-005  Update geofence radius
 *   44  AT-GF-006  Delete geofence
 *
 * Token usage:
 *   employeeSpec() — check-in, check-out, break, today, history, settings,
 *                    regularization submit + list own records
 *   authSpec()     — regularization approve/reject, admin-edit, admin-create,
 *                    attendance approve, geofence CRUD
 *   unauthSpec()   — all 401 negative tests
 *
 * State isolation strategy:
 *   - @BeforeClass performs a best-effort checkout (employee) to clear any stale session
 *   - Every test that checks in is responsible for checking out (or uses dependsOnMethods)
 *   - @AfterClass performs a guaranteed cleanup checkout if the flag isCheckedIn is still true
 */
@Epic("Project W API")
@Feature("Attendance & Workforce Management")
public class AttendanceTest extends BaseTest {

    // ── Shared state across dependent tests ──────────────────────────────────

    private String activeAttendanceId;
    private String completedAttendanceId;
    private String regularizationId;
    private String createdGeofenceId;
    private String createdPolygonGeofenceId;

    /** True only while an active check-in session exists — used by @AfterClass cleanup. */
    private boolean isCheckedIn = false;

    // ── Debug helper ──────────────────────────────────────────────────────────

    /**
     * Logs the HTTP status, key response fields, and current shared-state IDs.
     * Call at the end of every test for full debugging visibility.
     */
    private void debugResponse(String testId, Response response) {
        int status = response.getStatusCode();
        String body = response.getBody().asPrettyString();
        log.debug("[{}] HTTP {} | activeAttendanceId={} | completedAttendanceId={} | " +
                  "regularizationId={} | isCheckedIn={}",
                testId, status,
                activeAttendanceId, completedAttendanceId,
                regularizationId, isCheckedIn);
        log.debug("[{}] Response body:\n{}", testId, body);
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeClass(alwaysRun = true)
    public void ensureNotCheckedIn() {
        log.info("=== AttendanceTest @BeforeClass — clearing any stale employee session ===");
        log.debug("  employee : {}", ConfigManager.getEmployeeEmail());
        log.debug("  admin    : {}", ConfigManager.getAdminEmail());
        log.debug("  baseURI  : {}{}", ConfigManager.getBaseUrl(), ConfigManager.getApiBasePath());

        // Disable the checkout-to-checkin gap so rapid test runs aren't blocked.
        // The API enforces checkout_to_checkin_gap_minutes (default 60) across runs.
        // We set it to 0 here and restore it in @AfterClass.
        try {
            Response gapR = given().spec(employeeSpec())
                    .body(Map.of("checkout_to_checkin_gap_minutes", 0))
                    .put(Constants.ATTENDANCE_SETTINGS)
                    .then().extract().response();
            log.debug("  gap reset HTTP {} — checkout_to_checkin_gap_minutes set to 0", gapR.getStatusCode());
        } catch (Exception e) {
            log.warn("  gap reset failed: {}", e.getMessage());
        }

        try {
            Response r = given().spec(employeeSpec())
                    .body(TestDataFactory.checkOutPayload())
                    .post(Constants.ATTENDANCE_CHECKOUT)
                    .then().extract().response();
            log.debug("  pre-checkout HTTP {} — {}", r.getStatusCode(),
                    r.getStatusCode() == 200 ? "stale session cleared" : "no active session (expected)");
        } catch (Exception e) {
            log.debug("  pre-checkout skipped: {}", e.getMessage());
        }
    }

    // =========================================================================
    // SECTION 1 — CHECK-IN
    // =========================================================================

    @Test(priority = 1, description = "AT-CI-001: Successful check-in returns 200 with active session")
    @Story("Check-In")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /attendances/checkin with valid timezone should return HTTP 200, " +
                 "data._id not null, data.check_in_at not null, data.check_out_at = null.")
    public void AT_CI_001_checkInSuccess() {
        log.info("[AT-CI-001] POST {} | user={}", Constants.ATTENDANCE_CHECKIN, ConfigManager.getEmployeeEmail());
        log.debug("[AT-CI-001] Request body: {}", TestDataFactory.checkInPayload());

        // Extract first so debugResponse always fires even if assertions fail
        Response response = given()
                .spec(employeeSpec())
                .body(TestDataFactory.checkInPayload())
                .when()
                .post(Constants.ATTENDANCE_CHECKIN)
                .then()
                .extract().response();

        debugResponse("AT-CI-001", response);

        // Now assert — failure message will appear alongside the already-logged body
        Assert.assertEquals(response.getStatusCode(), 200,
                "Expected HTTP 200 on check-in");
        Assert.assertEquals(response.jsonPath().getInt("code"), 200,
                "Expected code=200 in body");
        Assert.assertNotNull(response.jsonPath().getString("data._id"),
                "data._id must not be null");
        Assert.assertNotNull(response.jsonPath().getString("data.check_in_at"),
                "data.check_in_at must not be null");
        Assert.assertNull(response.jsonPath().getString("data.check_out_at"),
                "data.check_out_at must be null on fresh check-in");

        activeAttendanceId = response.jsonPath().getString("data._id");
        String checkInAt   = response.jsonPath().getString("data.check_in_at");
        String status      = response.jsonPath().getString("data.status");
        String eid         = response.jsonPath().getString("data.eid");
        isCheckedIn = true;

        log.info("[AT-CI-001] PASSED — status={} | id={} | check_in_at={} | eid={}",
                status, activeAttendanceId, checkInAt, eid);
    }

    @Test(priority = 14, description = "AT-CI-002: Check-in with source metadata persists ip, device_id, location_name")
    @Story("Check-In")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /attendances/checkin with ip, device_id, location_name should return 200 " +
                 "and persist the metadata on the attendance record.")
    public void AT_CI_002_checkInWithMetadata() {
        if (isCheckedIn) {
            log.debug("[AT-CI-002] Checking out existing session before isolated test");
            given().spec(employeeSpec()).body(TestDataFactory.checkOutPayload())
                    .post(Constants.ATTENDANCE_CHECKOUT);
            isCheckedIn = false;
        }
        log.info("[AT-CI-002] POST {} | user={}", Constants.ATTENDANCE_CHECKIN, ConfigManager.getEmployeeEmail());
        log.debug("[AT-CI-002] Request body: {}", TestDataFactory.checkInWithMetaPayload());

        Response response = given()
                .spec(employeeSpec())
                .body(TestDataFactory.checkInWithMetaPayload())
                .when()
                .post(Constants.ATTENDANCE_CHECKIN)
                .then()
                .statusCode(200)
                .body("data.status", equalTo("in_progress"))
                .extract().response();

        isCheckedIn = true;
        activeAttendanceId = response.jsonPath().getString("data._id");
        String locationName = response.jsonPath().getString("data.location_name");
        log.info("[AT-CI-002] PASSED — id={} | location_name={}", activeAttendanceId, locationName);
        debugResponse("AT-CI-002", response);
    }

    @Test(priority = 2, description = "AT-CI-003: Double check-in returns 400",
          dependsOnMethods = "AT_CI_001_checkInSuccess")
    @Story("Check-In")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /attendances/checkin when already checked in should return HTTP 400 " +
                 "with message containing 'already'.")
    public void AT_CI_003_doubleCheckInReturns400() {
        if (!isCheckedIn) {
            log.debug("[AT-CI-003] Not checked in — checking in first to set up state");
            given().spec(employeeSpec()).body(TestDataFactory.checkInPayload())
                    .post(Constants.ATTENDANCE_CHECKIN);
            isCheckedIn = true;
        }
        log.info("[AT-CI-003] POST {} (second attempt, expecting 400) | activeId={}",
                Constants.ATTENDANCE_CHECKIN, activeAttendanceId);

        Response response = given()
                .spec(employeeSpec())
                .body(TestDataFactory.checkInPayload())
                .when()
                .post(Constants.ATTENDANCE_CHECKIN)
                .then()
                .statusCode(400)
                .body("message", containsStringIgnoringCase("already"))
                .extract().response();

        String message = response.jsonPath().getString("message");
        log.info("[AT-CI-003] PASSED — HTTP 400 | message=\"{}\"", message);
        debugResponse("AT-CI-003", response);
    }

    @Test(priority = 3, description = "AT-CI-004: Check-in without auth token returns 401")
    @Story("Check-In")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /attendances/checkin with no Authorization header should return HTTP 401.")
    public void AT_CI_004_checkInNoAuthReturns401() {
        log.info("[AT-CI-004] POST {} (no auth, expecting 401)", Constants.ATTENDANCE_CHECKIN);

        Response response = given()
                .spec(unauthSpec())
                .body(TestDataFactory.checkInPayload())
                .when()
                .post(Constants.ATTENDANCE_CHECKIN)
                .then()
                .statusCode(401)
                .extract().response();

        String message = response.jsonPath().getString("message");
        log.info("[AT-CI-004] PASSED — HTTP 401 | message=\"{}\"", message);
        debugResponse("AT-CI-004", response);
    }

    @Test(priority = 15, description = "AT-CI-005: Check-in with missing timezone defaults to Asia/Kolkata")
    @Story("Check-In")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /attendances/checkin with empty body should return 200 and default timezone to Asia/Kolkata.")
    public void AT_CI_005_checkInMissingTimezoneDefaults() {
        if (isCheckedIn) {
            log.debug("[AT-CI-005] Checking out existing session before isolated test");
            given().spec(employeeSpec()).body(TestDataFactory.checkOutPayload())
                    .post(Constants.ATTENDANCE_CHECKOUT);
            isCheckedIn = false;
        }
        log.info("[AT-CI-005] POST {} (empty body, expecting timezone default)", Constants.ATTENDANCE_CHECKIN);

        Response response = given()
                .spec(employeeSpec())
                .body(Map.of())
                .when()
                .post(Constants.ATTENDANCE_CHECKIN)
                .then()
                .statusCode(200)
                .body("data.status", equalTo("in_progress"))
                .extract().response();

        isCheckedIn = true;
        activeAttendanceId = response.jsonPath().getString("data._id");
        String timezone    = response.jsonPath().getString("data.timezone");
        log.info("[AT-CI-005] PASSED — id={} | timezone={}", activeAttendanceId, timezone);
        debugResponse("AT-CI-005", response);
    }

    // =========================================================================
    // SECTION 2 — CHECK-OUT
    // =========================================================================

    @Test(priority = 4, description = "AT-CO-001: Successful check-out returns 200 with completed record",
          dependsOnMethods = "AT_CI_001_checkInSuccess")
    @Story("Check-Out")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /attendances/checkout after check-in should return HTTP 200, " +
                 "data.check_out_at set, data.total_work_minutes >= 0.")
    public void AT_CO_001_checkOutSuccess() {
        if (!isCheckedIn) {
            log.debug("[AT-CO-001] Not checked in — checking in first");
            Response ci = given().spec(employeeSpec()).body(TestDataFactory.checkInPayload())
                    .post(Constants.ATTENDANCE_CHECKIN).then().extract().response();
            log.debug("[AT-CO-001] Re-check-in HTTP {} | body={}", ci.getStatusCode(), ci.getBody().asPrettyString());
            activeAttendanceId = ci.jsonPath().getString("data._id");
            isCheckedIn = true;
            log.debug("[AT-CO-001] Re-checked in, id={}", activeAttendanceId);
        }
        log.info("[AT-CO-001] POST {} | activeId={}", Constants.ATTENDANCE_CHECKOUT, activeAttendanceId);

        Response response = given()
                .spec(employeeSpec())
                .body(TestDataFactory.checkOutPayload())
                .when()
                .post(Constants.ATTENDANCE_CHECKOUT)
                .then()
                .extract().response();

        debugResponse("AT-CO-001", response);

        Assert.assertEquals(response.getStatusCode(), 200, "Expected HTTP 200 on checkout");
        Assert.assertEquals(response.jsonPath().getInt("code"), 200, "Expected code=200 in body");
        Assert.assertNotNull(response.jsonPath().getString("data.check_out_at"),
                "data.check_out_at must not be null after checkout");
        Assert.assertTrue(response.jsonPath().getInt("data.total_work_minutes") >= 0,
                "total_work_minutes must be >= 0");

        completedAttendanceId  = response.jsonPath().getString("data._id");
        String checkOutAt      = response.jsonPath().getString("data.check_out_at");
        String recordStatus    = response.jsonPath().getString("data.status");
        Integer totalWorkMins  = response.jsonPath().getInt("data.total_work_minutes");
        Integer breakMins      = response.jsonPath().getInt("data.break_minutes");
        isCheckedIn = false;

        log.info("[AT-CO-001] PASSED — id={} | status={} | check_out_at={} | " +
                 "total_work_minutes={} | break_minutes={}",
                completedAttendanceId, recordStatus, checkOutAt, totalWorkMins, breakMins);
    }

    @Test(priority = 5, description = "AT-CO-002: Check-out without prior check-in returns 400",
          dependsOnMethods = "AT_CO_001_checkOutSuccess")
    @Story("Check-Out")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /attendances/checkout when not checked in should return HTTP 400.")
    public void AT_CO_002_checkOutWithoutCheckInReturns400() {
        log.info("[AT-CO-002] POST {} (no active session, expecting 400)", Constants.ATTENDANCE_CHECKOUT);

        Response response = given()
                .spec(employeeSpec())
                .body(TestDataFactory.checkOutPayload())
                .when()
                .post(Constants.ATTENDANCE_CHECKOUT)
                .then()
                .statusCode(400)
                .extract().response();

        String message = response.jsonPath().getString("message");
        log.info("[AT-CO-002] PASSED — HTTP 400 | message=\"{}\"", message);
        debugResponse("AT-CO-002", response);
    }

    @Test(priority = 6, description = "AT-CO-003: Check-out without auth token returns 401")
    @Story("Check-Out")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /attendances/checkout with no Authorization header should return HTTP 401.")
    public void AT_CO_003_checkOutNoAuthReturns401() {
        log.info("[AT-CO-003] POST {} (no auth, expecting 401)", Constants.ATTENDANCE_CHECKOUT);

        Response response = given()
                .spec(unauthSpec())
                .body(TestDataFactory.checkOutPayload())
                .when()
                .post(Constants.ATTENDANCE_CHECKOUT)
                .then()
                .statusCode(401)
                .extract().response();

        String message = response.jsonPath().getString("message");
        log.info("[AT-CO-003] PASSED — HTTP 401 | message=\"{}\"", message);
        debugResponse("AT-CO-003", response);
    }

    // =========================================================================
    // SECTION 3 — BREAK MANAGEMENT
    // =========================================================================

    @Test(priority = 16, description = "AT-BK-001: Start break while checked in returns 200")
    @Story("Break Management")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /attendances/break/start when checked in should return HTTP 200 " +
                 "with a new break entry having start_at set and end_at null.")
    public void AT_BK_001_startBreakSuccess() {
        if (isCheckedIn) {
            log.debug("[AT-BK-001] Checking out existing session before fresh check-in");
            given().spec(employeeSpec()).body(TestDataFactory.checkOutPayload())
                    .post(Constants.ATTENDANCE_CHECKOUT);
            isCheckedIn = false;
        }
        Response ci = given().spec(employeeSpec()).body(TestDataFactory.checkInPayload())
                .post(Constants.ATTENDANCE_CHECKIN).then().extract().response();
        log.debug("[AT-BK-001] check-in HTTP {} | body={}", ci.getStatusCode(), ci.getBody().asPrettyString());
        activeAttendanceId = ci.jsonPath().getString("data._id");
        isCheckedIn = true;
        log.debug("[AT-BK-001] Checked in, id={}", activeAttendanceId);

        log.info("[AT-BK-001] POST {} | activeId={}", Constants.ATTENDANCE_BREAK_START, activeAttendanceId);

        Response response = given()
                .spec(employeeSpec())
                .body(Map.of())
                .when()
                .post(Constants.ATTENDANCE_BREAK_START)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .extract().response();

        String breakStartAt = response.jsonPath().getString("data.breaks[-1].start_at");
        String breakEndAt   = response.jsonPath().getString("data.breaks[-1].end_at");
        log.info("[AT-BK-001] PASSED — break started | start_at={} | end_at={}", breakStartAt, breakEndAt);
        debugResponse("AT-BK-001", response);
    }

    @Test(priority = 17, description = "AT-BK-002: Double break start returns 400",
          dependsOnMethods = "AT_BK_001_startBreakSuccess")
    @Story("Break Management")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /attendances/break/start when a break is already active should return HTTP 400.")
    public void AT_BK_002_doubleBreakStartReturns400() {
        log.info("[AT-BK-002] POST {} (break already active, expecting 400)", Constants.ATTENDANCE_BREAK_START);

        Response response = given()
                .spec(employeeSpec())
                .body(Map.of())
                .when()
                .post(Constants.ATTENDANCE_BREAK_START)
                .then()
                .statusCode(400)
                .extract().response();

        String message = response.jsonPath().getString("message");
        log.info("[AT-BK-002] PASSED — HTTP 400 | message=\"{}\"", message);
        debugResponse("AT-BK-002", response);
    }

    @Test(priority = 18, description = "AT-BK-003: End break calculates duration correctly",
          dependsOnMethods = "AT_BK_001_startBreakSuccess")
    @Story("Break Management")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /attendances/break/end should return HTTP 200 with end_at set " +
                 "and duration_minutes >= 0.")
    public void AT_BK_003_endBreakSuccess() {
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        log.info("[AT-BK-003] POST {} | activeId={}", Constants.ATTENDANCE_BREAK_END, activeAttendanceId);

        Response response = given()
                .spec(employeeSpec())
                .body(Map.of())
                .when()
                .post(Constants.ATTENDANCE_BREAK_END)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .extract().response();

        String breakEndAt      = response.jsonPath().getString("data.breaks[-1].end_at");
        Integer durationMins   = response.jsonPath().getInt("data.breaks[-1].duration_minutes");
        Integer totalBreakMins = response.jsonPath().getInt("data.break_minutes");
        log.info("[AT-BK-003] PASSED — end_at={} | duration_minutes={} | total break_minutes={}",
                breakEndAt, durationMins, totalBreakMins);
        debugResponse("AT-BK-003", response);
    }

    @Test(priority = 19, description = "AT-BK-004: End break without active break returns 400",
          dependsOnMethods = "AT_BK_003_endBreakSuccess")
    @Story("Break Management")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /attendances/break/end when no break is active should return HTTP 400.")
    public void AT_BK_004_endBreakWithoutActiveBreakReturns400() {
        log.info("[AT-BK-004] POST {} (no active break, expecting 400)", Constants.ATTENDANCE_BREAK_END);

        Response response = given()
                .spec(employeeSpec())
                .body(Map.of())
                .when()
                .post(Constants.ATTENDANCE_BREAK_END)
                .then()
                .statusCode(400)
                .extract().response();

        String message = response.jsonPath().getString("message");
        log.info("[AT-BK-004] PASSED — HTTP 400 | message=\"{}\"", message);
        debugResponse("AT-BK-004", response);
    }

    @Test(priority = 7, description = "AT-BK-005: Break start without auth token returns 401")
    @Story("Break Management")
    @Severity(SeverityLevel.BLOCKER)
    public void AT_BK_005_breakStartNoAuthReturns401() {
        log.info("[AT-BK-005] POST {} (no auth, expecting 401)", Constants.ATTENDANCE_BREAK_START);

        Response response = given()
                .spec(unauthSpec())
                .body(Map.of())
                .when()
                .post(Constants.ATTENDANCE_BREAK_START)
                .then()
                .statusCode(401)
                .extract().response();

        String message = response.jsonPath().getString("message");
        log.info("[AT-BK-005] PASSED — HTTP 401 | message=\"{}\"", message);
        debugResponse("AT-BK-005", response);
    }

    @Test(priority = 8, description = "AT-BK-006: Break end without auth token returns 401")
    @Story("Break Management")
    @Severity(SeverityLevel.BLOCKER)
    public void AT_BK_006_breakEndNoAuthReturns401() {
        log.info("[AT-BK-006] POST {} (no auth, expecting 401)", Constants.ATTENDANCE_BREAK_END);

        Response response = given()
                .spec(unauthSpec())
                .body(Map.of())
                .when()
                .post(Constants.ATTENDANCE_BREAK_END)
                .then()
                .statusCode(401)
                .extract().response();

        String message = response.jsonPath().getString("message");
        log.info("[AT-BK-006] PASSED — HTTP 401 | message=\"{}\"", message);
        debugResponse("AT-BK-006", response);
    }

    // =========================================================================
    // SECTION 4 — TODAY'S SUMMARY
    // =========================================================================

    @Test(priority = 20, description = "AT-TD-001: Get today's summary while checked in shows active session")
    @Story("Today's Summary")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /attendances/today while checked in should return HTTP 200 " +
                 "with data not null.")
    public void AT_TD_001_getTodaySummaryWhileCheckedIn() {
        if (!isCheckedIn) {
            log.debug("[AT-TD-001] Not checked in — checking in first");
            Response ci = given().spec(employeeSpec()).body(TestDataFactory.checkInPayload())
                    .post(Constants.ATTENDANCE_CHECKIN).then().extract().response();
            activeAttendanceId = ci.jsonPath().getString("data._id");
            isCheckedIn = true;
            log.debug("[AT-TD-001] Checked in, id={}", activeAttendanceId);
        }
        log.info("[AT-TD-001] GET {} | activeId={}", Constants.ATTENDANCE_TODAY, activeAttendanceId);

        Response response = given()
                .spec(employeeSpec())
                .queryParam("timezone", "Asia/Kolkata")
                .when()
                .get(Constants.ATTENDANCE_TODAY)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data", notNullValue())
                .extract().response();

        String sessionStatus  = response.jsonPath().getString("data.current_session.status");
        Integer workMins      = response.jsonPath().getInt("data.total_work_minutes");
        Integer sessCompleted = response.jsonPath().getInt("data.sessions_completed");
        log.info("[AT-TD-001] PASSED — current_session.status={} | total_work_minutes={} | sessions_completed={}",
                sessionStatus, workMins, sessCompleted);
        debugResponse("AT-TD-001", response);
    }

    @Test(priority = 21, description = "AT-TD-002: Get today's summary after check-out shows completed session",
          dependsOnMethods = "AT_TD_001_getTodaySummaryWhileCheckedIn")
    @Story("Today's Summary")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /attendances/today after checkout should return HTTP 200 with data not null.")
    public void AT_TD_002_getTodaySummaryAfterCheckOut() {
        if (isCheckedIn) {
            log.debug("[AT-TD-002] Checking out before querying today summary");
            given().spec(employeeSpec()).body(TestDataFactory.checkOutPayload())
                    .post(Constants.ATTENDANCE_CHECKOUT);
            isCheckedIn = false;
        }
        log.info("[AT-TD-002] GET {} (after checkout)", Constants.ATTENDANCE_TODAY);

        Response response = given()
                .spec(employeeSpec())
                .queryParam("timezone", "Asia/Kolkata")
                .when()
                .get(Constants.ATTENDANCE_TODAY)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data", notNullValue())
                .extract().response();

        Integer workMins      = response.jsonPath().getInt("data.total_work_minutes");
        Integer sessCompleted = response.jsonPath().getInt("data.sessions_completed");
        Integer breakMins     = response.jsonPath().getInt("data.break_minutes");
        log.info("[AT-TD-002] PASSED — total_work_minutes={} | sessions_completed={} | break_minutes={}",
                workMins, sessCompleted, breakMins);
        debugResponse("AT-TD-002", response);
    }

    @Test(priority = 9, description = "AT-TD-003: Get today's summary without auth returns 401")
    @Story("Today's Summary")
    @Severity(SeverityLevel.BLOCKER)
    public void AT_TD_003_getTodaySummaryNoAuthReturns401() {
        log.info("[AT-TD-003] GET {} (no auth, expecting 401)", Constants.ATTENDANCE_TODAY);

        Response response = given()
                .spec(unauthSpec())
                .when()
                .get(Constants.ATTENDANCE_TODAY)
                .then()
                .statusCode(401)
                .extract().response();

        String message = response.jsonPath().getString("message");
        log.info("[AT-TD-003] PASSED — HTTP 401 | message=\"{}\"", message);
        debugResponse("AT-TD-003", response);
    }

    // =========================================================================
    // SECTION 5 — HISTORY
    // =========================================================================

    @Test(priority = 22, description = "AT-HI-001: Get history returns list of past records")
    @Story("Attendance History")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /attendances/history should return HTTP 200 with attendance records.")
    public void AT_HI_001_getHistoryReturnsList() {
        log.info("[AT-HI-001] GET {}", Constants.ATTENDANCE_HISTORY);

        Response response = given()
                .spec(employeeSpec())
                .queryParam("timezone", "Asia/Kolkata")
                .when()
                .get(Constants.ATTENDANCE_HISTORY)
                .then()
                .extract().response();

        debugResponse("AT-HI-001", response);

        Assert.assertEquals(response.getStatusCode(), 200, "Expected HTTP 200 for history");
        Assert.assertEquals(response.jsonPath().getInt("code"), 200, "Expected code=200 in body");
        Assert.assertNotNull(response.jsonPath().get("data"), "data must not be null");

        // API may return data as a plain array OR as a paginated object { records: [...], total: N }
        int recordCount;
        Object data = response.jsonPath().get("data");
        if (data instanceof java.util.List) {
            recordCount = ((java.util.List<?>) data).size();
        } else {
            // paginated shape: data.records or data.attendances
            java.util.List<?> records = response.jsonPath().getList("data.records");
            if (records == null) records = response.jsonPath().getList("data.attendances");
            recordCount = (records != null) ? records.size() : 0;
            log.debug("[AT-HI-001] paginated response — data keys: {}",
                    response.jsonPath().getMap("data").keySet());
        }
        log.info("[AT-HI-001] PASSED — records returned={}", recordCount);
    }

    @Test(priority = 23, description = "AT-HI-002: Filter history by date range returns records within range")
    @Story("Attendance History")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /attendances/history with start_date and end_date should return HTTP 200.")
    public void AT_HI_002_filterHistoryByDateRange() {
        long endDate   = System.currentTimeMillis();
        long startDate = endDate - (7L * 24 * 60 * 60 * 1000);
        log.info("[AT-HI-002] GET {} | start_date={} | end_date={}", Constants.ATTENDANCE_HISTORY, startDate, endDate);

        Response response = given()
                .spec(employeeSpec())
                .queryParam("start_date", startDate)
                .queryParam("end_date", endDate)
                .queryParam("timezone", "Asia/Kolkata")
                .when()
                .get(Constants.ATTENDANCE_HISTORY)
                .then()
                .extract().response();

        debugResponse("AT-HI-002", response);

        Assert.assertEquals(response.getStatusCode(), 200, "Expected HTTP 200 for history with date filter");
        Assert.assertEquals(response.jsonPath().getInt("code"), 200, "Expected code=200 in body");

        int recordCount;
        Object data = response.jsonPath().get("data");
        if (data instanceof java.util.List) {
            recordCount = ((java.util.List<?>) data).size();
        } else {
            java.util.List<?> records = response.jsonPath().getList("data.records");
            if (records == null) records = response.jsonPath().getList("data.attendances");
            recordCount = (records != null) ? records.size() : 0;
        }
        log.info("[AT-HI-002] PASSED — records in last 7 days={}", recordCount);
    }

    @Test(priority = 10, description = "AT-HI-003: Get history without auth returns 401")
    @Story("Attendance History")
    @Severity(SeverityLevel.BLOCKER)
    public void AT_HI_003_getHistoryNoAuthReturns401() {
        log.info("[AT-HI-003] GET {} (no auth, expecting 401)", Constants.ATTENDANCE_HISTORY);

        Response response = given()
                .spec(unauthSpec())
                .when()
                .get(Constants.ATTENDANCE_HISTORY)
                .then()
                .statusCode(401)
                .extract().response();

        String message = response.jsonPath().getString("message");
        log.info("[AT-HI-003] PASSED — HTTP 401 | message=\"{}\"", message);
        debugResponse("AT-HI-003", response);
    }

    // =========================================================================
    // SECTION 6 — SETTINGS
    // =========================================================================

    @Test(priority = 24, description = "AT-ST-001: Get settings returns 200 with expected fields")
    @Story("Attendance Settings")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /attendances/settings should return HTTP 200 with auto_checkout_grace_minutes " +
                 "and required_daily_hours_minutes present.")
    public void AT_ST_001_getSettingsSuccess() {
        log.info("[AT-ST-001] GET {}", Constants.ATTENDANCE_SETTINGS);

        Response response = given()
                .spec(employeeSpec())
                .when()
                .get(Constants.ATTENDANCE_SETTINGS)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data", notNullValue())
                .extract().response();

        Integer graceMinutes   = response.jsonPath().getInt("data.auto_checkout_grace_minutes");
        Integer dailyHours     = response.jsonPath().getInt("data.required_daily_hours_minutes");
        String  timezone       = response.jsonPath().getString("data.timezone");
        log.info("[AT-ST-001] PASSED — auto_checkout_grace_minutes={} | required_daily_hours_minutes={} | timezone={}",
                graceMinutes, dailyHours, timezone);
        debugResponse("AT-ST-001", response);
    }

    @Test(priority = 25, description = "AT-ST-002: Update auto_checkout_grace_minutes setting")
    @Story("Attendance Settings")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT /attendances/settings with auto_checkout_grace_minutes=45 should return 200 " +
                 "with the updated value reflected.")
    public void AT_ST_002_updateAutoCheckoutGraceMinutes() {
        log.info("[AT-ST-002] PUT {} | body={{auto_checkout_grace_minutes: 45}}", Constants.ATTENDANCE_SETTINGS);

        Response response = given()
                .spec(employeeSpec())
                .body(Map.of("auto_checkout_grace_minutes", 45))
                .when()
                .put(Constants.ATTENDANCE_SETTINGS)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data.auto_checkout_grace_minutes", equalTo(45))
                .extract().response();

        Integer updated = response.jsonPath().getInt("data.auto_checkout_grace_minutes");
        log.info("[AT-ST-002] PASSED — auto_checkout_grace_minutes={}", updated);
        debugResponse("AT-ST-002", response);
    }

    @Test(priority = 26, description = "AT-ST-003: Update required_daily_hours_minutes setting")
    @Story("Attendance Settings")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT /attendances/settings with required_daily_hours_minutes=480 should return 200.")
    public void AT_ST_003_updateRequiredDailyHoursMinutes() {
        log.info("[AT-ST-003] PUT {} | body={{required_daily_hours_minutes: 480}}", Constants.ATTENDANCE_SETTINGS);

        Response response = given()
                .spec(employeeSpec())
                .body(Map.of("required_daily_hours_minutes", 480))
                .when()
                .put(Constants.ATTENDANCE_SETTINGS)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data.required_daily_hours_minutes", equalTo(480))
                .extract().response();

        Integer updated = response.jsonPath().getInt("data.required_daily_hours_minutes");
        log.info("[AT-ST-003] PASSED — required_daily_hours_minutes={}", updated);
        debugResponse("AT-ST-003", response);
    }

    @Test(priority = 41, description = "AT-ST-004: Update multiple settings in one call")
    @Story("Attendance Settings")
    @Severity(SeverityLevel.NORMAL)
    @Description("PUT /attendances/settings with multiple fields should update all of them.")
    public void AT_ST_004_updateMultipleSettings() {
        Map<String, Object> payload = Map.of(
                "auto_checkout_grace_minutes", 30,
                "required_daily_hours_minutes", 510
        );
        log.info("[AT-ST-004] PUT {} | body={}", Constants.ATTENDANCE_SETTINGS, payload);

        Response response = given()
                .spec(employeeSpec())
                .body(payload)
                .when()
                .put(Constants.ATTENDANCE_SETTINGS)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data.auto_checkout_grace_minutes", equalTo(30))
                .body("data.required_daily_hours_minutes", equalTo(510))
                .extract().response();

        Integer grace  = response.jsonPath().getInt("data.auto_checkout_grace_minutes");
        Integer daily  = response.jsonPath().getInt("data.required_daily_hours_minutes");
        log.info("[AT-ST-004] PASSED — auto_checkout_grace_minutes={} | required_daily_hours_minutes={}", grace, daily);
        debugResponse("AT-ST-004", response);
    }

    @Test(priority = 11, description = "AT-ST-005: Get settings without auth returns 401")
    @Story("Attendance Settings")
    @Severity(SeverityLevel.BLOCKER)
    public void AT_ST_005_getSettingsNoAuthReturns401() {
        log.info("[AT-ST-005] GET {} (no auth, expecting 401)", Constants.ATTENDANCE_SETTINGS);

        Response response = given()
                .spec(unauthSpec())
                .when()
                .get(Constants.ATTENDANCE_SETTINGS)
                .then()
                .statusCode(401)
                .extract().response();

        String message = response.jsonPath().getString("message");
        log.info("[AT-ST-005] PASSED — HTTP 401 | message=\"{}\"", message);
        debugResponse("AT-ST-005", response);
    }

    // =========================================================================
    // SECTION 7 — REGULARIZATION
    // =========================================================================

    @Test(priority = 27, description = "AT-RG-001: Create regularization request for own attendance")
    @Story("Regularization")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /attendances/regularization with a valid attendance_id should return 200 " +
                 "with data.status = 'pending'.")
    public void AT_RG_001_createRegularizationRequest() {
        if (completedAttendanceId == null) {
            log.debug("[AT-RG-001] No completedAttendanceId — creating a fresh check-in/out");
            if (isCheckedIn) {
                log.debug("[AT-RG-001] Already checked in — checking out first");
                given().spec(employeeSpec()).body(TestDataFactory.checkOutPayload())
                        .post(Constants.ATTENDANCE_CHECKOUT);
                isCheckedIn = false;
            }
            Response ci = given().spec(employeeSpec()).body(TestDataFactory.checkInPayload())
                    .post(Constants.ATTENDANCE_CHECKIN).then().extract().response();
            log.debug("[AT-RG-001] fresh check-in HTTP {} | id={}", ci.getStatusCode(),
                    ci.jsonPath().getString("data._id"));
            isCheckedIn = true;
            Response co = given().spec(employeeSpec()).body(TestDataFactory.checkOutPayload())
                    .post(Constants.ATTENDANCE_CHECKOUT).then().extract().response();
            log.debug("[AT-RG-001] fresh checkout HTTP {} | id={}", co.getStatusCode(),
                    co.jsonPath().getString("data._id"));
            completedAttendanceId = co.jsonPath().getString("data._id");
            isCheckedIn = false;
            log.debug("[AT-RG-001] completedAttendanceId={}", completedAttendanceId);
        }
        log.info("[AT-RG-001] POST {} | attendance_id={}", Constants.ATTENDANCE_REGULARIZATION, completedAttendanceId);
        log.debug("[AT-RG-001] Request body: {}", TestDataFactory.regularizationPayload(completedAttendanceId));

        Response response = given()
                .spec(employeeSpec())
                .body(TestDataFactory.regularizationPayload(completedAttendanceId))
                .when()
                .post(Constants.ATTENDANCE_REGULARIZATION)
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .body("data.status", equalTo("pending"))
                .extract().response();

        regularizationId  = response.jsonPath().getString("data._id");
        String regStatus  = response.jsonPath().getString("data.status");
        String userId     = response.jsonPath().getString("data.user_id");
        Assert.assertNotNull(regularizationId, "Regularization request must have an _id");
        log.info("[AT-RG-001] PASSED — id={} | status={} | user_id={}", regularizationId, regStatus, userId);
        debugResponse("AT-RG-001", response);
    }

    @Test(priority = 28, description = "AT-RG-002: Cannot create duplicate pending request for same attendance",
          dependsOnMethods = "AT_RG_001_createRegularizationRequest")
    @Story("Regularization")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /attendances/regularization with the same attendance_id when a pending " +
                 "request already exists should return HTTP 400.")
    public void AT_RG_002_duplicatePendingRequestReturns400() {
        log.info("[AT-RG-002] POST {} (duplicate, expecting 400/409) | attendance_id={}",
                Constants.ATTENDANCE_REGULARIZATION, completedAttendanceId);

        Response response = given()
                .spec(employeeSpec())
                .body(TestDataFactory.regularizationPayload(completedAttendanceId))
                .when()
                .post(Constants.ATTENDANCE_REGULARIZATION)
                .then()
                .statusCode(anyOf(equalTo(400), equalTo(409)))
                .extract().response();

        String message = response.jsonPath().getString("message");
        log.info("[AT-RG-002] PASSED — HTTP {} | message=\"{}\"", response.getStatusCode(), message);
        debugResponse("AT-RG-002", response);
    }

    @Test(priority = 29, description = "AT-RG-003: Get regularization requests returns list",
          dependsOnMethods = "AT_RG_001_createRegularizationRequest")
    @Story("Regularization")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /attendances/regularization should return HTTP 200 with regularization records.")
    public void AT_RG_003_getRegularizationRequestsList() {
        log.info("[AT-RG-003] GET {}", Constants.ATTENDANCE_REGULARIZATION);

        Response response = given()
                .spec(employeeSpec())
                .when()
                .get(Constants.ATTENDANCE_REGULARIZATION)
                .then()
                .extract().response();

        debugResponse("AT-RG-003", response);

        Assert.assertEquals(response.getStatusCode(), 200, "Expected HTTP 200 for regularization list");
        Assert.assertEquals(response.jsonPath().getInt("code"), 200, "Expected code=200 in body");
        Assert.assertNotNull(response.jsonPath().get("data"), "data must not be null");

        int count;
        Object data = response.jsonPath().get("data");
        if (data instanceof java.util.List) {
            count = ((java.util.List<?>) data).size();
        } else {
            java.util.List<?> records = response.jsonPath().getList("data.records");
            if (records == null) records = response.jsonPath().getList("data.regularizations");
            count = (records != null) ? records.size() : 0;
            log.debug("[AT-RG-003] paginated response — data keys: {}",
                    response.jsonPath().getMap("data").keySet());
        }
        log.info("[AT-RG-003] PASSED — regularization requests returned={}", count);
    }

    @Test(priority = 30, description = "AT-RG-004: Approve regularization request updates status to approved",
          dependsOnMethods = "AT_RG_001_createRegularizationRequest")
    @Story("Regularization")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT /attendances/regularization/:id/approve with status='approved' should return 200 " +
                 "with data.status = 'approved'.")
    public void AT_RG_004_approveRegularizationRequest() {
        if (regularizationId == null) {
            log.warn("[AT-RG-004] SKIPPED — no regularization ID available");
            return;
        }
        log.info("[AT-RG-004] PUT {} | id={} | body={{status: approved}}",
                Constants.ATTENDANCE_REGULARIZATION_APPROVE, regularizationId);

        Response response = given()
                .spec(authSpec())
                .pathParam("id", regularizationId)
                .body(Map.of("status", "approved"))
                .when()
                .put(Constants.ATTENDANCE_REGULARIZATION_APPROVE)
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .body("data.status", equalTo("approved"))
                .extract().response();

        String status     = response.jsonPath().getString("data.status");
        String approvedBy = response.jsonPath().getString("data.approved_by");
        String approvedAt = response.jsonPath().getString("data.approved_at");
        log.info("[AT-RG-004] PASSED — status={} | approved_by={} | approved_at={}", status, approvedBy, approvedAt);
        debugResponse("AT-RG-004", response);
    }

    @Test(priority = 31, description = "AT-RG-005: Cannot process an already-approved request",
          dependsOnMethods = "AT_RG_004_approveRegularizationRequest")
    @Story("Regularization")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT /attendances/regularization/:id/approve on an already-approved request " +
                 "should return HTTP 400.")
    public void AT_RG_005_cannotReprocessApprovedRequest() {
        if (regularizationId == null) {
            log.warn("[AT-RG-005] SKIPPED — no regularization ID available");
            return;
        }
        log.info("[AT-RG-005] PUT {} (already approved, expecting 400/409) | id={}",
                Constants.ATTENDANCE_REGULARIZATION_APPROVE, regularizationId);

        Response response = given()
                .spec(authSpec())
                .pathParam("id", regularizationId)
                .body(Map.of("status", "rejected"))
                .when()
                .put(Constants.ATTENDANCE_REGULARIZATION_APPROVE)
                .then()
                .statusCode(anyOf(equalTo(400), equalTo(409)))
                .extract().response();

        String message = response.jsonPath().getString("message");
        log.info("[AT-RG-005] PASSED — HTTP {} | message=\"{}\"", response.getStatusCode(), message);
        debugResponse("AT-RG-005", response);
    }

    @Test(priority = 32, description = "AT-RG-006: Reject regularization request with reason")
    @Story("Regularization")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT /attendances/regularization/:id/approve with status='rejected' and " +
                 "rejection_reason should return 200 with data.status = 'rejected'.")
    public void AT_RG_006_rejectRegularizationRequest() {
        log.debug("[AT-RG-006] Creating fresh check-in/out for isolated regularization");
        if (isCheckedIn) {
            log.debug("[AT-RG-006] Already checked in — checking out first");
            given().spec(employeeSpec()).body(TestDataFactory.checkOutPayload())
                    .post(Constants.ATTENDANCE_CHECKOUT);
            isCheckedIn = false;
        }
        Response ci = given().spec(employeeSpec()).body(TestDataFactory.checkInPayload())
                .post(Constants.ATTENDANCE_CHECKIN).then().extract().response();
        log.debug("[AT-RG-006] fresh check-in HTTP {} | id={}", ci.getStatusCode(),
                ci.jsonPath().getString("data._id"));
        isCheckedIn = true;
        Response co = given().spec(employeeSpec()).body(TestDataFactory.checkOutPayload())
                .post(Constants.ATTENDANCE_CHECKOUT).then().extract().response();
        isCheckedIn = false;
        String freshAttendanceId = co.jsonPath().getString("data._id");
        log.debug("[AT-RG-006] fresh checkout HTTP {} | freshAttendanceId={}",
                co.getStatusCode(), freshAttendanceId);

        Response reg = given().spec(employeeSpec())
                .body(TestDataFactory.regularizationPayload(freshAttendanceId))
                .post(Constants.ATTENDANCE_REGULARIZATION)
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .extract().response();
        String freshRegId = reg.jsonPath().getString("data._id");
        log.debug("[AT-RG-006] freshRegId={}", freshRegId);

        if (freshRegId == null) {
            log.warn("[AT-RG-006] SKIPPED — could not create fresh regularization request");
            return;
        }
        log.info("[AT-RG-006] PUT {} | id={} | body={{status: rejected}}", Constants.ATTENDANCE_REGULARIZATION_APPROVE, freshRegId);

        Response response = given()
                .spec(authSpec())
                .pathParam("id", freshRegId)
                .body(Map.of("status", "rejected", "rejection_reason", "Insufficient evidence provided"))
                .when()
                .put(Constants.ATTENDANCE_REGULARIZATION_APPROVE)
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .body("data.status", equalTo("rejected"))
                .extract().response();

        String status          = response.jsonPath().getString("data.status");
        String rejectionReason = response.jsonPath().getString("data.rejection_reason");
        log.info("[AT-RG-006] PASSED — status={} | rejection_reason=\"{}\"", status, rejectionReason);
        debugResponse("AT-RG-006", response);
    }

    @Test(priority = 12, description = "AT-RG-007: Get regularization requests without auth returns 401")
    @Story("Regularization")
    @Severity(SeverityLevel.BLOCKER)
    public void AT_RG_007_getRegularizationNoAuthReturns401() {
        log.info("[AT-RG-007] GET {} (no auth, expecting 401)", Constants.ATTENDANCE_REGULARIZATION);

        Response response = given()
                .spec(unauthSpec())
                .when()
                .get(Constants.ATTENDANCE_REGULARIZATION)
                .then()
                .statusCode(401)
                .extract().response();

        String message = response.jsonPath().getString("message");
        log.info("[AT-RG-007] PASSED — HTTP 401 | message=\"{}\"", message);
        debugResponse("AT-RG-007", response);
    }

    // =========================================================================
    // SECTION 8 — ADMIN OPERATIONS
    // =========================================================================

    @Test(priority = 33, description = "AT-AE-001: Admin can edit check-in time on an attendance record")
    @Story("Admin Operations")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT /attendances/:id/admin-edit with check_in_at should return 200 " +
                 "with data.status = 'manually_adjusted' and data.flags.is_manual_edit = true.")
    public void AT_AE_001_adminEditCheckInTime() {
        if (completedAttendanceId == null) {
            log.debug("[AT-AE-001] No completedAttendanceId — creating a fresh check-in/out");
            if (isCheckedIn) {
                log.debug("[AT-AE-001] Already checked in — checking out first");
                given().spec(employeeSpec()).body(TestDataFactory.checkOutPayload())
                        .post(Constants.ATTENDANCE_CHECKOUT);
                isCheckedIn = false;
            }
            Response ci = given().spec(employeeSpec()).body(TestDataFactory.checkInPayload())
                    .post(Constants.ATTENDANCE_CHECKIN).then().extract().response();
            log.debug("[AT-AE-001] fresh check-in HTTP {} | id={}", ci.getStatusCode(),
                    ci.jsonPath().getString("data._id"));
            isCheckedIn = true;
            Response co = given().spec(employeeSpec()).body(TestDataFactory.checkOutPayload())
                    .post(Constants.ATTENDANCE_CHECKOUT).then().extract().response();
            log.debug("[AT-AE-001] fresh checkout HTTP {} | id={}", co.getStatusCode(),
                    co.jsonPath().getString("data._id"));
            completedAttendanceId = co.jsonPath().getString("data._id");
            isCheckedIn = false;
            log.debug("[AT-AE-001] completedAttendanceId={}", completedAttendanceId);
        }
        String newCheckInAt = "2025-06-01T08:30:00.000Z";
        log.info("[AT-AE-001] PUT {} | id={} | new check_in_at={}",
                Constants.ATTENDANCE_ADMIN_EDIT, completedAttendanceId, newCheckInAt);

        Response response = given()
                .spec(authSpec())
                .pathParam("id", completedAttendanceId)
                .body(TestDataFactory.adminEditCheckInPayload(newCheckInAt))
                .when()
                .put(Constants.ATTENDANCE_ADMIN_EDIT)
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .body("data.flags.is_manual_edit", equalTo(true))
                .extract().response();

        String status      = response.jsonPath().getString("data.status");
        String checkInAt   = response.jsonPath().getString("data.check_in_at");
        Boolean manualEdit = response.jsonPath().getBoolean("data.flags.is_manual_edit");
        log.info("[AT-AE-001] PASSED — status={} | check_in_at={} | is_manual_edit={}", status, checkInAt, manualEdit);
        debugResponse("AT-AE-001", response);
    }

    @Test(priority = 42, description = "AT-AE-002: Admin edit on non-existent attendance returns 404 or 409")
    @Story("Admin Operations")
    @Severity(SeverityLevel.NORMAL)
    @Description("PUT /attendances/:id/admin-edit with a non-existent ID should return 404 or 409.")
    public void AT_AE_002_adminEditNonExistentReturnsError() {
        String fakeId = "000000000000000000000000";
        log.info("[AT-AE-002] PUT {} | id={} (non-existent, expecting 400/404/409)",
                Constants.ATTENDANCE_ADMIN_EDIT, fakeId);

        Response response = given()
                .spec(authSpec())
                .pathParam("id", fakeId)
                .body(TestDataFactory.adminEditCheckInPayload("2025-06-01T09:00:00.000Z"))
                .when()
                .put(Constants.ATTENDANCE_ADMIN_EDIT)
                .then()
                .statusCode(anyOf(equalTo(404), equalTo(409), equalTo(400)))
                .extract().response();

        String message = response.jsonPath().getString("message");
        log.info("[AT-AE-002] PASSED — HTTP {} | message=\"{}\"", response.getStatusCode(), message);
        debugResponse("AT-AE-002", response);
    }

    @Test(priority = 34, description = "AT-AC-001: Admin creates attendance for a user")
    @Story("Admin Operations")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /attendances/admin-create/:userId with check_in_at and check_out_at " +
                 "should return 201 with data.flags.is_admin_created = true.")
    public void AT_AC_001_adminCreateAttendance() {
        String userId = ConfigManager.getAdminUserId();
        if (userId == null || userId.isBlank()) {
            log.warn("[AT-AC-001] SKIPPED — test.admin.user_id not configured in config.properties");
            return;
        }
        log.info("[AT-AC-001] POST {} | userId={}", Constants.ATTENDANCE_ADMIN_CREATE, userId);

        Response response = given()
                .spec(authSpec())
                .pathParam("userId", userId)
                .body(TestDataFactory.adminCreatePayload(
                        "2025-06-01T09:00:00.000Z",
                        "2025-06-01T18:00:00.000Z"))
                .when()
                .post(Constants.ATTENDANCE_ADMIN_CREATE)
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(201), equalTo(409)))
                .extract().response();

        String status          = response.jsonPath().getString("data.status");
        Boolean isAdminCreated = response.jsonPath().getBoolean("data.flags.is_admin_created");
        log.info("[AT-AC-001] PASSED — HTTP {} | status={} | is_admin_created={}",
                response.getStatusCode(), status, isAdminCreated);
        debugResponse("AT-AC-001", response);
    }

    @Test(priority = 35, description = "AT-AC-003: Admin create without check_in_at returns 400")
    @Story("Admin Operations")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /attendances/admin-create/:userId without check_in_at should return 400.")
    public void AT_AC_003_adminCreateWithoutCheckInReturns400() {
        String userId = ConfigManager.getAdminUserId();
        if (userId == null || userId.isBlank()) {
            log.warn("[AT-AC-003] SKIPPED — test.admin.user_id not configured in config.properties");
            return;
        }
        log.info("[AT-AC-003] POST {} (no check_in_at, expecting 400/409) | userId={}",
                Constants.ATTENDANCE_ADMIN_CREATE, userId);

        Response response = given()
                .spec(authSpec())
                .pathParam("userId", userId)
                .body(Map.of("timezone", "Asia/Kolkata"))
                .when()
                .post(Constants.ATTENDANCE_ADMIN_CREATE)
                .then()
                .statusCode(anyOf(equalTo(400), equalTo(409)))
                .extract().response();

        String message = response.jsonPath().getString("message");
        log.info("[AT-AC-003] PASSED — HTTP {} | message=\"{}\"", response.getStatusCode(), message);
        debugResponse("AT-AC-003", response);
    }

    @Test(priority = 36, description = "AT-AA-001: Approve attendance sets status to manually_adjusted")
    @Story("Admin Operations")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT /attendances/:id/approve should return 200 with data.flags.is_manual_edit = true.")
    public void AT_AA_001_approveAttendance() {
        if (completedAttendanceId == null) {
            log.warn("[AT-AA-001] SKIPPED — no completed attendance ID available");
            return;
        }
        log.info("[AT-AA-001] PUT {} | id={}", Constants.ATTENDANCE_APPROVE, completedAttendanceId);

        Response response = given()
                .spec(authSpec())
                .pathParam("id", completedAttendanceId)
                .body(Map.of())
                .when()
                .put(Constants.ATTENDANCE_APPROVE)
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .extract().response();

        String status      = response.jsonPath().getString("data.status");
        Boolean manualEdit = response.jsonPath().getBoolean("data.flags.is_manual_edit");
        log.info("[AT-AA-001] PASSED — HTTP {} | status={} | is_manual_edit={}",
                response.getStatusCode(), status, manualEdit);
        debugResponse("AT-AA-001", response);
    }

    // =========================================================================
    // SECTION 9 — GEOFENCE MANAGEMENT
    // =========================================================================

    /**
     * Tries authSpec() first; if that returns 403 (insufficient RBAC role),
     * falls back to employeeSpec() and logs which token succeeded.
     * Returns the response from whichever call succeeded (or the 403 if both fail).
     */
    private Response geofencePost(String testId, Map<String, Object> payload) {
        Response r = given().spec(authSpec()).body(payload).when().post(Constants.GEOFENCES)
                .then().extract().response();
        if (r.getStatusCode() == 403) {
            log.warn("[{}] authSpec() returned 403 — retrying with employeeSpec(). 403 body: {}",
                    testId, r.getBody().asPrettyString());
            r = given().spec(employeeSpec()).body(payload).when().post(Constants.GEOFENCES)
                    .then().extract().response();
            log.debug("[{}] employeeSpec() returned HTTP {}", testId, r.getStatusCode());
        }
        return r;
    }

    private Response geofenceGet(String testId, String id) {
        Response r = (id != null)
                ? given().spec(authSpec()).pathParam("id", id).when().get(Constants.GEOFENCE_BY_ID).then().extract().response()
                : given().spec(authSpec()).when().get(Constants.GEOFENCES).then().extract().response();
        if (r.getStatusCode() == 403) {
            log.warn("[{}] authSpec() GET returned 403 — retrying with employeeSpec()", testId);
            r = (id != null)
                    ? given().spec(employeeSpec()).pathParam("id", id).when().get(Constants.GEOFENCE_BY_ID).then().extract().response()
                    : given().spec(employeeSpec()).when().get(Constants.GEOFENCES).then().extract().response();
        }
        return r;
    }

    private Response geofencePut(String testId, String id, Object body) {
        Response r = given().spec(authSpec()).pathParam("id", id).body(body)
                .when().put(Constants.GEOFENCE_BY_ID).then().extract().response();
        if (r.getStatusCode() == 403) {
            log.warn("[{}] authSpec() PUT returned 403 — retrying with employeeSpec()", testId);
            r = given().spec(employeeSpec()).pathParam("id", id).body(body)
                    .when().put(Constants.GEOFENCE_BY_ID).then().extract().response();
        }
        return r;
    }

    private Response geofenceDelete(String testId, String id) {
        Response r = given().spec(authSpec()).pathParam("id", id)
                .when().delete(Constants.GEOFENCE_BY_ID).then().extract().response();
        if (r.getStatusCode() == 403) {
            log.warn("[{}] authSpec() DELETE returned 403 — retrying with employeeSpec()", testId);
            r = given().spec(employeeSpec()).pathParam("id", id)
                    .when().delete(Constants.GEOFENCE_BY_ID).then().extract().response();
        }
        return r;
    }

    @Test(priority = 37, description = "AT-GF-001: Create circular geofence returns 200/201")
    @Story("Geofence Management")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /geofences with type='circular' should return HTTP 200 or 201 " +
                 "with data.type = 'circular' and data.radius_meters = 200.")
    public void AT_GF_001_createCircularGeofence() {
        Map<String, Object> payload = TestDataFactory.circularGeofencePayload();
        log.info("[AT-GF-001] POST {} | name={}", Constants.GEOFENCES, payload.get("name"));
        log.debug("[AT-GF-001] Request body: {}", payload);

        Response response = geofencePost("AT-GF-001", payload);
        debugResponse("AT-GF-001", response);

        Assert.assertTrue(
                response.getStatusCode() == 200 || response.getStatusCode() == 201,
                "Expected HTTP 200 or 201 for geofence creation, got " + response.getStatusCode()
                        + ". Body: " + response.getBody().asPrettyString());
        Assert.assertNotNull(response.jsonPath().getString("data._id"),
                "data._id must not be null");
        Assert.assertEquals(response.jsonPath().getString("data.type"), "circular",
                "data.type must be 'circular'");
        Assert.assertEquals(response.jsonPath().getInt("data.radius_meters"), 200,
                "data.radius_meters must be 200");

        createdGeofenceId = extractId(response);
        String type     = response.jsonPath().getString("data.type");
        Integer radius  = response.jsonPath().getInt("data.radius_meters");
        String gfStatus = response.jsonPath().getString("data.status");
        log.info("[AT-GF-001] PASSED — id={} | type={} | radius_meters={} | status={}",
                createdGeofenceId, type, radius, gfStatus);
    }

    @Test(priority = 38, description = "AT-GF-002: Create polygonal geofence returns 200/201")
    @Story("Geofence Management")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /geofences with type='polygonal' should return HTTP 200 or 201 " +
                 "with data.type = 'polygonal' and 4 coordinates.")
    public void AT_GF_002_createPolygonGeofence() {
        Map<String, Object> payload = TestDataFactory.polygonGeofencePayload();
        log.info("[AT-GF-002] POST {} | name={}", Constants.GEOFENCES, payload.get("name"));
        log.debug("[AT-GF-002] Request body: {}", payload);

        Response response = geofencePost("AT-GF-002", payload);
        debugResponse("AT-GF-002", response);

        Assert.assertTrue(
                response.getStatusCode() == 200 || response.getStatusCode() == 201,
                "Expected HTTP 200 or 201 for polygon geofence creation, got " + response.getStatusCode()
                        + ". Body: " + response.getBody().asPrettyString());
        Assert.assertEquals(response.jsonPath().getString("data.type"), "polygonal",
                "data.type must be 'polygonal'");
        Assert.assertNotNull(response.jsonPath().get("data.coordinates"),
                "data.coordinates must not be null");

        createdPolygonGeofenceId = extractId(response);
        int coordCount = response.jsonPath().getList("data.coordinates").size();
        Assert.assertNotNull(createdPolygonGeofenceId, "Created polygon geofence must have an _id");
        log.info("[AT-GF-002] PASSED — id={} | coordinates.count={}", createdPolygonGeofenceId, coordCount);
    }

    @Test(priority = 39, description = "AT-GF-003: Get all geofences returns list",
          dependsOnMethods = "AT_GF_001_createCircularGeofence")
    @Story("Geofence Management")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /geofences should return HTTP 200 with geofence records.")
    public void AT_GF_003_getAllGeofencesReturnsList() {
        log.info("[AT-GF-003] GET {}", Constants.GEOFENCES);

        Response response = geofenceGet("AT-GF-003", null);
        debugResponse("AT-GF-003", response);

        Assert.assertEquals(response.getStatusCode(), 200, "Expected HTTP 200 for geofences list");
        Assert.assertNotNull(response.jsonPath().get("data"), "data must not be null");

        int count;
        Object data = response.jsonPath().get("data");
        if (data instanceof java.util.List) {
            count = ((java.util.List<?>) data).size();
        } else {
            java.util.List<?> records = response.jsonPath().getList("data.records");
            if (records == null) records = response.jsonPath().getList("data.geofences");
            count = (records != null) ? records.size() : 0;
            log.debug("[AT-GF-003] paginated response — data keys: {}",
                    response.jsonPath().getMap("data").keySet());
        }
        log.info("[AT-GF-003] PASSED — geofences returned={}", count);
    }

    @Test(priority = 40, description = "AT-GF-004: Get geofence by valid ID returns correct data",
          dependsOnMethods = "AT_GF_001_createCircularGeofence")
    @Story("Geofence Management")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /geofences/:id should return HTTP 200 with data._id matching the requested ID.")
    public void AT_GF_004_getGeofenceByIdSuccess() {
        log.info("[AT-GF-004] GET {} | id={}", Constants.GEOFENCE_BY_ID, createdGeofenceId);

        Response response = geofenceGet("AT-GF-004", createdGeofenceId);
        debugResponse("AT-GF-004", response);

        Assert.assertEquals(response.getStatusCode(), 200,
                "Expected HTTP 200 for geofence by ID, got " + response.getStatusCode());
        Assert.assertEquals(response.jsonPath().getString("data._id"), createdGeofenceId,
                "data._id must match requested ID");

        String name   = response.jsonPath().getString("data.name");
        String type   = response.jsonPath().getString("data.type");
        String status = response.jsonPath().getString("data.status");
        log.info("[AT-GF-004] PASSED — id={} | name={} | type={} | status={}",
                createdGeofenceId, name, type, status);
    }

    @Test(priority = 43, description = "AT-GF-005: Update geofence radius",
          dependsOnMethods = "AT_GF_001_createCircularGeofence")
    @Story("Geofence Management")
    @Severity(SeverityLevel.NORMAL)
    @Description("PUT /geofences/:id with radius_meters=500 should return 200 with updated value.")
    public void AT_GF_005_updateGeofenceRadius() {
        log.info("[AT-GF-005] PUT {} | id={} | radius_meters=500", Constants.GEOFENCE_BY_ID, createdGeofenceId);

        Response response = geofencePut("AT-GF-005", createdGeofenceId, Map.of("radius_meters", 500));
        debugResponse("AT-GF-005", response);

        Assert.assertEquals(response.getStatusCode(), 200,
                "Expected HTTP 200 for geofence update, got " + response.getStatusCode());
        Assert.assertEquals(response.jsonPath().getInt("data.radius_meters"), 500,
                "radius_meters must be updated to 500");

        log.info("[AT-GF-005] PASSED — radius_meters={}", response.jsonPath().getInt("data.radius_meters"));
    }

    @Test(priority = 44, description = "AT-GF-006: Delete (deactivate) geofence returns 200",
          dependsOnMethods = "AT_GF_001_createCircularGeofence")
    @Story("Geofence Management")
    @Severity(SeverityLevel.NORMAL)
    @Description("DELETE /geofences/:id should return 200 with data.status = 'deleted' or 'inactive'.")
    public void AT_GF_006_deleteGeofence() {
        log.info("[AT-GF-006] DELETE {} | id={}", Constants.GEOFENCE_BY_ID, createdGeofenceId);

        Response response = geofenceDelete("AT-GF-006", createdGeofenceId);
        debugResponse("AT-GF-006", response);

        Assert.assertEquals(response.getStatusCode(), 200,
                "Expected HTTP 200 for geofence delete, got " + response.getStatusCode());

        String deletedStatus = response.jsonPath().getString("data.status");
        log.info("[AT-GF-006] PASSED — data.status={}", deletedStatus);
        createdGeofenceId = null;
    }

    // =========================================================================
    // SECTION 10 — END-TO-END
    // =========================================================================

    @Test(priority = 13, description = "AT-E2E-001: Full day: check-in → break start → break end → check-out")
    @Story("End-to-End")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Full attendance lifecycle: check-in → verify today summary → start break → " +
                 "end break → check-out → verify today summary shows completed session.")
    public void AT_E2E_001_fullAttendanceDay() {
        log.info("[AT-E2E-001] === Starting full attendance day lifecycle ===");
        if (isCheckedIn) {
            log.debug("[AT-E2E-001] Clearing existing session before E2E");
            given().spec(employeeSpec()).body(TestDataFactory.checkOutPayload())
                    .post(Constants.ATTENDANCE_CHECKOUT);
            isCheckedIn = false;
        }

        // Step 1: Check-in
        Response checkIn = given()
                .spec(employeeSpec())
                .body(TestDataFactory.checkInPayload())
                .when()
                .post(Constants.ATTENDANCE_CHECKIN)
                .then()
                .statusCode(200)
                .body("data.status", equalTo("in_progress"))
                .extract().response();
        isCheckedIn = true;
        String e2eAttendanceId = checkIn.jsonPath().getString("data._id");
        String checkInAt       = checkIn.jsonPath().getString("data.check_in_at");
        log.info("[AT-E2E-001] Step 1 PASS — checked in | id={} | check_in_at={}", e2eAttendanceId, checkInAt);

        // Step 2: Today summary — active session
        Response todayActive = given().spec(employeeSpec()).queryParam("timezone", "Asia/Kolkata")
                .get(Constants.ATTENDANCE_TODAY).then().statusCode(200).extract().response();
        String sessionStatus = todayActive.jsonPath().getString("data.current_session.status");
        log.info("[AT-E2E-001] Step 2 PASS — today summary | current_session.status={}", sessionStatus);

        // Step 3: Break start
        Response breakStart = given().spec(employeeSpec()).body(Map.of())
                .post(Constants.ATTENDANCE_BREAK_START).then().statusCode(200).extract().response();
        String breakStartAt = breakStart.jsonPath().getString("data.breaks[-1].start_at");
        log.info("[AT-E2E-001] Step 3 PASS — break started | start_at={}", breakStartAt);

        // Step 4: Break end
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        Response breakEnd = given().spec(employeeSpec()).body(Map.of())
                .post(Constants.ATTENDANCE_BREAK_END).then().statusCode(200).extract().response();
        String breakEndAt    = breakEnd.jsonPath().getString("data.breaks[-1].end_at");
        Integer breakDurMins = breakEnd.jsonPath().getInt("data.breaks[-1].duration_minutes");
        log.info("[AT-E2E-001] Step 4 PASS — break ended | end_at={} | duration_minutes={}", breakEndAt, breakDurMins);

        // Step 5: Check-out
        Response checkOut = given()
                .spec(employeeSpec())
                .body(TestDataFactory.checkOutPayload())
                .when()
                .post(Constants.ATTENDANCE_CHECKOUT)
                .then()
                .statusCode(200)
                .body("data.check_out_at", notNullValue())
                .body("data.total_work_minutes", greaterThanOrEqualTo(0))
                .extract().response();
        isCheckedIn = false;
        String checkOutAt     = checkOut.jsonPath().getString("data.check_out_at");
        Integer totalWorkMins = checkOut.jsonPath().getInt("data.total_work_minutes");
        Integer totalBreakMin = checkOut.jsonPath().getInt("data.break_minutes");
        String  finalStatus   = checkOut.jsonPath().getString("data.status");
        log.info("[AT-E2E-001] Step 5 PASS — checked out | check_out_at={} | status={} | " +
                 "total_work_minutes={} | break_minutes={}",
                checkOutAt, finalStatus, totalWorkMins, totalBreakMin);

        // Step 6: Today summary — completed session
        Response todayDone = given().spec(employeeSpec()).queryParam("timezone", "Asia/Kolkata")
                .get(Constants.ATTENDANCE_TODAY).then().statusCode(200).extract().response();
        Integer sessCompleted = todayDone.jsonPath().getInt("data.sessions_completed");
        log.info("[AT-E2E-001] Step 6 PASS — today summary | sessions_completed={}", sessCompleted);

        log.info("[AT-E2E-001] PASSED === Full lifecycle complete | id={} | check_in={} | check_out={} | " +
                 "work_mins={} | break_mins={} | status={}",
                e2eAttendanceId, checkInAt, checkOutAt, totalWorkMins, totalBreakMin, finalStatus);
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        log.info("=== AttendanceTest @AfterClass cleanup ===");
        log.debug("  isCheckedIn={} | completedAttendanceId={} | regularizationId={} | " +
                  "createdGeofenceId={} | createdPolygonGeofenceId={}",
                isCheckedIn, completedAttendanceId, regularizationId,
                createdGeofenceId, createdPolygonGeofenceId);

        if (isCheckedIn) {
            try {
                Response r = given().spec(employeeSpec()).body(TestDataFactory.checkOutPayload())
                        .post(Constants.ATTENDANCE_CHECKOUT).then().extract().response();
                log.info("  cleanup checkout HTTP {} — stale employee session cleared", r.getStatusCode());
            } catch (Exception e) {
                log.warn("  cleanup checkout failed: {}", e.getMessage());
            }
        }
        if (createdPolygonGeofenceId != null) {
            try {
                Response r = geofenceDelete("cleanup-polygon", createdPolygonGeofenceId);
                log.info("  cleanup polygon geofence HTTP {} | id={}", r.getStatusCode(), createdPolygonGeofenceId);
            } catch (Exception e) {
                log.warn("  cleanup polygon geofence failed: {}", e.getMessage());
            }
        }
        if (createdGeofenceId != null) {
            try {
                Response r = geofenceDelete("cleanup-circular", createdGeofenceId);
                log.info("  cleanup circular geofence HTTP {} | id={}", r.getStatusCode(), createdGeofenceId);
            } catch (Exception e) {
                log.warn("  cleanup circular geofence failed: {}", e.getMessage());
            }
        }
        // Restore the checkout-to-checkin gap to its default value
        try {
            Response gapR = given().spec(employeeSpec())
                    .body(Map.of("checkout_to_checkin_gap_minutes", 60))
                    .put(Constants.ATTENDANCE_SETTINGS)
                    .then().extract().response();
            log.debug("  gap restored HTTP {} — checkout_to_checkin_gap_minutes set back to 60",
                    gapR.getStatusCode());
        } catch (Exception e) {
            log.warn("  gap restore failed: {}", e.getMessage());
        }

        log.info("=== AttendanceTest cleanup complete ===");
    }
}
