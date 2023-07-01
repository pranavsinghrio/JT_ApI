package com.justransform.api_NEW.api.controllers;

import com.justransform.api_NEW.api.service.RestRouterResourceService;
import com.justransform.utils.URLConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.view.RedirectView;

import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@RestController
@CrossOrigin(origins = {"*"})
@Produces(MediaType.APPLICATION_JSON)
@RequestMapping(URLConstant.JUSTRANSFORM)
public class RestRouterRedirectResource {


    @Autowired
    private RestRouterResourceService restRedirectResouceService;

    @RequestMapping(value = URLConstant.REST_RECEIVE_REDIRECT, method = {RequestMethod.POST, RequestMethod.GET})
    public RedirectView getRestRouterRedirect(@Context final HttpServletRequest request) {
        String junction = filterJunction(request);
        Response getResponse = restRedirectResouceService.processReceivedData(request, junction);
        return new RedirectView((String) getResponse.getEntity());
    }

    private String filterJunction(HttpServletRequest request) {

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

        return new AntPathMatcher().extractPathWithinPattern(bestMatchPattern, path);
    }

}
