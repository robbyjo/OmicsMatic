/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Native statistics and evidence classes for the exact HOPS0.35 AMPS def_anc script.
 * Algorithm attribution: Felix M. Key, https://github.com/keyfm/amps,
 * postprocessing.AMPS.r SHA256 7912dfcede0084134d1d677368f851a4d55dadeb0b7eaa591fcfe2e9c6cbdf62.
 * The upstream README states CC BY; its attribution and source behavior are retained.
 * This implementation produces data, not the upstream RData/PDF plots, MALT
 * alignments, taxonomic assignments or a biological authentication decision.
 */
public final class HopsEvidence {
    private static final long EXACT_INTEGER=9007199254740991L;
    private HopsEvidence() { }

    public record Limits(long fileBytes,long tableRows,long tableCells,int samples,int taxa) {
        public Limits {if(fileBytes<1 || tableRows<1 || tableCells<1 || samples<1 || taxa<1)
            throw new IllegalArgumentException("Positive complete-table capacities required");}
    }
    /** Null numeric values are R NA; Double.NaN is R NaN. Both remain distinct. */
    public record Statistics(String node,Double dr6,Long n6,Double dr4,Long n4,
            Double maximumTerminalDamage,Double uniquePerReference) {
        public Map<String,Object> document() {
            var out=new LinkedHashMap<String,Object>();out.put("node",node);out.put("dr6",token(dr6));out.put("n6",n6);
            out.put("dr4",token(dr4));out.put("n4",n4);out.put("maximum_terminal_damage",token(maximumTerminalDamage));
            out.put("unique_per_reference",token(uniquePerReference));return Collections.unmodifiableMap(out);
        }
    }
    public record Pair(String sample,String taxon,Statistics defaults,Statistics ancient,int evidenceClass) {
        public Map<String,Object> document() {return Map.of("sample",sample,"taxon",taxon,"default",defaults.document(),
            "ancient",ancient.document(),"evidence_class",evidenceClass);}
    }
    public record Inspection(List<Pair> pairs,List<String> samples,List<String> taxa,
            Map<String,Map<String,Integer>> fullMatrix,Map<String,Map<String,Integer>> reducedMatrix,List<String> warnings) {
        public Inspection {pairs=List.copyOf(pairs);samples=List.copyOf(samples);taxa=List.copyOf(taxa);
            fullMatrix=freeze(fullMatrix);reducedMatrix=freeze(reducedMatrix);warnings=List.copyOf(warnings);}
        public Map<String,Object> document() {return Map.of("pairs",pairs.stream().map(Pair::document).toList(),
            "samples",samples,"taxa",taxa,"full_matrix",fullMatrix,"reduced_matrix",reducedMatrix,"warnings",warnings);}
    }

    /** Six edit-distance bins0..5, four damage values C>T1/2,G>A20/19 and source uniqueness.
     * Source n4>1, rounded ratios and strict/nonstrict thresholds are preserved.
     * Counts outside exactly representable complete integer sums refuse assessment.
     */
    public static Statistics statistics(String node,long[] bins,Double[] terminalDamage,Double unique,boolean ancient) {
        Objects.requireNonNull(node);Objects.requireNonNull(bins);Objects.requireNonNull(terminalDamage);
        if(bins.length!=6 || terminalDamage.length!=4)throw new IllegalArgumentException("Require six original edit bins and four named terminal-damage values");
        long n6=0,n4=0;
        for(int i=0;i<bins.length;i++) {if(bins[i]<0 || bins[i]>EXACT_INTEGER)throw new IllegalArgumentException("Unassessable original edit count");n6=exactAdd(n6,bins[i]);if(i<4)n4=exactAdd(n4,bins[i]);}
        Double damage=sourceNumber(maximum(terminalDamage)),sourceUnique=sourceNumber(unique);
        if(n4<=1)return new Statistics(null,null,null,null,null,damage,sourceUnique);
        return new Statistics(node,ratio(bins,ancient?1:0,5),sourceCount(n6),ratio(bins,ancient?1:0,3),sourceCount(n4),damage,sourceUnique);
    }
    /** Original hierarchical def_anc classes1..4; these are not read counts or biological verdicts. */
    public static int evidenceClass(Statistics defaults,Statistics ancient) {
        Objects.requireNonNull(defaults);Objects.requireNonNull(ancient);
        if(defaults.dr4()==null || !(defaults.dr4()>=0.9))return 1;
        if(defaults.maximumTerminalDamage()==null || !(defaults.maximumTerminalDamage()>0))return 2;
        return ancient.dr4()!=null && ancient.dr4()>0.8?4:3;
    }
    private static double ratio(long[] bins,int start,int end) {
        long down=0,total=0;
        for(int i=start;i<end;i++) {long difference=bins[i+1]-bins[i];total=exactAdd(total,Math.abs(difference));if(difference<0)down=exactAdd(down,-difference);}
        double value=(double)down/total;if(!Double.isFinite(value) || value==0)return value;
        // Restricted R4.0.5 fround(x,3) for0<x<=1. Compare adjacent decimal
        // distances before ties-to-even, retaining binary floating-point behavior.
        double scaled=value*1000,integer=Math.floor(scaled),lower=integer/1000,upper=Math.ceil(scaled)/1000;
        double up=upper-value,low=value-lower;return up<low || up==low && integer%2==1?upper:lower;
    }
    private static long exactAdd(long a,long b) {long value=Math.addExact(a,b);if(value>EXACT_INTEGER)
        throw new IllegalArgumentException("Complete HOPS integer sum exceeds exact source-double assessment capacity");return value;}
    private static Double maximum(Double[] values) {
        for(Double value:values)if(value==null)return null;double result=Double.NEGATIVE_INFINITY;
        for(double value:values)result=Math.max(result,value);return result;
    }
    // extract.stats5 combines character identifiers and numeric fields with c(),
    // then the script coerces those fields back to numeric. Preserve that R4.0.5
    // scalar fixed/scientific conversion, including lost low-order digits.
    private static Double sourceNumber(Double value) {
        return HopsRScalar.mixed(value);
    }
    private static long sourceCount(long value) {return HopsRScalar.mixed((double)value).longValue();}
    /** JSON-safe explicit source numeric tokens: null=NA, strings distinguish NaN and infinities. */
    public static Object token(Double value) {
        if(value==null)return null;if(Double.isNaN(value))return "NaN";
        if(value==Double.POSITIVE_INFINITY)return "Inf";if(value==Double.NEGATIVE_INFINITY)return "-Inf";return value;
    }

