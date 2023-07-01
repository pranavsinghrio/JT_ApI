package com.justransform.api_NEW.api.controllers;

import com.justransform.utils.URLConstant;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@RestController
@Produces(MediaType.APPLICATION_JSON)
@RequestMapping(URLConstant.JUSTRANSFORM)
public class JTFormResource {

}
