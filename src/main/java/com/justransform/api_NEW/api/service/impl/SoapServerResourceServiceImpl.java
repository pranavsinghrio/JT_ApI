package com.justransform.api_NEW.api.service.impl;

import com.justransform.api_NEW.api.common.ApiConstant;
import com.justransform.api_NEW.api.common.ApiUtils;
import com.justransform.api_NEW.api.service.SoapServerResourceService;
import com.justransform.app.base.services.ResourceService;
import com.justransform.dao.ConnectionDao;
import com.justransform.dao.SoapServerDao;
import com.justransform.entity.Transaction;
import com.justransform.entity.enums.EventStatus;
import com.justransform.services.EventService;
import com.justransform.services.TransactionService;
import com.justransform.taskhandlers.messages.QueueMessage;
import com.justransform.utils.FileUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

@Service
public class SoapServerResourceServiceImpl implements SoapServerResourceService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Autowired
    private SoapServerDao soapServerDao;
    @Autowired
    private ResourceService resourceService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private EventService eventService;
    @Autowired
    private ConnectionDao connectionDao;
    @Autowired
    private ApiUtils apiUtils;

    public static final String AUTHENTICATION_HEADER = "Authorization";

    @Override
    public Response getWsdl(String junction) {

        try {
            String wsdlId = soapServerDao.getWsdlIdByJusnction(junction);

            if (wsdlId == null) {
                return Response.status(Response.Status.UNAUTHORIZED).type(MediaType.TEXT_XML).build();
            }

            InputStream downloadResource = resourceService.downloadResource(wsdlId, null);

            return Response.ok(IOUtils.toString(downloadResource)).header("Content-Type", "text/xml").build();

        } catch (Exception e) {
            logger.error("getWsdl failed Due to : " , e);
            return Response.serverError().entity("Something went Wrong").type(MediaType.TEXT_XML).build();
        }
    }

    @Override
    public Response postData(String junction, HttpServletRequest request) {

        logger.debug("Soap Server receiveData : Received Message on Soap Server for posting data ");

        Transaction beginTransaction = null;
        List<String> messages = new ArrayList<String>();
        String xmlString = null;
        String username = null, password = null;
        String inputFileContent = null;
        File file = null;
        String usernameAndPassword = null;
        String payload = null;
        String requestContentType = request.getContentType() == null ? "application/json" : request.getContentType();

        List<QueueMessage> msgList = new ArrayList<>();

        try {
            file = FileUtil.stream2file(request.getInputStream(), "file", null);
            usernameAndPassword = apiUtils.extractUsernameAndPasswordForResource(request, "SOAP SERVER",
                    new FileInputStream(file), beginTransaction);
            final StringTokenizer tokenizer = new StringTokenizer(usernameAndPassword, ":");
            username = tokenizer.nextToken();
            password = tokenizer.nextToken();
        } catch (Exception e) {
            logger.error("Soap Server receiveData: Failed to decode authcredentials IOException " , e);
            eventService.createEvent("Soap Server receiveData: Exception occurred " + e, EventStatus.FAIL, null,
                    beginTransaction);
            messages.add("401-Unauthorized");
            xmlString = apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, requestContentType);
            return Response.serverError().type(requestContentType).entity(xmlString).build();
        }

        try {
            logger.debug("Soap Server receiveData: Posting message to gateway");

            Connection connection = connectionDao.getSoapServerConnectionByUserNamePassword(username, password,
                    junction);
            if (connection != null) {
                inputFileContent = new String(FileUtil.getBytesFromFile(file));
                beginTransaction=transactionService.beginTransaction();
                beginTransaction.setSrcConnection(connection);
                payload = new String(FileUtil.getBytesFromFile(file));

                HTTPHeaders httpHeaders = apiUtils.extractHeadersAndCreateEventForResource(request, "SOAP SERVER", beginTransaction, "");

                String fileName = null;
                // update the transaction with filename and upload the file to s3
                if (request.getHeader("filename") != null) {
                    fileName = request.getHeader("filename");
                    beginTransaction.setInboundFileName(fileName);
                } else
                    fileName = "Soap ServerReceived_" + System.nanoTime() + FileUtil.getRandom(3);

                String newResourceId = apiUtils.uploadFile("SOAP SERVER", beginTransaction, payload);

                beginTransaction.setResourceId(newResourceId);
                // This method does not persist any data, just updates the transaction object
                transactionService.updateTransactionObject(connection, beginTransaction, true);
//				transactionService.updateTransactionFromResource(beginTransaction, TransactionStatus.IN_PROGRESS);
                if (!file.delete()) {
                    file.deleteOnExit();
                }


                if (connection.getAckBP() != null) {
                    msgList.add(apiUtils.createSyncAckMessage(newResourceId, connection));
                }
                messages.add("Soap Server received message successfully");
                String executeCustomFunction = null;
                // Handle Edit Sync Response
                if (connection.getCustomFunctionId() != null) {
                    executeCustomFunction = apiUtils.extractAndExecuteEditSyncForSoapOrOracle(request.getQueryString(), inputFileContent,
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

                try {
                    apiUtils.sendMsgsToQueue(msgList, connection);
                } catch (Exception ex) {

                    logger.error(
                            "Soap Server receiveData: Failed to send message to queue for transformation.Exception is: "
                                    + ex);
                    eventService.createEvent("Soap Server receiveData: Exception occured " + ex.getMessage(),
                            EventStatus.FAIL, newResourceId, beginTransaction);
                    messages.add("Internal Server Error: Failed to start transformation");
                    xmlString = apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, requestContentType);
                    return Response.serverError().type(requestContentType).entity(xmlString).build();
                }
                eventService.createEvent("Data Sent To Gateway", EventStatus.STARTED_PROCESSING, newResourceId,
                        beginTransaction);

                if (executeCustomFunction != null) {
                    return Response.ok().type(httpHeaders.getHeaderMap().get("content-type")).entity(executeCustomFunction).build();
                } else {
                    return Response.ok().type(httpHeaders.getHeaderMap().get("content-type"))
                            .entity(apiUtils.formResponse(ApiConstant.STATUS_SUCCESS, messages, httpHeaders.getHeaderMap().get("content-type"))).build();
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

            logger.error("Soap Server receiveData: Request failed due to, " , e);

            eventService.createEvent("Soap Server receiveData: Request failed due to Internal Server Error",
                    EventStatus.FAIL, null, beginTransaction);

            messages.add(e.getMessage());
            return Response.serverError().type(requestContentType)
                    .entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, requestContentType)).build();
        }

    }

    @Override
    public Response postSoapData(String junction, final HttpServletRequest request) {

        logger.debug("Soap Server receiveData : Received Message on Soap Server for posting data ");


        List<String> messages = new ArrayList<String>();
        Transaction beginTransaction =null;
        String xmlString = null;
        String username = null, password = null;
        String inputFileContent = null;
        String usernameAndPassword = null;
        String payload = null;
        String requestContentType = request.getContentType() == null ? "application/json" : request.getContentType();

        String outContentType = request.getContentType();
        if (outContentType == "") outContentType = MediaType.WILDCARD;
        List<QueueMessage> msgList = new ArrayList<>();
        Connection connection = null;
        try {
            // Extract payload of SOAP Request
            payload = new String(FileUtil.getBytesFromFile(request.getInputStream()));

            usernameAndPassword = apiUtils.extractUsernameAndPasswordForResource(request, "SOAP SERVER",
                    FileUtil.getInputStreamFromString(payload), null);
            final StringTokenizer tokenizer = new StringTokenizer(usernameAndPassword, ":");
            username = tokenizer.nextToken();
            password = tokenizer.nextToken();
        } catch (Exception e) {
            logger.error("Soap Server receiveData: Failed to decode authcredentials IOException " , e);
            messages.add("401-Unauthorized");
            xmlString = apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, outContentType);
            return Response.serverError().type(outContentType).entity(xmlString).build();
        }

        logger.debug("Soap Server receiveData: Posting message to gateway");
        try {
            connection = connectionDao.getSoapServerConnectionByUserNamePassword(username, password,
                    junction);
        } catch (Exception e) {
            logger.error("Soap Server receiveData: Failed to retrieve connection due to " , e);
        }
        if (connection != null) {
            beginTransaction = transactionService.beginTransaction();
            beginTransaction.setSrcConnection(connection);
            try {
                logger.debug("Soap Server receiveData : Received Message on Soap Server for posting data ");

                HTTPHeaders httpHeaders = apiUtils.extractHeadersAndCreateEventForResource(request, "SOAP SERVER", beginTransaction, "");

                String fileName = null;
                // update the transaction with filename and upload the file to s3
                if (request.getHeader("filename") != null) {
                    fileName = request.getHeader("filename");
                    beginTransaction.setInboundFileName(fileName);
                } else
                    fileName = "Soap ServerReceived_" + System.nanoTime() + FileUtil.getRandom(3);

                String newResourceId = apiUtils.uploadFile("SOAP SERVER", beginTransaction, payload);

                beginTransaction.setResourceId(newResourceId);
                // This method does not persist any data, just updates the transaction object
                transactionService.updateTransactionObject(connection, beginTransaction, true);
//				transactionService.updateTransactionFromResource(beginTransaction, TransactionStatus.IN_PROGRESS);

                if (connection.getAckBP() != null) {
                    msgList.add(apiUtils.createSyncAckMessage(newResourceId, connection));
                }
                messages.add("Soap Server received message successfully");
                String executeCustomFunction = null;
                // Handle Edit Sync Response
                if (connection.getCustomFunctionId() != null) {
                    executeCustomFunction = apiUtils.extractAndExecuteEditSyncForSoapOrOracle(request.getQueryString(), inputFileContent,
                            payload, connection, beginTransaction, true, httpHeaders);

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

                try {
                    apiUtils.sendMsgsToQueue(msgList, connection);
                } catch (Exception ex) {

                    logger.error(
                            "Soap Server receiveData: Failed to send message to queue for transformation.Exception is: "
                                    + ex);
                    eventService.createEvent("Soap Server receiveData: Exception occured " + ex.getMessage(),
                            EventStatus.FAIL, newResourceId, beginTransaction);
                    messages.add("Internal Server Error: Failed to start transformation");
                    xmlString = apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, "");
                    return Response.serverError().type(requestContentType).entity(xmlString).build();
                }
                eventService.createEvent("Data Sent To Gateway", EventStatus.STARTED_PROCESSING, newResourceId,
                        beginTransaction);

                if (executeCustomFunction != null) {
                    return Response.ok().type(httpHeaders.getHeaderMap().get("content-type")).entity(executeCustomFunction).build();
                } else {
                    return Response.ok().type(request.getContentType())
                            .entity(apiUtils.formResponse(ApiConstant.STATUS_SUCCESS, messages, httpHeaders.getHeaderMap().get("content-type"))).build();
                }
            } catch (Exception e) {
                // If exception occurs here, set the transaction as failed and remove any
                // downstream queue messages.
                beginTransaction.setTransactionStatus(TransactionStatus.FAILURE);
                msgList.clear();

                logger.error("Soap Server receiveData: Request failed due to, " , e);

                eventService.createEvent("Soap Server receiveData: Request failed due to Internal Server Error",
                        EventStatus.FAIL, null, beginTransaction);

                messages.add(e.getMessage());
                return Response.serverError().type(request.getContentType())
                        .entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, "")).build();
            }
        } else {
            messages.add("401-Unauthorized");
            return Response.status(Response.Status.UNAUTHORIZED).type(request.getContentType())
                    .entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, "")).build();
        }
    }

    @Override
    public Response isJusnctionExists(String junction) {
        try {
            if (logger.isDebugEnabled()) {
                logger.info("isJusnctionExists Starting...");
            }
            Boolean isExist = soapServerDao.isJunctionExists(junction);

            if (logger.isDebugEnabled()) {
                logger.debug("isJusnctionExists Ended.");
            }

            return Response.status(Response.Status.OK).entity(isExist).type(MediaType.APPLICATION_JSON).build();
        } catch (Exception e) {
            logger.error("isJusnctionExists failed due to " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.APPLICATION_JSON).build();
        }
    }

    @Override
    public Response recieveData(HttpServletRequest request, HttpServletResponse response) {
        return null;
    }

    @Override
    public Response getServerStatus() {
        return Response.ok().type(MediaType.TEXT_PLAIN).build();
    }
}

