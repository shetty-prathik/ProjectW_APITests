package com.projectw.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * POJO matching the Vendor Mongoose schema in Project_W_BE.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VendorModel {

    @JsonProperty("_id")
    private String id;

    private String eid;
    private String code;
    private String name;
    private String contactPerson;
    private String email;
    private String phone;
    private String address;
    private String gstin;
    private List<String> specializations;
    private boolean isActive;
    private String paymentTerms;
    private Integer creditDays;
    private Double rating;
    private String notes;

    @JsonProperty("performance_metrics")
    private Map<String, Object> performanceMetrics;

    private String createdAt;
    private String updatedAt;

    public VendorModel() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEid() { return eid; }
    public void setEid(String eid) { this.eid = eid; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public List<String> getSpecializations() { return specializations; }
    public void setSpecializations(List<String> specializations) { this.specializations = specializations; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }

    public Integer getCreditDays() { return creditDays; }
    public void setCreditDays(Integer creditDays) { this.creditDays = creditDays; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Map<String, Object> getPerformanceMetrics() { return performanceMetrics; }
    public void setPerformanceMetrics(Map<String, Object> performanceMetrics) { this.performanceMetrics = performanceMetrics; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "VendorModel{id='" + id + "', eid='" + eid + "', code='" + code + "', name='" + name + "'}";
    }
}
