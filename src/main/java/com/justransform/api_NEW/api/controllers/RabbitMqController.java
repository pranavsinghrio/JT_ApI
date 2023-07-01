package com.justransform.api_NEW.api.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.QueryParam;

@RestController
public class RabbitMqController {

    @Autowired
    private RabbitMQService rabbitMQService;

    @PostMapping(URLConstant.STOPRABBITMQFORCONTAINER)
    public ResponseEntity<String> stopRabbitMQForConsumer(@PathVariable("container_name") String containerName) {
        rabbitMQService.sendQueueMessage(null, null, containerName, Operations.STOP_CONSUMER);
        return ResponseEntity.status(HttpStatus.OK).body("Request sent to fan-out handler to stop the consumers for container " + containerName);
    }

    @PostMapping(URLConstant.STOPRABBITMQCONSUMERFORCONTAINERANDENTERPRISES)
    public ResponseEntity<String> stopRabbitMqConsumerForContainerAndEnterprise(@QueryParam("enterpriseId") String enterpriseId, @PathVariable("container_name") String containerName) {
        if (enterpriseId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("EnterpriseId cannot be null");
        }
        rabbitMQService.sendQueueMessage(null, enterpriseId, containerName, Operations.STOP_CONSUMER);
        return ResponseEntity.status(HttpStatus.OK).body("Request sent to fan-out handler to stop the consumers for enterprises " + enterpriseId + " and container " + containerName);
    }

    @PostMapping(URLConstant.STARTRABBITMQCONSUMERSFORENTERPRISES)
    public ResponseEntity<String> startRabbitMqConsumerForEnterprises(@QueryParam("enterpriseId") String enterpriseId, @PathVariable("container_name") String containerName) {
        if (enterpriseId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("enterpriseId cannot be null ");
        }
        rabbitMQService.sendQueueMessage(null, enterpriseId, containerName, Operations.START_CONSUMER);
        return ResponseEntity.status(HttpStatus.OK).body("Request has been sent to fan-out handler to start consumers for container " + containerName);
    }

    @PostMapping(URLConstant.STOPRABBITMQFORVERSION)
    public ResponseEntity<String> stopRabbitMqConsumerForVersion(@PathVariable("version") String version) {
        rabbitMQService.sendQueueMessage(version, null, null, Operations.STOP_CONSUMER_VERSION);
        return ResponseEntity.status(HttpStatus.OK).body("Request has been sent to fan-out handler to stop consumers for version " + version);
    }

    @PostMapping(URLConstant.STOPRABBITMQCONSUMERFORVERSIONANDENTERPRISE)
    public ResponseEntity<String> stopRabbitMqConsumerForVersionAndEnterprise(@QueryParam("enterpriseId") String enterpriseId, @PathVariable("version") String version) {
        if (enterpriseId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("EnterpriseId cannot be null");
        }
        rabbitMQService.sendQueueMessage(version, enterpriseId, null, Operations.STOP_CONSUMER_VERSION);
        return ResponseEntity.status(HttpStatus.OK).body("Request has been sent to fan-out handler to stop consumers for version " + version + " and enterprise " + enterpriseId);
    }
}
