package com.projectw.utils;

/**
 * API path constants derived from Project_W_BE route definitions.
 * All paths are relative to the API base path (/accounts/api).
 */
public final class Constants {

    private Constants() {}

    // ── Auth ─────────────────────────────────────────────────────────────────
    public static final String LOGIN            = "/users/login";
    public static final String REGISTER         = "/users/register";
    public static final String USERS            = "/users";
    public static final String CHANGE_PASSWORD  = "/users/{id}/changepassword";
    public static final String ASSIGN_SHIFT     = "/users/{id}/shift";

    // ── RBAC ─────────────────────────────────────────────────────────────────
    public static final String ROLES            = "/roles";
    public static final String USER_ROLES       = "/users/{userId}/roles";
    public static final String USER_ROLE_BY_ID  = "/users/{userId}/roles/{roleId}";

    // ── Vendors ──────────────────────────────────────────────────────────────
    public static final String VENDORS          = "/vendors";
    public static final String VENDOR_BY_ID     = "/vendors/{id}";
    public static final String VENDORS_SEARCH   = "/vendors/search";
    public static final String VENDORS_RECOMMENDED = "/vendors/recommended";
    public static final String VENDOR_PERFORMANCE  = "/vendors/{id}/performance";

    // ── Products ─────────────────────────────────────────────────────────────
    public static final String PRODUCTS         = "/products";
    public static final String PRODUCT_BY_ID    = "/products/{id}";
    public static final String PRODUCTS_SEARCH  = "/products/search";
    public static final String PRODUCT_DESIGN   = "/products/{id}/design";

    // ── Raw Materials ─────────────────────────────────────────────────────────
    public static final String RAW_MATERIALS    = "/rawmaterials";
    public static final String RAW_MATERIAL_BY_ID = "/rawmaterials/{id}";
    public static final String RAW_MATERIALS_SEARCH = "/rawmaterials/search";

    // ── Customers ─────────────────────────────────────────────────────────────
    public static final String CUSTOMERS        = "/customer";
    public static final String CUSTOMER_BY_ID   = "/customer/{id}";
    public static final String CUSTOMERS_SEARCH = "/customer/search";

    // ── Sales Orders ──────────────────────────────────────────────────────────
    public static final String SALES_ORDERS     = "/salesorder";
    public static final String SALES_ORDER_BY_ID = "/salesorder/{id}";
    public static final String SALES_ORDER_SUBMIT = "/salesorder/{id}/submit";
    public static final String SALES_ORDER_SEARCH = "/salesorder/search";
    public static final String SALES_ORDER_PRODUCTION_ORDERS = "/salesorder/{id}/production-orders";
    public static final String SALES_ORDER_GEN_PO = "/salesorder/{id}/generate-production-orders";

    // ── Quotations ────────────────────────────────────────────────────────────
    public static final String QUOTATIONS       = "/quotation";
    public static final String QUOTATION_BY_ID  = "/quotation/{id}";
    public static final String QUOTATION_CONVERT = "/quotation/{id}/convert-to-sales-order";

    // ── Invoices ──────────────────────────────────────────────────────────────
    public static final String INVOICES         = "/invoice";
    public static final String INVOICE_BY_ID    = "/invoice/{id}";
    public static final String INVOICE_STATUS   = "/invoice/{id}/status";

    // ── Warehouses ────────────────────────────────────────────────────────────
    public static final String WAREHOUSES       = "/warehouse";
    public static final String WAREHOUSE_BY_ID  = "/warehouse/{id}";
    public static final String WAREHOUSES_SEARCH = "/warehouse/search";

    // ── Batches ───────────────────────────────────────────────────────────────
    public static final String BATCHES          = "/batches";
    public static final String BATCH_BY_ID      = "/batches/{id}";
    public static final String BATCH_DEPOSIT    = "/batches/{id}/deposit";

