/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Bounded streaming import of original biallelic additive PLINK association rows.
 * Effects retain the actual output A1/OMITTED pair; BIM A1 is never assumed.
 * Raw fields survive even when an upstream error makes all canonical estimates
 * unreportable. This reader performs no regression, P-value recomputation,
 * imputation, strand inference or historical-version equivalence claim.
 */
public final class PlinkAssociations {
    private PlinkAssociations() { }
    /** Original report family. */
    public enum Engine { PLINK1, PLINK2 }
    /** Explicit original regression choice; only additive TEST=ADD is admitted. */
    public enum Model { LINEAR, LOGISTIC, FIRTH, FIRTH_FALLBACK }
    /** Reportability of canonical numeric fields. Missing numbers are NaN. */
    public enum Status {
        /** Reported estimates, standard error and P-value are available. */
        VALID,
        /** PLINK1 did not report a standard error; none is reconstructed from rounded columns. */
        VALID_WITHOUT_REPORTED_SE,
        /** Positive decimal P or original -log10(P) survives only on the log scale. */
        VALID_LOG_P_ONLY,
        /** Raw P=0 provides no finite -log10(P); coefficient fields alone may remain reportable. */
        P_VALUE_OUT_OF_RESOLUTION,
        /** Original ERRCODE was not '.', even if provisional estimates were finite. */
        UNREPORTABLE_UPSTREAM_ERROR,
        /** Original NA/missing/invalid estimates do not establish completed inference. */
        UNREPORTABLE_MISSING_FIELDS
    }
    /** Exact BIM allele codes, not a reference-genome or strand validation. */
    public record Alleles(String bimA1,String bimA2) {
        /** Creates distinct, whitespace-free allele codes; unresolved codes cannot yield valid estimates. */
        public Alleles {
            token(bimA1);token(bimA2);
            if (bimA1.equalsIgnoreCase(bimA2)) throw new IllegalArgumentException("BIM alleles must be distinct");
        }
    }
    /** Explicit input/read bounds. The caller supplies an immutable expected-variant map. */
    public record Limits(int maximumRows,long maximumBytes,int maximumLineBytes) {
        /** Constructs positive row/byte bounds. */
        public Limits {
            if (maximumRows<1 || maximumBytes<1 || maximumLineBytes<64) throw new IllegalArgumentException("Association limits must be positive; line bound must be at least 64 bytes");
        }
        /** Defaults to two million variants, four GiB input and 16 KiB per line. */
        public static Limits defaults() { return new Limits(2_000_000,4L*1_073_741_824,16_384); }
    }
    /** One immutable row. Beta/OR refer to effectAllele, not implicitly to BIM A1. */
    public record Row(String variant,String effectAllele,String otherAllele,boolean effectIsBimA1,
            long observedSamples,boolean firth,double beta,double oddsRatio,double standardError,
            double statistic,double pValue,double negativeLog10PValue,Status status,String upstreamError,
            Map<String,String> rawFields) {
        /** Retains an immutable copy of all original textual fields. */
        public Row { rawFields=Map.copyOf(rawFields); }
        /** True only for estimates which are not suppressed by upstream error/missingness. */
        public boolean effectReportable() { return status!=Status.UNREPORTABLE_UPSTREAM_ERROR && status!=Status.UNREPORTABLE_MISSING_FIELDS; }
        /** True when a finite original P or -log10(P) establishes P-value resolution. */
        public boolean pReportable() { return effectReportable() && status!=Status.P_VALUE_OUT_OF_RESOLUTION; }
    }
    /** Counts valid and explicitly unreportable rows, including unavailable P resolution. */
    public record Counts(long rows,long effectReportable,long pReportable,long upstreamErrors,long missingFields,long unresolvedZeroP) { }

