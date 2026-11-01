/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.phylogeny;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * A complete, identity-preserving Newick tree with iterative parsing and audits.
 * Labels, underscores, comments, missing lengths and signed lengths are retained.
 * A metric requires known nonnegative lengths on every nonroot edge. Root stems
 * are retained but are outside taxon-to-taxon paths. This is not a tree estimator.
 */
public final class NewickTree {
    /** Explicit complete-input capacities; exceeding one returns no partial tree. */
    public record Limits(int characters, int nodes, int depth, int labelCharacters,
                         int commentCharacters) {
        public Limits {
            if (characters < 1 || nodes < 1 || depth < 1 || labelCharacters < 1 || commentCharacters < 1)
                throw new IllegalArgumentException("Newick capacities must be positive");
        }
        public static Limits defaults() { return new Limits(67_108_864, 1_000_000, 100_000, 1_048_576, 16_777_216); }
    }

    /** Flat preorder identities avoid recursive traversal and recursive ownership. */
    public record Node(int id, int parent, List<Integer> children, String label,
                       Double branchLength, List<String> comments) {
        public Node {
            children = List.copyOf(children);
            comments = List.copyOf(comments);
            if (branchLength != null && !Double.isFinite(branchLength))
                throw new IllegalArgumentException("Newick branch lengths must be finite");
        }
        public boolean isLeaf() { return children.isEmpty(); }
    }

    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private final String source;
    private final Limits limits;
    private final List<Node> nodes;
    private final Map<String,Integer> leaves;
    private final int[] labelStarts, labelEnds;
    private final List<String> trailingComments;
    private final String metricError;

    private NewickTree(Parser parser) {
        source = parser.source;
        limits = parser.limits;
        List<Node> result = new ArrayList<>(parser.nodes.size());
        Map<String,Integer> identities = new LinkedHashMap<>();
        labelStarts = new int[parser.nodes.size()]; labelEnds = new int[parser.nodes.size()];
        String badMetric = null;
        for (Mutable value : parser.nodes) {
            Node node = new Node(value.id, value.parent, value.children, value.label, value.length, value.comments);
            result.add(node); labelStarts[node.id()] = value.labelStart; labelEnds[node.id()] = value.labelEnd;
            if (node.isLeaf()) {
                if (node.label() == null || node.label().isEmpty())
                    throw new IllegalArgumentException("Newick tips must have nonempty taxon labels");
                if (identities.putIfAbsent(node.label(), node.id()) != null)
                    throw new IllegalArgumentException("Duplicate Newick taxon label: " + node.label());
            }
            if (node.parent() >= 0 && badMetric == null) {
                if (node.branchLength() == null) badMetric = "Missing Newick length on edge to node " + node.id();
                else if (node.branchLength() < 0) badMetric = "Negative Newick length on edge to node " + node.id();
            }
        }
        nodes = List.copyOf(result); leaves = Collections.unmodifiableMap(identities);
        trailingComments = List.copyOf(parser.trailing); metricError = badMetric;
    }

    /** Parse exactly one terminated tree; trailing trees or tokens are errors. */
    public static NewickTree parse(String text) { return parse(text, Limits.defaults()); }
    public static NewickTree parse(String text, Limits limits) {
        Parser parser = new Parser(Objects.requireNonNull(text), Objects.requireNonNull(limits));
        parser.parse(); return new NewickTree(parser);
    }
    public String originalText() { return source; }
    public int root() { return 0; }
    public List<Node> nodes() { return nodes; }
    public Map<String,Integer> leafNodes() { return leaves; }
    public Set<String> taxa() { return leaves.keySet(); }
    public List<String> trailingComments() { return trailingComments; }

    /** Reject incomplete or negative edge lengths rather than inventing distances. */
    public void requireMetricLengths() {
        if (metricError != null) throw new IllegalArgumentException(metricError);
    }

    public double distance(String first, String second) {
        requireMetricLengths(); Integer a = leaves.get(first), b = leaves.get(second);
        if (a == null || b == null) throw new IllegalArgumentException("Unknown Newick taxon in distance request");
        Map<Integer,Double> ancestors = new HashMap<>(); double distance = 0;
        for (int i = a; i >= 0; i = nodes.get(i).parent()) {
            ancestors.put(i, distance);
            if (nodes.get(i).parent() >= 0) distance = finiteSum(distance, nodes.get(i).branchLength());
        }
        distance = 0;
        for (int i = b; i >= 0; i = nodes.get(i).parent()) {
            Double prior = ancestors.get(i);
            if (prior != null) return finiteSum(prior, distance);
            if (nodes.get(i).parent() >= 0) distance = finiteSum(distance, nodes.get(i).branchLength());
        }
        throw new IllegalStateException("A parsed tree must have a common root");
    }

