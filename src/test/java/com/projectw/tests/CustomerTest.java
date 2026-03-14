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
 * Customer Module API Test Suite
 *
 * Based on: Project_W_BE/docs/CUSTOMER_MODULE_API_TEST_CASES.md
 * Base path: /accounts/api/customer
 *
 * Covers:
 *   AUTH-01 to AUTH-05  — Authentication tests
 *   C01–C12             — Create (POST /)
 *   L01–L05             — List (GET /)
 *   G01–G04             — Get by ID (GET /:id)
 *   U01–U07             — Update (PUT /:id)
 *   D01–D03             — Delete (DELETE /:id)
 *   S01–S11             — Search (POST /search)
 */
@Epic("Project W API")
@Feature("Customer Management")
public class CustomerTest extends BaseTest {

    private String createdCustomerId;
    private String createdCustomerName;
    private String createdCustomerCode;
    private String createdSupplierId;

    @BeforeClass(alwaysRun = true)
    public void setup() {
        log.info("CustomerTest @BeforeClass — suite ready");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5.1 Authentication Tests (AUTH-01 to AUTH-05)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test(priority = 1, description = "AUTH-01: No Authorization header returns 401")
    @Story("Authentication")
    @Severity(SeverityLevel.BLOCKER)
    public void AUTH_01_noAuthorizationReturns401() {
        given()
                .spec(unauthSpec())
                .when()
                .get(Constants.CUSTOMERS)
                .then()
                .statusCode(401)
                .body("success", equalTo(false))
                .body("message", containsStringIgnoringCase("token"));
        log.info("AUTH-01 PASSED");
    }

    @Test(priority = 2, description = "AUTH-02: Malformed token returns 401")
    @Story("Authentication")
    @Severity(SeverityLevel.BLOCKER)
    public void AUTH_02_malformedTokenReturns401() {
        given()
                .spec(unauthSpec())
                .header(Constants.HEADER_AUTHORIZATION, "Token xyz")
                .when()
                .get(Constants.CUSTOMERS)
                .then()
                .statusCode(401)
                .body("success", equalTo(false));
        log.info("AUTH-02 PASSED");
    }

    @Test(priority = 3, description = "AUTH-03: Invalid JWT returns 401")
    @Story("Authentication")
    @Severity(SeverityLevel.BLOCKER)
    public void AUTH_03_invalidJwtReturns401() {
        given()
                .spec(unauthSpec())
                .header(Constants.HEADER_AUTHORIZATION, Constants.BEARER_PREFIX + "invalid.jwt.token")
                .when()
                .get(Constants.CUSTOMERS)
                .then()
                .statusCode(401)
                .body("success", equalTo(false));
        log.info("AUTH-03 PASSED");
    }

    @Test(priority = 4, description = "AUTH-05: Valid JWT returns 200")
    @Story("Authentication")
    @Severity(SeverityLevel.BLOCKER)
    public void AUTH_05_validJwtReturns200() {
        given()
                .spec(authSpec())
                .when()
                .get(Constants.CUSTOMERS)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("message", containsStringIgnoringCase("retrieved"))
                .body("data", notNullValue());
        log.info("AUTH-05 PASSED");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5.2 POST / — Create Customer (C01–C12)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test(priority = 10, description = "C01: Create customer with required fields only")
    @Story("Create Customer")
    @Severity(SeverityLevel.BLOCKER)
    public void C01_createCustomerRequiredFields() {
        Map<String, Object> payload = TestDataFactory.customerMinimalPayload();
        createdCustomerName = (String) payload.get("name");

        Response response = given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.CUSTOMERS)
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .body("data._id", notNullValue())
                .body("data.name", equalTo(createdCustomerName))
                .body("data.customer_type", equalTo("customer"))
                .body("data.code", notNullValue())
                .extract().response();

        createdCustomerId = extractId(response);
        createdCustomerCode = response.jsonPath().getString("data.code");
        Assert.assertNotNull(createdCustomerId);
        log.info("C01 PASSED — id={} code={}", createdCustomerId, createdCustomerCode);
    }

    @Test(priority = 11, description = "C02: Create supplier with required fields")
    @Story("Create Customer")
    @Severity(SeverityLevel.CRITICAL)
    public void C02_createSupplierRequiredFields() {
        Map<String, Object> payload = TestDataFactory.supplierMinimalPayload();

        Response response = given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.CUSTOMERS)
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .body("data.customer_type", equalTo("supplier"))
                .body("data.code", notNullValue())
                .extract().response();

        createdSupplierId = extractId(response);
        String code = response.jsonPath().getString("data.code");
        Assert.assertTrue(code != null && code.startsWith("SUPP"),
                "Supplier code should start with SUPP");
        log.info("C02 PASSED — id={} code={}", createdSupplierId, code);
    }

