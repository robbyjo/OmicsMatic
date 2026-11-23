/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** Bounded NPY primitive context arrays, preserving axes, layout and payload bits. */
final class SquidpyWorkflowNpy {
    private SquidpyWorkflowNpy() { }

    record Array(List<Integer> shape, String descriptor, String dtype, boolean fortran,
                 long elements, long payloadBytes, String payloadSha256) { }

    static Array inspect(Path file, int maximumHeaderBytes, long maximumFileBytes,
                         long maximumElements) throws IOException {
        if (maximumHeaderBytes < 1 || maximumFileBytes < 1 || maximumElements < 0)
            throw new IOException("Positive NPY header/file capacities and nonnegative element capacity required");
        var before=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || before.isSymbolicLink()) throw new IOException("Regular captured NPY file required");
        if (before.size()>maximumFileBytes) throw new IOException("Context array exceeds declared member byte capacity");
        try (var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)))) {
            if (!Arrays.equals(in.readNBytes(6),new byte[]{(byte)147,78,85,77,80,89})) throw new IOException("Context NPY signature differs");
            int major=in.readUnsignedByte(),minor=in.readUnsignedByte();
            if (minor!=0 || major<1 || major>3) throw new IOException("NPY1.0,2.0 or3.0 required");
            long length=major==1?Short.toUnsignedInt(Short.reverseBytes(in.readShort())):Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
            if (length<1 || length>maximumHeaderBytes) throw new IOException("Context NPY header exceeds capacity");
            byte[] raw=in.readNBytes(Math.toIntExact(length));
            if (raw.length!=length) throw new EOFException("Truncated context NPY header");
            if (raw[raw.length-1]!='\n') throw new IOException("NPY header must end with a newline");
            var header=new Header(new String(raw,major==3?StandardCharsets.UTF_8:StandardCharsets.ISO_8859_1));header.parse();
            String descriptor=header.descriptor;
            if (!descriptor.matches("[<>=|][fiub][1248]")) throw new IOException("Explicit primitive NPY descriptor required");
            int units;
            try { units=Integer.parseInt(descriptor.substring(2)); }
            catch (NumberFormatException e) { throw new IOException("Invalid primitive NPY item size",e); }
            char kind=descriptor.charAt(1);
            boolean primitive=(kind=='f'&&(units==4||units==8)) || ((kind=='i'||kind=='u')&&Set.of(1,2,4,8).contains(units)) || (kind=='b'&&units==1);
            if (!primitive || (descriptor.charAt(0)=='|'&&units!=1)) throw new IOException("Primitive bool,integer or float32/64 NPY required; object,structured,string and complex arrays are not decoded");
            long elements=1,payload,expected;
            try {
                for (int dimension:header.shape) elements=Math.multiplyExact(elements,dimension);
                payload=Math.multiplyExact(elements,units);
                expected=Math.addExact(Math.addExact(major==1?10L:12L,length),payload);
            } catch (ArithmeticException e) { throw new IOException("Context NPY dimensions overflow byte capacity",e); }
            if (elements>maximumElements) throw new IOException("Context NPY exceeds declared entry capacity");
            if (before.size()!=expected) throw new IOException("Context NPY complete payload size differs from shape/type; truncated or trailing bytes");
            MessageDigest digest;
            try { digest=MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
            byte[] buffer=new byte[65536];long remaining=payload;
            while (remaining>0) {
                int count=in.read(buffer,0,(int)Math.min(buffer.length,remaining));
                if (count<0) throw new EOFException("Truncated context NPY payload");
                digest.update(buffer,0,count);remaining-=count;
            }
            if (in.read()!=-1) throw new IOException("Trailing context NPY bytes");
            var after=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if (!after.isRegularFile() || before.size()!=after.size() || !Objects.equals(before.fileKey(),after.fileKey()) || !before.lastModifiedTime().equals(after.lastModifiedTime()))
                throw new IOException("Context NPY changed during inspection");
            String dtype=kind=='b'?"bool":(kind=='f'?"float":kind=='u'?"uint":"int")+(units*8);
            return new Array(List.copyOf(header.shape),descriptor,dtype,header.fortran,elements,payload,HexFormat.of().formatHex(digest.digest()));
        }
    }

    private static final class Header {
        final String text;int position;String descriptor;boolean fortran;List<Integer> shape;
        Header(String text) { this.text=text; }
        void parse() throws IOException {
            take('{');var keys=new HashSet<String>();
            while (peek()!='}') {
                String key=string();if (!keys.add(key)) throw new IOException("Duplicate context NPY header key");take(':');
                switch (key) {
                    case "descr" -> descriptor=string();
                    case "fortran_order" -> {
                        if (literal("True")) fortran=true;
                        else if (literal("False")) fortran=false;
                        else throw new IOException("Literal NPY layout boolean required");
                    }
                    case "shape" -> {
                        take('(');shape=new ArrayList<>();boolean comma=false;
                        while (peek()!=')') {
                            int start=position;while (position<text.length() && text.charAt(position)>='0' && text.charAt(position)<='9') position++;
                            try { if (position==start) throw new NumberFormatException();shape.add(Integer.parseInt(text.substring(start,position))); }
                            catch (NumberFormatException e) { throw new IOException("Bounded nonnegative NPY dimensions required",e); }
                            if (shape.size()>16) throw new IOException("At most16 context-array dimensions are supported");
                            if (peek()!=',') break;take(',');comma=true;
                        }
                        take(')');if (shape.size()==1&&!comma) throw new IOException("One-dimensional NPY shape requires a tuple comma");
                    }
                    default -> throw new IOException("Unexpected context NPY header key");
                }
                if (peek()!=',') break;take(',');
            }
            take('}');skip();
            if (position!=text.length() || !keys.equals(Set.of("descr","fortran_order","shape"))) throw new IOException("Incomplete or trailing context NPY header expression");
        }
        boolean literal(String value) { skip();if (!text.startsWith(value,position)) return false;position+=value.length();return true; }
        String string() throws IOException {
            char quote=peek();if (quote!='\''&&quote!='"') throw new IOException("Literal context NPY string required");position++;var out=new StringBuilder();
            while (position<text.length()) {
                char c=text.charAt(position++);if (c==quote) return out.toString();
                if (c=='\\') { if (position==text.length()) break;c=text.charAt(position++);if (c!=quote&&c!='\\') throw new IOException("Unsupported context NPY string escape"); }
                out.append(c);
            }
            throw new IOException("Unterminated context NPY string");
        }
        void skip() { while (position<text.length()&&Character.isWhitespace(text.charAt(position))) position++; }
        char peek() throws IOException { skip();if (position==text.length()) throw new EOFException("Truncated context NPY expression");return text.charAt(position); }
        void take(char expected) throws IOException { if (peek()!=expected) throw new IOException("Unexpected context NPY expression");position++; }
    }
}
