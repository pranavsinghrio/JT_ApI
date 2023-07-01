package com.justransform.api_NEW.api.service.impl;

import com.justransform.api_NEW.api.common.ApiConstant;
import com.justransform.api_NEW.api.common.ApiUtils;
import com.justransform.api_NEW.api.common.HTTPHeaders;
import com.justransform.api_NEW.api.service.OtmResourceService;
import com.justransform.app.base.services.ResourceService;
import com.justransform.dao.ConnectionDao;
import com.justransform.entity.Transaction;
import com.justransform.entity.enums.EventStatus;
import com.justransform.entity.enums.Protocol;
import com.justransform.entity.enums.TransactionStatus;
import com.justransform.exception.JTServerException;
import com.justransform.pojo.OTMContent;
import com.justransform.services.EventService;
import com.justransform.services.TransactionService;
import com.justransform.taskhandlers.messages.QueueMessage;
import com.justransform.utils.FileUtil;
import com.justransform.utils.JTLogger;
import com.justransform.utils.JsonUtils;
import com.sun.jdi.connect.spi.Connection;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.InvalidTransactionException;
import org.hibernate.HibernateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.script.ScriptException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

@Service
public class OtmResourceServiceImpl implements OtmResourceService {

    private final Logger LOGGER = LoggerFactory.getLogger(OtmResourceServiceImpl.class);

    @Autowired
    private TransactionService transactionService;
    @Autowired
    private EventService eventService;
    @Autowired
    private ResourceService resourceService;
    public static final String AUTHENTICATION_HEADER = "Authorization";
    @Autowired
    private ConnectionDao connectionDao;
    @Autowired
    private ApiUtils apiUtils;

    @Override
    public Response getServerStatus() {
        return Response.ok().type(MediaType.TEXT_PLAIN).build();
    }

