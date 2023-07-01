package com.justransform.api_NEW.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransactionAPIService {

    private TransactionService transactionService;

    private ProgramServiceImpl programServiceImpl;

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionAPIService.class);

    @PersistenceContext
    EntityManager em;

    public TransactionAPIService(TransactionService transactionService,ProgramServiceImpl programServiceImpl){
        this.transactionService = transactionService;
        this.programServiceImpl = programServiceImpl;
    }

    public Object getListOfTransactions(String scope, List<Long> ids, String status) throws Exception {
        List<Long> connectionIds = new ArrayList<>();
        try {
            if (scope.equals("connection")) {
                connectionIds.addAll(ids);
            } else if (scope.equals("program")) {
                connectionIds.addAll(programServiceImpl.getSharedConnectionIdsByProgramId(ids));
            }
            String transactionQuery = "SELECT " +
                    "    JSON_OBJECT(" +
                    "        'transactionId', t.transaction_id, " +
                    "        'endTime', t.end_time, " +
                    "        'inBpVersion', t.in_bp_version, " +
                    "        'outBpVersion', t.out_bp_version, " +
                    "        'startTime', t.start_time, " +
                    "        'transactionStatus', t.transaction_status, " +
                    "        'destConnectionId', dest_connection_id, " +
                    "        'destConnectionName', r1.name, " +
                    "        'inboundBpId', inbound_bp_id, " +
                    "        'inboundBpName', r2.name, " +
                    "        'outBoundBpId', outBound_bp_id, " +
                    "        'outBoundBpName', r3.name, " +
                    "        'srcConnectionId', src_connection_id, " +
                    "        'srcConnectionName', r.name, " +
                    "        'transactionIndexId', transaction_index_id, " +
                    "        'resourceId', resourceId, " +
                    "        'dataTypeIn', dataTypeIn, " +
                    "        'dataTypeOut', dataTypeOut, " +
                    "        'businessId1', businessId1, " +
                    "        'businessId2', businessId2, " +
                    "        'businessId3', businessId3, " +
                    "        'businessId4', businessId4, " +
                    "        'businessId5', businessId5, " +
                    "        'replayStatus', replayStatus, " +
                    "        'syncBpId', sync_bp_id, " +
                    "        'syncBpVersion', t.sync_bp_version, " +
                    "        'outboundFileName', outbound_file_name, " +
                    "        'inboundFileName', inbound_file_name, " +
                    "        'ackBpId', ack_bp_id, " +
                    "        'ackBpName', r4.name, " +
                    "        'ackBpVersion', t.ack_bp_version, " +
                    "        'events', (" +
                    "            SELECT JSON_ARRAYAGG(" +
                    "                JSON_OBJECT(" +
                    "                    'eventId', e.event_id, " +
                    "                    'eventDesc', e.event_desc, " +
                    "                    'eventType', e.event_type, " +
                    "                    'eventFilePath', e.event_file_path, " +
                    "                    'eventTimestamp', e.event_timestamp, " +
                    "                    'transactionId', e.transaction_id" +
                    "                )" +
                    "            ) " +
                    "            FROM event e " +
                    "            WHERE e.transaction_id = t.transaction_id " +
                    "            GROUP BY e.transaction_id" +
                    "        )" +
                    "    ) as transaction " +
                    "FROM " +
                    "    transaction t " +
                    "    JOIN connection c ON c.connection_id = t.src_connection_id " +
                    "    LEFT JOIN connection c2 ON t.dest_connection_id = c2.connection_id " +
                    "    JOIN resource r ON r.resource_id = c.resource_id " +
                    "    LEFT JOIN resource r1 ON r1.resource_id = c2.resource_id " +
                    "    LEFT JOIN resource r2 ON r2.resource_id = t.inbound_bp_id " +
                    "    LEFT JOIN resource r3 ON r3.resource_id = t.outBound_bp_id " +
                    "    LEFT JOIN resource r4 ON r4.resource_id = t.ack_bp_id " +
                    "WHERE ";
            if (status.contains(",")) {
                transactionQuery = transactionQuery + " t.transaction_status in ('IN_PROGRESS','QUEUED')";
            } else {
                transactionQuery = transactionQuery + "t.transaction_status = :status ";
            }

            transactionQuery = transactionQuery +
                    "    AND t.src_connection_id IN (:conIds)" +
                    "ORDER BY" +
                    "    t.transaction_id ASC";

            String connectionIdString = connectionIds.stream().map(String::valueOf).collect(Collectors.joining(","));

            Query query = em.createNativeQuery(transactionQuery)
                    .setParameter("conIds", connectionIdString);
            if (!status.contains(",")) {
                query.setParameter("status", status);
            }
            List<String> transactionsList = query.getResultList();
            String transactionsString = "[" + transactionsList.stream().collect(Collectors.joining(",")) + "]";
            return new JsonSlurper().parseText(transactionsString);
        }catch(Exception e){
            LOGGER.error("Error while fetching the in-progress transactions :"+e.getMessage());
            throw e;
        }
    }
}
