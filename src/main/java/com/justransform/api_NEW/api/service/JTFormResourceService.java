package com.justransform.api_NEW.api.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.ws.rs.core.Response;

public interface JTFormResourceService {

    Response getRecieveData(HttpServletRequest request);

    Response postRecieveData(HttpServletRequest request, final HttpServletResponse response);

}
