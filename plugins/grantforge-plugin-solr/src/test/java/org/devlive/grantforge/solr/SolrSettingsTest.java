// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr;

import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SolrSettingsTest
{
    /** A value that removes the field. */
    static final String UNSET = "<unset>";

    static ServiceConfig config(String... values)
    {
        Map<String, String> map = new HashMap<>(Map.of(SolrSettings.URLS, "http://solr1.example.com:8983/solr"));
        for (int index = 0; index < values.length; index += 2) {
            if (UNSET.equals(values[index + 1])) {
                map.remove(values[index]);
            }
            else {
                map.put(values[index], values[index + 1]);
            }
        }
        return new ServiceConfig("search", map);
    }

    private static List<String> fields(ServiceConfig config)
    {
        return SolrSettings.problems(config).stream().map(ConfigProblem::field).toList();
    }

    @Test
    void readsANodeWithoutSignInAndTheDefaults()
    {
        assertThat(SolrSettings.problems(config())).isEmpty();
        SolrSettings settings = SolrSettings.of(config());
        assertThat(settings.urls()).containsExactly("http://solr1.example.com:8983/solr");
        assertThat(settings.authentication()).isEqualTo(SolrSettings.NONE);
        assertThat(settings.trustStore()).isNull();
        assertThat(settings.trustStoreType()).isEqualTo("PKCS12");
        assertThat(settings.timeout()).isEqualTo(SolrSettings.DEFAULT_TIMEOUT);
        assertThat(settings.toString()).contains("solr1").doesNotContain("password");
    }

    @Test
    void readsSeveralNodesOnce()
    {
        assertThat(SolrSettings.urls("http://s1:8983/solr/, https://s2:8983/solr,http://s1:8983/solr")).containsExactly("http://s1:8983/solr",
                "https://s2:8983/solr");
    }

    @ParameterizedTest
    @ValueSource(strings = {"s1:8983/solr", "ftp://s1/solr", "http:///solr", "http://s1:8983/solr?x=1", "http://s1/solr#top", "http://s1,,http://s2",
            "http://s 1/solr"})
    void refusesWhatIsNoListOfNodes(String value)
    {
        assertThat(SolrSettings.urls(value)).isEmpty();
        assertThat(fields(config(SolrSettings.URLS, value))).containsExactly(SolrSettings.URLS);
    }

    @Test
    void needsTheNodes()
    {
        assertThat(SolrSettings.problems(config(SolrSettings.URLS, UNSET))).singleElement()
                .satisfies(problem -> assertThat(problem.reason()).isEqualTo(ConfigProblem.Reason.REQUIRED));
    }

    @Test
    void readsABasicAndATokenSignIn()
    {
        SolrSettings basic = SolrSettings.of(config(SolrSettings.AUTHENTICATION, "Basic", SolrSettings.USER, "grantforge", SolrSettings.PASSWORD, "s"));
        assertThat(basic.authentication()).isEqualTo(SolrSettings.BASIC);
        assertThat(basic.user()).isEqualTo("grantforge");
        assertThat(basic.password()).isEqualTo("s");
        SolrSettings jwt = SolrSettings.of(config(SolrSettings.AUTHENTICATION, "jwt", SolrSettings.TOKEN_FIELD, " eyJ.a.b "));
        assertThat(jwt.token()).isEqualTo("eyJ.a.b");
        assertThat(fields(config(SolrSettings.AUTHENTICATION, "basic"))).containsExactly(SolrSettings.USER, SolrSettings.PASSWORD);
        assertThat(fields(config(SolrSettings.AUTHENTICATION, "jwt"))).containsExactly(SolrSettings.TOKEN_FIELD);
    }

    @Test
    void refusesFieldsOfAnotherSignInAndAnUnknownOne()
    {
        assertThat(fields(config(SolrSettings.TOKEN_FIELD, "t"))).containsExactly(SolrSettings.TOKEN_FIELD);
        assertThat(fields(config(SolrSettings.AUTHENTICATION, "jwt", SolrSettings.TOKEN_FIELD, "t", SolrSettings.USER, "u")))
                .containsExactly(SolrSettings.USER);
        assertThat(fields(config(SolrSettings.AUTHENTICATION, "kerberos"))).containsExactly(SolrSettings.AUTHENTICATION);
    }

    @Test
    void readsATrustStore()
    {
        SolrSettings settings = SolrSettings.of(config(SolrSettings.TRUST_STORE, "/etc/solr/truststore.jks", SolrSettings.TRUST_STORE_PASSWORD, "p",
                SolrSettings.TRUST_STORE_TYPE, "JKS"));
        assertThat(settings.trustStore()).isEqualTo("/etc/solr/truststore.jks");
        assertThat(settings.trustStorePassword()).isEqualTo("p");
        assertThat(settings.trustStoreType()).isEqualTo("JKS");
        assertThat(fields(config(SolrSettings.TRUST_STORE_PASSWORD, "p"))).containsExactly(SolrSettings.TRUST_STORE);
        assertThat(fields(config(SolrSettings.TRUST_STORE_TYPE, "JKS"))).containsExactly(SolrSettings.TRUST_STORE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "600001", "-1", "1000000", "soon"})
    void refusesATimeoutOutOfRange(String timeout)
    {
        assertThat(fields(config(SolrSettings.TIMEOUT, timeout))).containsExactly(SolrSettings.TIMEOUT);
        assertThatIllegalArgumentException().isThrownBy(() -> SolrSettings.of(config(SolrSettings.TIMEOUT, timeout)))
                .withMessageStartingWith(SolrSettings.TIMEOUT + ": ");
    }

    @Test
    void acceptsTheTimeoutsBounds()
    {
        assertThat(SolrSettings.timeout(config(SolrSettings.TIMEOUT, "1"))).isEqualTo(1);
        assertThat(SolrSettings.timeout(config(SolrSettings.TIMEOUT, "600000"))).isEqualTo(600_000);
    }
}
