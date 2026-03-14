# Customer Module — API Test Coverage

This document lists all test cases covered in `CustomerTest.java` for the Project W Customer API.

**Source:** `src/test/java/com/projectw/tests/CustomerTest.java`  
**Base path:** `/accounts/api/customer`  
**Reference:** `Project_W_BE/docs/CUSTOMER_MODULE_API_TEST_CASES.md`

---

## Summary

| Section | Test IDs | Count | Endpoint(s) |
|---------|----------|-------|-------------|
| Authentication | AUTH-01 to AUTH-05 | 4 | GET / |
| Create | C01–C12, C02b | 13 | POST / |
| List | L01–L05 | 5 | GET / |
| Get by ID | G01–G04 | 4 | GET /:id |
| Update | U01–U07 | 6 | PUT /:id |
| Delete | D01–D03 | 3 | DELETE /:id |
| Search | S01–S11 | 11 | POST /search |
| **Total** | | **46** | |

---

## 1. Authentication Tests

| ID | Method Name | Description | Request | Expected | Severity |
|----|-------------|-------------|---------|----------|----------|
| AUTH-01 | `AUTH_01_noAuthorizationReturns401` | No Authorization header returns 401 | GET / (no auth) | 401, `success: false`, message contains "token" | Blocker |
| AUTH-02 | `AUTH_02_malformedTokenReturns401` | Malformed token (e.g. "Token xyz") returns 401 | GET / with `Authorization: Token xyz` | 401, `success: false` | Blocker |
| AUTH-03 | `AUTH_03_invalidJwtReturns401` | Invalid JWT returns 401 | GET / with `Authorization: Bearer invalid.jwt.token` | 401, `success: false` | Blocker |
| AUTH-05 | `AUTH_05_validJwtReturns200` | Valid JWT returns 200 | GET / with valid Bearer token | 200, `code: 200`, data not null | Blocker |

*Note: AUTH-04 (Expired JWT) not implemented — requires custom expired token generation.*

---

## 2. Create Customer (POST /)

| ID | Method Name | Description | Request Body | Expected | Severity |
|----|-------------|-------------|--------------|----------|----------|
| C01 | `C01_createCustomerRequiredFields` | Create customer with required fields only | `customer_type`, `name`, `billing_address`, `gst_number` | 200/201, `data._id`, `data.code` auto-generated (CUST-*) | Blocker |
| C02 | `C02_createSupplierRequiredFields` | Create supplier with required fields | `customer_type: supplier`, `name`, `billing_address`, `gst_number` | 200/201, `data.code` starts with SUPP | Critical |
| C02b | `C02b_createSupplierWithFullOptionalFields` | Create supplier with full optional fields | All required + `vendor_code`, `shipping_address`, `tax_type: IGST`, `contact_details`, `bank_details` | 200/201, all optional fields saved for supplier | Critical |
| C03 | `C03_createWithFullOptionalFields` | Create customer with full optional fields | All required + `vendor_code`, `shipping_address`, `tax_type`, `contact_details`, `bank_details` | 200/201, all optional fields saved | Critical |
| C04 | `C04_createWithPrimaryContact` | Create with contact_details and primary flag | Required + `contact_details` with `is_primary: true` | 200/201 | Normal |
| C05 | `C05_createWithMultiplePrimaryContacts` | Create with multiple primary contacts | Multiple contacts with `is_primary: true` | 200/201 (pre-save keeps first) | Normal |
| C06 | `C06_createInvalidCustomerTypeReturns409` | Create with invalid customer_type | `customer_type: "invalid"` | 409 | Critical |
| C07 | `C07_createMissingCustomerTypeReturns409` | Create with missing customer_type | Omits `customer_type` | 409 | Critical |
| C08 | `C08_createMissingNameReturns409` | Create with missing name | Omits `name` | 409 | Critical |
| C09 | `C09_createMissingBillingAddressReturns409` | Create with missing billing_address | Omits `billing_address` | 409 | Critical |
| C10 | `C10_createMissingGstNumberReturns409` | Create with missing gst_number | Omits `gst_number` | 409 | Critical |
| C11 | `C11_createInvalidTaxTypeReturns409` | Create with invalid tax_type | `tax_type: "INVALID"` | 409 | Normal |
| C12 | `C12_createWithCodeInBodyIgnored` | Create with code in body (ignored) | Body includes `code: "CUSTOM-999"` | 200/201, returned `code` ≠ CUSTOM-999 | Normal |

---

## 3. List Customers (GET /)

| ID | Method Name | Description | Query Params | Expected | Severity |
|----|-------------|-------------|--------------|----------|----------|
| L01 | `L01_listAllCustomers` | List all customers (no filter) | None | 200, `data` not null | Critical |
| L02 | `L02_listFilterByCustomerType` | Filter by customer_type=customer | `customer_type=customer` | 200, all items have `customer_type: customer` | Normal |
| L03 | `L03_listFilterBySupplierType` | Filter by customer_type=supplier | `customer_type=supplier` | 200, all items have `customer_type: supplier` | Normal |
| L04 | `L04_listInvalidCustomerTypeIgnored` | Invalid customer_type ignored | `customer_type=invalid` | 200, all returned | Normal |
| L05 | `L05_listWithFlagsInBody` | With flags in body | Body `{ flags: {} }` | 200 | Minor |

---

## 4. Get Customer by ID (GET /:id)

