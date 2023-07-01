package com.justransform.api_NEW.api.controllers;

import com.justransform.api_NEW.api.service.TransactionAPIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RestController
@Produces(MediaType.APPLICATION_JSON)
@RequestMapping(URLConstant.JUSTRANSFORM)
public class TransactionController {

    @Autowired
    TransactionAPIService transactionService;

    @GetMapping("/transaction/{scope}/{id}")
    public ResponseEntity<Object> getInProgressTransactions(@PathVariable String scope, @PathVariable("id") List<Long> id, @QueryParam("status") String status) {
        try {
            if (scope.equals("connection") || scope.equals("program")) {
                return ResponseEntity.ok().body(transactionService.getListOfTransactions(scope, id, status));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("scope is not equal to connection or program");

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}
