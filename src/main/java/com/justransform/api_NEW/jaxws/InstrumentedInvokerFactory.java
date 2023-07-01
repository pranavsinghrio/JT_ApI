package com.justransform.api_NEW.jaxws;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableMap;

import javax.xml.ws.spi.Invoker;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class InstrumentedInvokerFactory {

    private final MetricRegistry metricRegistry;

    /**
     * Factory method for TimedInvoker.
     */
    private Invoker timed(Invoker invoker, List<Method> timedMethods) {

        ImmutableMap.Builder<String, Timer> timers = new ImmutableMap.Builder<>();

        for (Method m : timedMethods) {
            Timed annotation = m.getAnnotation(Timed.class);
            final String name = chooseName(annotation.name(), annotation.absolute(), m);
            Timer timer = metricRegistry.timer(name);
            timers.put(m.getName(), timer);
        }

        return new InstrumentedInvokers.TimedInvoker(invoker, timers.build());
    }

    /**
     * Factory method for MeteredInvoker.
     */
    private Invoker metered(Invoker invoker, List<Method> meteredMethods) {

        ImmutableMap.Builder<String, Meter> meters = new ImmutableMap.Builder<>();

        for (Method m : meteredMethods) {
            Metered annotation = m.getAnnotation(Metered.class);
            final String name = chooseName(annotation.name(), annotation.absolute(), m);
            Meter meter = metricRegistry.meter(name);
            meters.put(m.getName(), meter);
        }

        return new InstrumentedInvokers.MeteredInvoker(invoker, meters.build());
    }

    /**
     * Factory method for ExceptionMeteredInvoker.
     */
    private Invoker exceptionMetered(Invoker invoker, List<Method> meteredMethods) {

        ImmutableMap.Builder<String, InstrumentedInvokers.ExceptionMeter> meters =
                new ImmutableMap.Builder<>();

        for (Method m : meteredMethods) {

            ExceptionMetered annotation = m.getAnnotation(ExceptionMetered.class);
            final String name = chooseName(
                    annotation.name(),
                    annotation.absolute(),
                    m,
                    ExceptionMetered.DEFAULT_NAME_SUFFIX);
            Meter meter = metricRegistry.meter(name);
            meters.put(m.getName(), new InstrumentedInvokers.ExceptionMeter(meter, annotation.cause()));
        }

        return new InstrumentedInvokers.ExceptionMeteredInvoker(invoker, meters.build());
    }

    /* Based on com.codahale.metrics.jersey.InstrumentedResourceMethodDispatchProvider#chooseName */
    private String chooseName(String explicitName, boolean absolute, Method method, String... suffixes) {
        if (explicitName != null && !explicitName.isEmpty()) {
            if (absolute) {
                return explicitName;
            }
            return metricRegistry.name(method.getDeclaringClass(), explicitName);
        }
        return metricRegistry.name(metricRegistry.name(method.getDeclaringClass(),
                        method.getName()),
                suffixes);
    }

    /**
     *
     * @param metricRegistry Metric registry.
     */
    public InstrumentedInvokerFactory(MetricRegistry metricRegistry) {
        this.metricRegistry =  metricRegistry;
    }


    /**
     * Factory method for creating instrumented invoker chain.
     */
    public Invoker create(Object service, Invoker rootInvoker) {

        List<Method> timedmethods = new ArrayList<>();
        List<Method> meteredmethods = new ArrayList<>();
        List<Method> exceptionmeteredmethods = new ArrayList<>();

        for (Method m : service.getClass().getMethods()) {

            if (m.isAnnotationPresent(Timed.class)) {
                timedmethods.add(m);
            }

            if (m.isAnnotationPresent(Metered.class)) {
                meteredmethods.add(m);
            }

            if (m.isAnnotationPresent(ExceptionMetered.class)) {
                exceptionmeteredmethods.add(m);
            }
        }

        Invoker invoker = rootInvoker;

        if (timedmethods.size() > 0) {
            invoker = this.timed(invoker, timedmethods);
        }

        if (meteredmethods.size() > 0) {
            invoker = this.metered(invoker, meteredmethods);
        }

        if (exceptionmeteredmethods.size() > 0) {
            invoker = this.exceptionMetered(invoker, exceptionmeteredmethods);
        }

        return invoker;
    }

}
