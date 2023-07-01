package com.justransform.api_NEW.api.controllers;

import com.justransform.api_NEW.api.common.ApiConstant;
import com.justransform.api_NEW.api.service.RestResourceService;
import com.justransform.utils.URLConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

@RestController
@Produces(MediaType.APPLICATION_JSON)
@RequestMapping(ApiConstant.JUSTRANSFORM)
@CrossOrigin(origins = {"*"})
public class RestResourceV2 {

    @Qualifier("restResourceServiceImpl")
    @Autowired
    private RestResourceService restResourceService;

    @GetMapping(URLConstant.REST_RECEIVE)
    public ResponseEntity<Object> recieveData(@Context final HttpServletRequest request) {

        String junction = filterJunction(request);
        Response getResponse = restResourceService.getRecieveData(request,junction);
        return ResponseEntity.status(getResponse.getStatus())
                .contentType(org.springframework.http.MediaType.parseMediaType(getResponse.getMediaType() != null ? getResponse.getMediaType().toString() : "application/json"))
                .body(getResponse.getEntity());

    }

    @RequestMapping(value = URLConstant.REST_RECEIVE, method = {RequestMethod.POST,RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<Object> recieveData(@Context final HttpServletRequest request,
                                              @Context final HttpServletResponse response) {

        String junction = filterJunction(request);
        Response postResponse = restResourceService.postRecieveData(request, junction);
        return ResponseEntity.status(postResponse.getStatus())
                .contentType(org.springframework.http.MediaType.parseMediaType(postResponse.getMediaType() != null ? postResponse.getMediaType().toString() : "application/json"))
                .body(postResponse.getEntity());
    }

    @PostMapping(URLConstant.REST_RECEIVE_MULTIMEDIA)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ResponseEntity<Object> receiveMultipartData(@Context final HttpServletRequest request, @RequestParam("file") MultipartFile file,
                                                       @Context final HttpServletResponse response) throws IOException {
        Response postResponse = restResourceService
                .postRecieveMultipartData(request, file, response);
        return ResponseEntity.status(postResponse.getStatus())
                .contentType(org.springframework.http.MediaType.parseMediaType(postResponse.getMediaType() != null ? postResponse.getMediaType().toString() : "application/json"))
                .body(postResponse.getEntity());

    }

    @GetMapping(URLConstant.REST_HEARTBEAT)
    public ResponseEntity<Object> getMonitoringHeartBeat(@QueryParam("component") String componentName) {
        Response getResponse = restResourceService.getMonitoringHeartBeat(componentName);
        return ResponseEntity.status(getResponse.getStatus())
                .contentType(org.springframework.http.MediaType.parseMediaType(getResponse.getMediaType() != null ? getResponse.getMediaType().toString() : "application/json"))
                .body(getResponse.getEntity());
    }

    @GetMapping(URLConstant.REST)
    public ResponseEntity<Object> getServerStatus() {

        Response getResponse = restResourceService.getServerStatus();;
        return ResponseEntity.status(getResponse.getStatus())
                .contentType(org.springframework.http.MediaType.parseMediaType(getResponse.getMediaType() != null ? getResponse.getMediaType().toString() : "application/json"))
                .body(getResponse.getEntity());
    }

    private String filterJunction(HttpServletRequest request) {

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

        return new AntPathMatcher().extractPathWithinPattern(bestMatchPattern, path);
    }

}