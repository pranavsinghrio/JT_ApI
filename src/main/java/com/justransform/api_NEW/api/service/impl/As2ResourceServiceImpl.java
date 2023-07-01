package com.justransform.api_NEW.api.service.impl;

import com.justransform.api_NEW.api.common.ApiUtils;
import com.justransform.api_NEW.api.service.As2ResourceService;
import com.justransform.app.base.services.ResourceService;
import com.justransform.dao.ConnectionDao;
import com.justransform.entity.Connection;
import com.justransform.entity.Transaction;
import com.justransform.entity.enums.EventStatus;
import com.justransform.entity.enums.TransactionStatus;
import com.justransform.exception.JTServerException;
import com.justransform.services.EventService;
import com.justransform.services.TransactionService;
import com.justransform.taskhandlers.messages.QueueMessage;
import com.justransform.utils.FileUtil;
import com.justransform.utils.JTLogger;
import com.justransform.utils.JsonUtils;
import com.justransform.utils.PropertyReaderUtil;
import com.rabbitmq.client.AMQP;
import inedi.As2receiver;
import inedi.Certificate;
import inedi.InEDIException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.net.InetAddress;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static java.nio.file.StandardOpenOption.*;

@Service
public class As2ResourceServiceImpl implements As2ResourceService {

    private static final String AS2_LOGS_DIR = PropertyReaderUtil.getInstance().getPropertyValue("as2.logs.dir");
    private static final String AS2_CERTIFCATE_PATH = PropertyReaderUtil.getInstance()
            .getPropertyValue("as2.certificate.path");
    private static final String AS2_CERTIFCATE_PASSWORD = PropertyReaderUtil.getInstance()
            .getPropertyValue("as2.certificate.password");
    private static final String AS2_CERTIFICATE_SUBJECT = PropertyReaderUtil.getInstance()
            .getPropertyValue("as2.certificate.subject");
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private ConnectionDao connectionDao;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private ResourceService resourceService;
    @Autowired
    private EventService eventService;
    @Autowired
    private ApiUtils apiUtils;

    @Override
    public org.apache.catalina.connector.Response getServerStatus() {
        return Response.ok().type(MediaType.TEXT_PLAIN).build();
    }

    @Override
    public Response postReceiveData(final HttpServletRequest request, final HttpServletResponse response) {
        Long transactionId = null;
        Transaction beginTransaction = null;
        try {

            String fileName = null;
            String as2From = "";
            String as2To = "";

            as2From = removeQuotes(request.getHeader("as2-from"));
            as2To = removeQuotes(request.getHeader("as2-to"));
            List<QueueMessage> msgList = new ArrayList<>();
            List<String> messages = new ArrayList<String>();

            Connection sourceConnection = null;
            try {
                sourceConnection = connectionDao.validateAs2ConnectionForResource(as2To, as2From);
                if (sourceConnection == null) {
                    logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "Invalid AS2 Ids: From " + as2From + ", To: " + as2To);
                    return Response.status(Response.Status.BAD_REQUEST).entity("Invalid AS2 Ids").build();
                } else {
                    beginTransaction = transactionService.beginTransaction();
                    transactionId = beginTransaction.getTransactionId();
                    beginTransaction.setSrcConnection(sourceConnection);
                    transactionService.updateTransactionFromResource(beginTransaction, TransactionStatus.IN_PROGRESS);
                }
            } catch (Exception e2) {
                logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "Unable to validate AS2 from "+ as2From + " connection due to  ", e2);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Unable to validate AS2 connection").build();
            }

            if (request != null) {
                // Extract any headers that came with the message and upload to S3 for UI
                // display
                String headerContent = extractHeaders(request);
                String rawHeaderResourceId = null;
                if (headerContent != null && headerContent.length() > 0) {
                    rawHeaderResourceId = resourceService
                            .uploadFileToS3WithoutDB(new ByteArrayInputStream(headerContent.getBytes()), true);
                    eventService.createEvent("AS2 HTTP headers", EventStatus.MSG_RECEIVED, rawHeaderResourceId,
                            beginTransaction);
                }

            }

            InputStream inputStream = null;
            File file = null;
            String headerResourceId = null;

