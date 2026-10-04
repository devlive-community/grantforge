// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.persistence.secured.DataFieldType;

import java.util.Arrays;
import java.util.Optional;

/**
 * A value conditions take from whoever reads the data, rather than spell out, such as the reader's departments. Only these
 * exist, so conditions cannot reach anything else about the reader.
 */
public enum ConditionVariable
{
    /** The reader's account id. */
    SUBJECT_ID("subject.id", DataFieldType.NUMBER, false),
    /** The reader's user name. */
    SUBJECT_USERNAME("subject.username", DataFieldType.TEXT, false),
    /** The ids of the departments the reader is a member of. */
    SUBJECT_ORG_UNIT_IDS("subject.orgUnitIds", DataFieldType.NUMBER, true),
    /** The codes of the reader's groups. */
    SUBJECT_GROUP_CODES("subject.groupCodes", DataFieldType.TEXT, true),
    /** The codes of the reader's positions. */
    SUBJECT_POSITION_CODES("subject.positionCodes", DataFieldType.TEXT, true),
    /** The moment the data is read. */
    NOW("now", DataFieldType.TIME, false);

    private final String key;
    private final DataFieldType type;
    private final boolean list;

    ConditionVariable(String key, DataFieldType type, boolean list)
    {
        this.key = key;
        this.type = type;
        this.list = list;
    }

    /**
     * Finds a variable by the name conditions use.
     *
     * @param key such as {@code subject.orgUnitIds}
     * @return the variable, or empty
     */
    public static Optional<ConditionVariable> of(String key)
    {
        return Arrays.stream(values()).filter(variable -> variable.key.equals(key)).findFirst();
    }

    /**
     * Returns the name conditions use.
     *
     * @return such as {@code subject.id}
     */
    public String key()
    {
        return key;
    }

    /**
     * Returns the kind of value.
     *
     * @return the type
     */
    public DataFieldType type()
    {
        return type;
    }

    /**
     * Returns whether the variable holds several values, for {@code in} and {@code not_in}.
     *
     * @return {@code true} for lists
     */
    public boolean list()
    {
        return list;
    }
}
