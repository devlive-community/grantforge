// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.naming;

import jakarta.persistence.EntityManagerFactory;
import org.devlive.grantforge.persistence.naming.SchemaNamingVerifier.MappedNames;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class SchemaNamingVerifierTest
{
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void collectsTablesAndColumnsIncludingInheritedOnes()
    {
        MappedNames names = SchemaNamingVerifier.mappedNames(entityManagerFactory);

        assertThat(names.tables()).contains("gf_sample");
        assertThat(names.columns()).contains("id", "version", "created_at", "updated_at", "label");
    }

    @Test
    void mappedTestEntitiesFollowTheRules()
    {
        assertThat(SchemaNamingVerifier.verify(entityManagerFactory)).isEmpty();
    }

    @Test
    void mappedNamesAreSortedAndImmutable()
    {
        MappedNames names = new MappedNames(new HashSet<>(Set.of("b", "a")), Set.of("z", "y"));

        assertThat(names.tables()).containsExactly("a", "b");
        assertThatThrownBy(() -> names.columns().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }
}
