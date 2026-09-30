package org.devlive.grantforge.security.authorization;

import org.devlive.grantforge.security.enhancer.GrantForgeTokenEnhancer;
import org.devlive.grantforge.security.exception.GrantForgeOauthWebResponseExceptionTranslator;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.provider.token.TokenEnhancerChain;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;

import java.util.Arrays;

@Configuration
@EnableAuthorizationServer
public class GrantForgeServerConfigure extends AuthorizationServerConfigurerAdapter {

    private final JwtAccessTokenConverter accessTokenConverter;
    private final TokenStore tokenStore;
    private final AuthenticationManager authenticationManager;
    private final GrantForgeOauthWebResponseExceptionTranslator oauthWebResponseExceptionTranslator;
    private final GrantForgeTokenEnhancer grantForgeTokenEnhancer;

    public GrantForgeServerConfigure(JwtAccessTokenConverter accessTokenConverter, TokenStore tokenStore, AuthenticationManager authenticationManager, GrantForgeOauthWebResponseExceptionTranslator oauthWebResponseExceptionTranslator, GrantForgeTokenEnhancer grantForgeTokenEnhancer) {
        this.accessTokenConverter = accessTokenConverter;
        this.tokenStore = tokenStore;
        this.authenticationManager = authenticationManager;
        this.oauthWebResponseExceptionTranslator = oauthWebResponseExceptionTranslator;
        this.grantForgeTokenEnhancer = grantForgeTokenEnhancer;
    }

    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
        clients.inMemory()
                .withClient(GrantForgeOauth2Support.SECURITY_CLIENT_ID)
                // The secret password configuration must be filled in the format {bcrypt}+ encrypted password starting with Spring Security 5.0
//                .secret(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(BootStackAuthorizationOauth2Support.SECURITY_CLIENT_SECRET))
                .secret(GrantForgeOauth2Support.SECURITY_CLIENT_SECRET)
                .authorizedGrantTypes(GrantForgeOauth2Support.SECURITY_GRANT_TYPES)
                .scopes("select", "write", "read")
                .resourceIds(GrantForgeOauth2Support.SECURITY_RESOURCE_ID);
    }

    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) {
        TokenEnhancerChain enhancerChain = new TokenEnhancerChain();
        // custom token
        enhancerChain.setTokenEnhancers(Arrays.asList(accessTokenConverter, grantForgeTokenEnhancer));
        endpoints.tokenStore(tokenStore)
                .accessTokenConverter(accessTokenConverter)
                .tokenEnhancer(enhancerChain)
                .authenticationManager(authenticationManager)
                .exceptionTranslator(oauthWebResponseExceptionTranslator);
    }
}
