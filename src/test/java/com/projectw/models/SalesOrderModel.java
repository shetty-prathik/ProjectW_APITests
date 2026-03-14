package com.projectw.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * POJO matching the SalesOrder Mongoose schema in Project_W_BE.
 * Status lifecycle: draft → submitted → pending → in_progress → completed
 *                   → production_completed → dispatched → closed
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesOrderModel {

    @JsonProperty("_id")
    private String id;

    private String eid;
    private String code;

    @JsonProperty("customer_id")
    private String customerId;

    private String type;
    private String status;

    @JsonProperty("sale_order_date")
    private String saleOrderDate;

    @JsonProperty("delivery_date")
    private String deliveryDate;

    @JsonProperty("purchase_order_no")
    private String purchaseOrderNo;

    @JsonProperty("project_name")
    private String projectName;

    private List<Map<String, Object>> items;

    @JsonProperty("total_quantity")
    private Integer totalQuantity;

    @JsonProperty("total_amount")
    private Double totalAmount;

    @JsonProperty("tax_percentage")
    private Double taxPercentage;

    private String createdAt;
    private String updatedAt;

    public SalesOrderModel() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEid() { return eid; }
    public void setEid(String eid) { this.eid = eid; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSaleOrderDate() { return saleOrderDate; }
    public void setSaleOrderDate(String saleOrderDate) { this.saleOrderDate = saleOrderDate; }

    public String getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(String deliveryDate) { this.deliveryDate = deliveryDate; }

    public String getPurchaseOrderNo() { return purchaseOrderNo; }
    public void setPurchaseOrderNo(String purchaseOrderNo) { this.purchaseOrderNo = purchaseOrderNo; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public List<Map<String, Object>> getItems() { return items; }
    public void setItems(List<Map<String, Object>> items) { this.items = items; }

    public Integer getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public Double getTaxPercentage() { return taxPercentage; }
    public void setTaxPercentage(Double taxPercentage) { this.taxPercentage = taxPercentage; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "SalesOrderModel{id='" + id + "', eid='" + eid + "', code='" + code + "', status='" + status + "'}";
    }
}
