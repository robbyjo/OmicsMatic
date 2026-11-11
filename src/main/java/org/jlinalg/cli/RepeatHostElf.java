/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Bounded ELF64/x86-64 metadata and complete reported ldd bindings; not a runtime-health certificate.
 * See the ELF gABI chapters 2, 7 and 8 at https://gabi.xinuos.com/elf/ . */
final class RepeatHostElf {
    private RepeatHostElf() { }
    record Image(int type, String interpreter, List<String> neededNames) {
        Image { neededNames=List.copyOf(neededNames); }
        int neededEntries(){return neededNames.size();}
    }
    record Binding(String name, String path, boolean loader) { }
    record Report(List<Binding> bindings, boolean virtualDso, boolean noLibraryBindings) {
        Report { bindings = List.copyOf(bindings); }
    }
    private record State(Object key, long size, Object modified, Object created, Object changed) { }
    private record Segment(long virtualAddress,long offset,long bytes) { }
    private static final Pattern NAMED = Pattern.compile("^(\\S+)\\s+=>\\s+(/\\S+)\\s+\\(0x[0-9a-fA-F]+\\)$");
    private static final Pattern LOADER = Pattern.compile("^(/\\S+)\\s+\\(0x[0-9a-fA-F]+\\)$");
    private static final Pattern VDSO = Pattern.compile("^linux-vdso\\.so\\.1\\s+\\(0x[0-9a-fA-F]+\\)$");

