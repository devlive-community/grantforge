package org.devlive.grantforge.security.enhancer;

import org.devlive.grantforge.common.enums.SystemMessageEnums;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class GrantForgeTokenEnhancer implements TokenEnhancer {

    @Override
    public OAuth2AccessToken enhance(OAuth2AccessToken oAuth2AccessToken, OAuth2Authentication oAuth2Authentication) {
        final Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("code", SystemMessageEnums.SYSTEM_SUCCESS.getCode());
        additionalInfo.put("message", SystemMessageEnums.SYSTEM_SUCCESS.getValue());
        additionalInfo.put("data", oAuth2AccessToken.getValue());
        ((DefaultOAuth2AccessToken) oAuth2AccessToken).setAdditionalInformation(additionalInfo);
        // rest oauth2 other data
        ((DefaultOAuth2AccessToken) oAuth2AccessToken).setTokenType(null);
        ((DefaultOAuth2AccessToken) oAuth2AccessToken).setScope(null);
        ((DefaultOAuth2AccessToken) oAuth2AccessToken).setExpiration(null);
        ((DefaultOAuth2AccessToken) oAuth2AccessToken).setValue(null);
        return oAuth2AccessToken;
    }
}