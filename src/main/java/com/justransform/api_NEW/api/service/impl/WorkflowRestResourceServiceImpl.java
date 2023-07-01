package com.justransform.api_NEW.api.service.impl;

import com.justransform.api_NEW.api.common.ApiUtils;
import com.justransform.api_NEW.api.common.HTTPHeaders;
import com.justransform.api_NEW.api.service.RestResourceService;
import com.justransform.app.base.services.ResourceService;
import com.justransform.dao.ConnectionDao;
import com.justransform.entity.Transaction;
import com.justransform.services.EventService;
import com.justransform.services.TransactionService;
import com.justransform.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.flowable.engine.TaskService;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WorkflowRestResourceServiceImpl implements RestResourceService {

    public static final String AUTHENTICATION_HEADER = "Authorization";

    private static final String CONNECTION_CATEGORY = "connection";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private ConnectionDao connectionDao;
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
    @Autowired
    private UserService userService;
    @Autowired
    private TaskService taskService;

    @Override
    public Response getRecieveData(HttpServletRequest request, String junction) {
        logger.debug("REST receiveData : Received Message on REST for posting data ");
        Transaction beginTransaction = transactionService.beginTransaction();
        List<String> messages = new ArrayList<String>();
        String xmlString = null;
        String usernameAndPassword = null;
        String username = null, password = null;
        String bearerToken = null;
        String requestContentType = request.getContentType() == null ? "application/json" : request.getContentType();

        HTTPHeaders httpHeaders = apiUtils.extractHeadersAndCreateEventForResource(request, "REST", beginTransaction, "");

        try {
            usernameAndPassword = apiUtils.extractUsernameAndPasswordForResource(request, "REST", null,
                    beginTransaction);
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
            // if connection exists and edit sync script is also associated with the
            // connection.
            if (connection != null && connection.getCustomFunctionId() != null) {

                String executeCustomFunction = updateTransactionAndExecuteEditSyncCustomFunction(request.getQueryString(),
                        connection, beginTransaction, httpHeaders);
                if (executeCustomFunction != null) {
                    if (executeCustomFunction.trim().startsWith("<jtHTTPResponse>")) {
                        EditSyncResponse editSyncResponse = new EditSyncResponse(executeCustomFunction);
                        Response.Status cStatus = editSyncResponse.getHttpStatus();
                        String cMessage = editSyncResponse.getMessage();
                        return Response.status(cStatus).type(MediaType.APPLICATION_JSON).entity(cMessage).build();
                    }
                    return Response.ok(executeCustomFunction).type(MediaType.APPLICATION_JSON).build();
                }
                return Response.ok().type(MediaType.TEXT_PLAIN).build();
            } else {

                // if connection does not exists.
                if (connection == null) {
                    eventService.createEvent("Unauthorized user.Please check username/password", EventStatus.FAIL, null,
                            beginTransaction);
                    transactionService.updateRouteTransaction(beginTransaction, TransactionStatus.FAILURE);
                    return Response.status(Response.Status.UNAUTHORIZED).type(MediaType.TEXT_HTML).build();
                }
                // if connection exists but there is no edit sync script associated.
                else {
                    transactionService.updateRouteTransaction(beginTransaction, TransactionStatus.SUCCESS);
                    return Response.ok(null).type(MediaType.TEXT_PLAIN).build();
                }
            }

        } catch (Exception e) {
            logger.error("Failed to execute custom function due to " , e);
            eventService.createEvent("Failed to execute custom function for sync rest execution" + e, EventStatus.FAIL,
                    null, beginTransaction);
            messages.add(e.getMessage());
            return Response.serverError().type(requestContentType).entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, requestContentType)).build();
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

        logger.debug("REST receiveRest : Received Message on REST for posting data ");
        Transaction beginTransaction = transactionService.beginTransaction();

        try {
            usernameAndPassword = apiUtils.extractUsernameAndPasswordForResource(request, "REST", null,
                    beginTransaction);
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

        HTTPHeaders httpHeaders = apiUtils.extractHeadersAndCreateEventForResource(request, "REST", beginTransaction, "");

        beginTransaction.setTransactionStatus(TransactionStatus.IN_PROGRESS);

        try {
            Connection connection = null;

            if (bearerToken == null) {
                connection =  connectionDao.getRESTConnectionByUserNamePassword(username, password);
            } else {
                connection = connectionDao.getRESTConnectionByAuthToken(bearerToken);
            }

            if (connection != null) {
                beginTransaction.setSrcConnection(connection);

                String body = IOUtils.toString(request.getReader());

                // Update Inbound file name in transaction
                if (request.getHeader("filename") != null) {
                    beginTransaction.setInboundFileName(request.getHeader("filename"));
                }
                String newResourceId = apiUtils.uploadFile("REST", beginTransaction, body);

                beginTransaction.setResourceId(newResourceId);

                // This method does not persist any data, just updates the transaction object
                transactionService.updateTransactionObject(connection, beginTransaction, true);

                if (connection.getAckBP() != null) {
                    msgList.add(apiUtils.createSyncAckMessage(newResourceId, connection));
                }

                // Handle Edit Sync Response
                if (connection.getCustomFunctionId() != null) {
                    String requestQueryString = request.getQueryString();
                    cfResponse = executeEditSyncCustomFunctionForPost(httpHeaders.getHeaderString(), requestQueryString, body,
                            null, connection, beginTransaction, false, httpHeaders);
                    beginTransaction = transactionService.saveAndFlush(beginTransaction,beginTransaction.getTransactionStatus());
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

                handleWorkflow(connection, payload);

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
//					return Response.ok().type(MediaType.TEXT_XML)
//							.entity(apiUtils.formResponse(ApiConstant.STATUS_SUCCESS, messages)).build();
                    return Response.ok().type(MediaType.APPLICATION_JSON).entity(payload).build();
                }

            } else {
                // connection is null
                eventService.createEvent("REST Request failed, connection is null", EventStatus.INVALID_CONNECTION,
                        null, beginTransaction);
                beginTransaction.setTransactionStatus(TransactionStatus.FAILURE);

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
        Transaction beginTransaction = transactionService.beginTransaction();

        if (file==null || file.isEmpty() || file.getSize() == 0) {
            messages.add("Please upload a file or make sure the file is not empty");
            return Response.serverError().type(request.getContentType())
                    .entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, request.getContentType())).build();
        }

        try {
            usernameAndPassword = apiUtils.extractUsernameAndPasswordForResource(request, "REST", null,
                    beginTransaction);
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

        HTTPHeaders httpHeaders = apiUtils.extractHeadersAndCreateEventForResource(request, "REST", beginTransaction, "");

        beginTransaction.setTransactionStatus(TransactionStatus.IN_PROGRESS);

        try {
            Connection connection = null;

            if (bearerToken == null) {
                connection =  connectionDao.getRESTConnectionByUserNamePassword(username, password);
            } else {
                connection = connectionDao.getRESTConnectionByAuthToken(bearerToken);
            }

            if (connection != null) {
                beginTransaction.setSrcConnection(connection);
                payload = new String(file.getBytes());

                // Update Inbound file name in transaction
                if (file.getOriginalFilename() != null) {
                    beginTransaction.setInboundFileName(file.getOriginalFilename());
                }
                String newResourceId = apiUtils.uploadFile("REST", beginTransaction, payload);

                beginTransaction.setResourceId(newResourceId);

                // This method does not persist any data, just updates the transaction object
                transactionService.updateTransactionObject(connection, beginTransaction, true);

                if (connection.getAckBP() != null) {
                    msgList.add(apiUtils.createSyncAckMessage(newResourceId, connection));
                }

                // Handle Edit Sync Response
                if (connection.getCustomFunctionId() != null) {
                    String requestQueryString = request.getQueryString();

                    cfResponse = executeEditSyncCustomFunctionForPost(httpHeaders.getHeaderString(), requestQueryString, payload, newResourceId,
                            connection, beginTransaction, true, httpHeaders);
                    beginTransaction = transactionService.saveAndFlush(beginTransaction,beginTransaction.getTransactionStatus());
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
                eventService.createEvent("REST Request failed, connection is null", EventStatus.INVALID_CONNECTION,
                        null, beginTransaction);
                beginTransaction.setTransactionStatus(TransactionStatus.FAILURE);

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

    private String executeEditSyncCustomFunctionForPost(String headerContent, String requestQueryString, String body,
                                                        String newResourceId, Connection connection, Transaction beginTransaction, boolean isMultipart, HTTPHeaders httpHeaders)
            throws JTServerException, ClassCastException, ScriptException {
        String cfResponse = null;
        InputStream downloadResource = resourceService.downloadResource(connection.getCustomFunctionId(), connection.getCustomFunctionS3Version());
        if (downloadResource != null) {
            String customFunction = new String(FileUtil.getBytesFromFile(downloadResource));
            String input = null;

            StringBuilder headerPlusInput = new StringBuilder();
            headerPlusInput.append(headerContent.toString());
            headerPlusInput.append("\n");
            if(!isMultipart){
                headerPlusInput.append(body);
                if (headerPlusInput != null) {
                    input = headerPlusInput.toString();
                }
            } else {
                headerPlusInput.append(newResourceId);
                input = "<MULTIPART_FORM_DATA><jtresourceid>" + newResourceId
                        + "</jtresourceid></MULTIPART_FORM_DATA>";
            }


            cfResponse = apiUtils.executeEditSyncCustomFunction(customFunction, requestQueryString, input, body,
                    beginTransaction, httpHeaders);
        }

        return cfResponse;
    }

    private String updateTransactionAndExecuteEditSyncCustomFunction(String requestQueryString, Connection connection,
                                                                     Transaction beginTransaction, HTTPHeaders httpHeaders)
            throws ClassCastException, ScriptException, JTServerException, InvalidTransactionException {

        beginTransaction.setSrcConnection(connection);
        transactionService.updateRouteTransaction(beginTransaction, TransactionStatus.IN_PROGRESS);

        InputStream downloadResource = resourceService.downloadResource(connection.getCustomFunctionId(), connection.getCustomFunctionS3Version());
        if (downloadResource != null) {
            String customFunction = new String(FileUtil.getBytesFromFile(downloadResource));
            return apiUtils.executeEditSyncCustomFunction(customFunction, requestQueryString, null, null, beginTransaction, httpHeaders);
        } else {
            return null;
        }

    }

    private void handleWorkflow(Connection connection, String payload) throws JTServerException {

        Gson gson = new Gson();
        PostData data = gson.fromJson(payload, PostData.class);

        User user = userService.getUserByEmail(data.getUsername());

        if(user != null) {
            List<Task> tasks = taskService.createTaskQuery()
                    .taskAssignee(String.valueOf(user.getUserId()))
                    .includeProcessVariables()
                    .active()
                    .list().stream().filter(task -> task.getCategory().equals(CONNECTION_CATEGORY)).filter(task -> {
                        String connectionKey = WorkflowUtil.connectionVariableName(task.getTaskDefinitionKey());
                        return task.getProcessVariables().containsKey(connectionKey);
                    }).collect(Collectors.toList());

            if(tasks.size() > 0) {
                Map<String, Object> variables = new HashMap<>();
                String connectionData = WorkflowUtil.dataVariableName(tasks.get(0).getTaskDefinitionKey());
                variables.put(connectionData, data.getData());
                taskService.complete(tasks.get(0).getId(), variables);
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
}
