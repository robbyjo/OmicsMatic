/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.TextCigarCodec;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Native PAF geometry/tag validation against exact query and reference sequence lengths. */
public final class PafAlignments {
    private PafAlignments() { }
    /** Zero-based half-open spans; matches/blockLength is PAF alignment identity, not global identity. */
    public record Alignment(String query,long queryLength,long queryStart,long queryEnd,String strand,
                            String target,long targetLength,long targetStart,long targetEnd,
                            long matches,long blockLength,int mapq,String type,String cigar) { }
    public static Alignment parse(String line,Map<String,Long> queries,Map<String,Long> targets,boolean requireCigar) throws IOException {
        String[] f=line.split("\t",-1);
        if (f.length<12) throw new IOException("PAF requires at least twelve tab-delimited fields");
        try {
            long ql=Long.parseLong(f[1]),qs=Long.parseLong(f[2]),qe=Long.parseLong(f[3]),tl=Long.parseLong(f[6]),ts=Long.parseLong(f[7]),te=Long.parseLong(f[8]),m=Long.parseLong(f[9]),b=Long.parseLong(f[10]);
            int mapq=Integer.parseInt(f[11]);
            if (!Long.valueOf(ql).equals(queries.get(f[0]))||!Long.valueOf(tl).equals(targets.get(f[5]))||qs<0||qe<=qs||qe>ql||ts<0||te<=ts||te>tl||!Set.of("+","-").contains(f[4])||m<0||b<=0||m>b||mapq<0||mapq>255) throw new IOException("PAF names, lengths, coordinates, strand or mapping fields differ from the input contract");
            var tags=new HashMap<String,String>();
            for (int i=12;i<f.length;i++) {
                String[] tag=f[i].split(":",3);
                if (tag.length!=3||!tag[0].matches("[A-Za-z][A-Za-z0-9]")||tags.putIfAbsent(tag[0],tag[1]+":"+tag[2])!=null) throw new IOException("Malformed/duplicate PAF tag");
            }
            String type=tags.getOrDefault("tp","A:unknown");
            if (tags.containsKey("tp")&&!Set.of("A:P","A:S","A:I","A:i").contains(type)) throw new IOException("Invalid minimap2 PAF alignment type");
            String cg=tags.get("cg");
            if (requireCigar&&cg==null) throw new IOException("PAF omits requested base-alignment CIGAR");
            String cigar=".";
            if (cg!=null) {
                if (!cg.startsWith("Z:")) throw new IOException("PAF CIGAR tag must use Z type"); cigar=cg.substring(2);
                var c=TextCigarCodec.decode(cigar); long qc=0,tc=0,block=0;
                if (c.isEmpty()) throw new IOException("Empty PAF CIGAR");
                for (var e:c.getCigarElements()) {
                    var op=e.getOperator();
                    if (e.getLength()<1||!Set.of(CigarOperator.M,CigarOperator.I,CigarOperator.D,CigarOperator.N,CigarOperator.EQ,CigarOperator.X).contains(op)) throw new IOException("Unsupported PAF CIGAR operation");
                    if (op.consumesReadBases()) qc=Math.addExact(qc,e.getLength());
                    if (op.consumesReferenceBases()) tc=Math.addExact(tc,e.getLength());
                    if (op!=CigarOperator.N) block=Math.addExact(block,e.getLength());
                }
                if (qc!=qe-qs||tc!=te-ts||block!=b) throw new IOException("PAF CIGAR spans/block length disagree with mandatory fields");
            }
            return new Alignment(f[0],ql,qs,qe,f[4],f[5],tl,ts,te,m,b,mapq,type.substring(2),cigar);
        } catch (IllegalArgumentException|ArithmeticException failure) { throw new IOException("Malformed numeric/CIGAR PAF record",failure); }
    }
}
