/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.*;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Exact target identity and independent numerical audits for original kallisto. */
public final class KallistoTables {
    private KallistoTables() { }
    /**
     * Raw reference identity and visible preprocessing counts. Original0.48.0
     * preserves raw length in its abundance output even when indexing clips A tails.
     * @param id first ASCII-whitespace-delimited FASTA header token
     * @param length raw sequence length
     * @param ambiguous non-ACGUT IUPAC bases, replaced pseudorandomly upstream
     * @param uracils U bases, converted to T upstream
     * @param terminalAs known terminal A run; upstream clips runs at least ten long
     */
    public record Target(String id,int length,long ambiguous,long uracils,int terminalAs) {
        /**
         * Validates a target description.
         * @param id exact ID
         * @param length raw length
         * @param ambiguous ambiguity count
         * @param uracils U count
         * @param terminalAs terminal A count
         */
        public Target {
            if(id==null||id.isBlank()||id.chars().anyMatch(Character::isWhitespace)||id.chars().anyMatch(Character::isISOControl)||length<1||ambiguous<0||uracils<0||ambiguous+uracils>length||terminalAs<0||terminalAs>length)throw new IllegalArgumentException("Invalid reference target");
        }
    }
    /**
     * One immutable original abundance row.
     * @param target exact ID
     * @param length raw reference length
     * @param effectiveLength original effective length
     * @param estimatedCount fractional estimated fragment count
     * @param tpm original TPM
     */
    public record Estimate(String target,int length,double effectiveLength,double estimatedCount,double tpm) { }
    /**
     * An audited original table, without rounding or rewriting its values.
     * @param rows original target order
     * @param totalEstimatedCounts sum of estimates
     * @param totalTpm sum of TPM
     * @param nonzeroTargets targets with positive estimated counts
     */
    public record Audit(List<Estimate> rows,double totalEstimatedCounts,double totalTpm,int nonzeroTargets) {
        /**
         * Copies the row list.
         * @param rows estimates
         * @param totalEstimatedCounts estimate total
         * @param totalTpm TPM total
         * @param nonzeroTargets positive-count targets
         */
        public Audit { rows=List.copyOf(rows); }
    }
    /**
     * Streams plain/gzip wrapped IUPAC DNA/RNA FASTA without changing source bytes.
     * Duplicate/empty IDs,empty sequences and invalid bases fail. Strict mode
     * additionally rejects ambiguity,U and a known terminal A run of at least10.
     * Upstream mode records these changes; original kallisto performs them.
     * @param path source
     * @param maxTargets positive target limit
     * @param maxBases total raw-base limit
     * @param maxExpandedBytes decompressed byte limit
     * @param strict reject upstream sequence transformations
     * @return targets in exact original order
     * @throws IOException for invalid input,duplicate IDs or limits
     */
    public static List<Target> reference(Path path,int maxTargets,long maxBases,long maxExpandedBytes,boolean strict)throws IOException {
        if(maxTargets<1||maxBases<1||maxExpandedBytes<1)throw new IllegalArgumentException("Reference limits must be positive");
        var targets=new ArrayList<Target>();var seen=new HashSet<String>();String id=null;long length=0,ambiguous=0,uracils=0,total=0;int terminal=0;
        try(var reader=reader(path,maxExpandedBytes)) {
            String line;while((line=reader.readLine())!=null) {
                if(line.startsWith(">")) {
                    if(id!=null)add(targets,seen,newTarget(id,length,ambiguous,uracils,terminal,strict),maxTargets);
                    String header=line.substring(1);if(header.isBlank()||Character.isWhitespace(header.charAt(0)))throw new IOException("FASTA requires an immediate nonblank target ID");
                    id=header.split("\\s+",2)[0];length=ambiguous=uracils=0;terminal=0;
                } else {
                    if(id==null)throw new IOException("Sequence before the first FASTA header");
                    if(line.isEmpty())continue;
                    for(int i=0;i<line.length();i++) {
                        char c=Character.toUpperCase(line.charAt(i));if("ACGTRYSWKMBDHVNU".indexOf(c)<0)throw new IOException("Non-IUPAC reference base in target "+id);
                        if(++length>Integer.MAX_VALUE||++total>maxBases)throw new IOException("Reference exceeds target-length/--max-bases limit");
                        if(c=='U')uracils++;else if("ACGT".indexOf(c)<0)ambiguous++;
                        terminal=c=='A'?terminal+1:0;
                    }
                }
            }
        }
        if(id!=null)add(targets,seen,newTarget(id,length,ambiguous,uracils,terminal,strict),maxTargets);
        if(targets.isEmpty())throw new IOException("Empty reference FASTA");return List.copyOf(targets);
    }
    private static Target newTarget(String id,long length,long ambiguity,long uracils,int tail,boolean strict)throws IOException {
        if(length==0)throw new IOException("Empty FASTA target: "+id);
        if(strict&&(ambiguity>0||uracils>0||tail>=10))throw new IOException("Reference target "+id+" requires upstream ambiguity/U/poly-A processing; explicitly choose upstream processing or correct the reference");
        try{return new Target(id,(int)length,ambiguity,uracils,tail);}catch(IllegalArgumentException failure){throw new IOException("Invalid FASTA target: "+id,failure);}
    }
    private static void add(List<Target> result,Set<String> seen,Target target,int maximum)throws IOException {if(result.size()>=maximum)throw new IOException("Reference exceeds --max-targets");if(!seen.add(target.id()))throw new IOException("Duplicate reference target ID: "+target.id());result.add(target);}

