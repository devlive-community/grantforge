package org.devlive.grantforge.security.exception;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.devlive.grantforge.common.date.DateUtils;
import org.devlive.grantforge.common.enums.SystemMessageEnums;

import java.io.IOException;

public class GrantForgeOauthExceptionSerializer extends StdSerializer<GrantForgeOauthException> {

    public GrantForgeOauthExceptionSerializer() {
        super(GrantForgeOauthException.class);
    }

    @Override
    public void serialize(GrantForgeOauthException value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        generator.writeStartObject();
        generator.writeStringField("code", String.valueOf(SystemMessageEnums.SYSTEM_BAD_CREDENTIALS.getCode()));
        generator.writeStringField("message", SystemMessageEnums.SYSTEM_BAD_CREDENTIALS.getValue());
        generator.writeStringField("data", DateUtils.formatYmdhms());
        generator.writeEndObject();
    }
}