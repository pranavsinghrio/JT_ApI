package com.justransform.api_NEW.api.common;


import com.example.JustransformApplicationContext;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.justransform.entity.Connection;
import com.justransform.entity.Transaction;
import com.justransform.entity.enums.EventStatus;
import com.justransform.entity.enums.TransactionStatus;
import com.justransform.exception.JTServerException;
import com.justransform.services.EventService;
import com.justransform.services.QueueThrottlingService;
import com.justransform.services.ResourceService;
import com.justransform.services.TransactionService;
import com.justransform.taskhandlers.messages.QueueMessage;
import com.justransform.taskhandlers.transformer.QueueMessageFactory;
import com.justransform.transformer.common.groovyhelper.GroovyUtil;
import com.justransform.utils.QueueManagerUtils;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.soap.*;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.*;

@Component
public class ApiUtils {

    @Autowired
    private EventService eventService;
    @Autowired
    private ResourceService resourceService;
    @Autowired
    private QueueThrottlingService queuePriorityMsgService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private QueueManagerUtils queueManagerUtils;

    @Autowired
    private TransactionService transactionService;


    public String executeEditSyncCustomFunction(String customFunction, String queryString, String input, String body,
                                                Transaction beginTransaction, HTTPHeaders headers) throws ClassCastException, ScriptException, JTServerException {

        String cfResponse = GroovyUtil.executeCustomFunction(customFunction, queryString, headers.getHeaderMap(),
                beginTransaction.getTransactionId(), input, body, beginTransaction);

        if (cfResponse != null) {
            String uploadFileToS3WithoutDB = resourceService
                    .uploadFileToS3WithoutDB(new ByteArrayInputStream(cfResponse.getBytes()), true);
            eventService.createEvent("CustomFunction Executed Successfully for sync response", EventStatus.SUCCESS,
                    uploadFileToS3WithoutDB, beginTransaction);
        }

        return cfResponse;
    }

    public HTTPHeaders extractHeadersAndCreateEventForResource(HttpServletRequest request, String resourceName,
                                                               Transaction beginTransaction, String junction) {

        HTTPHeaders headerContent = extractHeaders(request);
        if(!junction.isEmpty()){
            headerContent.getHeaderMap().put("junction", junction);
        }
        try {
            String headerResourceId = null;
            if (headerContent != null && headerContent.getHeaderMap().size() > 0)
                headerResourceId = resourceService
                        .uploadFileToS3WithoutDB(new ByteArrayInputStream(headerContent.getHeaderString().getBytes()), true);
            eventService.createEvent(request.getMethod() + " "+ resourceName + " HTTP headers", EventStatus.EMPTY_MSG, headerResourceId,
                    beginTransaction);
        } catch (JTServerException e) {
            logger.error("Failed to upload " + resourceName + " http header content in ecm " , e);
        }
        logger.debug(resourceName + " received: " + headerContent.toString());
        return headerContent;
    }

    public HTTPHeaders extractHeaders(HttpServletRequest request) {
        HTTPHeaders httpHeaders = new HTTPHeaders();
        HashMap<String, String> headerMap = new HashMap<>();
        if (request != null) {
            Enumeration<String> enums = request.getHeaderNames();
            if (enums != null) {
                while (enums.hasMoreElements()) {
                    String enumKey = enums.nextElement();
                    Enumeration<String> headers = request.getHeaders(enumKey);
                    if (headers != null) {
                        while (headers.hasMoreElements()) {
                            String value = headers.nextElement();
                            headerMap.put(enumKey, value);
                        }
                    }
                }
            }
            if (request.getParameterMap() != null && request.getParameterMap().size() > 0) {
                headerMap.put("param", new Gson().toJson(request.getParameterMap()));
            }
            headerMap.put("method", request.getMethod());
            httpHeaders.setHeaderMap(headerMap);
            return httpHeaders;
        }

        return null;
    }

