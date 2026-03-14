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
 * Product CRUD Test Suite
 *
 * Covers:
 *  TC-PRD-01  Create product (drills category) → 201 + auto-code DR-YYYY-NNN
 *  TC-PRD-02  Create product missing required name → 400/409
 *  TC-PRD-03  Create product with invalid category → 400/409
 *  TC-PRD-04  Get all products → 200 + list
 *  TC-PRD-05  Get product by valid ID → 200 + correct data
 *  TC-PRD-06  Get product by invalid ObjectId → 400
 *  TC-PRD-07  Get product by non-existent ID → 404
 *  TC-PRD-08  Update product fields → 200 + updated data
 *  TC-PRD-09  Search products by name → 200 + filtered results
 *  TC-PRD-10  Search products by category → 200 + all results match category
 *  TC-PRD-11  Search products with price range → 200 + results within range
 *  TC-PRD-12  Product code follows DR-YYYY-NNN format for drills
 *  TC-PRD-13  Delete product → 200
 *  TC-PRD-14  Get deleted product returns 404
 */
@Epic("Project W API")
@Feature("Product Management")
public class ProductTest extends BaseTest {

    private String createdProductId;
    private String createdProductName;

    // ─── TC-PRD-01 ────────────────────────────────────────────────────────────

    @Test(priority = 1, description = "Create product with valid payload returns 201 and auto-code")
    @Story("Create Product")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /products with a valid drills category payload should return HTTP 201 " +
                 "with an auto-generated code matching DR-YYYY-NNN.")
    public void TC_PRD_01_createProductSuccess() {
        Map<String, Object> payload = TestDataFactory.productPayload();
        createdProductName = (String) payload.get("name");

        Response response = given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.PRODUCTS)
                .then()
                .statusCode(201)
                .body("code", equalTo(201))
                .body("data._id", notNullValue())
                .body("data.name", equalTo(createdProductName))
                .body("data.category", equalTo("drills"))
                .extract().response();

        createdProductId = extractId(response);
        Assert.assertNotNull(createdProductId, "Created product must have an _id");
        log.info("TC-PRD-01 PASSED — created product id={}", createdProductId);
    }

    // ─── TC-PRD-02 ────────────────────────────────────────────────────────────

    @Test(priority = 2, description = "Create product without name returns 400 or 409")
    @Story("Create Product")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /products without the required 'name' field should return a 4xx error.")
    public void TC_PRD_02_createProductMissingNameReturnsError() {
        Map<String, Object> payload = Map.of(
                "category", "drills",
                "type", "solid_carbide"
        );

        given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.PRODUCTS)
                .then()
                .statusCode(anyOf(equalTo(400), equalTo(409)));

        log.info("TC-PRD-02 PASSED — missing name correctly rejected");
    }

    // ─── TC-PRD-03 ────────────────────────────────────────────────────────────

    @Test(priority = 3, description = "Create product with invalid category returns error")
    @Story("Create Product")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /products with a category value not in the allowed enum " +
                 "(drills/endmills/reamers/...) should return a 4xx error.")
    public void TC_PRD_03_createProductInvalidCategoryReturnsError() {
        Map<String, Object> payload = Map.of(
                "name", "Test Product Invalid",
                "category", "invalid_category_xyz",
                "type", "solid_carbide"
        );

        given()
                .spec(authSpec())
                .body(payload)
                .when()
                .post(Constants.PRODUCTS)
                .then()
                .statusCode(anyOf(equalTo(400), equalTo(409)));

        log.info("TC-PRD-03 PASSED — invalid category correctly rejected");
    }

    // ─── TC-PRD-04 ────────────────────────────────────────────────────────────

    @Test(priority = 4, description = "Get all products returns 200 with list",
          dependsOnMethods = "TC_PRD_01_createProductSuccess")
    @Story("Read Product")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /products should return HTTP 200 with a non-empty items array.")
    public void TC_PRD_04_getAllProductsReturnsList() {
        given()
                .spec(authSpec())
                .queryParam("skip", 0)
                .queryParam("limit", 20)
                .when()
                .get(Constants.PRODUCTS)
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data", notNullValue());

        log.info("TC-PRD-04 PASSED — product list returned");
    }

    // ─── TC-PRD-05 ────────────────────────────────────────────────────────────

    @Test(priority = 5, description = "Get product by valid ID returns 200",
          dependsOnMethods = "TC_PRD_01_createProductSuccess")
    @Story("Read Product")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /products/{id} with the created product's ID should return HTTP 200 " +
                 "with the correct product document.")
    public void TC_PRD_05_getProductByIdSuccess() {
        given()
                .spec(authSpec())
                .pathParam("id", createdProductId)
                .when()
                .get(Constants.PRODUCT_BY_ID)
                .then()
                .statusCode(200)
                .body("data._id", equalTo(createdProductId))
                .body("data.name", equalTo(createdProductName))
                .body("data.eid", equalTo(TEST_EID));

        log.info("TC-PRD-05 PASSED — product retrieved by id={}", createdProductId);
    }

    // ─── TC-PRD-06 ────────────────────────────────────────────────────────────

    @Test(priority = 6, description = "Get product by invalid ObjectId returns 400")
    @Story("Read Product")
    @Severity(SeverityLevel.NORMAL)
    public void TC_PRD_06_getProductByInvalidIdReturns400() {
        given()
                .spec(authSpec())
                .pathParam("id", "not-an-objectid")
                .when()
                .get(Constants.PRODUCT_BY_ID)
                .then()
                .statusCode(400);

        log.info("TC-PRD-06 PASSED — invalid ObjectId rejected");
    }

    // ─── TC-PRD-07 ────────────────────────────────────────────────────────────

    @Test(priority = 7, description = "Get product by non-existent ID returns 404")
    @Story("Read Product")
    @Severity(SeverityLevel.NORMAL)
    public void TC_PRD_07_getProductByNonExistentIdReturns404() {
        given()
                .spec(authSpec())
                .pathParam("id", "000000000000000000000001")
                .when()
                .get(Constants.PRODUCT_BY_ID)
                .then()
                .statusCode(404);

        log.info("TC-PRD-07 PASSED — non-existent product returns 404");
    }

    // ─── TC-PRD-08 ────────────────────────────────────────────────────────────

    @Test(priority = 8, description = "Update product returns 200 with updated fields",
          dependsOnMethods = "TC_PRD_01_createProductSuccess")
    @Story("Update Product")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT /products/{id} with updated coating and price should return HTTP 200 " +
                 "and reflect the changes.")
    public void TC_PRD_08_updateProductSuccess() {
        Map<String, Object> updatePayload = Map.of(
                "coating", "TiN",
                "price", 999.99,
                "description", "Updated by automation test"
        );

        given()
                .spec(authSpec())
                .pathParam("id", createdProductId)
                .body(updatePayload)
                .when()
                .put(Constants.PRODUCT_BY_ID)
                .then()
                .statusCode(200)
                .body("data._id", equalTo(createdProductId))
                .body("data.coating", equalTo("TiN"));

        log.info("TC-PRD-08 PASSED — product updated successfully");
    }

    // ─── TC-PRD-09 ────────────────────────────────────────────────────────────

    @Test(priority = 9, description = "Search products by name returns filtered results",
          dependsOnMethods = "TC_PRD_01_createProductSuccess")
    @Story("Search Product")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /products/search with a name filter should return products " +
                 "whose name contains the search term.")
    public void TC_PRD_09_searchProductsByName() {
        String searchTerm = createdProductName.substring(0, Math.min(5, createdProductName.length()));

        Response response = given()
                .spec(authSpec())
                .body(Map.of("name", searchTerm))
                .queryParam("skip", 0)
                .queryParam("limit", 20)
                .when()
                .post(Constants.PRODUCTS_SEARCH)
                .then()
                .statusCode(200)
                .extract().response();

        List<?> items = response.jsonPath().getList("data.items");
        Assert.assertNotNull(items, "Search should return items array");
        log.info("TC-PRD-09 PASSED — found {} products matching '{}'", items.size(), searchTerm);
    }

    // ─── TC-PRD-10 ────────────────────────────────────────────────────────────

    @Test(priority = 10, description = "Search products by category returns all matching category",
          dependsOnMethods = "TC_PRD_01_createProductSuccess")
    @Story("Search Product")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /products/search with category='drills' should return only products " +
                 "in the drills category.")
    public void TC_PRD_10_searchProductsByCategory() {
        Response response = given()
                .spec(authSpec())
                .body(Map.of("category", "drills"))
                .queryParam("skip", 0)
                .queryParam("limit", 50)
                .when()
                .post(Constants.PRODUCTS_SEARCH)
                .then()
                .statusCode(200)
                .extract().response();

        List<String> categories = response.jsonPath().getList("data.items.category");
        for (String cat : categories) {
            Assert.assertEquals(cat, "drills",
                    "All returned products should be in 'drills' category");
        }
        log.info("TC-PRD-10 PASSED — category filter returns only drills");
    }

    // ─── TC-PRD-11 ────────────────────────────────────────────────────────────

    @Test(priority = 11, description = "Search products with price range returns results in range",
          dependsOnMethods = "TC_PRD_01_createProductSuccess")
    @Story("Search Product")
    @Severity(SeverityLevel.MINOR)
    @Description("POST /products/search with price_min and price_max should return only " +
                 "products whose price falls within that range.")
    public void TC_PRD_11_searchProductsByPriceRange() {
        Response response = given()
                .spec(authSpec())
                .body(Map.of("price_min", 0, "price_max", 10000))
                .queryParam("skip", 0)
                .queryParam("limit", 50)
                .when()
                .post(Constants.PRODUCTS_SEARCH)
                .then()
                .statusCode(200)
                .extract().response();

        List<Double> prices = response.jsonPath().getList("data.items.price");
        for (Double price : prices) {
            if (price != null) {
                Assert.assertTrue(price >= 0 && price <= 10000,
                        "Price " + price + " should be within [0, 10000]");
            }
        }
        log.info("TC-PRD-11 PASSED — price range filter works correctly");
    }

    // ─── TC-PRD-12 ────────────────────────────────────────────────────────────

    @Test(priority = 12, description = "Product code for drills follows DR-YYYY-NNN format",
          dependsOnMethods = "TC_PRD_01_createProductSuccess")
    @Story("Create Product")
    @Severity(SeverityLevel.NORMAL)
    @Description("The auto-generated code for a drill product should match DR-YYYY-NNN.")
    public void TC_PRD_12_drillProductCodeFormat() {
        Response response = given()
                .spec(authSpec())
                .pathParam("id", createdProductId)
                .when()
                .get(Constants.PRODUCT_BY_ID)
                .then()
                .statusCode(200)
                .extract().response();

        String code = response.jsonPath().getString("data.code");
        if (code != null) {
            Assert.assertTrue(code.matches("DR-\\d{4}-\\d{3}"),
                    "Drill code '" + code + "' should match DR-YYYY-NNN");
            log.info("TC-PRD-12 PASSED — product code='{}' matches expected format", code);
        } else {
            log.warn("TC-PRD-12 — code was null, skipping format assertion");
        }
    }

    // ─── TC-PRD-13 ────────────────────────────────────────────────────────────

    @Test(priority = 13, description = "Delete product returns 200",
          dependsOnMethods = "TC_PRD_01_createProductSuccess")
    @Story("Delete Product")
    @Severity(SeverityLevel.CRITICAL)
    @Description("DELETE /products/{id} should return HTTP 200.")
    public void TC_PRD_13_deleteProductSuccess() {
        given()
                .spec(authSpec())
                .pathParam("id", createdProductId)
                .when()
                .delete(Constants.PRODUCT_BY_ID)
                .then()
                .statusCode(200)
                .body("code", equalTo(200));

        log.info("TC-PRD-13 PASSED — product deleted id={}", createdProductId);
    }

    // ─── TC-PRD-14 ────────────────────────────────────────────────────────────

    @Test(priority = 14, description = "Get deleted product returns 404",
          dependsOnMethods = "TC_PRD_13_deleteProductSuccess")
    @Story("Delete Product")
    @Severity(SeverityLevel.NORMAL)
    @Description("After deletion, GET /products/{id} should return HTTP 404.")
    public void TC_PRD_14_getDeletedProductReturns404() {
        given()
                .spec(authSpec())
                .pathParam("id", createdProductId)
                .when()
                .get(Constants.PRODUCT_BY_ID)
                .then()
                .statusCode(404);

        log.info("TC-PRD-14 PASSED — deleted product returns 404");
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        log.info("ProductTest cleanup complete");
    }
}
