// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.devlive.grantforge.common.security.AuthenticatedEndpoint;
import org.devlive.grantforge.service.agent.AccessAudit;
import org.devlive.grantforge.service.agent.AgentCredential;
import org.devlive.grantforge.service.agent.AgentRegistry;
import org.devlive.grantforge.service.agent.PolicySnapshots;
import org.devlive.grantforge.service.agent.SignedSnapshot;
import org.devlive.grantforge.service.agent.SnapshotSigner;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import static java.util.Objects.requireNonNull;

/**
 * What agents call, signed in by their token: heartbeats, policy snapshots, the key snapshots are signed with and
 * access events. A snapshot answer carries its ETag, so an agent sending it back in {@code If-None-Match} gets 304 while
 * nothing changed, and the Ed25519 signature of its body in {@value #SIGNATURE_HEADER}.
 */
@RestController
public final class AgentController
{
    /** The signature of a snapshot's body, Base64. */
    public static final String SIGNATURE_HEADER = "X-GrantForge-Signature";

    /** The id of the key a snapshot was signed with. */
    public static final String KEY_HEADER = "X-GrantForge-Signing-Key";

    /** The policy version of a snapshot. */
    public static final String VERSION_HEADER = "X-GrantForge-Policy-Version";

    private final AgentRegistry registry;
    private final PolicySnapshots snapshots;
    private final SnapshotSigner signer;
    private final AccessAudit audit;

    /**
     * Creates the controller.
     *
     * @param registry records heartbeats
     * @param snapshots builds snapshots
     * @param signer the signing key
     * @param audit stores access events
     */
    public AgentController(AgentRegistry registry, PolicySnapshots snapshots, SnapshotSigner signer, AccessAudit audit)
    {
        this.registry = requireNonNull(registry, "registry");
        this.snapshots = requireNonNull(snapshots, "snapshots");
        this.signer = requireNonNull(signer, "signer");
        this.audit = requireNonNull(audit, "audit");
    }

    /**
     * Records a heartbeat, registering the agent the first time.
     *
     * @param agent the agent's credential
     * @param body what the agent reports
     * @param request the request, for the agent's address
     * @return the current policy version and when to report again
     */
    @AuthenticatedEndpoint
    @PostMapping("/api/v1/agent/heartbeat")
    public HeartbeatResponse heartbeat(@AuthenticationPrincipal AgentCredential agent, @Valid @RequestBody HeartbeatRequest body,
            HttpServletRequest request)
    {
        return HeartbeatResponse.from(registry.heartbeat(agent, body.report(), request.getRemoteAddr()));
    }

    /**
     * Returns the policy snapshot of the agent's service, or 304 if the agent has it already.
     *
     * @param agent the agent's credential
     * @param known the ETag of the snapshot the agent has
     * @return the snapshot as JSON, signed
     */
    @AuthenticatedEndpoint
    @GetMapping(path = "/api/v1/agent/policies", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> policies(@AuthenticationPrincipal AgentCredential agent,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) @Nullable String known)
    {
        SignedSnapshot snapshot = snapshots.signed(agent.serviceId());
        if (snapshot.etag().equals(known)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(snapshot.etag())
                    .header(VERSION_HEADER, Long.toString(snapshot.policyVersion())).build();
        }
        return ResponseEntity.ok().eTag(snapshot.etag()).contentType(MediaType.APPLICATION_JSON)
                .header(VERSION_HEADER, Long.toString(snapshot.policyVersion()))
                .header(KEY_HEADER, snapshot.keyId())
                .header(SIGNATURE_HEADER, snapshot.signature())
                .body(snapshot.body());
    }

    /**
     * Stores a batch of access events; events sent before are skipped, so a batch may be sent again safely.
     *
     * @param agent the agent's credential
     * @param body the events
     * @return what became of them
     */
    @AuthenticatedEndpoint
    @PostMapping("/api/v1/agent/access-events")
    public IngestedResponse accessEvents(@AuthenticationPrincipal AgentCredential agent, @Valid @RequestBody AccessEventBatch body)
    {
        return IngestedResponse.from(audit.record(agent, String.valueOf(body.instance()).strip(),
                body.events().stream().map(AccessEventRequest::fields).toList()));
    }

    /**
     * Returns the public key snapshots are signed with.
     *
     * @return the key
     */
    @AuthenticatedEndpoint
    @GetMapping("/api/v1/agent/signing-key")
    public SigningKeyResponse signingKey()
    {
        return SigningKeyResponse.from(signer.publicKey());
    }
}
