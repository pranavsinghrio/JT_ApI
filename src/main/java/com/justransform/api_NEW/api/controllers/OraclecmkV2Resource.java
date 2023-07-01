package com.justransform.api_NEW.api.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.connector.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@RestController
@Produces(MediaType.TEXT_XML)
@RequestMapping(URLConstant.JUSTRANSFORM)
class OraclecmkV2Resource {

    @Autowired
    OraclecmkV2ResourceService oraclecmkV2ResourceService;


    @GetMapping(URLConstant.ORACLE_CMK_V2)
    ResponseEntity<Object> getServerStatus() {
        Response getResponse = oraclecmkV2ResourceService.getServerStatus();

        return ResponseEntity.status(getResponse.getStatus())
                .contentType(org.springframework.http.MediaType.APPLICATION_XML)
                .body(getResponse.getEntity());
    }

    @GetMapping(URLConstant.ORACLE_cmk_V2_GET_WSDL)
    ResponseEntity<Object> getWsdl(HttpServletRequest request, @QueryParam("wsdl") String wsdl) {
        Response getResponse = oraclecmkV2ResourceService.getWsdl(request, wsdl);

        return ResponseEntity.status(getResponse.getStatus())
                .contentType(org.springframework.http.MediaType.parseMediaType(getResponse.getMediaType() != null ? getResponse.getMediaType().toString() : "text/xml"))
                .body(getResponse.getEntity());
    }

    @PostMapping(URLConstant.ORACLE_CMK_V2)
    ResponseEntity<Object> postSoapData(@Context final HttpServletRequest request) {
        Response postResponse = oraclecmkV2ResourceService.postRecieveData(request);
        return ResponseEntity.status(postResponse.getStatus())
                .contentType(org.springframework.http.MediaType.parseMediaType(postResponse.getMediaType() != null ? postResponse.getMediaType().toString() : "text/xml"))
                .body(postResponse.getEntity());
    }

    /*@GetMapping(URLConstant.ORACLE_CMK_V2)
    ResponseEntity<Object> recieveData(HttpServletRequest request, HttpServletResponse response){
        return ResponseEntity.ok(oraclecmkV2ResourceService.recieveData(request,response).getEntity());
    }*/
}