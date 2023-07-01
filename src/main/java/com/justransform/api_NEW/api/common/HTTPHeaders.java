package com.justransform.api_NEW.api.common;


import java.util.HashMap;

public class HTTPHeaders {

    public HTTPHeaders() {}

    private HashMap<String, String> headerMap;

    public HashMap<String, String> getHeaderMap() {
        return headerMap;
    }

    public void setHeaderMap(HashMap<String, String> headerMap) {
        this.headerMap = headerMap;
    }

    public String getHeaderString() {
        StringBuilder headerStringBuilder = new StringBuilder();
        headerMap.keySet().forEach(key -> headerStringBuilder.append(key).append(": ").append(headerMap.get(key)).append("\n"));
        return headerStringBuilder.toString();
    }
}