    private static double finiteSum(double left, double right) {
        double value = left + right;
        if (!Double.isFinite(value)) throw new ArithmeticException("Newick path-length sum is not finite");
        return value;
    }

    /**
     * Nontrivial unrooted bipartitions, represented by their smaller taxon side.
     * Equal sides choose the side containing the lexicographically first taxon.
     * The membership budget includes allocated descendant and split sets; it bounds
     * deeply unbalanced trees instead of silently omitting splits.
     */
    public Set<Set<String>> bipartitions(long maximumTaxonMemberships) {
        if (maximumTaxonMemberships < 1) throw new IllegalArgumentException("Split membership capacity must be positive");
        Map<Integer,Set<String>> descendants = new HashMap<>(); Set<Set<String>> result = new LinkedHashSet<>();
        String first = new TreeSet<>(leaves.keySet()).first(); long used = 0;
        for (int i = nodes.size()-1; i >= 0; i--) {
            Node node = nodes.get(i); long count = node.isLeaf() ? 1 : 0;
            for (int child : node.children()) count = Math.addExact(count, descendants.get(child).size());
            used = Math.addExact(used, count);
            if (used > maximumTaxonMemberships)
                throw new IllegalArgumentException("Complete Newick split membership capacity exceeded; raise the declared capacity");
            Set<String> tips = new HashSet<>();
            if (node.isLeaf()) tips.add(node.label());
            else for (int child : node.children()) tips.addAll(descendants.get(child));
            descendants.put(i, tips);
            int size = tips.size(), complement = leaves.size()-size;
            if (node.parent() < 0 || size < 2 || complement < 2) continue;
            used = Math.addExact(used, Math.min(size, complement));
            if (used > maximumTaxonMemberships)
                throw new IllegalArgumentException("Complete Newick split membership capacity exceeded; raise the declared capacity");
            Set<String> canonical = new TreeSet<>();
            if (size < complement || size == complement && tips.contains(first)) canonical.addAll(tips);
            else for (String taxon : leaves.keySet()) if (!tips.contains(taxon)) canonical.add(taxon);
            result.add(Collections.unmodifiableSet(canonical));
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Replace every tip identity exactly once while retaining all other source
     * characters, including branch precision, support labels and annotations.
     */
    public String relabelLeaves(Map<String,String> replacements) {
        return relabelLeaves(replacements, limits.characters());
    }

    public String relabelLeaves(Map<String,String> replacements, int maximumCharacters) {
        replacements = Map.copyOf(Objects.requireNonNull(replacements));
        if (maximumCharacters < 1) throw new IllegalArgumentException("Newick output character capacity must be positive");
        if (!replacements.keySet().equals(leaves.keySet()))
            throw new IllegalArgumentException("Relabeling must cover the complete exact taxon set");
        Set<String> unique = new HashSet<>();
        for (String value : replacements.values()) {
            validateLabel(value);
            if (value.length() > limits.labelCharacters()) throw new IllegalArgumentException("Replacement label character capacity exceeded");
            if (value.isEmpty() || !unique.add(value)) throw new IllegalArgumentException("Replacement taxon labels must be nonempty and unique");
        }
        StringBuilder output = new StringBuilder(); int last = 0;
        for (Node node : nodes) if (node.isLeaf()) {
            int start = labelStarts[node.id()], end = labelEnds[node.id()];
            String label = quoteLabel(replacements.get(node.label()));
            if ((long)output.length()+start-last+label.length() > maximumCharacters)
                throw new IllegalArgumentException("Complete relabeled Newick character capacity exceeded");
            output.append(source, last, start).append(label); last = end;
        }
        if ((long)output.length()+source.length()-last > maximumCharacters)
            throw new IllegalArgumentException("Complete relabeled Newick character capacity exceeded");
        return output.append(source, last, source.length()).toString();
    }

    private static String quoteLabel(String value) {
        boolean quote = false;
        for (int i=0; i<value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || "()[],:;'".indexOf(c) >= 0) { quote = true; break; }
        }
        return quote ? "'" + value.replace("'", "''") + "'" : value;
    }

    private static void validateLabel(String value) {
        Objects.requireNonNull(value, "Taxon label");
        for (int i=0; i<value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) throw new IllegalArgumentException("Control character in Newick label");
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i)))
                    throw new IllegalArgumentException("Unpaired surrogate in Newick label");
            } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("Unpaired surrogate in Newick label");
        }
    }

    private static final class Mutable {
        final int id, parent;
        final List<Integer> children = new ArrayList<>();
        final List<String> comments = new ArrayList<>();
        String label; Double length; int labelStart=-1, labelEnd=-1;
        Mutable(int id, int parent) { this.id=id; this.parent=parent; }
    }

    private static final class Parser {
        final String source; final Limits limits;
        final List<Mutable> nodes = new ArrayList<>(); final List<String> trailing = new ArrayList<>();
        final ArrayDeque<Mutable> stack = new ArrayDeque<>();
        int at, commentCharacters;
        Parser(String source, Limits limits) {
            this.source=source; this.limits=limits;
            if (source.length() > limits.characters()) throw error("character capacity exceeded");
        }
        IllegalArgumentException error(String message) { return new IllegalArgumentException("Newick " + message + " at character " + at); }
        char peek() { return at == source.length() ? '\0' : source.charAt(at); }
        void whitespace() {
            while (at < source.length() && Character.isWhitespace(source.charAt(at))) at++;
            if (at < source.length() && Character.isISOControl(source.charAt(at))) throw error("invalid control character");
        }
        void comments(List<String> output) {
            whitespace();
            while (peek() == '[') {
                int start=at, depth=0;
                do {
                    if (at == source.length()) throw error("unterminated comment");
                    char c=source.charAt(at++);
                    if (c == '[') depth++;
                    else if (c == ']') depth--;
                    else if (c == '\0') throw error("NUL in comment");
                    if (++commentCharacters > limits.commentCharacters()) throw error("comment capacity exceeded");
                } while (depth != 0);
                output.add(source.substring(start,at)); whitespace();
            }
        }
        Mutable create(List<String> leading) {
            if (nodes.size() == limits.nodes()) throw error("node capacity exceeded");
            if (stack.size() > limits.depth()) throw error("depth capacity exceeded");
            Mutable parent=stack.peek(); Mutable node=new Mutable(nodes.size(),parent == null ? -1 : parent.id);
            if (parent != null) parent.children.add(node.id);
            node.comments.addAll(leading); nodes.add(node); return node;
        }
        boolean delimiter(char value) { return value == '\0' || "()[],:;".indexOf(value) >= 0 || Character.isWhitespace(value); }
        void label(Mutable node, boolean required) {
            if (delimiter(peek())) {
                if (required) throw error("missing tip label");
                return;
            }
            node.labelStart=at; StringBuilder text=new StringBuilder();
            if (peek() == '\'') {
                at++; boolean closed=false;
                while (at < source.length()) {
                    char c=source.charAt(at++);
                    if (c == '\'') {
                        if (peek() == '\'') { at++; text.append('\''); }
                        else { closed=true; break; }
                    } else text.append(c);
                    if (text.length() > limits.labelCharacters()) throw error("label capacity exceeded");
                }
                if (!closed) throw error("unterminated quoted label");
            } else {
                while (!delimiter(peek())) {
                    char c=source.charAt(at++);
                    if (c == '\'') throw error("quote embedded in an unquoted label");
                    text.append(c);
                    if (text.length() > limits.labelCharacters()) throw error("label capacity exceeded");
                }
            }
            node.label=text.toString(); validateLabel(node.label); node.labelEnd=at;
        }
        void suffix(Mutable node, boolean internal) {
            comments(node.comments);
            if (internal) { label(node,false); comments(node.comments); }
            if (peek() == ':') {
                at++; comments(node.comments); int start=at;
                while (!delimiter(peek())) at++;
                String number=source.substring(start,at);
                if (!NUMBER.matcher(number).matches()) throw error("requires a finite decimal branch length");
                node.length=Double.valueOf(number);
                if (!Double.isFinite(node.length)) throw error("nonfinite branch length");
                comments(node.comments);
            }
        }
        void parse() {
            boolean subtree=true;
            while (true) {
                if (subtree) {
                    List<String> leading=new ArrayList<>(); comments(leading);
                    Mutable node=create(leading);
                    if (peek() == '(') { at++; stack.push(node); continue; }
                    label(node,true); suffix(node,false); subtree=false;
                }
                if (stack.isEmpty()) {
                    if (peek() != ';') throw error("requires one terminal semicolon");
                    at++; comments(trailing);
                    if (at != source.length()) throw error("trailing tree or token");
                    return;
                }
                if (peek() == ',') { at++; subtree=true; }
                else if (peek() == ')') { at++; Mutable node=stack.pop(); suffix(node,true); }
                else throw error("expected a comma or closing parenthesis; quote whitespace-bearing labels");
            }
        }
    }
}