            try {
                As2receiver as2 = new As2receiver();
                try {

                    as2.setLogDirectory(AS2_LOGS_DIR);

                    if (sourceConnection.getSubscriptionEmail() != null) {
                        if (sourceConnection.getSubscriptionEmail().equals("debug@justransform.com")) {
                            as2.config("LogDebug=true");
                        }
                    }
                    logger.debug(JTLogger.appendTransactionIdToLogs(transactionId) + "Started writing nsoftware logs to directory : " + AS2_LOGS_DIR);
                    if (sourceConnection.getAs2Config() != null && sourceConnection.getAs2Config().getEncryptData() != null
                            && sourceConnection.getAs2Config().getEncryptData().equalsIgnoreCase("true")) {
                        as2.config("RequireEncrypt=true");
                    }
                    if (sourceConnection.getAs2Config() != null && sourceConnection.getAs2Config().getSignData() != null
                            && sourceConnection.getAs2Config().getSignData().equalsIgnoreCase("true")) {
                        as2.config("RequireSign=true");
                    }
                } catch (InEDIException e) {
                    logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "InEDIException with code : " + e.getCode() + " Exception is ", e);
                    eventService.createEvent("Failed to consume received file, " + e, EventStatus.FAIL, null,
                            beginTransaction);
                    if (e.getCode() == 773) {
                        try {
                            response.sendError(406, e.getMessage());
                        } catch (IOException e1) {
                            logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "Failed to send Error response", e1);
                        }
                        return Response.status(Response.Status.NOT_ACCEPTABLE).entity(JsonUtils.getErrorJson(e.getMessage()))
                                .build();
                    } else {
                        logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "Returning internal server Error 500 ", e);
                        return Response.serverError().build();
                    }
                }
                try {
                    as2.readRequest(request);
                } catch (InEDIException ex) {
                    String errorMsg = null;
                    int eCode = ex.getCode();
                    errorMsg = getEMsgFromECode(eCode, ex.getMessage());
                    logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "Failed to read AS2 Request ", eCode + ": " + errorMsg);
                    eventService.createEvent("Reading AS2 Request for sender " + as2From + " :" + errorMsg,
                            EventStatus.FAIL, headerResourceId, beginTransaction);
                    return Response.status(Response.Status.BAD_REQUEST).build();
                }

                logger.debug(JTLogger.appendTransactionIdToLogs(transactionId) + "Read AS2 request succesfully");

                byte[] certFile = sourceConnection.getAs2Config().getCert();
                as2.setSignerCert(new Certificate(certFile));
                logger.debug(JTLogger.appendTransactionIdToLogs(transactionId) + "Set AS2 signing certificate succesfully");
                as2.setCertificate(new Certificate(Certificate.cstPFXFile, AS2_CERTIFCATE_PATH, AS2_CERTIFCATE_PASSWORD,
                        AS2_CERTIFICATE_SUBJECT));
                logger.debug(JTLogger.appendTransactionIdToLogs(transactionId) + "set AS2 certificate succesfully");

                try {
                    as2.processRequest();
                } catch (InEDIException e) {
                    String errorMsg = null;

                    int eCode = e.getCode();
                    errorMsg = getEMsgFromECode(eCode, e.getMessage());

                    logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "Failed to read AS2 Request: " + e.getMessage() + eCode + ": " + errorMsg + " ");
                    eventService.createEvent("Failure reading AS2 Request for sender " + as2From  + " due to :" + errorMsg + " - " + e.getMessage(),
                            EventStatus.FAIL, headerResourceId, beginTransaction);
                    transactionService.updateTransactionFromResource(beginTransaction,
                            TransactionStatus.FAILURE);
                    return Response.status(Response.Status.BAD_REQUEST).build();

                }

                if (as2.getEDIData().getName() != null) {
                    fileName = as2.getEDIData().getName();
                }

                // Update inbound file name in transaction
                if (fileName != null) {
                    beginTransaction.setInboundFileName(fileName);
                    try {
                        transactionService.updateTransactionFromResource(beginTransaction,
                                TransactionStatus.IN_PROGRESS);
                    } catch (Exception e) {
                        logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "Failed to update file name due to ", e);
                    }
                }

                logger.debug(JTLogger.appendTransactionIdToLogs(transactionId) + "process AS2 request successfully");

                logger.debug(JTLogger.appendTransactionIdToLogs(transactionId) + "process AS2 request successfully");

                inputStream = new ByteArrayInputStream(as2.getEDIData().getData());
                String newResourceId = null;
                try {
                    file = FileUtil.stream2file(inputStream, "as2" + System.nanoTime() + FileUtil.getRandom(3), null);
                    FileInputStream fileInputStream = new FileInputStream(file);
                    newResourceId = resourceService.uploadFileToS3WithoutDB(fileInputStream, true);
                    fileInputStream.close();
                    beginTransaction = transactionService.updateTransactionObject(sourceConnection, beginTransaction,
                            true);
                    // update transaction with Resource Details
                    beginTransaction.setResourceId(newResourceId);

                    eventService.createEvent("Received AS2 message from " + as2From, EventStatus.MSG_RECEIVED_AS2,
                            newResourceId, beginTransaction);
                } catch (Exception e) {
                    logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "Failed to upload received file in ecm ", e);
                    eventService.createEvent("Failed to upload received file in ecm ", EventStatus.FAIL, null,
                            beginTransaction);
                }


                try {
                    as2.sendResponse(response);
                } catch (InEDIException e) {
                    String errorMsg = null;
                    int eCode = e.getCode();
                    errorMsg = getEMsgFromECode(eCode, e.getMessage());
                    logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "Failed to Send AS2 MDN Receipt ", eCode + ": " + errorMsg);
                    eventService.createEvent("Failure sending AS2 MDN Receipt for sender " + as2From  + " due to:" + errorMsg + " - " + e.getMessage(),
                            EventStatus.WARNING, headerResourceId, beginTransaction);
                }

                createAs2FilesAndEvents(as2, beginTransaction);

                QueueMessage mt = apiUtils.createQueueMsgWithDetails(newResourceId, sourceConnection, beginTransaction);

                if (sourceConnection.getAckBP() != null) {
                    msgList.add(apiUtils.createSyncAckMessage(newResourceId, sourceConnection));
                }

                if (sourceConnection.getInboundQueueName() != null) {
                    mt.setQueueName(sourceConnection.getInboundQueueName());
                }

                QueueMessage tempMT = apiUtils.ifThrottlingEnabled(mt, sourceConnection, beginTransaction);
                if (tempMT != null)
                    msgList.add(tempMT);

                // Send messages to Queue for further processing
                try {
                    apiUtils.sendMsgsToQueue(msgList, sourceConnection);
                } catch (Exception ex) {
                    logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "Failed to send message to queue for transformation.Exception is: ", ex);
                    eventService.createEvent("Failed to Send File to route for transformation" + ex.getMessage(),
                            EventStatus.FAIL, null, beginTransaction);
                    return Response.serverError()
                            .entity(JsonUtils.getErrorJson("Failed to start transformation. Msg : " + ex.getMessage()))
                            .build();
                }

                eventService.createEvent("Sent Data for transformation to Route", EventStatus.SENT_MESSAGE,
                        newResourceId, beginTransaction);

                return Response.ok().build();

            } catch (InEDIException e) {
                logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "InEDIException with code : " + e.getCode() + " Exception is ", e);
                eventService.createEvent("Failed to consume received file, " + e, EventStatus.FAIL, null,
                        beginTransaction);
                if (e.getCode() == 773) {
                    try {
                        response.sendError(406, e.getMessage());
                    } catch (IOException e1) {
                        logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "Failed to send Error response", e1);
                    }
                    return javax.ws.rs.core.Response.status(Response.Status.NOT_ACCEPTABLE).entity(JsonUtils.getErrorJson(e.getMessage()))
                            .build();
                } else {
                    logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "Returning internal server Error 500 ", e);
                    return Response.serverError().build();
                }
            } catch (Exception e) {
                logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "AS2 Protocol Exception with message : " + e.getMessage());
                eventService.createEvent("Failed to consume received file due to: " + e.getMessage(), EventStatus.FAIL, null, beginTransaction);
                return Response.serverError().build();
            } finally {
                try {
                    if (inputStream != null)
                        inputStream.close();
                    if (file != null) {
                        boolean bDelete = file.delete();
                        if (!bDelete)
                            file.deleteOnExit();
                    }
                } catch (IOException e) {
                    logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "Error performing IO operations ", e);
                }
            }


        } catch (Exception e) {
            logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "AS2 Protocol Exception with message : " + e.getMessage() + " Exception is ", e);
            eventService.createEvent("Failed to consume received file due to: " + e.getMessage(), EventStatus.FAIL, null, beginTransaction);
            return Response.serverError().build();
        }
    }

    private void createAs2FilesAndEvents(As2receiver as2, Transaction beginTransaction) {
        try {
            String RequestedMICAlgorithms = "RequestedMICAlgorithms: " + as2.config("RequestedMICAlgorithms");
            String MICSenderSignatureAlgorithm = "MIC Value: " + as2.getMDNReceipt().getMICValue() + " SenderSignatureAlgorithm " + as2.config("SenderSignatureAlgorithm");
            String key = "";

            try {
                String ip = InetAddress.getLocalHost().getHostAddress();
                String mdnLogFileName = as2.getLogFile();
                String[] list = mdnLogFileName.split("/");
                key = ip.replaceAll("\\.", "_") + "_" + list[list.length - 1];
            } catch (Exception e) {
                /**
                 * Ignore any errors as this should not have any impact on the transaction processing.
                 */
            }

            logger.debug(RequestedMICAlgorithms);
            eventService.createEvent(RequestedMICAlgorithms + "; " + MICSenderSignatureAlgorithm, EventStatus.EMPTY_MSG, null, beginTransaction);

            logger.debug(MICSenderSignatureAlgorithm);

            File fileLog = new File(as2.getLogFile() + ".log");
            File fileReq = new File(as2.getLogFile() + ".req");
            File fileDat = new File(as2.getLogFile() + ".dat");
            File fileErr = new File(as2.getLogFile() + ".err");
            File fileMdn = new File(as2.getLogFile() + ".mdn");


            if (fileLog != null && fileLog.exists()) {
                logger.debug("Log: " + key + " : " + getFileContents(fileLog));
                eventService.createEvent("AS2 Log: " + key, EventStatus.EMPTY_MSG, getLogFromS3(fileLog), beginTransaction);
            }

            if (fileReq != null && fileReq.exists()) {
                logger.debug("Req:  " + key + getFileContents(fileLog));
                eventService.createEvent("AS2 Req: " + key, EventStatus.EMPTY_MSG, getLogFromS3(fileReq), beginTransaction);
            }

            if (fileDat != null && fileDat.exists()) {
                logger.debug("Data:  " + key + getFileContents(fileDat));
                eventService.createEvent("AS2 Data: " + key, EventStatus.EMPTY_MSG, getLogFromS3(fileDat), beginTransaction);
            }

            if (fileErr != null && fileErr.exists()) {
                logger.debug("Error:  " + key + getFileContents(fileErr));
                eventService.createEvent("AS2 Error: " + key, EventStatus.EMPTY_MSG, getLogFromS3(fileErr), beginTransaction);
            }

            if (fileMdn != null && fileMdn.exists()) {
                logger.debug("MDN:  " + key + getFileContents(fileMdn));
                eventService.createEvent("AS2 MDN: " + key, EventStatus.EMPTY_MSG, getLogFromS3(fileMdn), beginTransaction);

            }
        } catch (Exception e) {
            logger.error(JTLogger.appendTransactionIdToLogs(beginTransaction.getTransactionId()) + ": Error occurred while creating AS2 log file: ", e);
        }
    }

    private String getEMsgFromECode(int eCode, String exMsg) {

        String errorMsg = "";
        switch (eCode) {
            case 701:
                errorMsg = "Unable to write log file.";
                break;
            case 702:
                errorMsg = "Unable to read HTTP headers.";
                break;
            case 703:
                errorMsg = "Unable to read HTTP message body.";
                break;
            case 704:
                errorMsg = "No message to process.";
                break;
            case 705:
                errorMsg = "No response to send.";
                break;
            case 711:
                errorMsg = "The incoming message was encrypted with an unknown protocol.";
                break;
            case 712:
                errorMsg = "Unable to decrypt message.";
                break;
            case 713:
                errorMsg = "Unable to decompress message.";
                break;
            case 721:
                errorMsg = "An HTTP environment is required to perform this operation.";
                break;
            case 731:
                errorMsg = "*Unable to authenticate the sender.";
                break;
            case 732:
                errorMsg = "*Unable to verify content integrity.";
                break;
            case 733:
                errorMsg = "*Unsupported signature type was requested.";
                break;
            case 734:
                errorMsg = "*Unsupported MIC algorithm(s) were requested.";
                break;
            case 741:
                errorMsg = "You must specify an {@link inedi.As2receiver#getCertificate Certificate} .";
                break;
            case 751:
                errorMsg = "I/O error writing log file.";
                break;
            case 761:
                errorMsg = "Unable to mail asynchronous MDN.";
                break;
            case 762:
                errorMsg = "Unable to post asynchronous MDN.";
                break;
            case 801:
                errorMsg = "System error.";
                break;
            default:
                errorMsg = exMsg;
        }
        return errorMsg;
    }

    private void checkIfAs2TOAndFromExists(String as2To, String as2From, Long transactionId, String headerString) throws Exception {
        logger.debug(JTLogger.appendTransactionIdToLogs(transactionId) + "AS2 request received as2-from " + as2From + ", as2TO " + as2To +
                " Header : " + headerString);
        // Removed Quotes to prevent query failure, if as2Form or as2To have spaces in
        // between Sender adds Quotes to string

        if (as2From != null && as2To != null) {
            as2From = removeQuotes(as2From);
            as2To = removeQuotes(as2To);
        } else {
            logger.error(JTLogger.appendTransactionIdToLogs(transactionId) + "AS2 request failed because AS2 From & AS2To are null");
            throw new Exception("Invalid AS2 Ids");
        }
    }

    private String getFileContents(File logfile) throws IOException, JTServerException {
        return FileUtils.readFileToString(logfile, StandardCharsets.UTF_8);
    }

    private String getLogFromS3(File logfile) throws IOException, JTServerException {
        String headerResourceId = null;
        final File outputFile = File.createTempFile("JT_AS2_Log_" + System.nanoTime() + FileUtil.getRandom(3), ".req");

        try {
            Path outFile = Paths.get(outputFile.getPath());
            Path inFileLog = Paths.get(logfile.getPath());
            try (FileChannel in = FileChannel.open(inFileLog, READ);
                 FileChannel out = FileChannel.open(outFile, CREATE, WRITE)) {
                for (long p = 0, l = in.size(); p < l;)
                    p += in.transferTo(p, l - p, out);
            }
            headerResourceId = resourceService.uploadFileToS3WithoutDB(new FileInputStream(outputFile), true);
            if (outputFile != null)
                outputFile.delete();
        } catch (Exception e) {
            logger.error("Exception occurred while combining as2 log files: ", e);
        }
        return headerResourceId;
    }

    private String removeQuotes(String inputString) {
        if (inputString.charAt(0) == '"' && inputString.charAt(inputString.length() - 1) == '"') {
            return inputString.substring(1, inputString.length() - 1);
        }
        return inputString;
    }

    private String getHeaderString(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        String headerString = "";
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);

            headerString += headerName + ":" + headerValue + "\n";
        }

        headerString += "RequestURI : " + request.getRequestURI();

        return headerString;
    }

    private String extractHeaders(HttpServletRequest request) {
        if (request != null) {
            Enumeration<String> enums = request.getHeaderNames();
            if (enums != null) {
                StringBuilder headerContent = new StringBuilder();
                while (enums.hasMoreElements()) {
                    String enumKey = enums.nextElement();
                    Enumeration<String> headers = request.getHeaders(enumKey);
                    if (headers != null) {
                        while (headers.hasMoreElements())
                            headerContent.append(enumKey + ": " + headers.nextElement() + " \n");
                    }
                }
                return headerContent.toString();
            }
        }

        return null;
    }
}
