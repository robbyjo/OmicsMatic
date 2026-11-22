/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import htsjdk.samtools.util.SortingCollection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

/** Disk-backed SHA-256 multiset audit of original-orientation read names, mates, bases and qualities. */
public final class ReadContentAudit implements AutoCloseable {
    private final Path temporary;
    private final SortingCollection<String> expected;
    private final SortingCollection<String> observed;

    /** Temporary directory must not exist. Each collection retains at most maxInMemory fixed-size hashes. */
    public ReadContentAudit(Path temporary,int maxInMemory)throws IOException {
        if(maxInMemory<1)throw new IllegalArgumentException("Audit memory capacity must be positive");
        this.temporary=Files.createDirectory(temporary);
        expected=SortingCollection.newInstance(String.class,new Codec(),Comparator.naturalOrder(),maxInMemory,temporary);
        observed=SortingCollection.newInstance(String.class,new Codec(),Comparator.naturalOrder(),maxInMemory,temporary);
    }
    /** Add an input observation. mate is 0 for single-end, 1 or 2 for paired-end. */
    public void expected(String name,int mate,String sequence,String quality){expected.add(fingerprint(name,mate,sequence,quality));}
    /** Add one primary output observation transformed back to its original orientation. */
    public void observed(String name,int mate,String sequence,String quality){observed.add(fingerprint(name,mate,sequence,quality));}
    /** Compare sorted cryptographic multisets, retaining duplicate multiplicities without assuming output order. */
    public void verify()throws IOException {
        expected.doneAdding();observed.doneAdding();
        try(var a=expected.iterator();var b=observed.iterator()){
            while(a.hasNext()&&b.hasNext())if(!a.next().equals(b.next()))throw new IOException("Primary read content audit differs: identity, mate, bases, qualities or multiplicity changed");
            if(a.hasNext()||b.hasNext())throw new IOException("Primary read content audit differs: omitted/extra observations");
        }
    }
    private static String fingerprint(String name,int mate,String sequence,String quality){
        if(name==null||sequence==null||quality==null||mate<0||mate>2||sequence.length()!=quality.length())throw new IllegalArgumentException("Invalid read audit observation");
        try{
            MessageDigest hash=MessageDigest.getInstance("SHA-256");hash.update((byte)mate);
            for(String value:new String[]{name,sequence,quality}){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);int n=bytes.length;hash.update(new byte[]{(byte)(n>>>24),(byte)(n>>>16),(byte)(n>>>8),(byte)n});hash.update(bytes);}
            return HexFormat.of().formatHex(hash.digest());
        }catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    @Override public void close()throws IOException {expected.cleanup();observed.cleanup();Files.delete(temporary);}
    private static final class Codec implements SortingCollection.Codec<String> {
        private InputStream input;private OutputStream output;
        @Override public void setInputStream(InputStream stream){input=stream;}
        @Override public void setOutputStream(OutputStream stream){output=stream;}
        @Override public void encode(String value){try{output.write(value.getBytes(StandardCharsets.US_ASCII));}catch(IOException failure){throw new UncheckedIOException(failure);}}
        @Override public String decode(){try{byte[] bytes=input.readNBytes(64);if(bytes.length==0)return null;if(bytes.length!=64)throw new EOFException("Truncated audit spill hash");return new String(bytes,StandardCharsets.US_ASCII);}catch(IOException failure){throw new UncheckedIOException(failure);}}
        @Override public Codec clone(){return new Codec();}
    }
}
