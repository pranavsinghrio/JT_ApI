package com.justransform.api_NEW.api.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@Produces(MediaType.APPLICATION_JSON)
@RequestMapping(URLConstant.JUSTRANSFORM)
public class ProgramController {

    @Autowired
    private ProgramServiceImpl programServiceImpl;

    @Autowired
    private UserUtilityService userUtilityService;


    @GetMapping("/program/{program_id}")
    public ResponseEntity<Object> getProgram(@PathVariable("program_id") long programId) throws JTServerException {
        Map<String, Object> userAttributes = userUtilityService.getUserAttributes();
        try {
            Map<String, Object> programJson = programServiceImpl.getProgramJson(programId);

            if (userAttributes.get("email") == null || !(userAttributes.get("email").equals(programJson.get("creator")) || userAttributes.get("email").equals("admin@justransform.com"))) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            if (programJson == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok(programJson);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }

    }

    @PostMapping("/program/save")
    public ResponseEntity<String> saveProgram(@RequestBody ProgramData programJson) throws Exception {
        try {
            String status = programServiceImpl.saveProgram(programJson);
            if (status == "program created successfully") {
                return ResponseEntity.ok(status);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(status);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/program/{program_id}/connections")
    public ResponseEntity<Object> getProgramConnections(@PathVariable("program_id") List<Long> programId) throws JTServerException {
        try {
            Set<Long> programsSharedConnectionIds = programServiceImpl.getSharedConnectionIdsByProgramId(programId);

            if (programsSharedConnectionIds == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok(programsSharedConnectionIds);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

}
