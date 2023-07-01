package com.justransform.api_NEW.api.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.core.Context;
import java.io.IOException;

@RestController
@RequestMapping(URLConstant.JUSTRANSFORM_APP)
public class JustransformAppResource {

    @GetMapping()
    public void forwardToAppsURL(@Context final HttpServletRequest request, @Context final HttpServletResponse response){
        try {
            response.sendRedirect(PropertyReaderUtil.getInstance().getPropertyValue("apps.base.url"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}