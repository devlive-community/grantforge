// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserAclTest
{
    static List<Object> getuser(String commands, String keys, List<Object> selectors, String... flags)
    {
        return List.of("flags", List.of((Object[]) flags), "passwords", List.of(), "commands", commands, "keys", keys, "channels", "", "selectors", selectors);
    }

    static List<Object> selector(String commands, String keys, String channels)
    {
        return List.of("commands", commands, "keys", keys, "channels", channels);
    }

    @Test
    void readsWhatAclGetuserShows()
    {
        UserAcl acl = UserAcl.of(getuser("-@all +@connection", "", List.of(selector("-@all +@read", "%R~sales:*", ""), selector("-@all +publish", "", "&news:*")),
                "on", "sanitize-payload"));
        assertThat(acl.flags()).containsExactlyInAnyOrder("on", "sanitize-payload");
        assertThat(acl.root().commands()).isEqualTo("-@all +@connection");
        assertThat(acl.selectors()).extracting(Selector::rule).containsExactly("(%R~sales:* -@all +@read)", "(&news:* -@all +publish)");
        assertThat(acl.superuser()).isFalse();
        assertThat(acl.open()).isFalse();
    }

    @Test
    void tellsUsersNoSelectorBindsAndUsersAnyoneMaySignInAs()
    {
        assertThat(UserAcl.of(getuser("+@all", "~*", List.of(), "on")).superuser()).isTrue();
        assertThat(UserAcl.of(getuser("+@all", "%RW~*", List.of(), "on")).superuser()).isTrue();
        assertThat(UserAcl.of(getuser("+@all", "%R~*", List.of(), "on")).superuser()).isFalse();
        assertThat(UserAcl.of(getuser("-@all", "", List.of(), "on", "nopass")).open()).isTrue();
        assertThat(UserAcl.of(getuser("-@all", "", List.of(), "off", "nopass")).open()).isFalse();
    }

    @Test
    void readsKeysShownAsAListAndToleratesWhatIsMissing()
    {
        UserAcl acl = UserAcl.of(List.of("flags", "odd", "keys", List.of("~a", "~b"), "commands", "-@all", "selectors", "none", "dangling"));
        assertThat(acl.root().keys()).containsExactly("~a", "~b");
        assertThat(acl.selectors()).isEmpty();
        assertThat(acl.flags()).isEmpty();
        assertThat(UserAcl.of(null).root().commands()).isEmpty();
        assertThat(UserAcl.of(List.of("commands", "x", "keys")).root().commands()).isEqualTo("x");
    }
}
