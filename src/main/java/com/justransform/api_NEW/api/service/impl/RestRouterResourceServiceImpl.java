package com.justransform.api_NEW.api.service.impl;

import com.justransform.api_NEW.api.service.RestRouterResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

@Service
public class RestRouterResourceServiceImpl implements RestRouterResourceService {

    @Autowired
    private ConnectionDao connectionDao;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private EventService eventService;
    @Autowired
    private ApiUtils apiUtils;

    @Override
    public Response processReceivedData(HttpServletRequest request, String junction) {

        List<String> messages = new ArrayList<String>();
        String username = null, password = null;
        String xmlString = null;
        String cfResponse = null;
        String usernameAndPassword = null;
        String bearerToken = null;
        String requestContentType = request.getContentType() == null ? "application/json" : request.getContentType();
        JSONArray jsonEventArray = new JSONArray();

        logger.debug(request.getMethod() + " REST Router : Received REST Router request ");
        Transaction beginTransaction=null;

        if(junction == null || junction.isEmpty()){
            messages.add("401-Unauthorized");
            xmlString = apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, requestContentType);
            return Response.serverError().type(requestContentType).entity(xmlString).build();
        }

        try {
            Connection connection = null;
            connection =  connectionDao.getRESTRouterConnectionByJunction(junction);
            if(connection == null) {
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
                if (bearerToken == null) {
                    connection =  connectionDao.getRestRouterConnectionByUserNamePassword(username, password);
                } else {
                    connection = connectionDao.getRESTConnectionByAuthToken(bearerToken);
                }
            }

            if (connection != null) {
                beginTransaction = transactionService.beginTransaction();
                beginTransaction.setSrcConnection(connection);
                beginTransaction.setDestConnection(connection.getRestRouterConfig().getDestConnection());
                String body = IOUtils.toString(request.getInputStream(),request.getCharacterEncoding());

                // Update Inbound file name in transaction
                if (request.getHeader("filename") != null) {
                    beginTransaction.setInboundFileName(request.getHeader("filename"));
                }
                HTTPHeaders httpHeaders = apiUtils.extractHeadersAndCreateEventForResource(request, "REST Router", beginTransaction, junction);
                httpHeaders.getHeaderMap().put("junction",junction);

                if (request.getMethod().equals("POST")) {
                    String newResourceId = apiUtils.uploadFile("POST REST Router", beginTransaction, body);
                    beginTransaction.setResourceId(newResourceId);
                }

                // This method does not persist any data, just updates the transaction object
                transactionService.updateTransactionObject(connection, beginTransaction, true);

                // Handle Edit Sync Response
                if (connection.getCustomFunctionId() != null) {
                    String requestQueryString = request.getQueryString();
                    cfResponse = apiUtils.executeEditSyncCustomFunctionForPost(httpHeaders.getHeaderString(), requestQueryString, body,
                            null, connection, beginTransaction, false, httpHeaders);
                    beginTransaction = transactionService.saveAndFlush(beginTransaction,beginTransaction.getTransactionStatus());
                }

                if (cfResponse != null) {
                    jsonEventArray.put(eventService.createHandlerEvent("Rest Router executed successfully", EventStatus.SUCCESS,
                            null, beginTransaction.getTransactionId(), 0l));

                    eventService.saveEvents(jsonEventArray, TransactionStatus.SUCCESS,beginTransaction);

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
                    jsonEventArray.put(eventService.createHandlerEvent("Rest Router executed successfully", EventStatus.SUCCESS,
                            null, beginTransaction.getTransactionId(), 0l));

                    eventService.saveEvents(jsonEventArray, TransactionStatus.SUCCESS,beginTransaction);

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
            logger.error("REST Router Request failed", e);

            eventService.createEvent(e.getMessage(), EventStatus.FAIL, null, beginTransaction);

            messages.add(e.getMessage());
            return Response.serverError().type(MediaType.APPLICATION_JSON)
                    .entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, MediaType.APPLICATION_JSON)).build();
        }

    }
}