    public String uploadFile(String resourceName, Transaction transaction, String payload)
            throws JTServerException {
        // Upload payload to S3 for this transaction
        String newResourceId = resourceService.uploadFileToS3WithoutDB(FileUtil.getInputStreamFromString(payload), true);

        eventService.createEvent(resourceName + " message received", EventStatus.MSG_RECEIVED_REST, newResourceId, transaction);

        return newResourceId;
    }

    public String uploadFile(String resourceName, Transaction transaction, InputStream payload)
            throws JTServerException {
        // Upload payload to S3 for this transaction
        String newResourceId = resourceService.uploadFileToS3WithoutDB(payload, true);

        eventService.createEvent(resourceName + " message received", EventStatus.MSG_RECEIVED_REST, newResourceId, transaction);

        return newResourceId;
    }

    public String extractAndExecuteEditSyncForSoapOrOracle(String queryString, String inputFileContent, String payload, Connection connection,
                                                           Transaction beginTransaction, boolean isPayload, HTTPHeaders httpHeaders) throws ClassCastException, ScriptException, JTServerException {
        InputStream downloadResource = resourceService.downloadResource(connection.getCustomFunctionId(), connection.getCustomFunctionS3Version());
        String executeCustomFunction = null;
        if (downloadResource != null) {
            String customFunction = new String(FileUtil.getBytesFromFile(downloadResource));
            StringBuilder headerPlusInput = new StringBuilder();
            String input = null;

            if(isPayload){
                headerPlusInput.append(payload);
            } else {
                headerPlusInput.append(inputFileContent);
            }
            if (headerPlusInput != null) {
                input = headerPlusInput.toString();
            }
            executeCustomFunction = GroovyUtil.executeCustomFunction(customFunction, queryString, httpHeaders.getHeaderMap(),
                    beginTransaction.getTransactionId(), input, null, beginTransaction);

        }
        return executeCustomFunction;
    }


    public QueueMessage createSyncAckMessage(String newResourceId, Connection connection) {
        QueueMessageFactory messageFactory = JustransformApplicationContext.get().getQueueMessageFactory();
        QueueMessage ackMt = messageFactory.createSyncAckMessage(connection.getConnectionId(),PopulatePayloadMessage
                .populateRouteMessage(connection.getConnectionId(), -1l, newResourceId, 0l, null, -1l));
        ackMt.addAttribute("currentIndexValue", "0");
        ackMt.addAttribute("ack", "true");

        return ackMt;
    }

    public QueueMessage createQueueMsgWithDetails(String newResourceId, Connection connection,
                                                  Transaction transaction) {
        QueueMessageFactory messageFactory = JustransformApplicationContext.get().getQueueMessageFactory();
        QueueMessage mt = messageFactory.createRouteMessage(connection.getConnectionId(),PopulatePayloadMessage.populateRouteMessage(
                connection.getConnectionId(), transaction.getTransactionId(), newResourceId, 0l, null, -1l));
        mt.addAttribute("currentIndexValue", "0");
        mt.addAttribute(Constants.TRANSACTION_OBJ, JsonUtils.getJson(transaction));

        return mt;
    }

