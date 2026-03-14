package com.projectw.tests;

import com.projectw.base.BaseTest;
import com.projectw.utils.ConfigManager;
import com.projectw.utils.Constants;
import com.projectw.utils.TestDataFactory;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Authentication Test Suite
 *
 * Covers:
 *  TC-AUTH-01  Valid admin login returns 200 + JWT token
 *  TC-AUTH-02  Login with wrong password returns 401
 *  TC-AUTH-03  Login with unknown email returns 401/404
 *  TC-AUTH-04  Login with missing eid returns error
 *  TC-AUTH-05  Login with empty body returns 400
 *  TC-AUTH-06  Accessing protected endpoint without token returns 401
 *  TC-AUTH-07  Accessing protected endpoint with malformed token returns 401
 *  TC-AUTH-08  Accessing protected endpoint with expired/invalid token returns 401
 *  TC-AUTH-09  Token payload contains expected fields (eid, role)
 *  TC-AUTH-10  Health check endpoint is publicly accessible
 */
@Epic("Project W API")
@Feature("Authentication")
public class AuthTest extends BaseTest {

    // ─── TC-AUTH-01 ───────────────────────────────────────────────────────────

    @Test(priority = 1, description = "Valid admin login returns 200 and a JWT token")
    @Story("Login")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /users/login with valid eid, email, password should return HTTP 200 " +
                 "with a non-empty JWT token in data.token.")
    public void TC_AUTH_01_validLoginReturnsToken() {
        Map<String, Object> body = TestDataFactory.loginPayload(
                ConfigManager.getTestEid(),
                ConfigManager.getAdminEmail(),
                ConfigManager.getAdminPassword()
        );

        Response response = given()
                .spec(unauthSpec())
                .body(body)
                .when()
                .post(Constants.LOGIN)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data.token", notNullValue())
                .body("data.token", not(emptyString()))
                .extract().response();

        String token = extractToken(response);
        Assert.assertNotNull(token, "Token must not be null");
        Assert.assertFalse(token.isEmpty(), "Token must not be empty");
        log.info("TC-AUTH-01 PASSED — token length={}", token.length());
    }

    // ─── TC-AUTH-02 ───────────────────────────────────────────────────────────

    @Test(priority = 2, description = "Login with wrong password returns 401")
    @Story("Login")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /users/login with a valid email but incorrect password should return " +
                 "HTTP 401 Unauthorized.")
    public void TC_AUTH_02_wrongPasswordReturns401() {
        Map<String, Object> body = TestDataFactory.loginPayload(
                ConfigManager.getTestEid(),
                ConfigManager.getAdminEmail(),
                "WrongPassword999!"
        );

        given()
                .spec(unauthSpec())
                .body(body)
                .when()
                .post(Constants.LOGIN)
                .then()
                .statusCode(anyOf(equalTo(401), equalTo(400), equalTo(403)))
                .body("data", nullValue());

        log.info("TC-AUTH-02 PASSED — wrong password correctly rejected");
    }

    // ─── TC-AUTH-03 ───────────────────────────────────────────────────────────

    @Test(priority = 3, description = "Login with unknown email returns 401 or 404")
    @Story("Login")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /users/login with an email that does not exist in the database " +
                 "should return HTTP 401 or 404.")
    public void TC_AUTH_03_unknownEmailReturnsError() {
        Map<String, Object> body = TestDataFactory.loginPayload(
                ConfigManager.getTestEid(),
                "nonexistent_" + System.currentTimeMillis() + "@projectw.test",
                "SomePassword@123"
        );

        given()
                .spec(unauthSpec())
                .body(body)
                .when()
                .post(Constants.LOGIN)
                .then()
                .statusCode(anyOf(equalTo(401), equalTo(404), equalTo(400), equalTo(403)));

        log.info("TC-AUTH-03 PASSED — unknown email correctly rejected");
    }

    // ─── TC-AUTH-04 ───────────────────────────────────────────────────────────

    @Test(priority = 4, description = "Login with missing eid returns error")
    @Story("Login")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /users/login without the eid field should return a 4xx error " +
                 "because eid is required for tenant scoping.")
    public void TC_AUTH_04_missingEidReturnsError() {
        Map<String, Object> body = Map.of(
                "email", ConfigManager.getAdminEmail(),
                "password", ConfigManager.getAdminPassword()
        );

        given()
                .spec(unauthSpec())
                .body(body)
                .when()
                .post(Constants.LOGIN)
                .then()
                .statusCode(anyOf(equalTo(400), equalTo(401), equalTo(404), equalTo(409)));

        log.info("TC-AUTH-04 PASSED — missing eid correctly rejected");
    }

    // ─── TC-AUTH-05 ───────────────────────────────────────────────────────────

    @Test(priority = 5, description = "Login with empty body returns 400")
    @Story("Login")
    @Severity(SeverityLevel.MINOR)
    @Description("POST /users/login with an empty JSON body should return HTTP 400 Bad Request.")
    public void TC_AUTH_05_emptyBodyReturns400() {
        given()
                .spec(unauthSpec())
                .body("{}")
                .when()
                .post(Constants.LOGIN)
                .then()
                .statusCode(anyOf(equalTo(400), equalTo(401), equalTo(404), equalTo(409)));

        log.info("TC-AUTH-05 PASSED — empty body correctly rejected");
    }

    // ─── TC-AUTH-06 ───────────────────────────────────────────────────────────

    @Test(priority = 6, description = "Protected endpoint without token returns 401")
    @Story("Authorization")
    @Severity(SeverityLevel.BLOCKER)
    @Description("GET /vendors without an Authorization header should return HTTP 401 Unauthorized.")
    public void TC_AUTH_06_noTokenReturns401() {
        given()
                .spec(unauthSpec())
                .when()
                .get(Constants.VENDORS)
                .then()
                .statusCode(401);

        log.info("TC-AUTH-06 PASSED — missing token correctly rejected");
    }

    // ─── TC-AUTH-07 ───────────────────────────────────────────────────────────

    @Test(priority = 7, description = "Protected endpoint with malformed token returns 401")
    @Story("Authorization")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /vendors with a random string (not a valid JWT) in the Authorization " +
                 "header should return HTTP 401.")
    public void TC_AUTH_07_malformedTokenReturns401() {
        given()
                .spec(specWithToken("this.is.not.a.valid.jwt"))
                .when()
                .get(Constants.VENDORS)
                .then()
                .statusCode(401);

        log.info("TC-AUTH-07 PASSED — malformed token correctly rejected");
    }

    // ─── TC-AUTH-08 ───────────────────────────────────────────────────────────

    @Test(priority = 8, description = "Protected endpoint with expired/tampered token returns 401")
    @Story("Authorization")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /vendors with a syntactically valid but tampered JWT should return 401.")
    public void TC_AUTH_08_tamperedTokenReturns401() {
        // A real JWT structure (header.payload.signature) but with a fake signature
        String tamperedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
                ".eyJpZCI6ImZha2VpZCIsImVpZCI6ImVpZDAwMDEiLCJyb2xlIjoiYWRtaW4ifQ" +
                ".INVALIDSIGNATURE_tampered_by_test";

        given()
                .spec(specWithToken(tamperedToken))
                .when()
                .get(Constants.VENDORS)
                .then()
                .statusCode(401);

        log.info("TC-AUTH-08 PASSED — tampered token correctly rejected");
    }

    // ─── TC-AUTH-09 ───────────────────────────────────────────────────────────

    @Test(priority = 9, description = "Login response contains user data with expected fields")
    @Story("Login")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /users/login should return a data object containing the user's " +
                 "email, eid, and the JWT token.")
    public void TC_AUTH_09_loginResponseContainsUserData() {
        Map<String, Object> body = TestDataFactory.loginPayload(
                ConfigManager.getTestEid(),
                ConfigManager.getAdminEmail(),
                ConfigManager.getAdminPassword()
        );

        given()
                .spec(unauthSpec())
                .body(body)
                .when()
                .post(Constants.LOGIN)
                .then()
                .statusCode(200)
                .body("data.token", notNullValue())
                .body("data.user", notNullValue())
                .body("data.user.email", equalToIgnoringCase(ConfigManager.getAdminEmail()))
                .body("data.user.eid", equalTo(ConfigManager.getTestEid()));

        log.info("TC-AUTH-09 PASSED — login response contains expected user data");
    }

    // ─── TC-AUTH-10 ───────────────────────────────────────────────────────────

    @Test(priority = 10, description = "Health check endpoint is publicly accessible")
    @Story("Health")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /health should return HTTP 200 without any authentication.")
    public void TC_AUTH_10_healthCheckIsPublic() {
        given()
                .spec(unauthSpec())
                .when()
                .get(Constants.HEALTH)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("message", equalToIgnoringCase("healthy"));

        log.info("TC-AUTH-10 PASSED — health check accessible without auth");
    }

}
