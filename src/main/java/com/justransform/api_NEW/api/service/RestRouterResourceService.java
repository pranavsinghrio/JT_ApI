package com.justransform.api_NEW.api.service;

import jakarta.servlet.http.HttpServletRequest;

import javax.ws.rs.core.Response;

public interface RestRouterResourceService {

    Response processReceivedData(HttpServletRequest request, String junction);

}
