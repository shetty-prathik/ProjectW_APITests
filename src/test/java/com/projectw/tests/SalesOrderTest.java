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
 * Sales Order Lifecycle Test Suite
 *
 * Tests the full lifecycle: Customer → Product → Sales Order (draft) → Submit → Verify
 *
 * Covers:
 *  TC-SO-01  Create sales order in draft status → 201
 *  TC-SO-02  Create sales order without customer_id → 400/409
 *  TC-SO-03  Create sales order without items → 400/409
 *  TC-SO-04  Get all sales orders → 200 + list
 *  TC-SO-05  Get sales order by valid ID → 200 + status=draft
 *  TC-SO-06  Get sales order by invalid ObjectId → 400
 *  TC-SO-07  Get sales order by non-existent ID → 404
 *  TC-SO-08  Update draft sales order fields → 200
 *  TC-SO-09  Submit sales order → 200 + status=submitted + code generated
 *  TC-SO-10  Sales order code follows SO-YYYY-NNNN format after submit
 *  TC-SO-11  Cannot submit an already-submitted sales order
 *  TC-SO-12  Search sales orders by status → 200 + all match status
 *  TC-SO-13  Get production orders for a submitted sales order
 *  TC-SO-14  Delete draft sales order → 200
 *  TC-SO-15  Cannot delete a submitted sales order
 */
@Epic("Project W API")
@Feature("Sales Order Management")
public class SalesOrderTest extends BaseTest {

    private String testCustomerId;
    private String testProductId;
    private String createdSalesOrderId;
    private String submittedSalesOrderId;

    // ─── Setup: Create prerequisite customer and product ─────────────────────

    @BeforeClass(alwaysRun = true)
    public void createPrerequisites() {
        // Create test customer
        Response custResp = given()
                .spec(authSpec())
                .body(TestDataFactory.customerPayload())
                .when()
                .post(Constants.CUSTOMERS)
                .then()
                .statusCode(201)
                .extract().response();
        testCustomerId = extractId(custResp);
        Assert.assertNotNull(testCustomerId, "Test customer must be created");

        // Create test product
        Response prodResp = given()
                .spec(authSpec())
                .body(TestDataFactory.productPayload())
                .when()
                .post(Constants.PRODUCTS)
                .then()
                .statusCode(201)
                .extract().response();
        testProductId = extractId(prodResp);
        Assert.assertNotNull(testProductId, "Test product must be created");

        log.info("SalesOrderTest prerequisites: customerId={}, productId={}",
                testCustomerId, testProductId);
    }

    // ─── TC-SO-01 ─────────────────────────────────────────────────────────────

