// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.authz.application.AccessReviewCommand;
import org.devlive.grantforge.authz.domain.ReviewFallback;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * An access review to add or change.
 *
 * @param name the name
 * @param description a longer explanation
 * @param roleIds the roles whose assignments are reviewed, 1 to 50
 * @param durationDays how long each round stays open, 1 to 90
 * @param intervalDays days between scheduled rounds, from the duration to 366; left out for no repetition
 * @param unreviewed what happens to undecided assignments; kept unless said otherwise
 * @param enabled whether rounds start on schedule; on unless said otherwise
 * @param nextRunAt when the next scheduled round starts; left out for none
 */
public record AccessReviewRequest(
        @NotBlank @Size(max = 128) @Nullable String name,
        @Size(max = 512) @Nullable String description,
        @NotNull @Size(max = 50) @Nullable List<@NotNull @Size(max = 20) String> roleIds,
        @NotNull @Nullable Integer durationDays,
        @Nullable Integer intervalDays,
        @Nullable ReviewFallback unreviewed,
        @Nullable Boolean enabled,
        @Nullable Instant nextRunAt)
{
    /** Copies the roles; left out, validation refuses them. */
    public AccessReviewRequest
    {
        roleIds = roleIds == null ? null : List.copyOf(roleIds);
    }

    /**
     * Turns the request into a command.
     *
     * @return the command
     */
    public AccessReviewCommand command()
    {
        List<String> ids = roleIds == null ? List.of() : roleIds;
        return new AccessReviewCommand(String.valueOf(name), description, new LinkedHashSet<>(ids.stream().map(id -> PathIds.parse(id.strip(), "role"))
                .toList()), durationDays == null ? 0 : durationDays, intervalDays, unreviewed == null ? ReviewFallback.KEEP : unreviewed,
                enabled == null || enabled, nextRunAt);
    }
}
