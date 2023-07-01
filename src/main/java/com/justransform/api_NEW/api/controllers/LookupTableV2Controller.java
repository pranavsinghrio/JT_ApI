package com.justransform.api_NEW.api.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Profile("mongodb")
@RequestMapping("api/lookup/v2")
public class LookupTableV2Controller {
    private static final Logger LOGGER = LoggerFactory.getLogger(LookupTableV2Controller.class);

    private JTLookupTableContentService service;

    @Autowired
    private LookupTableV2Controller(JTLookupTableContentService service) {
        this.service = service;
    }

    @GetMapping(path = "/", produces = "application/json")
    public ResponseEntity<String> getTable(@RequestHeader("lookup-table-token") String token) {
        try {
            String response = service.getContentByToken(token);

            if (response != null) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping(path = "/{key}", produces = "application/json")
    public ResponseEntity<String> getTable(@RequestHeader("lookup-table-token") String token, @PathVariable("key") String key) throws Exception {
        try {
            String response = service.getValueByToken(token, key);

            if (response != null) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PutMapping(path = "/")
    public ResponseEntity<Void> putTableValue(@RequestHeader("lookup-table-token") String token, @RequestBody Map<String, String> body) {
        try {
            for(String key : body.keySet()) {
                service.putValueByToken(token, key, body.get(key));
            }
            return ResponseEntity.status(HttpStatus.OK).build();
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping(path = "/{key}")
    public ResponseEntity<String> deleteTableValue(
            @RequestHeader("lookup-table-token") String token,
            @RequestParam("key") String key) throws Exception {
        try {
            service.deleteKeyByToken(token, key);
            return ResponseEntity.status(HttpStatus.OK).build();
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @DeleteMapping(path = "/truncate")
    public ResponseEntity<Void> truncateTable(@RequestHeader("lookup-table-token") String token) {
        try {
            service.truncateByToken(token);
            return ResponseEntity.status(HttpStatus.OK).build();
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping(path = "/search/{token}")
    public ResponseEntity<?> getLookupTableKeys(@PathVariable("token") String token,
                                                @RequestBody Map<String, String> lookupFilterMap) {
        try {
            String response = service.getLookupTableByTokenAndFilter(token, lookupFilterMap);

            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            LOGGER.error("Failure when trying to query the requested look-up-table: " + e.getMessage(), e);

            if (e.getMessage().equals(LookupTableConstants.LOOK_UP_TABLE_NOT_FOUND_EXCEPTION_MSG))
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(LookupTableConstants.LOOK_UP_TABLE_NOT_FOUND_EXCEPTION_MSG);

            if(e.getMessage().equals(LookupTableConstants.INVALID_FILTER_EXCEPTION_MSG))
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LookupTableConstants.INVALID_FILTER_EXCEPTION_MSG);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
