package com.justransform.api_NEW.jaxws;



import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import org.apache.catalina.authenticator.BasicAuthenticator;

public class BasicAuthentication {

    private final Authenticator<BasicAuthenticator.BasicCredentials, ?> authenticator;
    private final String realm;

    public BasicAuthentication(Authenticator<BasicAuthenticator.BasicCredentials, ?> authenticator, String realm) {
        this.authenticator = authenticator;
        this.realm = realm;
    }

    public Authenticator<BasicAuthenticator.BasicCredentials, ?> getAuthenticator() {
        return this.authenticator;
    }

    public String getRealm() {
        return realm;
    }

}
