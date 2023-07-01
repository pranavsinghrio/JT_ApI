package com.justransform.api_NEW.api.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/lookup/v1")
public class LookupTableController {

    private static final Logger LOGGER = LoggerFactory.getLogger(LookupTableController.class);

    private LookupTableService lookupTableService;
    private JTKeyValueService keyValueService;

    @Autowired
    private LookupTableController(LookupTableService lookupTableService, JTKeyValueService keyValueService) {
        this.lookupTableService = lookupTableService;
        this.keyValueService = keyValueService;
    }

    @GetMapping(path = "/", produces = "application/json")
    public ResponseEntity<String> getTable(@RequestHeader("lookup-table-token") String token) {
        try {
            String data = LookupTableEncoder.decode(token);
            if (StringUtils.isNotEmpty(data)) {
                String[] splits = data.split("\\|");

                String response = lookupTableService.getTableValue(splits[1], splits[0]);

                if (response != null) {
                    return ResponseEntity.ok(response);
                } else {
                    return ResponseEntity.notFound().build();
                }
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping(path = "/{key}", produces = "application/json")
    public ResponseEntity<String> getTable(@RequestHeader("lookup-table-token") String token, @PathVariable("key") String key) throws Exception {
        try {
            String data = LookupTableEncoder.decode(token);
            if (StringUtils.isNotEmpty(data)) {
                String[] splits = data.split("\\|");
                String response = lookupTableService.getLookupValue(splits[1], splits[0], key);

                if (response != null) {
                    return ResponseEntity.ok(response);
                } else {
                    return ResponseEntity.notFound().build();
                }
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PutMapping(path = "/")
    public ResponseEntity<Void> putTableValue(@RequestHeader("lookup-table-token") String token, @RequestBody Map<String, Object> body) {
        try {
            String data = LookupTableEncoder.decode(token);
            if (StringUtils.isNotEmpty(data)) {
                String[] splits = data.split("\\|");
                lookupTableService.putLookupValue(splits[1], splits[0], body);
                return ResponseEntity.status(HttpStatus.OK).build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping(path = "/update")
    public ResponseEntity<Void> putTableValue(@RequestHeader("lookup-table-token") String token, @RequestParam String key, @RequestParam String col, @RequestParam String value) {
        try {
            String data = LookupTableEncoder.decode(token);
            if (StringUtils.isNotEmpty(data)) {
                String[] splits = data.split("\\|");
                lookupTableService.putValue(splits[1], splits[0], key, col, value);
                return ResponseEntity.status(HttpStatus.OK).build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping(path = "/{key}")
    public ResponseEntity<String> deleteTableValue(@RequestHeader("lookup-table-token") String token, @PathVariable("key") String key) throws Exception {
        try {
            String data = LookupTableEncoder.decode(token);
            if (StringUtils.isNotEmpty(data)) {
                String[] splits = data.split("\\|");
                lookupTableService.deleteLookupValue(splits[1], splits[0], key);
                return ResponseEntity.status(HttpStatus.OK).build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @DeleteMapping(path = "/truncate")
    public ResponseEntity<Void> truncateTable(@RequestHeader("lookup-table-token") String token) {
        try {
            String data = LookupTableEncoder.decode(token);
            if (StringUtils.isNotEmpty(data)) {
                String[] splits = data.split("\\|");
                lookupTableService.truncateLookupTable(splits[1], splits[0]);
                return ResponseEntity.status(HttpStatus.OK).build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(path = "/")
    public ResponseEntity<Void> setLookupTable(@RequestHeader("lookup-table-token") String token, @RequestBody Map<String, Object> body) {
        try {
            Gson gson = new Gson();
            String data = LookupTableEncoder.decode(token);
            if (StringUtils.isNotEmpty(data)) {
                String[] splits = data.split("\\|");
                lookupTableService.setLookupValue(splits[1], splits[0], gson.toJson(body));
                return ResponseEntity.status(HttpStatus.OK).build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping(path = "/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> downloadTable(@RequestHeader("lookup-table-token") String token) {
        try {
            String data = LookupTableEncoder.decode(token);
            if (StringUtils.isNotEmpty(data)) {
                String[] splits = data.split("\\|");
                String csv = lookupTableService.downloadLookupValue(splits[1], splits[0]);
                if (csv != null) {
                    byte[] fileBytes = csv.getBytes();
                    return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + splits[0] + ".csv\"")
                            .body(fileBytes);
                } else {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                }
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(path = "/migrate")
    public ResponseEntity<Void> migrate() {
        try {
            List<JTLookupTable> tables = lookupTableService.getAllTables();
            for(JTLookupTable table : tables) {
                if(StringUtils.isNotEmpty(table.getTableContents())) {
                    JsonFlattener flattener = new JsonFlattener(table.getTableContents()).withFlattenMode(
                            FlattenMode.KEEP_ARRAYS);
                    Map<String, Object> flattenJson = flattener.flattenAsMap();

                    Gson gson = new Gson();
                    List<JTKeyValue> values = lookupTableService.extractValues(table,
                            table.getTableContents());
                    keyValueService.deleteByTableId(table.getId());
                    keyValueService.saveAll(values);
                }
            }

            return ResponseEntity.status(HttpStatus.OK).build();
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
