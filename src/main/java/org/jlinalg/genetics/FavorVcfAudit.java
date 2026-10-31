/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Complete streaming expected-value hashes for the admitted FAVOR VCF profile.
 * The wire schema is favor-gds-values-v1: integers are signed little-endian
 * int32; missing genotype alleles are INT32_MIN. Strings are exact UTF-8 bytes
 * prefixed by their signed little-endian int32 byte length. There are no record
 * separators, implicit sorting, allele swaps or R-serialization bytes.
 * Genotypes follow allele, sample, variant order; phase follows sample, variant.
 */
public final class FavorVcfAudit {
    /** Explicit canonical serialization schema, independent of a GDS storage encoding. */
    public static final String SCHEMA="favor-gds-values-v1";
    /** Hash interpretation is part of the result, not inferred from its value. */
    public enum Kind { INT32_LE, UTF8_LENGTH_INT32_LE }
    /** SHA-256 of every serialized value in original order, with a complete value count. */
    public record Digest(Kind kind,long values,String sha256) {
        public Digest {
            Objects.requireNonNull(kind,"kind");
            if(values<0||sha256==null||!sha256.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid complete value digest");
        }
    }
    /**
     * Expected source-conversion values after a complete VCF scan. Variant-only
     * results omit genotype and phase entries; empty hashes never invent nodes.
     * Resource identity and before/after original GDS storage checks are separate.
     */
    public record Result(String schema,FavorVcfReader.Mode mode,long variants,int samples,
            Map<String,Digest> digests,FavorVcfReader.Report input) {
        public Result {digests=Map.copyOf(digests);}
    }
    private FavorVcfAudit() { }

    /**
     * Hash complete admitted input without retaining its genotype matrix.
     * @param path protected original VCF snapshot; caller verifies external byte hashes
     * @param options explicit scientific profile and complete intake capacities
     * @param cancellation cooperative deadline/cancellation check
     * @return complete expected values; no partial result is returned on failure
     * @throws IOException invalid input, exceeded capacity or unsupported variant ordinal
     */
    public static Result scan(Path path,FavorVcfReader.Options options,Runnable cancellation)throws IOException {
        Objects.requireNonNull(options,"options");
        var values=new LinkedHashMap<String,Values>();
        for(String name:new String[]{"sample.id","chromosome","$ref","$alt","annotation/id"})values.put(name,new Values(Kind.UTF8_LENGTH_INT32_LE));
        for(String name:new String[]{"variant.id","position"})values.put(name,new Values(Kind.INT32_LE));
        if(options.mode()==FavorVcfReader.Mode.DIPLOID_GT){values.put("genotype",new Values(Kind.INT32_LE));values.put("phase",new Values(Kind.INT32_LE));}
        try(var reader=FavorVcfReader.open(path,options,cancellation)) {
            for(String sample:reader.samples()){cancellation.run();values.get("sample.id").text(sample);}
            FavorVcfReader.Row row;
            while((row=reader.next())!=null) {
                cancellation.run();if(row.row()>Integer.MAX_VALUE)throw new IOException("FAVOR source variant.id exceeds supported signed32 ordinal");
                values.get("variant.id").integer((int)row.row());values.get("position").integer(row.variant().position());
                values.get("chromosome").text(Integer.toString(row.variant().chromosome()));
                values.get("$ref").text(row.variant().reference());values.get("$alt").text(row.variant().alternate());
                values.get("annotation/id").text(row.sourceGdsId());
                for(var call:row.calls()) {
                    cancellation.run();var gt=values.get("genotype");
                    gt.integer(call.first()<0?Integer.MIN_VALUE:call.first());gt.integer(call.second()<0?Integer.MIN_VALUE:call.second());
                    values.get("phase").integer(call.phased()?1:0);
                }
            }
            if(!reader.report().complete())throw new IOException("Incomplete FAVOR VCF audit");
            var hashes=new LinkedHashMap<String,Digest>();
            for(var entry:values.entrySet())hashes.put(entry.getKey(),entry.getValue().finish());
            return new Result(SCHEMA,options.mode(),reader.report().variants(),reader.samples().size(),hashes,reader.report());
        }
    }
    private static final class Values {
        private final Kind kind;
        private final MessageDigest hash;
        private final byte[] buffer=new byte[8192];
        private int used;
        private long count;
        Values(Kind kind){this.kind=kind;try{hash=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException absent){throw new AssertionError(absent);}}
        void intBytes(int value){if(used>buffer.length-4)flush();for(int i=0;i<4;i++)buffer[used++]=(byte)(value>>>(8*i));}
        void integer(int value){intBytes(value);count++;}
        void text(String value){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);intBytes(bytes.length);flush();hash.update(bytes);count++;}
        void flush(){if(used>0){hash.update(buffer,0,used);used=0;}}
        Digest finish(){flush();return new Digest(kind,count,HexFormat.of().formatHex(hash.digest()));}
    }
}
