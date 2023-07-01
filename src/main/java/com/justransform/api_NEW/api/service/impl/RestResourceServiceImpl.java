package com.justransform.api_NEW.api.service.impl;

import com.justransform.api_NEW.api.common.ApiConstant;
import com.justransform.api_NEW.api.common.ApiUtils;
import com.justransform.api_NEW.api.service.RestResourceService;
import com.justransform.app.base.services.ResourceService;
import com.justransform.dao.ConnectionDao;
import com.justransform.dao.HeartbeatDao;
import com.justransform.entity.Heartbeat;
import com.justransform.entity.Transaction;
import com.justransform.entity.enums.EventStatus;
import com.justransform.entity.enums.TransactionStatus;
import com.justransform.exception.JTServerException;
import com.justransform.services.EventService;
import com.justransform.services.TransactionService;
import com.justransform.taskhandlers.messages.QueueMessage;
import com.justransform.utils.Constants;
import com.justransform.utils.FileUtil;
import com.nimbusds.jose.shaded.json.JSONObject;
import com.sun.jdi.connect.spi.Connection;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.InvalidTransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.script.ScriptException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

@Service
public class RestResourceServiceImpl implements RestResourceService {

    @Autowired
    private ConnectionDao connectionDao;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private EventService eventService;
    @Autowired
    private ResourceService resourceService;
    @Autowired
    private HeartbeatDao heartbeatDao;
    @Autowired
    private ApiUtils apiUtils;
    public static final String AUTHENTICATION_HEADER = "Authorization";

    @Override
    public Response getRecieveData(HttpServletRequest request, String junction) {
        logger.debug("REST receiveData : Received Message on REST for posting data ");
        Transaction beginTransaction = null;
        List<String> messages = new ArrayList<String>();
        String xmlString = null;
        String usernameAndPassword = null;
        String username = null, password = null;
        String bearerToken = null;
        String requestContentType = request.getContentType() == null ? "application/json" : request.getContentType();
        List<QueueMessage> msgList = new ArrayList<>();
        String cfResponse = null;
        String requestQueryString=null;


        try {
            usernameAndPassword = apiUtils.extractUsernameAndPasswordForResource(request, "REST", null, null);
            if(usernameAndPassword != null && !usernameAndPassword.isEmpty() && usernameAndPassword.contains(":")){
                final StringTokenizer tokenizer = new StringTokenizer(usernameAndPassword, ":");
                username = tokenizer.nextToken();
                password = tokenizer.nextToken();
            } else{
                bearerToken = usernameAndPassword;
            }
        } catch (Exception e) {
            messages.add("401-Unauthorized");
            xmlString = apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, requestContentType);
            return Response.serverError().type(requestContentType).entity(xmlString).build();
        }

