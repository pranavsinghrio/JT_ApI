package com.justransform.api_NEW.api.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import javax.ws.rs.core.Response;

public interface RestResourceService {

    Response getRecieveData(HttpServletRequest request, String Junction);

    Response getMonitoringHeartBeat(String componentName);

    Response postRecieveData(HttpServletRequest request, String Junction);

    Response postRecieveMultipartData(HttpServletRequest request, MultipartFile uploadedInputStream,
                                      HttpServletResponse response);

    Response getServerStatus();
}
