/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Complete retained populations and source-report audit; upstream algorithm selection is not reimplemented. */
final class FastpOutputAudit {
    private FastpOutputAudit() { }
    record Data(Path processing,String version,Path first,Path second,FastpFastq.Encoding encoding,FastpFastq.Alphabet alphabet,
                FastpFastq.Plus plus,FastpFastq.Pairing pairing,FastpParameters.Options options,FastpFastq.Limits fastq) { }
    record Limits(FastpMetrics.Limits statistics,FastpSort.Limits sorting,FastpJoin.Limits matching,int jsonBytes) {
        Limits {java.util.Objects.requireNonNull(statistics);java.util.Objects.requireNonNull(sorting);java.util.Objects.requireNonNull(matching);if(jsonBytes<65536||jsonBytes>67_108_864)throw new IllegalArgumentException("Invalid explicit fastp JSON capacity");}
    }
    record Result(String status,long independentlyRecomputedReportFields,long additionallyConstrainedReportFields,FastpJoin.Counts conservation,
                  Map<String,FastpMetrics.Totals> statistics,List<String> warnings,String ledger) {
        Result {statistics=Map.copyOf(statistics);warnings=List.copyOf(warnings);}
    }
    static Result verify(Data data,Path newAudit,Limits limits,FastpProcess.Check disk,Runnable check)throws IOException {
        Path root=data.processing().toAbsolutePath().normalize(),audit=newAudit.toAbsolutePath().normalize();
        if(!root.toRealPath().equals(root)||!audit.getParent().toRealPath().equals(audit.getParent()))throw new IOException("fastp output audit requires canonical source and NEW audit paths");
        if(audit.equals(root)||root.startsWith(audit))throw new IOException("fastp output audit cannot replace its raw source tree");
        Files.createDirectory(audit);check.run();if(Files.exists(root.resolve("FAILURE.json"),LinkOption.NOFOLLOW_LINKS))throw new IOException("A failed fastp raw stage cannot be promoted by an output audit");
        Map<?,?> request=FastpRuntime.object(FastpFiles.json(root.resolve("request.json"),limits.jsonBytes()));
        if(!SimpleJson.parse(FastpJson.json(data.options())).equals(request.get("options"))||!data.encoding().name().equals(request.get("encoding"))||!data.alphabet().name().equals(request.get("alphabet"))||!data.plus().name().equals(request.get("plus_policy"))||!data.pairing().name().equals(request.get("pairing_policy")))throw new IOException("fastp output audit choices differ from the original request");
        if(!SimpleJson.parse(FastpJson.json(data.fastq())).equals(FastpRuntime.object(request.get("limits")).get("fastq")))throw new IOException("fastp output audit input capacities differ from the complete original request");
        List<?> captures=FastpMetrics.array(FastpFiles.json(root.resolve("input-captures.json"),limits.jsonBytes()));boolean paired=data.second()!=null;var stable=new java.util.TreeMap<Path,FastpFiles.Identity>();
        if(captures.size()!=(paired?2:1))throw new IOException("fastp captured file population differs");
        for(int i=0;i<captures.size();i++){
            Map<?,?> capture=FastpRuntime.object(captures.get(i));String member=FastpFiles.member(FastpRuntime.text(capture.get("snapshot")));Path source=root.resolve(member);if(!source.equals(i==0?data.first():data.second()))throw new IOException("fastp audit read source is not its complete captured file");
            var expected=FastpRuntime.fingerprint(FastpRuntime.object(FastpRuntime.object(capture.get("identity")).get("pin")));var identity=FastpFiles.identity(source,data.fastq().encodedBytes(),check);if(!identity.pin().equals(expected))throw new IOException("fastp captured original bytes changed before output audit");stable.put(source,identity);
        }
        Map<?,?> stage=FastpRuntime.object(FastpFiles.json(root.resolve("stage.json"),65536));if(!"ORIGINAL_PROCESSING_FINISHED_OUTPUT_AUDIT_PENDING".equals(stage.get("status"))||!Boolean.FALSE.equals(stage.get("scientific_output_admitted")))throw new IOException("fastp raw stage is incomplete");
        String profile="linux-x86_64-fastp-"+data.version();if(!profile.equals(request.get("profile")))throw new IOException("fastp requested edition differs");
        Map<?,?> locked=FastpRuntime.locked(profile),runtime=FastpRuntime.object(FastpFiles.json(root.resolve("runtime-binding.json"),limits.jsonBytes()));if(!profile.equals(runtime.get("profile")))throw new IOException("fastp runtime profile differs");
        Map<?,?> recordedProgram=FastpRuntime.object(FastpRuntime.object(runtime.get("payload")).get("fastp"));if(!FastpRuntime.fingerprint(FastpRuntime.object(recordedProgram.get("pin"))).equals(FastpRuntime.fingerprint(FastpRuntime.object(locked.get("binary")))))throw new IOException("fastp recorded original program differs from exact locked bytes");
        Path original=root.resolve("original");Map<?,?> process=FastpRuntime.object(FastpFiles.json(original.resolve("process.json"),limits.jsonBytes()));
        long outputBytes=FastpRuntime.integer(FastpRuntime.object(process.get("limits")).get("fileBytes"),1,1_000_000_000_000L);
        var outputLimits=new FastpFastq.Limits(outputBytes,outputBytes,Math.min(1_000_000_000L,data.fastq().reads()*(paired?2:1)),outputBytes,data.fastq().readCharacters(),Math.min(1_000_000,data.fastq().headerCharacters()+32));
        if(!"completed-process-only".equals(process.get("status"))||FastpRuntime.integer(process.get("exit_code"),0,0)!=0||!List.of().equals(process.get("owned_live_after_cleanup")))throw new IOException("fastp process status/cleanup does not admit output auditing");
        Map<?,?> report=FastpRuntime.object(FastpFiles.json(original.resolve("fastp.json"),limits.jsonBytes()));
        verifyCommand(data,request,runtime,process,report,captures);
        Set<String> outputs=new HashSet<>(Set.of("process.stdout","process.stderr","launch.json","process.json","WARNINGS.md","fastp.json","fastp.html","read1.fastq","failed.fastq"));if(paired)outputs.add("read2.fastq");if(paired&&data.options().storeUnpaired()){outputs.add("unpaired1.fastq");outputs.add("unpaired2.fastq");}
        var found=new HashSet<String>();try(var paths=Files.newDirectoryStream(original)){for(Path path:paths){if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!found.add(path.getFileName().toString()))throw new IOException("Linked/nonregular fastp original output member");stable.put(path,FastpFiles.identity(path,outputBytes,check));}}
        if(!found.equals(outputs))throw new IOException("Incomplete/extra original fastp output-file population");
        var before1=new FastpMetrics.Model(limits.statistics());var before2=paired?new FastpMetrics.Model(limits.statistics()):null;
        var after1=new FastpMetrics.Model(limits.statistics());var after2=paired?new FastpMetrics.Model(limits.statistics()):null;
        Path index=audit.resolve("complete-index.tsv");long indexed=0;
        try(var out=FastpSort.writer(index);var first=new FastpFastq.Reader(data.first(),data.fastq(),data.encoding(),data.alphabet(),data.plus(),false,check);var second=paired?new FastpFastq.Reader(data.second(),data.fastq(),data.encoding(),data.alphabet(),data.plus(),false,check):null){
            for(FastpFastq.Read a=first.next();a!=null;a=first.next()){
                FastpFastq.Read b=second==null?null:second.next();if(second!=null&&b==null)throw new IOException("fastp complete captured mate2 ends early");if(b!=null)FastpFastq.checkPair(a,b,data.pairing());before1.add(a,data.encoding(),check);if(b!=null)before2.add(b,data.encoding(),check);
                if(++indexed>limits.sorting().records())throw new IOException("Complete fastp index exceeds explicit record capacity");write(out,FastpJoin.input(a,b,data.encoding(),data.pairing()),limits.sorting());if((indexed&511)==0){out.flush();disk.verify();check.run();}
            }
            if(second!=null&&second.next()!=null)throw new IOException("fastp captured mate2 has extra records");first.completeStats();if(second!=null)second.completeStats();
            if(paired)indexed+=addPairedOutput(original,after1,after2,data,outputLimits,limits,limits.sorting().records()-indexed,out,disk,check);
            else indexed+=addOutput(original.resolve("read1.fastq"),"MAIN1",after1,data,outputLimits,limits,limits.sorting().records()-indexed,out,disk,check);
            indexed+=addOutput(original.resolve("failed.fastq"),"FAILED",null,data,outputLimits,limits,limits.sorting().records()-indexed,out,disk,check);
            if(paired&&data.options().storeUnpaired()){indexed+=addOutput(original.resolve("unpaired1.fastq"),"UNPAIRED1",null,data,outputLimits,limits,limits.sorting().records()-indexed,out,disk,check);indexed+=addOutput(original.resolve("unpaired2.fastq"),"UNPAIRED2",null,data,outputLimits,limits,limits.sorting().records()-indexed,out,disk,check);}
        }
        if(before1.totals().reads()==0)throw new FastpRuntime.Unavailable("fastp complete input is empty;skip dependent analysis");
        if(paired&&after1.totals().reads()!=after2.totals().reads())throw new IOException("fastp paired passing output counts differ");
        long fields=before1.verifyDetail(FastpRuntime.object(report.get("read1_before_filtering")),data.version())+after1.verifyDetail(FastpRuntime.object(report.get("read1_after_filtering")),data.version());
        if(paired)fields+=before2.verifyDetail(FastpRuntime.object(report.get("read2_before_filtering")),data.version())+after2.verifyDetail(FastpRuntime.object(report.get("read2_after_filtering")),data.version());
        Map<?,?> summary=FastpRuntime.object(report.get("summary"));FastpMetrics.exactKeys(summary,Set.of("fastp_version","sequencing","before_filtering","after_filtering"),"summary sections");if(!data.version().equals(summary.get("fastp_version")))throw new IOException("fastp report edition differs");
        fields+=FastpMetrics.verifySummary(FastpRuntime.object(summary.get("before_filtering")),before1,before2)+FastpMetrics.verifySummary(FastpRuntime.object(summary.get("after_filtering")),after1,after2);
        var sorted=FastpSort.sort(index,audit.resolve("sort"),limits.sorting(),disk,check);if(sorted.records()!=indexed)throw new IOException("Complete fastp index population changed during sorting");
        var joined=FastpJoin.reconcile(sorted.path(),audit.resolve("read-dispositions.tsv"),paired,data.options(),limits.matching(),limits.sorting().lineCharacters(),disk,check);
        long inputReads=Math.addExact(before1.totals().reads(),paired?before2.totals().reads():0);if(joined.inputReads()!=inputReads)throw new IOException("fastp complete read reconciliation lost input occurrences");
        var warnings=new ArrayList<String>();if(joined.ambiguousFamilies()>0)warnings.add("DUPLICATE_HEADER_AMBIGUITY: assignment is a feasible conservation witness;exact occurrence identities are not uniquely established. Review the complete disposition ledger.");
        long constrained=verifyFilters(report,data,joined,inputReads,warnings)+verifyAuxiliary(report,data,before1.totals(),paired?before2.totals():null,warnings);
        var statistics=new java.util.TreeMap<String,FastpMetrics.Totals>();statistics.put("read1_before",before1.totals());statistics.put("read1_after",after1.totals());if(paired){statistics.put("read2_before",before2.totals());statistics.put("read2_after",after2.totals());}
        warnings.add("AUDIT_SCOPE: complete captured populations,source filtering/disposition constraints,quality-cut/prefix/correction envelopes and full per-cycle/five-mer report statistics. Upstream adapter/overlap selection,poly-tail algorithms,Bloom simulation and biological accuracy are not independently reproduced.");
        for(var entry:stable.entrySet())if(!FastpFiles.identity(entry.getKey(),Math.max(outputBytes,data.fastq().encodedBytes()),check).equals(entry.getValue()))throw new IOException("fastp raw original input/output changed during complete output audit");
        FastpFiles.writeJson(audit.resolve("effective-output-fastq-limits.json"),outputLimits);
        var result=new Result("PASSED_COMPLETE_POPULATION_AND_SOURCE_REPORT_AUDIT",fields,constrained,joined,statistics,warnings,"read-dispositions.tsv");FastpFiles.writeJson(audit.resolve("output-audit.json"),result);disk.verify();check.run();return result;
    }
    private static long addPairedOutput(Path original,FastpMetrics.Model first,FastpMetrics.Model second,Data data,FastpFastq.Limits outputLimits,Limits limits,long remaining,BufferedWriter out,FastpProcess.Check disk,Runnable check)throws IOException {
        long count=0;
        try(var one=new FastpFastq.Reader(original.resolve("read1.fastq"),outputLimits,FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.LITERAL_ASCII,FastpFastq.Plus.SOURCE_IGNORED,true,check);var two=new FastpFastq.Reader(original.resolve("read2.fastq"),outputLimits,FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.LITERAL_ASCII,FastpFastq.Plus.SOURCE_IGNORED,true,check)){
            for(FastpFastq.Read a=one.next();a!=null;a=one.next()){
                var b=two.next();if(b==null)throw new IOException("fastp main mate2 output ends early");FastpFastq.checkPair(a,b,data.pairing());
                // Also enforce the shared identity required by complete occurrence matching.
                FastpJoin.input(a,b,FastpFastq.Encoding.PHRED33,data.pairing());
                if(count+2>remaining)throw new IOException("Complete fastp index exceeds explicit record capacity");count+=2;first.add(a,FastpFastq.Encoding.PHRED33,check);second.add(b,FastpFastq.Encoding.PHRED33,check);
                write(out,FastpJoin.output("MAIN1",a,data.pairing()),limits.sorting());write(out,FastpJoin.output("MAIN2",b,data.pairing()),limits.sorting());if((count&511)==0){out.flush();disk.verify();check.run();}
            }
            if(two.next()!=null)throw new IOException("fastp main mate2 output has extra records");if(one.completeStats().gzip()||two.completeStats().gzip())throw new IOException("Original fastp fixed .fastq outputs must be plain files");
        }return count;
    }
    private static long addOutput(Path path,String kind,FastpMetrics.Model model,Data data,FastpFastq.Limits outputLimits,Limits limits,long remaining,BufferedWriter out,FastpProcess.Check disk,Runnable check)throws IOException {
        long count=0;try(var in=new FastpFastq.Reader(path,outputLimits,FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.LITERAL_ASCII,FastpFastq.Plus.SOURCE_IGNORED,true,check)){
            for(FastpFastq.Read row=in.next();row!=null;row=in.next()){if(++count>remaining)throw new IOException("Complete fastp index exceeds explicit record capacity");if(model!=null)model.add(row,FastpFastq.Encoding.PHRED33,check);write(out,FastpJoin.output(kind,row,data.pairing()),limits.sorting());if((count&511)==0){out.flush();disk.verify();check.run();}}if(in.completeStats().gzip())throw new IOException("Original fastp fixed .fastq outputs must be plain files");
        }return count;
    }
    private static void write(BufferedWriter out,String line,FastpSort.Limits limits)throws IOException {if(line.length()>limits.lineCharacters()||256+8L*line.length()+32768>limits.memoryBytes()/4)throw new IOException("fastp complete index row exceeds explicit bounded sort/line memory");out.write(line);out.write('\n');}
    private static long verifyFilters(Map<?,?> report,Data data,FastpJoin.Counts counts,long inputReads,List<String> warnings)throws IOException {
        Map<?,?> filters=FastpRuntime.object(report.get("filtering_result"));var keys=new HashSet<>(Set.of("passed_filter_reads","low_quality_reads","too_many_N_reads","too_short_reads","too_long_reads"));if(data.options().filters().complexityEnabled())keys.add("low_complexity_reads");if(data.options().correction()){keys.add("corrected_reads");keys.add("corrected_bases");}FastpMetrics.exactKeys(filters,keys,"filtering-result");
        Map<Integer,String> labels=Map.of(0,"passed_filter_reads",12,"too_many_N_reads",16,"too_short_reads",17,"too_long_reads",20,"low_quality_reads",24,"low_complexity_reads");long total=0,gap=0;
        for(var entry:labels.entrySet()){
            long value=entry.getKey()==24&&!data.options().filters().complexityEnabled()?0:FastpRuntime.integer(filters.get(entry.getValue()),0,inputReads);long known=counts.representedSourceFilterCounts().get(entry.getKey());if(value<known||!data.options().duplication().deduplicate()&&(data.second()==null||entry.getKey()==0)&&value!=known)throw new IOException("fastp whole source filter family differs from retained dispositions: "+entry.getValue());if(data.second()!=null&&(value&1)!=0)throw new IOException("Original paired fastp filter counters must count pairs as two reads");total=Math.addExact(total,value);gap=Math.addExact(gap,value-known);
        }
        if(total!=inputReads||gap!=counts.unrepresentedReads()||data.second()==null&&!data.options().duplication().deduplicate()&&counts.unrepresentedReads()!=0)throw new IOException("fastp complete filtering/omitted-pair/duplicate conservation differs");
        if(data.second()!=null)warnings.add("PAIRED_FAILURE_OMISSION: the original source emits neither mate when both fail. Unrepresented pairs can therefore be failed pairs or duplicate suppression;these are not identified as duplicates from absence alone. Their source counter gaps and possible failure envelopes are constrained.");
        if(data.options().correction()){
            long correctedReads=FastpRuntime.integer(filters.get("corrected_reads"),0,inputReads),correctedBases=FastpRuntime.integer(filters.get("corrected_bases"),0,Long.MAX_VALUE);
            if(correctedReads<counts.visibleCorrectedReads()||correctedBases<counts.visibleCorrectedBases()||correctedReads>correctedBases)throw new IOException("fastp correction counters are inconsistent with visible corrections");
            warnings.add("CORRECTION_COUNTER_SCOPE: source counters also include corrected bases later trimmed,filtered or suppressed. Visible output changes are checked lower bounds,not a recomputed whole-source correction total.");
        }
        if(data.options().duplication().evaluate()){
            Map<?,?> duplicate=FastpRuntime.object(report.get("duplication"));FastpMetrics.exactKeys(duplicate,Set.of("rate"),"duplication");double rate=number(duplicate.get("rate"));if(rate<0||rate>1)throw new IOException("Invalid original fastp duplicate rate");
            if(data.options().duplication().deduplicate()){
                if(data.version().equals("0.22.0")&&data.options().threads()>1){warnings.add("SOURCE_0_22_CONCURRENCY: original duplicate buffers/counters are not atomic. Multithreaded rate/representative reproducibility is not certified;retain the source rate and consider explicitly selecting one thread.");}
                else if(data.second()==null)FastpMetrics.rounded(rate,(double)counts.unrepresentedReads()/inputReads,"duplication/rate");
                else {
                    long units=inputReads/2,missing=counts.unrepresentedReads()/2;double tolerance=rate==0?0:0.500001*Math.pow(10,Math.floor(Math.log10(rate))-5)+8*Math.ulp(rate);
                    long minimum=Math.max(0,(long)Math.ceil((rate-tolerance)*units)),maximum=Math.min(units,(long)Math.floor((rate+tolerance)*units));
                    long passingGap=FastpRuntime.integer(filters.get("passed_filter_reads"),0,inputReads)-counts.representedSourceFilterCounts().get(0);
                    if(minimum>maximum||minimum>missing||maximum*2<passingGap)throw new IOException("fastp source duplicate estimate cannot account for missing passing pairs within report precision");
                    warnings.add("PAIRED_DUPLICATE_RATE: source rate constrains suppressed pair count within printed precision;remaining absent pairs failed both mates. Their individual occurrence causes are not uniquely assigned.");
                }
            }
            warnings.add("BLOOM_SCOPE: observed suppression is not proof of exact identical-sequence duplication. The source Bloom estimate/suppression and its thread behavior remain upstream.");
        }else if(report.containsKey("duplication"))throw new IOException("Unexpected duplicate evaluation in a declared disabled run");
        return keys.size()+ (data.options().duplication().evaluate()?1:0);
    }
    private static long verifyAuxiliary(Map<?,?> report,Data data,FastpMetrics.Totals first,FastpMetrics.Totals second,List<String> warnings)throws IOException {
        boolean paired=second!=null;long reads=first.reads()+(paired?second.reads():0),bases=first.bases()+(paired?second.bases():0);var keys=new HashSet<>(Set.of("summary","filtering_result","read1_before_filtering","read1_after_filtering","command"));long checked=0;
        if(paired){keys.add("read2_before_filtering");keys.add("read2_after_filtering");keys.add("insert_size");Map<?,?> insert=FastpRuntime.object(report.get("insert_size"));FastpMetrics.exactKeys(insert,Set.of("peak","unknown","histogram"),"insert-size");List<?> histogram=FastpMetrics.array(insert.get("histogram"));if(histogram.size()!=512)throw new IOException("Incomplete fastp insert-size histogram");long sum=FastpRuntime.integer(insert.get("unknown"),0,first.reads()),max=-1;int peak=0;for(int i=0;i<512;i++){long value=FastpRuntime.integer(histogram.get(i),0,first.reads());sum=Math.addExact(sum,value);if(value>max){max=value;peak=i;}}if(sum>first.reads()||FastpRuntime.integer(insert.get("peak"),0,511)!=peak)throw new IOException("fastp sampled insert-size histogram population/peak differs");checked+=514;warnings.add("INSERT_SIZE_SCOPE: original histograms use thread0 and eligible paired reads;they are not a complete all-pair distribution.");}
        if(data.options().duplication().evaluate())keys.add("duplication");
        if(report.containsKey("adapter_cutting")){
            if(!data.options().adapters().enabled())throw new IOException("Unexpected disabled adapter report");keys.add("adapter_cutting");Map<?,?> adapter=FastpRuntime.object(report.get("adapter_cutting"));var fields=new HashSet<>(Set.of("adapter_trimmed_reads","adapter_trimmed_bases","read1_adapter_sequence","read1_adapter_counts"));if(paired){fields.add("read2_adapter_sequence");fields.add("read2_adapter_counts");}FastpMetrics.exactKeys(adapter,fields,"adapter");long trimmed=FastpRuntime.integer(adapter.get("adapter_trimmed_reads"),0,reads);FastpRuntime.integer(adapter.get("adapter_trimmed_bases"),0,bases);long mapTotal=0;
            for(int i=1;i<=(paired?2:1);i++){String supplied=i==1?data.options().adapters().read1():data.options().adapters().read2();String actual=FastpRuntime.text(adapter.get("read"+i+"_adapter_sequence"));if(!supplied.isEmpty()&&!supplied.equals("auto")&&!supplied.equals(actual))throw new IOException("fastp explicit adapter sequence differs");for(Object n:FastpRuntime.object(adapter.get("read"+i+"_adapter_counts")).values())mapTotal=Math.addExact(mapTotal,FastpRuntime.integer(n,0,reads));}if(mapTotal>trimmed)throw new IOException("fastp displayed adapter counts exceed trimmed population");checked+=fields.size();
        }else if(paired&&data.options().adapters().enabled()||!data.options().adapters().read1().isEmpty()&&!data.options().adapters().read1().equals("auto"))throw new IOException("Required fastp adapter report is absent");
        if(data.options().polyX()){
            keys.add("polyx_trimming");Map<?,?> poly=FastpRuntime.object(report.get("polyx_trimming"));FastpMetrics.exactKeys(poly,Set.of("total_polyx_trimmed_reads","total_polyx_trimmed_bases","polyx_trimmed_reads","polyx_trimmed_bases"),"poly-X");
            for(String unit:List.of("reads","bases")){Map<?,?> values=FastpRuntime.object(poly.get("polyx_trimmed_"+unit));FastpMetrics.exactKeys(values,Set.of("A","T","C","G"),"poly-X base bins");long sum=0;for(Object n:values.values())sum=Math.addExact(sum,FastpRuntime.integer(n,0,unit.equals("reads")?reads:bases));FastpMetrics.whole(poly,"total_polyx_trimmed_"+unit,sum);}checked+=12;
        }
        FastpMetrics.exactKeys(report,keys,"top-level report");return checked;
    }
    private static void verifyCommand(Data data,Map<?,?> request,Map<?,?> runtime,Map<?,?> process,Map<?,?> report,List<?> captures)throws IOException {
        List<?> actual=FastpMetrics.array(process.get("command"));int marker=actual.indexOf("--");if(marker!=7||actual.size()<marker+3)throw new IOException("fastp original process prefix differs");String first=argument(actual,"--in1");String member=FastpRuntime.text(FastpRuntime.object(captures.get(0)).get("snapshot"));String suffix="/"+member;if(!first.endsWith(suffix))throw new IOException("fastp original input is not its captured source member");String anchor=first.substring(0,first.length()-suffix.length());String second=captures.size()==2?anchor+"/"+FastpRuntime.text(FastpRuntime.object(captures.get(1)).get("snapshot")):null;
        Map<?,?> capacities=FastpRuntime.object(FastpRuntime.object(request.get("limits")).get("process"));if(!capacities.equals(process.get("limits")))throw new IOException("fastp process/request capacities differ");
        var expected=new ArrayList<String>(List.of("/usr/bin/setsid","/usr/bin/prlimit","--as="+capacities.get("addressSpaceBytes"),"--cpu="+capacities.get("cpuSeconds"),"--fsize="+capacities.get("fileBytes"),"--nofile="+capacities.get("openFiles"),"--core=0","--",FastpRuntime.text(runtime.get("home"))+"/fastp"));expected.addAll(FastpParameters.arguments(data.version(),captures.size()==2,data.encoding(),data.options(),first,second));if(!expected.equals(actual))throw new IOException("fastp original fixed command differs from complete typed choices");
        String display=String.join(" ",expected.subList(marker+1,expected.size()))+" ";if(!display.equals(report.get("command")))throw new IOException("fastp report command differs from original process arguments");
    }
    private static String argument(List<?> values,String name)throws IOException {int at=values.indexOf(name);if(at<0||at+1==values.size()||values.lastIndexOf(name)!=at)throw new IOException("Missing/duplicate typed fastp input argument");return FastpRuntime.text(values.get(at+1));}
    private static double number(Object value)throws IOException {if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IOException("Invalid finite fastp report number");return n.doubleValue();}
}