    /**
     * Checks original five-column abundance output against exact ordered raw
     * targets, library totals and count/effective-length TPM arithmetic. Original
     * six-significant-digit text is allowed a2e-5 relative rounding budget.
     * No MLE,uncertainty or raw counts are reconstructed by this audit.
     * @param path original abundance TSV
     * @param targets exact reference targets
     * @param mappedFragments original pseudoaligned count (or bootstrap draw total)
     * @param maxBytes input/expanded limit
     * @return immutable original rows and audit totals
     * @throws IOException on mismatched identities,domains or arithmetic
     */
    public static Audit abundance(Path path,List<Target> targets,long mappedFragments,long maxBytes)throws IOException {
        if(targets.isEmpty()||mappedFragments<0)throw new IllegalArgumentException("Nonempty targets and nonnegative fragment count required");
        var rows=new ArrayList<Estimate>();double counts=0,tpms=0,maxRate=0;int positive=0;
        try(var input=reader(path,maxBytes)) {
            if(!"target_id\tlength\teff_length\test_counts\ttpm".equals(input.readLine()))throw new IOException("Original kallisto abundance header differs");String line;
            while((line=input.readLine())!=null) {
                if(rows.size()>=targets.size())throw new IOException("Abundance has extra target rows");String[] fields=line.split("\t",-1);if(fields.length!=5)throw new IOException("Abundance row requires five TSV fields");
                Target target=targets.get(rows.size());if(!target.id().equals(fields[0]))throw new IOException("Abundance target/order mismatch: expected "+target.id());
                int length;try{length=Integer.parseInt(fields[1]);}catch(NumberFormatException failure){throw new IOException("Noninteger original target length",failure);}
                if(length!=target.length())throw new IOException("Abundance reference length differs for "+target.id());
                double effective=number(fields[2]),count=number(fields[3]),tpm=number(fields[4]);
                if(!(effective>0))throw new IOException("Effective length must be positive for "+target.id());
                double rate=count/effective;if(!Double.isFinite(rate))throw new IOException("Nonfinite count/effective-length rate");maxRate=Math.max(maxRate,rate);
                counts+=count;tpms+=tpm;if(count>0)positive++;rows.add(new Estimate(fields[0],length,effective,count,tpm));
            }
        }
        if(rows.size()!=targets.size())throw new IOException("Abundance is missing target rows");
        if(!Double.isFinite(counts)||!Double.isFinite(tpms)||Math.abs(counts-mappedFragments)>2e-5*Math.max(1,mappedFragments))throw new IOException("Estimated-count total differs from pseudoaligned fragment total");
        double scaledTotal=0;if(maxRate>0)for(Estimate row:rows)scaledTotal+=(row.estimatedCount()/row.effectiveLength())/maxRate;
        for(Estimate row:rows) {
            double expected=maxRate==0?0:((row.estimatedCount()/row.effectiveLength())/maxRate)/scaledTotal*1e6;
            if(Math.abs(row.tpm()-expected)>2e-5*Math.max(1,expected))throw new IOException("TPM does not match count/effective-length arithmetic for "+row.target());
        }
        if(maxRate>0&&Math.abs(tpms-1e6)>20)throw new IOException("Positive-library TPM total differs from one million");
        return new Audit(rows,counts,tpms,positive);
    }
    private static double number(String text)throws IOException {try{double value=Double.parseDouble(text);if(!Double.isFinite(value)||value<0)throw new NumberFormatException();return value;}catch(NumberFormatException failure){throw new IOException("Original abundance has a nonfinite/negative/nonnumeric value; inspect empty-library/reference/model warnings",failure);}}
    private static BufferedReader reader(Path path,long maximum)throws IOException {
        if(maximum<1||!Files.isRegularFile(path)||Files.size(path)>maximum)throw new IOException("File missing or exceeds --max-bytes: "+path);
        InputStream raw=Files.newInputStream(path);
        try {
            InputStream expanded=path.toString().toLowerCase(Locale.ROOT).endsWith(".gz")?new GZIPInputStream(raw):raw;
            return new BufferedReader(new InputStreamReader(new LimitedStream(expanded,maximum),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
        }catch(IOException|RuntimeException failure){raw.close();throw failure;}
    }
    private static final class LimitedStream extends FilterInputStream {
        private long left;LimitedStream(InputStream input,long limit){super(input);left=limit;}
        @Override public int read()throws IOException{int value=super.read();if(value>=0&&--left<0)throw new IOException("Expanded source exceeds --max-bytes");return value;}
        @Override public int read(byte[] buffer,int offset,int length)throws IOException{int count=in.read(buffer,offset,left>=length?length:(int)left+1);if(count>0&&(left-=count)<0)throw new IOException("Expanded source exceeds --max-bytes");return count;}
    }
}
