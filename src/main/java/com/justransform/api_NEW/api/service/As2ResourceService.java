package com.justransform.api_NEW.api.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.Response;

public interface As2ResourceService {

    org.apache.catalina.connector.Response getServerStatus();

    Response postReceiveData(final HttpServletRequest request, final HttpServletResponse response);

}
