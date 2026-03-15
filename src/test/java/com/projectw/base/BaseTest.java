package com.projectw.base;

import com.projectw.listeners.ExtentReportListener;
import com.projectw.utils.ConfigManager;
import com.projectw.utils.Constants;
import com.projectw.utils.TestDataFactory;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Listeners;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Base class for all Project W API tests.
 *
 * Responsibilities:
 *  - Configures RestAssured base URI, base path, timeouts
 *  - Performs admin and employee login once per suite and stores both JWT tokens
 *  - Provides helper methods: authSpec(), employeeSpec(), unauthSpec(), extractId(), extractToken()
 *  - Attaches AllureRestAssured filter for request/response logging in reports
 *  - Registers ExtentReportListener so the HTML report is generated on every run
 *
 * Token roles:
 *  - adminToken    → ganesh@gmail.com   (role.hierarchy >= 80, Super Admin)
 *  - employeeToken → prathik@gmail.com  (regular employee, no admin privileges)
 */
@Listeners(ExtentReportListener.class)
public abstract class BaseTest {

    protected static final Logger log = LoggerFactory.getLogger(BaseTest.class);

    /** JWT token for the admin user (ganesh@gmail.com). Shared across all tests. */
    protected static String adminToken;

    /** JWT token for the employee user (prathik@gmail.com). Shared across all tests. */
    protected static String employeeToken;

    /** Enterprise ID used for all test operations. */
    protected static final String TEST_EID = ConfigManager.getTestEid();

    @BeforeSuite(alwaysRun = true)
    public void globalSetup() {
        configureRestAssured();
        adminToken = loginAndGetToken(
                ConfigManager.getTestEid(),
                ConfigManager.getAdminEmail(),
                ConfigManager.getAdminPassword()
        );
        log.info("Suite setup complete. Admin token acquired for eid={} user={}",
                TEST_EID, ConfigManager.getAdminEmail());

        employeeToken = loginAndGetToken(
                ConfigManager.getTestEid(),
                ConfigManager.getEmployeeEmail(),
                ConfigManager.getEmployeePassword()
        );
        log.info("Employee token acquired for user={}", ConfigManager.getEmployeeEmail());
    }

    // ─── RestAssured Configuration ───────────────────────────────────────────

    private void configureRestAssured() {
        RestAssured.baseURI  = ConfigManager.getBaseUrl();
        RestAssured.basePath = ConfigManager.getApiBasePath();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.ALL);
        // Timeouts (connection.timeout, read.timeout) are in config.properties for reference;
        // apply per-request if needed via requestSpec().config() with HttpClientConfig for your RestAssured version.

        log.info("RestAssured configured: baseURI={}{}", ConfigManager.getBaseUrl(),
                ConfigManager.getApiBasePath());
    }

    // ─── Request Specifications ──────────────────────────────────────────────

    /**
     * Returns a RequestSpecification authenticated as the admin user (ganesh@gmail.com).
     * Use for all admin-only operations: admin-edit, admin-create, approve, regularization approve/reject.
     */
    protected RequestSpecification authSpec() {
        return new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .addHeader(Constants.HEADER_AUTHORIZATION, Constants.BEARER_PREFIX + adminToken)
                .addFilter(new AllureRestAssured())
                .build();
    }

    /**
     * Returns a RequestSpecification authenticated as the employee user (prathik@gmail.com).
     * Use for employee-perspective tests: check-in, check-out, break, regularization submit,
     * today summary, history, settings.
     */
    protected RequestSpecification employeeSpec() {
        return new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .addHeader(Constants.HEADER_AUTHORIZATION, Constants.BEARER_PREFIX + employeeToken)
                .addFilter(new AllureRestAssured())
                .build();
    }

    /**
     * Returns a RequestSpecification without auth — for testing public endpoints
     * (login, register) and negative auth tests.
     */
    protected RequestSpecification unauthSpec() {
        return new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .addFilter(new AllureRestAssured())
                .build();
    }

    /**
     * Returns a RequestSpecification with a custom token — for testing
     * cross-tenant access or expired token scenarios.
     */
    protected RequestSpecification specWithToken(String token) {
        return new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .addHeader(Constants.HEADER_AUTHORIZATION, Constants.BEARER_PREFIX + token)
                .addFilter(new AllureRestAssured())
                .build();
    }

    /**
     * Returns a RequestSpecification for multipart requests (e.g. file upload).
     * Does not set Content-Type so RestAssured can set multipart/form-data when using multiPart().
     */
    protected RequestSpecification authSpecForMultipart() {
        return new RequestSpecBuilder()
                .addHeader(Constants.HEADER_AUTHORIZATION, Constants.BEARER_PREFIX + adminToken)
                .addFilter(new AllureRestAssured())
                .build();
    }

    // ─── Auth Helper ─────────────────────────────────────────────────────────

    /**
     * Logs in with the given credentials and returns the JWT token string.
     * Throws if login fails.
     */
    protected String loginAndGetToken(String eid, String email, String password) {
        Map<String, Object> body = TestDataFactory.loginPayload(eid, email, password);

        Response response = given()
                .spec(unauthSpec())
                .body(body)
                .when()
                .post(Constants.LOGIN)
                .then()
                .statusCode(200)
                .extract().response();

        String token = response.jsonPath().getString("data.token");
        if (token == null || token.isEmpty()) {
            throw new RuntimeException("Login succeeded but no token returned for " + email);
        }
        log.debug("Token acquired for {}", email);
        return token;
    }

    // ─── Response Extraction Helpers ─────────────────────────────────────────

    /** Extracts the MongoDB _id from response.data._id */
    protected String extractId(Response response) {
        return response.jsonPath().getString("data._id");
    }

    /** Extracts the JWT token from response.data.token */
    protected String extractToken(Response response) {
        return response.jsonPath().getString("data.token");
    }

    /** Extracts a nested field from the response data object. */
    protected <T> T extractData(Response response, String field, Class<T> type) {
        return response.jsonPath().getObject("data." + field, type);
    }

    /** Extracts the top-level response code field. */
    protected int extractCode(Response response) {
        return response.jsonPath().getInt("code");
    }

    /** Extracts the top-level message field. */
    protected String extractMessage(Response response) {
        return response.jsonPath().getString("message");
    }
}
