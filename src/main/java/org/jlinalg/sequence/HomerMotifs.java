/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/**
 * Supplied HOMER DNA motif matrices and original natural-log threshold scanning.
 * Scores use max(p,0.001)/0.25 without row renormalization. This API neither
 * discovers motifs nor calculates enrichment P, q or E values.
 */
public final class HomerMotifs {
    private HomerMotifs() { }

    /** Requested scanning strand. */
    public enum Strands { FORWARD, REVERSE, BOTH }

    /**
     * Complete source motif, retaining header metadata separately from scoring.
     * @param ordinal one-based source motif order
     * @param consensus source consensus label; not used as the score matrix
     * @param name original name
     * @param threshold inclusive natural-log score cutoff
     * @param sourceLogP optional original fourth header field; not a computed P value
     * @param extraHeader remaining source fields, without inferred semantics
     * @param probabilities ordered A/C/G/T rows; copied and never normalized
     */
    public record Motif(int ordinal,String consensus,String name,double threshold,Double sourceLogP,
            List<String> extraHeader,double[][] probabilities) {
        /** Validates the supplied finite probability rows and copies all state. */
        public Motif {
            if(ordinal<1||!Double.isFinite(threshold)||sourceLogP!=null&&!Double.isFinite(sourceLogP))
                throw new IllegalArgumentException("Require positive motif ordinal and finite source fields");
            token(consensus,"consensus");token(name,"name");
            extraHeader=List.copyOf(extraHeader);
            for(String field:extraHeader)if(field.codePoints().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Control character in motif metadata");
            if(probabilities.length<1||probabilities.length>4096)throw new IllegalArgumentException("Motif width must be 1..4096");
            probabilities=copy(probabilities);
            for(double[] row:probabilities){
                if(row.length!=4)throw new IllegalArgumentException("Each motif row needs ordered A/C/G/T values");
                double sum=0;
                for(double p:row){if(!Double.isFinite(p)||p<0||p>1)throw new IllegalArgumentException("Require finite motif probabilities in [0,1]");sum+=p;}
                // Original matrices print three decimals, so their row sum can differ by 0.002.
                if(Math.abs(sum-1)>.002000000001)throw new IllegalArgumentException("Require probability rows within three-decimal sum precision; no counts/weights conversion or normalization is inferred");
            }
        }
        /** @return defensive copies of the unmodified source probabilities */
        @Override public double[][] probabilities(){return copy(probabilities);}
        /** @return matrix width, independent of the source consensus label */
        public int width(){return probabilities.length;}
    }

    /**
     * Explicit bounded motif-file intake.
     * @param motifs maximum motif headers
     * @param width maximum rows in one motif, at most 4096
     * @param totalRows maximum rows in the complete file
     * @param inputBytes maximum original bytes
     * @param decodedCharacters maximum decoded characters including newlines
     * @param lineCharacters maximum text line, at most one MiB
     * @param retainedBytes conservative cumulative model/header allocation budget
     */
    public record Limits(int motifs,int width,long totalRows,long inputBytes,long decodedCharacters,int lineCharacters,long retainedBytes) {
        /** Validates resource bounds before parsing. */
        public Limits {
            if(motifs<1||width<1||width>4096||totalRows<1||inputBytes<1||decodedCharacters<1||lineCharacters<16||lineCharacters>1048576||retainedBytes<1)
                throw new IllegalArgumentException("Require positive bounded HOMER motif intake limits");
        }
        /**
         * Uses a 256-MiB cumulative model allocation ceiling.
         * @param motifs maximum motifs
         * @param width maximum width
         * @param totalRows maximum matrix rows
         * @param inputBytes maximum original bytes
         * @param decodedCharacters maximum decoded characters
         * @param lineCharacters maximum line characters
         */
        public Limits(int motifs,int width,long totalRows,long inputBytes,long decodedCharacters,int lineCharacters){
            this(motifs,width,totalRows,inputBytes,decodedCharacters,lineCharacters,268435456);
        }
    }

    /**
     * Explicit scanner policy.
     * @param strands selected orientations
     * @param offset supplied integer offset, or null for original truncation(-length/2)
     * @param maskNonDna explicitly map printable non-ACGT letters/symbols to N, including U
     * @param maximumResidues maximum complete sequence length before copying
     * @param maximumOperations maximum complete worst-case base-score operations
     * @param maximumHits fail before emitting a hit beyond this bound
     */
    public record Options(Strands strands,Integer offset,boolean maskNonDna,int maximumResidues,long maximumOperations,long maximumHits) {
        /** Validates the bounded policy. */
        public Options {
            Objects.requireNonNull(strands);
            if(maximumResidues<1||maximumOperations<1||maximumHits<1)throw new IllegalArgumentException("Require positive scan residue/work/hit limits");
            if(offset!=null&&offset==-12345678)throw new IllegalArgumentException("Reserved original HOMER midpoint sentinel; select null/midpoint explicitly");
        }
    }

    /**
     * One hit with unambiguous interval and original oriented anchor.
     * @param left0 zero-based left coordinate in the supplied sequence
     * @param endExclusive zero-based exclusive right coordinate
     * @param reportedPosition plus left or minus rightmost base, plus selected offset
     * @param strand plus or minus
     * @param forwardWord uppercase forward-oriented sequence, also for minus hits
     * @param logOdds natural-log score; not a P value
     */
    public record Hit(int left0,int endExclusive,long reportedPosition,char strand,String forwardWord,double logOdds) { }

    /**
     * Complete scan accounting; callbacks remain provisional until return.
     * @param residues complete input sequence length
     * @param lowercasePromoted lowercase A/C/G/T promoted to uppercase
     * @param maskedResidues non-N noncanonical residues explicitly mapped to N
     * @param windows candidate windows before ambiguity masking, counted once per position
     * @param ambiguousWindows windows excluded on both orientations
     * @param scoredSites valid windows times selected strand count
     * @param hits emitted threshold-passing sites
     * @param maximumPossibleOperations declared worst-case score work, including masked windows
     */
    public record Summary(int residues,long lowercasePromoted,long maskedResidues,long windows,
            long ambiguousWindows,long scoredSites,long hits,long maximumPossibleOperations) { }

    /** Immutable score model compiled once for repeated sequence scanning. */
    public static final class Compiled {
        private final Motif motif;
        private final double[][] scores;
        private Compiled(Motif motif){
            this.motif=Objects.requireNonNull(motif);scores=copy(motif.probabilities);
            for(double[] row:scores)for(int j=0;j<4;j++)row[j]=StrictMath.log(Math.max(.001,row[j])/.25);
        }
        /** @return immutable original motif metadata and defensively copied probabilities */
        public Motif motif(){return motif;}
    }

    /**
     * Compiles immutable cached natural-log scores.
     * @param motif supplied model
     * @return reusable score model
     */
    public static Compiled compile(Motif motif){return new Compiled(motif);}

    /** Receives one provisional hit and may abort the scan with an IOException. */
    @FunctionalInterface public interface HitConsumer {
        /**
         * Receives a threshold-passing site.
         * @param hit accepted site
         * @throws IOException to abort downstream output
         */
        void accept(Hit hit)throws IOException;
    }

    /**
     * Reads a complete plain/gzip UTF-8 HOMER motif file, preserving matrix/header values.
     * Header fields are tab-separated: &gt;consensus, name, threshold, optional log P.
     * Duplicate names remain distinguishable by ordinal. No implicit motif-name grouping,
     * threshold fitting, background substitution or matrix normalization is performed.
     * @param path regular nonlinked input
     * @param limits explicit input/work capacities
     * @return immutable ordered motifs with defensively copied matrices
     * @throws IOException on invalid or incomplete input
     */
    public static List<Motif> read(Path path,Limits limits)throws IOException{
        Objects.requireNonNull(limits);Path real=path.toAbsolutePath().normalize();
        if(!Files.isRegularFile(real,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(real)||!real.toRealPath().equals(real)||Files.size(real)>limits.inputBytes)
            throw new IOException("Require an unchanged real regular motif file within its byte limit");
        var result=new ArrayList<Motif>();var rows=new ArrayList<double[]>();String[] header=null;
        long number=0,characters=0,totalRows=0,retained=0;
        try(var raw=new BufferedInputStream(Files.newInputStream(real))){
            raw.mark(2);int a=raw.read(),b=raw.read();raw.reset();
            try(var reader=new BufferedReader(new InputStreamReader(a==31&&b==139?new GZIPInputStream(raw):raw,
                    StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))){
                for(;;){
                    var line=new StringBuilder();int c;
                    while((c=reader.read())!=-1){
                        if(++characters>limits.decodedCharacters)throw new IOException("HOMER motif decoded-character bound exceeded");
                        if(c=='\n')break;
                        if(line.length()>=limits.lineCharacters)throw new IOException("HOMER motif line exceeds explicit bound");
                        if(Character.isISOControl(c)&&c!='\t'&&c!='\r')throw new IOException("Control character in HOMER motif input");
                        line.append((char)c);
                    }
                    if(c<0&&line.isEmpty())break;number++;
                    if(!line.isEmpty()&&line.charAt(line.length()-1)=='\r')line.setLength(line.length()-1);
                    String text=line.toString();if(text.indexOf('\r')>=0)throw new IOException("Embedded carriage return");
                    if(text.isEmpty())continue;
                    retained=Math.addExact(retained,Math.addExact(256,Math.multiplyExact(8L,text.length())));
                    if(retained>limits.retainedBytes)throw new IOException("HOMER cumulative model/header allocation budget exceeded before field allocation");
                    String[] fields=text.split("\t",-1);
                    if(text.startsWith(">")){
                        if(header!=null)result.add(finish(result.size()+1,header,rows));
                        if(result.size()>=limits.motifs)throw new IOException("HOMER motif count bound exceeded");
                        if(fields.length<3||fields.length>64)throw new IOException("HOMER motif header needs consensus, name and score threshold");
                        header=fields;rows=new ArrayList<>();
                    }else{
                        if(header==null||fields.length!=4)throw new IOException("HOMER motif matrix row needs an active header and four tab-separated values");
                        if(rows.size()>=limits.width||++totalRows>limits.totalRows)throw new IOException("HOMER motif row/width capacity exceeded");
                        rows.add(new double[]{number(fields[0]),number(fields[1]),number(fields[2]),number(fields[3])});
                    }
                }
            }
            if(header!=null)result.add(finish(result.size()+1,header,rows));
            if(result.isEmpty())throw new IOException("No complete HOMER motifs");
        }catch(IllegalArgumentException failure){throw new IOException("Invalid HOMER motif near line "+number+": "+failure.getMessage(),failure);}
        return List.copyOf(result);
    }

    /**
     * Scans one caller-bounded sequence in original forward-then-reverse order.
     * Reverse hits report the rightmost forward base, and retain forward matched text.
     * Unlike FIMO, U is masked under the explicit mask policy, never converted to T.
     * Dense scans stream; original HOMER also reserves twice the longest-sequence site capacity.
     * @param supplied complete caller-owned sequence
     * @param motif supplied probability matrix and fixed threshold
     * @param options explicit strands, offset, alphabet and work/hit limits
     * @param consumer provisional hit callback
     * @return completed input/scoring summary
     * @throws IOException on invalid input, capacity or callback failure
     */
    public static Summary scan(CharSequence supplied,Motif motif,Options options,HitConsumer consumer)throws IOException{
        return scan(supplied,compile(motif),options,consumer);
    }

    /**
     * Scans with a reusable immutable score model; policies and coordinates match the supplied-motif overload.
     * @param supplied complete caller-bounded sequence
     * @param compiled cached scores from the unchanged source motif
     * @param options explicit scan policies
     * @param consumer provisional hit callback
     * @return complete sequence summary
     * @throws IOException on invalid input, resource bound or callback failure
     */
    public static Summary scan(CharSequence supplied,Compiled compiled,Options options,HitConsumer consumer)throws IOException{
        Objects.requireNonNull(compiled);Motif motif=compiled.motif;
        Objects.requireNonNull(supplied);Objects.requireNonNull(motif);Objects.requireNonNull(options);Objects.requireNonNull(consumer);
        int n=supplied.length(),width=motif.width(),orientations=options.strands==Strands.BOTH?2:1;
        if(n>options.maximumResidues)throw new IOException("HOMER sequence exceeds declared residue capacity before allocation");
        long windows=Math.max(0L,(long)n-width+1),operations;
        try{operations=Math.multiplyExact(Math.multiplyExact(windows,width),orientations);}
        catch(ArithmeticException overflow){throw new IOException("HOMER scan work exceeds signed capacity",overflow);}
        if(operations>options.maximumOperations)throw new IOException("HOMER scan exceeds declared worst-case base-score work");
        char[] sequence=new char[n];long lowercase=0,masked=0;
        for(int i=0;i<n;i++){
            char c=supplied.charAt(i),u=Character.toUpperCase(c);
            if(c<33||c>126)throw new IOException("Require printable ASCII DNA without embedded whitespace");
            if("ACGT".indexOf(u)>=0){sequence[i]=u;if(c!=u)lowercase++;}
            else if(u=='N'){sequence[i]='N';}
            else if(options.maskNonDna){sequence[i]='N';masked++;}
            else throw new IOException("Noncanonical HOMER residue '"+c+"'; correct it or explicitly permit masking to N");
        }
        double[][] scores=compiled.scores;
        long hits=0,valid=0;int offset=options.offset==null?-n/2:options.offset;
        for(int orientation=0;orientation<2;orientation++){
            boolean reverse=orientation==1;
            if(reverse&&options.strands==Strands.FORWARD||!reverse&&options.strands==Strands.REVERSE)continue;
            int last=n-width;
            for(int step=0;step<=last;step++){
                int left=reverse?last-step:step;double score=0;boolean ambiguous=false;
                for(int j=0;j<width;j++){
                    int base=index(sequence[reverse?left+width-1-j:left+j]);if(base<0){ambiguous=true;break;}
                    score+=scores[j][reverse?3-base:base];
                }
                if(ambiguous)continue;valid++;
                if(score>=motif.threshold){
                    if(++hits>options.maximumHits)throw new IOException("HOMER hit capacity exceeded; no truncated result is complete");
                    consumer.accept(new Hit(left,left+width,(long)(reverse?left+width-1:left)+offset,reverse?'-':'+',new String(sequence,left,width),score));
                }
            }
        }
        long ambiguous=windows-valid/orientations;
        return new Summary(n,lowercase,masked,windows,ambiguous,valid,hits,operations);
    }

    private static Motif finish(int ordinal,String[] header,List<double[]> rows)throws IOException{
        if(rows.isEmpty())throw new IOException("HOMER motif header has no matrix rows");
        Double logP=header.length>3?number(header[3]):null;
        return new Motif(ordinal,header[0].substring(1),header[1],number(header[2]),logP,
                header.length>4?List.of(java.util.Arrays.copyOfRange(header,4,header.length)):List.of(),rows.toArray(double[][]::new));
    }
    private static double number(String value)throws IOException{
        if(value.length()>128||!value.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new IOException("Invalid decimal HOMER numeric field");
        try{double v=Double.parseDouble(value);if(!Double.isFinite(v))throw new NumberFormatException();return v;}
        catch(NumberFormatException failure){throw new IOException("Nonfinite HOMER numeric field",failure);}
    }
    private static void token(String value,String label){
        if(value==null||value.isBlank()||value.length()>8192||value.codePoints().anyMatch(c->Character.isISOControl(c)||c==0x2028||c==0x2029))
            throw new IllegalArgumentException("Invalid bounded motif "+label);
    }
    private static int index(char c){return switch(c){case 'A'->0;case 'C'->1;case 'G'->2;case 'T'->3;default->-1;};}
    private static double[][] copy(double[][] input){Objects.requireNonNull(input);double[][] out=new double[input.length][];for(int i=0;i<out.length;i++)out[i]=Objects.requireNonNull(input[i]).clone();return out;}
}

