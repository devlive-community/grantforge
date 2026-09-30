// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

/**
 * Architecture rules shared by every GrantForge module.
 *
 * <p>A module applies all of them with a test class such as:
 * <pre>{@code
 * @AnalyzeClasses(packages = "org.devlive.grantforge", importOptions = ImportOption.DoNotIncludeTests.class)
 * class ArchitectureTest
 * {
 *     @ArchTest
 *     static final ArchTests RULES = ArchTests.in(ArchitectureRules.class);
 * }
 * }</pre>
 * Rules are fields (not methods) so they can be evaluated individually in tests.
 */
public final class ArchitectureRules
{
    /**
     * Dependencies are injected through constructors so they can be final and checked for null.
     * The rule inspects fields only, so a module without fields is valid (empty check allowed).
     */
    @ArchTest
    public static final ArchRule NO_FIELD_INJECTION = NO_CLASSES_SHOULD_USE_FIELD_INJECTION.allowEmptyShould(true);

    /** Output goes through the logging framework so it can be filtered, structured and redacted. */
    @ArchTest
    public static final ArchRule NO_STANDARD_STREAMS = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    /** Throwing {@code Exception}, {@code RuntimeException} or {@code Throwable} hides the failure kind. */
    @ArchTest
    public static final ArchRule NO_GENERIC_EXCEPTIONS = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

    /** SLF4J (backed by Logback) is the only logging API. */
    @ArchTest
    public static final ArchRule NO_JAVA_UTIL_LOGGING = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    /**
     * {@link Optional#get()} throws an unexplained exception when empty; callers must use
     * {@code orElseThrow} with a meaningful exception, or handle absence explicitly.
     */
    @ArchTest
    public static final ArchRule NO_OPTIONAL_GET = noClasses()
            .should().callMethod(Optional.class, "get")
            .because("an empty Optional must be handled explicitly (orElseThrow with a domain exception)");

    /** The platform is built on Jakarta EE; legacy {@code javax} enterprise APIs must not come back. */
    @ArchTest
    public static final ArchRule NO_JAVAX_ENTERPRISE_APIS = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    // "javax.annotation" is matched exactly: javax.annotation.processing belongs to the JDK.
                    "javax.persistence..", "javax.servlet..", "javax.validation..", "javax.annotation",
                    "javax.inject..", "javax.transaction..", "javax.ws.rs..")
            .because("GrantForge uses the jakarta namespace");

    /**
     * Every package declares {@link NullMarked} in its {@code package-info.java}, so NullAway checks it
     * and every unannotated type is non-null by default.
     */
    @ArchTest
    public static final ArchRule PACKAGES_ARE_NULL_MARKED = classes()
            .should(resideInNullMarkedPackage())
            .because("NullAway only checks @NullMarked code");

    private ArchitectureRules()
    {
    }

    private static ArchCondition<JavaClass> resideInNullMarkedPackage()
    {
        return new ArchCondition<>("reside in a package annotated with @NullMarked")
        {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events)
            {
                JavaPackage javaPackage = javaClass.getPackage();
                boolean marked = javaPackage.isAnnotatedWith(NullMarked.class);
                String message = String.format("%s is in package %s, which %s @NullMarked",
                        javaClass.getName(), javaPackage.getName(), marked ? "is" : "is not");
                events.add(new SimpleConditionEvent(javaClass, marked, message));
            }
        };
    }
}
