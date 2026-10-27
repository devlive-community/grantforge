// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A Doris that answers queries by a fragment of their text, records the statements it is given, and fails the queries
 * and statements that contain a fragment it is told to fail. A query no answer matches returns no rows; the first
 * matching answer wins.
 */
final class FakeDoris
        implements Doris
{
    final List<String> executed = new ArrayList<>();
    final List<List<Object>> parameters = new ArrayList<>();
    private final Map<String, Function<List<Object>, List<Row>>> answers = new LinkedHashMap<>();
    private final Map<String, DorisFailure> failures = new LinkedHashMap<>();
    boolean closed;

    /** Answers the queries containing a fragment with rows. */
    FakeDoris answer(String fragment, Row... rows)
    {
        return answer(fragment, parameters -> List.of(rows));
    }

    /** Answers the queries containing a fragment by their parameters. */
    FakeDoris answer(String fragment, Function<List<Object>, List<Row>> answer)
    {
        answers.put(fragment, answer);
        return this;
    }

    /** Fails the queries and statements containing a fragment, until told otherwise. */
    FakeDoris fail(String fragment, boolean unreachable)
    {
        failures.put(fragment, new DorisFailure("failed: " + fragment, unreachable, null));
        return this;
    }

    /** Fails the queries and statements containing a fragment with a message, until told otherwise. */
    FakeDoris fail(String fragment, String message)
    {
        failures.put(fragment, new DorisFailure(message, false, null));
        return this;
    }

    /** Stops failing what contains a fragment. */
    FakeDoris heal(String fragment)
    {
        failures.remove(fragment);
        return this;
    }

    private void check(String sql)
    {
        for (Map.Entry<String, DorisFailure> failure : failures.entrySet()) {
            if (sql.contains(failure.getKey())) {
                throw failure.getValue();
            }
        }
    }

    @Override
    public List<Row> query(String sql, Object... values)
    {
        check(sql);
        parameters.add(Arrays.asList(values));
        for (Map.Entry<String, Function<List<Object>, List<Row>>> answer : answers.entrySet()) {
            if (sql.contains(answer.getKey())) {
                return answer.getValue().apply(Arrays.asList(values));
            }
        }
        return List.of();
    }

    @Override
    public void execute(String sql)
    {
        check(sql);
        executed.add(sql);
    }

    @Override
    public void close()
    {
        closed = true;
    }
}