    private String getAuthFromSoapHeader(InputStream is) throws Exception {
        try {
            SOAPMessage soapMessage = MessageFactory.newInstance().createMessage(null, is);

            SOAPPart part = soapMessage.getSOAPPart();
            SOAPEnvelope env = part.getEnvelope();
            SOAPHeader soapHeader = env.getHeader();

            String wsseUsername = null;
            String wssePassword = null;
            String username = null;
            String password = null;

            if (soapHeader != null) {
                @SuppressWarnings("rawtypes")
                Iterator headerElements = soapHeader.examineAllHeaderElements();
                if (headerElements != null) {
                    while (headerElements.hasNext()) {
                        SOAPHeaderElement headerElement = (SOAPHeaderElement) headerElements.next();
                        if (headerElement.getElementName().getLocalName().equals("Security")) {
                            SOAPHeaderElement securityElement = headerElement;
                            Iterator<?> it2 = securityElement.getChildElements();
                            while (it2.hasNext()) {
                                Node soapNode = (Node) it2.next();
                                if (soapNode instanceof SOAPElement) {
                                    SOAPElement element = (SOAPElement) soapNode;
                                    wsseUsername = getFirstChildElementValue(element, ApiConstant.QNAME_WSSE_USERNAME);
                                    wssePassword = getFirstChildElementValue(element, ApiConstant.QNAME_WSSE_PASSWORD);
                                }
                                if (wsseUsername != null && wssePassword != null) {
                                    logger.debug("In SoapRequestHandler: started validating with SOAP Header, "
                                            + "for username & password " + wsseUsername + " " + wssePassword);
                                    username = wsseUsername;
                                    password = wssePassword;
                                    soapHeader.detachNode();
                                }
                            }
                        }
                    }
                }
            }
            return username + ":" + password;
        } catch (Exception e1) {
            logger.error("SOAP");
            e1.printStackTrace();
            throw new Exception();
        }
    }

    private String getFirstChildElementValue(SOAPElement soapElement, QName qNameToFind) {
        String value = null;
        Iterator<?> it = soapElement.getChildElements(qNameToFind);
        while (it.hasNext()) {
            SOAPElement element = (SOAPElement) it.next(); // use first
            value = element.getValue();
        }
        return value;
    }

    public String extractUsernameAndPasswordForResource(HttpServletRequest request, String resourceName,
                                                        InputStream file,Transaction beginTransaction) throws Exception {
        String authCredentials = null, username = null, password = null, usernameAndPassword = null;
        if (request != null) {

            if (request.getHeader("username") != null)
                username = request.getHeader("username");
            else if (username == null)
                username = request.getHeader("user");

            if (request.getHeader("password") != null)
                password = request.getHeader("password");

            if (request.getMethod().equalsIgnoreCase("get") && request.getParameter("username") != null && request.getParameter("password") != null) {
                username = request.getParameter("username");
                password = request.getParameter("password");
            }

            if (username != null && password != null) {
                return usernameAndPassword = username + ":" + password;
            }
            authCredentials = request.getHeader("authorization");
            if(authCredentials != null) {
                final String encodedUserPassword = authCredentials.replaceFirst("Basic", "").trim();
                try {
                    if (authCredentials.startsWith("Bearer")) {
                        String bearerToken = authCredentials.replaceFirst("Bearer", "").trim();
                        if (bearerToken.length() < 1) {
                            throw new Exception("Bearer token is not valid or null.");
                        }
                        return bearerToken;
                    }
                    byte[] decodedBytes = Base64.getDecoder().decode(encodedUserPassword);
                    return usernameAndPassword = new String(decodedBytes, "UTF-8");
                } catch (IOException e) {
                    logger.error(resourceName + " receiveData: Failed to decode authcredentials IOException ", e);
                    if (beginTransaction != null) {
                        //eventService.createEvent(resourceName + " receiveData: Exception occurred " + e, EventStatus.FAIL,
                        //	null, beginTransaction);
                    }
                    throw new Exception();
                }
            }

            if (username == null || password == null) {
                if (file != null) {
                    return usernameAndPassword = getAuthFromSoapHeader(file);
                } else {
                    throw new Exception();
                }
            }
        }
        throw new Exception();
    }

    public QueueMessage ifThrottlingEnabled(QueueMessage mt, Connection connection, Transaction beginTransaction) {
        if (JustransformApplicationContext.get().getEnableInboundThrottling() != null
                && JustransformApplicationContext.get().getEnableInboundThrottling().equalsIgnoreCase("true")) {
            try {
                mt.addAttribute(Constants.TRANSACTION_OBJ, JsonUtils.getJson(beginTransaction));
                queuePriorityMsgService.storeMessageToQueuePriority(mt,
                        connection.getResource().getCreator().getUserId(), connection.getConnectionId());
                return null;
            } catch (Exception e) {
                logger.error(
                        "Failed to save message in database.Directly putting message on queue.Exception occured " , e);
                return mt;
            }
        } else {
            return mt;
        }

    }