| ID | Method Name | Description | Path | Expected | Severity |
|----|-------------|-------------|------|----------|----------|
| G01 | `G01_getCustomerByIdSuccess` | Valid ObjectId, customer exists | `/:id` (created customer) | 200, `data._id`, `data.name` match | Critical |
| G02 | `G02_getCustomerNotFoundReturns404` | Valid ObjectId, customer does not exist | `/:id` (000000000000000000000001) | 404, message contains "not found" | Critical |
| G03 | `G03_getCustomerInvalidIdReturns400` | Invalid ObjectId | `/abc123` | 400, message contains "invalid" | Critical |
| G04 | `G04_getCustomerEmptyIdReturns400` | Empty/invalid ID | `/ ` (space) | 400 or 404 | Normal |

---

## 5. Update Customer (PUT /:id)

| ID | Method Name | Description | Request | Expected | Severity |
|----|-------------|-------------|---------|----------|----------|
| U01 | `U01_updateCustomerFullDocument` | Update with full valid document | Full customer object with updated name/address | 200, updated fields reflected | Critical |
| U02 | `U02_updateInvalidIdReturns400` | Invalid ObjectId | `/abc123` + valid body | 400 | Normal |
| U03 | `U03_updateNotFoundReturns404` | Valid ObjectId, customer not found | Non-existent id + valid body | 404 | Normal |
| U05 | `U05_updateWithCodeInBodyIgnored` | Body includes code (ignored) | Body with `code: "CUSTOM-999"` | 200, `code` not changed | Normal |
| U06 | `U06_updatePartialFields` | Partial update (full replacement semantics) | Only `name`, `billing_address`, required fields | 200, omitted fields cleared | Normal |
| U07 | `U07_updateInvalidCustomerTypeReturns409` | Invalid customer_type in body | `customer_type: "invalid"` | 409 | Normal |

*Note: U04 (Customer from another tenant) not implemented — requires multi-tenant setup.*

---

## 6. Delete Customer (DELETE /:id)

| ID | Method Name | Description | Path | Expected | Severity |
|----|-------------|-------------|------|----------|----------|
| D01 | `D01_deleteCustomerSuccess` | Delete existing customer | `/:id` (newly created disposable customer) | 200, message contains "deleted" | Critical |
| D02 | `D02_deleteNotFoundReturns404` | Delete non-existent customer | `/:id` (000000000000000000000001) | 404 | Normal |
| D03 | `D03_deleteInvalidIdReturns400` | Delete with invalid ObjectId | `/abc123` | 400 | Normal |

---

## 7. Search Customers (POST /search)

| ID | Method Name | Description | Body | Query | Expected | Severity |
|----|-------------|-------------|------|-------|----------|----------|
| S01 | `S01_searchNoFilters` | No filters, no pagination | `{}` | - | 200, `data.items` present | Critical |
| S02 | `S02_searchByName` | Filter by name (case-insensitive) | `{ name: "Partial" }` | skip=0, limit=50 | 200, items array | Normal |
| S03 | `S03_searchByCode` | Filter by code | `{ code: "CUST" }` | skip=0, limit=50 | 200, `data.items` not null | Normal |
| S04 | `S04_searchByGstNumber` | Filter by gst_number | `{ gst_number: "27AABC" }` | skip=0, limit=50 | 200, `data.items` not null | Normal |
| S05 | `S05_searchByCustomerType` | Filter by customer_type=customer | `{ customer_type: "customer" }` | skip=0, limit=50 | 200, all items customer type | Normal |
| S06 | `S06_searchBySupplierType` | Filter by customer_type=supplier | `{ customer_type: "supplier" }` | skip=0, limit=50 | 200, all items supplier type | Normal |
| S07 | `S07_searchInvalidCustomerTypeIgnored` | Invalid customer_type ignored | `{ customer_type: "invalid" }` | skip=0, limit=50 | 200 | Minor |
| S08 | `S08_searchWithPagination` | Pagination with skip and limit | `{}` | skip=10, limit=50 | 200, `data.skip=10`, `data.limit=50` | Normal |
| S09 | `S09_searchLimitCappedAtMax` | Limit capped at max | `{}` | limit=5000 | 200, `data.limit` ≤ 2000 | Minor |
| S10 | `S10_searchEmptyFilterIgnored` | Empty/whitespace filter ignored | `{ name: "   " }` | skip=0, limit=50 | 200 | Minor |
| S11 | `S11_searchCombinedFilters` | Combined filters | `{ name: "ABC", customer_type: "customer" }` | skip=0, limit=50 | 200, `data.items` not null | Normal |

---

## Test Dependencies

- **C01** creates the primary customer used by: L01, G01, U01, U05, U06, U07, S02, D01
- **C02** creates the supplier used in cleanup
- **@AfterClass** deletes `createdCustomerId` and `createdSupplierId`

---

## Run Commands

```powershell
# Run CustomerTest only (Extent Report generated automatically)
mvn test -Dtest=CustomerTest

# Run CustomerTest via dedicated suite
mvn test -Pcustomer
```

## Extent Report

After each run, an Extent Report HTML file is generated at:

```
target/extent-reports/ProjectW_TestReport_<yyyy-MM-dd_HH-mm-ss>.html
```

The report includes:
- Test name and description for each case
- PASS / FAIL / SKIP status with colour-coded labels
- Exception stack trace on failures
- Category filter by test class (CustomerTest)

`ExtentReportListener` is registered in `BaseTest` (which `CustomerTest` extends) and in `testng.xml` / `testng-customer.xml`.

---

*Generated from CustomerTest.java. Last updated: March 2026.*
