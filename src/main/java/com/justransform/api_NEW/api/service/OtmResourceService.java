package com.justransform.api_NEW.api.service;

import jakarta.servlet.http.HttpServletRequest;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

public interface OtmResourceService {

    public Response getServerStatus();

    public Response getRecieveData(@Context final HttpServletRequest request);

    public Response postRecieveData(@Context final HttpServletRequest request);

}
