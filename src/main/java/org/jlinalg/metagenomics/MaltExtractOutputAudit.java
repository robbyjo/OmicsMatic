/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.io.*;
import java.util.*;

/** Admission checks for complete retained ORIGINAL MaltExtract1.7 output.
 * Original statistics remain upstream results, not new native statistical methods.
 * Filename/row/summary transformations follow the three distinct source classes
 * in the GPL HOPS0.35 binary; original bytes and nonfinite cells are never repaired.
 */
public final class MaltExtractOutputAudit {
    private MaltExtractOutputAudit() { }
    public interface View {
        Set<String> members();
        BufferedReader reader(String member)throws IOException;
    }
    public record Population(MaltExtractRmaReader.Report report,
            List<MaltExtractRmaReader.Read> reads,Set<Integer> selected) {
        public Population {reads=List.copyOf(reads);selected=Set.copyOf(selected);}
    }
    public static String tableName(String raw) {return raw.replace(' ','_').replace('\'','_').replace('#','_').replace('$','_');}
    public static String fileName(String raw) {return raw.replace(' ','_').replace('/','_').replace('\\','_').replace('#','_');}
    public static String summaryName(String raw) {return raw.replace(' ','_');}
    private static final Map<String,String> HEADERS=headers();
    private static Map<String,String> headers() {
        var h=new LinkedHashMap<String,String>();
        h.put("coverage/_coverageHist.txt","Taxon\tReference\t0\t1\t2\t3\t4\t5\t6\t7\t8\t9\t10\thigher");
        h.put("coverage/_postionsCovered.txt","Taxon\tReference\tAverageCoverage\tCoverge_StandardDeviation\tpercCoveredHigher1\tpercCoveredHigher2\tpercCoveredHigher3\tpercCoveredHigher4\tpercCoveredHigher5");
        var damage=new StringBuilder("Node");
        for(int i=1;i<=20;i++)damage.append('\t').append(i<=10?"C>T_":"G>A_").append(i);
        for(int i=1;i<=20;i++)damage.append('\t').append(i<=10?"D>V(11Substitution)_":"H>B(11Substitution)_").append(i);
        h.put("damageMismatch/_damageMismatch.txt",damage+"\tconsidered_Matches");
        h.put("editDistance/_editDistance.txt","Taxon\t0\t1\t2\t3\t4\t5\t6\t7\t8\t9\t10\thigher");
        h.put("filterInformation/_assignedReads.txt","Node\tNumberOfAssignedReads");
        h.put("filterInformation/_filterTable.txt","Node\tNumberOfUnfilteredReads\tNumberOfFilteredReads\tNumberOfUnfilteredAlignments\tnumberOfAlignments\tturnedOn?\tdownSampling?");
        h.put("percentIdentity/_percentIdentity.txt","Taxon\t80\t85\t90\t95\t100");
        h.put("readDist/_additionalNodeEntries.txt","TargetNode\t01\t02\t03\t04\t05\t06\t07\t08\t09\t10");
        h.put("readDist/_alignmentDist.txt","Taxon\tReference\tuniquePerReference\tnonStacked\tnonDuplicatesonReference\tTotalAlignmentsOnReference\tReferenceLength");
        var lengths=new StringBuilder("Taxon");for(int i=25;i<=200;i+=5)lengths.append('\t').append(i).append("bp");
        h.put("readDist/_readLengthDist.txt",lengths.toString());
        h.put("readDist/_readLengthStat.txt","Taxon\tMean\tGeometricMean\tMedian\tStandardDev");
        return Collections.unmodifiableMap(h);
    }
    private record Table(Map<String,List<String[]>> rows,long nonfinite,List<Integer> ragged,int lines) { }
    private static String line(BufferedReader reader)throws IOException {
        String value=reader.readLine();if(value!=null&&value.length()>10000000)throw new IOException("Complete original output line exceeds declared10M character capacity");return value;
    }
    private static Table table(View view,String path,String expected,Set<String> labels,boolean unique)throws IOException {
        var rows=new TreeMap<String,List<String[]>>();var ragged=new ArrayList<Integer>();long nonfinite=0;int lines=0;
        try(var r=view.reader(path)) {
            String header=line(r);if(!expected.equals(header))throw new IOException("Original header differs: "+path);
            int width=header.split("\t",-1).length;
            for(String raw;(raw=line(r))!=null;) {
                if(++lines>1000000)throw new IOException("Complete original table exceeds row capacity: "+path);
                String[] fields=raw.split("\t",-1);if(fields.length!=width)ragged.add(lines);
                if(!labels.contains(fields[0]))throw new IOException("Unexpected original taxon label in "+path+": "+fields[0]);
                var group=rows.computeIfAbsent(fields[0],k->new ArrayList<>());if(unique&&!group.isEmpty())throw new IOException("Ambiguous duplicate original row: "+path+" / "+fields[0]);group.add(fields);
                for(String value:fields)if(Set.of("NaN","Infinity","-Infinity","Inf","-Inf").contains(value))nonfinite++;
            }
        }
        if(!rows.keySet().equals(labels))throw new IOException("Incomplete original selected taxon rows: "+path);
        return new Table(rows,nonfinite,List.copyOf(ragged),lines);
    }
    private static String[] one(Table table,String label,int width,String path)throws IOException {
        var rows=table.rows().get(label);if(rows==null||rows.size()!=1||rows.get(0).length!=width)throw new IOException("Required original counter row is ambiguous/ragged: "+path+" / "+label);return rows.get(0);
    }
    private static long count(String value)throws IOException {
        if(!value.matches("0|[1-9][0-9]*"))throw new IOException("Invalid literal original nonnegative count: "+value);
        try{return Long.parseLong(value);}catch(NumberFormatException e){throw new IOException("Original count exceeds complete-counter capacity",e);}
    }
    private static Map<MaltExtractReadProjection.ReadPair,Integer> fasta(View view,String path)throws IOException {
        var pairs=new HashMap<MaltExtractReadProjection.ReadPair,Integer>();
        try(var r=view.reader(path)) {
            String header=line(r);if(header==null)return pairs;
            if(header.equals("None")){if(line(r)!=null)throw new IOException("Original None marker has extra content: "+path);return pairs;}
            do {
                String sequence=line(r);if(!header.startsWith(">")||sequence==null)throw new IOException("Incomplete literal original FASTA: "+path);
                pairs.merge(new MaltExtractReadProjection.ReadPair(header,sequence),1,Math::addExact);
                if(pairs.size()>1000000)throw new IOException("Complete original FASTA exceeds read capacity");
            }while((header=line(r))!=null);
        }
        return pairs;
    }
    private static Map<MaltExtractReadProjection.ReadPair,Integer> pairs(List<MaltExtractReadProjection.ReadPair> reads) {
        var result=new HashMap<MaltExtractReadProjection.ReadPair,Integer>();for(var read:reads)result.merge(read,1,Math::addExact);return result;
    }
    /** Refuse a collision rather than let original output overwrite another taxon. */
    public static void names(Map<Integer,String> names,Set<Integer> ids)throws IOException {
        var table=new HashSet<String>();var files=new HashSet<String>();var summary=new HashSet<String>();
        for(int id:ids) {
            String raw=names.get(id);if(raw==null||raw.isEmpty()||raw.chars().anyMatch(c->c<32||c==127))throw new IOException("Missing/control-character original display name for selected taxon "+id);
            if(!table.add(tableName(raw))||!files.add(fileName(raw))||!summary.add(summaryName(raw)))throw new IOException("Selected taxa collide in an original output naming namespace; no files may be overwritten");
            if(Set.of(".","..").contains(fileName(raw)))throw new IOException("Original output name is not a regular filename");
        }
    }
    public static Map<String,Object> audit(View view,Map<String,Population> samples,
            Map<Integer,String> names,List<String> modes,boolean complete)throws IOException {
        if(samples.isEmpty()||!Set.of(List.of("default"),List.of("default","ancient")).contains(modes))throw new IOException("Explicit original output modes/population required");
        var ids=new TreeSet<Integer>();samples.values().forEach(s->ids.addAll(s.selected()));names(names,ids);
        var required=new TreeSet<String>();var summaries=new ArrayList<Map<String,Object>>();var tables=new ArrayList<Map<String,Object>>();var warnings=new ArrayList<String>();
        var filtered=new HashMap<String,Long>();long nonfinite=0;
        for(String mode:modes)for(var entry:samples.entrySet()) {
            String sample=entry.getKey(),base=sample+".rma6";Population population=entry.getValue();
            var labels=new TreeSet<String>();for(int id:population.selected())labels.add(tableName(names.get(id)));
            var parsed=new HashMap<String,Table>();
            for(var specification:HEADERS.entrySet()) {
                String template=specification.getKey();int slash=template.indexOf('/');
                String path=mode+"/"+template.substring(0,slash+1)+base+template.substring(slash+1);required.add(path);
                boolean multiple=template.startsWith("coverage/")||template.endsWith("_alignmentDist.txt");
                Table t=table(view,path,specification.getValue(),labels,!multiple);parsed.put(template,t);nonfinite+=t.nonfinite();
                tables.add(Map.of("path",path,"rows",t.lines(),"nonfinite_cells",t.nonfinite(),"ragged_rows",t.ragged()));
                if(!t.ragged().isEmpty())warnings.add("ORIGINAL RAGGED TABLE: "+path+" rows"+t.ragged()+" retained unchanged; dependent tabular interpretation is skipped. Resolve with upstream authors or a separately qualified interpretation; do not pad or truncate cells.");
            }
            for(int id:new TreeSet<>(population.selected())) {
                String name=tableName(names.get(id));String[] assigned=one(parsed.get("filterInformation/_assignedReads.txt"),name,2,"assignedReads");
                String[] filter=one(parsed.get("filterInformation/_filterTable.txt"),name,7,"filterTable");String[] damage=one(parsed.get("damageMismatch/_damageMismatch.txt"),name,42,"damageMismatch");
                long stored=population.report().classifications().containsKey(id)?population.report().classifications().get(id).reads():0;
                long before=count(filter[1]),after=count(filter[2]),beforeMatches=count(filter[3]),afterMatches=count(filter[4]);
                // StrainMisMatchContainer counts only retained alignments with
                // source alignment length>=20; this is not the filter counter.
                long damageMatches=count(damage[41]);
                if(count(assigned[1])!=stored||before>stored||after>before||afterMatches>beforeMatches||damageMatches>afterMatches)throw new IOException("Original selected-class counters contradict complete stored input: "+sample+" / "+mode+" / "+id);
                for(int i=1;i<41;i++)try{Double.parseDouble(damage[i]);}catch(NumberFormatException e){throw new IOException("Unparseable original damage cell; retain literal output",e);}
                if(!Set.of("On","Off","na").contains(filter[5])||!Set.of("On","Off","na").contains(filter[6]))throw new IOException("Unknown original filter/removal flag");
                String stem=mode+"/reads/"+base+"/"+fileName(names.get(id));String fasta=stem+".fasta";required.add(fasta);
                required.add(mode+"/alignments/"+base+"/"+fileName(names.get(id))+".blast");
                var actual=fasta(view,fasta);long readCount=actual.values().stream().mapToLong(Integer::longValue).sum();
                if(readCount!=after)throw new IOException("Original FASTA population differs from original filter count: "+fasta);
                var nativeResult=MaltExtractReadProjection.project(population.reads(),id,MaltExtractReadProjection.Options.complete(mode.equals("default")?MaltExtractReadProjection.Mode.DEFAULT:MaltExtractReadProjection.Mode.ANCIENT));
                var expected=pairs(nativeResult.reads());
                if(complete) {
                    if(before!=nativeResult.sequenceEligible()||after!=nativeResult.reads().size()||beforeMatches!=nativeResult.consideredMatches()||afterMatches!=nativeResult.retainedMatches()||!actual.equals(expected))throw new IOException("Complete original/native projection differs: "+sample+" / "+mode+" / "+id);
                }else for(var read:actual.entrySet())if(read.getValue()>expected.getOrDefault(read.getKey(),0))throw new IOException("Stock original FASTA contains a read outside the complete qualified candidates");
                boolean prefix=!complete&&stored>10000;
                if(prefix)warnings.add("FULL-POPULATION ANALYSIS SKIPPED: stock MaltExtract1.7 processes only the first10000 stored reads for "+sample+" / taxon"+id+" of"+stored+"; its downSampling? flag may still be Off. Retain stock outputs. Required action: explicitly select a complete profile and run a NEW output directory if full-population extraction is intended; do not silently switch methods.");
                var row=new LinkedHashMap<String,Object>();row.put("sample",sample);row.put("mode",mode);row.put("taxon",id);row.put("stored_assigned",stored);row.put("examined_eligible_reads",before);row.put("filtered_reads",after);row.put("considered_matches",beforeMatches);row.put("retained_matches",afterMatches);row.put("original_turned_on",filter[5]);row.put("original_downsampling",filter[6]);row.put("stock_prefix_sampling",prefix);row.put("complete_native_projection_equal",complete);summaries.add(row);
                row.put("damage_length_eligible_matches",damageMatches);filtered.put(sample+"/"+mode+"/"+id,after);
            }
        }
        String header="Node";for(String sample:samples.keySet())header+="\t"+sample+".rma6";
        var summaryLabels=new TreeSet<String>();for(int id:ids)summaryLabels.add(summaryName(names.get(id)));
        for(String mode:modes) {
            String path=mode+"/RunSummary.txt";required.add(path);Table summary=table(view,path,header,summaryLabels,true);
            for(int id:ids) {
                String[] fields=one(summary,summaryName(names.get(id)),samples.size()+1,path);int column=1;
                for(String sample:samples.keySet())if(count(fields[column++])!=filtered.getOrDefault(sample+"/"+mode+"/"+id,0L))throw new IOException("Original RunSummary contradicts complete per-sample output");
            }
            path=mode+"/TotalCount.txt";required.add(path);Table total=table(view,path,header,Set.of("Total_Count"),true);String[] fields=one(total,"Total_Count",samples.size()+1,path);int column=1;
            for(var population:samples.values())if(count(fields[column++])!=population.report().reads())throw new IOException("Original TotalCount contradicts complete RMA stored population");
        }
        if(!view.members().containsAll(required))throw new IOException("Missing complete original output members: "+difference(required,view.members()));
        if(nonfinite>0)warnings.add("ORIGINAL NONFINITE CELLS: "+nonfinite+" NaN/Inf cells retained literally. No imputation, zero replacement or biological absence claim is made. Inspect the original tables before downstream interpretation.");
        var report=new LinkedHashMap<String,Object>();report.put("groups",summaries);report.put("tables",tables);report.put("nonfinite_cells",nonfinite);report.put("warnings",warnings);report.put("required_outputs",required);report.put("uninterpreted_retained_outputs",difference(view.members(),required));report.put("original_statistics_reimplemented",false);return report;
    }
    private static Set<String> difference(Set<String> a,Set<String> b) {var result=new TreeSet<>(a);result.removeAll(b);return result;}
}
