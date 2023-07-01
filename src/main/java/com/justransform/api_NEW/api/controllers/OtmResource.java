package com.justransform.api_NEW.api.controllers;


import com.justransform.api_NEW.api.service.OtmResourceService;
import com.justransform.utils.URLConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@RestController
@Produces(MediaType.APPLICATION_JSON)
@RequestMapping(URLConstant.JUSTRANSFORM)
public class OtmResource {

    @Autowired
    private OtmResourceService otmResourceService;

    @GetMapping(URLConstant.OTM_RECEIVE)
    public ResponseEntity<Object> recieveData(@Context final HttpServletRequest request) {
        Response getResponse = otmResourceService.getRecieveData(request);

        return ResponseEntity.status(getResponse.getStatus())
                .contentType(org.springframework.http.MediaType.parseMediaType(getResponse.getMediaType() != null ? getResponse.getMediaType().toString() : "application/json"))
                .body(getResponse.getEntity());


    }

    @PostMapping(URLConstant.OTM_RECEIVE)
    public ResponseEntity<Object> recieveData(@Context final HttpServletRequest request,
                                              @Context final HttpServletResponse response) {
        Response postResponse = otmResourceService.postRecieveData(request);

        return ResponseEntity.status(postResponse.getStatus())
                .contentType(org.springframework.http.MediaType.parseMediaType(postResponse.getMediaType() != null ? postResponse.getMediaType().toString() : "application/json"))
                .body(postResponse.getEntity());
    }

    @GetMapping(URLConstant.OTM)
    public ResponseEntity<Object> getServerStatus() {
        Response getResponse =  otmResourceService.getServerStatus();

        return ResponseEntity.status(getResponse.getStatus())
                .contentType(org.springframework.http.MediaType.parseMediaType(getResponse.getMediaType() != null ? getResponse.getMediaType().toString() : "application/json"))
                .body(getResponse.getEntity());
    }
}
