// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.common;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;

import static java.util.Objects.requireNonNull;

/** Immutable callback data with no dependency on any Hadoop SPI version. Inode views are borrowed under its native lock. */
public final class HdfsAuthorizationContext
{
    private final String user;
    private final String[] groups;
    private final @Nullable HdfsNode[] nodes;
    private final byte[] @Nullable [] components;
    private final @Nullable String path;
    private final int snapshotId;
    private final int ancestorIndex;
    private final boolean checkOwner;
    private final boolean ignoreEmptyDir;
    private final int ancestorAccess;
    private final int parentAccess;
    private final int access;
    private final int subAccess;
    private final @Nullable String operation;
    private final @Nullable String callerContext;
    private final @Nullable String clientIp;

    HdfsAuthorizationContext(Builder builder)
    {
        user = builder.user;
        groups = builder.groups.clone();
        nodes = builder.nodes.clone();
        components = copy(builder.components);
        path = builder.path;
        snapshotId = builder.snapshotId;
        ancestorIndex = builder.ancestorIndex;
        checkOwner = builder.checkOwner;
        ignoreEmptyDir = builder.ignoreEmptyDir;
        ancestorAccess = builder.ancestorAccess;
        parentAccess = builder.parentAccess;
        access = builder.access;
        subAccess = builder.subAccess;
        operation = builder.operation;
        callerContext = builder.callerContext;
        clientIp = builder.clientIp;
    }

    /**
     * Starts a callback context using Hadoop's authenticated short user name and group names.
     *
     * @param user the authenticated user
     * @param groups the user's groups
     * @return a builder
     * @throws IllegalArgumentException if the user is blank
     */
    public static Builder builder(String user, String... groups)
    {
        if (blank(requireNonNull(user, "user"))) {
            throw new IllegalArgumentException("the HDFS callback user is missing");
        }
        return new Builder(user, groups);
    }

    /**
     * Returns the authenticated user.
     *
     * @return the authenticated user
     */
    public String user()
    {
        return user;
    }

    /**
     * Returns a copy of the authenticated groups.
     *
     * @return a copy of the authenticated groups
     */
    public String[] groups()
    {
        return groups.clone();
    }

    /**
     * Returns a copy of the inode chain, with null entries for missing components.
     *
     * @return a copy of the inode chain, with null entries for missing components
     */
    public @Nullable HdfsNode[] nodes()
    {
        return nodes.clone();
    }

    /**
     * Returns a deep copy of path components, with null for Hadoop's root-only component.
     *
     * @return a deep copy of path components, with null for Hadoop's root-only component
     */
    public byte[] @Nullable [] components()
    {
        return copy(components);
    }

    /**
     * Returns the requested path, or null for single-inode and pathless native callbacks.
     *
     * @return the requested path, or null for single-inode and pathless native callbacks
     */
    public @Nullable String path()
    {
        return path;
    }

    /**
     * Returns the native snapshot identifier.
     *
     * @return the native snapshot identifier
     */
    public int snapshotId()
    {
        return snapshotId;
    }

    /**
     * Returns the last potential ancestor's index, or minus one for a single-inode check.
     *
     * @return the last potential ancestor's index, or minus one for a single-inode check
     */
    public int ancestorIndex()
    {
        return ancestorIndex;
    }

    /**
     * Returns whether native ownership was checked.
     *
     * @return whether native ownership was checked
     */
    public boolean checkOwner()
    {
        return checkOwner;
    }

    /**
     * Returns Hadoop's empty-directory flag; policy denies also protect empty entries.
     *
     * @return Hadoop's empty-directory flag; policy denies also protect empty entries
     */
    public boolean ignoreEmptyDir()
    {
        return ignoreEmptyDir;
    }

    /**
     * Returns the ancestor's native action mask.
     *
     * @return the ancestor's native action mask
     */
    public int ancestorAccess()
    {
        return ancestorAccess;
    }

    /**
     * Returns the parent's native action mask.
     *
     * @return the parent's native action mask
     */
    public int parentAccess()
    {
        return parentAccess;
    }

    /**
     * Returns the target's native action mask.
     *
     * @return the target's native action mask
     */
    public int access()
    {
        return access;
    }

    /**
     * Returns the subtree's native action mask.
     *
     * @return the subtree's native action mask
     */
    public int subAccess()
    {
        return subAccess;
    }

    /**
     * Returns the native operation name, when available in that Hadoop version.
     *
     * @return the native operation name, when available in that Hadoop version
     */
    public @Nullable String operation()
    {
        return operation;
    }

    /**
     * Returns the native caller-context text, when available.
     *
     * @return the native caller-context text, when available
     */
    public @Nullable String callerContext()
    {
        return callerContext;
    }

    /**
     * Returns the authenticated RPC peer's IP address, when available.
     *
     * @return the authenticated RPC peer's IP address, when available
     */
    public @Nullable String clientIp()
    {
        return clientIp;
    }

    int nodeCount()
    {
        return nodes.length;
    }

    @Nullable HdfsNode node(int index)
    {
        return nodes[index];
    }

