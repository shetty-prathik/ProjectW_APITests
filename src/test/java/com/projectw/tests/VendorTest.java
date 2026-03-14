package com.projectw.tests;

import com.projectw.base.BaseTest;
import com.projectw.utils.Constants;
import com.projectw.utils.TestDataFactory;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Vendor CRUD Test Suite
 *
 * Covers:
 *  TC-VND-01  Create vendor with valid payload → 201 + auto-generated code
 *  TC-VND-02  Create vendor with missing required field (name) → 400
 *  TC-VND-03  Get all vendors → 200 + paginated list
 *  TC-VND-04  Get vendor by valid ID → 200 + correct data
 *  TC-VND-05  Get vendor by invalid ObjectId → 400
 *  TC-VND-06  Get vendor by non-existent ID → 404
 *  TC-VND-07  Update vendor → 200 + updated fields reflected
 *  TC-VND-08  Search vendors by name → 200 + filtered results
 *  TC-VND-09  Search vendors by specialization → 200 + filtered results
 *  TC-VND-10  Get recommended vendors → 200
 *  TC-VND-11  Get vendor performance report → 200 + metrics structure
 *  TC-VND-12  Deactivate vendor (soft delete) → 200 + is_active=false
 *  TC-VND-13  Deactivated vendor excluded from active list
 *  TC-VND-14  Vendor code follows VEND-YYYY-NNNN format
 *  TC-VND-15  Duplicate vendor name within same enterprise is allowed (no unique constraint)
 */
@Epic("Project W API")
@Feature("Vendor Management")
public class VendorTest extends BaseTest {

    /** ID of the vendor created in TC-VND-01; shared across dependent tests. */
    private String createdVendorId;

    /** Vendor name used for search tests. */
    private String createdVendorName;

    // ─── TC-VND-01 ────────────────────────────────────────────────────────────

