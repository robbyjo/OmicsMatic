/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Bounded complete MEME Suite XML reader. Literal decimal strings and element
 * order are retained; no probability is normalized and no external resource is
 * opened. Parsing alone does not establish process completion or scientific fit.
 */
public final class MemeXml {
    private MemeXml() { }

    /**
     * Explicit input/tree capacities, not a JVM heap guarantee.
     * @param maximumBytes maximum raw UTF-8 bytes
     * @param maximumCharacters maximum decoded UTF-16 characters
     * @param maximumElements maximum complete elements
     * @param maximumDepth maximum nested element depth
     * @param maximumAttributes maximum attributes per element
     */
    public record Limits(int maximumBytes, int maximumCharacters, int maximumElements,
                         int maximumDepth, int maximumAttributes) {
        /** Rejects nonpositive or unsupported capacities. */
        public Limits {
            if (maximumBytes < 1 || maximumBytes > 268435456 || maximumCharacters < 1
                    || maximumCharacters > 268435456 || maximumElements < 1 || maximumElements > 2000000
                    || maximumDepth < 1 || maximumDepth > 256 || maximumAttributes < 1 || maximumAttributes > 1024)
                throw new IllegalArgumentException("Invalid explicit MEME XML capacities");
        }
    }

    /**
     * Complete element; child order, attribute order and literal text are retained.
     * Non-whitespace mixed content and namespaces are unsupported and rejected.
     * @param name original unqualified element name
     * @param attributes immutable original attributes
     * @param text literal decoded text, including surrounding whitespace
     * @param children ordered complete child elements
     */
    public record Node(String name, Map<String,String> attributes, String text, List<Node> children) {
        /** Makes defensive copies. */
        public Node {
            Objects.requireNonNull(name); Objects.requireNonNull(text);
            attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
            children = List.copyOf(children);
        }
        /**
         * Returns exactly one direct named child, refusing missing/duplicate nodes.
         * @param child requested original name
         * @return complete direct child
         * @throws IOException on missing or duplicate children
         */
        public Node one(String child) throws IOException {
            List<Node> values = named(child);
            if (values.size() != 1) throw new IOException("Require exactly one MEME XML " + name + "/" + child);
            return values.get(0);
        }
        /**
         * Returns all direct children with the exact name, in original order.
         * @param child requested original name
         * @return immutable matching children
         */
        public List<Node> named(String child) { return children.stream().filter(n -> n.name().equals(child)).toList(); }
        /**
         * Returns a required original attribute.
         * @param key attribute name
         * @return unchanged value
         * @throws IOException if absent
         */
        public String attribute(String key) throws IOException {
            String value = attributes.get(key);
            if (value == null) throw new IOException("Missing MEME XML attribute " + name + "/@" + key);
            return value;
        }
    }

    /**
     * Complete raw document identity and parsed tree; no analysis admission.
     * @param root complete root element
     * @param bytes raw UTF-8 bytes consumed
     * @param sha256 complete original raw SHA-256
     * @param elements complete elements consumed
     * @param internalMemeDtd whether the original declaration-only MEME DTD was retained
     */
    public record Document(Node root, int bytes, String sha256, int elements, boolean internalMemeDtd) { }

