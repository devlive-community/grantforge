// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.error;

import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Business failure carrying an {@link ErrorCode}.
 *
 * <p>The exception message ({@code detail}) is for logs and developers and is written in English. API
 * clients receive the code and the localised message for {@link ErrorCode#messageKey()}, formatted with
 * {@link #getArguments()}.
 *
 * <p>The class is final: failures are distinguished by their {@link ErrorCode}, not by subclasses, which
 * also keeps the validating constructors safe from partially constructed subclass instances.
 */
public final class GrantForgeException
        extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final transient ErrorCode errorCode;
    private final transient List<Object> arguments;
    private final transient List<FieldIssue> fieldIssues;

    /**
     * Creates the exception.
     *
     * @param errorCode the error; must not be {@code null}
     * @param detail developer-facing description; must not be {@code null}
     * @param arguments values for the localised message; {@code null} elements are not allowed
     */
    public GrantForgeException(ErrorCode errorCode, String detail, Object... arguments)
    {
        this(errorCode, detail, null, arguments);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param errorCode the error; must not be {@code null}
     * @param detail developer-facing description; must not be {@code null}
     * @param cause the underlying failure; may be {@code null}
     * @param arguments values for the localised message; {@code null} elements are not allowed
     */
    public GrantForgeException(ErrorCode errorCode, String detail, @Nullable Throwable cause, Object... arguments)
    {
        super(requireNonNull(detail, "detail"), cause);
        this.errorCode = requireNonNull(errorCode, "errorCode");
        // List.of copies the array and rejects null elements, so a bad argument fails here, not at render time.
        this.arguments = List.of(requireNonNull(arguments, "arguments"));
        this.fieldIssues = List.of();
    }

    private GrantForgeException(GrantForgeException original, List<FieldIssue> fieldIssues)
    {
        super(original.getMessage(), original.getCause());
        this.errorCode = original.errorCode;
        this.arguments = original.arguments;
        this.fieldIssues = List.copyOf(requireNonNull(fieldIssues, "fieldIssues"));
    }

    /**
     * Returns the same failure naming the inputs at fault, so the console can point at them.
     *
     * @param issues what is wrong with which input
     * @return the failure with the issues
     */
    public GrantForgeException withFieldIssues(List<FieldIssue> issues)
    {
        return new GrantForgeException(this, issues);
    }

    /**
     * Returns what is wrong with which input.
     *
     * @return the issues; empty unless {@link #withFieldIssues(List)} named some
     */
    public List<FieldIssue> getFieldIssues()
    {
        return fieldIssues;
    }

    /**
     * Returns the error code.
     *
     * @return the error code; never {@code null}
     */
    public ErrorCode getErrorCode()
    {
        return errorCode;
    }

    /**
     * Returns the arguments for the localised message.
     *
     * @return an immutable list; never {@code null}
     */
    public List<Object> getArguments()
    {
        return arguments;
    }
}
