// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class AuthzErrorCodeTest
{
    @Test
    void codesHonourTheErrorCodeContract()
    {
        assertThat(AuthzErrorCode.values()).allSatisfy(error -> {
            assertThat(error.code()).matches("GF-AUTHZ-\\d{3}");
            assertThat(error.httpStatus()).isBetween(400, 599);
            assertThat(error.messageKey()).matches("error\\.authz(\\.[a-z][a-z-]*)+");
        });
        assertThat(Arrays.stream(AuthzErrorCode.values()).map(AuthzErrorCode::code)).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @ValueSource(strings = {"authz.properties", "authz_zh_CN.properties"})
    void everyCodeHasAMessage(String bundle) throws IOException
    {
        Properties messages = new Properties();
        try (InputStream stream = AuthzErrorCodeTest.class.getResourceAsStream("/i18n/" + bundle)) {
            assertThat(stream).as(bundle).isNotNull();
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                messages.load(reader);
            }
        }

        assertThat(AuthzErrorCode.values())
                .allSatisfy(error -> assertThat(messages.getProperty(error.messageKey())).as(error.code()).isNotBlank());
    }
}
