# Project W API Test Suite

TestNG + REST Assured automation for the **Project W Backend** — a Node.js/Express/MongoDB manufacturing ERP.

## Tech Stack

| Tool | Version | Purpose |
|---|---|---|
| **TestNG** | 7.9.0 | Test framework, suite management, dependency ordering |
| **REST Assured** | 5.4.0 | HTTP client for API testing |
| **Allure** | 2.27.0 | HTML test reports with request/response logs |
| **Jackson** | 2.17.1 | JSON serialisation/deserialisation |
| **Lombok** | 1.18.32 | Reduces POJO boilerplate |
| **JavaFaker** | 1.0.2 | Randomised, realistic test data |
| **Logback** | 1.5.6 | Test execution logging |
| **Maven** | 3.x | Build and dependency management |

---

## Project Structure

```
Project_W_Tests/
├── pom.xml
└── src/test/
    ├── java/com/projectw/
    │   ├── base/
    │   │   └── BaseTest.java           ← RestAssured config, admin login, shared helpers
    │   ├── models/
    │   │   ├── ApiResponse.java        ← Generic { code, message, data } wrapper
    │   │   ├── VendorModel.java
    │   │   ├── CustomerModel.java
    │   │   └── SalesOrderModel.java
    │   ├── utils/
    │   │   ├── ConfigManager.java      ← Reads config.properties (overridable via system props)
    │   │   ├── Constants.java          ← All API path constants
    │   │   └── TestDataFactory.java    ← Faker-based payload builders
    │   └── tests/
    │       ├── AuthTest.java           ← 10 auth test cases
    │       ├── VendorTest.java         ← 15 vendor CRUD test cases
    │       ├── ProductTest.java        ← 14 product CRUD test cases
    │       ├── CustomerTest.java       ← 14 customer CRUD test cases
    │       ├── SalesOrderTest.java     ← 15 sales order lifecycle test cases
    │       └── AttendanceTest.java     ← 17 attendance + shift + geofence test cases
    └── resources/
        ├── config.properties           ← Base URL, credentials, timeouts
        ├── testng.xml                  ← Suite definition (smoke + full regression)
        └── logback-test.xml            ← Logging configuration
```

---

## Prerequisites

1. **Java 11+** and **Maven 3.6+** installed
2. **Project_W_BE server running** on `http://localhost:3001`
3. **Test credentials exist** in the database:
   - EID: `eid0001`
   - Admin email: `admin@projectw.com`
   - Admin password: `Admin@1234`

   To seed test data, run in Project_W_BE:
   ```bash
   node scripts/create-default-roles.js
   ```

---

## Configuration

Edit `src/test/resources/config.properties` to match your environment:

```properties
base.url=http://localhost:3001
api.base.path=/accounts/api
test.eid=eid0001
test.admin.email=admin@projectw.com
test.admin.password=Admin@1234
```

Or override via Maven system properties (useful for CI):

```bash
mvn test -Dbase.url=http://staging.projectw.internal:3001 \
         -Dtest.admin.email=ci@projectw.com \
         -Dtest.admin.password=CIPassword@123
```

---

## Running Tests

### Run full suite
```bash
mvn test
```

### Run a specific test class
```bash
mvn test -Dtest=VendorTest
mvn test -Dtest=AuthTest
mvn test -Dtest=SalesOrderTest
```

### Run smoke tests only
```bash
mvn test -Dgroups=smoke
```

### Run and generate Allure report
```bash
mvn test
mvn allure:report        # generates target/site/allure-maven-plugin/index.html
mvn allure:serve         # opens report in browser
```

---

## Test Cases

### Authentication (`AuthTest`) — 10 cases

