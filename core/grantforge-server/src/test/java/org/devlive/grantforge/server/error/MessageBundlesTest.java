// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.error;

import org.devlive.grantforge.common.error.CommonErrorCode;
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

/** Every error code the server can return has a message in every supported language. */
class MessageBundlesTest
{
    private static Properties load(String bundle) throws IOException
    {
        Properties properties = new Properties();
        try (InputStream stream = MessageBundlesTest.class.getResourceAsStream("/i18n/" + bundle)) {
            assertThat(stream).as(bundle).isNotNull();
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }

    @ParameterizedTest
    @ValueSource(strings = {"messages.properties", "messages_zh_CN.properties"})
    void everyCommonErrorHasAMessage(String bundle) throws IOException
    {
        Properties messages = load(bundle);

        assertThat(Arrays.stream(CommonErrorCode.values()).map(CommonErrorCode::messageKey))
                .allSatisfy(key -> assertThat(messages.getProperty(key)).as(key).isNotBlank());
    }
}