    // ── Batch Stages ──────────────────────────────────────────────────────────
    public static final String BATCH_STAGES     = "/batchstages";
    public static final String BATCH_STAGE_BY_ID = "/batchstages/{id}";
    public static final String BATCH_STAGE_STATUS = "/batchstages/{id}/status";
    public static final String BATCH_STAGE_QC   = "/batchstages/{id}/qc";
    public static final String BATCH_STAGE_QC_SUBMITTED = "/batchstages/qc/submitted";

    // ── Attendance ────────────────────────────────────────────────────────────
    public static final String ATTENDANCE_CHECKIN          = "/attendances/checkin";
    public static final String ATTENDANCE_CHECKOUT         = "/attendances/checkout";
    public static final String ATTENDANCE_BREAK_START      = "/attendances/break/start";
    public static final String ATTENDANCE_BREAK_END        = "/attendances/break/end";
    public static final String ATTENDANCE_TODAY            = "/attendances/today";
    public static final String ATTENDANCE_HISTORY          = "/attendances/history";
    public static final String ATTENDANCE_SETTINGS         = "/attendances/settings";
    public static final String ATTENDANCE_REGULARIZATION   = "/attendances/regularization";
    public static final String ATTENDANCE_REGULARIZATION_BY_ID = "/attendances/regularization/{id}";
    public static final String ATTENDANCE_REGULARIZATION_APPROVE = "/attendances/regularization/{id}/approve";
    public static final String ATTENDANCE_BY_ID            = "/attendances/{id}";
    public static final String ATTENDANCE_APPROVE          = "/attendances/{id}/approve";
    public static final String ATTENDANCE_ADMIN_EDIT       = "/attendances/{id}/admin-edit";
    public static final String ATTENDANCE_ADMIN_CREATE     = "/attendances/admin-create/{userId}";

    // ── Geofences ─────────────────────────────────────────────────────────────
    public static final String GEOFENCES        = "/geofences";
    public static final String GEOFENCE_BY_ID   = "/geofences/{id}";
    public static final String GEOFENCE_ASSIGN  = "/geofences/{id}/assign";

    // ── Shifts ────────────────────────────────────────────────────────────────
    public static final String SHIFTS           = "/shifts";
    public static final String SHIFT_BY_ID      = "/shifts/{id}";

    // ── Enterprises ───────────────────────────────────────────────────────────
    public static final String ENTERPRISES      = "/enterprises";

    // ── Production Orders ─────────────────────────────────────────────────────
    public static final String PRODUCTION_ORDERS = "/productionorder";
    public static final String PRODUCTION_ORDER_BY_ID = "/productionorder/{id}";

    // ── Delivery Challans ─────────────────────────────────────────────────────
    public static final String DELIVERY_CHALLANS = "/deliverychallan";
    public static final String DELIVERY_CHALLAN_BY_ID = "/deliverychallan/{id}";

    // ── Returnable DC ─────────────────────────────────────────────────────────
    public static final String RETURNABLE_DC    = "/returnable";
    public static final String RETURNABLE_DC_BY_ID = "/returnable/{id}";

    // ── Inventory ─────────────────────────────────────────────────────────────
    public static final String INVENTORY_STOCK  = "/inventory/stock";
    public static final String INVENTORY_RECEIVE = "/inventory/receive";

    // ── Health ────────────────────────────────────────────────────────────────
    public static final String HEALTH           = "/health";

    /** Valid 24-char hex ObjectId that does not exist in DB; use for 404 tests. */
    public static final String NON_EXISTENT_OBJECT_ID = "000000000000000000000001";

    // ── HTTP Headers ─────────────────────────────────────────────────────────
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX        = "Bearer ";
    public static final String CONTENT_TYPE_JSON    = "application/json";

    // ── Response fields ───────────────────────────────────────────────────────
    public static final String FIELD_CODE    = "code";
    public static final String FIELD_MESSAGE = "message";
    public static final String FIELD_DATA    = "data";
    public static final String FIELD_TOKEN   = "token";
    public static final String FIELD_ID      = "_id";
}