| TC ID | Description | Severity |
|---|---|---|
| TC-AUTH-01 | Valid admin login returns 200 + JWT token | BLOCKER |
| TC-AUTH-02 | Wrong password returns 401 | CRITICAL |
| TC-AUTH-03 | Unknown email returns 401/404 | NORMAL |
| TC-AUTH-04 | Missing eid returns error | NORMAL |
| TC-AUTH-05 | Empty body returns 400 | MINOR |
| TC-AUTH-06 | Protected endpoint without token returns 401 | BLOCKER |
| TC-AUTH-07 | Malformed token returns 401 | CRITICAL |
| TC-AUTH-08 | Tampered JWT returns 401 | CRITICAL |
| TC-AUTH-09 | Login response contains user data with email + eid | NORMAL |
| TC-AUTH-10 | Health check is publicly accessible | NORMAL |

### Vendor Management (`VendorTest`) — 15 cases

| TC ID | Description | Severity |
|---|---|---|
| TC-VND-01 | Create vendor → 201 + auto-code | BLOCKER |
| TC-VND-02 | Create without name → 400 | CRITICAL |
| TC-VND-03 | Get all vendors → paginated list | CRITICAL |
| TC-VND-04 | Get by valid ID → correct data | CRITICAL |
| TC-VND-05 | Get by invalid ObjectId → 400 | NORMAL |
| TC-VND-06 | Get by non-existent ID → 404 | NORMAL |
| TC-VND-07 | Update vendor → updated fields reflected | CRITICAL |
| TC-VND-08 | Search by name → filtered results | NORMAL |
| TC-VND-09 | Search by specialization → filtered results | NORMAL |
| TC-VND-10 | Get recommended vendors → 200 | NORMAL |
| TC-VND-11 | Get performance report → metrics structure | NORMAL |
| TC-VND-12 | Soft delete → is_active=false | CRITICAL |
| TC-VND-13 | Deactivated vendor excluded from active list | NORMAL |
| TC-VND-14 | Code format: VEND-YYYY-NNNN | NORMAL |
| TC-VND-15 | Duplicate names allowed | MINOR |

### Product Management (`ProductTest`) — 14 cases

| TC ID | Description | Severity |
|---|---|---|
| TC-PRD-01 | Create drill product → 201 + DR-YYYY-NNN code | BLOCKER |
| TC-PRD-02 | Create without name → error | CRITICAL |
| TC-PRD-03 | Create with invalid category → error | NORMAL |
| TC-PRD-04 | Get all products → list | CRITICAL |
| TC-PRD-05 | Get by valid ID → correct data | CRITICAL |
| TC-PRD-06 | Get by invalid ObjectId → 400 | NORMAL |
| TC-PRD-07 | Get by non-existent ID → 404 | NORMAL |
| TC-PRD-08 | Update coating + price → reflected | CRITICAL |
| TC-PRD-09 | Search by name → filtered | NORMAL |
| TC-PRD-10 | Search by category → all match | NORMAL |
| TC-PRD-11 | Search by price range → within range | MINOR |
| TC-PRD-12 | Drill code format: DR-YYYY-NNN | NORMAL |
| TC-PRD-13 | Delete product → 200 | CRITICAL |
| TC-PRD-14 | Get deleted product → 404 | NORMAL |

### Customer Management (`CustomerTest`) — 14 cases

| TC ID | Description | Severity |
|---|---|---|
| TC-CUST-01 | Create customer → 201 + CUST code | BLOCKER |
| TC-CUST-02 | Create supplier → 201 + SUPP code | CRITICAL |
| TC-CUST-03 | Create without name → error | CRITICAL |
| TC-CUST-04 | Create with invalid type → error | NORMAL |
| TC-CUST-05 | Get all customers → list | CRITICAL |
| TC-CUST-06 | Get by valid ID → correct data | CRITICAL |
| TC-CUST-07 | Get by invalid ObjectId → 400 | NORMAL |
| TC-CUST-08 | Get by non-existent ID → 404 | NORMAL |
| TC-CUST-09 | Update contact details → reflected | CRITICAL |
| TC-CUST-10 | Search by name → filtered | NORMAL |
| TC-CUST-11 | Search by type → all match | NORMAL |
| TC-CUST-12 | Customer code: CUST-YYYY-NNNN | NORMAL |
| TC-CUST-13 | Supplier code: SUPP-YYYY-NNNN | NORMAL |
| TC-CUST-14 | Delete customer → 200 | CRITICAL |

