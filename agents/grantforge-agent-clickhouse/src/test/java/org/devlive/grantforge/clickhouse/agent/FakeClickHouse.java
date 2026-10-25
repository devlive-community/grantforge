// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A ClickHouse that answers queries by a fragment of their text, records the statements it is given, and fails the
 * queries and statements that contain a fragment it is told to fail. A query no answer matches returns no rows.
 */
final class FakeClickHouse
        implements ClickHouse
{
    final List<String> executed = new ArrayList<>();
    final List<Map<String, Object>> parameters = new ArrayList<>();
    private final Map<String, Function<Map<String, Object>, List<Row>>> answers = new LinkedHashMap<>();
    private final Map<String, ClickHouseFailure> failures = new LinkedHashMap<>();
    boolean closed;

    /** Answers the queries containing a fragment with rows. */
    FakeClickHouse answer(String fragment, Row... rows)
    {
        return answer(fragment, parameters -> List.of(rows));
    }

    /** Answers the queries containing a fragment by their parameters. */
    FakeClickHouse answer(String fragment, Function<Map<String, Object>, List<Row>> answer)
    {
        answers.put(fragment, answer);
        return this;
    }

    /** Fails the queries and statements containing a fragment, until told otherwise. */
    FakeClickHouse fail(String fragment, int code)
    {
        failures.put(fragment, new ClickHouseFailure(code, "failed: " + fragment, null));
        return this;
    }

    /** Stops failing what contains a fragment. */
    FakeClickHouse heal(String fragment)
    {
        failures.remove(fragment);
        return this;
    }

    private void check(String sql)
    {
        for (Map.Entry<String, ClickHouseFailure> failure : failures.entrySet()) {
            if (sql.contains(failure.getKey())) {
                throw failure.getValue();
            }
        }
    }

    @Override
    public List<Row> query(String sql, Map<String, Object> values)
    {
        check(sql);
        parameters.add(values);
        for (Map.Entry<String, Function<Map<String, Object>, List<Row>>> answer : answers.entrySet()) {
            if (sql.contains(answer.getKey())) {
                return answer.getValue().apply(values);
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
