package com.justransform.api_NEW.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class S3ResponseData {

    private Map<String, String> data;
    private String url;
    public Map<String, String> getData() {
        return data;
    }
    public void setData(Map<String, String> data) {
        this.data = data;
    }
    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }


}
