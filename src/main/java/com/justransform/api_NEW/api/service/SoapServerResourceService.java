package com.justransform.api_NEW.api.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.ws.rs.core.Response;

public interface SoapServerResourceService {

    Response getWsdl(String junction);

    Response isJusnctionExists(String junction);

    Response recieveData(HttpServletRequest request, HttpServletResponse response);

    Response getServerStatus();

    Response postData(String junction,final HttpServletRequest request);

    Response postSoapData(String junction, final HttpServletRequest request);
}
