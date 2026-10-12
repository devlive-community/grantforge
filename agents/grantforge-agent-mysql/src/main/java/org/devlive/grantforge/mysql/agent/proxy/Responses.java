// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.proxy;

import io.netty.buffer.ByteBuf;
import org.apache.shardingsphere.database.protocol.mysql.constant.MySQLStatusFlag;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Follows the server's responses to the commands the proxy sent on, to know where each one ends: clients may send
 * several commands before reading the answers, so an error the proxy answers itself must wait until the responses to
 * the commands before it have passed, and what a command changes (the database, the character set, the user) takes
 * effect only once the server said OK.
 *
 * <p>The server's bytes reach the client unchanged; this class only reads them, as MySQL's client/server protocol lays
 * them out: OK and ERR packets, result sets (a column count, column definitions, an EOF unless the client deprecated
 * it, rows and a closing EOF or OK, with more results to follow when the status says so), LOCAL INFILE requests, the
 * answer to COM_STMT_PREPARE and sign-in exchanges. Not thread-safe: the connection's event loop uses it.
 */
final class Responses
{
    private static final int MORE_RESULTS = MySQLStatusFlag.SERVER_MORE_RESULTS_EXISTS.getValue();
    private static final int CURSOR_EXISTS = MySQLStatusFlag.SERVER_STATUS_CURSOR_EXISTS.getValue();

    /** The kinds of response the protocol has. */
    enum Kind
    {
        /** OK, ERR, a LOCAL INFILE exchange or result sets: COM_QUERY, COM_STMT_EXECUTE, COM_PROCESS_INFO. */
        RESULT,

        /** Rows of an open cursor: COM_STMT_FETCH. */
        FETCH,

        /** The statement's id, parameters and columns, or ERR: COM_STMT_PREPARE. */
        PREPARE,

        /** One packet: OK, EOF, ERR or text, as COM_PING or COM_STATISTICS answer. */
        SINGLE,

        /** Column definitions closed by EOF: COM_FIELD_LIST. */
        FIELDS,

        /** A sign-in exchange closed by OK or ERR: COM_CHANGE_USER. */
        SIGN_IN
    }

    private enum State
    {
        START, INFILE, COLUMNS, COLUMNS_EOF, ROWS, SKIP
    }

    /** One response the proxy waits for, or an answer of its own that waits its turn. */
    private static final class Entry
    {
        final @Nullable Kind kind;
        final @Nullable Consumer<Boolean> done;
        final @Nullable LongConsumer prepared;
        final @Nullable ByteBuf local;

        Entry(@Nullable Kind kind, @Nullable Consumer<Boolean> done, @Nullable LongConsumer prepared, @Nullable ByteBuf local)
        {
            this.kind = kind;
            this.done = done;
            this.prepared = prepared;
            this.local = local;
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final boolean deprecateEof;
    private State state = State.START;
    private long remaining;
    private boolean continuing;

    /**
     * Creates a follower for a connection.
     *
     * @param deprecateEof whether client and server agreed that OK packets replace EOF packets
     */
    Responses(boolean deprecateEof)
    {
        this.deprecateEof = deprecateEof;
    }

    /**
     * Expects the response to a command sent on.
     *
     * @param kind its kind
     * @param done called with whether it ended in success, or {@code null}
     * @param prepared called with the statement id COM_STMT_PREPARE returned, or {@code null}
     */
    void expect(Kind kind, @Nullable Consumer<Boolean> done, @Nullable LongConsumer prepared)
    {
        entries.addLast(new Entry(kind, done, prepared, null));
    }

    /**
     * Queues an answer of the proxy's own behind the responses still to come.
     *
     * @param packet the packets to send the client
     * @return the packets when nothing comes before them, to send now; {@code null} when they wait
     */
    @Nullable ByteBuf answer(ByteBuf packet)
    {
        if (entries.isEmpty()) {
            return packet;
        }
        entries.addLast(new Entry(null, null, null, packet));
        return null;
    }

    /**
     * Returns whether the client may now send packets that continue a command: the file of a LOCAL INFILE request or
     * the steps of a sign-in.
     *
     * @return {@code true} if it may
     */
    boolean awaitsClient()
    {
        Entry head = entries.peekFirst();
        return head != null && ((head.kind == Kind.RESULT && state == State.INFILE) || head.kind == Kind.SIGN_IN);
    }

    /**
     * Returns whether no response is outstanding.
     *
     * @return {@code true} if none is
     */
    boolean idle()
    {
        return entries.isEmpty();
    }

    /**
     * Reads a packet of the server on its way to the client.
     *
     * @param frame the packet, header included
     * @return the proxy's own answers whose turn came with it, to send after it
     */
    List<ByteBuf> observe(ByteBuf frame)
    {
        boolean continued = continuing;
        continuing = Packets.length(frame) == Packets.MAX_PAYLOAD;
        Entry head = entries.peekFirst();
        if (continued || head == null || head.kind == null) {
            return List.of();
        }
        Boolean ended = step(head, frame);
        if (ended == null) {
            return List.of();
        }
        entries.removeFirst();
        state = State.START;
        if (head.done != null) {
            head.done.accept(ended);
        }
        List<ByteBuf> answers = new ArrayList<>();
        while (!entries.isEmpty() && entries.peekFirst().kind == null) {
            answers.add(entries.removeFirst().local);
        }
        return answers;
    }

    /** Advances the head response by a packet; returns whether it ended well, or {@code null} while it goes on. */
    private @Nullable Boolean step(Entry head, ByteBuf frame)
    {
        int first = Packets.first(frame);
        boolean whole = Packets.length(frame) < Packets.MAX_PAYLOAD;
        Kind kind = head.kind;
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case SINGLE -> first != Packets.ERR;
            case SIGN_IN -> first == Packets.OK ? Boolean.TRUE : first == Packets.ERR ? Boolean.FALSE : null;
            case FIELDS -> first == Packets.ERR ? Boolean.FALSE : (first == Packets.EOF && whole) ? Boolean.TRUE : null;
            case PREPARE -> prepare(head, frame, first);
            case FETCH -> {
                if (state == State.START) {
                    state = State.ROWS;
                }
                yield result(frame, first, whole);
            }
            case RESULT -> result(frame, first, whole);
        };
    }

