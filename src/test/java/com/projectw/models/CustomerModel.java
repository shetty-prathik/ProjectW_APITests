package com.projectw.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * POJO matching the Customer Mongoose schema in Project_W_BE.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerModel {

    @JsonProperty("_id")
    private String id;

    private String eid;
    private String code;

    @JsonProperty("customer_type")
    private String customerType;

    private String name;

    @JsonProperty("vendor_code")
    private String vendorCode;

    @JsonProperty("billing_address")
    private String billingAddress;

    @JsonProperty("shipping_address")
    private String shippingAddress;

    @JsonProperty("gst_number")
    private String gstNumber;

    @JsonProperty("tax_type")
    private String taxType;

    @JsonProperty("payment_terms")
    private String paymentTerms;

    @JsonProperty("contact_details")
    private List<Map<String, Object>> contactDetails;

    @JsonProperty("bank_details")
    private List<Map<String, Object>> bankDetails;

    private String createdAt;
    private String updatedAt;

    public CustomerModel() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEid() { return eid; }
    public void setEid(String eid) { this.eid = eid; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getCustomerType() { return customerType; }
    public void setCustomerType(String customerType) { this.customerType = customerType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVendorCode() { return vendorCode; }
    public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }

    public String getBillingAddress() { return billingAddress; }
    public void setBillingAddress(String billingAddress) { this.billingAddress = billingAddress; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public String getGstNumber() { return gstNumber; }
    public void setGstNumber(String gstNumber) { this.gstNumber = gstNumber; }

    public String getTaxType() { return taxType; }
    public void setTaxType(String taxType) { this.taxType = taxType; }

    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }

    public List<Map<String, Object>> getContactDetails() { return contactDetails; }
    public void setContactDetails(List<Map<String, Object>> contactDetails) { this.contactDetails = contactDetails; }

    public List<Map<String, Object>> getBankDetails() { return bankDetails; }
    public void setBankDetails(List<Map<String, Object>> bankDetails) { this.bankDetails = bankDetails; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "CustomerModel{id='" + id + "', eid='" + eid + "', code='" + code + "', name='" + name + "'}";
    }
}
