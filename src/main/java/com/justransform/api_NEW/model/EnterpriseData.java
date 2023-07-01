package com.justransform.api_NEW.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.justransform.entity.*;
import com.justransform.entity.enums.EmployeeCountEnum;
import com.justransform.entity.enums.IndustryType;
import jakarta.persistence.OneToOne;

import java.beans.Transient;
import java.util.ArrayList;
import java.util.List;

public class EnterpriseData extends Enterprise {

    @JsonProperty("enterpriseId")
    private long enterpriseId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("users")
    private List<User> users = new ArrayList<User>();

    @JsonProperty("privateResource")
    private Resource privateResource;

    @JsonProperty("tenantVo")
    private Tenant tenant;

    @JsonProperty("jtEnterprise")
    private boolean jtEnterprise;

    @JsonProperty("publicResource")
    private Resource publicResource;

    @JsonProperty("domain")
    private String domain;

    @OneToOne
    @JsonProperty("rootUser")
    private User rootUser;

    @JsonProperty("contacts")
    private List<com.justransform.entity.EnterpriseContact> contact;

    @JsonProperty("addresses")
    private List<EnterpriseAddress> addresses;

    @JsonProperty("website")
    private String website;

    @JsonProperty("foundedYear")
    private String founded;

    @JsonProperty("employeeCount")
    private EmployeeCountEnum employeeCount;

    @JsonProperty("description")
    private String description;

    @JsonProperty("industryType")
    private IndustryType industryType;

    @JsonProperty("logoPath")
    private String logoFilePath;

    @JsonProperty("b2bEnterpriseProfile")
    private List<B2BEnterpriseProfile> b2bEnterpriseProfiles;


    public List<EnterpriseContact> getContact() {
        return contact;
    }

    public void setContact(List<EnterpriseContact> contact) {
        this.contact = contact;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getFounded() {
        return founded;
    }

    public void setFounded(String founded) {
        this.founded = founded;
    }

    public EmployeeCountEnum getEmployeeCount() {
        return employeeCount;
    }

    public void setEmployeeCount(EmployeeCountEnum employeeCount) {
        this.employeeCount = employeeCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public IndustryType getIndustryType() {
        return industryType;
    }

    public void setIndustryType(IndustryType industryType) {
        this.industryType = industryType;
    }

    public String getLogoFilePath() {
        return logoFilePath;
    }

    public void setLogoFilePath(String logoFilePath) {
        this.logoFilePath = logoFilePath;
    }

    public long getEnterpriseId() {
        return enterpriseId;
    }

    public void setEnterpriseId(long enterpriseId) {
        this.enterpriseId = enterpriseId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    public Resource getPrivateResource() {
        return privateResource;
    }

    public void setPrivateResource(Resource privateResource) {
        this.privateResource = privateResource;
    }

    public Resource getPublicResource() {
        return publicResource;
    }

    public void setPublicResource(Resource publicResource) {
        this.publicResource = publicResource;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    @Override
    public boolean isJtEnterprise() {
        return jtEnterprise;
    }

    public void setJtEnterprise(boolean jtEnterprise) {
        this.jtEnterprise = jtEnterprise;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public User getRootUser() {
        return rootUser;
    }

    public void setRootUser(User rootUser) {
        this.rootUser = rootUser;
    }

    @Transient
    public boolean isTenant() {
        if (tenant == null)
            return false;
        return true;
    }

    public List<B2BEnterpriseProfile> getB2bEnterpriseProfiles() {
        return b2bEnterpriseProfiles;
    }

    public void setB2bEnterpriseProfiles(
            List<B2BEnterpriseProfile> b2bEnterpriseProfiles) {
        this.b2bEnterpriseProfiles = b2bEnterpriseProfiles;
    }

    public List<EnterpriseAddress> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<EnterpriseAddress> addresses) {
        this.addresses = addresses;
    }

    @Override
    public String toString() {
        return domain;
    }

    @Override
    public boolean equals(Object anObject) {
        if (!(anObject instanceof Enterprise)) {
            return false;
        }
        Enterprise otherMember = (Enterprise) anObject;
        return otherMember.getDomain().equals(getDomain());
    }

}
