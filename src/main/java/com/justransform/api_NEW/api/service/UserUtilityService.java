package com.justransform.api_NEW.api.service;

import com.justransform.api_NEW.ApplicationSecurityConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.neo4j.Neo4jProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserUtilityService {

    @Autowired
    ApplicationSecurityConfiguration applicationSecurityConfiguration;

    public Map<String, Object> getUserAttributes() {
        Map<String, Object> userMap = new HashMap<>();
        Neo4jProperties.Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String token = ((OAuth2ResourceServerProperties.Jwt) ((JwtAuthenticationToken) authentication).getPrincipal()).getTokenValue();

        GetUserRequest getUserRequest = GetUserRequest.builder()
                .accessToken(token)
                .build();
        CognitoIdentityProviderClient cognitoClient = applicationSecurityConfiguration.cognitoClient();

        GetUserResponse getUserResponse = cognitoClient.getUser(getUserRequest);
        List<AttributeType> attributes = getUserResponse.userAttributes();

        for (AttributeType attribute : attributes) {
            userMap.put(attribute.name(), attribute.value());
        }
        return userMap;
    }

    public boolean validateById(Long enterpriseId) {
        Map<String, Object> userAttributes = getUserAttributes();
        if ((userAttributes.get("profile") != null) && (userAttributes.get("profile").equals("1") || userAttributes.get("profile").equals(enterpriseId.toString()))) {
            return true;
        } else {
            return false;
        }
    }

    public boolean validateByDomain(String domain) {
        Map<String, Object> userAttributes = getUserAttributes();
        if ((userAttributes.get("name") != null) && (userAttributes.get("name").equals("justransform.com") || userAttributes.get("name").equals(domain))) {
            return true;
        } else {
            return false;
        }
    }
}
