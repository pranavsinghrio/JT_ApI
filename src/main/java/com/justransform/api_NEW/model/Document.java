package com.justransform.api_NEW.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.justransform.common.vo.ResourceVo;
import com.justransform.entity.AccessType;

public class Document {

    @JsonProperty("programVo")
    private String program;

    @JsonProperty("document")
    private ResourceVo documentData;

    @JsonProperty("accessLevel")
    private AccessType accessLevel;

    public String getProgram() {
        return program;
    }

    public void setProgram(String program) {
        this.program = program;
    }

    public ResourceVo getDocumentData() {
        return documentData;
    }

    public void setDocumentData(ResourceVo documentData) {
        this.documentData = documentData;
    }

    public AccessType getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(AccessType accessLevel) {
        this.accessLevel = accessLevel;
    }
}
