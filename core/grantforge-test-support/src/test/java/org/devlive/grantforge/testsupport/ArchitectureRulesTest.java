// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.devlive.grantforge.testsupport.fixture.clean.StatelessFixture;
import org.devlive.grantforge.testsupport.fixture.violations.FieldInjectionFixture;
import org.devlive.grantforge.testsupport.fixture.violations.GenericExceptionFixture;
import org.devlive.grantforge.testsupport.fixture.violations.HibernateNativeQueryFixture;
import org.devlive.grantforge.testsupport.fixture.violations.JavaUtilLoggingFixture;
import org.devlive.grantforge.testsupport.fixture.violations.JavaxFixture;
import org.devlive.grantforge.testsupport.fixture.violations.JdbcTemplateFixture;
import org.devlive.grantforge.testsupport.fixture.violations.NativeQueryFixture;
import org.devlive.grantforge.testsupport.fixture.violations.NativeRepositoryFixture;
import org.devlive.grantforge.testsupport.fixture.violations.OptionalGetFixture;
import org.devlive.grantforge.testsupport.fixture.violations.StandardStreamsFixture;
import org.devlive.grantforge.testsupport.fixture.violations.api.EntityLeakFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ArchitectureRulesTest
{
    private static final String FIXTURES = "org.devlive.grantforge.testsupport.fixture";
    private static final JavaClasses CLEAN = new ClassFileImporter().importPackages(FIXTURES + ".clean");
    private static final JavaClasses VIOLATIONS = new ClassFileImporter().importPackages(FIXTURES + ".violations");

    static Stream<Arguments> rulesAndOffenders()
    {
        return Stream.of(
                Arguments.of("NO_FIELD_INJECTION", ArchitectureRules.NO_FIELD_INJECTION, FieldInjectionFixture.class),
                Arguments.of("NO_STANDARD_STREAMS", ArchitectureRules.NO_STANDARD_STREAMS, StandardStreamsFixture.class),
                Arguments.of("NO_GENERIC_EXCEPTIONS", ArchitectureRules.NO_GENERIC_EXCEPTIONS,
                        GenericExceptionFixture.class),
                Arguments.of("NO_JAVA_UTIL_LOGGING", ArchitectureRules.NO_JAVA_UTIL_LOGGING, JavaUtilLoggingFixture.class),
                Arguments.of("NO_OPTIONAL_GET", ArchitectureRules.NO_OPTIONAL_GET, OptionalGetFixture.class),
                Arguments.of("NO_JAVAX_ENTERPRISE_APIS", ArchitectureRules.NO_JAVAX_ENTERPRISE_APIS, JavaxFixture.class),
                Arguments.of("NO_NATIVE_SQL", ArchitectureRules.NO_NATIVE_SQL, NativeQueryFixture.class),
                Arguments.of("ENTITIES_STAY_OUT_OF_API", ArchitectureRules.ENTITIES_STAY_OUT_OF_API,
                        EntityLeakFixture.class),
                Arguments.of("PACKAGES_ARE_NULL_MARKED", ArchitectureRules.PACKAGES_ARE_NULL_MARKED,
                        StandardStreamsFixture.class));
    }

    @ParameterizedTest(name = "{0} reports its offender")
    @MethodSource("rulesAndOffenders")
    void ruleReportsTheOffendingFixture(String name, ArchRule rule, Class<?> offender)
    {
        EvaluationResult result = rule.evaluate(VIOLATIONS);

        assertThat(result.hasViolation()).as(name).isTrue();
        assertThat(result.getFailureReport().getDetails()).as(name)
                .anyMatch(detail -> detail.contains(offender.getName()));
    }

    @ParameterizedTest(name = "{0} accepts clean code")
    @MethodSource("rulesAndOffenders")
    void ruleAcceptsCleanCode(String name, ArchRule rule, Class<?> offender)
    {
        assertThatCode(() -> rule.check(CLEAN)).as(name).doesNotThrowAnyException();
    }

    @Test
    void nativeSqlRuleCoversRepositoriesAndSpringJdbc()
    {
        EvaluationResult result = ArchitectureRules.NO_NATIVE_SQL.evaluate(VIOLATIONS);

        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains(NativeRepositoryFixture.class.getName() + ".raw()"))
                .anyMatch(detail -> detail.contains(JdbcTemplateFixture.class.getName()))
                .anyMatch(detail -> detail.contains(HibernateNativeQueryFixture.class.getName()));
    }

    @Test
    void fieldInjectionRuleAcceptsClassesWithoutFields()
    {
        JavaClasses stateless = new ClassFileImporter().importClasses(StatelessFixture.class);

        assertThatCode(() -> ArchitectureRules.NO_FIELD_INJECTION.check(stateless)).doesNotThrowAnyException();
    }

    @Test
    void everyPublicRuleIsAnArchTestAndCoveredHere()
    {
        List<Field> rules = Arrays.stream(ArchitectureRules.class.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()) && ArchRule.class.equals(field.getType()))
                .toList();

        assertThat(rules).allMatch(field -> field.isAnnotationPresent(ArchTest.class));
        assertThat(rules).extracting(Field::getName)
                .containsExactlyInAnyOrderElementsOf(rulesAndOffenders().map(args -> (String) args.get()[0]).toList());
    }
}
