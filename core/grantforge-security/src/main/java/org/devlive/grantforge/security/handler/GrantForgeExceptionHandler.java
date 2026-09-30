package org.devlive.grantforge.security.handler;

import lombok.extern.slf4j.Slf4j;
import org.devlive.grantforge.common.enums.SystemMessageEnums;
import org.devlive.grantforge.common.validation.ValidationUtils;
import org.devlive.grantforge.service.entity.common.CommonResponseModel;
import org.hibernate.HibernateException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
@ResponseBody
@Slf4j
public class GrantForgeExceptionHandler
{
    /**
     * POST validation error
     *
     * @param exception param validation exception
     * @return error result
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Object methodArgumentNotValidHandler(MethodArgumentNotValidException exception)
    {
        return CommonResponseModel.validateError(ValidationUtils.extractValidate(exception.getBindingResult()));
    }

    /**
     * GET validation error
     *
     * @param exception param validation exception
     * @return error result
     */
    @ExceptionHandler(value = BindException.class)
    public Object methodArgumentNotValidHandler(BindException exception)
    {
        return CommonResponseModel.validateError(ValidationUtils.extractValidate(exception.getBindingResult()));
    }

    /**
     * http param validation error
     *
     * @param exception param validation exception
     * @return error result
     */
    @ExceptionHandler(value = HttpMessageNotReadableException.class)
    public Object methodHttpMessageNotReadableException(HttpMessageNotReadableException exception)
    {
        return CommonResponseModel.error(SystemMessageEnums.SYSTEM_BODY_MUST_NULL, exception.getMessage());
    }

    /**
     * Media Type validation error
     *
     * @param exception param validation exception
     * @return error result
     */
    @ExceptionHandler(value = HttpMediaTypeNotSupportedException.class)
    public Object methodHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException exception)
    {
        return CommonResponseModel.error(SystemMessageEnums.SYSTEM_MEDIA_TYPE_NOT_SUPPORT, exception.getMessage());
    }

    @ExceptionHandler(value = MethodArgumentTypeMismatchException.class)
    public Object methodMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException exception)
    {
        return CommonResponseModel.error(SystemMessageEnums.SYSTEM_METHOD_CONVERT_NOT_SUPPORT, exception.getMessage());
    }

    @ExceptionHandler(value = MissingServletRequestParameterException.class)
    public Object methodMissingServletRequestParameterException(MissingServletRequestParameterException exception)
    {
        return CommonResponseModel.error(SystemMessageEnums.SYSTEM_PARAM_MUST_NULL, exception.getMessage());
    }

    @ExceptionHandler(value = HttpRequestMethodNotSupportedException.class)
    public Object methodHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException exception)
    {
        return CommonResponseModel.error(SystemMessageEnums.SYSTEM_METHOD_NOT_SUPPORT, exception.getMessage());
    }

    /**
     * 数据库保存错误处理
     */
    @ExceptionHandler(value = HibernateException.class)
    public Object methodHibernateException(HibernateException exception)
    {
        log.error("Hibernate Exception: {}", exception);
        SystemMessageEnums.SYSTEM_PERSISTENCE.setValue(String.format(SystemMessageEnums.SYSTEM_PERSISTENCE.getValue(), exception.getMessage()));
        return CommonResponseModel.error(SystemMessageEnums.SYSTEM_PERSISTENCE);
    }
}
