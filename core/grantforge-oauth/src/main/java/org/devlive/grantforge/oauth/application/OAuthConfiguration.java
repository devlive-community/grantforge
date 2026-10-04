// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

/**
 * The parts of the authorization server Spring Authorization Server looks up as beans: the published key set, the encoder
 * that signs with the active key, the decoder the UserInfo endpoint checks access tokens with, and the issuer. The server
 * module adds the filter chain.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OAuthProperties.class)
public class OAuthConfiguration
{
    /**
     * Publishes the active and recently retired keys.
     *
     * @param keys the signing keys
     * @return the key set source
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(SigningKeys keys)
    {
        return keys.published();
    }

    /**
     * Signs tokens with the active key only.
     *
     * @param keys the signing keys
     * @return the encoder
     */
    @Bean
    public JwtEncoder jwtEncoder(SigningKeys keys)
    {
        return new NimbusJwtEncoder(keys.signing());
    }

    /**
     * Checks tokens against every published key.
     *
     * @param jwkSource the published keys
     * @return the decoder
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource)
    {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        return new NimbusJwtDecoder(processor);
    }

    /**
     * Names the issuer, when one is configured; otherwise each request's URL does.
     *
     * @param properties the settings
     * @return the settings of the authorization server
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings(OAuthProperties properties)
    {
        AuthorizationServerSettings.Builder settings = AuthorizationServerSettings.builder();
        String issuer = properties.issuer();
        if (issuer != null) {
            settings.issuer(issuer);
        }
        return settings.build();
    }
}
