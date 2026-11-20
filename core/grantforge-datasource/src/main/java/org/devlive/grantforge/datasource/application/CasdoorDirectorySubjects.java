// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.application;

import org.devlive.grantforge.datasource.provider.DirectoryProvider.Employee;
import org.devlive.grantforge.identity.application.IdentitySourceSettings;
import org.devlive.grantforge.identity.application.OidcSettings;
import org.devlive.grantforge.identity.domain.IdentitySource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves the Casdoor OIDC subject for employees in a configured DingTalk directory. */
@Component
public final class CasdoorDirectorySubjects implements DirectorySubjectResolver
{
    /** Opt-in for a Casdoor organization whose OAuth properties cannot be edited by users. */
    public static final String TRUST_DINGTALK_UNION_ID = "casdoorTrustDingTalkUnionId";

    private static final int PAGE_SIZE = 100;
    private final IdentitySourceSettings settings;
    private final RestClient client;

    /**
     * Builds the Casdoor directory client.
     * @param settings encrypted identity provider settings
     */
    @SuppressWarnings("PMD.CloseResource") // The shared Java 17 HttpClient has no close method.
    public CasdoorDirectorySubjects(IdentitySourceSettings settings)
    {
        this.settings = settings;
        HttpClient transport = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(transport);
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String setting()
    {
        return "casdoorOrganization";
    }

    @Override
    public Set<String> configurationKeys()
    {
        return Set.of(setting(), TRUST_DINGTALK_UNION_ID);
    }

    @Override
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // Each upstream account has a distinct subject and status.
    public Map<String, String> resolve(IdentitySource identity, Map<String, String> configuration, List<Employee> employees)
    {
        String organization = configuration.getOrDefault(setting(), "");
        boolean trustUnionId = Boolean.parseBoolean(configuration.get(TRUST_DINGTALK_UNION_ID));
        OidcSettings oidc = settings.oidc(identity);
        String secret = settings.secret(identity);
        if (!identity.isEnabled() || secret == null || secret.isBlank()) {
            throw DataSourceErrors.invalid("Casdoor pre-provisioning requires an enabled OIDC source with a client secret");
        }
        URI issuer = URI.create(oidc.issuer());
        String host = issuer.getHost();
        if (host == null || (!"https".equalsIgnoreCase(issuer.getScheme()) && !isLoopbackHttp(issuer, host))) {
            throw DataSourceErrors.invalid("Casdoor pre-provisioning requires an HTTPS issuer");
        }
        // Casdoor serves its management API at the issuer's origin, including for organization-specific issuers.
        URI endpoint = UriComponentsBuilder.newInstance().scheme(issuer.getScheme()).host(issuer.getHost())
                .port(issuer.getPort()).path("/api/get-users").build().toUri();
        Map<String, Candidate> names = new HashMap<>();
        Map<String, Candidate> oauthUsers = new HashMap<>();
        Set<String> ambiguousNames = new HashSet<>();
        Set<String> ambiguousOAuth = new HashSet<>();
        Set<String> seen = new HashSet<>();
        long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
        for (int page = 1; ; page++) {
            if (System.nanoTime() >= deadline) {
                throw DataSourceErrors.unavailable("Casdoor directory lookup timed out");
            }
            JsonNode rows = readPage(endpoint, organization, oidc.clientId(), secret, page);
            for (JsonNode row : rows) {
                String id = row.path("id").asString("");
                String name = row.path("name").asString("");
                if (!organization.equals(row.path("owner").asString("")) || id.isBlank() || name.isBlank() || !seen.add(id)) {
                    throw DataSourceErrors.unavailable("Casdoor directory page has an unexpected organization or duplicate identity");
                }
                boolean active = !row.path("isForbidden").asBoolean(false) && !row.path("isDeleted").asBoolean(false);
                Candidate candidate = new Candidate(id, active);
                index(names, ambiguousNames, name, candidate);
                if (trustUnionId) {
                    indexOAuth(row, candidate, oauthUsers, ambiguousOAuth);
                }
            }
            if (rows.size() < PAGE_SIZE) {
                break;
            }
        }
        Map<String, String> resolved = new HashMap<>();
        Set<String> employeeUnionIds = new HashSet<>();
        Set<String> duplicateUnionIds = new HashSet<>();
        for (Employee employee : employees) {
            String unionId = employee.unionId();
            if (unionId != null && !unionId.isBlank() && !employeeUnionIds.add(unionId)) {
                duplicateUnionIds.add(unionId);
            }
        }
        Set<String> usedSubjects = new HashSet<>();
        Set<String> repeatedSubjects = new HashSet<>();
        for (Employee employee : employees) {
            String unionId = employee.unionId();
            if (unionId == null || unionId.isBlank() || duplicateUnionIds.contains(unionId)
                    || ambiguousNames.contains(unionId) || ambiguousOAuth.contains(unionId)) {
                continue;
            }
            Candidate byName = names.get(unionId);
            Candidate strict = byName != null && employee.id().equals(byName.subject()) ? byName : null;
            Candidate oauth = oauthUsers.get(unionId);
            if (strict != null && oauth != null && !strict.subject().equals(oauth.subject())) {
                continue;
            }
            Candidate match = strict != null ? strict : oauth;
            if (match != null && match.active()) {
                resolved.put(employee.id(), match.subject());
                if (!usedSubjects.add(match.subject())) {
                    repeatedSubjects.add(match.subject());
                }
            }
        }
        resolved.values().removeIf(repeatedSubjects::contains);
        return Map.copyOf(resolved);
    }

    private static void indexOAuth(JsonNode user, Candidate candidate, Map<String, Candidate> users, Set<String> ambiguous)
    {
        JsonNode properties = user.path("properties");
        String unionId = properties.path("oauth_DingTalk_unionId").asString("");
        String providerId = properties.path("oauth_DingTalk_id").asString("");
        if (!unionId.isBlank() && !providerId.isBlank() && providerId.equals(user.path("dingtalk").asString(""))) {
            index(users, ambiguous, unionId, candidate);
        }
    }

    private static void index(Map<String, Candidate> users, Set<String> ambiguous, String key, Candidate candidate)
    {
        if (users.putIfAbsent(key, candidate) != null) {
            ambiguous.add(key);
        }
    }

    private record Candidate(String subject, boolean active)
    {
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP") // Only the literal loopback address is allowed for HTTP.
    private static boolean isLoopbackHttp(URI uri, String host)
    {
        return "http".equalsIgnoreCase(uri.getScheme()) && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host));
    }

    @SuppressWarnings("PMD.PreserveStackTrace") // External errors can include sensitive response data.
    private JsonNode readPage(URI endpoint, String organization, String clientId, String secret, int page)
    {
        URI uri = UriComponentsBuilder.fromUri(endpoint).queryParam("owner", organization).queryParam("p", page)
                .queryParam("pageSize", PAGE_SIZE).queryParam("sortField", "name").queryParam("sortOrder", "ascend").build().encode().toUri();
        JsonNode response;
        try {
            response = client.get().uri(uri).headers(headers -> headers.setBasicAuth(clientId, secret))
                    .retrieve().body(JsonNode.class);
        }
        catch (RestClientException failed) {
            throw DataSourceErrors.unavailable("Casdoor directory lookup failed");
        }
        if (response == null || !"ok".equals(response.path("status").asString()) || !response.path("data").isArray()) {
            throw DataSourceErrors.unavailable("Casdoor did not return a directory page");
        }
        return response.path("data");
    }
}