    /**
     * Streams a complete declared variant family. Duplicate, unknown or omitted
     * variants fail. A late structural failure may follow earlier consumer calls;
     * callers must quarantine partial output. This reader is not thread safe when
     * the supplied map or consumer is mutated concurrently.
     */
    public static Counts scan(Path path,Engine engine,Model model,Map<String,Alleles> expected,
            int maximumObservedSamples,Limits limits,Consumer<Row> consumer) throws IOException {
        Objects.requireNonNull(engine);Objects.requireNonNull(model);Objects.requireNonNull(expected);Objects.requireNonNull(limits);Objects.requireNonNull(consumer);
        if (maximumObservedSamples<1 || expected.isEmpty() || expected.size()>limits.maximumRows()) throw new IllegalArgumentException("Require a bounded nonempty variant family and positive cohort size");
        if (engine==Engine.PLINK1 && (model==Model.FIRTH || model==Model.FIRTH_FALLBACK)) throw new IllegalArgumentException("This original PLINK1 report contract has no Firth mode");
        if (!Files.isRegularFile(path) || Files.size(path)>limits.maximumBytes()) throw new IOException("Missing or oversized original association report");
        var seen=new HashSet<String>();long rows=0,effects=0,probabilities=0,errors=0,missing=0,zeros=0;
        try (InputStream input=new BufferedInputStream(Files.newInputStream(path))) {
            String text=line(input,limits.maximumLineBytes());if (text==null) throw new IOException("Original association report is empty");
            String[] names=fields(text.startsWith("#")?text.substring(1):text);var header=new LinkedHashMap<String,Integer>();
            for (int i=0;i<names.length;i++) if (header.putIfAbsent(names[i],i)!=null) throw new IOException("Duplicate association header: "+names[i]);
            String id=engine==Engine.PLINK1?"SNP":"ID",count=engine==Engine.PLINK1?"NMISS":"OBS_CT",stat=engine==Engine.PLINK1?"STAT":model==Model.LINEAR?"T_STAT":"Z_STAT";
            for (String name:new String[]{id,"A1","TEST",count,model==Model.LINEAR?"BETA":"OR",stat}) if (!header.containsKey(name)) throw new IOException("Missing required original column: "+name);
            boolean logarithmic=header.containsKey("NEG_LOG10_P");if (logarithmic==header.containsKey("P")) throw new IOException("Require exactly one original P or NEG_LOG10_P column");
            String se=engine==Engine.PLINK2 && model!=Model.LINEAR?"LOG(OR)_SE":"SE";
            if (engine==Engine.PLINK2) for (String name:new String[]{"OMITTED","ERRCODE",se}) if (!header.containsKey(name)) throw new IOException("Missing required PLINK2 column: "+name);
            if (model==Model.FIRTH_FALLBACK && !header.containsKey("FIRTH?")) throw new IOException("Firth fallback requires the original FIRTH? flag");
            if ((model==Model.LINEAR || model==Model.LOGISTIC) && header.containsKey("FIRTH?")) throw new IOException("Original hybrid report requires an explicit Firth-fallback model");
            while ((text=line(input,limits.maximumLineBytes()))!=null) {
                if (text.isBlank()) continue;if (++rows>limits.maximumRows()) throw new IOException("Association row bound exceeded");
                String[] values=fields(text);if (values.length!=names.length) throw new IOException("Association row width differs at row "+rows);
                var raw=new LinkedHashMap<String,String>();for (int i=0;i<names.length;i++) raw.put(names[i],values[i]);
                String variant=raw.get(id);Alleles alleles=expected.get(variant);
                if (alleles==null || !seen.add(variant)) throw new IOException("Unknown or duplicate association variant: "+variant);
                if (!raw.get("TEST").equals("ADD")) throw new IOException("Require additive TEST=ADD at "+variant);
                String effect=raw.get("A1");boolean first=effect.equals(alleles.bimA1());
                if (!first && !effect.equals(alleles.bimA2())) throw new IOException("Original effect allele does not match BIM at "+variant);
                String other=first?alleles.bimA2():alleles.bimA1();
                if (engine==Engine.PLINK2 && !other.equals(raw.get("OMITTED"))) throw new IOException("Original OMITTED/BIM allele pair differs at "+variant);
                long n;try { n=Long.parseLong(raw.get(count)); } catch (NumberFormatException invalid) { throw new IOException("Invalid observed sample count at "+variant,invalid); }
                if (n<0 || n>maximumObservedSamples) throw new IOException("Observed sample count exceeds declared cohort at "+variant);
                boolean firth=model==Model.FIRTH;
                if (raw.containsKey("FIRTH?")) {
                    if (!Set.of("Y","N").contains(raw.get("FIRTH?"))) throw new IOException("Invalid original FIRTH? flag at "+variant);
                    firth=raw.get("FIRTH?").equals("Y");if (model==Model.FIRTH && !firth) throw new IOException("Forced Firth report contains a non-Firth row");
                }
                String error=engine==Engine.PLINK2?raw.get("ERRCODE"):".";
                Row row;
                if (!error.equals(".")) {
                    row=unreportable(variant,effect,other,first,n,firth,Status.UNREPORTABLE_UPSTREAM_ERROR,error,raw);errors++;
                } else {
                    double coefficient=value(raw.get(model==Model.LINEAR?"BETA":"OR")),standardError=raw.containsKey(se)?value(raw.get(se)):Double.NaN;
                    double statistic=value(raw.get(stat)),probability=value(raw.get(logarithmic?"NEG_LOG10_P":"P"));
                    BigDecimal decimal=null;
                    if (Double.isFinite(probability)) {
                        try { decimal=new BigDecimal(raw.get(logarithmic?"NEG_LOG10_P":"P")); }
                        catch (NumberFormatException invalid) { throw new IOException("Original probability field is not a supported decimal",invalid); }
                    }
                    boolean valid=n>0 && Double.isFinite(coefficient) && (model==Model.LINEAR || coefficient>0) && Double.isFinite(statistic)
                        && (engine==Engine.PLINK1 && !raw.containsKey(se) || Double.isFinite(standardError) && standardError>0)
                        && decimal!=null && decimal.signum()>=0 && (logarithmic || decimal.compareTo(BigDecimal.ONE)<=0)
                        && !Set.of("0",".").contains(effect) && !Set.of("0",".").contains(other);
                    if (!valid) { row=unreportable(variant,effect,other,first,n,firth,Status.UNREPORTABLE_MISSING_FIELDS,".",raw);missing++; }
                    else {
                        Status status=Double.isNaN(standardError)?Status.VALID_WITHOUT_REPORTED_SE:Status.VALID;
                        double p=probability,negativeLog;
                        if (logarithmic) { negativeLog=probability;p=Math.pow(10,-negativeLog);if (p==0) { p=Double.NaN;status=Status.VALID_LOG_P_ONLY; } }
                        else if (p==0 && decimal.signum()>0) { p=Double.NaN;negativeLog=decimalNegativeLog10(decimal);status=Status.VALID_LOG_P_ONLY; }
                        else if (p==0) { p=Double.NaN;negativeLog=Double.NaN;status=Status.P_VALUE_OUT_OF_RESOLUTION;zeros++; }
                        else negativeLog=p<Double.MIN_NORMAL?decimalNegativeLog10(decimal):-Math.log10(p);
                        row=new Row(variant,effect,other,first,n,firth,model==Model.LINEAR?coefficient:Math.log(coefficient),model==Model.LINEAR?Double.NaN:coefficient,standardError,statistic,p,negativeLog,status,".",raw);
                        effects++;if (row.pReportable()) probabilities++;
                    }
                }
                consumer.accept(row);
            }
        }
        if (seen.size()!=expected.size()) throw new IOException("Original association report omitted "+(expected.size()-seen.size())+" declared variants; partial consumer output is not a complete testing family");
        return new Counts(rows,effects,probabilities,errors,missing,zeros);
    }
    private static Row unreportable(String id,String a1,String a2,boolean first,long n,boolean firth,Status status,String error,Map<String,String> raw) {
        return new Row(id,a1,a2,first,n,firth,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,status,error,raw);
    }
    private static double value(String literal) throws IOException {
        if (literal==null || Set.of("NA","NaN","nan",".").contains(literal)) return Double.NaN;
        try { return Double.parseDouble(literal); } catch (NumberFormatException invalid) { throw new IOException("Invalid original numeric field: "+literal,invalid); }
    }
    private static double decimalNegativeLog10(BigDecimal probability) {
        String digits=probability.unscaledValue().toString();int leading=Math.min(16,digits.length());
        double significand=Double.parseDouble(digits.substring(0,leading))*Math.pow(10,1-leading);
        return ((long)probability.scale()-probability.precision()+1)-Math.log10(significand);
    }
    private static String[] fields(String line) { return line.strip().split("\\s+"); }
    private static String line(InputStream input,int maximum) throws IOException {
        var bytes=new ByteArrayOutputStream();int value;
        while ((value=input.read())!=-1 && value!='\n') { if (bytes.size()==maximum) throw new IOException("Association line-byte bound exceeded");bytes.write(value); }
        if (value==-1 && bytes.size()==0) return null;byte[] raw=bytes.toByteArray();int length=raw.length;if (length>0 && raw[length-1]=='\r') length--;
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw,0,length)).toString();
    }
    private static void token(String value) {
        Objects.requireNonNull(value);if (value.isEmpty() || value.chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)||Character.isSpaceChar(c))) throw new IllegalArgumentException("Empty or whitespace-containing allele code");
    }
}
