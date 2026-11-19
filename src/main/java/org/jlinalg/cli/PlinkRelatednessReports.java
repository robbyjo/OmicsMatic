/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import jdistlib.Normal;
import org.jlinalg.genetics.KingRobustKinship;
import static org.jlinalg.cli.PlinkRelatednessSupport.*;
import static org.jlinalg.cli.PlinkRelatednessInputs.*;

/** Original reports stay untouched; native checks and canonical exports are separate. */
final class PlinkRelatednessReports {
    private PlinkRelatednessReports() { }
    private static final String KING="#FID1 IID1 FID2 IID2 NSNP HETHET IBS0 HET1_HOM2 HET2_HOM1 KINSHIP";
    private static final String GENOME="FID1 IID1 FID2 IID2 RT EZ Z0 Z1 Z2 PI_HAT PHE DST PPC RATIO IBS0 IBS1 IBS2 HOMHOM HETHET";
    private static final String NATIVE="native_jointly_called\tnative_both_heterozygous\tnative_ibs0\tnative_first_het_second_hom\tnative_first_hom_second_het\tnative_king_between_family\tnative_disposition\trequested_estimator_reportable";
    record Result(Map<String,Object> summary,List<String> warnings) { }
    private record Row(String[] fields,int first,int second) { }
    @FunctionalInterface private interface Check {void accept(Row row,KingRobustKinship.Estimate e)throws IOException;}
    private static final class Stats {
        long rows,finite,unreportable,operations,ppcUnavailable,batches,scanBytes;
        String nativeScope="RETURNED_PAIRS";
        boolean completePopulation;
        final Map<String,Object> extra=new LinkedHashMap<>();
        Map<String,Object> summary(){var m=new LinkedHashMap<String,Object>();m.put("pair_rows_checked",rows);m.put("reportable_pair_rows",finite);m.put("unreportable_pair_rows",unreportable);m.put("native_pair_variant_operations",operations);m.put("native_pair_scan_bytes",scanBytes);m.put("native_batches",batches);m.put("native_scope",nativeScope);m.put("native_counter_projection","PAIR_PARTICIPANTS_ONLY_FROM_UNCHANGED_COMPLETE_INPUT");m.put("complete_source_pair_population_checked",completePopulation);m.put("unavailable_source_PPC_values",ppcUnavailable);m.putAll(extra);return m;}
    }
    private static final class Batch {
        final List<Row> rows=new ArrayList<>();long bytes;
        final Prepared p;final PlinkRelatednessContracts.Request r;final Clock clock;final Stats stats;final Check check;
        Batch(Prepared p,PlinkRelatednessContracts.Request r,Clock clock,Stats stats,Check check){this.p=p;this.r=r;this.clock=clock;this.stats=stats;this.check=check;}
        void add(Row row)throws IOException {
            long next=512;for(String v:row.fields())next=Math.addExact(next,4L*v.length()+64);
            long overhead=Math.addExact(p.estimatedResidentBytes(),Math.addExact(4096L,160L*Math.min((long)p.samples().size(),2L*(rows.size()+1))));
            if(!rows.isEmpty()&&(rows.size()>=r.limits().pairBatchSize()||Math.addExact(overhead,Math.addExact(bytes+next,416L*(rows.size()+1)))>r.limits().workingBytes()))flush();
            overhead=Math.addExact(p.estimatedResidentBytes(),Math.addExact(4096L,160L*Math.min((long)p.samples().size(),2L*(rows.size()+1))));
            require(Math.addExact(overhead,Math.addExact(bytes+next,416L*(rows.size()+1)))<=r.limits().workingBytes(),"Pair-batch working_bytes exceeded; no rows are truncated");
            rows.add(row);bytes+=next;
        }
        void flush()throws IOException {
            if(rows.isEmpty())return;long work=Math.multiplyExact((long)rows.size(),p.variants());stats.operations=Math.addExact(stats.operations,work);
            require(stats.operations<=r.limits().pairVariantOperations(),"pair_variant_operations exceeded; choose suitable declared capacity, population or explicit audit mode");
            var pairs=new ArrayList<KingRobustKinship.Pair>(rows.size());for(Row row:rows)pairs.add(new KingRobustKinship.Pair(row.first(),row.second()));
            var estimates=count(p,pairs,bytes,r.limits(),clock);stats.scanBytes=Math.addExact(stats.scanBytes,Files.size(Path.of(p.prefix()+".bed")));for(int i=0;i<rows.size();i++)check.accept(rows.get(i),estimates.get(i));
            stats.batches++;rows.clear();bytes=0;
        }
    }
    static Result publish(Path root,PlinkRelatednessContracts.Request r,Prepared p,boolean verify,Clock clock)throws IOException {
        var stats=new Stats();var warnings=new ArrayList<String>();
        switch(r.operation()){
            case KING_TABLE,GENOME->table(root,r,p,verify,clock,stats);
            case KING_MATRIX->matrix(root,r,p,verify,clock,stats);
            case KING_CUTOFF->cutoff(root,r,p,verify,clock,stats);
        }
        stats.extra.put("samples",p.samples().size());stats.extra.put("input_variants",p.variants());
        if(stats.unreportable>0)warnings.add(stats.unreportable+" source pairs are UNREPORTABLE. Their raw source values are retained and requested_estimator_reportable=false; do not turn NaN/Inf/no-joint-call pairs into zero coefficients or unrelated pairs. Resolve the stated genotype/information problem before pair-level inference.");
        if(stats.ppcUnavailable>0)warnings.add(stats.ppcUnavailable+" original PPC values are unavailable. Their NA/NaN tokens remain unchanged; do not infer a successful concordance test from an absent value.");
        if(!stats.completePopulation&&r.operation()!=PlinkRelatednessContracts.Operation.KING_CUTOFF)warnings.add("The native audit checks every returned pair, not independently omitted pairs in a source-filtered table. Preserve the declared filter and exact original command; no independent completeness claim is made for filtered output.");
        if(r.operation()==PlinkRelatednessContracts.Operation.KING_CUTOFF&&!bool(r.settings(),"audit_retained_pairs"))warnings.add("The requested audit_retained_pairs=false verifies the exact original keep/remove partition only. It does not independently certify an unrelated set, refit coefficients, assess unassessable pairs or prove optimal selection. Request the explicit retained-pair native audit when that additional work is needed.");
        return new Result(stats.summary(),List.copyOf(warnings));
    }
    private static void table(Path root,PlinkRelatednessContracts.Request r,Prepared p,boolean verify,Clock clock,Stats stats)throws IOException {
        boolean king=r.operation()==PlinkRelatednessContracts.Operation.KING_TABLE;
        String header=king?KING:GENOME;int columns=king?10:19;var l=r.limits();
        Path source=root.resolve("worker/result."+(king?"kin0":"genome"));
        try(var in=new Lines(source,l.lineBytes(),clock);var out=new Table(root.resolve("pairs.tsv"),verify,l.lineBytes(),clock)){
            String first=in.next();require(first!=null&&first.strip().replaceAll("\\s+"," ").equals(header),"Unexpected original "+(king?"KING":"GENOME")+" header");
            out.line(header.replace(' ','\t')+"\t"+NATIVE+(king?"":"\tppc_reportable"));
            var batch=new Batch(p,r,clock,stats,(row,e)->{
                boolean reportable=king?checkKing(row.fields(),e,r):checkGenome(row.fields(),e,r,stats);
                if(reportable)stats.finite++;else stats.unreportable++;
                String line=String.join("\t",row.fields())+"\t"+nativeFields(e,reportable);
                if(!king)line+="\t"+Double.isFinite(value(row.fields()[12]));
                out.line(line);
            });
            long previous=-1;String line;
            while((line=in.next())!=null){String[] f=fields(line,columns,"original pair report");int a=p.sample(new Id(f[0],f[1])),b=p.sample(new Id(f[2],f[3]));
                long ordinal=king?lowerOrdinal(a,b,p.samples().size()):upperOrdinal(a,b,p.samples().size());require(ordinal>previous,"Original pair population is duplicated or no longer in its pinned profile order");previous=ordinal;
                if(bool(r.settings(),"rel_check"))require(f[0].equals(f[2]),"Original rel-check returned different-FID pair");
                batch.add(new Row(f,a,b));stats.rows++;
            }
            batch.flush();
            boolean noFilter=king?r.settings().get("kinship_filter")==null:r.settings().get("min_pi_hat")==null&&r.settings().get("max_pi_hat")==null;
            stats.completePopulation=noFilter;long expected=bool(r.settings(),"rel_check")?p.sameFamilyPairs():p.totalPairs();
            require(!noFilter||stats.rows==expected,"Original complete pair population is missing rows");
            require(stats.rows==0||stats.finite>0,"No pair has a reportable original relationship estimate; retain raw/partial diagnostics, no complete analysis admitted");
            stats.extra.put("empty_source_filtered_result",stats.rows==0&&!noFilter);
            out.end();
        }
    }
    private static boolean checkKing(String[] f,KingRobustKinship.Estimate e,PlinkRelatednessContracts.Request r)throws IOException {
        var c=e.counts();require(whole(f[4])==c.jointlyCalled(),"Original KING NSNP differs from complete native joint calls");
        long[] counts={c.bothHeterozygous(),c.oppositeHomozygotes(),c.firstHetSecondHom(),c.firstHomSecondHet()};
        for(int i=0;i<counts.length;i++){
            if(bool(r.settings(),"counts"))require(whole(f[5+i])==counts[i],"Original KING count differs from native bit census");
            else significant(f[5+i],(double)counts[i]/c.jointlyCalled());
        }
        significant(f[9],e.coefficient());Double filter=nullableNumber(r.settings(),"kinship_filter");
        require(filter==null||Double.isNaN(e.coefficient())||e.coefficient()>=filter,"Original pair violates requested exact KING filter");
        return e.disposition()==KingRobustKinship.Disposition.FINITE&&Double.isFinite(value(f[9]));
    }
    private static boolean checkGenome(String[] f,KingRobustKinship.Estimate e,PlinkRelatednessContracts.Request r,Stats stats)throws IOException {
        var c=e.counts();require(whole(f[14])==c.oppositeHomozygotes()&&whole(f[15])==c.ibs1()&&whole(f[16])==c.ibs2(),"Original GENOME IBS counts differ from native captured genotypes");
        fixed(f[11],(c.ibs2()+0.5*c.ibs1())/c.jointlyCalled(),6);
        long hom=whole(f[17]),het=whole(f[18]);require(hom<=c.oppositeHomozygotes()&&het<=c.bothHeterozygous(),"Original distance-thinned PPC counts exceed complete native categories");
        double inverse=1.0/(hom+het);
        // Preserve the original source's approximate constants, not idealized 2/3 and 2/9.
        double z=(het*inverse-0.666666)/Math.sqrt(0.2222222*inverse);
        double ppc=Normal.cumulative(z,0,1,true,false);fixed(f[12],ppc,4);
        if(!Double.isFinite(value(f[12])))stats.ppcUnavailable++;
        fixed(f[13],hom==0?Double.NaN:(double)het/hom,4);
        double z0=value(f[6]),z1=value(f[7]),z2=value(f[8]),pi=value(f[9]);
        boolean finite=Double.isFinite(z0)&&Double.isFinite(z1)&&Double.isFinite(z2)&&Double.isFinite(pi)&&c.jointlyCalled()>0;
        if(finite){
            require(Math.abs(z0+z1+z2-1)<=0.000151,"Rounded original Z0/Z1/Z2 no longer sum to one");
            require(Math.abs(pi-(z2+0.5*z1))<=0.000126,"Rounded original PI_HAT differs from Z2+Z1/2");
            if(!bool(r.settings(),"unbounded"))for(double d:new double[]{z0,z1,z2,pi})require(d>=-0.0000501&&d<=1.0000501,"Bounded original IBD value is outside source rounding bounds");
            Double min=nullableNumber(r.settings(),"min_pi_hat"),max=nullableNumber(r.settings(),"max_pi_hat");
            require((min==null||pi>=min-0.0000501)&&(max==null||pi<=max+0.0000501),"Original PI_HAT violates declared filter even allowing source four-decimal rounding");
        }
        return finite;
    }
    private static String nativeFields(KingRobustKinship.Estimate e,boolean reportable){var c=e.counts();return c.jointlyCalled()+"\t"+c.bothHeterozygous()+"\t"+c.oppositeHomozygotes()+"\t"+c.firstHetSecondHom()+"\t"+c.firstHomSecondHet()+"\t"+e.coefficient()+"\t"+e.disposition()+"\t"+reportable;}
    private static void matrix(Path root,PlinkRelatednessContracts.Request r,Prepared p,boolean verify,Clock clock,Stats stats)throws IOException {
        var ids=ids(root.resolve("worker/result.king.id"),p,r,clock);require(ids.size()==p.samples().size(),"Original matrix ID population changed");for(int i=0;i<ids.size();i++)require(ids.get(i)==i,"Original matrix axes differ from captured FAM order");
        boolean single=text(r.settings(),"precision").equals("FLOAT32");int bytes=single?4:8;Path matrix=root.resolve("worker/result.king.bin");
        require(Files.size(matrix)==Math.multiplyExact(p.totalPairs(),bytes),"Original triangular matrix size differs; no diagonal or extra entries are admitted");
        try(var in=new BufferedInputStream(Files.newInputStream(matrix));var out=new Table(root.resolve("pairs.tsv"),verify,r.limits().lineBytes(),clock)){
            out.line("FID1\tIID1\tFID2\tIID2\toriginal_king\t"+NATIVE);
            var batch=new Batch(p,r,clock,stats,(row,e)->{
                byte[] raw=in.readNBytes(bytes);require(raw.length==bytes,"Truncated original binary matrix");var b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);double actual=single?b.getFloat():b.getDouble();
                double expected=single?(float)e.coefficient():e.coefficient();
                require(Double.isFinite(actual)&&e.disposition()==KingRobustKinship.Disposition.FINITE,"Original matrix has unreportable NaN/Inf/zero-heterozygosity entries; no complete usable matrix is admitted");
                require(single?Float.floatToIntBits((float)actual)==Float.floatToIntBits((float)expected):Double.doubleToLongBits(actual)==Double.doubleToLongBits(expected),"Original binary KING value differs from native estimator bits");
                out.line(String.join("\t",row.fields())+"\t"+actual+"\t"+nativeFields(e,true));stats.finite++;
            });
            for(int a=1;a<p.samples().size();a++)for(int b=0;b<a;b++){var x=p.id(a);var y=p.id(b);batch.add(new Row(new String[]{x.fid(),x.iid(),y.fid(),y.iid()},a,b));stats.rows++;}
            batch.flush();require(in.read()==-1,"Extra binary matrix entries");out.end();stats.completePopulation=true;stats.nativeScope="ALL_TRIANGULAR_PAIRS";
        }
    }
    private static List<Integer> ids(Path path,Prepared p,PlinkRelatednessContracts.Request r,Clock clock)throws IOException {
        var result=new ArrayList<Integer>();boolean allZero=p.samples().stream().allMatch(s->s.familyId().equals("0"));
        try(var in=new Lines(path,r.limits().lineBytes(),clock)){
            String header=in.next();require(header!=null,"Missing original ID header");String[] h=header.strip().split("\\s+");boolean fid=Arrays.equals(h,new String[]{"#FID","IID"});require(fid||allZero&&Arrays.equals(h,new String[]{"#IID"}),"Unexpected original ID schema; missing FID is permitted only for an all-zero-FID input");
            int previous=-1;String line;while((line=in.next())!=null){String[] f=fields(line,fid?2:1,"original ID list");int sample=p.sample(new Id(fid?f[0]:"0",fid?f[1]:f[0]));require(sample>previous,"Original ID list duplicated/reordered captured participants");previous=sample;result.add(sample);require(result.size()<=p.samples().size(),"Original ID population too large");}
        }
        return result;
    }
    private static void cutoff(Path root,PlinkRelatednessContracts.Request r,Prepared p,boolean verify,Clock clock,Stats stats)throws IOException {
        var kept=ids(root.resolve("worker/result.king.cutoff.in.id"),p,r,clock);var removed=ids(root.resolve("worker/result.king.cutoff.out.id"),p,r,clock);
        var keep=new BitSet(p.samples().size());var discard=new BitSet(p.samples().size());for(int i:kept)keep.set(i);for(int i:removed)discard.set(i);var intersection=(BitSet)keep.clone();intersection.and(discard);require(intersection.isEmpty(),"Original keep/remove lists overlap");var union=(BitSet)keep.clone();union.or(discard);require(union.cardinality()==p.samples().size(),"Original keep/remove partition omits participants");
        try(var out=new Table(root.resolve("selection.tsv"),verify,r.limits().lineBytes(),clock)){out.line("FID\tIID\toriginal_decision");for(int i=0;i<p.samples().size();i++){var id=p.id(i);out.line(id.fid()+"\t"+id.iid()+"\t"+(keep.get(i)?"KEPT":"EXCLUDED"));}out.end();}
        stats.extra.put("kept_samples",kept.size());stats.extra.put("excluded_samples",removed.size());stats.extra.put("source_selection_rows",p.samples().size());stats.extra.put("optimal_selection_independently_proved",false);stats.nativeScope="SOURCE_PARTITION_ONLY";
        if(bool(r.settings(),"audit_retained_pairs")){
            double threshold=number(r.settings(),"threshold");stats.nativeScope="ALL_RETAINED_PAIRS";
            try(var out=new Table(root.resolve("pairs.tsv"),verify,r.limits().lineBytes(),clock)){
                out.line("FID1\tIID1\tFID2\tIID2\t"+NATIVE.replace("requested_estimator_reportable","native_retained_pair_assessable"));
                var batch=new Batch(p,r,clock,stats,(row,e)->{require(e.disposition()==KingRobustKinship.Disposition.FINITE,"Original retained set has an unassessable pair; it is not independently admitted as an unrelated set");require(e.coefficient()<=threshold,"Original retained pair exceeds the exact native cutoff; retain the discrepancy and review source numerical/selection behavior");out.line(String.join("\t",row.fields())+"\t"+nativeFields(e,true));stats.finite++;});
                for(int a=1;a<kept.size();a++)for(int b=0;b<a;b++){int i=kept.get(a),j=kept.get(b);var x=p.id(i);var y=p.id(j);batch.add(new Row(new String[]{x.fid(),x.iid(),y.fid(),y.iid()},i,j));stats.rows++;}
                batch.flush();out.end();
            }
        }
    }
    private static long whole(String text)throws IOException {
        try{long value=new java.math.BigDecimal(text).longValueExact();require(value>=0,"Negative original count");return value;}catch(NumberFormatException|ArithmeticException e){throw new IOException("Original count is not a nonnegative integer: "+text,e);}
    }
    private static double value(String text)throws IOException {
        return switch(text.toLowerCase(Locale.ROOT)){case "na","nan","-nan","+nan","nan(ind)","-nan(ind)"->Double.NaN;case "inf","+inf","infinity","+infinity"->Double.POSITIVE_INFINITY;case "-inf","-infinity"->Double.NEGATIVE_INFINITY;default->{try{yield Double.parseDouble(text);}catch(NumberFormatException e){throw new IOException("Unsupported original numeric token: "+text,e);}}};
    }
    private static void fixed(String raw,double expected,int decimals)throws IOException {
        double actual=value(raw);if(!Double.isFinite(expected)){require(Double.isNaN(expected)?Double.isNaN(actual):actual==expected,"Original nonfinite class differs from native arithmetic");return;}
        require(Double.isFinite(actual)&&Math.abs(actual-expected)<=0.5001*Math.pow(10,-decimals)+4*Math.ulp(expected),"Original rounded field differs from native arithmetic: "+raw+" versus "+expected);
    }
    private static void significant(String raw,double expected)throws IOException {
        if(!Double.isFinite(expected)){fixed(raw,expected,0);return;}
        double actual=value(raw),tolerance=expected==0?0:0.5001*Math.pow(10,Math.floor(Math.log10(Math.abs(expected)))-5);
        require(Double.isFinite(actual)&&Math.abs(actual-expected)<=tolerance+4*Math.ulp(expected),"Original six-significant-digit field differs from native arithmetic: "+raw+" versus "+expected);
    }
}
