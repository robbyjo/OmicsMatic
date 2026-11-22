/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded original PCA reports, preserving raw values, identities and allele orientation.
 * Format/orthogonality checks do not establish randomized approximation accuracy.
 */
final class PlinkPcaReports {
    private PlinkPcaReports() { }

    record Limits(int maximumSamples,int maximumVariants,int maximumComponents,long maximumRetainedBytes,
            long maximumReportBytes,int maximumLineBytes) {
        Limits {
            if(maximumSamples<2||maximumVariants<1||maximumComponents<1||maximumRetainedBytes<1||maximumReportBytes<1||maximumLineBytes<64)
                throw new IllegalArgumentException("PCA report bounds must be positive;at least two samples and64line bytes are required");
        }
    }
    /** One reported finite number with its original decimal spelling and six-digit rounding radius. */
    record Number(String literal,double value,double roundingRadius) { }
    record Sample(PlinkQcInputs.Id id,List<Number> components) {
        Sample { components=List.copyOf(components); }
    }
    record Variant(String id,String chromosome,String firstAllele,String secondAllele) { }
    /** Alleles describe the actual original weight row, not an inferred effect allele. */
    record Weight(String variant,String chromosome,String firstAllele,String secondAllele,
            String countedAllele,List<Number> components) {
        Weight { components=List.copyOf(components); }
    }
    record Basis(List<Number> eigenvalues,Map<PlinkQcInputs.Id,Sample> samples,double maximumOrthogonalityError) {
        Basis { eigenvalues=List.copyOf(eigenvalues); samples=java.util.Collections.unmodifiableMap(new LinkedHashMap<>(samples)); }
    }
    record WeightCounts(int variants,long rows) { }
    @FunctionalInterface interface WeightConsumer { void accept(Weight weight) throws IOException; }

    static Basis basis(Path eigenvalues,Path eigenvectors,Set<PlinkQcInputs.Id> expected,int components,Limits limits) throws IOException {
        if(expected.size()<2||expected.size()>limits.maximumSamples()||components<1||components>limits.maximumComponents()||components>expected.size())
            throw new IOException("Original PCA sample/component dimensions exceed declared bounds");
        long retained=Math.multiplyExact(Math.multiplyExact((long)expected.size(),components+2L),256L);
        if(retained>limits.maximumRetainedBytes())throw new IOException("Original PCA coordinate metadata exceeds declared retained-byte bound");
        var values=new ArrayList<Number>();
        try(var input=PlinkGlmInputs.limited(eigenvalues,limits.maximumReportBytes())) {
            for(String line;(line=PlinkGlmInputs.line(input,limits.maximumLineBytes()))!=null;) {
                if(line.isBlank()||values.size()>=components||line.strip().split("\\s+").length!=1)throw new IOException("Unexpected original PCA eigenvalue row/count");
                Number value=number(line.strip());
                if(value.value()<=0)throw new IOException("Original PCA requested eigenvalue is nonpositive/unresolved;reduce components or review the panel");
                if(!values.isEmpty()&&value.value()>values.get(values.size()-1).value())throw new IOException("Original PCA eigenvalues are not descending");
                values.add(value);
            }
        }
        if(values.size()!=components)throw new IOException("Original PCA eigenvalue count differs from requested components");
        var samples=new LinkedHashMap<PlinkQcInputs.Id,Sample>();
        try(var input=PlinkGlmInputs.limited(eigenvectors,limits.maximumReportBytes())) {
            String[] expectedHeader=header(List.of("FID","IID"),components);
            checkHeader(PlinkGlmInputs.line(input,limits.maximumLineBytes()),expectedHeader);
            for(String line;(line=PlinkGlmInputs.line(input,limits.maximumLineBytes()))!=null;) {
                String[] row=fields(line,expectedHeader.length);var id=new PlinkQcInputs.Id(row[0],row[1]);
                if(!expected.contains(id)||samples.containsKey(id))throw new IOException("Unknown/duplicate original PCA FID/IID pair");
                samples.put(id,new Sample(id,numbers(row,2,components)));
            }
        }
        if(!samples.keySet().equals(expected))throw new IOException("Original PCA omitted declared fitted samples");
        double maximumError=0;
        for(int a=0;a<components;a++)for(int b=0;b<=a;b++) {
            double product=0,bound=0;
            for(var sample:samples.values()) {
                Number x=sample.components().get(a),y=sample.components().get(b);
                product+=x.value()*y.value();bound+=Math.abs(x.value())*y.roundingRadius()+Math.abs(y.value())*x.roundingRadius()+x.roundingRadius()*y.roundingRadius();
            }
            double error=Math.abs(product-(a==b?1:0));maximumError=Math.max(maximumError,error);
            if(!Double.isFinite(product)||error>bound+64*Math.ulp(1.0)*samples.size())throw new IOException("Original PCA eigenvectors are not orthonormal within reported decimal precision");
        }
        return new Basis(values,samples,maximumError);
    }

