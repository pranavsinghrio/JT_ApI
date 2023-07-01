package com.justransform.api_NEW.api.controllers;


import com.justransform.api_NEW.api.service.As2ResourceService;
import com.justransform.utils.URLConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@RestController
@Produces(MediaType.APPLICATION_JSON)
@RequestMapping(URLConstant.JUSTRANSFORM)
public class As2Resource {

    @Autowired
    private As2ResourceService as2ResourceService;

    @GetMapping(URLConstant.AS2)
    public ResponseEntity<Object> getServerStatus() {
        Response getResponse =  as2ResourceService.getServerStatus();
        return ResponseEntity.status(getResponse.getStatus())
                .contentType(org.springframework.http.MediaType.parseMediaType(getResponse.getMediaType() != null ? getResponse.getMediaType().toString() : "application/json"))
                .body(getResponse.getEntity());
    }

    @PostMapping(URLConstant.AS2_RECEIVE)
    public ResponseEntity<Object> recieveData(@Context final HttpServletRequest request,
                                              @Context final HttpServletResponse response) {
        Response postResponse = as2ResourceService.postReceiveData(request, response);

        return ResponseEntity.status(postResponse.getStatus())
                .contentType(org.springframework.http.MediaType.parseMediaType(postResponse.getMediaType() != null ? postResponse.getMediaType().toString() : "application/json"))
                .body(postResponse.getEntity());

    }

}
