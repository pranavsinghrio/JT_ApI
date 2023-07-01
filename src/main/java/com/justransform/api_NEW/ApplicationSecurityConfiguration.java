package com.justransform.api_NEW;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.CacheControlHeadersWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.test.web.client.RequestMatcher;

@Configuration
@EnableWebSecurity
public class ApplicationSecurityConfiguration extends WebSecurityConfigurerAdapter {
    private static final String[] AUTH_WHITELIST = {
            // -- Swagger UI v3
            "/v3/api-docs/**",
            "v3/api-docs/**",
            "/api/swagger-ui/**",
            "api/swagger-ui/**",
            "api/swagger-ui.html/**",
            "/api/swagger-ui.html/**",
            // CSA Controllers
            "/csa/api/token",
            // Actuators
            "/actuator/**",
            "/health/**",
            //sample
            "/sample/**",
            "sample/**",
            //Protocol API
//			"/justransform/**",
//			"justransform/**",
            "/justransform/as2/**",
            "/justransform/rest/**",
            "/justransform/CollaborationMessageService/**",
            "/justransform/otm/**",
            "/justransform/rest/**",
            "/justransform/restredirect/receive/**",
            "/justransform/restrouter/receive/**",
            "/justransform/soapserver/**",
            "/justransform/workflow/rest/**",
    };

    @Override
    public void configure(HttpSecurity http) throws Exception {
        RequestMatcher notResourcesMatcher = new NegatedRequestMatcher(new AntPathRequestMatcher("/justransform/**"));
        HeaderWriter notResourcesHeaderWriter = new DelegatingRequestMatcherHeaderWriter(notResourcesMatcher , new CacheControlHeadersWriter());

        http.csrf().disable();
        http.headers().frameOptions().disable();
        http.headers().contentTypeOptions().disable();
        http.headers().xssProtection(xXssConfig -> xXssConfig.block(false).disable());
        http.headers().cacheControl().disable();
        http.headers().addHeaderWriter(notResourcesHeaderWriter);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeRequests(auth -> auth
                        .antMatchers(AUTH_WHITELIST).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer().jwt();
    }

    @Bean
    public UserDetailsService userDetailsServiceBean() throws Exception {
        return super.userDetailsServiceBean();
    }

    @Bean
    public CognitoIdentityProviderClient cognitoClient() {
        DefaultCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();

        String region = PropertyReaderUtil.getInstance().getPropertyValue("aws.oauthRegion");
        CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
        return cognitoClient;
    }
}