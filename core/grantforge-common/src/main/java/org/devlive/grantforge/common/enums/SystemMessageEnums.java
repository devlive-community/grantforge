// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.enums;

public enum SystemMessageEnums
{
    SYSTEM_SUCCESS(2000, "查询成功"),
    SYSTEM_UNAUTHORIZED(4000, "访问权限无效，请联系管理员分配权限"),
    SYSTEM_ERROR_TOKEN(4001, "无效的授权密钥"),
    SYSTEM_BAD_CREDENTIALS(4002, "错误的用户名或密码"),
    SYSTEM_PARAMS_VALIDATE_ERROR(4003, "param validation error, please check"),
    SYSTEM_BODY_MUST_NULL(4004, "required request body is missing"),
    SYSTEM_MEDIA_TYPE_NOT_SUPPORT(4005, "media type not supported or media type must not null"),
    SYSTEM_METHOD_CONVERT_NOT_SUPPORT(4006, "failed to convert id type"),
    SYSTEM_PARAM_MUST_NULL(4007, "required request param is missing"),
    SYSTEM_PARAM_PATH_MUST_NULL(4008, "required request path param is missing"),
    SYSTEM_USER_EXISTS(4009, "user exists, please replace user name"),
    SYSTEM_METHOD_NOT_SUPPORT(4010, "request method not supported"),
    SYSTEM_PARAMS_NOT_NULL(4011, "params must not null"),
    SYSTEM_PERSISTENCE(4012, "数据持久化异常，异常信息：%s"),

    SYSTEM_JSON_ERROR(4100, "无效的 JSON 数据");

    private Integer code;
    private String value;

    SystemMessageEnums(Integer code, String value)
    {
        this.code = code;
        this.value = value;
    }

    public String getValue()
    {
        return value;
    }

    public void setValue(String value)
    {
        this.value = value;
    }

    public Integer getCode()
    {
        return code;
    }
}