    public String formResponse(String statusType, List<String> messageDetails, String contentType) {

        DocumentBuilderFactory dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder;
        try {
            dBuilder = dbFactory.newDocumentBuilder();

            Document doc = dBuilder.newDocument();
            // root element
            Element rootElement = doc.createElement("jtresponse");
            doc.appendChild(rootElement);

            // status element
            Element status = doc.createElement("status");

            if (statusType.equals(ApiConstant.STATUS_SUCCESS))
                status.appendChild(doc.createTextNode(ApiConstant.STATUS_SUCCESS));
            else if (statusType.equals(ApiConstant.STATUS_FAILURE))
                status.appendChild(doc.createTextNode(ApiConstant.STATUS_FAILURE));
            else
                status.appendChild(doc.createTextNode(ApiConstant.STATUS_WARNING));

            rootElement.appendChild(status);

            if (messageDetails != null && messageDetails.size() > 0) {
                // messages element
                Element messages = doc.createElement("messages");
                rootElement.appendChild(messages);

                for (String msg : messageDetails) {
                    // message element
                    Element message = doc.createElement("message");
                    message.appendChild(doc.createTextNode(msg));
                    messages.appendChild(message);
                }
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance("com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl",null);;
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            transformer.transform(source, result);
            String xmlResponse = writer.toString();
            if (contentType != null && contentType.equals(MediaType.APPLICATION_JSON)) {
                JSONObject jsonResponse = XML.toJSONObject(xmlResponse); // converts xml to json
                return jsonResponse.toString();
            } else {
                return xmlResponse;
            }

        } catch (Exception e) {
            logger.error("Error in sending response " , e);
        }
        return null;

    }

    public String executeNewEditSyncCustomFunctionForPost(String customFunction, String queryString, Transaction transaction,
                                                          String input, HTTPHeaders httpHeaders) throws ClassCastException, ScriptException, JTServerException {
        String executeCustomFunction = GroovyUtil.executeCustomFunction(customFunction, queryString, httpHeaders.getHeaderMap(),
                transaction.getTransactionId(), input, null, transaction);
        if (executeCustomFunction != null) {
            String uploadFileToS3WithoutDB = resourceService
                    .uploadFileToS3WithoutDB(new ByteArrayInputStream(executeCustomFunction.getBytes()), true);
            eventService.createEvent("CustomFunction Executed Successfully for sync rest response",
                    EventStatus.SUCCESS, uploadFileToS3WithoutDB, transaction);
        }
        return executeCustomFunction;
    }

    public String executeEditSyncCustomFunctionForPost(String headerContent, String requestQueryString, String body,
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


            cfResponse = executeEditSyncCustomFunction(customFunction, requestQueryString, input, body,
                    beginTransaction, httpHeaders);
        }

        return cfResponse;
    }

    public String getContentTypeForResponse(HTTPHeaders httpHeaders, EditSyncResponse editSyncResponse) {
        String contentType = httpHeaders.getHeaderMap().get("content-type");
        if(!editSyncResponse.getContentType().isEmpty()) {
            contentType = editSyncResponse.getContentType();
        }
        return contentType;
    }

    public void sendMsgsToQueue(List<QueueMessage> msgList, Connection connection) {
        // Send messages to Queue for further processing
        //msgList.get(0).addAttribute("status","queued");
        String param = null;
        for (QueueMessage msg : msgList) {
            queueManagerUtils.sendMsgToQueue(msg, connection);
            param = msg.getPayload();
        }
        JsonObject obj = new Gson().fromJson(param, JsonObject.class);
        if (obj.get(Constants.TRANSACTION_ID) == null) return;
        Long transactionId = obj.get(Constants.TRANSACTION_ID).getAsLong();
        transactionService.updateTransactionStatus(transactionId, TransactionStatus.QUEUED);
    }
}