    static Image image(Path path, long maximumBytes, Runnable check) throws IOException {
        if (!path.isAbsolute() || !path.normalize().equals(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !path.toRealPath().equals(path)) throw new IOException("ELF inspection requires a canonical regular file");
        State before = state(path); if (before.size() > maximumBytes || maximumBytes < 1) throw new IOException("Complete ELF file exceeds its declared capacity");
        Image result = null;
        try (var input = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer header = read(input, 0, (int) Math.min(64, before.size()), before.size(), check);
            if (header.remaining() >= 4 && header.get(0) == 127 && header.get(1) == 'E' && header.get(2) == 'L' && header.get(3) == 'F') {
                if (header.remaining() != 64 || header.get(4) != 2 || header.get(5) != 1 || header.get(6) != 1)
                    throw new IOException("This host profile requires complete little-endian ELF64 version1 headers");
                int type = Short.toUnsignedInt(header.getShort(16));
                if ((type != 2 && type != 3) || Short.toUnsignedInt(header.getShort(18)) != 62 || header.getInt(20) != 1
                        || Short.toUnsignedInt(header.getShort(52)) != 64)
                    throw new IOException("This host profile requires x86-64 executable/shared ELF images");
                long offset = header.getLong(32); int width = Short.toUnsignedInt(header.getShort(54)), count = Short.toUnsignedInt(header.getShort(56));
                if (offset < 64 || width != 56 || count < 1 || count > 4096) throw new IOException("Unsupported or unbounded ELF program header table");
                range(offset, (long) width * count, before.size()); String interpreter = null; boolean dynamic = false;
                var loads=new ArrayList<Segment>();var neededOffsets=new ArrayList<Long>();Long stringAddress=null,stringBytes=null;
                for (int i = 0; i < count; i++) {
                    ByteBuffer entry = read(input, offset + (long) width * i, width, before.size(), check);
                    int kind = entry.getInt(0); long start = entry.getLong(8), bytes = entry.getLong(32);
                    if(kind==1&&bytes>0){
                        range(start,bytes,before.size());long address=entry.getLong(16);
                        if(address<0)throw new IOException("Unsupported unsigned ELF load address");loads.add(new Segment(address,start,bytes));
                    }else if (kind == 3) {
                        if (interpreter != null || bytes < 2 || bytes > 4096) throw new IOException("Invalid or repeated ELF interpreter segment");
                        ByteBuffer raw = read(input, start, (int) bytes, before.size(), check);
                        if (raw.get(raw.limit() - 1) != 0) throw new IOException("ELF interpreter is not NUL terminated");
                        raw.limit(raw.limit() - 1); interpreter = libraryPath(utf8(raw));
                    } else if (kind == 2) {
                        if (dynamic || bytes < 16 || bytes > 1048576 || bytes % 16 != 0) throw new IOException("Invalid or unbounded ELF dynamic segment");
                        dynamic = true; range(start, bytes, before.size()); boolean end = false;
                        for (long position = 0; position < bytes; position += 16) {
                            ByteBuffer tag = read(input, start + position, 16, before.size(), check);
                            if (tag.getLong(0) == 0) { end = true; break; }
                            long value=tag.getLong(8);
                            if(tag.getLong(0)==1){
                                if(neededOffsets.size()>=4096)throw new IOException("ELF needed-entry capacity exceeded");neededOffsets.add(value);
                            }else if(tag.getLong(0)==5){
                                if(stringAddress!=null)throw new IOException("Duplicate ELF dynamic string-table address");stringAddress=value;
                            }else if(tag.getLong(0)==10){
                                if(stringBytes!=null)throw new IOException("Duplicate ELF dynamic string-table size");stringBytes=value;
                            }
                        }
                        if (!end) throw new IOException("ELF dynamic segment has no complete DT_NULL terminator");
                    }
                }
                var neededNames=new ArrayList<String>();
                if(!neededOffsets.isEmpty()){
                    if(stringAddress==null||stringBytes==null||stringAddress<0||stringBytes<1)throw new IOException("Needed ELF libraries lack a complete dynamic string table");
                    Long stringOffset=null;
                    for(Segment load:loads)if(stringAddress>=load.virtualAddress()&&stringBytes<=load.bytes()
                            &&stringAddress-load.virtualAddress()<=load.bytes()-stringBytes){
                        long candidate=load.offset()+(stringAddress-load.virtualAddress());
                        if(stringOffset!=null&&stringOffset!=candidate)throw new IOException("Ambiguous ELF string-table mapping");stringOffset=candidate;
                    }
                    if(stringOffset==null)throw new IOException("ELF dynamic string table is outside file-backed load segments");
                    range(stringOffset,stringBytes,before.size());
                    for(long nameOffset:neededOffsets){
                        if(nameOffset<0||nameOffset>=stringBytes)throw new IOException("ELF needed name escapes its string table");
                        ByteBuffer name=read(input,stringOffset+nameOffset,(int)Math.min(4097,stringBytes-nameOffset),before.size(),check);int end=-1;
                        for(int n=0;n<name.limit();n++)if(name.get(n)==0){end=n;break;}
                        if(end<1||end>4096)throw new IOException("ELF needed name is empty,oversized or not NUL terminated");
                        name.limit(end);String value=utf8(name);
                        if(value.chars().anyMatch(c->c<33||c==127))throw new IOException("Unsupported ELF needed-library name");neededNames.add(value);
                    }
                }
                result = new Image(type, interpreter, neededNames);
            }
        }
        if (!before.equals(state(path))) throw new IOException("ELF image changed across metadata inspection");
        check.run(); return result;
    }
    static Report bindings(Image image, int exitCode, byte[] stdout, byte[] stderr) throws IOException {
        Objects.requireNonNull(image, "Inspected ELF metadata");
        if (stdout.length > 1048576 || stderr.length > 1048576) throw new IOException("Complete library probe output exceeds its capacity");
        String out = utf8(ByteBuffer.wrap(stdout)).strip(), err = utf8(ByteBuffer.wrap(stderr)).strip();
        String combined = (out + "\n" + err).strip();
        if (List.of("statically linked", "not a dynamic executable").contains(combined)) {
            if ((exitCode != 0 && exitCode != 1) || image.neededEntries() != 0) throw new IOException("Library probe status/static claim conflicts with ELF needed entries");
            return new Report(List.of(), false, true);
        }
        if (exitCode != 0 || !err.isEmpty() || out.isEmpty()) throw new IOException("Incomplete/failed library binding probe;inspect retained stdout/stderr and exit code");
        var result = new ArrayList<Binding>(); var names = new HashSet<String>(); boolean vdso = false;
        for (String raw : out.split("\n", -1)) {
            String line = raw.strip();
            if (line.isEmpty() || line.length() > 8192 || result.size() >= 4096) throw new IOException("Invalid or excessive library binding row");
            if (VDSO.matcher(line).matches()) {
                if (vdso) throw new IOException("Duplicate virtual DSO binding"); vdso = true; continue;
            }
            var named = NAMED.matcher(line); var loader = LOADER.matcher(line); Binding binding;
            if (named.matches()) {
                String name=named.group(1);
                if(name.length()>4096||name.chars().anyMatch(c->c<33||c==127))throw new IOException("Unsupported library binding name");
                binding = new Binding(name, libraryPath(named.group(2)), false);
            }
            else if (loader.matches()) { String path = libraryPath(loader.group(1)); binding = new Binding("@loader:" + path, path, true); }
            else throw new IOException("Unrecognized or missing complete library binding: " + line);
            if (!names.add(binding.name())) throw new IOException("Duplicate library binding name: " + binding.name());
            result.add(binding);
        }
        if(result.isEmpty()&&(!vdso||image.neededEntries()>0))throw new IOException("ELF needed libraries are absent from the complete binding report");
        for(String needed:image.neededNames()){
            boolean present=result.stream().anyMatch(binding->binding.name().equals(needed)||binding.path().equals(needed)
                ||(!needed.contains("/")&&binding.path().substring(binding.path().lastIndexOf('/')+1).equals(needed)));
            if(!present)throw new IOException("Required ELF library is absent from the complete binding report: "+needed);
        }
        return new Report(result, vdso, result.isEmpty());
    }
    private static String libraryPath(String value) throws IOException {
        // These are Linux image/report paths, even when metadata tests run on Windows.
        if (!value.startsWith("/") || value.length()<2 || value.length()>4096 || value.endsWith("/")
                || value.chars().anyMatch(c->c<33||c==127))
            throw new IOException("Unsupported library binding path");
        return value;
    }
    private static ByteBuffer read(FileChannel input, long offset, int count, long size, Runnable check) throws IOException {
        range(offset, count, size); ByteBuffer buffer = ByteBuffer.allocate(count).order(ByteOrder.LITTLE_ENDIAN); input.position(offset);
        while (buffer.hasRemaining()) { check.run(); if (input.read(buffer) < 0) throw new IOException("Truncated ELF metadata"); }
        buffer.flip(); return buffer;
    }
    private static void range(long offset, long count, long size) throws IOException {
        if (offset < 0 || count < 0 || offset > size || count > size - offset) throw new IOException("ELF metadata range escapes the complete file");
    }
    private static String utf8(ByteBuffer raw) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(raw).toString();
    }
    private static State state(Path path) throws IOException {
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); Object changed = null;
        try { changed = Files.getAttribute(path, "unix:ctime", LinkOption.NOFOLLOW_LINKS); }
        catch (UnsupportedOperationException | IllegalArgumentException unavailable) { /* Basic stability is still required. */ }
        return new State(attributes.fileKey(), attributes.size(), attributes.lastModifiedTime(), attributes.creationTime(), changed);
    }
}