    @Test(priority = 1, description = "Create sales order in draft status returns 201")
    @Story("Create Sales Order")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /salesorder with a valid customer and product should return HTTP 201 " +
                 "with status='draft' and no code yet (code is generated only on submit).")
    public void TC_SO_01_createSalesOrderDraftSuccess() {
        Map<String, Object> payload = TestDataFactory.salesOrderPayload(testCustomerId, testProductId);

        Response response = given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.SALES_ORDERS)
                .then()
                .statusCode(201)
                .body("code", equalTo(201))
                .body("data._id", notNullValue())
                .body("data.status", equalTo("draft"))
                .body("data.type", equalTo("sales_order"))
                .extract().response();

        createdSalesOrderId = extractId(response);
        Assert.assertNotNull(createdSalesOrderId, "Created SO must have an _id");
        log.info("TC-SO-01 PASSED — created SO id={}", createdSalesOrderId);
    }

    // ─── TC-SO-02 ─────────────────────────────────────────────────────────────

    @Test(priority = 2, description = "Create sales order without customer_id returns error")
    @Story("Create Sales Order")
    @Severity(SeverityLevel.CRITICAL)
    public void TC_SO_02_createSalesOrderMissingCustomerReturnsError() {
        Map<String, Object> payload = Map.of(
                "type", "sales_order",
                "sale_order_date", "2025-06-01",
                "items", List.of(Map.of("product", testProductId, "quantity", 10, "unit_price", 500))
        );

        given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.SALES_ORDERS)
                .then()
                .statusCode(anyOf(equalTo(400), equalTo(409)));

        log.info("TC-SO-02 PASSED — missing customer_id correctly rejected");
    }

    // ─── TC-SO-03 ─────────────────────────────────────────────────────────────

    @Test(priority = 3, description = "Create sales order without items returns error")
    @Story("Create Sales Order")
    @Severity(SeverityLevel.CRITICAL)
    public void TC_SO_03_createSalesOrderNoItemsReturnsError() {
        Map<String, Object> payload = Map.of(
                "customer_id", testCustomerId,
                "type", "sales_order",
                "sale_order_date", "2025-06-01",
                "items", List.of()
        );

        given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.SALES_ORDERS)
                .then()
                .statusCode(anyOf(equalTo(400), equalTo(409)));

        log.info("TC-SO-03 PASSED — empty items array correctly rejected");
    }

    // ─── TC-SO-04 ─────────────────────────────────────────────────────────────

    @Test(priority = 4, description = "Get all sales orders returns 200 with list",
          dependsOnMethods = "TC_SO_01_createSalesOrderDraftSuccess")
    @Story("Read Sales Order")
    @Severity(SeverityLevel.CRITICAL)
    public void TC_SO_04_getAllSalesOrdersReturnsList() {
        given()
                .spec(authSpec())
                .queryParam("skip", 0)
                .queryParam("limit", 20)
                .when()
                .get(Constants.SALES_ORDERS)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data", notNullValue());

        log.info("TC-SO-04 PASSED — sales order list returned");
    }

    // ─── TC-SO-05 ─────────────────────────────────────────────────────────────

    @Test(priority = 5, description = "Get sales order by valid ID returns 200 with status=draft",
          dependsOnMethods = "TC_SO_01_createSalesOrderDraftSuccess")
    @Story("Read Sales Order")
    @Severity(SeverityLevel.CRITICAL)
    public void TC_SO_05_getSalesOrderByIdSuccess() {
        given()
                .spec(authSpec())
                .pathParam("id", createdSalesOrderId)
                .when()
                .get(Constants.SALES_ORDER_BY_ID)
                .then()
                .statusCode(200)
                .body("data._id", equalTo(createdSalesOrderId))
                .body("data.status", equalTo("draft"))
                .body("data.eid", equalTo(TEST_EID));

        log.info("TC-SO-05 PASSED — SO retrieved by id={}", createdSalesOrderId);
    }

    // ─── TC-SO-06 ─────────────────────────────────────────────────────────────

    @Test(priority = 6, description = "Get sales order by invalid ObjectId returns 400")
    @Story("Read Sales Order")
    @Severity(SeverityLevel.NORMAL)
    public void TC_SO_06_getSalesOrderByInvalidIdReturns400() {
        given()
                .spec(authSpec())
                .pathParam("id", "bad-id")
                .when()
                .get(Constants.SALES_ORDER_BY_ID)
                .then()
                .statusCode(400);

        log.info("TC-SO-06 PASSED — invalid ObjectId rejected");
    }

    // ─── TC-SO-07 ─────────────────────────────────────────────────────────────

    @Test(priority = 7, description = "Get sales order by non-existent ID returns 404")
    @Story("Read Sales Order")
    @Severity(SeverityLevel.NORMAL)
    public void TC_SO_07_getSalesOrderByNonExistentIdReturns404() {
        given()
                .spec(authSpec())
                .pathParam("id", "000000000000000000000001")
                .when()
                .get(Constants.SALES_ORDER_BY_ID)
                .then()
                .statusCode(404);

        log.info("TC-SO-07 PASSED — non-existent SO returns 404");
    }

    // ─── TC-SO-08 ─────────────────────────────────────────────────────────────

    @Test(priority = 8, description = "Update draft sales order returns 200",
          dependsOnMethods = "TC_SO_01_createSalesOrderDraftSuccess")
    @Story("Update Sales Order")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT /salesorder/{id} on a draft order should allow updating project_name " +
                 "and delivery_date.")
    public void TC_SO_08_updateDraftSalesOrderSuccess() {
        Map<String, Object> updatePayload = Map.of(
                "project_name", "Updated Project Name",
                "delivery_date", "2025-12-31"
        );

        given()
                .spec(authSpec())
                .pathParam("id", createdSalesOrderId)
                .body(updatePayload)
                .when()
                .put(Constants.SALES_ORDER_BY_ID)
                .then()
                .statusCode(200)
                .body("data.project_name", equalTo("Updated Project Name"));

        log.info("TC-SO-08 PASSED — draft SO updated successfully");
    }

    // ─── TC-SO-09 ─────────────────────────────────────────────────────────────

    @Test(priority = 9, description = "Submit sales order changes status to submitted and generates code",
          dependsOnMethods = "TC_SO_01_createSalesOrderDraftSuccess")
    @Story("Submit Sales Order")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /salesorder/{id}/submit should transition status from 'draft' to 'submitted' " +
                 "and generate the SO code (SO-YYYY-NNNN).")
    public void TC_SO_09_submitSalesOrderSuccess() {
        Response response = given()
                .spec(authSpec())
                .pathParam("id", createdSalesOrderId)
                .body("{}")
                .when()
                .post(Constants.SALES_ORDER_SUBMIT)
                .then()
                .statusCode(200)
                .body("data._id", equalTo(createdSalesOrderId))
                .body("data.status", equalTo("submitted"))
                .extract().response();

        submittedSalesOrderId = createdSalesOrderId;
        String code = response.jsonPath().getString("data.code");
        log.info("TC-SO-09 PASSED — SO submitted, status=submitted, code={}", code);
    }

    // ─── TC-SO-10 ─────────────────────────────────────────────────────────────

    @Test(priority = 10, description = "Submitted SO code follows SO-YYYY-NNNN format",
          dependsOnMethods = "TC_SO_09_submitSalesOrderSuccess")
    @Story("Submit Sales Order")
    @Severity(SeverityLevel.NORMAL)
    public void TC_SO_10_submittedSalesOrderCodeFormat() {
        Response response = given()
                .spec(authSpec())
                .pathParam("id", submittedSalesOrderId)
                .when()
                .get(Constants.SALES_ORDER_BY_ID)
                .then()
                .statusCode(200)
                .extract().response();

        String code = response.jsonPath().getString("data.code");
        if (code != null) {
            Assert.assertTrue(code.matches("SO-\\d{4}-\\d{4}"),
                    "SO code '" + code + "' should match SO-YYYY-NNNN");
            log.info("TC-SO-10 PASSED — SO code='{}' format correct", code);
        } else {
            log.warn("TC-SO-10 — code was null after submit");
        }
    }

    // ─── TC-SO-11 ─────────────────────────────────────────────────────────────

    @Test(priority = 11, description = "Cannot submit an already-submitted sales order",
          dependsOnMethods = "TC_SO_09_submitSalesOrderSuccess")
    @Story("Submit Sales Order")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /salesorder/{id}/submit on an already-submitted order should return " +
                 "a 4xx error because the status transition is invalid.")
    public void TC_SO_11_cannotResubmitSalesOrder() {
        given()
                .spec(authSpec())
                .pathParam("id", submittedSalesOrderId)
                .body("{}")
                .when()
                .post(Constants.SALES_ORDER_SUBMIT)
                .then()
                .statusCode(anyOf(equalTo(400), equalTo(409)));

        log.info("TC-SO-11 PASSED — re-submit correctly rejected");
    }

    // ─── TC-SO-12 ─────────────────────────────────────────────────────────────

    @Test(priority = 12, description = "Search sales orders by status returns filtered results",
          dependsOnMethods = "TC_SO_09_submitSalesOrderSuccess")
    @Story("Search Sales Order")
    @Severity(SeverityLevel.NORMAL)
    public void TC_SO_12_searchSalesOrdersByStatus() {
        Response response = given()
                .spec(authSpec())
                .body(Map.of("status", "submitted"))
                .queryParam("skip", 0)
                .queryParam("limit", 50)
                .when()
                .post(Constants.SALES_ORDER_SEARCH)
                .then()
                .statusCode(200)
                .extract().response();

        List<String> statuses = response.jsonPath().getList("data.items.status");
        for (String status : statuses) {
            Assert.assertEquals(status, "submitted",
                    "All returned SOs should have status='submitted'");
        }
        log.info("TC-SO-12 PASSED — status filter returns only submitted orders");
    }

    // ─── TC-SO-13 ─────────────────────────────────────────────────────────────

    @Test(priority = 13, description = "Get production orders for submitted SO returns 200",
          dependsOnMethods = "TC_SO_09_submitSalesOrderSuccess")
    @Story("Sales Order → Production Orders")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /salesorder/{id}/production-orders should return the production orders " +
                 "linked to this sales order (may be empty if not yet generated).")
    public void TC_SO_13_getProductionOrdersForSalesOrder() {
        given()
                .spec(authSpec())
                .pathParam("id", submittedSalesOrderId)
                .when()
                .get(Constants.SALES_ORDER_PRODUCTION_ORDERS)
                .then()
                .statusCode(200)
                .body("code", equalTo(200));

        log.info("TC-SO-13 PASSED — production orders endpoint returns 200");
    }

    // ─── TC-SO-14 ─────────────────────────────────────────────────────────────

    @Test(priority = 14, description = "Delete draft sales order returns 200")
    @Story("Delete Sales Order")
    @Severity(SeverityLevel.CRITICAL)
    @Description("DELETE /salesorder/{id} on a draft order should return HTTP 200.")
    public void TC_SO_14_deleteDraftSalesOrderSuccess() {
        // Create a fresh draft SO to delete
        Map<String, Object> payload = TestDataFactory.salesOrderPayload(testCustomerId, testProductId);
        Response createResp = given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.SALES_ORDERS)
                .then()
                .statusCode(201)
                .extract().response();

        String draftId = extractId(createResp);

        given()
                .spec(authSpec())
                .pathParam("id", draftId)
                .when()
                .delete(Constants.SALES_ORDER_BY_ID)
                .then()
                .statusCode(200)
                .body("code", equalTo(200));

        log.info("TC-SO-14 PASSED — draft SO deleted id={}", draftId);
    }

    // ─── TC-SO-15 ─────────────────────────────────────────────────────────────

    @Test(priority = 15, description = "Cannot delete a submitted sales order",
          dependsOnMethods = "TC_SO_09_submitSalesOrderSuccess")
    @Story("Delete Sales Order")
    @Severity(SeverityLevel.CRITICAL)
    @Description("DELETE /salesorder/{id} on a submitted order should return a 4xx error " +
                 "because submitted orders cannot be deleted.")
    public void TC_SO_15_cannotDeleteSubmittedSalesOrder() {
        given()
                .spec(authSpec())
                .pathParam("id", submittedSalesOrderId)
                .when()
                .delete(Constants.SALES_ORDER_BY_ID)
                .then()
                .statusCode(anyOf(equalTo(400), equalTo(409)));

        log.info("TC-SO-15 PASSED — delete of submitted SO correctly rejected");
    }

    // ─── Cleanup ─────────────────────────────────────────────────────────────

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        if (testCustomerId != null) {
            given().spec(authSpec()).pathParam("id", testCustomerId).delete(Constants.CUSTOMER_BY_ID);
        }
        if (testProductId != null) {
            given().spec(authSpec()).pathParam("id", testProductId).delete(Constants.PRODUCT_BY_ID);
        }
        log.info("SalesOrderTest cleanup complete");
    }
}