    // Path components are decoded separately; their arrays never leave this immutable context without a copy.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    String inodePath(int nodeIndex)
    {
        if (nodeIndex == nodes.length - 1 && path != null) {
            return path;
        }
        int componentIndex = nodeIndex + components.length - nodes.length;
        StringBuilder joined = new StringBuilder();
        for (int index = 0; index <= componentIndex; index++) {
            byte[] component = components[index];
            if (component != null && component.length > 0) {
                joined.append('/').append(new String(component, StandardCharsets.UTF_8));
            }
        }
        return joined.length() == 0 ? "/" : joined.toString();
    }

    static boolean blank(String value)
    {
        // Preserve String.trim() semantics on Java 8 without allocating a second string just to test emptiness.
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) > ' ') {
                return false;
            }
        }
        return true;
    }

    static byte[] @Nullable [] copy(byte[] @Nullable [] source)
    {
        byte[] @Nullable [] result = source.clone();
        for (int index = 0; index < result.length; index++) {
            byte[] component = result[index];
            if (component != null) {
                result[index] = component.clone();
            }
        }
        return result;
    }

    /** Collects native callback data. */
    public static final class Builder
    {
        // Package visibility avoids Java 8 synthetic accessors when the enclosing immutable context reads its builder.
        final String user;
        final String[] groups;
        @Nullable HdfsNode[] nodes = new HdfsNode[0];
        byte[] @Nullable [] components = new byte[0][];
        @Nullable String path;
        int snapshotId;
        int ancestorIndex = -1;
        boolean checkOwner;
        boolean ignoreEmptyDir;
        int ancestorAccess;
        int parentAccess;
        int access;
        int subAccess;
        @Nullable String operation;
        @Nullable String callerContext;
        @Nullable String clientIp;

        Builder(String user, String... groups)
        {
            this.user = user;
            this.groups = requireNonNull(groups, "groups").clone();
        }

        /**
         * Sets the inode chain, including null entries for paths not yet created.
         *
         * @param value the inode views
         * @return this builder
         */
        // Hadoop supplies one fixed chain including null slots for missing paths; preserve that array contract.
        @SuppressWarnings("PMD.UseVarargs")
        public Builder nodes(@Nullable HdfsNode[] value)
        {
            nodes = requireNonNull(value, "nodes").clone();
            return this;
        }

        /**
         * Sets the full native path components, retaining merged snapshot components and the root null component.
         *
         * @param value the components
         * @return this builder
         */
        public Builder components(byte[] @Nullable [] value)
        {
            components = copy(requireNonNull(value, "components"));
            return this;
        }

        /**
         * Sets the requested path, or null.
         *
         * @param value the requested path, or null
         * @return this builder
         */
        public Builder path(@Nullable String value)
        {
            path = value;
            return this;
        }

        /**
         * Sets the native snapshot identifier.
         *
         * @param value the native snapshot identifier
         * @return this builder
         */
        public Builder snapshotId(int value)
        {
            snapshotId = value;
            return this;
        }

        /**
         * Sets the potential ancestor index.
         *
         * @param value the potential ancestor index
         * @return this builder
         */
        public Builder ancestorIndex(int value)
        {
            ancestorIndex = value;
            return this;
        }

        /**
         * Sets whether native ownership was checked.
         *
         * @param value whether native ownership was checked
         * @return this builder
         */
        public Builder checkOwner(boolean value)
        {
            checkOwner = value;
            return this;
        }

        /**
         * Sets the native empty-directory flag.
         *
         * @param value the native empty-directory flag
         * @return this builder
         */
        public Builder ignoreEmptyDir(boolean value)
        {
            ignoreEmptyDir = value;
            return this;
        }

        /**
         * Sets native action masks: read is 4, write is 2, execute is 1; absent or NONE is 0.
         *
         * @param ancestor the ancestor mask
         * @param parent the parent mask
         * @param target the target mask
         * @param subtree the subtree mask
         * @return this builder
         * @throws IllegalArgumentException if any mask has bits outside 0 through 7
         */
        public Builder actions(int ancestor, int parent, int target, int subtree)
        {
            mask(ancestor);
            mask(parent);
            mask(target);
            mask(subtree);
            ancestorAccess = ancestor;
            parentAccess = parent;
            access = target;
            subAccess = subtree;
            return this;
        }

        /**
         * Sets the operation name, or null.
         *
         * @param value the operation name, or null
         * @return this builder
         */
        public Builder operation(@Nullable String value)
        {
            operation = value;
            return this;
        }

        /**
         * Sets the caller context, or null.
         *
         * @param value the caller context, or null
         * @return this builder
         */
        public Builder callerContext(@Nullable String value)
        {
            callerContext = value;
            return this;
        }

        /**
         * Sets the RPC peer's IP, or null.
         *
         * @param value the RPC peer's IP, or null
         * @return this builder
         */
        public Builder clientIp(@Nullable String value)
        {
            clientIp = value;
            return this;
        }

        /**
         * Returns an immutable callback context.
         *
         * @return an immutable callback context
         */
        public HdfsAuthorizationContext build()
        {
            return new HdfsAuthorizationContext(this);
        }

        private static void mask(int value)
        {
            if (value < 0 || value > 7) {
                throw new IllegalArgumentException("HDFS action masks must be between 0 and 7");
            }
        }
    }
}