        try {
            Connection connection = null;

            if (bearerToken == null) {
                connection =  connectionDao.getRESTConnectionByUserNamePassword(username, password);
            } else {
                connection = connectionDao.getRESTConnectionByAuthToken(bearerToken);
            }
            // connection is not null
            if (connection != null) {

                boolean flag = checkJunction(junction, connection);
                if(!flag){
                    messages.add("401-Unauthorized, provided junction unable to match with generated junction");
                    xmlString = apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, requestContentType);
                    return Response.serverError().type(requestContentType).entity(xmlString).build();
                }

                beginTransaction = transactionService.beginTransaction();
                beginTransaction.setSrcConnection(connection);
                String body = IOUtils.toString(request.getInputStream(),request.getCharacterEncoding());

                // Update Inbound file name in transaction
                if (request.getHeader("filename") != null) {
                    beginTransaction.setInboundFileName(request.getHeader("filename"));
                }
                HTTPHeaders httpHeaders = apiUtils.extractHeadersAndCreateEventForResource(request, "REST", beginTransaction, junction);

                String newResourceId = apiUtils.uploadFile("REST", beginTransaction, body);

                beginTransaction.setResourceId(newResourceId);
                transactionService.updateTransactionObject(connection, beginTransaction, true);

                if (connection.getAckBP() != null) {
                    msgList.add(apiUtils.createSyncAckMessage(newResourceId, connection));
                }

                // Handle Edit Sync Response
                if (connection.getCustomFunctionId() != null) {
                    beginTransaction = transactionService.saveAndFlush(beginTransaction,beginTransaction.getTransactionStatus());
                    requestQueryString = request.getQueryString();
                    cfResponse = apiUtils.executeEditSyncCustomFunctionForPost(httpHeaders.getHeaderString(), requestQueryString, body,
                            null, connection, beginTransaction, false, httpHeaders);
                }
                QueueMessage mt = apiUtils.createQueueMsgWithDetails(newResourceId, connection, beginTransaction);

                mt.addAttribute(Constants.PARAMS,requestQueryString);

                mt.addAttribute(Constants.HTTP_HEADERS,new JSONObject (httpHeaders.getHeaderMap()).toString());
                if (connection.getInboundQueueName() != null) {
                    mt.setQueueName(connection.getInboundQueueName());
                }

                QueueMessage tempMT = apiUtils.ifThrottlingEnabled(mt, connection, beginTransaction);
                if (tempMT != null)
                    msgList.add(tempMT);
                // Send messages to Queue for further processing
                try {
                    logger.info("Sending messages to RabbitMQ for further processing");
//                    apiUtils.sendMsgsToQueue(msgList, connection);
                } catch (Exception ex) {

                    logger.error(
                            "REST receiveData: Failed to send message to queue for transformation.Exception is: " + ex);
                    eventService.createEvent("REST receiveData: Exception occured " + ex.getMessage(), EventStatus.FAIL,
                            newResourceId, beginTransaction);
                    messages.add("Internal Server Error: Failed to start transformation");
                    return Response.serverError().type(request.getContentType())
                            .entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, request.getContentType())).build();

                }
                eventService.createEvent("Data Sent To Gateway", EventStatus.STARTED_PROCESSING, newResourceId,
                        beginTransaction);

                if (cfResponse != null) {
                    if (cfResponse.trim().startsWith("<jtHTTPResponse>")) {
                        EditSyncResponse editSyncResponse = new EditSyncResponse(cfResponse);
                        Response.Status cStatus = editSyncResponse.getHttpStatus();
                        String cMessage = editSyncResponse.getMessage();
                        String contentType = apiUtils.getContentTypeForResponse(httpHeaders, editSyncResponse);
                        transactionService.updateRouteTransaction(beginTransaction,TransactionStatus.SUCCESS);
                        eventService.createEvent("GET Call Executed Successfully" , EventStatus.SUCCESS,
                                null, beginTransaction);
                        return Response.status(cStatus).type(contentType).entity(cMessage).build();
                    } else {
                        eventService.createEvent("GET Call Executed Successfully" , EventStatus.SUCCESS,
                                null, beginTransaction);
                        transactionService.updateRouteTransaction(beginTransaction,TransactionStatus.SUCCESS);
                        return Response.ok().type(httpHeaders.getHeaderMap().get("content-type")).entity(cfResponse).build();
                    }
                } else {
                    eventService.createEvent("GET Call Executed Successfully" , EventStatus.SUCCESS,
                            null, beginTransaction);
                    transactionService.updateRouteTransaction(beginTransaction,TransactionStatus.SUCCESS);
                    // This method does not persist any data, just updates the transaction object
                    //transactionService.updateTransactionObject(connection,beginTransaction,true);
                    return Response.ok().type(requestContentType)
                            .entity(apiUtils.formResponse(ApiConstant.STATUS_SUCCESS, messages, requestContentType)).build();
                }
            } else {
                // connection is null
                messages.add("401-Unauthorized");
                return Response.status(Response.Status.UNAUTHORIZED).type(requestContentType)
                        .entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, requestContentType)).build();}

        } catch (Exception e) {
            if(beginTransaction != null) {
                try {
                    transactionService.updateRouteTransaction(beginTransaction,TransactionStatus.FAILURE);
                    eventService.createEvent(e.getMessage(), EventStatus.FAIL, null, beginTransaction);
                } catch (InvalidTransactionException | JTServerException e1) {
                    logger.error("Error while updating transaction: " + e1);
                }
            }
            logger.error("REST Request failed", e);
            messages.add(e.getMessage());
            return Response.serverError().type(MediaType.APPLICATION_JSON)
                    .entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, MediaType.APPLICATION_JSON)).build();
        }

    }

    @Override
    public Response postRecieveData(HttpServletRequest request, String junction) {

        List<QueueMessage> msgList = new ArrayList<>();
        List<String> messages = new ArrayList<String>();
        String username = null, password = null;
        String payload = null;
        String xmlString = null;
        String usernameAndPassword = null;
        String cfResponse = null;
        String bearerToken = null;
        String requestContentType = request.getContentType() == null ? "application/json" : request.getContentType();
        String requestQueryString=null;
        logger.debug("REST receiveRest : Received Message on REST for posting data ");
        Transaction beginTransaction=null;
        try {
            usernameAndPassword = apiUtils.extractUsernameAndPasswordForResource(request, "REST", null, null);
            if(usernameAndPassword != null && !usernameAndPassword.isEmpty() && usernameAndPassword.contains(":")){
                final StringTokenizer tokenizer = new StringTokenizer(usernameAndPassword, ":");
                username = tokenizer.nextToken();
                password = tokenizer.nextToken();
            } else{
                bearerToken = usernameAndPassword;
            }
        } catch (Exception e) {
            messages.add("401-Unauthorized");
            xmlString = apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, requestContentType);
            return Response.serverError().type(requestContentType).entity(xmlString).build();
        }

        try {
            Connection connection = null;

            if (bearerToken == null) {
                connection =  connectionDao.getRESTConnectionByUserNamePassword(username, password);
            } else {
                connection = connectionDao.getRESTConnectionByAuthToken(bearerToken);
            }

            if (connection != null) {

                boolean flag = checkJunction(junction, connection);
                if(!flag){
                    messages.add("401-Unauthorized, provided junction unable to match with generated junction");
                    xmlString = apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, requestContentType);
                    return Response.serverError().type(requestContentType).entity(xmlString).build();
                }

                beginTransaction = transactionService.beginTransaction();
                beginTransaction.setSrcConnection(connection);

                String body = IOUtils.toString(request.getInputStream(),request.getCharacterEncoding());

                // Update Inbound file name in transaction
                if (request.getHeader("filename") != null) {
                    beginTransaction.setInboundFileName(request.getHeader("filename"));
                }
                HTTPHeaders httpHeaders = apiUtils.extractHeadersAndCreateEventForResource(request, "REST", beginTransaction, junction);

                String newResourceId = apiUtils.uploadFile("REST", beginTransaction, body);

                beginTransaction.setResourceId(newResourceId);

                // This method does not persist any data, just updates the transaction object
                transactionService.updateTransactionObject(connection, beginTransaction, true);

                if (connection.getAckBP() != null) {
                    msgList.add(apiUtils.createSyncAckMessage(newResourceId, connection));
                }

                // Handle Edit Sync Response
                if (connection.getCustomFunctionId() != null) {
                    beginTransaction = transactionService.saveAndFlush(beginTransaction,beginTransaction.getTransactionStatus());
                    requestQueryString = request.getQueryString();
                    cfResponse = apiUtils.executeEditSyncCustomFunctionForPost(httpHeaders.getHeaderString(), requestQueryString, body,
                            null, connection, beginTransaction, false, httpHeaders);
                }

                // creates the message and updates it with connection, transaction and payload
                // details.
                QueueMessage mt = apiUtils.createQueueMsgWithDetails(newResourceId, connection, beginTransaction);

                mt.addAttribute(Constants.PARAMS,requestQueryString);
                mt.addAttribute(Constants.HTTP_HEADERS,new JSONObject(httpHeaders.getHeaderMap()).toString());
                if (connection.getInboundQueueName() != null) {
                    mt.setQueueName(connection.getInboundQueueName());
                }

                // if connection has a dedicated queue, route it to that queue, else leave it
                // with the default
                if (connection.getInboundQueueName() != null) {
                    mt.setQueueName(connection.getInboundQueueName());
                }

                QueueMessage tempMT = apiUtils.ifThrottlingEnabled(mt, connection, beginTransaction);
                if (tempMT != null)
                    msgList.add(tempMT);

                // Send messages to Queue for further processing
                try {
                    apiUtils.sendMsgsToQueue(msgList, connection);
                } catch (Exception ex) {

                    logger.error(
                            "REST receiveData: Failed to send message to queue for transformation.Exception is: " + ex);
                    eventService.createEvent("REST receiveData: Exception occured " + ex.getMessage(), EventStatus.FAIL,
                            newResourceId, beginTransaction);
                    messages.add("Internal Server Error: Failed to start transformation");
                    return Response.serverError().type(request.getContentType())
                            .entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, request.getContentType())).build();
                }
                eventService.createEvent("Data Sent To Gateway", EventStatus.STARTED_PROCESSING, newResourceId,
                        beginTransaction);

                if (cfResponse != null) {
                    if (cfResponse.trim().startsWith("<jtHTTPResponse>")) {
                        EditSyncResponse editSyncResponse = new EditSyncResponse(cfResponse);
                        Response.Status cStatus = editSyncResponse.getHttpStatus();
                        String cMessage = editSyncResponse.getMessage();
                        String contentType = apiUtils.getContentTypeForResponse(httpHeaders, editSyncResponse);
                        return Response.status(cStatus).type(contentType).entity(cMessage).build();
                    } else {
                        return Response.ok().type(httpHeaders.getHeaderMap().get("content-type")).entity(cfResponse).build();
                    }
                } else {
                    return Response.ok().type(requestContentType)
                            .entity(apiUtils.formResponse(ApiConstant.STATUS_SUCCESS, messages, requestContentType)).build();
                }

            } else {
                // connection is null

                messages.add("401-Unauthorized");
                return Response.status(Response.Status.UNAUTHORIZED).type(requestContentType)
                        .entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, requestContentType)).build();
            }
        } catch (Exception e) {
            // If exception occurs here, set the transaction as failed and remove any
            // downstream queue messages.
            beginTransaction.setTransactionStatus(TransactionStatus.FAILURE);
            msgList.clear();

            logger.error("REST Request failed", e);

            eventService.createEvent(e.getMessage(), EventStatus.FAIL, null, beginTransaction);

            messages.add(e.getMessage());
            return Response.serverError().type(MediaType.APPLICATION_JSON)
                    .entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, MediaType.APPLICATION_JSON)).build();
        }

    }



    @Override
    public Response postRecieveMultipartData(HttpServletRequest request, MultipartFile file,
                                             HttpServletResponse response) {

        List<QueueMessage> msgList = new ArrayList<>();
        List<String> messages = new ArrayList<String>();
        String username = null, password = null;
        String payload = null;
        String xmlString = null;
        String usernameAndPassword = null;
        String cfResponse = null;
        String bearerToken = null;
        String requestContentType = request.getContentType() == null ? "application/json" : request.getContentType();


        logger.debug("REST receiveRest : Received Message on REST for posting data ");
        Transaction beginTransaction=null;
        if (file==null || file.isEmpty() || file.getSize() == 0) {
            messages.add("Please upload a file or make sure the file is not empty");
            return Response.serverError().type(request.getContentType())
                    .entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, request.getContentType())).build();
        }

        try {
            usernameAndPassword = apiUtils.extractUsernameAndPasswordForResource(request, "REST", null,
                    null);
            if(usernameAndPassword != null && !usernameAndPassword.isEmpty() && usernameAndPassword.contains(":")){
                final StringTokenizer tokenizer = new StringTokenizer(usernameAndPassword, ":");
                username = tokenizer.nextToken();
                password = tokenizer.nextToken();
            } else{
                bearerToken = usernameAndPassword;
            }
        } catch (Exception e) {
            messages.add("401-Unauthorized");
            xmlString = apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, requestContentType);
            return Response.serverError().type(requestContentType).entity(xmlString).build();
        }





        try {
            Connection connection = null;

            if (bearerToken == null) {
                connection =  connectionDao.getRESTConnectionByUserNamePassword(username, password);
            } else {
                connection = connectionDao.getRESTConnectionByAuthToken(bearerToken);
            }

            if (connection != null) {
                beginTransaction=transactionService.beginTransaction();
                beginTransaction.setSrcConnection(connection);
                payload = new String(file.getBytes());

                // Update Inbound file name in transaction
                if (file.getOriginalFilename() != null) {
                    beginTransaction.setInboundFileName(file.getOriginalFilename());
                }
                HTTPHeaders httpHeaders = apiUtils.extractHeadersAndCreateEventForResource(request, "REST", beginTransaction, "");
                String newResourceId = apiUtils.uploadFile("REST", beginTransaction, file.getInputStream());

                beginTransaction.setResourceId(newResourceId);

                // This method does not persist any data, just updates the transaction object
                transactionService.updateTransactionObject(connection, beginTransaction, true);

                if (connection.getAckBP() != null) {
                    msgList.add(apiUtils.createSyncAckMessage(newResourceId, connection));
                }

                // Handle Edit Sync Response
                if (connection.getCustomFunctionId() != null) {
                    beginTransaction = transactionService.saveAndFlush(beginTransaction,beginTransaction.getTransactionStatus());
                    String requestQueryString = request.getQueryString();

                    cfResponse = apiUtils.executeEditSyncCustomFunctionForPost(httpHeaders.getHeaderString(), requestQueryString, payload, newResourceId,
                            connection, beginTransaction, true, httpHeaders);
                }

                // creates the message and updates it with connection, transaction and payload
                // details.
                QueueMessage mt = apiUtils.createQueueMsgWithDetails(newResourceId, connection, beginTransaction);

                // if connection has a dedicated queue, route it to that queue, else leave it
                // with the default
                if (connection.getInboundQueueName() != null) {
                    mt.setQueueName(connection.getInboundQueueName());
                }

                QueueMessage tempMT = apiUtils.ifThrottlingEnabled(mt, connection, beginTransaction);
                if (tempMT != null)
                    msgList.add(tempMT);

                // Send messages to Queue for further processing
                apiUtils.sendMsgsToQueue(msgList, connection);
                eventService.createEvent("Data Sent To Gateway", EventStatus.STARTED_PROCESSING, newResourceId,
                        beginTransaction);

                if (cfResponse != null) {
                    if (cfResponse.trim().startsWith("<jtHTTPResponse>")) {
                        EditSyncResponse editSyncResponse = new EditSyncResponse(cfResponse);
                        Response.Status cStatus = editSyncResponse.getHttpStatus();
                        String cMessage = editSyncResponse.getMessage();
                        return Response.status(cStatus).type(MediaType.APPLICATION_JSON).entity(cMessage).build();
                    } else {
                        return Response.ok().type(MediaType.APPLICATION_JSON).entity(cfResponse).build();
                    }
                } else {
                    return Response.ok().type(requestContentType)
                            .entity(apiUtils.formResponse(ApiConstant.STATUS_SUCCESS, messages, requestContentType)).build();
                }

            } else {
                // connection is null
                messages.add("401-Unauthorized");
                return Response.status(Response.Status.UNAUTHORIZED).type(requestContentType)
                        .entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, requestContentType)).build();
            }
        } catch (Exception e) {
            // If exception occurs here, set the transaction as failed and remove any
            // downstream queue messages.
            beginTransaction.setTransactionStatus(TransactionStatus.FAILURE);
            msgList.clear();

            logger.error("REST Request failed", e);

            eventService.createEvent(e.getMessage(), EventStatus.FAIL, null, beginTransaction);

            messages.add(e.getMessage());
            return Response.serverError().type(MediaType.APPLICATION_JSON)
                    .entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, MediaType.APPLICATION_JSON)).build();
        }

    }



    private String updateTransactionAndExecuteEditSyncCustomFunction(String requestQueryString, Connection connection,
                                                                     Transaction beginTransaction, HTTPHeaders httpHeaders)
            throws ClassCastException, ScriptException, JTServerException, InvalidTransactionException {

        beginTransaction.setSrcConnection(connection);
        try {
            transactionService.updateRouteTransaction(beginTransaction, TransactionStatus.IN_PROGRESS);
        } catch (javax.transaction.InvalidTransactionException e) {
            throw new RuntimeException(e);
        }

        if(StringUtils.isEmpty(connection.getCustomFunctionId())) {
            return null;
        }else {
            InputStream downloadResource = resourceService.downloadResource(connection.getCustomFunctionId(), connection.getCustomFunctionS3Version());
            if (downloadResource != null) {
                String customFunction = new String(FileUtil.getBytesFromFile(downloadResource));
                return apiUtils.executeEditSyncCustomFunction(customFunction, requestQueryString, null, null, beginTransaction, httpHeaders);
            } else {
                return null;
            }

        }
    }

    @Override
    public Response getMonitoringHeartBeat(String componentName) {
        try {
            if (componentName != null && !componentName.isEmpty()) {
                Heartbeat heartbeat = new Heartbeat();
                heartbeat.setComponent(componentName);
                heartbeat.setActionTaken(false);
                heartbeat.setTimestamp(new Timestamp(System.nanoTime() + FileUtil.getRandom(3)));
                heartbeatDao.save(heartbeat);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Response.ok().type(MediaType.TEXT_PLAIN).build();
    }

    @Override
    public Response getServerStatus() {
        logger.debug("Health Check");
        return Response.ok().type(MediaType.TEXT_PLAIN).build();
    }

    public boolean checkJunction(String junction, Connection connection){  //jun=empty

        if(!junction.isEmpty()){
            if(connection.getRestConfig().getJunction() == null || !connection.getRestConfig().getJunction().equals(junction)){
                return false;
            }
        }
        return true;
    }
}
