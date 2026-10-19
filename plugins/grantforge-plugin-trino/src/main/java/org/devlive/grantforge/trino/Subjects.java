// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino;

import org.jspecify.annotations.Nullable;

import javax.security.auth.Subject;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;

/**
 * Runs work as a JAAS subject on every JDK the server supports. JDK 18 added {@code Subject.current()} and
 * {@code Subject.callAs}; from JDK 23 on, {@code Subject.getSubject} throws unless a security manager is allowed, which
 * JDK 24 removed. So the new methods are used where they exist and the old ones on JDK 17.
 */
public final class Subjects
{
    private static final @Nullable MethodHandle CURRENT = find("current", MethodType.methodType(Subject.class));
    private static final @Nullable MethodHandle CALL_AS = find("callAs", MethodType.methodType(Object.class, Subject.class, Callable.class));

    private Subjects()
    {
    }

    private static @Nullable MethodHandle find(String name, MethodType type)
    {
        try {
            return MethodHandles.publicLookup().findStatic(Subject.class, name, type);
        }
        catch (NoSuchMethodException | IllegalAccessException missing) {
            return null;
        }
    }

    /**
     * Returns the subject the current work runs as.
     *
     * @return the subject, or {@code null} outside of one
     * @throws IllegalStateException never in practice: the JDK's method throws nothing checked
     */
    // JDK 17 has nothing but the deprecated way; a method handle declares Throwable, which is passed on as it came.
    @SuppressWarnings({"removal", "deprecation", "PMD.AvoidCatchingThrowable"})
    public static @Nullable Subject current()
    {
        MethodHandle current = CURRENT;
        if (current == null) {
            return Subject.getSubject(AccessController.getContext());
        }
        try {
            return (Subject) current.invokeExact();
        }
        catch (RuntimeException | Error failed) {
            throw failed;
        }
        catch (Throwable impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * Runs work as a subject.
     *
     * @param subject the subject
     * @param work the work
     * @param <T> its result's type
     * @return its result
     * @throws Exception what the work threw
     * @throws IllegalStateException never in practice: the JDK's method throws nothing else checked
     */
    // The JDK wraps what the work threw in a CompletionException; the work's own exception is thrown instead of it.
    @SuppressWarnings({"removal", "deprecation", "unchecked", "PMD.AvoidCatchingThrowable", "PMD.PreserveStackTrace"})
    public static <T> T callAs(@Nullable Subject subject, Callable<T> work) throws Exception
    {
        MethodHandle callAs = CALL_AS;
        if (callAs == null) {
            try {
                return Subject.doAs(subject, (PrivilegedExceptionAction<T>) work::call);
            }
            catch (PrivilegedActionException failed) {
                throw failed.getException();
            }
        }
        try {
            return (T) callAs.invoke(subject, work);
        }
        catch (CompletionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw failed;
        }
        catch (Exception | Error failed) {
            throw failed;
        }
        catch (Throwable impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