    /** Read all complete source tables. Nodes are explicit literal output identities,
     * not automatically renamed from a user species list. Missing edit rows remain
     * unassessable NA, separately warned, even though the original matrix uses class1.
     */
    public static Inspection inspect(Path results,List<String> nodes,Limits limits)throws IOException {
        try {
            var warnings=new ArrayList<String>();var taxa=new ArrayList<String>();var seen=new HashSet<String>();
            for(String node:nodes) {if(node==null || node.isEmpty() || node.chars().anyMatch(c->c<32) || !seen.add(node))throw invalid("Require unique nonempty literal node names");taxa.add(node);}
            if(taxa.isEmpty() || taxa.size()>limits.taxa())throw invalid("Complete requested taxa exceed capacity or are empty");
            Table summary=table(results.resolve("default/RunSummary.txt"),limits);var samples=new ArrayList<>(summary.header().subList(1,summary.header().size()));
            if(samples.isEmpty() || samples.size()>limits.samples() || new HashSet<>(samples).size()!=samples.size())throw invalid("Complete original samples are absent/nonunique or exceed capacity");
            var pairs=new ArrayList<Pair>();var matrix=new LinkedHashMap<String,Map<String,Integer>>();for(String taxon:taxa)matrix.put(taxon,new LinkedHashMap<>());
            for(String sample:samples) {
                if(sample.isEmpty() || sample.equals(".") || sample.equals("..") || sample.indexOf('/')>=0 || sample.indexOf('\\')>=0)throw invalid("Original sample cannot be represented as a literal source member");
                Table read=table(results.resolve("default/readDist/"+sample+"_alignmentDist.txt"),limits);
                var def=statistics(results,"default",sample,taxa,read,limits,warnings);
                var ancient=statistics(results,"ancient",sample,taxa,read,limits,warnings);
                for(String taxon:taxa) {int score=evidenceClass(def.get(taxon),ancient.get(taxon));matrix.get(taxon).put(sample,score);pairs.add(new Pair(sample,taxon,def.get(taxon),ancient.get(taxon),score));}
            }
            var retainedSamples=new ArrayList<String>();for(String sample:samples)if(taxa.stream().anyMatch(t->matrix.get(t).get(sample)>1))retainedSamples.add(sample);
            var reduced=new LinkedHashMap<String,Map<String,Integer>>();
            for(String taxon:taxa)if(retainedSamples.stream().anyMatch(s->matrix.get(taxon).get(s)>1)) {
                String display=taxon.replace('>','.').replace('<','.');if(reduced.containsKey(display))throw invalid("Original display-name substitution creates a nonunique JSON identity");
                var row=new LinkedHashMap<String,Integer>();for(String sample:retainedSamples)row.put(sample,matrix.get(taxon).get(sample));reduced.put(display,row);
            }
            warnings.add("Classes1..4 reproduce source screening criteria, not read totals, absence or biological authentication. Missing and NaN statistics are retained separately.");
            warnings.add("The original mixed character/numeric c() conversion selects scalar fixed/scientific formatting with15-digit settings, then parses it back using the Linux R4.0.5 numeric path; source precision loss is retained.");
            warnings.add("This component implements def_anc statistics/matrices only. Original default-mode/PDF/RData behavior and MALT/LCA/MaltExtract filtering are not replaced or silently repaired.");
            return new Inspection(pairs,samples,taxa,matrix,reduced,warnings);
        }catch(IllegalArgumentException | ArithmeticException error) {throw invalid("Original numeric/source-table contract is unassessable: "+error);}
    }
    private static Map<String,Statistics> statistics(Path root,String mode,String sample,List<String> taxa,Table read,Limits limits,List<String> warnings)throws IOException {
        Table edit=table(root.resolve(mode+"/editDistance/"+sample+"_editDistance.txt"),limits),damage=table(root.resolve(mode+"/damageMismatch/"+sample+"_damageMismatch.txt"),limits);
        if(edit.header().size()<7)throw invalid("Missing complete original six edit-distance columns");
        for(int i=0;i<6;i++)if(!edit.header().get(i+1).equals(Integer.toString(i)))throw invalid("Original edit-distance bin order differs");
        int[] indices={column(damage,"C>T_1"),column(damage,"C>T_2"),column(damage,"G>A_20"),column(damage,"G>A_19")};int unique=column(read,"uniquePerReference");var out=new LinkedHashMap<String,Statistics>();
        for(String taxon:taxa) {
            List<String> ed=edit.rows().get(taxon),dam=damage.rows().get(taxon),rd=read.rows().get(taxon);
            if(ed==null) {warnings.add("UNASSESSABLE: missing literal edit-distance node "+taxon+" in "+mode+"/"+sample+"; source NA statistics retained, not absence.");out.put(taxon,new Statistics(null,null,null,null,null,null,null));continue;}
            long[] bins=new long[6];for(int i=0;i<6;i++)bins[i]=count(ed.get(i+1));Double[] values=new Double[4];for(int i=0;i<4;i++)values[i]=dam==null?null:number(dam.get(indices[i]));
            if(dam==null || rd==null)warnings.add("UNASSESSABLE: missing literal damage/read-distribution row for "+taxon+" in "+mode+"/"+sample+"; source NA retained.");
            out.put(taxon,statistics(taxon,bins,values,rd==null?null:number(rd.get(unique)),mode.equals("ancient")));
        }
        return out;
    }
    private record Table(List<String> header,Map<String,List<String>> rows) { }
    private static Table table(Path path,Limits limits)throws IOException {
        if(Files.size(path)>limits.fileBytes())throw invalid("Complete original table exceeds file-byte capacity: "+path);
        try(BufferedReader input=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            String line=input.readLine();if(line==null)throw invalid("Missing original table header: "+path);List<String> header=fields(line);if(header.size()<2 || new HashSet<>(header).size()!=header.size())throw invalid("Missing/nonunique original table columns: "+path);
            var rows=new LinkedHashMap<String,List<String>>();long cells=header.size();
            while((line=input.readLine())!=null) {List<String> fields=fields(line);cells=Math.addExact(cells,fields.size());if(rows.size()>=limits.tableRows() || cells>limits.tableCells())throw invalid("Complete original table exceeds row/cell capacity: "+path);
                if(fields.size()!=header.size() || fields.get(0).isEmpty() || rows.putIfAbsent(fields.get(0),fields)!=null)throw invalid("Incomplete/nonunique original table row: "+path);}
            return new Table(header,rows);
        }
    }
    private static List<String> fields(String line)throws IOException {
        if(line.length()>1048576 || line.indexOf('\0')>=0 || line.indexOf('"')>=0 || line.indexOf('\'')>=0)throw invalid("Unassessable original table quoting/line capacity; no identity repaired");return Arrays.asList(line.split("\t",-1));
    }
    private static int column(Table table,String name)throws IOException {int index=table.header().indexOf(name);if(index<1)throw invalid("Missing original column "+name);return index;}
    private static long count(String value)throws IOException {Double numeric=number(value);if(numeric==null || !Double.isFinite(numeric) || numeric<0 || numeric>EXACT_INTEGER || numeric!=Math.rint(numeric))throw invalid("Unassessable exact original edit count: "+value);return numeric.longValue();}
    private static Double number(String text)throws IOException {
        return switch(text) {case "NA"->null;case "NaN"->Double.NaN;case "Inf","+Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->{
            if(!text.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))throw invalid("Malformed original numeric token: "+text);
            try {yield HopsRScalar.decimal(text);}catch(NumberFormatException bad) {throw invalid("Malformed original numeric token: "+text);}
        }};
    }
    private static Map<String,Map<String,Integer>> freeze(Map<String,Map<String,Integer>> input) {var out=new LinkedHashMap<String,Map<String,Integer>>();for(var row:input.entrySet())out.put(row.getKey(),Collections.unmodifiableMap(new LinkedHashMap<>(row.getValue())));return Collections.unmodifiableMap(out);}
    private static IOException invalid(String message) {return new IOException("HOPS EVIDENCE FAIL2: "+message+". Required action: retain complete original tables/diagnostics, resolve the named format, identity or capacity discrepancy and retry a NEW directory. No subset, repaired identifier or biological result is admitted.");}
}
