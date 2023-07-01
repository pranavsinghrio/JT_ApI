package com.justransform.api_NEW.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.justransform.entity.enums.ProgramStatus;
import com.justransform.entity.enums.ProgramType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ProgramData {

    @JsonProperty("ID")
    private long programId;

    @JsonProperty("Title")
    private String title;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("Start Date")
    private Date startDate;

    @JsonProperty("End Date")
    private Date endDate;

    @JsonProperty("Program Type")
    private ProgramType type;

    @JsonProperty("Status")
    private ProgramStatus status;

    @JsonProperty("Workflow")
    private Long workflowId;

    @JsonProperty("Logo")
    private String logo;

    @JsonProperty("Documents")
    private List<Document> programDocuments = new ArrayList<>();

    @JsonProperty("Connections")
    private List<ConnectionMock> connectionMocks = new ArrayList<>();

    @JsonProperty("Alert Mail")
    private String alertEmail;

    @JsonProperty("Connection Parent ResourceId")
    private Long connectionResourceId;

    @JsonProperty("Document Parent ResourceId")
    private Long documentResourceId;

    @JsonProperty("User Id")
    private Long userId;

    @JsonProperty("source")
    private String source;

    @JsonProperty("region")
    private String region;

    public String getAlertEmail() {
        return alertEmail;
    }

    public void setAlertEmail(String alertEmail) {
        this.alertEmail = alertEmail;
    }

    public long getProgramId() {
        return programId;
    }

    public void setProgramId(long programId) {
        this.programId = programId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public ProgramType getType() {
        return type;
    }

    public void setType(ProgramType type) {
        this.type = type;
    }

    public ProgramStatus getStatus() {
        return status;
    }

    public void setStatus(ProgramStatus status) {
        this.status = status;
    }

    public Long getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(Long workflowId) {
        this.workflowId = workflowId;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public List<Document> getProgramDocuments() {
        return programDocuments;
    }

    public void setProgramDocuments(List<Document> programDocuments) {
        this.programDocuments = programDocuments;
    }

    public List<ConnectionMock> getConnections() {
        return connectionMocks;
    }

    public void setConnections(List<ConnectionMock> connectionMocks) {
        this.connectionMocks = connectionMocks;
    }

    public Long getConnectionResourceId() {
        return connectionResourceId;
    }

    public void setConnectionResourceId(Long connectionResourceId) {
        this.connectionResourceId = connectionResourceId;
    }

    public Long getDocumentResourceId() {
        return documentResourceId;
    }

    public void setDocumentResourceId(Long documentResourceId) {
        this.documentResourceId = documentResourceId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSource() {
        return source;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
