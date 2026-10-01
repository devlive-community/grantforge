// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.query;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class InClauseBatcherTest
{
    private static List<Integer> range(int size)
    {
        return IntStream.range(0, size).boxed().toList();
    }

    @Test
    void emptyInputNeverCallsTheQuery()
    {
        List<List<Integer>> calls = new ArrayList<>();

        List<String> result = InClauseBatcher.query(List.<Integer>of(), batch -> {
            calls.add(batch);
            return List.of("x");
        });

        assertThat(result).isEmpty();
        assertThat(calls).isEmpty();
    }

    @Test
    void splitsIntoBatchesOfAtMostOneThousand()
    {
        List<Integer> sizes = new ArrayList<>();

        List<Integer> result = InClauseBatcher.query(range(2500), batch -> {
            sizes.add(batch.size());
            return batch;
        });

        assertThat(sizes).containsExactly(1000, 1000, 500);
        assertThat(result).containsExactlyElementsOf(range(2500));
    }

    @Test
    void removesDuplicatesInEncounterOrder()
    {
        assertThat(InClauseBatcher.batches(List.of(3, 1, 3, 2, 1), 2)).containsExactly(List.of(3, 1), List.of(2));
    }

    @Test
    void batchesAreImmutable()
    {
        List<List<Integer>> batches = InClauseBatcher.batches(range(3), 10);

        assertThat(batches.get(0)).isUnmodifiable();
    }

    @Test
    void forEachVisitsEveryBatch()
    {
        List<Integer> seen = new ArrayList<>();

        InClauseBatcher.forEach(range(1001), seen::addAll);

        assertThat(seen).hasSize(1001);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 1001})
    void rejectsBatchSizesOutOfRange(int batchSize)
    {
        assertThatIllegalArgumentException().isThrownBy(() -> InClauseBatcher.batches(range(1), batchSize));
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contracts to test the guards
    void rejectsNullValuesArgumentsAndResults()
    {
        assertThatNullPointerException().isThrownBy(() -> InClauseBatcher.batches(Arrays.asList(1, null), 10));
        assertThatNullPointerException().isThrownBy(() -> InClauseBatcher.batches(null, 10));
        assertThatNullPointerException().isThrownBy(() -> InClauseBatcher.query(range(1), null));
        assertThatNullPointerException().isThrownBy(() -> InClauseBatcher.forEach(range(1), null));
        assertThatNullPointerException().isThrownBy(() -> InClauseBatcher.query(range(1), batch -> null));
    }
}
