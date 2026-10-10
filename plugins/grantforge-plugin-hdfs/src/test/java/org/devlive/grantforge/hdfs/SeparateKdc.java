// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * A KDC of its own realm in a JVM of its own. Kerby keeps a KDC's realm and ports in shared option constants, so a
 * second KDC in the same JVM takes over the first one's realm.
 */
final class SeparateKdc
        implements AutoCloseable
{
    private static final String READY = "kdc ready";

    private final Process process;

    private SeparateKdc(Process process)
    {
        this.process = process;
    }

    /**
     * Starts the KDC and waits until it serves.
     *
     * @param realm its realm
     * @param port its TCP port
     * @param work its working directory
     * @param keytab where to export the principal's keys
     * @param principal the one principal it knows besides its own
     * @return the running KDC
     * @throws IOException if it does not start
     */
    @SuppressWarnings("PMD.DoNotUseThreads") // a reader of the child's output, as a pipe needs
    static SeparateKdc start(String realm, int port, Path work, Path keytab, String principal) throws IOException
    {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(List.of(java, "-cp", System.getProperty("java.class.path"), SeparateKdc.class.getName(),
                realm, Integer.toString(port), work.toString(), keytab.toString(), principal)).redirectErrorStream(true).start();
        BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder said = new StringBuilder();
        for (String line = output.readLine(); line != null; line = output.readLine()) {
            if (READY.equals(line)) {
                // Keep reading what it says, so that it never blocks on a full pipe.
                Thread drain = new Thread(() -> output.lines().forEach(ignored -> { }), "kdc-" + realm);
                drain.setDaemon(true);
                drain.start();
                return new SeparateKdc(process);
            }
            said.append(line).append('\n');
        }
        throw new IOException("the KDC of " + realm + " did not start:\n" + said);
    }

    @Override
    public void close() throws InterruptedException
    {
        process.destroy();
        process.waitFor();
    }

    /**
     * Runs the KDC until it is stopped.
     *
     * @param arguments the realm, TCP port, working directory, keytab and principal
     * @throws Exception if it cannot start
     */
    public static void main(String[] arguments) throws Exception
    {
        SimpleKdcServer kdc = new SimpleKdcServer();
        kdc.setWorkDir(new File(arguments[2]));
        kdc.setKdcRealm(arguments[0]);
        kdc.setKdcHost("localhost");
        kdc.setAllowUdp(false);
        kdc.setKdcTcpPort(Integer.parseInt(arguments[1]));
        kdc.init();
        kdc.start();
        kdc.createAndExportPrincipals(new File(arguments[3]), arguments[4]);
        System.out.println(READY);
        System.out.flush();
        // The test stops it by ending the process.
        Thread.currentThread().join();
    }
}
