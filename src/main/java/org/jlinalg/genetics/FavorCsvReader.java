/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Strict streaming UTF-8 CSV admission for local FAVOR resources. Literal cells
 * retain whitespace, empty/NA text, escaped quotes and embedded line endings.
 * CSV type inference, readr/GDS missingness conversion and file hashing are
 * separate steps. The caller must protect and re-verify original/snapshot bytes.
 */
public final class FavorCsvReader implements AutoCloseable {
    /** Truly empty physical records may be rejected or explicitly skipped, as by xsv. */
    public enum BlankRecords { ERROR, IGNORE }
    /** File/data-row/cell and per-column/field/record bounds, all measured before admission. */
    public record Limits(long maximumBytes,long maximumRows,long maximumCells,int maximumColumns,
            int maximumFieldBytes,int maximumRecordBytes) {
        public Limits {
            if(maximumBytes<1||maximumRows<0||maximumCells<1||maximumColumns<1||maximumColumns>1024
                    ||maximumFieldBytes<1||maximumRecordBytes<1)throw new IllegalArgumentException("Invalid CSV byte,row,cell,column,field or record capacity");
        }
        /** Conservative development defaults; complete populations are rejected rather than truncated. */
        public static Limits defaults(){return new Limits(1_073_741_824L,100_000_000L,1_000_000_000L,1024,1_048_576,16_777_216);}
    }
    /** Complete is true only after next() has observed EOF; cells include the header. */
    public record Report(long bytes,long rows,long cells,long skippedBlankRecords,boolean utf8Bom,boolean complete) { }

    private final Path path;
    private final InputStream stream;
    private final Limits limits;
    private final BlankRecords blankRecords;
    private final Runnable check;
    private final byte[] prefix;
    private final boolean bom;
    private final List<String> header;
    private int prefixIndex,peeked=-2,recordBytes;
    private long bytes,rows,cells,blank;
    private boolean complete,closed,failed;