    @Test(priority = 12, description = "Create supplier with full optional fields")
    @Story("Create Customer")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /customer with customer_type='supplier' and all optional fields " +
                 "(vendor_code, shipping_address, tax_type, contact_details, bank_details) " +
                 "should return 200/201 and save all fields correctly.")
    public void C02b_createSupplierWithFullOptionalFields() {
        Map<String, Object> payload = TestDataFactory.supplierFullPayload();

        given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.CUSTOMERS)
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .body("data.customer_type", equalTo("supplier"))
                .body("data.vendor_code", notNullValue())
                .body("data.shipping_address", notNullValue())
                .body("data.tax_type", equalTo("IGST"))
                .body("data.contact_details", notNullValue())
                .body("data.bank_details", notNullValue());

        log.info("C02b PASSED — supplier with full optional fields created");
    }

    @Test(priority = 13, description = "C03: Create with full optional fields")
    @Story("Create Customer")
    @Severity(SeverityLevel.CRITICAL)
    public void C03_createWithFullOptionalFields() {
        Map<String, Object> payload = TestDataFactory.customerFullPayload();

        Response response = given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.CUSTOMERS)
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .body("data.vendor_code", notNullValue())
                .body("data.shipping_address", notNullValue())
                .body("data.tax_type", equalTo("GST"))
                .body("data.contact_details", notNullValue())
                .body("data.bank_details", notNullValue())
                .extract().response();

        log.info("C03 PASSED — full payload saved");
    }

    @Test(priority = 14, description = "C04: Create with contact_details and primary flag")
    @Story("Create Customer")
    @Severity(SeverityLevel.NORMAL)
    public void C04_createWithPrimaryContact() {
        Map<String, Object> payload = TestDataFactory.customerWithPrimaryContactPayload();

        given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.CUSTOMERS)
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .body("data.contact_details", notNullValue());
        log.info("C04 PASSED");
    }

    @Test(priority = 15, description = "C05: Create with multiple primary contacts (pre-save keeps first)")
    @Story("Create Customer")
    @Severity(SeverityLevel.NORMAL)
    public void C05_createWithMultiplePrimaryContacts() {
        Map<String, Object> payload = TestDataFactory.customerWithMultiplePrimaryPayload();

        given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.CUSTOMERS)
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(201)));
        log.info("C05 PASSED");
    }

    @Test(priority = 16, description = "C06: Create with invalid customer_type returns 409")
    @Story("Create Customer")
    @Severity(SeverityLevel.CRITICAL)
    public void C06_createInvalidCustomerTypeReturns409() {
        Map<String, Object> payload = Map.of(
                "customer_type", "invalid",
                "name", "X",
                "billing_address", "Y",
                "gst_number", "27AABCU9603R1ZM"
        );

        given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.CUSTOMERS)
                .then()
                .statusCode(409);
        log.info("C06 PASSED");
    }

    @Test(priority = 17, description = "C07: Create with missing customer_type returns 409")
    @Story("Create Customer")
    @Severity(SeverityLevel.CRITICAL)
    public void C07_createMissingCustomerTypeReturns409() {
        Map<String, Object> payload = Map.of(
                "name", "X",
                "billing_address", "Y",
                "gst_number", "27AABCU9603R1ZM"
        );

        given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.CUSTOMERS)
                .then()
                .statusCode(409);
        log.info("C07 PASSED");
    }

    @Test(priority = 18, description = "C08: Create with missing name returns 409")
    @Story("Create Customer")
    @Severity(SeverityLevel.CRITICAL)
    public void C08_createMissingNameReturns409() {
        Map<String, Object> payload = Map.of(
                "customer_type", "customer",
                "billing_address", "Y",
                "gst_number", "27AABCU9603R1ZM"
        );

        given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.CUSTOMERS)
                .then()
                .statusCode(409);
        log.info("C08 PASSED");
    }

    @Test(priority = 19, description = "C09: Create with missing billing_address returns 409")
    @Story("Create Customer")
    @Severity(SeverityLevel.CRITICAL)
    public void C09_createMissingBillingAddressReturns409() {
        Map<String, Object> payload = Map.of(
                "customer_type", "customer",
                "name", "X",
                "gst_number", "27AABCU9603R1ZM"
        );

        given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.CUSTOMERS)
                .then()
                .statusCode(409);
        log.info("C09 PASSED");
    }

    @Test(priority = 20, description = "C10: Create with missing gst_number returns 409")
    @Story("Create Customer")
    @Severity(SeverityLevel.CRITICAL)
    public void C10_createMissingGstNumberReturns409() {
        Map<String, Object> payload = Map.of(
                "customer_type", "customer",
                "name", "X",
                "billing_address", "Y"
        );

        given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.CUSTOMERS)
                .then()
                .statusCode(409);
        log.info("C10 PASSED");
    }

    @Test(priority = 21, description = "C11: Create with invalid tax_type returns 409")
    @Story("Create Customer")
    @Severity(SeverityLevel.NORMAL)
    public void C11_createInvalidTaxTypeReturns409() {
        Map<String, Object> payload = TestDataFactory.customerMinimalPayload();
        payload.put("tax_type", "INVALID");

        given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.CUSTOMERS)
                .then()
                .statusCode(409);
        log.info("C11 PASSED");
    }

    @Test(priority = 22, description = "C12: Create with code in body (ignored, auto-generated)")
    @Story("Create Customer")
    @Severity(SeverityLevel.NORMAL)
    public void C12_createWithCodeInBodyIgnored() {
        Map<String, Object> payload = TestDataFactory.customerMinimalPayload();
        payload.put("code", "CUSTOM-999");

        Response response = given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.CUSTOMERS)
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .extract().response();

        String returnedCode = response.jsonPath().getString("data.code");
        Assert.assertNotEquals(returnedCode, "CUSTOM-999",
                "code in body should be ignored; API must auto-generate");
        log.info("C12 PASSED — returned code={}", returnedCode);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5.3 GET / — List Customers (L01–L05)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test(priority = 30, description = "L01: List all customers (no filter)",
          dependsOnMethods = "C01_createCustomerRequiredFields")
    @Story("List Customers")
    @Severity(SeverityLevel.CRITICAL)
    public void L01_listAllCustomers() {
        given()
                .spec(authSpec())
                .when()
                .get(Constants.CUSTOMERS)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("message", containsStringIgnoringCase("retrieved"))
                .body("data", notNullValue());
        log.info("L01 PASSED");
    }

    @Test(priority = 31, description = "L02: Filter by customer_type=customer")
    @Story("List Customers")
    @Severity(SeverityLevel.NORMAL)
    public void L02_listFilterByCustomerType() {
        Response response = given()
                .spec(authSpec())
                .queryParam("customer_type", "customer")
                .when()
                .get(Constants.CUSTOMERS)
                .then()
                .statusCode(200)
                .extract().response();

        Object data = response.jsonPath().get("data");
        if (data instanceof List) {
            for (Object item : (List<?>) data) {
                Map<?, ?> m = (Map<?, ?>) item;
                Assert.assertEquals(m.get("customer_type"), "customer");
            }
        }
        log.info("L02 PASSED");
    }

    @Test(priority = 32, description = "L03: Filter by customer_type=supplier")
    @Story("List Customers")
    @Severity(SeverityLevel.NORMAL)
    public void L03_listFilterBySupplierType() {
        Response response = given()
                .spec(authSpec())
                .queryParam("customer_type", "supplier")
                .when()
                .get(Constants.CUSTOMERS)
                .then()
                .statusCode(200)
                .extract().response();

        Object data = response.jsonPath().get("data");
        if (data instanceof List) {
            for (Object item : (List<?>) data) {
                Map<?, ?> m = (Map<?, ?>) item;
                Assert.assertEquals(m.get("customer_type"), "supplier");
            }
        }
        log.info("L03 PASSED");
    }

    @Test(priority = 33, description = "L04: Invalid customer_type ignored, all returned")
    @Story("List Customers")
    @Severity(SeverityLevel.NORMAL)
    public void L04_listInvalidCustomerTypeIgnored() {
        given()
                .spec(authSpec())
                .queryParam("customer_type", "invalid")
                .when()
                .get(Constants.CUSTOMERS)
                .then()
                .statusCode(200)
                .body("data", notNullValue());
        log.info("L04 PASSED");
    }

    @Test(priority = 34, description = "L05: With flags in body")
    @Story("List Customers")
    @Severity(SeverityLevel.MINOR)
    public void L05_listWithFlagsInBody() {
        // GET typically ignores body; some implementations accept flags
        given()
                .spec(authSpec())
                .body(Map.of("flags", Map.of()))
                .when()
                .get(Constants.CUSTOMERS)
                .then()
                .statusCode(200);
        log.info("L05 PASSED");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5.4 GET /:id — Get Customer by ID (G01–G04)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test(priority = 40, description = "G01: Valid ObjectId, customer exists",
          dependsOnMethods = "C01_createCustomerRequiredFields")
    @Story("Get Customer")
    @Severity(SeverityLevel.CRITICAL)
    public void G01_getCustomerByIdSuccess() {
        given()
                .spec(authSpec())
                .pathParam("id", createdCustomerId)
                .when()
                .get(Constants.CUSTOMER_BY_ID)
                .then()
                .statusCode(200)
                .body("data._id", equalTo(createdCustomerId))
                .body("data.name", equalTo(createdCustomerName));
        log.info("G01 PASSED");
    }

    @Test(priority = 41, description = "G02: Valid ObjectId, customer does not exist")
    @Story("Get Customer")
    @Severity(SeverityLevel.CRITICAL)
    public void G02_getCustomerNotFoundReturns404() {
        given()
                .spec(authSpec())
                .pathParam("id", "000000000000000000000001")
                .when()
                .get(Constants.CUSTOMER_BY_ID)
                .then()
                .statusCode(404)
                .body("message", containsStringIgnoringCase("not found"));
        log.info("G02 PASSED");
    }

    @Test(priority = 42, description = "G03: Invalid ObjectId returns 400")
    @Story("Get Customer")
    @Severity(SeverityLevel.CRITICAL)
    public void G03_getCustomerInvalidIdReturns400() {
        given()
                .spec(authSpec())
                .pathParam("id", "abc123")
                .when()
                .get(Constants.CUSTOMER_BY_ID)
                .then()
                .statusCode(400)
                .body("message", containsStringIgnoringCase("invalid"));
        log.info("G03 PASSED");
    }

    @Test(priority = 43, description = "G04: Empty ID returns 400")
    @Story("Get Customer")
    @Severity(SeverityLevel.NORMAL)
    public void G04_getCustomerEmptyIdReturns400() {
        // Path /customer/ may route differently; try invalid empty-like id
        given()
                .spec(authSpec())
                .pathParam("id", " ")
                .when()
                .get(Constants.CUSTOMER_BY_ID)
                .then()
                .statusCode(anyOf(equalTo(400), equalTo(404)));
        log.info("G04 PASSED");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5.5 PUT /:id — Update Customer (U01–U07)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test(priority = 50, description = "U01: Update with full valid document",
          dependsOnMethods = "C01_createCustomerRequiredFields")
    @Story("Update Customer")
    @Severity(SeverityLevel.CRITICAL)
    public void U01_updateCustomerFullDocument() {
        Map<String, Object> fullDoc = TestDataFactory.customerMinimalPayload();
        fullDoc.put("name", "Updated Full Doc " + System.currentTimeMillis());
        fullDoc.put("billing_address", "Updated Address, Mumbai - 400001");

        given()
                .spec(authSpec())
                .pathParam("id", createdCustomerId)
                .body(fullDoc)
                .when()
                .put(Constants.CUSTOMER_BY_ID)
                .then()
                .statusCode(200)
                .body("data.name", containsString("Updated Full Doc"))
                .body("data.billing_address", containsString("Updated Address"));
        log.info("U01 PASSED");
    }

    @Test(priority = 51, description = "U02: Invalid ObjectId returns 400")
    @Story("Update Customer")
    @Severity(SeverityLevel.NORMAL)
    public void U02_updateInvalidIdReturns400() {
        Map<String, Object> body = TestDataFactory.customerMinimalPayload();

        given()
                .spec(authSpec())
                .pathParam("id", "abc123")
                .body(body)
                .when()
                .put(Constants.CUSTOMER_BY_ID)
                .then()
                .statusCode(400);
        log.info("U02 PASSED");
    }

    @Test(priority = 52, description = "U03: Valid ObjectId, customer not found returns 404")
    @Story("Update Customer")
    @Severity(SeverityLevel.NORMAL)
    public void U03_updateNotFoundReturns404() {
        Map<String, Object> body = TestDataFactory.customerMinimalPayload();

        given()
                .spec(authSpec())
                .pathParam("id", "000000000000000000000001")
                .body(body)
                .when()
                .put(Constants.CUSTOMER_BY_ID)
                .then()
                .statusCode(404);
        log.info("U03 PASSED");
    }

    @Test(priority = 53, description = "U05: Body includes code (ignored)",
          dependsOnMethods = "C01_createCustomerRequiredFields")
    @Story("Update Customer")
    @Severity(SeverityLevel.NORMAL)
    public void U05_updateWithCodeInBodyIgnored() {
        Map<String, Object> body = TestDataFactory.customerMinimalPayload();
        body.put("name", "Code Ignored Test");
        body.put("code", "CUSTOM-999");

        Response response = given()
                .spec(authSpec())
                .pathParam("id", createdCustomerId)
                .body(body)
                .when()
                .put(Constants.CUSTOMER_BY_ID)
                .then()
                .statusCode(200)
                .extract().response();

        String returnedCode = response.jsonPath().getString("data.code");
        Assert.assertNotEquals(returnedCode, "CUSTOM-999");
        log.info("U05 PASSED — code remained {}", returnedCode);
    }

    @Test(priority = 54, description = "U06: Partial update (omitted fields cleared)",
          dependsOnMethods = "C01_createCustomerRequiredFields")
    @Story("Update Customer")
    @Severity(SeverityLevel.NORMAL)
    public void U06_updatePartialFields() {
        // Full replacement: send required fields + only name/billing_address changed
        Map<String, Object> body = Map.of(
                "customer_type", "customer",
                "name", "Partial Update " + System.currentTimeMillis(),
                "billing_address", "New Billing Addr",
                "gst_number", "27AABCU9603R1ZM"
        );

        given()
                .spec(authSpec())
                .pathParam("id", createdCustomerId)
                .body(body)
                .when()
                .put(Constants.CUSTOMER_BY_ID)
                .then()
                .statusCode(200)
                .body("data.name", containsString("Partial Update"))
                .body("data.billing_address", equalTo("New Billing Addr"));
        log.info("U06 PASSED");
    }

    @Test(priority = 55, description = "U07: Invalid customer_type in body returns 409",
          dependsOnMethods = "C01_createCustomerRequiredFields")
    @Story("Update Customer")
    @Severity(SeverityLevel.NORMAL)
    public void U07_updateInvalidCustomerTypeReturns409() {
        Map<String, Object> body = TestDataFactory.customerMinimalPayload();
        body.put("customer_type", "invalid");

        given()
                .spec(authSpec())
                .pathParam("id", createdCustomerId)
                .body(body)
                .when()
                .put(Constants.CUSTOMER_BY_ID)
                .then()
                .statusCode(409);
        log.info("U07 PASSED");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5.6 DELETE /:id — Delete Customer (D01–D03)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test(priority = 60, description = "D01: Delete existing customer returns 200",
          dependsOnMethods = "C01_createCustomerRequiredFields")
    @Story("Delete Customer")
    @Severity(SeverityLevel.CRITICAL)
    public void D01_deleteCustomerSuccess() {
        // Create a disposable customer to delete
        Map<String, Object> payload = TestDataFactory.customerMinimalPayload();
        String id = given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.CUSTOMERS)
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .extract().jsonPath().getString("data._id");

        given()
                .spec(authSpec())
                .pathParam("id", id)
                .when()
                .delete(Constants.CUSTOMER_BY_ID)
                .then()
                .statusCode(200)
                .body("message", containsStringIgnoringCase("deleted"));
        log.info("D01 PASSED");
    }

    @Test(priority = 61, description = "D02: Delete non-existent customer returns 404")
    @Story("Delete Customer")
    @Severity(SeverityLevel.NORMAL)
    public void D02_deleteNotFoundReturns404() {
        given()
                .spec(authSpec())
                .pathParam("id", "000000000000000000000001")
                .when()
                .delete(Constants.CUSTOMER_BY_ID)
                .then()
                .statusCode(404);
        log.info("D02 PASSED");
    }

    @Test(priority = 62, description = "D03: Delete with invalid ObjectId returns 400")
    @Story("Delete Customer")
    @Severity(SeverityLevel.NORMAL)
    public void D03_deleteInvalidIdReturns400() {
        given()
                .spec(authSpec())
                .pathParam("id", "abc123")
                .when()
                .delete(Constants.CUSTOMER_BY_ID)
                .then()
                .statusCode(400);
        log.info("D03 PASSED");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5.7 POST /search — Search Customers (S01–S11)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test(priority = 70, description = "S01: Search with no filters, no pagination")
    @Story("Search Customers")
    @Severity(SeverityLevel.CRITICAL)
    public void S01_searchNoFilters() {
        Response response = given()
                .spec(authSpec())
                .body(Map.of())
                .when()
                .post(Constants.CUSTOMERS_SEARCH)
                .then()
                .statusCode(200)
                .body("data", notNullValue())
                .extract().response();

        Object data = response.jsonPath().get("data");
        if (data instanceof Map) {
            Assert.assertTrue(((Map<?, ?>) data).containsKey("items"));
        }
        log.info("S01 PASSED");
    }

    @Test(priority = 71, description = "S02: Filter by name (case-insensitive)",
          dependsOnMethods = "C01_createCustomerRequiredFields")
    @Story("Search Customers")
    @Severity(SeverityLevel.NORMAL)
    public void S02_searchByName() {
        // After U06 the name is "Partial Update ..."; use that substring
        String term = "Partial";

        Response response = given()
                .spec(authSpec())
                .body(Map.of("name", term))
                .queryParam("skip", 0)
                .queryParam("limit", 50)
                .when()
                .post(Constants.CUSTOMERS_SEARCH)
                .then()
                .statusCode(200)
                .extract().response();

        List<?> items = response.jsonPath().getList("data.items");
        Assert.assertNotNull(items);
        log.info("S02 PASSED — found {} items for name='{}'", items.size(), term);
    }

    @Test(priority = 72, description = "S03: Filter by code")
    @Story("Search Customers")
    @Severity(SeverityLevel.NORMAL)
    public void S03_searchByCode() {
        given()
                .spec(authSpec())
                .body(Map.of("code", "CUST"))
                .queryParam("skip", 0)
                .queryParam("limit", 50)
                .when()
                .post(Constants.CUSTOMERS_SEARCH)
                .then()
                .statusCode(200)
                .body("data.items", notNullValue());
        log.info("S03 PASSED");
    }

    @Test(priority = 73, description = "S04: Filter by gst_number")
    @Story("Search Customers")
    @Severity(SeverityLevel.NORMAL)
    public void S04_searchByGstNumber() {
        given()
                .spec(authSpec())
                .body(Map.of("gst_number", "27AABC"))
                .queryParam("skip", 0)
                .queryParam("limit", 50)
                .when()
                .post(Constants.CUSTOMERS_SEARCH)
                .then()
                .statusCode(200)
                .body("data.items", notNullValue());
        log.info("S04 PASSED");
    }

    @Test(priority = 74, description = "S05: Filter by customer_type=customer")
    @Story("Search Customers")
    @Severity(SeverityLevel.NORMAL)
    public void S05_searchByCustomerType() {
        Response response = given()
                .spec(authSpec())
                .body(Map.of("customer_type", "customer"))
                .queryParam("skip", 0)
                .queryParam("limit", 50)
                .when()
                .post(Constants.CUSTOMERS_SEARCH)
                .then()
                .statusCode(200)
                .extract().response();

        List<String> types = response.jsonPath().getList("data.items.customer_type");
        if (types != null) {
            for (String t : types) Assert.assertEquals(t, "customer");
        }
        log.info("S05 PASSED");
    }

    @Test(priority = 75, description = "S06: Filter by customer_type=supplier")
    @Story("Search Customers")
    @Severity(SeverityLevel.NORMAL)
    public void S06_searchBySupplierType() {
        Response response = given()
                .spec(authSpec())
                .body(Map.of("customer_type", "supplier"))
                .queryParam("skip", 0)
                .queryParam("limit", 50)
                .when()
                .post(Constants.CUSTOMERS_SEARCH)
                .then()
                .statusCode(200)
                .extract().response();

        List<String> types = response.jsonPath().getList("data.items.customer_type");
        if (types != null) {
            for (String t : types) Assert.assertEquals(t, "supplier");
        }
        log.info("S06 PASSED");
    }

    @Test(priority = 76, description = "S07: Invalid customer_type in body ignored")
    @Story("Search Customers")
    @Severity(SeverityLevel.MINOR)
    public void S07_searchInvalidCustomerTypeIgnored() {
        given()
                .spec(authSpec())
                .body(Map.of("customer_type", "invalid"))
                .queryParam("skip", 0)
                .queryParam("limit", 50)
                .when()
                .post(Constants.CUSTOMERS_SEARCH)
                .then()
                .statusCode(200);
        log.info("S07 PASSED");
    }

    @Test(priority = 77, description = "S08: Pagination with skip and limit")
    @Story("Search Customers")
    @Severity(SeverityLevel.NORMAL)
    public void S08_searchWithPagination() {
        Response response = given()
                .spec(authSpec())
                .body(Map.of())
                .queryParam("skip", 10)
                .queryParam("limit", 50)
                .when()
                .post(Constants.CUSTOMERS_SEARCH)
                .then()
                .statusCode(200)
                .extract().response();

        Integer skip = response.jsonPath().getInt("data.skip");
        Integer limit = response.jsonPath().getInt("data.limit");
        Assert.assertEquals(skip, Integer.valueOf(10));
        Assert.assertEquals(limit, Integer.valueOf(50));
        log.info("S08 PASSED — skip={} limit={}", skip, limit);
    }

    @Test(priority = 78, description = "S09: Limit capped at max")
    @Story("Search Customers")
    @Severity(SeverityLevel.MINOR)
    public void S09_searchLimitCappedAtMax() {
        Response response = given()
                .spec(authSpec())
                .body(Map.of())
                .queryParam("skip", 0)
                .queryParam("limit", 5000)
                .when()
                .post(Constants.CUSTOMERS_SEARCH)
                .then()
                .statusCode(200)
                .extract().response();

        Integer limit = response.jsonPath().getInt("data.limit");
        Assert.assertTrue(limit <= 2000, "limit should be capped at MAX_PAGE_LIMIT (2000)");
        log.info("S09 PASSED — limit={}", limit);
    }

    @Test(priority = 79, description = "S10: Empty/whitespace filter values ignored")
    @Story("Search Customers")
    @Severity(SeverityLevel.MINOR)
    public void S10_searchEmptyFilterIgnored() {
        given()
                .spec(authSpec())
                .body(Map.of("name", "   "))
                .queryParam("skip", 0)
                .queryParam("limit", 50)
                .when()
                .post(Constants.CUSTOMERS_SEARCH)
                .then()
                .statusCode(200);
        log.info("S10 PASSED");
    }

    @Test(priority = 80, description = "S11: Combined filters")
    @Story("Search Customers")
    @Severity(SeverityLevel.NORMAL)
    public void S11_searchCombinedFilters() {
        given()
                .spec(authSpec())
                .body(Map.of(
                        "name", "ABC",
                        "customer_type", "customer"
                ))
                .queryParam("skip", 0)
                .queryParam("limit", 50)
                .when()
                .post(Constants.CUSTOMERS_SEARCH)
                .then()
                .statusCode(200)
                .body("data.items", notNullValue());
        log.info("S11 PASSED");
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        if (createdCustomerId != null) {
            try {
                given().spec(authSpec()).pathParam("id", createdCustomerId).delete(Constants.CUSTOMER_BY_ID);
            } catch (Exception e) {
                log.warn("Cleanup: could not delete created customer: {}", e.getMessage());
            }
        }
        if (createdSupplierId != null) {
            try {
                given().spec(authSpec()).pathParam("id", createdSupplierId).delete(Constants.CUSTOMER_BY_ID);
            } catch (Exception e) {
                log.warn("Cleanup: could not delete created supplier: {}", e.getMessage());
            }
        }
        log.info("CustomerTest cleanup complete");
    }
}
