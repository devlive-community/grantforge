// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.apache.hive.service.auth;

import org.apache.hive.org.apache.thrift.TConfiguration;
import org.apache.hive.org.apache.thrift.transport.TTransport;
import org.apache.hive.org.apache.thrift.transport.TTransportException;
import org.devlive.grantforge.hive.Subjects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import javax.security.auth.Subject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The plugin's transport is the one the driver loads, and opens its transport as the caller's subject on any JDK. */
class TSubjectAssumingTransportTest
{
    /** A transport that records who opened it, or fails. */
    private static final class Recording
            extends TTransport
    {
        @Nullable Subject openedAs;
        final @Nullable RuntimeException failure;
        final @Nullable TTransportException transportFailure;

        Recording(@Nullable RuntimeException failure, @Nullable TTransportException transportFailure)
        {
            this.failure = failure;
            this.transportFailure = transportFailure;
        }

        @Override
        public boolean isOpen()
        {
            return openedAs != null;
        }

        @Override
        public void open() throws TTransportException
        {
            if (failure != null) {
                throw failure;
            }
            if (transportFailure != null) {
                throw transportFailure;
            }
            openedAs = Subjects.current();
        }

        @Override
        public void close()
        {
            openedAs = null;
        }

        @Override
        public int read(byte[] buffer, int offset, int length)
        {
            return 0;
        }

        @Override
        public void write(byte[] buffer, int offset, int length)
        {
            // Nothing to send.
        }

        @Override
        public TConfiguration getConfiguration()
        {
            return TConfiguration.DEFAULT;
        }

        @Override
        public void updateKnownMessageSize(long size)
        {
            // Nothing to track.
        }

        @Override
        public void checkReadBytesAvailable(long numBytes)
        {
            // Everything is available.
        }
    }

    @Test
    void opensAsTheCallersSubject() throws Exception
    {
        assertThat(TSubjectAssumingTransport.class.getProtectionDomain().getCodeSource().getLocation().getPath())
                .as("the plugin's class, not the driver's").doesNotContain("hive-jdbc");
        Subject subject = new Subject();
        Recording wrapped = new Recording(null, null);
        Subjects.callAs(subject, () -> {
            new TSubjectAssumingTransport(wrapped).open();
            return null;
        });
        assertThat(wrapped.openedAs).isSameAs(subject);
    }

    @Test
    void passesFailuresOn()
    {
        TTransportException refused = new TTransportException("refused");
        assertThatThrownBy(() -> new TSubjectAssumingTransport(new Recording(null, refused)).open()).isSameAs(refused);
        IllegalStateException broken = new IllegalStateException("broken");
        assertThatThrownBy(() -> new TSubjectAssumingTransport(new Recording(broken, null)).open()).isSameAs(broken);
    }
}
