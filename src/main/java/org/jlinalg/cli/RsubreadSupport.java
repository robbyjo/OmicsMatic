/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.jlinalg.sequence.RsubreadCountMatrix.Name;

/** Closed typed IO for the Rsubread adapter; no user code or shell expressions. */
final class RsubreadSupport {
    private RsubreadSupport() { }
    static final String ACTION = "Read WARNINGS.md and original stdout/stderr. SKIP3: permit/provide the declared exact runtime, then bind NEW paths; otherwise skip dependent analysis. FAIL2: preserve inputs and all partial outputs, correct the named cause and retry NEW output paths. Never refresh scientific hashes or substitute an edition automatically.";
    static final class Unavailable extends IOException {
        private static final long serialVersionUID = 1L;
        Unavailable(String message) { super(message); }
        Unavailable(String message, Throwable cause) { super(message, cause); }
    }
    static Map<String,Object> object(Object value) throws IOException {
        if (!(value instanceof Map<?,?> raw)) throw new IOException("Expected JSON object");
        Map<String,Object> result = new LinkedHashMap<>();
        for (var e : raw.entrySet()) { if (!(e.getKey() instanceof String name)) throw new IOException("Non-string object key"); result.put(name,e.getValue()); }
        return result;
    }
    static List<?> list(Object value) throws IOException { if (!(value instanceof List<?> result)) throw new IOException("Expected JSON array"); return result; }
    static String text(Object value) throws IOException {
        if (!(value instanceof String result) || result.isBlank()) throw new IOException("Expected nonblank text");
        try { Name.literal(result); } catch (IllegalArgumentException e) { throw new IOException("Invalid Unicode text",e); }
        return result;
    }
    static void keys(Map<String,Object> value, Set<String> allowed) throws IOException {
        for (String key : value.keySet()) if (!allowed.contains(key)) throw new IOException("Unknown field: "+key);
    }
    static long integer(Object value, long min, long max) throws IOException {
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue()!=n.longValue() || n.longValue()<min || n.longValue()>max)
            throw new IOException("Expected integer in ["+min+","+max+"]"); return n.longValue();
    }
    static boolean bool(Object value) throws IOException { if (!(value instanceof Boolean result)) throw new IOException("Expected boolean"); return result; }
    static double real(Object value, double min, double max) throws IOException {
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue()<min || n.doubleValue()>max) throw new IOException("Expected bounded finite number"); return n.doubleValue();
    }
    static Map<String,Object> read(Path path) throws IOException {
        if (!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.size(path)>16000000) throw new IOException("Require bounded regular JSON file: "+path);
        return object(SimpleJson.parse(Files.readString(path,StandardCharsets.UTF_8)));
    }
    static Path resolve(Path directory, String value) throws IOException {
        text(value); if (value.chars().anyMatch(c->c<32 || c==127)) throw new IOException("Control character in file path");
        Path path=Path.of(value); return (path.isAbsolute()?path:directory.resolve(path)).toAbsolutePath().normalize();
    }
    static Path contained(Path root, String value) throws IOException {
        Path path=Path.of(value);
        if (path.isAbsolute() || !path.normalize().equals(path) || value.contains("\\") || value.contains(":") || path.startsWith("..")) throw new IOException("Unsafe relative capture path");
        Path result=root.resolve(path).normalize(); if (!result.startsWith(root)) throw new IOException("Capture escapes root"); return result;
    }
    static String hash(Path path) throws IOException {
        MessageDigest digest;
        try { digest=MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
        try (InputStream input=Files.newInputStream(path)) { byte[] bytes=new byte[65536]; for(int n;(n=input.read(bytes))!=-1;) { checkInterrupt();digest.update(bytes,0,n); } }
        return HexFormat.of().formatHex(digest.digest());
    }
    static String hash(byte[] raw) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)); } catch(NoSuchAlgorithmException e) { throw new AssertionError(e); } }
    static Map<String,Object> pin(Path path) throws IOException { return Map.of("bytes",Files.size(path),"sha256",hash(path)); }
    static void checkPin(Path path,Object expected) throws IOException {
        if (!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || !pin(path).equals(object(expected))) throw new IOException("File changed/unavailable: "+path);
    }
    static Map<String,Object> inventory(Path root) throws IOException {
        if (!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing regular directory: "+root);
        Map<String,Object> result=new TreeMap<>();
        try (var paths=Files.walk(root)) {
            var iterator=paths.iterator();while(iterator.hasNext()) {
                checkInterrupt();Path path=iterator.next();
                if (Files.isSymbolicLink(path)) throw new IOException("Links are not captured runtime/output files: "+path);
                if (Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)) continue;
                if (!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || result.size()>=100000) throw new IOException("Unsupported/capacity-exceeded file population");
                result.put(root.relativize(path).toString().replace('\\','/'),pin(path));
            }
        }return result;
    }
    static void checkInterrupt() throws java.io.InterruptedIOException { if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Operation interrupted; no result admitted"); }
    static void cancel(Path root) throws IOException { checkInterrupt();if(Files.exists(root.resolve("CANCEL"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Cancellation requested; no result admitted"); }
    static void json(Path path,Object value) throws IOException { Files.writeString(path,encode(value)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW); }
    static String encode(Object value) throws IOException {
        if(value==null)return "null";
        if(value instanceof String s) {
            Name.literal(s);var out=new StringBuilder("\"");for(char c:s.toCharArray()) switch(c) {
                case '"'->out.append("\\\"");case '\\'->out.append("\\\\");case '\n'->out.append("\\n");case '\r'->out.append("\\r");case '\t'->out.append("\\t");
                default->{if(c<32)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);}
            }return out.append('"').toString();
        }
        if(value instanceof Boolean)return value.toString();
        if(value instanceof Number n) { if(!Double.isFinite(n.doubleValue()))throw new IOException("Nonfinite JSON number");return n.toString(); }
        if(value instanceof Map<?,?> map) { var items=new ArrayList<String>();for(var e:map.entrySet())items.add(encode(e.getKey())+":"+encode(e.getValue()));return "{"+String.join(",",items)+"}"; }
        if(value instanceof Collection<?> list) { var items=new ArrayList<String>();for(Object v:list)items.add(encode(v));return "["+String.join(",",items)+"]"; }
        throw new IOException("Unsupported JSON value");
    }
    static void warnings(Path root,String status,String detail) throws IOException {
        Files.writeString(root.resolve("WARNINGS.md"),"# "+status+"\n\n"+detail+"\n\nRequired action: "+ACTION+"\n\nOriginal algorithms execute upstream. Structural checks do not independently recount alignments/junctions or identify a paper binary.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
}
