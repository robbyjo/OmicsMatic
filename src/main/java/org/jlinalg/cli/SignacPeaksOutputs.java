/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.SignacPeakOutput;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import static org.jlinalg.cli.SignacPeaksContracts.value;

/** Source-output transport plus native geometry/reduction; MACS statistics are not refitted. */
final class SignacPeaksOutputs {
    private SignacPeaksOutputs() { }
    static List<String> publish(Path root,Map<String,Object> r,SignacPeaksInputs.Prepared prepared,String home,String stage,boolean compare)throws IOException {
        List<String> warnings=new ArrayList<>();List<SignacPeakOutput.Group> groups=new ArrayList<>();long rows=0,warningCount=0;int combinedRows=-1;
        try(var table=new Table(root.resolve("peaks.tsv"),compare)) {
            table.row("group","chromosome","start","end","strand","name","score","fold_change","negative_log10_pvalue","negative_log10_qvalue","relative_summit_position");
            for(var job:prepared.jobs()) {
                List<String> inputs=job.inputs().stream().map(p->stage+"/"+p).toList();var command=SignacPeaksRuntime.command(home,stage,r,inputs,job.id(),stage+"/macs/"+job.id());process(root.resolve("process/"+job.id()+"/process.json"),r,command,List.of(stage+"/prepared",stage+"/macs"));
                Path output=root.resolve("macs/"+job.id());boolean broad=Boolean.TRUE.equals(r.get("broad"));for(String suffix:broad?List.of("_peaks.broadPeak","_peaks.gappedPeak","_peaks.xls"):List.of("_peaks.narrowPeak","_summits.bed","_peaks.xls"))if(!Files.isRegularFile(output.resolve(job.id()+suffix),LinkOption.NOFOLLOW_LINKS))throw new IOException("Original MACS omitted output: "+suffix);
                var peaks=SignacPeakOutput.read(output.resolve(job.id()+"_peaks."+(broad?"broadPeak":"narrowPeak")),broad,(int)Math.max(1,value(r,"maximum_peak_rows")-rows),value(r,"maximum_output_bytes"),()->Thread.currentThread().isInterrupted());rows+=peaks.size();if(rows>value(r,"maximum_peak_rows"))throw new IOException("Complete peak row capacity exceeded");
                try(var regions=new Table(root.resolve(r.get("mode").equals("POOLED")?"regions.tsv":"regions-"+job.id()+".tsv"),compare)){regions.row("chromosome","start","end");var coordinates=new HashSet<String>();boolean duplicated=false;
                    for(var p:peaks){String prefix=job.id()+"_peak_";if(!p.name().startsWith(prefix))throw new IOException("Original MACS peak name differs from controlled transport prefix");String restored=job.sourceName()+"_peak_"+p.name().substring(prefix.length());table.row(job.sourceName(),p.chromosome(),Integer.toString(p.start()),Integer.toString(p.end()),p.strand(),restored,p.score(),p.foldChange(),p.negativeLog10P(),p.negativeLog10Q(),p.relativeSummit()==null?"":p.relativeSummit().toString());regions.row(p.chromosome(),Integer.toString(p.start()),Integer.toString(p.end()));if(!coordinates.add(p.chromosome()+":"+p.start()+":"+p.end()))duplicated=true;}
                    if(duplicated)warnings.add("SOURCE_DUPLICATE_PEAK_COORDINATES ["+job.sourceName()+"]: distinct source peak/summit rows share coordinates. They remain retained; downstream feature quantification can refuse ambiguous duplicate region labels. Choose a declared feature definition before quantifying; no automatic deletion/merge occurred.");}
                if(!peaks.isEmpty())groups.add(new SignacPeakOutput.Group(job.sourceName(),peaks));else warnings.add("SOURCE_EMPTY_PEAK_TABLE ["+job.sourceName()+"]: original MACS completed but returned no peaks. This is distinct from a failed source call; retain its exact settings and logs.");
                try(var log=SeuratDifferentialInputs.reader(root.resolve("process/"+job.id()+"/process.stderr"))){String line;while((line=SeuratDifferentialInputs.line(log))!=null){String lower=line.toLowerCase(Locale.ROOT);if(lower.contains("warning")||lower.contains("error")){if(warningCount<1000)warnings.add("ORIGINAL_MACS_WARNING ["+job.sourceName()+"]: "+line);warningCount++;}}}
            }
        }
        List<String> sequenceLevels=List.of();
        if(r.get("mode").equals("GROUPED")&&Boolean.TRUE.equals(r.get("combine_peaks"))) {
            final SignacPeakOutput.Combined result;try{result=SignacPeakOutput.combine(groups,(int)value(r,"maximum_peak_rows"),()->Thread.currentThread().isInterrupted());}catch(IllegalArgumentException e){throw new IOException("Original grouped peak reduction failed; no combined result admitted",e);}
            sequenceLevels=result.sequenceLevels();combinedRows=result.peaks().size();for(String warning:result.warnings())warnings.add("ORIGINAL_SEQUENCE_COMBINATION_WARNING: "+warning);
            try(var table=new Table(root.resolve("combined-peaks.tsv"),compare)){table.row("chromosome","start","end","strand","peak_called_in","groups_json");for(var p:result.peaks())table.row(p.chromosome(),Integer.toString(p.start()),Integer.toString(p.end()),p.strand(),String.join(",",p.groups()),RsemJson.json(p.groups()));}
            try(var regions=new Table(root.resolve("regions.tsv"),compare)){regions.row("chromosome","start","end");for(var p:result.peaks())regions.row(p.chromosome(),Integer.toString(p.start()),Integer.toString(p.end()));}
            warnings.add("SOURCE_COMBINED_METADATA: original source reduction discards per-peak scores and p/q statistics. Combined intervals retain source-normalized membership, not a newly tested peak probability.");
        }
        if(warningCount>1000)warnings.add("SOURCE_LOG_WARNING_COUNT: "+warningCount+" warning/error lines exist; first1000 are shown here. Read complete retained process stderr logs for every message.");
        warnings.add("VERIFY_SCOPE: captured requests, source/runtime bindings, native preparation, peak transport/geometry and grouped reduction are audited. MACS statistical tests are not independently refitted. Raw spreadsheets/logs remain forensic evidence; coordinated fabricated source values with recomputed copies/hashes can pass. Hashes do not authenticate execution or biology.");
        Map<String,Object> diagnostics=Map.of("source_peak_rows",rows,"combined_peak_rows",combinedRows,"sequence_levels",sequenceLevels,"requested_jobs",prepared.jobs().size(),"source_log_warning_lines",warningCount,"warnings",warnings,"native_MACS_statistical_replay",false,"historical_whole_environment",false,"host_DLL_authentication",false);
        if(compare){if(!same(diagnostics,read(root.resolve("diagnostics.json"),64_000_000)))throw new IOException("Native peak diagnostics differ");}else json(root.resolve("diagnostics.json"),diagnostics);return warnings;
    }
    static void process(Path path,Map<String,Object> r,List<String> command,List<String> accounted)throws IOException {
        var record=read(path,16_000_000);List<String> invoked=new ArrayList<>(List.of("/usr/bin/setsid","/usr/bin/prlimit","--as="+value(r,"maximum_memory_bytes"),"--cpu="+value(r,"maximum_cpu_seconds"),"--fsize="+value(r,"maximum_output_bytes"),"--nofile=256","--core=0","--"));invoked.addAll(command);
        if(!"completed-process-only".equals(record.get("status"))||integer(record,"exit_code",-1,0,0)!=0||!list(record.get("command"),"command").equals(invoked)||!same(list(record.get("accepted_process_exit_codes"),"accepted exits"),List.of(0))||!list(record.get("owned_live_after_cleanup"),"owned live").isEmpty()||!list(record.get("accounted_roots"),"accounted roots").equals(accounted))throw new IOException("Original peak process/capacity/binding did not complete");
        if(!same(SimpleJson.parse(RsemJson.json(SignacPeaksRuntime.limits(r))),record.get("limits")))throw new IOException("Recorded source process capacities differ");
    }
    static Set<String> allowedFiles(Map<String,Object> r,SignacPeaksInputs.Prepared prepared)throws IOException {
        Set<String> names=new TreeSet<>(List.of("source-request.json","request.json","effective-request.json","prepared-summary.json","runtime-binding.json","diagnostics.json","manifest.json","manifest.yaml","analysis-status.json","WARNINGS.md","COMPLETED.json","capture-files.json","peaks.tsv"));
        var captured=list(r.get("sources"),"sources");for(Object raw:captured){var s=object(raw,"source");names.add(text(s,"fragments"));if(s.containsKey("map"))names.add(text(s,"map"));}if(r.containsKey("cell_groups"))names.add(text(r,"cell_groups"));
        names.add("source/SOURCE-PROFILES.json");names.add("source/runtime-probe.py");for(String n:object(SignacPeaksRuntime.profile().get("files"),"source files").keySet())names.add("source/"+n);
        addProcess(names,"runtime-probe/");for(var job:prepared.jobs()){
            names.add(r.get("mode").equals("POOLED")?"regions.tsv":"regions-"+job.id()+".tsv");
            if(r.get("mode").equals("GROUPED"))names.add("prepared/"+job.id()+".bed");addProcess(names,"process/"+job.id()+"/");String prefix="macs/"+job.id()+"/"+job.id();names.add(prefix+"_peaks.xls");
            if(Boolean.TRUE.equals(r.get("broad"))){names.add(prefix+"_peaks.broadPeak");names.add(prefix+"_peaks.gappedPeak");}else{names.add(prefix+"_peaks.narrowPeak");names.add(prefix+"_summits.bed");}
            if(Boolean.TRUE.equals(r.get("bedgraph"))){names.add(prefix+"_treat_pileup.bdg");names.add(prefix+"_control_lambda.bdg");}
        }
        if(r.get("mode").equals("GROUPED")&&Boolean.TRUE.equals(r.get("combine_peaks"))){names.add("combined-peaks.tsv");names.add("regions.tsv");}return names;
    }
    private static void addProcess(Set<String> names,String prefix){for(String name:List.of("launch.json","process.json","process.stdout","process.stderr","WARNINGS.md"))names.add(prefix+name);}
    private static final class Table implements AutoCloseable {
        private final BufferedReader input;private final BufferedWriter output;
        Table(Path path,boolean compare)throws IOException{input=compare?SeuratDifferentialInputs.reader(path):null;output=compare?null:Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
        void row(String...values)throws IOException {SignacPeaksInputs.check();var text=new StringWriter();FollowupSupport.row(text,values);String line=text.toString();if(input==null)output.write(line);else if(!line.substring(0,line.length()-1).equals(SeuratDifferentialInputs.line(input)))throw new IOException("Published peak table differs from native source transport/reduction");}
        public void close()throws IOException{if(input!=null)try{if(SeuratDifferentialInputs.line(input)!=null)throw new IOException("Extra published peak rows");}finally{input.close();}else output.close();}
    }
}
