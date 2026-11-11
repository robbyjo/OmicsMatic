/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.phylogeny;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * An owned, streaming collection of complete semicolon-terminated Newick trees.
 * A returned tree is provisional evidence about that record only: the collection
 * is complete only after {@link #nextTree()} has returned null and close succeeds.
 * A bad later record, decoding failure, read failure or capacity failure prevents
 * collection admission. No first-tree-only or truncated collection is certified.
 * Record text includes preceding whitespace/comments and ends at its semicolon;
 * final document whitespace/comments are exposed separately by trailingText().
 */
public final class NewickReader implements AutoCloseable {
    /** Complete-document limits, in UTF-16 characters and number of trees. */
    public record Limits(NewickTree.Limits perTree, long characters, long trees) {
        public Limits {
            Objects.requireNonNull(perTree);
            if (characters < 1 || trees < 1)
                throw new IllegalArgumentException("Newick collection capacities must be positive");
        }
        public static Limits defaults() {
            return new Limits(NewickTree.Limits.defaults(), 8_589_934_592L, 1_000_000);
        }
    }

    private final Reader source;
    private final Limits limits;
    private long characters, trees;
    private boolean eof, closed;
    private Throwable failure;
    private String trailing;

    /** Takes ownership of the supplied reader, including on a later parse failure. */
    public NewickReader(Reader source, Limits limits) {
        Objects.requireNonNull(source);
        this.limits = Objects.requireNonNull(limits);
        this.source = source instanceof BufferedReader ? source : new BufferedReader(source);
    }

    public NewickReader(Reader source) { this(source, Limits.defaults()); }

    /** Open plain UTF-8 with malformed and unmappable input treated as failures. */
    public static NewickReader open(Path path, Limits limits) throws IOException {
        Objects.requireNonNull(limits);
        var decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        return new NewickReader(new InputStreamReader(Files.newInputStream(path), decoder), limits);
    }

    public long charactersRead() { return characters; }
    public long treesRead() { return trees; }

    /** EOF must be reached and every read, parse, budget and close must succeed. */
    public boolean isComplete() { return eof && closed && failure == null; }

    public String trailingText() {
        if (!isComplete()) throw new IllegalStateException("Newick collection has not reached verified complete EOF", failure);
        return trailing;
    }

    /** Returns one complete tree, or null only after verifying the full remaining tail. */
    public NewickTree nextTree() throws IOException {
        if (failure != null) throw new IllegalStateException("Newick collection previously failed; no complete collection is available", failure);
        if (closed) throw new IllegalStateException("Newick collection reader is closed");
        if (eof) return null;
        try {
            StringBuilder text = new StringBuilder();
            boolean quote = false, token = false;
            int commentDepth = 0, commentCharacters = 0;
            while (true) {
                int next = source.read();
                if (next < 0) {
                    if (commentDepth != 0 || quote || token)
                        throw new EOFException("Incomplete Newick collection record at character " + characters + "; require a complete terminated tree or balanced document comments");
                    eof = true; trailing = text.toString(); return null;
                }
                characters = Math.incrementExact(characters);
                if (characters > limits.characters())
                    throw new IllegalArgumentException("Complete Newick collection character capacity exceeded; raise the declared capacity");
                if (text.length() == limits.perTree().characters())
                    throw new IllegalArgumentException("Complete Newick collection record character capacity exceeded; raise the declared per-tree capacity");
                char c = (char)next;
                text.append(c);
                if (c == '\0') throw new IllegalArgumentException("NUL in Newick collection at character " + characters);
                if (commentDepth != 0 || !quote && c == '[') {
                    if (++commentCharacters > limits.perTree().commentCharacters())
                        throw new IllegalArgumentException("Newick collection comment capacity exceeded");
                }
                if (commentDepth != 0) {
                    if (c == '[') commentDepth = Math.incrementExact(commentDepth);
                    else if (c == ']') commentDepth--;
                } else if (quote) {
                    // Adjacent escaped apostrophes close/reopen without exposing delimiters.
                    if (c == '\'') quote = false;
                } else if (c == '[') commentDepth = 1;
                else if (c == '\'') { quote = true; token = true; }
                else if (c == ';') {
                    if (trees == limits.trees())
                        throw new IllegalArgumentException("Complete Newick collection tree capacity exceeded; raise the declared capacity");
                    NewickTree tree = NewickTree.parse(text.toString(), limits.perTree());
                    trees++; return tree;
                } else if (!Character.isWhitespace(c)) token = true;
                else if (Character.isISOControl(c) && c != '\r' && c != '\n' && c != '\t' && c != '\f')
                    throw new IllegalArgumentException("Invalid control character in Newick collection");
            }
        } catch (IOException | RuntimeException error) {
            failure = error; throw error;
        }
    }

    /** Closing before verified EOF preserves the incomplete state; close errors invalidate it. */
    @Override public void close() throws IOException {
        if (closed) return;
        closed = true;
        try { source.close(); }
        catch (IOException | RuntimeException error) {
            if (failure == null) failure = error;
            else if (failure != error) failure.addSuppressed(error);
            throw error;
        }
    }
}
