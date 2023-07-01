package com.justransform.api_NEW.api.common;
import javax.xml.namespace.QName;

public class ApiConstant {

    public static final String AUTHENTICATION_HEADER = "Authorization";
    public final static String STATUS_SUCCESS = "Success";
    public final static String STATUS_FAILURE = "Failure";
    public final static String STATUS_WARNING = "Warning";
    public static final String WSSE_NS_URI = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    public static final QName QNAME_WSSE_USERNAME = new QName(WSSE_NS_URI, "Username");
    public static final QName QNAME_WSSE_PASSWORD = new QName(WSSE_NS_URI, "Password");

    public static final String JUSTRANSFORM = "/justransform/v2";

}
