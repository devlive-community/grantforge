// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.hdfs.protocol.DirectoryListing;
import org.apache.hadoop.hdfs.protocol.HdfsFileStatus;
import org.apache.hadoop.hdfs.web.GrantForgeWebHdfsListing;
import org.apache.hadoop.hdfs.web.WebHdfsFileSystem;
import org.jspecify.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Uses the stable REST LISTSTATUS operation for old WebHDFS servers, preserving batched native RPC lookups. */
final class HdfsDirectoryListing
{
    private HdfsDirectoryListing()
    {
    }

    static FileStatus status(FileSystem files, Path path) throws IOException
    {
        return files instanceof WebHdfsFileSystem web ? GrantForgeWebHdfsListing.status(web, path) : files.getFileStatus(path);
    }

    static List<FileStatus> list(FileSystem files, Path path, int maximum) throws IOException
    {
        if (files instanceof WebHdfsFileSystem web) {
            return GrantForgeWebHdfsListing.list(web, path, maximum);
        }
        RemoteIterator<FileStatus> iterator = files.listStatusIterator(path);
        List<FileStatus> found = new ArrayList<>();
        while (iterator.hasNext()) {
            if (found.size() >= maximum) {
                throw new DirectoryTooLargeException(maximum);
            }
            found.add(iterator.next());
        }
        return found;
    }

    /**
     * Reads one page of a directory, sorted by name, starting after a name. HDFS over RPC and WebHDFS servers with
     * LISTSTATUS_BATCH page through any directory; older WebHDFS servers and other file systems are read once within
     * the scan limit, failing above it instead of leaving entries out.
     *
     * @param files the file system
     * @param path the directory
     * @param after the name the page starts after; {@code null} for the first page
     * @param size the most entries wanted
     * @param maximum the scan limit for file systems that cannot page
     * @return the page
     * @throws FileNotFoundException if the directory does not exist
     * @throws IOException if listing fails, or a directory that cannot be paged exceeds the scan limit
     */
    // The caller opened the file system and closes it; the narrowed views of it here are not separate resources.
    @SuppressWarnings("PMD.CloseResource")
    static Page page(FileSystem files, Path path, @Nullable String after, int size, int maximum) throws IOException
    {
        if (files instanceof DistributedFileSystem dfs) {
            return batched(startAfter -> {
                DirectoryListing listing = dfs.getClient().listPaths(path.toUri().getPath(),
                        startAfter.isEmpty() ? HdfsFileStatus.EMPTY_NAME : startAfter.getBytes(StandardCharsets.UTF_8), false);
                if (listing == null) {
                    throw new FileNotFoundException("File does not exist: " + path);
                }
                return batch(listing, dfs.getUri(), path);
            }, after, size);
        }
        if (files instanceof WebHdfsFileSystem web) {
            try {
                return batched(startAfter -> batch(GrantForgeWebHdfsListing.batch(web, path, startAfter), web.getUri(), path), after, size);
            }
            catch (IOException failed) {
                if (!GrantForgeWebHdfsListing.batchUnsupported(failed)) {
                    throw failed;
                }
                // Hadoop 2.7 cannot page: the whole directory, within the scan limit, is the only complete answer.
            }
        }
        List<FileStatus> all = new ArrayList<>(list(files, path, maximum));
        all.sort(Comparator.comparing(entry -> entry.getPath().getName()));
        List<FileStatus> rest = after == null ? all : all.stream().filter(entry -> entry.getPath().getName().compareTo(after) > 0).toList();
        return rest.size() <= size ? new Page(rest, null) : new Page(rest.subList(0, size), name(rest.get(size - 1)));
    }

    private static Batch batch(DirectoryListing listing, URI uri, Path path)
    {
        return new Batch(Arrays.stream(listing.getPartialListing()).map(status -> status.makeQualified(uri, path)).toList(), listing.hasMore());
    }

    /** Collects a page from batches, asking for the next batch only while the page is not full. */
    private static Page batched(BatchSource source, @Nullable String after, int size) throws IOException
    {
        List<FileStatus> found = new ArrayList<>();
        String startAfter = after == null ? "" : after;
        while (true) {
            Batch batch = source.read(startAfter);
            for (FileStatus status : batch.entries()) {
                if (found.size() == size) {
                    return new Page(found, name(found.get(size - 1)));
                }
                found.add(status);
            }
            if (!batch.more() || batch.entries().isEmpty()) {
                return new Page(found, null);
            }
            if (found.size() == size) {
                return new Page(found, name(found.get(size - 1)));
            }
            startAfter = name(batch.entries().get(batch.entries().size() - 1));
        }
    }

    private static String name(FileStatus status)
    {
        return status.getPath().getName();
    }

    /**
     * One page of a directory.
     *
     * @param entries the entries, sorted by name
     * @param next the name the next page starts after; {@code null} on the last page
     */
    record Page(List<FileStatus> entries, @Nullable String next)
    {
    }

    /** One batch the file system returned, and whether entries remain after it. */
    private record Batch(List<FileStatus> entries, boolean more)
    {
    }

    /** Reads the batch after a name. */
    @FunctionalInterface
    private interface BatchSource
    {
        Batch read(String startAfter) throws IOException;
    }
}