    /**
     * Streams one PLINK1 variant row or two PLINK2 allele rows per declared marker.
     * Expected map iteration order is the declared BIM panel order. Coefficients
     * retain original engine signs/scales and are never normalized here. A consumer
     * must quarantine already-written output if a later row fails validation.
     */
    static WeightCounts weights(Path path,String engine,Map<String,Variant> expected,int components,Limits limits,WeightConsumer consumer) throws IOException {
        Objects.requireNonNull(consumer,"PCA weight consumer");boolean legacy=engine.equals("plink1");
        if(!legacy&&!engine.equals("plink2"))throw new IOException("Unknown PCA report engine");
        if(expected.isEmpty()||expected.size()>limits.maximumVariants()||components<1||components>limits.maximumComponents())throw new IOException("Original PCA weight dimensions exceed declared bounds");
        if(Math.multiplyExact(components+5L,128L)>limits.maximumRetainedBytes())throw new IOException("Original PCA weight row exceeds declared retained-byte bound");
        long rows=0;
        try(var input=PlinkGlmInputs.limited(path,limits.maximumReportBytes())) {
            String[] names=header(legacy?List.of("CHR","VAR","A1","A2"):List.of("CHROM","ID","REF","ALT","A1"),components);
            checkHeader(PlinkGlmInputs.line(input,limits.maximumLineBytes()),names);
            for(var entry:expected.entrySet()) {
                Variant variant=entry.getValue();if(!entry.getKey().equals(variant.id()))throw new IOException("Declared PCA weight marker key differs from identity");
                if(variant.firstAllele().equals(variant.secondAllele()))throw new IOException("Declared PCA weight alleles must be distinct");
                var alleles=new java.util.HashSet<String>();String first=null,second=null;
                for(int allele=0;allele<(legacy?1:2);allele++) {
                    String[] row=fields(PlinkGlmInputs.line(input,limits.maximumLineBytes()),names.length);
                    if(!row[1].equals(variant.id())||!row[0].equals(variant.chromosome()))throw new IOException("Original PCA weight marker order/identity/chromosome differs");
                    if(row[2].equals(row[3])||!Set.of(row[2],row[3]).equals(Set.of(variant.firstAllele(),variant.secondAllele())))throw new IOException("Original PCA weight allele pair differs");
                    String counted=legacy?row[2]:row[4];
                    if(!counted.equals(row[2])&&!counted.equals(row[3])||!alleles.add(counted))throw new IOException("Unknown/duplicate original PCA counted allele");
                    if(first!=null&&(!first.equals(row[2])||!second.equals(row[3])))throw new IOException("Original PCA allele rows disagree on REF/ALT order");
                    first=row[2];second=row[3];
                    consumer.accept(new Weight(variant.id(),row[0],row[2],row[3],counted,numbers(row,legacy?4:5,components)));rows++;
                }
            }
            if(PlinkGlmInputs.line(input,limits.maximumLineBytes())!=null)throw new IOException("Extra original PCA weight rows");
        }
        return new WeightCounts(expected.size(),rows);
    }

    /** A finite printed value; no coercion of NA, infinity, zero-underflow or invented precision. */
    private static Number number(String literal) throws IOException {
        try {
            double value=Double.parseDouble(literal);var decimal=new java.math.BigDecimal(literal);
            if(!Double.isFinite(value)||decimal.signum()!=0&&value==0||decimal.precision()>6)
                throw new NumberFormatException("Nonfinite/unresolved value or unexpected pinned-program precision");
            double radius=value==0?0:Math.pow(10,Math.floor(Math.log10(Math.abs(value)))-5)/2;
            return new Number(literal,value,radius);
        }catch(NumberFormatException failure){throw new IOException("Unreportable original PCA number: "+literal,failure);}
    }
    private static List<Number> numbers(String[] row,int offset,int count) throws IOException {
        var values=new ArrayList<Number>(count);for(int i=0;i<count;i++)values.add(number(row[offset+i]));return List.copyOf(values);
    }
    private static String[] header(List<String> prefix,int components) {
        var result=new ArrayList<>(prefix);for(int i=1;i<=components;i++)result.add("PC"+i);return result.toArray(String[]::new);
    }
    private static void checkHeader(String line,String[] expected) throws IOException {
        if(line==null||!Arrays.equals(fields(line.strip().replaceFirst("^#",""),expected.length),expected))throw new IOException("Original PCA header differs from requested pinned report columns");
    }
    private static String[] fields(String line,int length) throws IOException {
        if(line==null||line.isBlank())throw new IOException("Missing/blank original PCA row");String[] values=line.strip().split("\\s+");
        if(values.length!=length)throw new IOException("Original PCA report row width differs");return values;
    }
}