    private @Nullable Boolean prepare(Entry head, ByteBuf frame, int first)
    {
        if (state == State.SKIP) {
            remaining--;
            return remaining <= 0 ? Boolean.TRUE : null;
        }
        if (first == Packets.ERR) {
            return false;
        }
        ByteBuf payload = Packets.payload(frame);
        // OK, statement id (4), columns (2), parameters (2), filler, warnings.
        long id = payload.getUnsignedIntLE(payload.readerIndex() + 1);
        int columns = payload.getUnsignedShortLE(payload.readerIndex() + 5);
        int parameters = payload.getUnsignedShortLE(payload.readerIndex() + 7);
        if (head.prepared != null) {
            head.prepared.accept(id);
        }
        remaining = (long) parameters + columns + (deprecateEof ? 0 : (parameters > 0 ? 1 : 0) + (columns > 0 ? 1 : 0));
        if (remaining == 0) {
            return true;
        }
        state = State.SKIP;
        return null;
    }

    private @Nullable Boolean result(ByteBuf frame, int first, boolean whole)
    {
        return switch (state) {
            case START, INFILE -> {
                if (first == Packets.ERR) {
                    yield false;
                }
                if (first == Packets.OK) {
                    yield more(frame);
                }
                if (state == State.START && first == Packets.LOCAL_INFILE) {
                    state = State.INFILE;
                    yield null;
                }
                remaining = Packets.leadingInteger(frame);
                state = State.COLUMNS;
                yield null;
            }
            case COLUMNS -> {
                remaining--;
                if (remaining == 0) {
                    state = deprecateEof ? State.ROWS : State.COLUMNS_EOF;
                }
                yield null;
            }
            // An open cursor sends its rows on COM_STMT_FETCH, not now.
            case COLUMNS_EOF -> {
                if ((Packets.status(frame) & CURSOR_EXISTS) != 0) {
                    yield true;
                }
                state = State.ROWS;
                yield null;
            }
            case ROWS, SKIP -> {
                if (first == Packets.ERR) {
                    yield false;
                }
                boolean closing = first == Packets.EOF && whole && (deprecateEof || Packets.length(frame) < 9);
                yield closing ? more(frame) : null;
            }
        };
    }

    /** Ends a result unless the server says more results follow. */
    private @Nullable Boolean more(ByteBuf frame)
    {
        if ((Packets.status(frame) & MORE_RESULTS) != 0) {
            state = State.START;
            return null;
        }
        return true;
    }

    /** Releases the answers that still wait, when the connection closes. */
    void release()
    {
        for (Entry entry : entries) {
            if (entry.local != null) {
                entry.local.release();
            }
        }
        entries.clear();
    }
}
