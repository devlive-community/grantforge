// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.apache.hive.service.auth;

import org.apache.hadoop.hive.thrift.TFilterTransport;
import org.apache.hive.org.apache.thrift.transport.TTransport;
import org.apache.hive.org.apache.thrift.transport.TTransportException;
import org.devlive.grantforge.hive.Subjects;

/**
 * Hive JDBC's transport for {@code kerberosAuthType=fromSubject}, which opens the SASL transport as the subject of the
 * calling code. Hive's own class finds that subject with {@code Subject.getSubject}, which throws from JDK 23 on; this
 * one, which the plugin's class loader finds before the driver's, uses {@link Subjects}, so Kerberos lookups work on
 * every JDK the server supports. Otherwise it does what Hive's does.
 */
public class TSubjectAssumingTransport
        extends TFilterTransport
{
    /**
     * Wraps a transport.
     *
     * @param wrapped the SASL transport
     */
    public TSubjectAssumingTransport(TTransport wrapped)
    {
        super(wrapped);
    }

    @Override
    public void open() throws TTransportException
    {
        try {
            Subjects.callAs(Subjects.current(), () -> {
                wrapped.open();
                return null;
            });
        }
        catch (TTransportException failed) {
            throw failed;
        }
        catch (RuntimeException failed) {
            throw failed;
        }
        catch (Exception failed) {
            throw new TTransportException(failed);
        }
    }
}
