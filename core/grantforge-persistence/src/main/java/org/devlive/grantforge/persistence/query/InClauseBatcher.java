// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Splits large {@code IN (...)} parameter lists into batches every supported database accepts.
 *
 * <p>Oracle rejects more than 1000 expressions in an {@code IN} list (ORA-01795) and other databases
 * degrade with very long lists, so every query or update that binds a collection goes through this class.
 * Values are de-duplicated in encounter order, which also keeps batches as small as possible.
 */
public final class InClauseBatcher
{
    /** Largest number of values bound in one {@code IN} clause. */
    public static final int MAX_BATCH_SIZE = 1000;

    private InClauseBatcher()
    {
    }

    /**
     * Runs a query once per batch and concatenates the results.
     *
     * @param values values to bind; {@code null} elements are rejected, an empty collection skips the query
     * @param query called with each batch (at most {@value #MAX_BATCH_SIZE} distinct values); must not
     *        return {@code null}
     * @param <T> value type
     * @param <R> result type
     * @return the results of all batches in batch order
     * @throws NullPointerException if an argument, a value or a query result is {@code null}
     */
    public static <T, R> List<R> query(Collection<? extends T> values, Function<List<T>, List<R>> query)
    {
        return query(values, MAX_BATCH_SIZE, query);
    }

    /**
     * Runs a query once per batch of the given size and concatenates the results.
     *
     * @param values values to bind; {@code null} elements are rejected
     * @param batchSize values per batch, 1 to {@value #MAX_BATCH_SIZE}
     * @param query called with each batch; must not return {@code null}
     * @param <T> value type
     * @param <R> result type
     * @return the results of all batches in batch order
     * @throws IllegalArgumentException if {@code batchSize} is out of range
     */
    public static <T, R> List<R> query(Collection<? extends T> values, int batchSize, Function<List<T>, List<R>> query)
    {
        requireNonNull(query, "query");
        List<R> results = new ArrayList<>();
        for (List<T> batch : InClauseBatcher.<T>batches(values, batchSize)) {
            results.addAll(requireNonNull(query.apply(batch), "query result"));
        }
        return results;
    }

    /**
     * Runs an action (such as a bulk update or delete) once per batch.
     *
     * @param values values to bind; {@code null} elements are rejected, an empty collection skips the action
     * @param action called with each batch (at most {@value #MAX_BATCH_SIZE} distinct values)
     * @param <T> value type
     */
    public static <T> void forEach(Collection<? extends T> values, Consumer<List<T>> action)
    {
        requireNonNull(action, "action");
        InClauseBatcher.<T>batches(values, MAX_BATCH_SIZE).forEach(action);
    }

    /**
     * Splits values into de-duplicated, immutable batches.
     *
     * @param values values to split; {@code null} elements are rejected
     * @param batchSize values per batch, 1 to {@value #MAX_BATCH_SIZE}
     * @param <T> value type
     * @return the batches; empty when {@code values} is empty
     * @throws IllegalArgumentException if {@code batchSize} is out of range
     */
    public static <T> List<List<T>> batches(Collection<? extends T> values, int batchSize)
    {
        requireNonNull(values, "values");
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "batchSize must be between 1 and " + MAX_BATCH_SIZE + " but was " + batchSize);
        }
        List<T> distinct = new ArrayList<>(new LinkedHashSet<T>(values));
        List<List<T>> batches = new ArrayList<>();
        for (int start = 0; start < distinct.size(); start += batchSize) {
            // List.copyOf rejects null elements, so a null value fails before any SQL is issued.
            batches.add(List.copyOf(distinct.subList(start, Math.min(start + batchSize, distinct.size()))));
        }
        return batches;
    }
}