### Sales Order Lifecycle (`SalesOrderTest`) — 15 cases

| TC ID | Description | Severity |
|---|---|---|
| TC-SO-01 | Create SO in draft → 201 | BLOCKER |
| TC-SO-02 | Create without customer_id → error | CRITICAL |
| TC-SO-03 | Create with empty items → error | CRITICAL |
| TC-SO-04 | Get all SOs → list | CRITICAL |
| TC-SO-05 | Get by valid ID → status=draft | CRITICAL |
| TC-SO-06 | Get by invalid ObjectId → 400 | NORMAL |
| TC-SO-07 | Get by non-existent ID → 404 | NORMAL |
| TC-SO-08 | Update draft SO → reflected | CRITICAL |
| TC-SO-09 | Submit SO → status=submitted + code generated | BLOCKER |
| TC-SO-10 | Submitted SO code: SO-YYYY-NNNN | NORMAL |
| TC-SO-11 | Re-submit already submitted SO → error | CRITICAL |
| TC-SO-12 | Search by status → all match | NORMAL |
| TC-SO-13 | Get production orders for SO → 200 | NORMAL |
| TC-SO-14 | Delete draft SO → 200 | CRITICAL |
| TC-SO-15 | Delete submitted SO → error | CRITICAL |

### Attendance & Workforce (`AttendanceTest`) — 17 cases

| TC ID | Description | Severity |
|---|---|---|
| TC-ATT-01 | Check-in → 200 + in_progress status | BLOCKER |
| TC-ATT-02 | Double check-in → 400 | CRITICAL |
| TC-ATT-03 | Get today's attendance → in_progress | CRITICAL |
| TC-ATT-04 | Start break → 200 | NORMAL |
| TC-ATT-05 | Break without check-in → 400 (documented) | NORMAL |
| TC-ATT-06 | End break → 200 + duration set | NORMAL |
| TC-ATT-07 | Check-out → 200 + check_out_at set | BLOCKER |
| TC-ATT-08 | Check-out without check-in → 400 | CRITICAL |
| TC-ATT-09 | Get attendance history → list | NORMAL |
| TC-ATT-10 | Get attendance settings → 200 | NORMAL |
| TC-ATT-11 | Update attendance settings → 200 | NORMAL |
| TC-ATT-12 | Create shift → 201 | CRITICAL |
| TC-ATT-13 | Get all shifts → list | NORMAL |
| TC-ATT-14 | Create circular geofence → 201 | CRITICAL |
| TC-ATT-15 | Create polygonal geofence → 201 | CRITICAL |
| TC-ATT-16 | Get all geofences → list | NORMAL |
| TC-ATT-17 | Delete geofence → 200 | NORMAL |

---

## Total: 85 test cases across 6 test classes

---

## Design Decisions

- **`@BeforeSuite` admin login**: JWT token is acquired once per suite run and shared across all tests via `BaseTest.adminToken`. This avoids repeated login calls.
- **`dependsOnMethods`**: Tests that need a created resource (e.g., TC-VND-04 needs TC-VND-01's ID) declare the dependency explicitly. TestNG skips dependent tests if the prerequisite fails.
- **Soft assertions**: Status code checks use `statusCode(anyOf(...))` where the API may return multiple valid codes (e.g., 400 or 409 for validation errors).
- **Test isolation**: Each test class creates its own test data in `@BeforeClass` and cleans up in `@AfterClass`.
- **Allure integration**: Every request/response is automatically captured in the Allure report via `AllureRestAssured` filter.
- **No hardcoded IDs**: All entity IDs are captured from create responses and passed to subsequent tests.
