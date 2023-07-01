package com.justransform.api_NEW.api.service.impl;

import com.amazonaws.services.s3.AmazonS3Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.justransform.api_NEW.model.ConnectionMock;
import com.justransform.api_NEW.model.Document;
import com.justransform.api_NEW.model.ProgramData;
import com.justransform.app.base.services.ResourceService;
import com.justransform.common.vo.ConnectionVo;
import com.justransform.common.vo.ProgramDocumentVo;
import com.justransform.common.vo.ProgramVo;
import com.justransform.common.vo.ResourceVo;
import com.justransform.dao.AccessTypeDao;
import com.justransform.dao.ProgramDao;
import com.justransform.dao.UserDao;
import com.justransform.data.service.converter.EntityToVoConverter;
import com.justransform.data.service.converter.VoToEntityConverter;
import com.justransform.entity.Connection;
import com.justransform.entity.Program;
import com.justransform.entity.ProgramDocument;
import com.justransform.entity.User;
import com.justransform.entity.enums.ProgramUserAccessType;
import com.justransform.exception.JTServerException;
import com.justransform.services.ConnectionService;
import com.justransform.services.ProgramService;
import com.justransform.services.ScopeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProgramServiceImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgramServiceImpl.class);
    @Autowired
    private ProgramService programService;

    @Autowired
    private ProgramDao programDao;


    @Autowired
    private ResourceService resourceService;

    @Autowired
    private AccessTypeDao accessTypeDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private ConnectionService connectionService;

    @Autowired
    private ScopeService scopeService;

    public Map<String, Object> getProgramJson(long programId) throws JTServerException {
        Map<String, Object> programMap = new LinkedHashMap<>();
        ProgramVo programData = null;

        try {
            programData = programService.getProgramVoById(programId);
            if (programData == null) {
                return null;
            }

            List<Object> documents = getDocuments(programData);
            List<Object> connections = getConnections(programData);
            extractProgramInfo(programMap, programData);
            programMap.put("Documents", documents);
            programMap.put("Connections", connections);

        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            throw new JTServerException(e.getMessage());
        }
        return programMap;
    }

    private static void extractProgramInfo(Map<String, Object> mapData, ProgramVo programData) {
        mapData.put("ID", System.currentTimeMillis());
        mapData.put("Title", programData.getTitle());
        mapData.put("Description", programData.getDescription());
        mapData.put("Program Type", programData.getType());
        mapData.put("Status", programData.getStatus());
        mapData.put("Workflow", programData.getWorkflow());
        mapData.put("Logo", programData.getLogo());
        mapData.put("Start Date", programData.getStartDate());
        mapData.put("End Date", programData.getEndDate());
        mapData.put("Alert Mail", programData.getAlertEmail());
        mapData.put("creator", programData.getCreator().getEmailId());
    }

    private static List<Object> getConnections(ProgramVo programData) {
        List<Object> connectionsList = programData.getConnections().stream().map(connection -> {
            connection.setConnectionId(System.currentTimeMillis());
            if (connection.getResource() != null) {
                connection.getResource().setOwner(null);
                connection.getResource().setCreator(null);
                connection.getResource().setParent(null);
            }
            if (connection.getInboundBP() != null) {
                connection.getInboundBP().setResourceId(System.currentTimeMillis());
                connection.getInboundBP().setOwner(null);
                connection.getInboundBP().setCreator(null);
                connection.getInboundBP().setParent(null);
            }
            if (connection.getOutboundBP() != null) {
                connection.getOutboundBP().setResourceId(System.currentTimeMillis());
                connection.getOutboundBP().setOwner(null);
                connection.getOutboundBP().setCreator(null);
                connection.getOutboundBP().setParent(null);
            }
            if (connection.getResource() != null) {
                connection.getResource().setResourceId(System.currentTimeMillis());
            }
            return connection;
        }).map(e -> {
            ConnectionMock connectionMock = new ConnectionMock();
            connectionMock.setConnectionVo(e);
            return connectionMock;
        }).collect(Collectors.toList());
        return connectionsList;
    }

    private static List<Object> getDocuments(ProgramVo programData) {
        List<Object> documentsList = programData.getDocuments().stream().map(document -> {
            document.setProgramVo(null);
            document.getDocument().setResourceId(System.currentTimeMillis());
            document.getDocument().setOwner(null);
            document.getDocument().setCreator(null);
            document.getDocument().setParent(null);
            return document;
        }).collect(Collectors.toList());
        return documentsList;
    }

    public String saveProgram(ProgramData programJson) throws Exception {
        try {
            User user = userDao.getUserById(programJson.getUserId());
            String bucketName = "";
            AmazonS3Client amazonS3Client = null;
            boolean differentEnv = false;

            if (!(programJson.getSource().equals(System.getenv("JT_DB")))) {
                differentEnv = true;
                HashMap secretsMap = extractAwsSecrets(programJson.getSource(), programJson.getRegion());
                if (secretsMap == null) {
                    return "unable to fetch secret values";
                }
                String accessKey = secretsMap.get("awsAccessKey") != null ? secretsMap.get("awsAccessKey").toString() : "";
                String secretKey = secretsMap.get("awsSecret") != null ? secretsMap.get("awsSecret").toString() : "";
                bucketName = (secretsMap != null && secretsMap.get("bucketName") != null) ? secretsMap.get("bucketName").toString() : "";
                if (accessKey != "" && secretKey != "") {
                    amazonS3Client = retrieveSecretsAndConnectionAWS(accessKey, secretKey);
                } else {
                    LOGGER.error("accessKey and secretKey are empty");
                    return "provided wrong source and region";
                }
            }

            Program program = new Program();
            program.setProgramId(programJson.getProgramId());
            program.setTitle(programJson.getTitle());
            program.setDescription(programJson.getDescription());
            program.setType(programJson.getType());
            program.setStatus(programJson.getStatus());
            program.setWorkflowId(programJson.getWorkflowId());
            program.setLogo(programJson.getLogo());
            program.setStartDate(programJson.getStartDate());
            program.setEndDate(programJson.getEndDate());
            program.setAlertEmail(programJson.getAlertEmail());
            program.setCreator(user);
            program = programDao.createProgram(program);

            programDao.addProgramUser(program, user, null, ProgramUserAccessType.OWNER);
            JsonObject scopeDetails = new JsonObject();
            scopeDetails.addProperty("programId", program.getProgramId());
            scopeService.setScope(user.getEmailId(), scopeDetails.toString());

            ProgramVo programVo = EntityToVoConverter.getInstance().convertProgramToProgramVo(program, ConvertorActionType.FETCH_PROGRAM);
            //String jackRabbitId and long new environment resourceId
            Map<String, Long> documentMap = new HashMap<>();

            for (Document programDocument : programJson.getProgramDocuments()) {
                if (!documentMap.containsKey(programDocument.getDocumentData().getJackRabbitId())) {
                    ProgramDocumentVo programDocumentVo = new ProgramDocumentVo();
                    ResourceVo S3Resource = getNewResourceVo(programDocument.getDocumentData().getJackRabbitId(), null, user, programDocument.getDocumentData().getName(), programDocument.getDocumentData().getResourceType(), programJson.getDocumentResourceId(), differentEnv, amazonS3Client, bucketName);
                    programDocumentVo.setDocument(S3Resource);

                    programDocumentVo.setAccessLevel(programDocument.getAccessLevel());
                    programDocumentVo.setProgramVo(programVo);
                    programVo.getDocuments().add(programDocumentVo);
                    ProgramDocument programDocumentNew = VoToEntityConverter.getInstance().convertProgramDocumentVoToProgramDocument(programDocumentVo);
                    programDao.createProgramDocument(programDocumentNew);
                    documentMap.put(programDocument.getDocumentData().getJackRabbitId(), S3Resource.getResourceId());
                }
            }

            for (ConnectionMock connectionMock : programJson.getConnections()) {
                ConnectionVo connectionVo = connectionMock.getConnectionVo();
                Connection connection = VoToEntityConverter.getInstance().convertConnectionVoToConnection(connectionVo, null);
                connection.setConnectionId(null);
                if (connectionVo.getCustomFunctionId() != null)
                    connection.setCustomFunctionId(createDocumentJackRabbitId(connectionVo.getCustomFunctionId(), connectionVo.getCustomFunctionS3Version(), differentEnv, amazonS3Client, bucketName));
                else
                    connection.setCustomFunctionId(null);

                if (connectionVo.getobCustomFunctionId() != null)
                    connection.setObCustomFunctionId(createDocumentJackRabbitId(connectionVo.getobCustomFunctionId(), connectionVo.getObCustomFunctionS3Version(), differentEnv, amazonS3Client, bucketName));
                else
                    connection.setObCustomFunctionId(null);
                if (connectionVo.getInboundBP() != null)
                    connection.setInboundBP(createDocumentResource(connectionVo.getInboundBP(), user, programJson.getDocumentResourceId(), differentEnv, amazonS3Client, bucketName, documentMap));
                else
                    connection.setInboundBP(null);
                if (connectionVo.getOutboundBP() != null)
                    connection.setOutboundBP(createDocumentResource(connectionVo.getOutboundBP(), user, programJson.getDocumentResourceId(), differentEnv, amazonS3Client, bucketName, documentMap));
                else
                    connection.setOutboundBP(null);
                if (connectionVo.getSyncBP() != null)
                    connection.setSyncBP(createDocumentResource(connectionVo.getSyncBP(), user, programJson.getDocumentResourceId(), differentEnv, amazonS3Client, bucketName, documentMap));
                else
                    connection.setSyncBP(null);
                if (connectionVo.getAckBP() != null)
                    connection.setAckBP(createDocumentResource(connectionVo.getAckBP(), user, programJson.getDocumentResourceId(), differentEnv, amazonS3Client, bucketName, documentMap));
                else
                    connection.setAckBP(null);

                connection.setCustomFunctionS3Version(null);
                connection.setCustomFunctionVersion(null);
                connection.setObCustomFunctionS3Version(null);
                connection.setObCustomFunctionVersion(null);

                connection = connectionService.saveConnection(user, connection);

            }
            return "program created successfully";
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            throw new Exception(e.getMessage());
        }
    }

    private ResourceVo getNewResourceVo(String jackRabbitId, String version, User user, String documentName, ResourceType resouceType, long parentId, boolean differentEnv, AmazonS3Client amazonS3Client, String bucketName) throws JTServerException {
        InputStream fileContent;
        if (differentEnv) {
            fileContent = downloadResource(jackRabbitId, amazonS3Client, bucketName);
        } else {
            fileContent = resourceService.downloadResource(jackRabbitId, version);
        }
        ResourceVo S3Resource = resourceService.createResourceInS3(user.getEmailId(), fileContent, documentName, resouceType, parentId, false);
        return S3Resource;
    }

    private Resource createDocumentResource(ResourceVo resourceVo, User user, long parentId, boolean differentEnv, AmazonS3Client amazonS3Client, String bucketName,Map<String, Long> documentMap) throws JTServerException {
        if(!documentMap.containsKey(resourceVo.getJackRabbitId())){
            ResourceVo newResourceVo = getNewResourceVo(resourceVo.getJackRabbitId(), null, user, resourceVo.getName(), resourceVo.getResourceType(), parentId, differentEnv, amazonS3Client, bucketName);
            documentMap.put(resourceVo.getJackRabbitId(),newResourceVo.getResourceId());
            return VoToEntityConverter.getInstance().convertResourceVoToResource(newResourceVo, null);
        }else {
            return resourceService.getResource(documentMap.get(resourceVo.getJackRabbitId()));
        }
    }

    private String createDocumentJackRabbitId(String jackRabbitId, String version, boolean differentEnv, AmazonS3Client amazonS3Client, String bucketName) throws JTServerException {
        InputStream fileContent;
        if (differentEnv) {
            fileContent = downloadResource(jackRabbitId, amazonS3Client, bucketName);
        } else {
            fileContent = resourceService.downloadResource(jackRabbitId, version);
        }
        return resourceService.uploadFileToS3WithoutDB(fileContent, false);
    }

    private AmazonS3Client retrieveSecretsAndConnectionAWS(String accessKey, String secretKey) {
        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
        return new AmazonS3Client(credentials);
    }

    private HashMap extractAwsSecrets(String environment, String region) {
        GetSecretValueResult secretValueResult = null;
        try {
            LOGGER.info("Entering getSecret ");

            AWSSecretsManagerClientBuilder clientBuilder = AWSSecretsManagerClientBuilder.standard();
            if (region != null) {
                clientBuilder = AWSSecretsManagerClientBuilder.standard().withRegion(region);
            }
            AWSSecretsManager client = clientBuilder.build();
            GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest().withSecretId(environment);
            try {
                secretValueResult = client.getSecretValue(getSecretValueRequest);

            } catch (ResourceNotFoundException e) {
                LOGGER.error("The requested secret " + environment + " was not found");
            } catch (InvalidRequestException e) {
                LOGGER.error("The request was invalid due to: " + e.getMessage());
            } catch (InvalidParameterException e) {
                LOGGER.error("The request had invalid params: " + e.getMessage());
            }
            LOGGER.info("Exiting getSecret ");
            if (secretValueResult == null) {
                LOGGER.info("No AWS Secret's found");
                return null;
            }
            if (secretValueResult.getSecretString() != null) {
                String secret = secretValueResult.getSecretString();
                ObjectMapper objectMapper = new ObjectMapper();
                HashMap secretMap = objectMapper.readValue(secret, HashMap.class);
                return secretMap;
            }
        } catch (Exception e) {
            LOGGER.error("Error while retriving the secret values for program api :" + e.getMessage());
        }
        return null;
    }

    private InputStream downloadResource(String jackRabbitId, AmazonS3Client amazonS3Client, String bucketName) throws JTServerException {
        try {
            S3ObjectId s3ObjectId = new S3ObjectId(bucketName, jackRabbitId);
            GetObjectRequest getObjectRequest = new GetObjectRequest(s3ObjectId);
            S3Object object = amazonS3Client.getObject(getObjectRequest);
            FileInputStream downloadStream = null;
            FileOutputStream fos = null;
            File file = null;
            S3ObjectInputStream objectContent = null;
            try {
                file = File.createTempFile("s3Download" + System.nanoTime() + FileUtil.getRandom(3), null);
                objectContent = object.getObjectContent();
                fos = new FileOutputStream(file);
                IOUtils.copy(objectContent, fos);

                downloadStream = new FileInputStream(file);
                byte[] data = new byte[downloadStream.available()];
                downloadStream.read(data);

                ByteArrayInputStream stream = new ByteArrayInputStream(data);
                LOGGER.info("In downloadResource: After IOUtils.copy " + System.nanoTime() + FileUtil.getRandom(3));
                return stream;
            } catch (IOException e) {
                LOGGER.error("Error Message: downloadResourceFromS3, on getting stream, " + e.getMessage());
                throw new JTServerException("Failed while downloading file from s3 "+e.getMessage());
            } finally {
                try {
                    if (object != null) object.close();
                    if (objectContent != null) objectContent.close();
                    if (fos != null) fos.close();
                    if (downloadStream != null) downloadStream.close();
                    if (file != null) {
                        boolean bDelete = file.delete();
                        if (!bDelete) file.deleteOnExit();
                    }
                } catch (IOException e) {
                    LOGGER.error("Error Message: downloadResourceFromS3, on closing IO object, " + e.getMessage());
                    throw new JTServerException("Failed while downloading file from s3 ", e.getMessage());
                }
            }
        } catch (AmazonServiceException ase) {
            LOGGER.error("Caught an AmazonServiceException, which " + "means your request made it " + "to Amazon S3, but was rejected with an error response" + " for some reason. " + ase);
            LOGGER.error("Error Message:    " + ase.getMessage());
            LOGGER.error("HTTP Status Code: " + ase.getStatusCode());
            LOGGER.error("AWS Error Code:   " + ase.getErrorCode());
            LOGGER.error("Error Type:       " + ase.getErrorType());
            LOGGER.error("Request ID:       " + ase.getRequestId());
            throw new JTServerException("Failed while downloading file from s3 ", ase.getMessage());
        } catch (AmazonClientException ace) {
            LOGGER.error("Caught an AmazonClientException, which " + "means the client encountered " + "an internal error while trying to " + "communicate with S3, " + "such as not being able to access the network. " + ace);
            LOGGER.error("Error Message: " + ace.getMessage());
            throw new JTServerException("Failed while downloading file from s3 ", ace.getMessage());
        }
    }

    public Set<Long> getSharedConnectionIdsByProgramId(List<Long> programIds) throws Exception {
        Set<Long> programsSharedConnectionId = new HashSet<>();
        try {
            for (Long programId : programIds) {
                Set<Long> connectionId = programService.getProgramVoById(programId).getConnections().stream().map(connectionVo -> connectionVo.getConnectionId()).collect(Collectors.toSet());
                programsSharedConnectionId.addAll(connectionId);
            }
        }catch (Exception e) {
            LOGGER.error(e.getMessage());
            throw new Exception(e.getMessage());
        }
        return programsSharedConnectionId;
    }
}