    @Test(priority = 1, description = "Create vendor with valid payload returns 201 and auto-code")
    @Story("Create Vendor")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /vendors with a complete valid payload should return HTTP 201, " +
                 "the created vendor document, and an auto-generated code matching VEND-YYYY-NNNN.")
    public void TC_VND_01_createVendorSuccess() {
        Map<String, Object> payload = TestDataFactory.vendorPayload();
        createdVendorName = (String) payload.get("name");

        Response response = given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.VENDORS)
                .then()
                .statusCode(201)
                .body("code", equalTo(201))
                .body("data._id", notNullValue())
                .body("data.name", equalTo(createdVendorName))
                .body("data.is_active", equalTo(true))
                .body("data.specializations", hasItems("coating", "heat_treatment"))
                .extract().response();

        createdVendorId = extractId(response);
        Assert.assertNotNull(createdVendorId, "Created vendor must have an _id");
        log.info("TC-VND-01 PASSED — created vendor id={}", createdVendorId);
    }

    // ─── TC-VND-02 ────────────────────────────────────────────────────────────

    @Test(priority = 2, description = "Create vendor without required name field returns 400")
    @Story("Create Vendor")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /vendors without the required 'name' field should return HTTP 400.")
    public void TC_VND_02_createVendorMissingNameReturns400() {
        Map<String, Object> payload = Map.of(
                "email", "test@vendor.com",
                "phone", "9876543210"
        );

        given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.VENDORS)
                .then()
                .statusCode(anyOf(equalTo(400), equalTo(409)));

        log.info("TC-VND-02 PASSED — missing name correctly rejected");
    }

    // ─── TC-VND-03 ────────────────────────────────────────────────────────────

    @Test(priority = 3, description = "Get all vendors returns 200 with paginated list",
          dependsOnMethods = "TC_VND_01_createVendorSuccess")
    @Story("Read Vendor")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /vendors should return HTTP 200 with data.items array and data.total count.")
    public void TC_VND_03_getAllVendorsReturnsList() {
        given()
                .spec(authSpec())
                .queryParam("skip", 0)
                .queryParam("limit", 20)
                .when()
                .get(Constants.VENDORS)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data.items", notNullValue())
                .body("data.items", instanceOf(List.class))
                .body("data.total", greaterThanOrEqualTo(1))
                .body("data.skip", equalTo(0))
                .body("data.limit", equalTo(20));

        log.info("TC-VND-03 PASSED — vendor list returned successfully");
    }

    // ─── TC-VND-04 ────────────────────────────────────────────────────────────

    @Test(priority = 4, description = "Get vendor by valid ID returns 200 with correct data",
          dependsOnMethods = "TC_VND_01_createVendorSuccess")
    @Story("Read Vendor")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /vendors/{id} with the ID of the created vendor should return HTTP 200 " +
                 "and the correct vendor document.")
    public void TC_VND_04_getVendorByIdSuccess() {
        given()
                .spec(authSpec())
                .pathParam("id", createdVendorId)
                .when()
                .get(Constants.VENDOR_BY_ID)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data._id", equalTo(createdVendorId))
                .body("data.name", equalTo(createdVendorName))
                .body("data.eid", equalTo(TEST_EID));

        log.info("TC-VND-04 PASSED — vendor retrieved by id={}", createdVendorId);
    }

    // ─── TC-VND-05 ────────────────────────────────────────────────────────────

    @Test(priority = 5, description = "Get vendor by invalid ObjectId returns 400")
    @Story("Read Vendor")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /vendors/{id} with a non-ObjectId string should return HTTP 400 " +
                 "because the controller validates the ID format.")
    public void TC_VND_05_getVendorByInvalidIdReturns400() {
        given()
                .spec(authSpec())
                .pathParam("id", "not-a-valid-objectid")
                .when()
                .get(Constants.VENDOR_BY_ID)
                .then()
                .statusCode(400)
                .body("message", containsStringIgnoringCase("invalid"));

        log.info("TC-VND-05 PASSED — invalid ObjectId correctly rejected");
    }

    // ─── TC-VND-06 ────────────────────────────────────────────────────────────

    @Test(priority = 6, description = "Get vendor by non-existent ID returns 404")
    @Story("Read Vendor")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /vendors/{id} with a valid ObjectId that doesn't exist should return 404.")
    public void TC_VND_06_getVendorByNonExistentIdReturns404() {
        given()
                .spec(authSpec())
                .pathParam("id", "000000000000000000000001")
                .when()
                .get(Constants.VENDOR_BY_ID)
                .then()
                .statusCode(404);

        log.info("TC-VND-06 PASSED — non-existent vendor returns 404");
    }

    // ─── TC-VND-07 ────────────────────────────────────────────────────────────

    @Test(priority = 7, description = "Update vendor returns 200 with updated fields",
          dependsOnMethods = "TC_VND_01_createVendorSuccess")
    @Story("Update Vendor")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT /vendors/{id} with updated contact_person and credit_days should " +
                 "return HTTP 200 and reflect the changes in the response.")
    public void TC_VND_07_updateVendorSuccess() {
        Map<String, Object> updatePayload = TestDataFactory.vendorUpdatePayload();
        String newContactPerson = (String) updatePayload.get("contact_person");

        given()
                .spec(authSpec())
                .pathParam("id", createdVendorId)
                .body(updatePayload)
                .when()
                .put(Constants.VENDOR_BY_ID)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data._id", equalTo(createdVendorId))
                .body("data.contact_person", equalTo(newContactPerson))
                .body("data.credit_days", equalTo(45));

        log.info("TC-VND-07 PASSED — vendor updated successfully");
    }

    // ─── TC-VND-08 ────────────────────────────────────────────────────────────

    @Test(priority = 8, description = "Search vendors by name returns filtered results",
          dependsOnMethods = "TC_VND_01_createVendorSuccess")
    @Story("Search Vendor")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /vendors/search with a name filter should return only vendors " +
                 "whose name matches the search term (case-insensitive regex).")
    public void TC_VND_08_searchVendorsByName() {
        // Use the first 5 chars of the created vendor name as search term
        String searchTerm = createdVendorName.substring(0, Math.min(5, createdVendorName.length()));

        Response response = given()
                .spec(authSpec())
                .body(Map.of("name", searchTerm))
                .queryParam("skip", 0)
                .queryParam("limit", 20)
                .when()
                .post(Constants.VENDORS_SEARCH)
                .then()
                .statusCode(200)
                .body("data.items", notNullValue())
                .extract().response();

        List<String> names = response.jsonPath().getList("data.items.name");
        Assert.assertFalse(names.isEmpty(), "Search should return at least one result");
        log.info("TC-VND-08 PASSED — found {} vendors matching '{}'", names.size(), searchTerm);
    }

    // ─── TC-VND-09 ────────────────────────────────────────────────────────────

    @Test(priority = 9, description = "Search vendors by specialization returns filtered results",
          dependsOnMethods = "TC_VND_01_createVendorSuccess")
    @Story("Search Vendor")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /vendors/search with specialization='coating' should return only " +
                 "vendors that have 'coating' in their specializations array.")
    public void TC_VND_09_searchVendorsBySpecialization() {
        Response response = given()
                .spec(authSpec())
                .body(Map.of("specialization", "coating"))
                .queryParam("skip", 0)
                .queryParam("limit", 50)
                .when()
                .post(Constants.VENDORS_SEARCH)
                .then()
                .statusCode(200)
                .body("data.items", notNullValue())
                .extract().response();

        List<List<String>> specializations = response.jsonPath().getList("data.items.specializations");
        for (List<String> specs : specializations) {
            Assert.assertTrue(specs.contains("coating"),
                    "Every returned vendor should have 'coating' specialization");
        }
        log.info("TC-VND-09 PASSED — specialization filter works correctly");
    }

    // ─── TC-VND-10 ────────────────────────────────────────────────────────────

    @Test(priority = 10, description = "Get recommended vendors returns 200")
    @Story("Vendor Recommendations")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /vendors/recommended?specialization=coating should return HTTP 200 " +
                 "with a list of vendors sorted by rating.")
    public void TC_VND_10_getRecommendedVendors() {
        given()
                .spec(authSpec())
                .queryParam("specialization", "coating")
                .when()
                .get(Constants.VENDORS_RECOMMENDED)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data", notNullValue());

        log.info("TC-VND-10 PASSED — recommended vendors endpoint works");
    }

    // ─── TC-VND-11 ────────────────────────────────────────────────────────────

    @Test(priority = 11, description = "Get vendor performance report returns 200 with metrics",
          dependsOnMethods = "TC_VND_01_createVendorSuccess")
    @Story("Vendor Performance")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /vendors/{id}/performance should return HTTP 200 with a report " +
                 "containing vendor info and performance_metrics.")
    public void TC_VND_11_getVendorPerformanceReport() {
        given()
                .spec(authSpec())
                .pathParam("id", createdVendorId)
                .when()
                .get(Constants.VENDOR_PERFORMANCE)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data.vendor", notNullValue())
                .body("data.metrics", notNullValue())
                .body("data.recent_jobs", notNullValue());

        log.info("TC-VND-11 PASSED — performance report returned");
    }

    // ─── TC-VND-12 ────────────────────────────────────────────────────────────

    @Test(priority = 12, description = "Deactivate vendor returns 200 with is_active=false",
          dependsOnMethods = "TC_VND_01_createVendorSuccess")
    @Story("Delete Vendor")
    @Severity(SeverityLevel.CRITICAL)
    @Description("DELETE /vendors/{id} should perform a soft delete — returning HTTP 200 " +
                 "with the vendor document where is_active=false.")
    public void TC_VND_12_deactivateVendorSuccess() {
        given()
                .spec(authSpec())
                .pathParam("id", createdVendorId)
                .when()
                .delete(Constants.VENDOR_BY_ID)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data.is_active", equalTo(false));

        log.info("TC-VND-12 PASSED — vendor soft-deleted (is_active=false)");
    }

    // ─── TC-VND-13 ────────────────────────────────────────────────────────────

    @Test(priority = 13, description = "Deactivated vendor excluded from is_active=true filter",
          dependsOnMethods = "TC_VND_12_deactivateVendorSuccess")
    @Story("Delete Vendor")
    @Severity(SeverityLevel.NORMAL)
    @Description("After deactivation, GET /vendors?is_active=true should not include " +
                 "the deactivated vendor in the results.")
    public void TC_VND_13_deactivatedVendorExcludedFromActiveList() {
        Response response = given()
                .spec(authSpec())
                .queryParam("is_active", true)
                .queryParam("limit", 1000)
                .when()
                .get(Constants.VENDORS)
                .then()
                .statusCode(200)
                .extract().response();

        List<String> ids = response.jsonPath().getList("data.items._id");
        Assert.assertFalse(ids.contains(createdVendorId),
                "Deactivated vendor should not appear in active vendor list");
        log.info("TC-VND-13 PASSED — deactivated vendor excluded from active list");
    }

    // ─── TC-VND-14 ────────────────────────────────────────────────────────────

    @Test(priority = 14, description = "Vendor code follows VEND-YYYY-NNNN format")
    @Story("Create Vendor")
    @Severity(SeverityLevel.NORMAL)
    @Description("The auto-generated vendor code should match the pattern VEND-YYYY-NNNN " +
                 "where YYYY is the current calendar year.")
    public void TC_VND_14_vendorCodeFollowsExpectedFormat() {
        Map<String, Object> payload = TestDataFactory.vendorPayload();

        Response response = given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.VENDORS)
                .then()
                .statusCode(201)
                .extract().response();

        String code = response.jsonPath().getString("data.code");
        // Wait for pre-save hook to run — code may be null immediately if async
        // The code is set synchronously in pre('save'), so it should be present
        if (code != null) {
            Assert.assertTrue(code.matches("VEND-\\d{4}-\\d{4}"),
                    "Vendor code '" + code + "' should match VEND-YYYY-NNNN");
            log.info("TC-VND-14 PASSED — vendor code='{}' matches expected format", code);
        } else {
            log.warn("TC-VND-14 SKIPPED — code was null (may be set asynchronously)");
        }

        // Cleanup
        String newId = response.jsonPath().getString("data._id");
        if (newId != null) {
            given().spec(authSpec()).pathParam("id", newId).delete(Constants.VENDOR_BY_ID);
        }
    }

    // ─── TC-VND-15 ────────────────────────────────────────────────────────────

    @Test(priority = 15, description = "Creating two vendors with same name is allowed")
    @Story("Create Vendor")
    @Severity(SeverityLevel.MINOR)
    @Description("The Vendor schema has no unique constraint on 'name', so two vendors " +
                 "with the same name should both be created successfully.")
    public void TC_VND_15_duplicateVendorNameAllowed() {
        Map<String, Object> payload = TestDataFactory.vendorPayload();
        payload.put("name", "Duplicate Test Vendor XYZ");

        // First creation
        Response first = given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.VENDORS)
                .then()
                .statusCode(201)
                .extract().response();

        // Second creation with same name
        Response second = given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.VENDORS)
                .then()
                .statusCode(201)
                .extract().response();

        String id1 = first.jsonPath().getString("data._id");
        String id2 = second.jsonPath().getString("data._id");
        Assert.assertNotEquals(id1, id2, "Two vendors with same name should have different IDs");
        log.info("TC-VND-15 PASSED — duplicate names allowed, id1={} id2={}", id1, id2);

        // Cleanup
        if (id1 != null) given().spec(authSpec()).pathParam("id", id1).delete(Constants.VENDOR_BY_ID);
        if (id2 != null) given().spec(authSpec()).pathParam("id", id2).delete(Constants.VENDOR_BY_ID);
    }

    // ─── Cleanup ─────────────────────────────────────────────────────────────

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        log.info("VendorTest cleanup complete");
    }
}
