package com.justransform.api_NEW.api.controllers;

import com.justransform.api_NEW.api.service.SoapServerResourceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.ws.rs.Consumes;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@RestController
@Consumes(MediaType.TEXT_XML)
@RequestMapping(URLConstant.JUSTRANSFORM)
public class SoapserverResource {

    @Autowired
    private SoapServerResourceService soapServerResourceService;

    @GetMapping(URLConstant.SOAP_SERVER_RECEIVE)
    public ResponseEntity<Object> getWsdl(@PathVariable("junction") String junction, @QueryParam("wsdl") String wsdl) {
        Response getResponse = soapServerResourceService.getWsdl(junction);
        return ResponseEntity.status(getResponse.getStatus())
                .contentType(org.springframework.http.MediaType.parseMediaType(getResponse.getMediaType() != null ? getResponse.getMediaType().toString() : "application/json"))
                .body(getResponse.getEntity());
    }

    @PostMapping(URLConstant.SOAP_SERVER_RECEIVE_OLD_JUNCTION)
    ResponseEntity<Object> postData(@PathVariable("junction") String junction, @Context final HttpServletRequest request) {

        Response postResponse = soapServerResourceService.postData(junction, request);
        return ResponseEntity.status(postResponse.getStatus())
                .contentType(org.springframework.http.MediaType.parseMediaType(postResponse.getMediaType() != null ? postResponse.getMediaType().toString() : "application/json"))
                .body(postResponse.getEntity());
    }

    @PostMapping(URLConstant.SOAP_SERVER_RECEIVE_JUNCTION)
    ResponseEntity<Object> postSoapData(@PathVariable("junction") String junction, @Context final HttpServletRequest request) {
        Response postResponse = soapServerResourceService.postSoapData(junction, request);
        return ResponseEntity.status(postResponse.getStatus())
                .contentType(org.springframework.http.MediaType.parseMediaType(postResponse.getMediaType() != null ? postResponse.getMediaType().toString() : "application/json"))
                .body(postResponse.getEntity());
    }

    @GetMapping(URLConstant.IS_SOAP_SERVER_JUNCTION_EXISTS)
    public ResponseEntity<Object> isJusnctionExists(@QueryParam("junction") String junction) {
        Response getResponse = soapServerResourceService.isJusnctionExists(junction);
        return ResponseEntity.status(getResponse.getStatus())
                .contentType(org.springframework.http.MediaType.parseMediaType(getResponse.getMediaType() != null ? getResponse.getMediaType().toString() : "application/json"))
                .body(getResponse.getEntity());
    }

    @GetMapping(URLConstant.SOAPSERVER)
    public ResponseEntity<Object> getServerStatus() {
        Response getResponse = soapServerResourceService.getServerStatus();
        return ResponseEntity.status(getResponse.getStatus())
                .contentType(org.springframework.http.MediaType.parseMediaType(getResponse.getMediaType() != null ? getResponse.getMediaType().toString() : "application/json"))
                .body(getResponse.getEntity());
    }

/*	@GetMapping(URLConstant.SOAPSERVER)
	public Response recieveData(HttpServletRequest request, HttpServletResponse response) {
		return soapServerResourceService.recieveData(request, response);
	}*/
}
