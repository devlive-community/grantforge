// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.hdfs.web.GrantForgeWebHdfsListing;
import org.apache.hadoop.hdfs.web.WebHdfsFileSystem;

import java.io.IOException;
import java.util.ArrayList;
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
}
