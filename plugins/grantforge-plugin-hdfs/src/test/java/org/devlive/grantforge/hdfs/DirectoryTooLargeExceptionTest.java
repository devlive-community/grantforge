// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class DirectoryTooLargeExceptionTest
{
    @Test
    void saysHowToNarrowTheLookupAndIsNamedAsTheLimit()
    {
        DirectoryTooLargeException tooLarge = new DirectoryTooLargeException(2);

        assertThat(tooLarge).hasMessage("directory exceeds 2 entries; narrow the lookup directory using lookup.path");
        assertThat(HdfsProvider.failure(new IOException("listing failed", tooLarge)).getReason())
                .isEqualTo(LookupException.Reason.LIMIT_EXCEEDED);
    }
}