    /**
     * Reads a complete unchanged regular UTF-8 XML file with no external entity,
     * namespace, processing-instruction or mixed-content support. The original
     * MEME internal element/attribute declaration DTD is allowed, without expansion.
     * @param path original or captured regular file
     * @param limits explicit positive capacities
     * @param cancellation caller cancellation check
     * @return complete document receipt
     * @throws IOException on malformed, changed, truncated, unsupported or excessive XML
     */
    public static Document read(Path path, Limits limits, Runnable cancellation) throws IOException {
        Objects.requireNonNull(path); Objects.requireNonNull(limits); Objects.requireNonNull(cancellation);
        cancellation.run();
        BasicFileAttributes before = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || before.size() > limits.maximumBytes()) throw new IOException("Require bounded regular MEME XML");
        byte[] raw;
        try (var input = Files.newInputStream(path)) { raw = input.readNBytes(Math.addExact(limits.maximumBytes(), 1)); }
        BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (raw.length > limits.maximumBytes() || raw.length != before.size() || !after.isRegularFile()
                || after.size() != before.size() || !after.lastModifiedTime().equals(before.lastModifiedTime())
                || !Objects.equals(after.fileKey(), before.fileKey())) throw new IOException("MEME XML changed or exceeded raw capacity");
        cancellation.run();
        String text;
        try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString(); }
        catch (CharacterCodingException malformed) { throw new IOException("MEME XML must be complete UTF-8", malformed); }
        if (text.length() > limits.maximumCharacters()) throw new IOException("MEME XML decoded-character capacity exceeded");
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> { throw new XMLStreamException("External XML resource forbidden"); });
        var stack = new ArrayDeque<Builder>(); Node root = null; int count = 0; boolean dtd = false;
        XMLStreamReader reader = null;
        try {
            reader = factory.createXMLStreamReader(new StringReader(text));
            if (reader.getVersion() != null && !reader.getVersion().equals("1.0")) throw new IOException("Require XML 1.0");
            String encoding = reader.getCharacterEncodingScheme();
            if (encoding != null && !encoding.equalsIgnoreCase("UTF-8")) throw new IOException("Require declared UTF-8 XML");
            while (reader.hasNext()) {
                cancellation.run(); int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if (++count > limits.maximumElements() || stack.size() >= limits.maximumDepth()) throw new IOException("MEME XML element/depth capacity exceeded");
                    if (reader.getNamespaceCount() != 0 || present(reader.getNamespaceURI()) || present(reader.getPrefix())) throw new IOException("MEME XML namespaces are unsupported");
                    if (stack.isEmpty() && root != null) throw new IOException("Repeated MEME XML root");
                    if (reader.getAttributeCount() > limits.maximumAttributes()) throw new IOException("MEME XML attribute capacity exceeded");
                    var attributes = new LinkedHashMap<String,String>();
                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        if (present(reader.getAttributeNamespace(i)) || present(reader.getAttributePrefix(i))) throw new IOException("Qualified MEME XML attribute unsupported");
                        if (attributes.putIfAbsent(reader.getAttributeLocalName(i), reader.getAttributeValue(i)) != null) throw new IOException("Duplicate MEME XML attribute");
                    }
                    stack.push(new Builder(reader.getLocalName(), attributes));
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (stack.isEmpty()) throw new IOException("Unexpected MEME XML element end");
                    Builder complete = stack.pop();
                    if (!complete.name.equals(reader.getLocalName())) throw new IOException("Mismatched MEME XML end");
                    if (!complete.children.isEmpty() && !complete.text.toString().isBlank()) throw new IOException("MEME XML mixed content unsupported");
                    Node node = new Node(complete.name, complete.attributes, complete.text.toString(), complete.children);
                    if (stack.isEmpty()) root = node; else stack.peek().children.add(node);
                } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA || event == XMLStreamConstants.SPACE) {
                    if (stack.isEmpty()) { if (!reader.isWhiteSpace()) throw new IOException("Text outside MEME XML root"); }
                    else stack.peek().text.append(reader.getText());
                } else if (event == XMLStreamConstants.DTD) {
                    String declaration = reader.getText();
                    if (dtd || root != null || !stack.isEmpty() || !declaration.matches("(?s)<!DOCTYPE\\s+MEME\\s*\\[.*]>")) throw new IOException("Only the original internal MEME declaration DTD is supported");
                    if (declaration.contains("<!ENTITY") || declaration.contains(" SYSTEM ") || declaration.contains(" PUBLIC ")) throw new IOException("MEME XML entity/external DTD forbidden");
                    dtd = true;
                } else if (event == XMLStreamConstants.ENTITY_REFERENCE || event == XMLStreamConstants.PROCESSING_INSTRUCTION)
                    throw new IOException("MEME XML entity reference or processing instruction forbidden");
            }
            if (root == null || !stack.isEmpty()) throw new IOException("Incomplete MEME XML document");
            if (dtd && !root.name().equals("MEME")) throw new IOException("MEME DTD/root mismatch");
        } catch (XMLStreamException malformed) { throw new IOException("Invalid complete MEME XML", malformed); }
        finally { if (reader != null) try { reader.close(); } catch (XMLStreamException failed) { throw new IOException("MEME XML close failed", failed); } }
        cancellation.run();
        String digest = digest(raw);
        requireUnchanged(path, before, digest, limits.maximumBytes(), cancellation);
        return new Document(root, raw.length, digest, count, dtd);
    }
    private static String digest(byte[] raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void requireUnchanged(Path path, BasicFileAttributes original, String expected, int maximum, Runnable check) throws IOException {
        MessageDigest hash;
        try { hash = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        long bytes = 0; byte[] buffer = new byte[65536];
        try (var input = Files.newInputStream(path)) {
            for (int n; (n = input.read(buffer)) != -1;) {
                check.run(); bytes = Math.addExact(bytes, n);
                if (bytes > maximum) throw new IOException("MEME XML grew beyond raw capacity during finalization");
                hash.update(buffer, 0, n);
            }
        }
        BasicFileAttributes end = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (bytes != original.size() || !end.isRegularFile() || end.size() != original.size()
                || !end.lastModifiedTime().equals(original.lastModifiedTime()) || !Objects.equals(end.fileKey(), original.fileKey())
                || !HexFormat.of().formatHex(hash.digest()).equals(expected)) throw new IOException("MEME XML changed before complete receipt");
    }
    private static boolean present(String value) { return value != null && !value.isEmpty(); }
    private static final class Builder {
        final String name; final Map<String,String> attributes; final StringBuilder text = new StringBuilder(); final List<Node> children = new ArrayList<>();
        Builder(String name, Map<String,String> attributes) { this.name = name; this.attributes = attributes; }
    }
}
