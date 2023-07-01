package com.justransform.api_NEW.api.service.impl;

import com.justransform.api_NEW.api.common.ApiConstant;
import com.justransform.api_NEW.api.common.ApiUtils;
import com.justransform.api_NEW.api.service.OraclecmkV2ResourceService;
import com.justransform.app.base.services.ResourceService;
import com.justransform.dao.ConnectionDao;
import com.justransform.dao.HeartbeatDao;
import com.justransform.data.repositories.OracleCmkV2Repository;
import com.justransform.services.EventService;
import com.justransform.services.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

@Service
public class OraclecmkV2ResourceServiceImpl implements OraclecmkV2ResourceService {

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
    @Autowired
    private OracleCmkV2Repository oracleCmkV2Repository;

    public static final String AUTHENTICATION_HEADER = "Authorization";

    @Override
    public Response recieveData(HttpServletRequest request, HttpServletResponse response) {
        return null;
    }

    @Override
    public Response getServerStatus() {
        return Response.ok().build();
    }

    @Override
    public Response getWsdl(HttpServletRequest request, String wsdl) {
        String username = null, password = null;
        List<String> messages = new ArrayList<String>();
        String payload = null;
        String xmlString = null;
        String usernameAndPassword = null;
        String cfResponse = null;

        logger.debug("Oracle CMK V2 : Received Message for posting data ");
        Transaction beginTransaction = transactionService.beginTransaction();

        try {
            usernameAndPassword = apiUtils.extractUsernameAndPasswordForResource(request, "Oracle CMK", null,
                    beginTransaction);
            final StringTokenizer tokenizer = new StringTokenizer(usernameAndPassword, ":");
            username = tokenizer.nextToken();
            password = tokenizer.nextToken();
        } catch (Exception e) {
            messages.add("401-Unauthorized");
            xmlString = apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, request.getContentType());
            return Response.serverError().type(request.getContentType()).entity(xmlString).build();
        }

        try {
            OracleCMKV2 oracleCMKV2 = oracleCmkV2Repository.findByUsernameAndPassword(username, password);

            if (oracleCMKV2 == null || oracleCMKV2.getWsdlScriptId() == null) {
                return Response.status(Response.Status.UNAUTHORIZED).type(MediaType.TEXT_XML).build();
            }

            InputStream downloadResource = resourceService.downloadResource(oracleCMKV2.getWsdlScriptId(), null);

            return Response.ok(IOUtils.toString(downloadResource)).header("Content-Type", "text/xml").build();

        } catch (Exception e) {
            logger.error("Oracle CMK V2, getWsdl failed Due to : " , e);
            return Response.serverError().entity("Something went Wrong").type(MediaType.TEXT_XML).build();
        }

    }

    @Override
    public Response postRecieveData(HttpServletRequest request) {

        logger.debug("Oracle CMK V2 receiveData : Received Message for posting data ");

        Transaction beginTransaction = null;
        List<String> messages = new ArrayList<String>();
        String xmlString = null;
        String username = null, password = null;
        String inputFileContent = null;
        String usernameAndPassword = null;
        String payload = null;

        List<QueueMessage> msgList = new ArrayList<>();

        try {
            // Extract payload of Oracle CMK Request
            payload = new String(FileUtil.getBytesFromFile(request.getInputStream()));

            usernameAndPassword = apiUtils.extractUsernameAndPasswordForResource(request, "Oracle CMK",
                    FileUtil.getInputStreamFromString(payload), null);
            final StringTokenizer tokenizer = new StringTokenizer(usernameAndPassword, ":");
            username = tokenizer.nextToken();
            password = tokenizer.nextToken();
        } catch (Exception e) {
            logger.error("Oracle CMK V2 receiveData: Failed to decode authcredentials IOException " , e);
            eventService.createEvent("Oracle CMK receiveData: Exception occurred " + e, EventStatus.FAIL, null,
                    beginTransaction);
            messages.add("401-Unauthorized");
            xmlString = apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, request.getContentType());
            return Response.serverError().type(request.getContentType()).entity(xmlString).build();
        }



        try {
            logger.debug("Oracle CMK V2 receiveData: Posting message to gateway");

            Connection connection = connectionDao.getOracleCmkV2ConnectionByUserNamePassword(username, password);
            if (connection != null) {
                beginTransaction=transactionService.beginTransaction();
                beginTransaction.setSrcConnection(connection);

                String fileName = null;
                // update the transaction with filename and upload the file to s3
                if (request.getHeader("filename") != null) {
                    fileName = request.getHeader("filename");
                    beginTransaction.setInboundFileName(fileName);
                } else
                    fileName = "Oracle CMK ServerReceived_" + System.nanoTime() + FileUtil.getRandom(3);

                HTTPHeaders httpHeaders = apiUtils.extractHeadersAndCreateEventForResource(request, "Oracle CMK", beginTransaction,"");
                String newResourceId = apiUtils.uploadFile("Oracle CMK", beginTransaction, payload);

                beginTransaction.setResourceId(newResourceId);
                // This method does not persist any data, just updates the transaction object
                transactionService.updateTransactionObject(connection, beginTransaction, true);
//                transactionService.updateTransactionFromResource(beginTransaction, TransactionStatus.IN_PROGRESS);

                if (connection.getAckBP() != null) {
                    msgList.add(apiUtils.createSyncAckMessage(newResourceId, connection));
                }
                String executeCustomFunction = null;
                // Handle Edit Sync Response
                if (connection.getCustomFunctionId() != null) {
                    executeCustomFunction = apiUtils.extractAndExecuteEditSyncForSoapOrOracle(request.getQueryString(), inputFileContent,
                            payload,connection, beginTransaction, true, httpHeaders);

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
                            "Oracle CMK V2 receiveData: Failed to send message to queue for transformation.Exception is: "
                                    + ex);
                    eventService.createEvent("Oracle CMK receiveData: Exception occured " + ex.getMessage(),
                            EventStatus.FAIL, newResourceId, beginTransaction);
                    messages.add("Internal Server Error: Failed to start transformation");
                    xmlString = apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, request.getContentType());
                    return Response.serverError().type(request.getContentType()).entity(xmlString).build();
                }
                eventService.createEvent("Data Sent To Gateway", EventStatus.STARTED_PROCESSING, newResourceId,
                        beginTransaction);

                if (executeCustomFunction != null) {
                    return Response.ok().type(httpHeaders.getHeaderMap().get("content-type")).entity(executeCustomFunction).build();
                } else {
                    return Response.ok().type(request.getContentType())
                            .entity(apiUtils.formResponse(ApiConstant.STATUS_SUCCESS, messages, httpHeaders.getHeaderMap().get("content-type"))).build();
                }

            } else {
                // connection is null
                messages.add("401-Unauthorized");
                return Response.status(Response.Status.UNAUTHORIZED).type(request.getContentType())
                        .entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, request.getContentType())).build();
            }
        } catch (Exception e) {
            // If exception occurs here, set the transaction as failed and remove any
            // downstream queue messages.
            beginTransaction.setTransactionStatus(TransactionStatus.FAILURE);
            msgList.clear();

            logger.error("Oracle CMK V2 receiveData: Request failed due to, " , e);

            eventService.createEvent("Oracle CMK receiveData: Request failed due to Internal Server Error",
                    EventStatus.FAIL, null, beginTransaction);

            messages.add("Oracle CMK receiveData: Request failed due to Internal Server Error : " + e.getMessage());
            return Response.serverError().type(request.getContentType())
                    .entity(apiUtils.formResponse(ApiConstant.STATUS_FAILURE, messages, request.getContentType())).build();
        }

    }
}
