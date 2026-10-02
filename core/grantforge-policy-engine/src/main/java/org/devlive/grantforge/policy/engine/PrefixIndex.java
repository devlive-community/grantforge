// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the policies that may cover a requested resource without trying them all: a prefix tree per root level over
 * what each policy's root values certainly start with. A policy is a candidate when one of its prefixes is a prefix
 * of the requested root value; policies whose root values promise nothing (exclusions, {@code *}, regular
 * expressions, leading wildcards) are always candidates. Only candidates are matched in full.
 */
final class PrefixIndex
{
    private final Map<String, Node> roots = new HashMap<>();
    private final Map<String, BitSet> always = new HashMap<>();

    PrefixIndex(List<CompiledPolicy> policies)
    {
        for (int index = 0; index < policies.size(); index++) {
            CompiledPolicy policy = policies.get(index);
            CompiledPolicy.Level level = policy.rootLevel();
            boolean unbounded = level.excludes();
            for (ValueMatcher matcher : level.matchers()) {
                if (matcher.literalPrefix().isEmpty()) {
                    unbounded = true;
                }
            }
            if (unbounded) {
                bits(always, policy.root()).set(index);
                continue;
            }
            Node root = rootOf(policy.root());
            for (ValueMatcher matcher : level.matchers()) {
                root.insert(matcher.literalPrefix(), index);
            }
        }
    }

    private Node rootOf(String level)
    {
        Node root = roots.get(level);
        if (root == null) {
            root = new Node();
            roots.put(level, root);
        }
        return root;
    }

    private static BitSet bits(Map<String, BitSet> sets, String key)
    {
        BitSet set = sets.get(key);
        if (set == null) {
            set = new BitSet();
            sets.put(key, set);
        }
        return set;
    }

    /**
     * Returns the indexes of the policies that may cover a resource.
     *
     * @param root the requested root level
     * @param value its normalized value
     * @return the candidates
     */
    BitSet candidates(String root, String value)
    {
        BitSet found = new BitSet();
        BitSet unbounded = always.get(root);
        if (unbounded != null) {
            found.or(unbounded);
        }
        Node node = roots.get(root);
        if (node == null) {
            return found;
        }
        found.or(node.policies);
        for (int index = 0; index < value.length(); index++) {
            node = node.children.get(value.charAt(index));
            if (node == null) {
                break;
            }
            found.or(node.policies);
        }
        return found;
    }

    /** A node of the prefix tree: the policies whose prefix ends here, and what follows. */
    static final class Node
    {
        final BitSet policies = new BitSet();
        final Map<Character, Node> children = new HashMap<>();

        void insert(String prefix, int policy)
        {
            Node node = this;
            for (int index = 0; index < prefix.length(); index++) {
                node = node.child(prefix.charAt(index));
            }
            node.policies.set(policy);
        }

        Node child(char key)
        {
            Node next = children.get(key);
            if (next == null) {
                next = new Node();
                children.put(key, next);
            }
            return next;
        }
    }
}
