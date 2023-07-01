package com.justransform.api_NEW.jaxws;
import com.google.common.collect.ImmutableList;
import com.justransform.jaxws.AbstractBuilder;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.message.Message;

import javax.xml.ws.handler.Handler;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * JAX-WS/CXF client builder.
 */
public class ClientBuilder<T> extends AbstractBuilder {

    Class<T> serviceClass;
    String address;
    private int connectTimeout = 500;
    private int receiveTimeout = 2000;
    ImmutableList<Handler> handlers;

    public Class<T> getServiceClass() {
        return serviceClass;
    }

    public String getAddress() {
        return address;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getReceiveTimeout() {
        return receiveTimeout;
    }

    public ImmutableList<Handler> getHandlers() {
        return handlers;
    }

    /**
     * Create new ClientBuilder. Endpoint will be published relative to the CXF servlet path.
     * @param serviceClass Service interface class..
     * @param address Endpoint URL address..
     */
    public ClientBuilder(Class<T> serviceClass, String address) {
        checkArgument(serviceClass != null, "ServiceClass is null");
        checkArgument(address != null, "Address is null");
        checkArgument((address).trim().length() > 0, "Address is empty");
        this.serviceClass = serviceClass;
        this.address = address;
    }

    /**
     * Change default HTTP client connect timeout.
     * @param value Timeout value in milliseconds.
     * @return ClientBuilder instance.
     */
    public com.justransform.jaxws.ClientBuilder<T> connectTimeout(int value) {
        this.connectTimeout = value;
        return this;
    }

    /**
     * Change default HTTP client receive timeout.
     * @param value Timeout value in milliseconds.
     * @return ClientBuilder instance.
     */
    public com.justransform.jaxws.ClientBuilder<T> receiveTimeout(int value) {
        this.receiveTimeout = value;
        return this;
    }

    /**
     * Add client side JAX-WS handlers.
     * @param handlers JAX-WS handlers.
     * @return ClientBuilder instance.
     */
    public com.justransform.jaxws.ClientBuilder<T> handlers(Handler... handlers) {
        this.handlers = ImmutableList.<Handler>builder().add(handlers).build();
        return this;
    }

    @Override
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final com.justransform.jaxws.ClientBuilder<T> cxfInInterceptors(Interceptor<? extends Message>... interceptors) {
        return (com.justransform.jaxws.ClientBuilder<T>)super.cxfInInterceptors(interceptors);
    }

    @Override
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final com.justransform.jaxws.ClientBuilder<T> cxfInFaultInterceptors(Interceptor<? extends Message>... interceptors) {
        return (com.justransform.jaxws.ClientBuilder<T>)super.cxfInFaultInterceptors(interceptors);
    }

    @Override
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final com.justransform.jaxws.ClientBuilder<T> cxfOutInterceptors(Interceptor<? extends Message>... interceptors) {
        return (com.justransform.jaxws.ClientBuilder<T>)super.cxfOutInterceptors(interceptors);
    }

    @Override
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final com.justransform.jaxws.ClientBuilder<T> cxfOutFaultInterceptors(Interceptor<? extends Message>... interceptors) {
        return (com.justransform.jaxws.ClientBuilder<T>)super.cxfOutFaultInterceptors(interceptors);
    }

    @Override
    public com.justransform.jaxws.ClientBuilder<T> enableMtom() {
        return (com.justransform.jaxws.ClientBuilder<T>)super.enableMtom();
    }
}
