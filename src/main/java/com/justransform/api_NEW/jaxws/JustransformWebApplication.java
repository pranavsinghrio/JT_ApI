package com.justransform.api_NEW.jaxws;

import com.justransform.ApplicationProfiles;
import com.justransform.JustransformApplicationContext;
import com.justransform.JustransformWebConfiguration;
import com.justransform.client.impl.B2BPortalClient;
import com.justransform.client.impl.ResourceUtil;
import com.justransform.dao.*;
import com.justransform.rabbitmq.configuration.RabbitMQUtilsCommon;
import com.justransform.services.*;
import com.justransform.taskhandlers.MessageHandler;
import com.justransform.utils.PropertyReaderUtil;
import com.justransform.utils.QueueManagerUtils;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

@Component
@Profile(ApplicationProfiles.WEB)
public class JustransformWebApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(JustransformWebApplication.class);

    private static final String APP_NAME = "justransform-services";
    public static final String DEFAULT_CONFIG_FILE = "x_aws.properties";

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ConnectionDao connectionDao;

    @Autowired
    private TransactionDao transactionDao;

    @Autowired
    private B2BPortalClient b2BPortalClient;

    @Autowired
    private QuartzJobsDetailDao quartzJobsDetailDao;

    @Autowired
    private EdiDashboardDao ediDashboardDao;

    @Autowired
    private JustransformWebConfiguration justransformWebConfiguration;

    @Autowired
    private JTQueueService queueService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private PropertyReaderUtil propertyReaderUtil;

    @Autowired
    private EventService eventService;

    @Autowired
    private JTConnectionService jtConnectionService;

    @Autowired
    private QueueThrottlingService queuePriorityMsgService;

    @Autowired
    private ScheduleJobDao scheduleJobDao;

    @Autowired
    private JTQueueService jtQueueService;

    @Autowired
    private QueueManagerUtils queueManagerUtils;

    @Autowired
    private RabbitMqRejectedMsgService rabbitMqRejectedMsgService;

    @Autowired
    RabbitMQUtilsCommon rabbitMQUtilsCommon;

//	@Autowired
//	ConnectionTableService connectionTableService;
//
//	@Autowired
//	RESTConnectionService restConnectionService;

    private SessionFactory getSessionFactory() {
        return entityManagerFactory.unwrap(SessionFactory.class);
    }
//	public static void main(String[] args) {
//		SpringApplication.run(JustransformWebApplication.class, args);
//	}

    public String getName() {
        String hostName;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
            ResourceUtil.setHostName(hostName);
        } catch (UnknownHostException uhe) {
            hostName = "localhost";
        }
        LOGGER.info("Application name is " + APP_NAME + "-" + hostName);
        return APP_NAME + "-" + hostName;
    }


    @PostConstruct
    public void initialize() {
//		bootstrap.addBundle(new VaadinBundle(JustransformServlet.class, "/justransform-app/*"));
//		bootstrap.addBundle(hibernate);
//		bootstrap.addBundle(new MigrationsBundle<JustransformWebConfiguration>() {
//			public DataSourceFactory getDataSourceFactory(JustransformWebConfiguration configuration) {
//				return configuration.getDataSourceFactory();
//			}
//		});
//		bootstrap.addBundle(jaxWsBundle);
        getName();
        try {
            LOGGER.info("Running Web Configuration");
            run();//JustransformWebConfiguration configuration
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    public void run() throws Exception {


        HashMap<String, Object> handlerServicesDAOMap = new HashMap<>();
        handlerServicesDAOMap.put(EdiDashboardDao.class.getName(), ediDashboardDao);
        handlerServicesDAOMap.put(ConnectionDao.class.getName(), connectionDao);
        handlerServicesDAOMap.put(TransactionDao.class.getName(), transactionDao);
        handlerServicesDAOMap.put(TransactionService.class.getName(), transactionService);
        handlerServicesDAOMap.put(JTQueueService.class.getName(), queueService);
        handlerServicesDAOMap.put(ScheduleJobDao.class.getName(), scheduleJobDao);
        handlerServicesDAOMap.put(JTQueueService.class.getName(), jtQueueService);
        handlerServicesDAOMap.put(JTConnectionService.class.getName(), jtConnectionService);
        handlerServicesDAOMap.put(QueueManagerUtils.class.getName(), queueManagerUtils);
        handlerServicesDAOMap.put(RabbitMqRejectedMsgService.class.getName(), rabbitMqRejectedMsgService);

//		handlerServicesDAOMap.put(RESTConnectionService.class.getName(), restConnectionService);
//		handlerServicesDAOMap.put(ConnectionTableService.class.getName(), connectionTableService);

//
        final Map<String, Class<? extends MessageHandler>> handlers = new HashMap<>();
        boolean runListeners = Boolean.parseBoolean(propertyReaderUtil.getPropertyValue("apps.runlisteners"));
        JustransformApplicationContext.init(justransformWebConfiguration, handlers, getSessionFactory(), handlerServicesDAOMap,
                null, runListeners);

//		fanout exchange-queue-consumer creation

        boolean isConCachingEnabled = Boolean.parseBoolean(PropertyReaderUtil.getInstance().getPropertyValue("isConCachingEnabled"));
        if(isConCachingEnabled){
            rabbitMQUtilsCommon.createFanoutExchangeAndQueue();
            rabbitMQUtilsCommon.startFanoutContainer();
        }

//
//		// publish SOAP endpoints
//		jaxWsBundle
//				.publishEndpoint(new EndpointBuilder("/receive", new JustransformSoapServiceService(transactionService,
//						eventService, connectionDao, resourceService, queuePriorityMsgService)).enableMtom());
//		jaxWsBundle.publishEndpoint(new EndpointBuilder("/CollaborationMessageService",
//				new CollaborationMessagePortTypeImpl(transactionService, eventService, connectionDao, resourceService,
//						queuePriorityMsgService))
//						.enableMtom());
//
//		jaxWsBundle.publishEndpoint(new EndpointBuilder("/fulfillment", new FulfillmentRequestImpl(transactionService,
//				eventService, connectionDao, resourceService, queuePriorityMsgService)).enableMtom());
//
//		jaxWsBundle.publishEndpoint(new EndpointBuilder("/sync",
//				new SyncResponseWebServiceImpl(transactionService, eventService, connectionDao, resourceService))
//						.enableMtom());
//
    }
}