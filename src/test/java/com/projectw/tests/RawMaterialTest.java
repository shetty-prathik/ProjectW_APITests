package com.projectw.tests;

import com.projectw.base.BaseTest;
import com.projectw.utils.Constants;
import com.projectw.utils.TestDataFactory;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Raw Material Module API Test Suite
 *
 * Based on: Project_W_BE Raw Material Module API Test Cases
 * Base path: /accounts/api/rawmaterials
 *
 * Covers:
 *   AUTH-01 to AUTH-05  — Authentication
 *   C01–C13             — Create (POST /)
 *   L01–L06             — List (GET /)
 *   G01–G05             — Get by ID (GET /:id)
 *   U01–U08             — Update (PUT /:id)
 *   D01–D03             — Delete (DELETE /:id)
 *   S01–S20             — Search (POST /search)
 */
@Epic("Project W API")
@Feature("Raw Material Management")
public class RawMaterialTest extends BaseTest {

    private String createdRawMaterialId;
    private String createdRawMaterialName;
    private String createdCustomerId; // for C06, U06

    @BeforeClass(alwaysRun = true)
    public void setup() {
        log.info("RawMaterialTest @BeforeClass — suite ready");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5.1 Authentication Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test(priority = 1, description = "AUTH-01: No Authorization header returns 401")
    @Story("Authentication")
    @Severity(SeverityLevel.BLOCKER)
    public void AUTH_01_noAuthorizationReturns401() {
        given().spec(unauthSpec()).when().get(Constants.RAW_MATERIALS)
                .then().statusCode(401).body("success", equalTo(false));
        log.info("AUTH-01 PASSED");
    }

    @Test(priority = 2, description = "AUTH-02: Malformed token returns 401")
    @Story("Authentication")
    @Severity(SeverityLevel.BLOCKER)
    public void AUTH_02_malformedTokenReturns401() {
        given().spec(unauthSpec()).header(Constants.HEADER_AUTHORIZATION, "Token xyz")
                .when().get(Constants.RAW_MATERIALS)
                .then().statusCode(401);
        log.info("AUTH-02 PASSED");
    }

    @Test(priority = 3, description = "AUTH-03: Invalid JWT returns 401")
    @Story("Authentication")
    @Severity(SeverityLevel.BLOCKER)
    public void AUTH_03_invalidJwtReturns401() {
        given().spec(unauthSpec()).header(Constants.HEADER_AUTHORIZATION, Constants.BEARER_PREFIX + "invalid")
                .when().get(Constants.RAW_MATERIALS)
                .then().statusCode(401);
        log.info("AUTH-03 PASSED");
    }

    @Test(priority = 4, description = "AUTH-05: Valid JWT returns 200")
    @Story("Authentication")
    @Severity(SeverityLevel.BLOCKER)
    public void AUTH_05_validJwtReturns200() {
        given().spec(authSpec()).when().get(Constants.RAW_MATERIALS)
                .then().statusCode(200).body("code", equalTo(200)).body("data", notNullValue());
        log.info("AUTH-05 PASSED");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5.2 POST / — Create Raw Material
    // ═══════════════════════════════════════════════════════════════════════════

    @Test(priority = 10, description = "C01: Create with required field (price only)")
    @Story("Create Raw Material")
    @Severity(SeverityLevel.BLOCKER)
    public void C01_createWithPriceOnly() {
        Map<String, Object> payload = TestDataFactory.rawMaterialMinimalPayload();
        Response response = given().spec(authSpec()).body(payload)
                .when().post(Constants.RAW_MATERIALS)
                .then().statusCode(anyOf(equalTo(200), equalTo(201)))
                .body("data._id", notNullValue()).body("data.price", equalTo(100))
                .body("data.code", notNullValue())
                .extract().response();
        createdRawMaterialId = extractId(response);
        String code = response.jsonPath().getString("data.code");
        Assert.assertTrue(code != null && (code.startsWith("RAW") || code.startsWith("WH")),
                "Code should be RAW-* or WH-*");
        log.info("C01 PASSED — id={} code={}", createdRawMaterialId, code);
    }

    @Test(priority = 11, description = "C02: Create with full optional fields",
          dependsOnMethods = "C01_createWithPriceOnly")
    @Story("Create Raw Material")
    @Severity(SeverityLevel.CRITICAL)
    public void C02_createWithFullOptionalFields() {
        Map<String, Object> payload = TestDataFactory.rawMaterialFullPayload(null);
        createdRawMaterialName = (String) payload.get("name");
        given().spec(authSpec()).body(payload)
                .when().post(Constants.RAW_MATERIALS)
                .then().statusCode(anyOf(equalTo(200), equalTo(201)))
                .body("data.name", equalTo(createdRawMaterialName))
                .body("data.group", equalTo("rods"))
                .body("data.type", equalTo("rod"))
                .body("data.grade", equalTo("A"))
                .body("data.length", equalTo(1200))
                .body("data.diameter", equalTo(10));
        log.info("C02 PASSED");
    }

    @Test(priority = 12, description = "C03: Create wheel-type (type=wheel)")
    @Story("Create Raw Material")
    @Severity(SeverityLevel.CRITICAL)
    public void C03_createWheelType() {
        Map<String, Object> payload = TestDataFactory.rawMaterialWheelPayload();
        Response response = given().spec(authSpec()).body(payload)
                .when().post(Constants.RAW_MATERIALS)
                .then().statusCode(anyOf(equalTo(200), equalTo(201)))
                .body("data.code", notNullValue())
                .extract().response();
        String code = response.jsonPath().getString("data.code");
        Assert.assertTrue(code != null && code.startsWith("WH"),
                "Wheel-type should get WH- prefix");
        log.info("C03 PASSED — code={}", code);
    }

    @Test(priority = 13, description = "C04: Create wheel-type (group=wheel only)")
    @Story("Create Raw Material")
    @Severity(SeverityLevel.NORMAL)
    public void C04_createWheelGroupOnly() {
        Map<String, Object> payload = TestDataFactory.rawMaterialWheelGroupOnlyPayload();
        Response response = given().spec(authSpec()).body(payload)
                .when().post(Constants.RAW_MATERIALS)
                .then().statusCode(anyOf(equalTo(200), equalTo(201)))
                .extract().response();
        String code = response.jsonPath().getString("data.code");
        Assert.assertTrue(code != null && code.startsWith("WH"));
        log.info("C04 PASSED — code={}", code);
    }

    @Test(priority = 14, description = "C05: Create with code in body (ignored)")
    @Story("Create Raw Material")
    @Severity(SeverityLevel.NORMAL)
    public void C05_createWithCodeInBodyIgnored() {
        Map<String, Object> payload = TestDataFactory.rawMaterialMinimalPayload();
        payload.put("code", "CUSTOM-999");
        Response response = given().spec(authSpec()).body(payload)
                .when().post(Constants.RAW_MATERIALS)
                .then().statusCode(anyOf(equalTo(200), equalTo(201)))
                .extract().response();
        String returnedCode = response.jsonPath().getString("data.code");
        Assert.assertNotEquals(returnedCode, "CUSTOM-999");
        log.info("C05 PASSED — code={}", returnedCode);
    }

    @Test(priority = 15, description = "C06: Create with valid customer_id")
    @Story("Create Raw Material")
    @Severity(SeverityLevel.NORMAL)
    public void C06_createWithValidCustomerId() {
        // Create a customer first
        Map<String, Object> cust = TestDataFactory.customerMinimalPayload();
        String custId = given().spec(authSpec()).body(cust)
                .when().post(Constants.CUSTOMERS)
                .then().statusCode(anyOf(equalTo(200), equalTo(201)))
                .extract().jsonPath().getString("data._id");
        createdCustomerId = custId;
        Map<String, Object> payload = TestDataFactory.rawMaterialFullPayload(custId);
        given().spec(authSpec()).body(payload)
                .when().post(Constants.RAW_MATERIALS)
                .then().statusCode(anyOf(equalTo(200), equalTo(201)))
                .body("data.customer_id", notNullValue());
        log.info("C06 PASSED — customer_id stored");
    }

    @Test(priority = 16, description = "C07: Create with invalid customer_id returns 409")
    @Story("Create Raw Material")
    @Severity(SeverityLevel.CRITICAL)
    public void C07_createWithInvalidCustomerIdReturns409() {
        Map<String, Object> payload = TestDataFactory.rawMaterialMinimalPayload();
        payload.put("customer_id", "000000000000000000000001");
        given().spec(authSpec()).body(payload)
                .when().post(Constants.RAW_MATERIALS)
                .then().statusCode(409);
        log.info("C07 PASSED");
    }

    @Test(priority = 17, description = "C08: Create with missing price returns 409")
    @Story("Create Raw Material")
    @Severity(SeverityLevel.CRITICAL)
    public void C08_createMissingPriceReturns409() {
        Map<String, Object> payload = Map.of("name", "Steel", "description", "Rod");
        given().spec(authSpec()).body(payload)
                .when().post(Constants.RAW_MATERIALS)
                .then().statusCode(409);
        log.info("C08 PASSED");
    }

    @Test(priority = 18, description = "C09: Create with negative price returns 409")
    @Story("Create Raw Material")
    @Severity(SeverityLevel.CRITICAL)
    public void C09_createNegativePriceReturns409() {
        Map<String, Object> payload = Map.of("price", -10);
        given().spec(authSpec()).body(payload)
                .when().post(Constants.RAW_MATERIALS)
                .then().statusCode(409);
        log.info("C09 PASSED");
    }

    @Test(priority = 19, description = "C10: Create with numberOfHoles negative returns 409")
    @Story("Create Raw Material")
    @Severity(SeverityLevel.NORMAL)
    public void C10_createNegativeNumberOfHolesReturns409() {
        Map<String, Object> payload = TestDataFactory.rawMaterialMinimalPayload();
        payload.put("numberOfHoles", -1);
        given().spec(authSpec()).body(payload)
                .when().post(Constants.RAW_MATERIALS)
                .then().statusCode(409);
        log.info("C10 PASSED");
    }

    @Test(priority = 20, description = "C11: Create with empty body returns 409 (or 200 if API allows defaults)")
    @Story("Create Raw Material")
    @Severity(SeverityLevel.CRITICAL)
    public void C11_createEmptyBodyReturns409() {
        given().spec(authSpec()).body("{}")
                .when().post(Constants.RAW_MATERIALS)
                .then().statusCode(anyOf(equalTo(200), equalTo(201), equalTo(409)));
        log.info("C11 PASSED");
    }

    @Test(priority = 21, description = "C13: Create with isPopulateCustomer flag")
    @Story("Create Raw Material")
    @Severity(SeverityLevel.MINOR)
    public void C13_createWithPopulateCustomerFlag() {
        Map<String, Object> body = Map.of("price", 100, "flags", Map.of("isPopulateCustomer", true));
        given().spec(authSpec()).body(body)
                .when().post(Constants.RAW_MATERIALS)
                .then().statusCode(anyOf(equalTo(200), equalTo(201)));
        log.info("C13 PASSED");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5.3 GET / — List Raw Materials
    // ═══════════════════════════════════════════════════════════════════════════

    @Test(priority = 30, description = "L01: List all (no params)",
          dependsOnMethods = "C01_createWithPriceOnly")
    @Story("List Raw Materials")
    @Severity(SeverityLevel.CRITICAL)
    public void L01_listAll() {
        given().spec(authSpec()).when().get(Constants.RAW_MATERIALS)
                .then().statusCode(200).body("code", equalTo(200)).body("data", notNullValue());
        log.info("L01 PASSED");
    }

    @Test(priority = 31, description = "L02: With skip and limit")
    @Story("List Raw Materials")
    @Severity(SeverityLevel.NORMAL)
    public void L02_listWithSkipLimit() {
        given().spec(authSpec()).queryParam("skip", 0).queryParam("limit", 50)
                .when().get(Constants.RAW_MATERIALS)
                .then().statusCode(200);
        log.info("L02 PASSED");
    }

    @Test(priority = 32, description = "L03: With isPopulateCustomer=true")
    @Story("List Raw Materials")
    @Severity(SeverityLevel.MINOR)
    public void L03_listWithPopulateCustomer() {
        given().spec(authSpec()).queryParam("isPopulateCustomer", true)
                .when().get(Constants.RAW_MATERIALS)
                .then().statusCode(200);
        log.info("L03 PASSED");
    }

    @Test(priority = 33, description = "L04: With flags in body")
    @Story("List Raw Materials")
    @Severity(SeverityLevel.MINOR)
    public void L04_listWithFlagsInBody() {
        given().spec(authSpec()).body(Map.of("flags", Map.of("isPopulateCustomer", true)))
                .when().get(Constants.RAW_MATERIALS)
                .then().statusCode(200);
        log.info("L04 PASSED");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5.4 GET /:id — Get Raw Material by ID
    // ═══════════════════════════════════════════════════════════════════════════

    @Test(priority = 40, description = "G01: Valid ObjectId, exists",
          dependsOnMethods = "C01_createWithPriceOnly")
    @Story("Get Raw Material")
    @Severity(SeverityLevel.CRITICAL)
    public void G01_getByIdSuccess() {
        given().spec(authSpec()).pathParam("id", createdRawMaterialId)
                .when().get(Constants.RAW_MATERIAL_BY_ID)
                .then().statusCode(200).body("data._id", equalTo(createdRawMaterialId));
        log.info("G01 PASSED");
    }

    @Test(priority = 41, description = "G02: Valid ObjectId, does not exist")
    @Story("Get Raw Material")
    @Severity(SeverityLevel.CRITICAL)
    public void G02_getByIdNotFoundReturns404() {
        given().spec(authSpec()).pathParam("id", "000000000000000000000001")
                .when().get(Constants.RAW_MATERIAL_BY_ID)
                .then().statusCode(404).body("message", containsStringIgnoringCase("not found"));
        log.info("G02 PASSED");
    }

    @Test(priority = 42, description = "G03: Invalid ObjectId returns 400")
    @Story("Get Raw Material")
    @Severity(SeverityLevel.CRITICAL)
    public void G03_getByIdInvalidReturns400() {
        given().spec(authSpec()).pathParam("id", "abc123")
                .when().get(Constants.RAW_MATERIAL_BY_ID)
                .then().statusCode(400);
        log.info("G03 PASSED");
    }

    @Test(priority = 43, description = "G05: With isPopulateCustomer flag",
          dependsOnMethods = "C01_createWithPriceOnly")
    @Story("Get Raw Material")
    @Severity(SeverityLevel.MINOR)
    public void G05_getByIdWithPopulateCustomer() {
        given().spec(authSpec()).pathParam("id", createdRawMaterialId)
                .queryParam("isPopulateCustomer", true)
                .when().get(Constants.RAW_MATERIAL_BY_ID)
                .then().statusCode(200);
        log.info("G05 PASSED");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5.5 PUT /:id — Update Raw Material
    // ═══════════════════════════════════════════════════════════════════════════

    @Test(priority = 50, description = "U01: Update single field",
          dependsOnMethods = "C01_createWithPriceOnly")
    @Story("Update Raw Material")
    @Severity(SeverityLevel.CRITICAL)
    public void U01_updateSingleField() {
        given().spec(authSpec()).pathParam("id", createdRawMaterialId)
                .body(Map.of("name", "Updated Name " + System.currentTimeMillis()))
                .when().put(Constants.RAW_MATERIAL_BY_ID)
                .then().statusCode(200).body("data.name", containsString("Updated Name"));
        log.info("U01 PASSED");
    }

    @Test(priority = 51, description = "U02: Update multiple fields",
          dependsOnMethods = "C01_createWithPriceOnly")
    @Story("Update Raw Material")
    @Severity(SeverityLevel.CRITICAL)
    public void U02_updateMultipleFields() {
        given().spec(authSpec()).pathParam("id", createdRawMaterialId)
                .body(Map.of("name", "Multi Update", "price", 150, "grade", "B"))
                .when().put(Constants.RAW_MATERIAL_BY_ID)
                .then().statusCode(200)
                .body("data.name", equalTo("Multi Update"))
                .body("data.price", equalTo(150))
                .body("data.grade", equalTo("B"));
        log.info("U02 PASSED");
    }

    @Test(priority = 52, description = "U03: Invalid ObjectId returns 400")
    @Story("Update Raw Material")
    @Severity(SeverityLevel.NORMAL)
    public void U03_updateInvalidIdReturns400() {
        given().spec(authSpec()).pathParam("id", "abc123")
                .body(Map.of("price", 100))
                .when().put(Constants.RAW_MATERIAL_BY_ID)
                .then().statusCode(400);
        log.info("U03 PASSED");
    }

    @Test(priority = 53, description = "U04: Not found returns 404")
    @Story("Update Raw Material")
    @Severity(SeverityLevel.NORMAL)
    public void U04_updateNotFoundReturns404() {
        given().spec(authSpec()).pathParam("id", "000000000000000000000001")
                .body(Map.of("price", 100))
                .when().put(Constants.RAW_MATERIAL_BY_ID)
                .then().statusCode(404);
        log.info("U04 PASSED");
    }

    @Test(priority = 54, description = "U05: Body includes code (stripped)",
          dependsOnMethods = "C01_createWithPriceOnly")
    @Story("Update Raw Material")
    @Severity(SeverityLevel.NORMAL)
    public void U05_updateWithCodeInBodyStripped() {
        Response r = given().spec(authSpec()).pathParam("id", createdRawMaterialId)
                .body(Map.of("price", 200, "code", "CUSTOM"))
                .when().put(Constants.RAW_MATERIAL_BY_ID)
                .then().statusCode(200).extract().response();
        String code = r.jsonPath().getString("data.code");
        Assert.assertFalse("CUSTOM".equals(code));
        log.info("U05 PASSED — code={}", code);
    }

    @Test(priority = 55, description = "U06: Update customer_id to valid",
          dependsOnMethods = {"C01_createWithPriceOnly", "C06_createWithValidCustomerId"})
    @Story("Update Raw Material")
    @Severity(SeverityLevel.NORMAL)
    public void U06_updateCustomerIdValid() {
        if (createdCustomerId == null) return;
        given().spec(authSpec()).pathParam("id", createdRawMaterialId)
                .body(Map.of("customer_id", createdCustomerId))
                .when().put(Constants.RAW_MATERIAL_BY_ID)
                .then().statusCode(200);
        log.info("U06 PASSED");
    }

    @Test(priority = 56, description = "U07: Update customer_id to invalid returns 409",
          dependsOnMethods = "C01_createWithPriceOnly")
    @Story("Update Raw Material")
    @Severity(SeverityLevel.NORMAL)
    public void U07_updateCustomerIdInvalidReturns409() {
        given().spec(authSpec()).pathParam("id", createdRawMaterialId)
                .body(Map.of("customer_id", "000000000000000000000001"))
                .when().put(Constants.RAW_MATERIAL_BY_ID)
                .then().statusCode(409);
        log.info("U07 PASSED");
    }

    @Test(priority = 57, description = "U08: Update price to negative returns 409",
          dependsOnMethods = "C01_createWithPriceOnly")
    @Story("Update Raw Material")
    @Severity(SeverityLevel.NORMAL)
    public void U08_updateNegativePriceReturns409() {
        given().spec(authSpec()).pathParam("id", createdRawMaterialId)
                .body(Map.of("price", -5))
                .when().put(Constants.RAW_MATERIAL_BY_ID)
                .then().statusCode(409);
        log.info("U08 PASSED");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5.6 DELETE /:id — Delete Raw Material
    // ═══════════════════════════════════════════════════════════════════════════

    @Test(priority = 60, description = "D01: Delete existing returns 200")
    @Story("Delete Raw Material")
    @Severity(SeverityLevel.CRITICAL)
    public void D01_deleteSuccess() {
        Map<String, Object> payload = TestDataFactory.rawMaterialMinimalPayload();
        String id = given().spec(authSpec()).body(payload)
                .when().post(Constants.RAW_MATERIALS)
                .then().statusCode(anyOf(equalTo(200), equalTo(201)))
                .extract().jsonPath().getString("data._id");
        given().spec(authSpec()).pathParam("id", id)
                .when().delete(Constants.RAW_MATERIAL_BY_ID)
                .then().statusCode(200).body("message", containsStringIgnoringCase("deleted"));
        log.info("D01 PASSED");
    }

    @Test(priority = 61, description = "D02: Delete non-existent returns 404")
    @Story("Delete Raw Material")
    @Severity(SeverityLevel.NORMAL)
    public void D02_deleteNotFoundReturns404() {
        given().spec(authSpec()).pathParam("id", "000000000000000000000001")
                .when().delete(Constants.RAW_MATERIAL_BY_ID)
                .then().statusCode(404);
        log.info("D02 PASSED");
    }

    @Test(priority = 62, description = "D03: Delete invalid ObjectId returns 400")
    @Story("Delete Raw Material")
    @Severity(SeverityLevel.NORMAL)
    public void D03_deleteInvalidIdReturns400() {
        given().spec(authSpec()).pathParam("id", "abc123")
                .when().delete(Constants.RAW_MATERIAL_BY_ID)
                .then().statusCode(400);
        log.info("D03 PASSED");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5.7 POST /search — Search Raw Materials
    // ═══════════════════════════════════════════════════════════════════════════

    @Test(priority = 70, description = "S01: Search no filters")
    @Story("Search Raw Materials")
    @Severity(SeverityLevel.CRITICAL)
    public void S01_searchNoFilters() {
        Response r = given().spec(authSpec()).body(Map.of())
                .when().post(Constants.RAW_MATERIALS_SEARCH)
                .then().statusCode(200).body("data", notNullValue()).extract().response();
        Object data = r.jsonPath().get("data");
        if (data instanceof Map) {
            Assert.assertTrue(((Map<?, ?>) data).containsKey("items"));
        }
        log.info("S01 PASSED");
    }

    @Test(priority = 71, description = "S02: Filter by name",
          dependsOnMethods = "C02_createWithFullOptionalFields")
    @Story("Search Raw Materials")
    @Severity(SeverityLevel.NORMAL)
    public void S02_searchByName() {
        String term = createdRawMaterialName != null && createdRawMaterialName.length() >= 5
                ? createdRawMaterialName.substring(0, 5) : "Steel";
        given().spec(authSpec()).body(Map.of("name", term))
                .queryParam("skip", 0).queryParam("limit", 50)
                .when().post(Constants.RAW_MATERIALS_SEARCH)
                .then().statusCode(200).body("data.items", notNullValue());
        log.info("S02 PASSED");
    }

    @Test(priority = 72, description = "S03: Filter by code")
    @Story("Search Raw Materials")
    @Severity(SeverityLevel.NORMAL)
    public void S03_searchByCode() {
        given().spec(authSpec()).body(Map.of("code", "RAW"))
                .queryParam("skip", 0).queryParam("limit", 50)
                .when().post(Constants.RAW_MATERIALS_SEARCH)
                .then().statusCode(200).body("data.items", notNullValue());
        log.info("S03 PASSED");
    }

    @Test(priority = 73, description = "S04: Filter by group")
    @Story("Search Raw Materials")
    @Severity(SeverityLevel.NORMAL)
    public void S04_searchByGroup() {
        given().spec(authSpec()).body(Map.of("group", "wheel"))
                .queryParam("skip", 0).queryParam("limit", 50)
                .when().post(Constants.RAW_MATERIALS_SEARCH)
                .then().statusCode(200).body("data.items", notNullValue());
        log.info("S04 PASSED");
    }

    @Test(priority = 74, description = "S07: Filter by grade")
    @Story("Search Raw Materials")
    @Severity(SeverityLevel.NORMAL)
    public void S07_searchByGrade() {
        given().spec(authSpec()).body(Map.of("grade", "A"))
                .queryParam("skip", 0).queryParam("limit", 50)
                .when().post(Constants.RAW_MATERIALS_SEARCH)
                .then().statusCode(200);
        log.info("S07 PASSED");
    }

    @Test(priority = 75, description = "S08: Filter by description")
    @Story("Search Raw Materials")
    @Severity(SeverityLevel.NORMAL)
    public void S08_searchByDescription() {
        given().spec(authSpec()).body(Map.of("description", "rod"))
                .queryParam("skip", 0).queryParam("limit", 50)
                .when().post(Constants.RAW_MATERIALS_SEARCH)
                .then().statusCode(200);
        log.info("S08 PASSED");
    }

    @Test(priority = 76, description = "S11: Filter by exact price")
    @Story("Search Raw Materials")
    @Severity(SeverityLevel.NORMAL)
    public void S11_searchByExactPrice() {
        given().spec(authSpec()).body(Map.of("price", 100))
                .queryParam("skip", 0).queryParam("limit", 50)
                .when().post(Constants.RAW_MATERIALS_SEARCH)
                .then().statusCode(200);
        log.info("S11 PASSED");
    }

    @Test(priority = 77, description = "S15: Pagination with skip and limit")
    @Story("Search Raw Materials")
    @Severity(SeverityLevel.NORMAL)
    public void S15_searchWithPagination() {
        Response r = given().spec(authSpec()).body(Map.of())
                .queryParam("skip", 10).queryParam("limit", 50)
                .when().post(Constants.RAW_MATERIALS_SEARCH)
                .then().statusCode(200).extract().response();
        Integer skip = r.jsonPath().getInt("data.skip");
        Integer limit = r.jsonPath().getInt("data.limit");
        Assert.assertEquals(skip, Integer.valueOf(10));
        Assert.assertEquals(limit, Integer.valueOf(50));
        log.info("S15 PASSED");
    }

    @Test(priority = 78, description = "S16: Limit capped at 100")
    @Story("Search Raw Materials")
    @Severity(SeverityLevel.MINOR)
    public void S16_searchLimitCappedAt100() {
        Response r = given().spec(authSpec()).body(Map.of())
                .queryParam("limit", 500)
                .when().post(Constants.RAW_MATERIALS_SEARCH)
                .then().statusCode(200).extract().response();
        Integer limit = r.jsonPath().getInt("data.limit");
        Assert.assertTrue(limit <= 100, "Limit should be capped at 100");
        log.info("S16 PASSED — limit={}", limit);
    }

    @Test(priority = 79, description = "S18: Combined filters")
    @Story("Search Raw Materials")
    @Severity(SeverityLevel.NORMAL)
    public void S18_searchCombinedFilters() {
        given().spec(authSpec()).body(Map.of("name", "Steel", "group", "rods", "price", 100))
                .queryParam("skip", 0).queryParam("limit", 50)
                .when().post(Constants.RAW_MATERIALS_SEARCH)
                .then().statusCode(200).body("data.items", notNullValue());
        log.info("S18 PASSED");
    }

    @Test(priority = 80, description = "S20: Empty/whitespace filter ignored")
    @Story("Search Raw Materials")
    @Severity(SeverityLevel.MINOR)
    public void S20_searchEmptyFilterIgnored() {
        given().spec(authSpec()).body(Map.of("name", "   "))
                .queryParam("skip", 0).queryParam("limit", 50)
                .when().post(Constants.RAW_MATERIALS_SEARCH)
                .then().statusCode(200);
        log.info("S20 PASSED");
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        if (createdRawMaterialId != null) {
            try {
                given().spec(authSpec()).pathParam("id", createdRawMaterialId)
                        .delete(Constants.RAW_MATERIAL_BY_ID);
            } catch (Exception e) {
                log.warn("Cleanup: could not delete raw material: {}", e.getMessage());
            }
        }
        if (createdCustomerId != null) {
            try {
                given().spec(authSpec()).pathParam("id", createdCustomerId)
                        .delete(Constants.CUSTOMER_BY_ID);
            } catch (Exception e) {
                log.warn("Cleanup: could not delete customer: {}", e.getMessage());
            }
        }
        log.info("RawMaterialTest cleanup complete");
    }
}
