package com.justransform.api_NEW.api.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.ws.rs.core.Response;

public interface OraclecmkV2ResourceService {

    Response recieveData(HttpServletRequest request, HttpServletResponse response);

    Response getServerStatus();

    Response getWsdl(final HttpServletRequest request, String wsdl);

    Response postRecieveData(final HttpServletRequest request);
}
