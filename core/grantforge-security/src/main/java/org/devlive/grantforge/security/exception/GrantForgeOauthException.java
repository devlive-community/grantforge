package org.devlive.grantforge.security.exception;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.springframework.security.oauth2.common.exceptions.OAuth2Exception;

@JsonSerialize(using = GrantForgeOauthExceptionSerializer.class)
public class GrantForgeOauthException extends OAuth2Exception {
    public GrantForgeOauthException(String msg) {
        super(msg);
    }
}
