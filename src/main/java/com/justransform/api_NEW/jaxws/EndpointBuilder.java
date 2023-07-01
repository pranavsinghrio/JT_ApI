package com.justransform.api_NEW.jaxws;

import com.justransform.jaxws.AbstractBuilder;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.message.Message;
import org.hibernate.SessionFactory;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * JAX-WS/CXF server endpoint builder.
 */
public class EndpointBuilder extends AbstractBuilder {

    private String path;
    private Object service;
    SessionFactory sessionFactory;
    BasicAuthentication authentication;

    public String getPath() {
        return path;
    }

    public Object getService() {
        return service;
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public BasicAuthentication getAuthentication() {
        return authentication;
    }

    /**
     * Create new EndpointBuilder. Endpoint will be published relative to the CXF servlet path.
     * @param path Relative endpoint path.
     * @param service Service implementation.
     */
    public EndpointBuilder(String path, Object service) {
        checkArgument(service != null, "Service is null");
        checkArgument(path != null, "Path is null");
        checkArgument((path).trim().length() > 0, "Path is empty");
        if (!path.startsWith("local:")) { // local transport is used in tests
            path = (path.startsWith("/")) ? path : "/" + path;
        }
        this.path = path;
        this.service = service;
    }

    /**
     * Publish JAX-WS endpoint with Dropwizard Hibernate Bundle integration.
     * Service will be scanned for @UnitOfWork annotations.
     * @param sessionFactory Hibernate session factory.
     */
    public com.justransform.jaxws.EndpointBuilder sessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
        return this;
    }

    /**
     * Publish JAX-WS protected endpoint using Dropwizard BasicAuthentication.
     * @param authentication BasicAuthentication implementation.
     */
    public com.justransform.jaxws.EndpointBuilder authentication(BasicAuthentication authentication) {
        this.authentication = authentication;
        return this;
    }

    @Override
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final com.justransform.jaxws.EndpointBuilder cxfInInterceptors(Interceptor<? extends Message>... interceptors) {
        return (com.justransform.jaxws.EndpointBuilder)super.cxfInInterceptors(interceptors);
    }

    @Override
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final com.justransform.jaxws.EndpointBuilder cxfInFaultInterceptors(Interceptor<? extends Message>... interceptors) {
        return (com.justransform.jaxws.EndpointBuilder)super.cxfInFaultInterceptors(interceptors);
    }

    @Override
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final com.justransform.jaxws.EndpointBuilder cxfOutInterceptors(Interceptor<? extends Message>... interceptors) {
        return (com.justransform.jaxws.EndpointBuilder)super.cxfOutInterceptors(interceptors);
    }

    @Override
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final com.justransform.jaxws.EndpointBuilder cxfOutFaultInterceptors(Interceptor<? extends Message>... interceptors) {
        return (com.justransform.jaxws.EndpointBuilder)super.cxfOutFaultInterceptors(interceptors);
    }

    /**
     * Invoking enableMTOM is not necessary if you use @MTOM JAX-WS annotation on your service implementation class.
     * @return
     */
    @Override
    public com.justransform.jaxws.EndpointBuilder enableMtom() {
        return (com.justransform.jaxws.EndpointBuilder)super.enableMtom();
    }
}
