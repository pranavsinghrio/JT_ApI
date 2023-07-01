package com.justransform.api_NEW;

import com.amazonaws.partitions.model.Endpoint;
import com.justransform.api_NEW.jaxws.JAXWSBundle;
import com.justransform.jaxws.EndpointBuilder;
import com.justransform.oracle.fulfillmentrequest.FulfillmentRequestImpl;
import com.justransform.soap.get.SyncResponseWebServiceImpl;
import com.justransform.soap.ns.JustransformSoapServiceService;
import com.justransform.soap.oracle.CollaborationMessagePortTypeImpl;
import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.endpoint.EndpointImpl;
import org.apache.cxf.transport.servlet.CXFServlet;
import org.glassfish.jersey.logging.LoggingFeature;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class ApplicationConfig {

    @Autowired
    JAXWSBundle javaBundle;

    @Bean
    public ServletRegistrationBean cxfServletRegistration() {
        ServletRegistrationBean registration = new ServletRegistrationBean(new CXFServlet(), "/soap/*");
        registration.setName(DispatcherServletAutoConfiguration.DEFAULT_DISPATCHER_SERVLET_REGISTRATION_BEAN_NAME);
        return registration;
    }

    @Bean(name= Bus.DEFAULT_BUS_ID)
    public SpringBus springBus(LoggingFeature loggingFeature) {

        SpringBus cxfBus = new  SpringBus();
        cxfBus.getFeatures().add(loggingFeature);

        return cxfBus;
    }

    @Bean
    public LoggingFeature loggingFeature() {

        LoggingFeature loggingFeature = new LoggingFeature();
        loggingFeature.setPrettyLogging(true);

        return loggingFeature;
    }

    @Bean
    public JAXWSBundle initJaxws(Bus bus){
        JAXWSBundle javaBundle = new JAXWSBundle();
        javaBundle.initialize(bus);
        return javaBundle;
    }

    @Bean(name = "receiveEndpoint")
    public Endpoint soapEndpoint(JustransformSoapServiceService justransformSoapServiceService) {

        EndpointImpl endpoint = javaBundle
                .publishEndpoint(new EndpointBuilder("/receive", justransformSoapServiceService).enableMtom());
        return endpoint;
    }

    @Bean(name = "collaborationMessagePortTypeEndpoint")
    public Endpoint collaborationEndpoint(CollaborationMessagePortTypeImpl collaborationMessagePortType) {
        EndpointImpl endpoint = javaBundle
                .publishEndpoint(new EndpointBuilder("/CollaborationMessageService", collaborationMessagePortType).enableMtom());
        return endpoint;
    }

    @Bean(name = "fulfillmentEndpoint")
    public Endpoint fulfillmentRequestEndpoint(FulfillmentRequestImpl fulfillmentRequest) {
        EndpointImpl endpoint = javaBundle
                .publishEndpoint(new EndpointBuilder("/fulfillment", fulfillmentRequest).enableMtom());
        return endpoint;
    }

    @Bean(name = "syncEndpoint")
    public Endpoint syncResponseWebServiceEndpoint(SyncResponseWebServiceImpl syncResponseWebService) {
        EndpointImpl endpoint = javaBundle
                .publishEndpoint(new EndpointBuilder("/sync", syncResponseWebService).enableMtom());
        return endpoint;
    }

//    @Bean
//    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory){
//        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
//        //adding perhaps some extra confoguration to template like message convertor. etc.
//        return rabbitTemplate;
//    }

}