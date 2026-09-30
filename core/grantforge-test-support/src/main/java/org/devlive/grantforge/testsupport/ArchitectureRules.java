// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
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
    private static final String JPA_ENTITY = "jakarta.persistence.Entity";
    private static final String SPRING_DATA_QUERY = "org.springframework.data.jpa.repository.Query";

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
     * SQL is written in JPQL or the Criteria API so Hibernate translates it for every supported database.
     * Native SQL (native JPA/Hibernate queries, {@code @Query(nativeQuery = true)} or Spring JDBC) is only
     * allowed in {@code ..persistence.dialect..} packages, which must be tested on each database.
     */
    @ArchTest
    public static final ArchRule NO_NATIVE_SQL = noClasses()
            .that().resideOutsideOfPackage("..persistence.dialect..")
            .should().callMethodWhere(nativeQueryCall())
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
            .orShould().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.simple.JdbcClient")
            .orShould(declareNativeRepositoryQueries())
            .because("native SQL is not portable across the supported databases")
            .allowEmptyShould(true);

    /**
     * API types (controllers and request/response models in {@code ..api..} packages) never expose JPA
     * entities: responses are explicit DTOs, so lazy loading and internal fields cannot leak to clients.
     */
    @ArchTest
    public static final ArchRule ENTITIES_STAY_OUT_OF_API = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().areAnnotatedWith(JPA_ENTITY)
            .because("API responses are DTOs, never JPA entities")
            .allowEmptyShould(true);

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

    private static DescribedPredicate<JavaMethodCall> nativeQueryCall()
    {
        return DescribedPredicate.describe("a native SQL query", call -> {
            String name = call.getTarget().getName();
            JavaClass owner = call.getTargetOwner();
            // Referenced by name so this module needs no JPA or Hibernate dependency.
            return name.startsWith("createNative")
                    && (owner.isAssignableTo("jakarta.persistence.EntityManager")
                    || owner.isAssignableTo("org.hibernate.SharedSessionContract")
                    || owner.isAssignableTo("org.hibernate.query.QueryProducer"));
        });
    }

    private static ArchCondition<JavaClass> declareNativeRepositoryQueries()
    {
        return new ArchCondition<>("declare Spring Data @Query(nativeQuery = true) methods")
        {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events)
            {
                // Used inside noClasses(): ArchUnit inverts the events, so a class that DOES declare a native
                // query must be reported as "satisfied" to become a violation of the rule.
                boolean found = false;
                for (JavaMethod method : javaClass.getMethods()) {
                    for (JavaAnnotation<JavaMethod> annotation : method.getAnnotations()) {
                        boolean nativeQuery = SPRING_DATA_QUERY.equals(annotation.getRawType().getName())
                                && Boolean.TRUE.equals(annotation.get("nativeQuery").orElse(Boolean.FALSE));
                        if (nativeQuery) {
                            found = true;
                            events.add(SimpleConditionEvent.satisfied(method,
                                    method.getFullName() + " declares a native @Query"));
                        }
                    }
                }
                if (!found) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                            javaClass.getName() + " declares no native @Query"));
                }
            }
        };
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
