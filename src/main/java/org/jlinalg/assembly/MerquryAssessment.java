/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.assembly;

import java.util.*;

/** Independent complete-count audit of Merqury 1.1/1.3 evaluation equations.
 * Original equations: marbl/merqury eval/spectra-cn.sh (United States Government Work).
 * Raw Meryl databases, original scripts, plots and source warnings remain separate.
 */
public final class MerquryAssessment {
    private MerquryAssessment() { }
    public record Quality(long assemblyOnlyOccurrences,long totalOccurrences,double error,double qv) { }
    public record Completeness(long recoveredDistinct,long solidReadDistinct,double percent) { }
    public record Spectrum(String copies,long readMultiplicity,long distinct) { }
    /** Exact count percentage; the original report can differ through intermediate awk serialization. */
    public record FalseDuplications(long cutoff,long[] distinctByCopyClass,long duplicatedDistinct,
                                    long consideredDistinct,double percentage,List<String> absentSourceFields) {
        public FalseDuplications {distinctByCopyClass=distinctByCopyClass.clone();absentSourceFields=List.copyOf(absentSourceFields);}
        @Override public long[] distinctByCopyClass(){return distinctByCopyClass.clone();}
        public boolean sourceWellDefined(){return absentSourceFields.isEmpty()&&consideredDistinct>0;}
    }
    public record Assessment(Quality quality,Completeness completeness,List<Spectrum> spectra,
                             long assemblyOnlyDistinct,long assemblyOnlyExtraOccurrences) {
        public Assessment {spectra=List.copyOf(spectra);}
    }
    /** Read keys are literal; noncanonical prepared databases are not silently canonicalized. */
    public static Assessment assess(Map<String,Long> read,Map<String,Long> assembly,int k,long solidCutoff,Runnable check) {
        validate(read,k,check);validate(assembly,k,check);
        if(solidCutoff<0)throw new IllegalArgumentException("Declare a nonnegative original solid cutoff");
        long total=0,onlyOccurrences=0,onlyDistinct=0,solid=0,recovered=0;
        var spectrum=new TreeMap<String,TreeMap<Long,Long>>();
        for(var entry:assembly.entrySet()) {
            check.run();total=Math.addExact(total,entry.getValue());
            if(!read.containsKey(entry.getKey())){onlyOccurrences=Math.addExact(onlyOccurrences,entry.getValue());onlyDistinct++;}
        }
        for(var entry:read.entrySet()) {
            check.run();long copies=assembly.getOrDefault(entry.getKey(),0L);String category=copies==0?"read-only":copies>4?">4":Long.toString(copies);
            var hist=spectrum.computeIfAbsent(category,key->new TreeMap<>());hist.merge(entry.getValue(),1L,Math::addExact);
            if(entry.getValue()>solidCutoff){solid++;if(copies>0)recovered++;}
        }
        var rows=new ArrayList<Spectrum>();
        for(String category:List.of("read-only","1","2","3","4",">4")) {
            var hist=spectrum.get(category);if(hist!=null)hist.forEach((mult,n)->rows.add(new Spectrum(category,mult,n)));
        }
        // Preserve the source cancellation-prone formula. This is an audit of it,
        // not a log1p/expm1 repair or a universal estimate of biological accuracy.
        double error=1-Math.pow(1-(double)onlyOccurrences/total,1.0/k);
        double qv=-10*Math.log(error)/Math.log(10);
        return new Assessment(new Quality(onlyOccurrences,total,error,qv),new Completeness(recovered,solid,100.0*recovered/solid),rows,onlyDistinct,onlyOccurrences-onlyDistinct);
    }
    public static Map<String,Long> unionSum(List<Map<String,Long>> tables,int k,Runnable check) {
        Objects.requireNonNull(tables);if(tables.isEmpty())throw new IllegalArgumentException("At least one complete table required");
        var union=new TreeMap<String,Long>();for(var table:tables){validate(table,k,check);table.forEach((mer,n)->union.merge(mer,n,Math::addExact));}return Collections.unmodifiableMap(union);
    }
    /** Meryl difference retains the left table's counts; it does not subtract counts. */
    public static Map<String,Long> difference(Map<String,Long> left,Map<String,Long> right,int k,Runnable check) {
        validate(left,k,check);validate(right,k,check);var out=new TreeMap<String,Long>();for(var e:left.entrySet()){check.run();if(!right.containsKey(e.getKey()))out.put(e.getKey(),e.getValue());}return Collections.unmodifiableMap(out);
    }
    /** Meryl intersect's values come from the FIRST operand; no minimum-count substitution. */
    public static Map<String,Long> intersect(Map<String,Long> left,Map<String,Long> right,int k,Runnable check) {
        validate(left,k,check);validate(right,k,check);var out=new TreeMap<String,Long>();for(var e:left.entrySet()){check.run();if(right.containsKey(e.getKey()))out.put(e.getKey(),e.getValue());}return Collections.unmodifiableMap(out);
    }
    public static Map<String,Long> greaterThan(Map<String,Long> input,long cutoff,int k,Runnable check) {
        validate(input,k,check);if(cutoff<0)throw new IllegalArgumentException("Nonnegative cutoff required");var out=new TreeMap<String,Long>();input.forEach((mer,n)->{check.run();if(n>cutoff)out.put(mer,n);});return Collections.unmodifiableMap(out);
    }
    /**
     * Original false_duplications.sh uses DISTINCT keys in copy classes, below
     * round-even(1.5 * the first copy-one histogram mode). The source awk leaves
     * an absent class as an empty field; shell whitespace then shifts columns.
     * Such a source report is explicitly undefined here, rather than repaired
     * by silently replacing those empty fields with zero and emitting a rate.
     */
    public static FalseDuplications falseDuplications(List<Spectrum> supplied,Runnable check) {
        Objects.requireNonNull(supplied);Objects.requireNonNull(check);var rows=new ArrayList<>(supplied);var bins=new HashSet<String>();
        for(var row:rows) {
            check.run();if(!List.of("read-only","1","2","3","4",">4").contains(row.copies())||row.readMultiplicity()<1||row.distinct()<1||!bins.add(row.copies()+"/"+row.readMultiplicity()))throw new IllegalArgumentException("Complete positive, unique original spectrum bins required");
        }
        rows.sort(Comparator.comparingLong(Spectrum::readMultiplicity));double maximum=0;long mode=0;
        for(var row:rows)if(row.copies().equals("1")&&(double)row.distinct()>maximum){maximum=(double)row.distinct();mode=row.readMultiplicity();}
        double rounded=Math.rint(mode*1.5);if(rounded>=0x1.0p63)throw new ArithmeticException("False-duplication cutoff exceeds exact int64 domain");long cutoff=(long)rounded;
        long[] counts=new long[5];boolean[] present=new boolean[5];List<String> labels=List.of("1","2","3","4",">4");
        for(var row:rows) {
            check.run();int i=labels.indexOf(row.copies());if(i>=0&&row.readMultiplicity()<cutoff){counts[i]=Math.addExact(counts[i],row.distinct());present[i]=true;}
        }
        var absent=new ArrayList<String>();long total=0,duplicated=0;
        for(int i=0;i<counts.length;i++){total=Math.addExact(total,counts[i]);if(i>0)duplicated=Math.addExact(duplicated,counts[i]);if(!present[i])absent.add(labels.get(i));}
        double percent=absent.isEmpty()&&total>0?100.0*duplicated/total:Double.NaN;
        return new FalseDuplications(cutoff,counts,duplicated,total,percent,absent);
    }
    public static void validate(Map<String,Long> table,int k,Runnable check) {
        Objects.requireNonNull(table);Objects.requireNonNull(check);if(k<1)throw new IllegalArgumentException("Positive k required");
        for(var entry:table.entrySet()) {
            check.run();String mer=Objects.requireNonNull(entry.getKey());Long count=Objects.requireNonNull(entry.getValue());
            if(mer.length()!=k||mer.chars().anyMatch(c->c!='A'&&c!='C'&&c!='G'&&c!='T')||count<=0)throw new IllegalArgumentException("Require literal uppercase DNA k-mers with positive int64 counts");
        }
    }
    /** Meryl canonical word uses its encoded A,C,T,G order, which differs from ASCII A,C,G,T. */
    public static String canonical(String word) {
        Objects.requireNonNull(word);if(word.isEmpty())throw new IllegalArgumentException("Empty k-mer");var reverse=new StringBuilder(word.length());
        for(int i=word.length()-1;i>=0;i--)reverse.append(switch(word.charAt(i)){case'A'->'T';case'C'->'G';case'G'->'C';case'T'->'A';default->throw new IllegalArgumentException("Literal uppercase DNA k-mer required");});
        String other=reverse.toString();
        for(int i=0;i<word.length();i++) {
            int a="ACTG".indexOf(word.charAt(i)),b="ACTG".indexOf(other.charAt(i));
            if(a!=b)return a<b?word:other;
        }
        return word;
    }
}
