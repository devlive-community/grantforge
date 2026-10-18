// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeDelegationTokenResult;
import org.apache.kafka.clients.admin.DescribeUserScramCredentialsResult;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.admin.ListGroupsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.ListTransactionsResult;
import org.apache.kafka.clients.admin.TransactionListing;
import org.apache.kafka.clients.admin.TransactionState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.errors.ClusterAuthorizationException;
import org.apache.kafka.common.errors.DelegationTokenDisabledException;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.apache.kafka.common.security.token.delegation.DelegationToken;
import org.apache.kafka.common.security.token.delegation.TokenInformation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaLookupTest
{
    private final Admin admin = mock(Admin.class);

    private static <T> KafkaFuture<T> failed(Throwable cause)
    {
        KafkaFutureImpl<T> future = new KafkaFutureImpl<>();
        future.completeExceptionally(cause);
        return future;
    }

    private List<String> lookup(String resource, String typed, int limit) throws Exception
    {
        return new KafkaLookup(resource, typed, limit).run(admin);
    }

    @Test
    void listsTopicsByPrefixAndInternalOnesOnlyWhenAskedFor() throws Exception
    {
        ListTopicsResult topics = mock(ListTopicsResult.class);
        when(topics.names()).thenReturn(KafkaFuture.completedFuture(Set.of("orders", "orders-eu", "payments", "Orders")));
        when(admin.listTopics(any(ListTopicsOptions.class))).thenReturn(topics);
        assertThat(lookup(KafkaProvider.TOPIC, "orders", 10)).containsExactly("orders", "orders-eu");
        assertThat(lookup(KafkaProvider.TOPIC, "", 2)).containsExactly("Orders", "orders");
        ArgumentCaptor<ListTopicsOptions> options = ArgumentCaptor.forClass(ListTopicsOptions.class);
        verify(admin, org.mockito.Mockito.times(2)).listTopics(options.capture());
        assertThat(options.getValue().shouldListInternal()).isFalse();
        lookup(KafkaProvider.TOPIC, "__", 10);
        verify(admin, org.mockito.Mockito.times(3)).listTopics(options.capture());
        assertThat(options.getValue().shouldListInternal()).isTrue();
    }

    @Test
    void listsGroupsTransactionsUsersAndTokens() throws Exception
    {
        ListGroupsResult groups = mock(ListGroupsResult.class);
        when(groups.all()).thenReturn(KafkaFuture.completedFuture(List.of(new GroupListing("billing", Optional.empty(), "consumer",
                Optional.empty()), new GroupListing("audit", Optional.empty(), "consumer", Optional.empty()))));
        when(admin.listGroups()).thenReturn(groups);
        assertThat(lookup(KafkaProvider.GROUP, "", 10)).containsExactly("audit", "billing");

        ListTransactionsResult transactions = mock(ListTransactionsResult.class);
        when(transactions.all()).thenReturn(KafkaFuture.completedFuture(List.of(new TransactionListing("payments-tx", 4L,
                TransactionState.ONGOING))));
        when(admin.listTransactions()).thenReturn(transactions);
        assertThat(lookup(KafkaProvider.TRANSACTIONAL_ID, "pay", 10)).containsExactly("payments-tx");

        DescribeUserScramCredentialsResult users = mock(DescribeUserScramCredentialsResult.class);
        when(users.users()).thenReturn(KafkaFuture.completedFuture(List.of("alice", "bob")));
        when(admin.describeUserScramCredentials()).thenReturn(users);
        assertThat(lookup(KafkaProvider.USER, "a", 10)).containsExactly("alice");

        DescribeDelegationTokenResult tokens = mock(DescribeDelegationTokenResult.class);
        TokenInformation info = new TokenInformation("token-1", new KafkaPrincipal("User", "alice"), List.of(), 0, 1, 2);
        when(tokens.delegationTokens()).thenReturn(KafkaFuture.completedFuture(List.of(new DelegationToken(info, new byte[] {1}))));
        when(admin.describeDelegationToken()).thenReturn(tokens);
        assertThat(lookup(KafkaProvider.DELEGATION_TOKEN, "token", 10)).containsExactly("token-1");
        assertThat(lookup(KafkaProvider.CLUSTER, "kafka", 10)).containsExactly(KafkaLookup.CLUSTER_NAME);
    }

    @Test
    void brokersWithoutAListingListNothingButRefusalsFail() throws Exception
    {
        ListTransactionsResult transactions = mock(ListTransactionsResult.class);
        when(transactions.all()).thenReturn(failed(new UnsupportedVersionException("Kafka 2.8")));
        when(admin.listTransactions()).thenReturn(transactions);
        assertThat(lookup(KafkaProvider.TRANSACTIONAL_ID, "", 10)).isEmpty();

        DescribeDelegationTokenResult tokens = mock(DescribeDelegationTokenResult.class);
        when(tokens.delegationTokens()).thenReturn(failed(new DelegationTokenDisabledException("off")));
        when(admin.describeDelegationToken()).thenReturn(tokens);
        assertThat(lookup(KafkaProvider.DELEGATION_TOKEN, "", 10)).isEmpty();

        DescribeUserScramCredentialsResult users = mock(DescribeUserScramCredentialsResult.class);
        when(users.users()).thenReturn(failed(new ClusterAuthorizationException("no")));
        when(admin.describeUserScramCredentials()).thenReturn(users);
        assertThatThrownBy(() -> lookup(KafkaProvider.USER, "", 10)).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ClusterAuthorizationException.class);
        assertThatThrownBy(() -> lookup("partition", "", 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
