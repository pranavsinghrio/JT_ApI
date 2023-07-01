package com.justransform.api_NEW.api.controllers;


import com.justransform.api_NEW.api.service.UserUtilityService;
import com.justransform.api_NEW.api.service.impl.EnterpriseServiceImpl;
import com.justransform.api_NEW.model.EnterpriseData;
import com.justransform.common.vo.EnterpriseVo;
import com.justransform.entity.Enterprise;
import com.justransform.services.EnterpriseService;
import com.justransform.utils.URLConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;
import java.util.Set;

@RestController
@Produces(MediaType.APPLICATION_JSON)
@RequestMapping(URLConstant.JUSTRANSFORM)
public class EnterpriseController {

    @Autowired
    EnterpriseServiceImpl enterpriseServiceImpl;

    @Autowired
    EnterpriseService enterpriseService;

    @Autowired
    UserUtilityService userUtilityService;


    @GetMapping("/enterprise/{id}")
    public ResponseEntity<Object> getEnterprise(@PathVariable("id") Long enterpriseId) {

        boolean idFlag = userUtilityService.validateById(enterpriseId);
        if (idFlag) {
            try {
                Enterprise enterprise = enterpriseService.getEnterprise(enterpriseId);
                if (enterprise == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Enterprise not found");
                }
                EnterpriseVo enterpriseVo = enterpriseServiceImpl.getEnterprise(enterpriseId);
                return ResponseEntity.ok(enterpriseVo);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

    }

    @GetMapping("/enterprise/domain/{domain_name}")
    public ResponseEntity<Object> getEnterpriseByDomain(@PathVariable("domain_name") String domain) {
        boolean domainFlag = userUtilityService.validateByDomain(domain);
        if (domainFlag) {
            try {
                Enterprise enterprise = enterpriseService.getEnterpriseByDomain(domain);
                if (enterprise == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Enterprise not found");
                }
                EnterpriseVo enterpriseVo = enterpriseServiceImpl.getEnterpriseByDomain(domain);
                return ResponseEntity.ok(enterpriseVo);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/enterprise")
    public ResponseEntity<Object> saveEnterprise(@RequestBody EnterpriseData enterprise) {
        Map<String, Object> userAttributes = userUtilityService.getUserAttributes();
        if (userAttributes.get("name") != null && userAttributes.get("name").equals("justransform.com")) {
            try {
                Enterprise createdEnterprise = enterpriseServiceImpl.saveEnterprise(enterprise);
                return ResponseEntity.ok(createdEnterprise);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unable to save enterprise");
            }
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PutMapping("/enterprise/{id}")
    public ResponseEntity<Object> updateEnterprise(@RequestBody EnterpriseData enterpriseData, @PathVariable("id") long enterpriseId) {
        boolean idFlag = userUtilityService.validateById(enterpriseId);
        if (idFlag) {
            try {
                Enterprise enterprise = enterpriseService.getEnterprise(enterpriseId);
                if (enterprise == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Enterprise not found");
                }
                String status = enterpriseServiceImpl.updateEnterprise(enterpriseData);
                return ResponseEntity.status(HttpStatus.OK).body(status);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to update enterprise");
            }
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @DeleteMapping("/enterprise/{id}")
    public ResponseEntity<String> delete(@RequestBody EnterpriseData enterpriseData, @PathVariable("id") long enterpriseId) {
        boolean idFlag = userUtilityService.validateById(enterpriseId);
        if (idFlag) {
            try {
                Enterprise enterprise = enterpriseService.getEnterprise(enterpriseId);
                if (enterprise == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Enterprise not found");
                }
                String status = enterpriseServiceImpl.deleteEnterprise(enterpriseData);
                return ResponseEntity.ok(status);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Enterprise deletion failed");
            }
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

    }

    @GetMapping("/enterprise/{domain}/partners/list")
    public ResponseEntity getTradingPartners(@PathVariable("domain") String enterpriseDomain) throws JTServerException {
        boolean domainFlag = userUtilityService.validateByDomain(enterpriseDomain);
        if (domainFlag) {
            Set<String> tradingPartners = enterpriseServiceImpl.getTradingPartners(enterpriseDomain);
            if (tradingPartners.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No partners found for " + enterpriseDomain);
            }
            return ResponseEntity.ok(tradingPartners);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
