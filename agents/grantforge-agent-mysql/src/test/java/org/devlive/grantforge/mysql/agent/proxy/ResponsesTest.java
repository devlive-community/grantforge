// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsesTest
{
    @Test
    void localRefusalsWaitForEarlierCommandsAndPreserveTheirOrder()
    {
        Responses responses = new Responses(false);
        List<Boolean> results = new ArrayList<>();
        ByteBuf first = frame(0xFF, 1, 0);
        ByteBuf second = frame(0xFF, 2, 0);
        try {
            responses.expect(Responses.Kind.RESULT, results::add, null);
            assertThat(responses.answer(first)).isNull();
            assertThat(responses.answer(second)).isNull();
            assertThat(observe(responses, 0, 0, 0, 2, 0, 0, 0)).containsExactly(first, second);
            assertThat(results).containsExactly(true);
            assertThat(responses.idle()).isTrue();
            assertThat(first.refCnt()).isEqualTo(1);
        }
        finally {
            responses.release();
            first.release();
            second.release();
        }
    }

    @Test
    void followsResultSetMetadataRowsAndMoreResults()
    {
        Responses responses = new Responses(false);
        List<Boolean> results = new ArrayList<>();
        responses.expect(Responses.Kind.RESULT, results::add, null);
        observe(responses, 1);
        observe(responses, 3, 'c', 'o', 'l');
        observe(responses, 0xFE, 0, 0, 2, 0);
        observe(responses, 1, 'a');
        assertThat(results).isEmpty();
        observe(responses, 0xFE, 0, 0, 8, 0);
        assertThat(results).as("SERVER_MORE_RESULTS_EXISTS keeps the command outstanding").isEmpty();
        observe(responses, 0, 0, 0, 2, 0, 0, 0);
        assertThat(results).containsExactly(true);
        assertThat(responses.idle()).isTrue();
    }

    @Test
    void followsDeprecatedEofAndDoesNotConfuseALongTextRowWithTheTerminator()
    {
        Responses responses = new Responses(true);
        List<Boolean> results = new ArrayList<>();
        responses.expect(Responses.Kind.RESULT, results::add, null);
        observe(responses, 1);
        observe(responses, 3, 'c', 'o', 'l');
        observe(responses, 1, 'a');
        assertThat(results).isEmpty();
        observe(responses, 0xFE, 0, 0, 2, 0, 0, 0);
        assertThat(results).containsExactly(true);

        Responses legacy = new Responses(false);
        legacy.expect(Responses.Kind.FETCH, results::add, null);
        observe(legacy, 0xFE, 1, 0, 0, 0, 0, 0, 0, 0, 'x');
        assertThat(legacy.idle()).isFalse();
        observe(legacy, 0xFE, 0, 0, 2, 0);
        assertThat(legacy.idle()).isTrue();
    }

    @Test
    void followsLocalInfileAndSignInUntilTheirFinalOutcome()
    {
        Responses responses = new Responses(false);
        List<Boolean> results = new ArrayList<>();
        responses.expect(Responses.Kind.RESULT, results::add, null);
        assertThat(responses.awaitsClient()).isFalse();
        observe(responses, 0xFB, 'f', 'i', 'l', 'e');
        assertThat(responses.awaitsClient()).isTrue();
        observe(responses, 0xFF, 1, 0);
        assertThat(results).containsExactly(false);
        responses.expect(Responses.Kind.SIGN_IN, results::add, null);
        assertThat(responses.awaitsClient()).isTrue();
        observe(responses, 0xFE, 'p', 'l', 'u', 'g', 'i', 'n', 0);
        observe(responses, 1, 4);
        assertThat(results).containsExactly(false);
        observe(responses, 0, 0, 0, 2, 0, 0, 0);
        assertThat(results).containsExactly(false, true);
        assertThat(responses.awaitsClient()).isFalse();
    }

    @Test
    void doesNotCompletePrepareUntilAllParameterAndColumnDefinitionsArrive()
    {
        Responses responses = new Responses(false);
        List<Boolean> results = new ArrayList<>();
        AtomicLong statement = new AtomicLong(-1);
        responses.expect(Responses.Kind.PREPARE, results::add, statement::set);
        observe(responses, 0, 0xFF, 0xFF, 0xFF, 0x7F, 1, 0, 1, 0, 0, 0, 0);
        assertThat(statement.get()).isEqualTo(2147483647L);
        observe(responses, 3, 'p', 'a', 'r');
        observe(responses, 0xFE, 0, 0, 2, 0);
        observe(responses, 3, 'c', 'o', 'l');
        assertThat(results).isEmpty();
        observe(responses, 0xFE, 0, 0, 2, 0);
        assertThat(results).containsExactly(true);
    }

    @Test
    void handlesPrepareWithoutMetadataAndServerErrors()
    {
        Responses responses = new Responses(true);
        List<Boolean> results = new ArrayList<>();
        responses.expect(Responses.Kind.PREPARE, results::add, null);
        observe(responses, 0, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        responses.expect(Responses.Kind.SINGLE, results::add, null);
        observe(responses, 0xFF, 1, 0);
        responses.expect(Responses.Kind.FIELDS, results::add, null);
        observe(responses, 3, 'c', 'o', 'l');
        observe(responses, 0xFE, 0, 0, 2, 0);
        assertThat(results).containsExactly(true, false, true);
    }

    @Test
    void anOpenCursorCompletesExecuteBeforeItsRowsAreFetched()
    {
        Responses responses = new Responses(false);
        List<Boolean> results = new ArrayList<>();
        responses.expect(Responses.Kind.RESULT, results::add, null);
        observe(responses, 1);
        observe(responses, 3, 'c', 'o', 'l');
        observe(responses, 0xFE, 0, 0, 64, 0);
        assertThat(results).containsExactly(true);
        responses.expect(Responses.Kind.FETCH, results::add, null);
        observe(responses, 0, 0, 1, 'a');
        assertThat(results).containsExactly(true);
        observe(responses, 0xFE, 0, 0, 128, 0);
        assertThat(results).containsExactly(true, true);
    }

    @Test
    void closingTheConnectionReleasesOnlyQueuedLocalAnswers()
    {
        Responses responses = new Responses(false);
        ByteBuf immediate = frame(0xFF, 1, 0);
        ByteBuf waiting = frame(0xFF, 2, 0);
        assertThat(responses.answer(immediate)).isSameAs(immediate);
        responses.expect(Responses.Kind.RESULT, null, null);
        assertThat(responses.answer(waiting)).isNull();
        responses.release();
        assertThat(waiting.refCnt()).isZero();
        assertThat(immediate.refCnt()).isEqualTo(1);
        assertThat(responses.idle()).isTrue();
        responses.release();
        immediate.release();
    }

    private static List<ByteBuf> observe(Responses responses, int... bytes)
    {
        ByteBuf frame = frame(bytes);
        try {
            return responses.observe(frame);
        }
        finally {
            frame.release();
        }
    }

    private static ByteBuf frame(int... bytes)
    {
        ByteBuf frame = Unpooled.buffer(bytes.length + Packets.HEADER).writeMediumLE(bytes.length).writeByte(1);
        for (int value : bytes) {
            frame.writeByte(value);
        }
        return frame;
    }
}
