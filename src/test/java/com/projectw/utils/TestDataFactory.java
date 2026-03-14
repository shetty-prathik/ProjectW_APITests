package com.projectw.utils;

import com.github.javafaker.Faker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates realistic, randomised test data using JavaFaker.
 * All factory methods return Map<String, Object> payloads ready for REST Assured.
 */
public class TestDataFactory {

    private static final Faker faker = new Faker();

    private TestDataFactory() {}

    // ── Auth ─────────────────────────────────────────────────────────────────

    public static Map<String, Object> loginPayload(String eid, String email, String password) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eid", eid);
        payload.put("email", email);
        payload.put("password", password);
        return payload;
    }

    public static Map<String, Object> registerPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("firstName", faker.name().firstName());
        payload.put("lastName", faker.name().lastName());
        payload.put("email", "test_" + UUID.randomUUID().toString().substring(0, 8) + "@projectw.test");
        payload.put("password", "Test@" + faker.number().digits(4));
        payload.put("eid", ConfigManager.getTestEid());
        return payload;
    }

    // ── Vendor ───────────────────────────────────────────────────────────────

    public static Map<String, Object> vendorPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", faker.company().name() + " Coatings");
        payload.put("contact_person", faker.name().fullName());
        payload.put("email", faker.internet().emailAddress());
        payload.put("phone", faker.phoneNumber().phoneNumber());
        payload.put("address", faker.address().fullAddress());
        payload.put("gstin", "27" + faker.number().digits(12));
        payload.put("specializations", List.of("coating", "heat_treatment"));
        payload.put("is_active", true);
        payload.put("payment_terms", "30 Days");
        payload.put("credit_days", 30);
        payload.put("notes", "Auto-generated test vendor");
        return payload;
    }

    public static Map<String, Object> vendorUpdatePayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("contact_person", faker.name().fullName());
        payload.put("phone", faker.phoneNumber().phoneNumber());
        payload.put("notes", "Updated by automation test — " + UUID.randomUUID());
        payload.put("credit_days", 45);
        return payload;
    }

    // ── Customer ─────────────────────────────────────────────────────────────

    /** Valid 15-char Indian GST format (state 2 + PAN 10 + entity 2 + check 1). */
    private static String randomGstNumber(String stateCode) {
        return stateCode + "AABCU9603R" + faker.number().digits(2) + "Z";
    }

    public static Map<String, Object> customerPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("customer_type", "customer");
        payload.put("name", faker.company().name());
        payload.put("gst_number", randomGstNumber("27"));
        payload.put("tax_type", "GST");
        payload.put("payment_terms", "45 Days");
        payload.put("billing_address", faker.address().fullAddress());
        payload.put("shipping_address", faker.address().fullAddress());
        payload.put("contact_details", List.of(Map.of(
                "contact_person_name", faker.name().fullName(),
                "contact_number", faker.phoneNumber().phoneNumber(),
                "email", faker.internet().emailAddress(),
                "is_primary", true
        )));
        return payload;
    }

    /** Minimal customer payload — required fields only (C01). */
    public static Map<String, Object> customerMinimalPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("customer_type", "customer");
        payload.put("name", "ABC Ltd " + faker.number().digits(4));
        payload.put("billing_address", "123 Main St, City - 400001");
        payload.put("gst_number", "27AABCU9603R1ZM");
        return payload;
    }

    /** Minimal supplier payload — required fields only (C02). */
    public static Map<String, Object> supplierMinimalPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("customer_type", "supplier");
        payload.put("name", "XYZ Supplies " + faker.number().digits(4));
        payload.put("billing_address", "456 Oak Ave, City - 400002");
        payload.put("gst_number", "29ABCDE1234F1Z5");
        return payload;
    }

    /** Full supplier payload with all optional fields. */
    public static Map<String, Object> supplierFullPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("customer_type", "supplier");
        payload.put("name", "Full Supplier Pvt Ltd " + faker.number().digits(4));
        payload.put("billing_address", "789, Supplier Street, City - 400003");
        payload.put("shipping_address", "101, Warehouse Ave, City - 400004");
        payload.put("gst_number", randomGstNumber("29"));
        payload.put("vendor_code", "SUPP-VEND-" + faker.number().digits(4));
        payload.put("tax_type", "IGST");
        payload.put("payment_terms", "45 Days");
        payload.put("contact_details", List.of(
                Map.of(
                        "contact_person_name", "Jane Smith",
                        "contact_number", "+919876543211",
                        "designation", "Procurement Manager",
                        "email", "jane@supplier.com",
                        "is_primary", true
                )
        ));
        payload.put("bank_details", List.of(Map.of(
                "bank_name", "Supplier Bank",
                "branch_name", "Industrial Branch",
                "account_no", 987654321098L,
                "ifsc_code", "SUPP0005678",
                "pan_no", "FGHIJ5678K"
        )));
        return payload;
    }

    /** Full customer payload with all optional fields (C03). */
    public static Map<String, Object> customerFullPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("customer_type", "customer");
        payload.put("name", "Test Company Pvt Ltd " + faker.number().digits(4));
        payload.put("billing_address", "123, Test Street, City - 400001");
        payload.put("shipping_address", "456, Ship Street, City - 400002");
        payload.put("gst_number", randomGstNumber("27"));
        payload.put("vendor_code", "VEND-" + faker.number().digits(4));
        payload.put("tax_type", "GST");
        payload.put("payment_terms", "30 Days");
        payload.put("contact_details", List.of(
                Map.of(
                        "contact_person_name", "John Doe",
                        "contact_number", "+919876543210",
                        "designation", "Manager",
                        "email", "john@test.com",
                        "is_primary", true
                )
        ));
        payload.put("bank_details", List.of(Map.of(
                "bank_name", "Test Bank",
                "branch_name", "Main Branch",
                "account_no", 123456789012L,
                "ifsc_code", "TEST0001234",
                "pan_no", "ABCDE1234F"
        )));
        return payload;
    }

    /** Customer with contact_details and primary flag (C04). */
    public static Map<String, Object> customerWithPrimaryContactPayload() {
        Map<String, Object> payload = customerMinimalPayload();
        payload.put("name", "Primary Contact Test " + faker.number().digits(4));
        payload.put("contact_details", List.of(Map.of(
                "contact_person_name", "John",
                "email", "j@x.com",
                "is_primary", true
        )));
        return payload;
    }

    /** Customer with multiple contacts marked primary (C05 — pre-save keeps first). */
    public static Map<String, Object> customerWithMultiplePrimaryPayload() {
        Map<String, Object> payload = customerMinimalPayload();
        payload.put("name", "Multi Primary " + faker.number().digits(4));
        payload.put("contact_details", List.of(
                Map.of("contact_person_name", "A", "email", "a@x.com", "is_primary", true),
                Map.of("contact_person_name", "B", "email", "b@x.com", "is_primary", true)
        ));
        return payload;
    }

    public static Map<String, Object> supplierPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("customer_type", "supplier");
        payload.put("name", faker.company().name() + " Suppliers");
        payload.put("gst_number", randomGstNumber("29"));
        payload.put("tax_type", "IGST");
        payload.put("payment_terms", "30 Days");
        payload.put("billing_address", faker.address().fullAddress());
        return payload;
    }

    // ── Warehouse ─────────────────────────────────────────────────────────────

    public static Map<String, Object> warehousePayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "WH-" + faker.address().cityName());
        payload.put("address", faker.address().fullAddress());
        payload.put("is_active", true);
        return payload;
    }

    // ── Raw Material ──────────────────────────────────────────────────────────

    /** Minimal raw material — price only (required). */
    public static Map<String, Object> rawMaterialMinimalPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("price", 100);
        return payload;
    }

    /** Full raw material payload with all optional fields. */
    public static Map<String, Object> rawMaterialFullPayload(String customerId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("price", 150);
        payload.put("name", "Steel Rod 10mm " + faker.number().digits(3));
        payload.put("description", "High-grade steel rod for manufacturing");
        payload.put("group", "rods");
        payload.put("location", "Warehouse A - Rack 1");
        payload.put("type", "rod");
        payload.put("delivery_code", "DEL-" + faker.number().digits(3));
        payload.put("length", 1200);
        payload.put("lead", 5);
        payload.put("diameter", 10);
        payload.put("width", 50);
        payload.put("helixAngle", 15);
        payload.put("degree", 90);
        payload.put("numberOfHoles", 4);
        payload.put("grade", "A");
        payload.put("innerDiameter", 8);
        payload.put("bondType", 1);
        payload.put("gritSize", 60);
        payload.put("bondWidth", 25);
        if (customerId != null && !customerId.isBlank()) {
            payload.put("customer_id", customerId);
        }
        return payload;
    }

    /** Wheel-type raw material (code WH-0001). */
    public static Map<String, Object> rawMaterialWheelPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("price", 200);
        payload.put("name", "Grinding Wheel 100mm " + faker.number().digits(3));
        payload.put("group", "wheel");
        payload.put("type", "wheel");
        payload.put("diameter", 100);
        payload.put("grade", "A");
        return payload;
    }

    /** Wheel-type by group only (no type). */
    public static Map<String, Object> rawMaterialWheelGroupOnlyPayload() {
        return Map.of(
                "price", 150,
                "group", "wheel"
        );
    }

    public static Map<String, Object> rawMaterialPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("price", faker.number().randomDouble(2, 100, 5000));
        payload.put("name", "Carbide Rod " + faker.number().digits(3));
        payload.put("description", "Solid carbide rod for drill manufacturing");
        payload.put("group", "carbide");
        payload.put("type", "rod");
        payload.put("grade", "K10");
        payload.put("diameter", faker.number().randomDouble(2, 3, 20));
        payload.put("length", faker.number().randomDouble(2, 50, 300));
        return payload;
    }

    // ── Product ───────────────────────────────────────────────────────────────

    public static Map<String, Object> productPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Drill " + faker.number().digits(4));
        payload.put("category", "drills");
        payload.put("type", "solid_carbide");
        payload.put("description", "Solid carbide drill for steel");
        payload.put("coating", "TiAlN");
        payload.put("diameter", faker.number().randomDouble(2, 1, 20));
        payload.put("overallLength", faker.number().randomDouble(2, 30, 150));
        payload.put("fluteLength", faker.number().randomDouble(2, 15, 80));
        payload.put("price", faker.number().randomDouble(2, 200, 5000));
        payload.put("hsn_code", "8207");
        return payload;
    }

    // ── Sales Order ───────────────────────────────────────────────────────────

    public static Map<String, Object> salesOrderPayload(String customerId, String productId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("customer_id", customerId);
        payload.put("type", "sales_order");
        payload.put("sale_order_date", "2025-06-01");
        payload.put("delivery_date", "2025-09-01");
        payload.put("purchase_order_no", "PO-TEST-" + faker.number().digits(4));
        payload.put("project_name", "Test Project " + faker.lorem().word());
        payload.put("items", List.of(Map.of(
                "product", productId,
                "quantity", faker.number().numberBetween(10, 100),
                "unit_price", faker.number().randomDouble(2, 100, 2000),
                "discount", 0,
                "remarks", "Test item"
        )));
        return payload;
    }

    // ── Quotation ─────────────────────────────────────────────────────────────

    public static Map<String, Object> quotationPayload(String customerId, String productId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("customer_id", customerId);
        payload.put("quotation_date", "2025-06-01");
        payload.put("valid_until", "2025-07-01");
        payload.put("gst_percentage", 18);
        payload.put("payment_terms", "30 Days");
        payload.put("project_name", "Quotation Test " + faker.lorem().word());
        payload.put("items", List.of(Map.of(
                "product", productId,
                "description", "Solid carbide drill",
                "quantity", 50,
                "price_per_unit", faker.number().randomDouble(2, 100, 2000),
                "discount_percentage", 5,
                "remarks", "Test quotation item"
        )));
        return payload;
    }

    // ── Shift ─────────────────────────────────────────────────────────────────

    public static Map<String, Object> shiftPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Shift-" + faker.lorem().word());
        payload.put("start_time", "09:00");
        payload.put("end_time", "18:00");
        payload.put("overtime_after_minutes", 480);
        payload.put("break_duration", 60);
        payload.put("grace_period", 15);
        payload.put("work_days", List.of("monday", "tuesday", "wednesday", "thursday", "friday"));
        payload.put("work_day_attribution", "check_in");
        payload.put("is_active", true);
        return payload;
    }

    // ── Geofence ─────────────────────────────────────────────────────────────

    public static Map<String, Object> circularGeofencePayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Factory Geofence " + faker.number().digits(3));
        payload.put("description", "Main factory premises");
        payload.put("type", "circular");
        payload.put("center", Map.of("latitude", 19.0596, "longitude", 72.8295));
        payload.put("radius_meters", 200);
        payload.put("rules", Map.of(
                "check_in_required", true,
                "check_out_required", true,
                "allow_outside_check_in", false,
                "allow_outside_check_out", false
        ));
        return payload;
    }

    public static Map<String, Object> polygonGeofencePayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Zone Geofence " + faker.number().digits(3));
        payload.put("type", "polygonal");
        payload.put("coordinates", List.of(
                Map.of("latitude", 19.058, "longitude", 72.828),
                Map.of("latitude", 19.058, "longitude", 72.831),
                Map.of("latitude", 19.061, "longitude", 72.831),
                Map.of("latitude", 19.061, "longitude", 72.828)
        ));
        payload.put("rules", Map.of(
                "check_in_required", true,
                "check_out_required", false,
                "allow_outside_check_in", true,
                "allow_outside_check_out", true
        ));
        return payload;
    }

    // ── Attendance ────────────────────────────────────────────────────────────

    public static Map<String, Object> checkInPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("timezone", "Asia/Kolkata");
        return payload;
    }

    public static Map<String, Object> checkInWithMetaPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("timezone", "Asia/Kolkata");
        payload.put("latitude", 19.0596);
        payload.put("longitude", 72.8295);
        payload.put("ip", "192.168.1.100");
        payload.put("device_id", "device-abc-001");
        payload.put("location_name", "Factory Floor A");
        return payload;
    }

    public static Map<String, Object> checkOutPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("timezone", "Asia/Kolkata");
        return payload;
    }

    public static Map<String, Object> checkOutWithMetaPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("timezone", "Asia/Kolkata");
        payload.put("latitude", 19.0596);
        payload.put("longitude", 72.8295);
        payload.put("ip", "192.168.1.101");
        payload.put("device_id", "device-abc-001");
        return payload;
    }

    public static Map<String, Object> regularizationPayload(String attendanceId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("attendance_id", attendanceId);
        payload.put("reason", "Forgot to check in on time due to system issue");
        payload.put("requested_changes", Map.of(
                "check_in_at", "2025-06-01T09:00:00.000Z",
                "check_out_at", "2025-06-01T18:00:00.000Z"
        ));
        return payload;
    }

    public static Map<String, Object> adminEditCheckInPayload(String checkInAt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("check_in_at", checkInAt);
        return payload;
    }

    public static Map<String, Object> adminCreatePayload(String checkInAt, String checkOutAt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("check_in_at", checkInAt);
        payload.put("check_out_at", checkOutAt);
        payload.put("timezone", "Asia/Kolkata");
        payload.put("attendance_type", "admin_created");
        payload.put("location_name", "Office");
        return payload;
    }
}