    private FavorCsvReader(Path supplied,Limits limits,BlankRecords blankRecords,Runnable check)throws IOException {
        this.path=supplied.toAbsolutePath().normalize();this.limits=limits;this.blankRecords=blankRecords;this.check=check;
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path)||Files.size(path)>limits.maximumBytes())throw new IOException("Missing,linked or oversized plain CSV: "+path);
        stream=new BufferedInputStream(Files.newInputStream(path),8192);
        try {
            check.run();byte[] first=stream.readNBytes((int)Math.min(3,limits.maximumBytes()==Long.MAX_VALUE?3:limits.maximumBytes()+1));bytes=first.length;
            if(bytes>limits.maximumBytes())throw failure("CSV --maximumBytes exceeded during initial read");
            bom=first.length==3&&(first[0]&255)==239&&(first[1]&255)==187&&(first[2]&255)==191;prefix=bom?new byte[0]:first;
            List<String> value=record();if(value==null)throw failure("Missing CSV header");
            if(new HashSet<>(value).size()!=value.size()||value.stream().anyMatch(s->s.isBlank()||s.length()>1024||s.codePoints().anyMatch(Character::isISOControl)))throw failure("CSV headers must be distinct bounded nonblank names without controls");
            header=value;
        } catch(IOException|RuntimeException failure) {
            try{stream.close();}catch(IOException closing){failure.addSuppressed(closing);}throw failure;
        }
    }
    /**
     * Open an uncompressed regular file and admit its complete header.
     * @param path exact plain UTF-8 input; UTF-8 BOM is recorded, not lost from source provenance
     * @param limits complete file/row/cell and per-record capacities
     * @param blankRecords explicit treatment of truly empty physical records
     * @param cancellation cooperative deadline/cancellation check
     * @return owned reader; the caller must close it, including after errors
     * @throws IOException invalid file/header/encoding or exceeded input capacity
     */
    public static FavorCsvReader open(Path path,Limits limits,BlankRecords blankRecords,Runnable cancellation)throws IOException {
        Objects.requireNonNull(path,"path");Objects.requireNonNull(limits,"limits");Objects.requireNonNull(blankRecords,"blankRecords");Objects.requireNonNull(cancellation,"cancellation");
        return new FavorCsvReader(path,limits,blankRecords,cancellation);
    }
    /** Immutable exact decoded column names, in original order. */
    public List<String> header(){return header;}
    /** Current counters; an early close or failure never certifies a complete scan. */
    public Report report(){return new Report(bytes,rows,cells,blank,bom,complete);}
    /**
     * Read the next complete logical record, preserving embedded CR/LF and quotes.
     * Bare quotes in unquoted fields and text after a closing quote are refused;
     * no permissive repair is inferred. Record terminators may be LF, CRLF or CR.
     * @return immutable row with exactly the header width, or null after verified EOF
     * @throws IOException invalid CSV/encoding, closed reader or exceeded capacity
     */
    public List<String> next()throws IOException {
        if(closed)throw failure("CSV reader is closed");if(failed)throw failure("Failed CSV scans cannot be resumed");if(complete)return null;
        try {
            check.run();List<String> value=record();if(value==null){complete=true;return null;}
            if(value.size()!=header.size())throw failure("CSV data width differs from complete header");
            if(rows>=limits.maximumRows())throw failure("Complete CSV data-row capacity exceeded");rows++;return value;
        } catch(IOException|RuntimeException failure){failed=true;throw failure;}
    }
    private int peek()throws IOException {
        if(peeked!=-2)return peeked;
        if(prefixIndex<prefix.length)peeked=prefix[prefixIndex++]&255;
        else {
            if((bytes&8191)==0)check.run();int value=stream.read();if(value>=0){if(bytes>=limits.maximumBytes())throw failure("Complete CSV byte capacity exceeded");bytes++;}peeked=value;
        }
        return peeked;
    }
    private int take()throws IOException {
        int value=peek();peeked=-2;if(value>=0){if(recordBytes>=limits.maximumRecordBytes())throw failure("Complete CSV logical-record byte capacity exceeded");recordBytes++;}return value;
    }
    private List<String> record()throws IOException {
        for(;;) {
            check.run();recordBytes=0;if(peek()==-1)return null;
            if(peek()=='\r'||peek()=='\n') {
                int end=take();if(end=='\r'&&peek()=='\n')take();
                if(blankRecords==BlankRecords.ERROR)throw failure("Empty physical CSV record requires explicit IGNORE policy");blank++;continue;
            }
            var fields=new ArrayList<String>();boolean syntax=false;
            for(;;) {
                if(fields.size()>=limits.maximumColumns())throw failure("CSV column capacity exceeded");
                if(fields.size()>=limits.maximumCells()-cells)throw failure("Complete CSV decoded-cell capacity exceeded");
                var field=new ByteArrayOutputStream();
                if(peek()=='"') {
                    syntax=true;take();
                    for(;;) {
                        int value=take();if(value==-1)throw failure("Unterminated quoted CSV field");
                        if(value=='"'){if(peek()=='"'){take();append(field,'"');}else break;}
                        else append(field,value);
                    }
                    int after=peek();if(after!=','&&after!='\r'&&after!='\n'&&after!=-1)throw failure("Unexpected text after closing CSV quote");
                } else {
                    for(;;) {
                        int value=peek();if(value==','||value=='\r'||value=='\n'||value==-1)break;
                        if(value=='"')throw failure("Bare quote in unquoted CSV field");syntax=true;append(field,take());
                    }
                }
                if(cells>=limits.maximumCells())throw failure("Complete CSV decoded-cell capacity exceeded");
                String decoded;
                try{decoded=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(field.toByteArray())).toString();}
                catch(IOException invalid){throw new IOException("Invalid CSV UTF-8 at "+context(),invalid);}
                if(decoded.indexOf('\0')>=0)throw failure("NUL is not admitted in CSV cells");fields.add(decoded);
                int terminator=peek();if(terminator==','){syntax=true;take();continue;}
                if(terminator=='\r'){take();if(peek()=='\n')take();}else if(terminator=='\n')take();
                if(!syntax) {
                    if(blankRecords==BlankRecords.ERROR)throw failure("Empty physical CSV record requires explicit IGNORE policy");
                    blank++;break;
                }
                if(fields.size()>limits.maximumCells()-cells)throw failure("Complete CSV decoded-cell capacity exceeded");cells+=fields.size();return List.copyOf(fields);
            }
        }
    }
    private void append(ByteArrayOutputStream field,int value)throws IOException {if(field.size()>=limits.maximumFieldBytes())throw failure("CSV decoded-field byte capacity exceeded");field.write(value);}
    private String context(){return path+", data rows admitted="+rows+", source bytes read="+bytes;}
    private IOException failure(String message){return new IOException(message+": "+context());}
    /** Close the owned input; this does not certify an incomplete scan. */
    @Override public void close()throws IOException {if(!closed){closed=true;stream.close();}}
}
