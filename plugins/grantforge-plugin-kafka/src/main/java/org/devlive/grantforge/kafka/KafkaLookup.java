// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.TransactionListing;
import org.apache.kafka.common.errors.DelegationTokenDisabledException;
import org.apache.kafka.common.errors.UnsupportedByAuthenticationException;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.apache.kafka.common.security.token.delegation.DelegationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

/**
 * Lists the names of one resource type that start with what was typed: topics (internal ones such as
 * {@code __consumer_offsets} only when the typing starts with an underscore), consumer and share groups, transactional
 * IDs, users with SCRAM credentials, delegation tokens, and the cluster, which Kafka always names {@code kafka-cluster}.
 * Brokers that do not offer a listing, such as transactions before Kafka 3.0 or delegation tokens when they are
 * disabled, list nothing rather than fail. Names compare with case, as Kafka's do.
 *
 * @param resource the resource type
 * @param typed what was typed
 * @param limit the most names returned
 * @throws IllegalArgumentException from {@link #run} for a resource type with no names to list
 */
record KafkaLookup(String resource, String typed, int limit)
{
    /** The name of every Kafka cluster's cluster resource. */
    static final String CLUSTER_NAME = "kafka-cluster";

    /**
     * Runs the lookup.
     *
     * @param admin the client
     * @return the names, sorted
     * @throws ExecutionException when Kafka refuses the listing
     * @throws InterruptedException when interrupted while waiting
     */
    List<String> run(Admin admin) throws ExecutionException, InterruptedException
    {
        SortedSet<String> names = new TreeSet<>();
        switch (resource) {
            case KafkaProvider.TOPIC -> names.addAll(admin.listTopics(new ListTopicsOptions().listInternal(typed.startsWith("_"))).names().get());
            case KafkaProvider.GROUP -> {
                for (GroupListing group : admin.listGroups().all().get()) {
                    names.add(group.groupId());
                }
            }
            case KafkaProvider.TRANSACTIONAL_ID -> {
                for (TransactionListing transaction : optional(() -> admin.listTransactions().all().get())) {
                    names.add(transaction.transactionalId());
                }
            }
            case KafkaProvider.USER -> names.addAll(optional(() -> admin.describeUserScramCredentials().users().get()));
            case KafkaProvider.DELEGATION_TOKEN -> {
                for (DelegationToken token : optional(() -> admin.describeDelegationToken().delegationTokens().get())) {
                    names.add(token.tokenInfo().tokenId());
                }
            }
            case KafkaProvider.CLUSTER -> names.add(CLUSTER_NAME);
            default -> throw new IllegalArgumentException("Kafka services look up no " + resource);
        }
        List<String> found = new ArrayList<>();
        for (String name : names) {
            if (found.size() == limit) {
                break;
            }
            if (name.startsWith(typed)) {
                found.add(name);
            }
        }
        return found;
    }

    /** A listing some brokers do not offer. */
    @FunctionalInterface
    private interface Listing<T>
    {
        Collection<T> list() throws ExecutionException, InterruptedException;
    }

    private static <T> Collection<T> optional(Listing<T> listing) throws ExecutionException, InterruptedException
    {
        try {
            return listing.list();
        }
        catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof UnsupportedVersionException || cause instanceof DelegationTokenDisabledException
                    || cause instanceof UnsupportedByAuthenticationException) {
                return List.of();
            }
            throw failed;
        }
    }
}