    @Override
    public Response getRecieveData(HttpServletRequest request) {
        Transaction beginTransaction = transactionService.beginTransaction();
        Long transactionId = null;
        LOGGER.debug(JTLogger.appendTransactionIdToLogs(transactionId) + "OTM receiveData : Received Message on OTM");
        List<String> messages = new ArrayList<String>();
        String usernameAndPassword = null;
        String username = null, password = null;
        String requestContentType = request.getContentType() == null ? "application/json" : request.getContentType();

        HTTPHeaders httpHeaders = apiUtils.extractHeadersAndCreateEventForResource(request, "OTM", beginTransaction, "");

        try {
            usernameAndPassword = apiUtils.extractUsernameAndPasswordForResource(request, "OTM", null,
                    beginTransaction);
            final StringTokenizer tokenizer = new StringTokenizer(usernameAndPassword, ":");
            username = tokenizer.nextToken();
            password = tokenizer.nextToken();
        } catch (Exception e) {
            messages.add("401-Unauthorized");
            return Response.serverError().type(requestContentType)
                    .entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, requestContentType)).build();
        }

        try {
            Connection connection = connectionDao.getConnectionByUserNamePassword(username, password, Protocol.OTM);
            ;
            // if connection exists and edit sync script is also associated with the
            // connection.
            if (connection != null && connection.getCustomFunctionId() != null) {
                String cfResponse = updateTransactionAndExecuteEditSyncCustomFunction(request.getQueryString(),
                        connection, beginTransaction, httpHeaders);

                if (cfResponse != null) {

                    return Response.ok(cfResponse).type(httpHeaders.getHeaderMap().get("content-type")).build();
                }
                return Response.ok().type(httpHeaders.getHeaderMap().get("content-type")).build();

//				return Response.ok( cfResponse != null ? cfResponse ).type(MediaType.APPLICATION_JSON).build();
            } else {

                // if connection does not exists.
                if (connection == null) {
                    LOGGER.error(JTLogger.appendTransactionIdToLogs(transactionId)
                            + "Could not find connection with username/password");
                    eventService.createEvent("Unauthorized user.Please check username/password", EventStatus.FAIL, null,
                            beginTransaction);
                    transactionService.updateRouteTransaction(beginTransaction, TransactionStatus.FAILURE);
                    return Response.status(Response.Status.UNAUTHORIZED).type(MediaType.TEXT_HTML).build();
                }
                // if connection exists but there is no edit sync script associated.
                else {
//					transactionService.updateRouteTransaction(beginTransaction, TransactionStatus.SUCCESS);
                    return Response.ok(null).type(MediaType.TEXT_PLAIN).build();
                }
            }

        } catch (Exception e) {
            LOGGER.error("Failed to execute custom function due to " , e);
            eventService.createEvent("Failed to execute custom function for sync otm execution" + e, EventStatus.FAIL,
                    null, beginTransaction);
            return Response.serverError().entity("Failed to execute custom function for sync otm execution").type(MediaType.APPLICATION_JSON).build();
        }

    }

    private String updateTransactionAndExecuteEditSyncCustomFunction(String queryString, Connection connection,
                                                                     Transaction beginTransaction, HTTPHeaders httpHeaders)
            throws InvalidTransactionException, JTServerException, ClassCastException, ScriptException {

        beginTransaction.setSrcConnection(connection);
        transactionService.updateRouteTransaction(beginTransaction, TransactionStatus.IN_PROGRESS);

        InputStream downloadResource = resourceService.downloadResource(connection.getCustomFunctionId(), connection.getCustomFunctionS3Version());
        if (downloadResource != null) {
            String customFunction = new String(FileUtil.getBytesFromFile(downloadResource));
            String executeCustomFunction = apiUtils.executeNewEditSyncCustomFunctionForPost(customFunction, queryString,
                    beginTransaction, null, httpHeaders);

            transactionService.updateRouteTransaction(beginTransaction, TransactionStatus.SUCCESS);
            return executeCustomFunction;
        } else {
            LOGGER.warn(JTLogger.appendTransactionIdToLogs(beginTransaction.getTransactionId())
                    + "Custom function was not associated with connection");
            transactionService.updateRouteTransaction(beginTransaction, TransactionStatus.WARNING);
            return null;
        }

    }

    @Override
    public Response postRecieveData(HttpServletRequest request) {
        List<QueueMessage> msgList = new ArrayList<>();
        // File file = null;
        Transaction beginTransaction = null;
        String username = null, password = null;
//		ConnectionVo connection = null;
        File otmInboundFile = null, outputFile = null, acknowledgement = null;
        FileOutputStream out = null;
        FileInputStream inputStream = null, ackStream = null;
        ByteArrayInputStream byteStream = null;
        Long transactionId = null;
        List<String> messages = new ArrayList<String>();
        String xmlString = null;
        String usernameAndPassword = null;
        String requestContentType = request.getContentType() == null ? "application/json" : request.getContentType();

        try {

            try {
                usernameAndPassword = apiUtils.extractUsernameAndPasswordForResource(request, "OTM", null,
                        null);
                final StringTokenizer tokenizer = new StringTokenizer(usernameAndPassword, ":");
                username = tokenizer.nextToken();
                password = tokenizer.nextToken();
            } catch (Exception e) {
                messages.add("401-Unauthorized");
                xmlString = apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, requestContentType);
                return Response.serverError().type(requestContentType).entity(xmlString).build();
            }

            Connection connection = connectionDao.getConnectionByUserNamePassword(username, password, Protocol.OTM);
            if (connection != null) {
                beginTransaction = transactionService.beginTransaction();
                apiUtils.extractHeadersAndCreateEventForResource(request, "OTM", beginTransaction,"");
                transactionId = beginTransaction.getTransactionId();
                LOGGER.debug(JTLogger.appendTransactionIdToLogs(transactionId) + "Received an OTM Inbound message request");
                otmInboundFile = new File(Constants.INBOUND_DIR,
                        "inbound" + System.nanoTime() + FileUtil.getRandom(3) + ".xml");

                out = new FileOutputStream(otmInboundFile);
                IOUtils.copy(request.getInputStream(), out);
                out.close();
                LOGGER.debug(JTLogger.appendTransactionIdToLogs(transactionId) + "inputstream copied to file");
                // file = FileUtil.stream2file(request.getInputStream(), "file", null);
                inputStream = new FileInputStream(otmInboundFile);
                String eventResourceId = resourceService.uploadFileToS3WithoutDB(inputStream, true);
                eventService.createEvent("OTM receiveData: File Received", EventStatus.FILE_RECIEVED_FOR_PROCESSING,
                        eventResourceId, beginTransaction);
                outputFile = File.createTempFile("NamespaceRemovedOtmFile_" + System.nanoTime() + FileUtil.getRandom(3),
                        ".xml");
                otmInboundFile = removeNamespace(otmInboundFile, outputFile, transactionId);

                beginTransaction.setSrcConnection(connection);
                transactionService.updateTransactionFromResource(beginTransaction, TransactionStatus.IN_PROGRESS);
//				partner = connection;
                LOGGER.debug(JTLogger.appendTransactionIdToLogs(transactionId)
                        + "Got an OTM Inbound message request from username: " + username);

                LOGGER.debug(JTLogger.appendTransactionIdToLogs(transactionId)
                        + "event created of validation of connection");

                String filename = request.getHeader("filename");
                if (filename != null) {
                    beginTransaction.setInboundFileName(filename);
                    transactionService.updateTransactionFromResource(beginTransaction, TransactionStatus.IN_PROGRESS);
                } else {
                    filename = "otm" + System.nanoTime() + FileUtil.getRandom(3);
                }

                acknowledgement = processInbound(otmInboundFile, beginTransaction, connection, msgList);

                LOGGER.debug(JTLogger.appendTransactionIdToLogs(transactionId) + "acknowledgement received");
                if (acknowledgement != null) {
                    ackStream = new FileInputStream(acknowledgement);
                    String ackResourceId = resourceService.uploadFileToS3WithoutDB(ackStream, true);
                    LOGGER.debug(JTLogger.appendTransactionIdToLogs(transactionId)
                            + "OTM Inbound message acknowledgement generated");
                    eventService.createEvent("OTM Inbound message acknowledgement generated", EventStatus.SUCCESS,
                            ackResourceId, beginTransaction);
                    apiUtils.sendMsgsToQueue(msgList, connection);
                    return Response.ok().type(MediaType.APPLICATION_XML).entity(FileUtil.getBytesFromFile(acknowledgement)).build();
                } else {
                    LOGGER.debug(JTLogger.appendTransactionIdToLogs(transactionId)
                            + "OTM Inbound message acknowledgement: " + acknowledgement);
                    eventService.createEvent("OTM Inbound TransmissionReport is completed. ", EventStatus.SUCCESS, null,
                            beginTransaction);
                    apiUtils.sendMsgsToQueue(msgList, connection);
                    return Response.ok().type(MediaType.APPLICATION_XML).build();
                }

            } else {
                LOGGER.error("OTM Inbound message request from unrecognized partner: " + username + " / " + password);
//				eventService.createEvent("OTM receiveData: Connection null", EventStatus.FAIL, null, beginTransaction);
                return Response.status(Response.Status.UNAUTHORIZED).type(MediaType.APPLICATION_XML).build();
            }
        } catch (Exception e) {
            LOGGER.error(JTLogger.appendTransactionIdToLogs(transactionId)
                    + "OTM Inbound message request, Exception occurred, ", e);
            if (beginTransaction != null) {
                eventService.createEvent("OTM receiveData: Exception occurred, " + e, EventStatus.FAIL, null,
                        beginTransaction);
            }
            return Response.serverError()
                    .entity(JsonUtils.getErrorJson("OTM Inbound message request, Exception occurred")).type(MediaType.APPLICATION_JSON).build();
        } finally {
            msgList.clear();
            try {
                if (inputStream != null)
                    inputStream.close();
                if (out != null)
                    out.close();
                if (byteStream != null)
                    byteStream.close();
                if (ackStream != null)
                    ackStream.close();
                if (otmInboundFile != null) {
                    boolean bDelete = otmInboundFile.delete();
                    if (!bDelete)
                        otmInboundFile.deleteOnExit();
                }
                if (outputFile != null) {
                    boolean bDelete = outputFile.delete();
                    if (!bDelete)
                        outputFile.deleteOnExit();
                }
                if (acknowledgement != null)
                    acknowledgement.deleteOnExit();
            } catch (IOException ie) {
                LOGGER.error(
                        JTLogger.appendTransactionIdToLogs(transactionId) + "Error occurred while closing streams: ",
                        ie);
            }

        }
    }

    public File processInbound(File otmInboundFile, Transaction beginTransaction, Connection connection,
                               List<QueueMessage> msgList) throws HibernateException, Exception {
        InputStream ack = null;
        File ackFile = null;
        OTMContent otmMessage = null;
        Long transactionId = null;
        transactionId = beginTransaction.getTransactionId();
        try {
            // Process the incoming file
            otmMessage = new OTMContent(otmInboundFile);

            LOGGER.debug(JTLogger.appendTransactionIdToLogs(transactionId) + "Inbound written at : "
                    + otmInboundFile.getAbsolutePath());

            // TODO handle ack or report
            String status = otmMessage.getProperty("otm.reportStatus");
            if (status != null) {
                // Its Transmission Report
                LOGGER.debug(JTLogger.appendTransactionIdToLogs(transactionId) + "Inbound Transmission Report Status = "
                        + status);
                if (status.equals("ERROR") == true) {
                    String error = otmMessage.getProperty("otm.iMessageText");
                    LOGGER.debug(
                            JTLogger.appendTransactionIdToLogs(transactionId) + "Transmission Report Error : " + error);
                }
                if (otmMessage.getProperty("instanceProcessId") != null)
                    eventService.createEvent(
                            "Instance ProcessID: " + otmMessage.getProperty("instanceProcessId") + " "
                                    + TransactionType.Received_Acknowledgement.toString() + " "
                                    + connection.getOtmConfig().getUsername(),
                            EventStatus.RECEIVED_ACKNOWLEDGEMENT, null, beginTransaction);
                else
                    eventService.createEvent(
                            TransactionType.Received_Acknowledgement.toString() + " "
                                    + connection.getOtmConfig().getUsername(),
                            EventStatus.RECEIVED_ACKNOWLEDGEMENT, null, beginTransaction);

                sendMessageToRoutHanler(otmInboundFile, beginTransaction, connection, msgList);
                return null;
            }

            LOGGER.debug(JTLogger.appendTransactionIdToLogs(transactionId)
                    + "Partner Verification succeeded for Inbound request");
            File otmTransmissionBodyFile = new File(Constants.INBOUND_DIR,
                    "inbound" + System.nanoTime() + FileUtil.getRandom(3) + ".xml");
            FileOutputStream out = new FileOutputStream(otmTransmissionBodyFile);
            IOUtils.copy(otmMessage.getBodyAsStream(), out);
            out.close();

            LOGGER.debug(JTLogger.appendTransactionIdToLogs(transactionId) + "Finished processing for Inbound request");
            // Resource created after processing inbound

            beginTransaction = transactionService.updateTransactionObject(connection, beginTransaction, true);

            ack = otmMessage.getAcknowledgement();

            LOGGER.debug(JTLogger.appendTransactionIdToLogs(transactionId) + "Generated Acknowledgement for Inbound");

            ackFile = FileUtil.stream2file(ack, "otm-ack", ".xml");

            String newResourceId = sendMessageToRoutHanler(otmTransmissionBodyFile, beginTransaction, connection,
                    msgList);

            // send message to route for execution of ack process
            if (connection != null && connection.getAckBP() != null) {
                QueueMessage ackMt = apiUtils.createSyncAckMessage(newResourceId, connection);
                // if connection has a dedicated queue, route it to that queue, else leave it
                // with the default
                if (connection.getInboundQueueName() != null) {
                    ackMt.setQueueName(connection.getInboundQueueName());
                }

                QueueMessage tempMT = apiUtils.ifThrottlingEnabled(ackMt, connection, beginTransaction);
                if (tempMT != null)
                    msgList.add(tempMT);
            }
        } catch (FileNotFoundException e) {
            LOGGER.error(JTLogger.appendTransactionIdToLogs(transactionId) + " Exception - " + e.getMessage(), e);
            throw e;
        } catch (IOException e) {
            LOGGER.error(JTLogger.appendTransactionIdToLogs(transactionId) + " Exception - " + e.getMessage(), e);
            throw e;
        }
        return ackFile;
    }

    public String sendMessageToRoutHanler(File otmTransmissionBodyFile, Transaction beginTransaction,
                                          Connection connection, List<QueueMessage> msgList) throws Exception {
        Long transactionId = null;
        transactionId = beginTransaction.getTransactionId();
        String newResourceId = null;
        try {

            newResourceId = resourceService.uploadFileToS3WithoutDB(new FileInputStream(otmTransmissionBodyFile), true);

            beginTransaction.setResourceId(newResourceId);
            QueueMessage mt = apiUtils.createQueueMsgWithDetails(newResourceId, connection, beginTransaction);
            // if connection has a dedicated queue, route it to that queue, else leave it
            // with the default
            if (connection.getInboundQueueName() != null) {
                mt.setQueueName(connection.getInboundQueueName());
            }

            QueueMessage tempMT = apiUtils.ifThrottlingEnabled(mt, connection, beginTransaction);
            if (tempMT != null)
                msgList.add(tempMT);

            // Add transaction for sending unwrapped file
            eventService.createEvent(TransactionType.Sent_Message.toString() + " Route", EventStatus.SENT_MESSAGE,
                    newResourceId, beginTransaction);

        } catch (Exception ex) {
            LOGGER.error(JTLogger.appendTransactionIdToLogs(transactionId)
                    + "OTM receiveData: Failed to send message to queue for transformation.Exception is: ", ex);
            eventService.createEvent("OTM receiveData: Exception occured " + ex.getMessage(), EventStatus.FAIL,
                    newResourceId, beginTransaction);
            throw ex;
        }

        return newResourceId;

    }

    private File removeNamespace(File inboundFile, File outputFile, Long transactionId)
            throws TransformerFactoryConfigurationError {
        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = null;
        try {
            InputStream in = this.getClass().getResourceAsStream("/remove-namespace.xslt");

            if (in == null) {
                LOGGER.error(
                        JTLogger.appendTransactionIdToLogs(transactionId) + "removeNamespace, Failed to get xslt file");
            }
            transformer = factory.newTransformer(new StreamSource(in));
            transformer.setOutputProperty(OutputKeys.INDENT, "no");

            if (in != null)
                in.close();
        } catch (Exception e) {
            LOGGER.error(JTLogger.appendTransactionIdToLogs(transactionId) + "Header Remover Utility failed :", e);
        }
        StreamResult result = null;
        try {
            result = new StreamResult(outputFile);
            Source text = new StreamSource(inboundFile);
            transformer.transform(text, result);
            if (result.getOutputStream() != null)
                result.getOutputStream().close();
            if (result.getWriter() != null)
                result.getWriter().close();
        } catch (TransformerException e) {
            LOGGER.error(JTLogger.appendTransactionIdToLogs(transactionId) + "Header Remover Utility failed :", e);
        } catch (Exception e) {
            LOGGER.error(JTLogger.appendTransactionIdToLogs(transactionId) + "Header Remover Utility failed :", e);
        }

        return outputFile;
    }
}
