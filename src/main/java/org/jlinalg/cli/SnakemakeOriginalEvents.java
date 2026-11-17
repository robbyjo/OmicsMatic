/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;

/** Independent Java audit of complete original-controller job events, not log-text matching. */
final class SnakemakeOriginalEvents {
    record Job(long id,String rule,List<String> inputs,List<String> outputs,boolean checkpoint,int informationRecords,int errorRecords,int finishes) {
        Job { inputs=List.copyOf(inputs);outputs=List.copyOf(outputs); }
    }
    record Diagnostic(String stream,int sequence,long pid,Map<?,?> record) { }
    record Result(long controllerPid,String runId,int streams,int sourceRecords,int startedBatches,int finalDone,int finalTotal,int errorDiagnostics,Map<Long,Job> jobs,List<Diagnostic> diagnostics) {
        Result { jobs=Map.copyOf(jobs);diagnostics=List.copyOf(diagnostics); }
    }
    private SnakemakeOriginalEvents() { }
    static Result audit(Path directory,Map<?,?> receipt,int maxRows,int maxLineBytes,long maxBytes,boolean requireJobs,Runnable check) throws IOException {
        if(!"ORIGINAL_CLI_RETURNED_NOT_ANALYSIS_ADMISSION".equals(receipt.get("status"))||integer(receipt.get("schema_version"),1,1)!=1||integer(receipt.get("exit_code"),0,0)!=0||!list(receipt.get("collector_errors")).isEmpty()||receipt.get("failure")!=null)throw new IOException("Original CLI did not finish successfully with complete event capture");
        long pid=integer(receipt.get("driver_pid"),1,Long.MAX_VALUE);String run=text(receipt.get("run_id"));
        if(!run.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")||!stringMap(receipt.get("helpers_before")).equals(SnakemakeOriginalRuntime.helperPins())||!stringMap(receipt.get("helpers_after")).equals(SnakemakeOriginalRuntime.helperPins()))throw new IOException("Driver run/helper identities differ");
        Map<?,?> declared=object(receipt.get("collector_streams"));var files=new TreeMap<String,Path>();
        try(var paths=Files.list(directory)) { for(Path path:paths.toList()) { String name=path.getFileName().toString();if(!name.matches("events-[1-9][0-9]*\\.jsonl")||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unexpected/error event stream member: "+name);files.put(name,path); } }
        if(!files.keySet().equals(declared.keySet())||!files.containsKey("events-"+pid+".jsonl"))throw new IOException("Complete event-stream census/controller stream differs");
        long totalBytes=0;int totalRows=0,sourceRows=0,workflows=0,batches=0,done=0,total=0,errors=0,startedGroups=0,reportedGroups=0,pendingGroupMembers=0;var jobs=new LinkedHashMap<Long,Job>();var scheduled=new HashSet<Long>();var grouped=new HashSet<Long>();var completed=new HashSet<Long>();var diagnostics=new ArrayList<Diagnostic>();
        for(var entry:files.entrySet()) {
            Path path=entry.getValue();Pin expected=fingerprint(declared.get(entry.getKey()),maxBytes);Pin before=pin(path,maxBytes,check);if(!before.equals(expected))throw new IOException("Event stream bytes differ from driver receipt");
            totalBytes=Math.addExact(totalBytes,before.bytes());if(totalBytes>maxBytes)throw new IOException("Complete event streams exceed log_bytes");
            long streamPid=Long.parseLong(entry.getKey().substring(7,entry.getKey().length()-6));int sequence=0;boolean closed=false;
            try(BufferedReader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
                String line;
                while((line=readLine(reader,maxLineBytes,check))!=null) {
                    check.run();if(++totalRows>maxRows||closed)throw new IOException("Event row capacity exceeded or records follow collector close");
                    Map<?,?> item=object(SnakemakeOriginalParameters.parse(line,maxLineBytes,1_000_000));
                    if(integer(item.get("sequence"),1,Integer.MAX_VALUE)!=++sequence||integer(item.get("pid"),1,Long.MAX_VALUE)!=streamPid||!run.equals(item.get("run_id")))throw new IOException("Event sequence/process/run identity differs");
                    String kind=text(item.get("record_type"));
                    if(sequence==1) { keys(item,"record_type","collector_version","pid","run_id","sequence");if(!kind.equals("collector_opened")||integer(item.get("collector_version"),1,1)!=1)throw new IOException("Collector opening declaration differs");continue; }
                    if(kind.equals("collector_closed")) { keys(item,"record_type","pid","run_id","collector_failed","sequence");if(!Boolean.FALSE.equals(item.get("collector_failed")))throw new IOException("Collector failed; no event result admitted");closed=true;continue; }
                    keys(item,"record_type","pid","run_id","record","sequence");if(!kind.equals("original_log_record"))throw new IOException("Unknown event envelope");
                    sourceRows++;Map<?,?> record=object(item.get("record"));if(integer(record.get("process"),1,Long.MAX_VALUE)!=streamPid)throw new IOException("Original record process differs from collector process");
                    if(integer(record.get("levelno"),0,Integer.MAX_VALUE)>=30)diagnostics.add(new Diagnostic(entry.getKey(),sequence,streamPid,record));
                    if(streamPid!=pid)continue;Object event=record.get("event");if(event==null)continue;
                    switch(text(event)) {
                        case "workflow_started" -> { workflows++;if(!"9.27.1".equals(record.get("snakemake_version")))throw new IOException("Original started event reports a different edition"); }
                        case "job_started" -> {
                            batches++;
                            for(Object value:list(record.get("jobs"))) {
                                if(value instanceof String group) { if(!group.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IOException("Unknown original scheduled group identity");startedGroups++; }
                                else scheduled.add(integer(value,0,Long.MAX_VALUE));
                            }
                        }
                        case "group_info" -> {
                            if(pendingGroupMembers!=0)throw new IOException("Source group member information was truncated");
                            List<?> members=list(record.get("jobs"));if(members.isEmpty())throw new IOException("Empty source job group");
                            for(Object value:members) {Map<?,?> member=object(value);keys(member,"recorded_type","repr");if(!"snakemake.jobs.Job".equals(member.get("recorded_type")))throw new IOException("Unknown grouped-job source representation");text(member.get("repr"));}
                            pendingGroupMembers=members.size();reportedGroups++;
                        }
                        case "job_info" -> {
                            long id=integer(record.get("jobid"),0,Long.MAX_VALUE);String rule=text(record.get("rule_name"));List<String> inputs=strings(record.get("input")),outputs=strings(record.get("output"));Job previous=jobs.get(id);
                            if(rule.isEmpty())throw new IOException("Empty source rule name");
                            if(Boolean.TRUE.equals(record.get("indent"))) {if(pendingGroupMembers<1)throw new IOException("Grouped source job has no complete group declaration");pendingGroupMembers--;grouped.add(id);}
                            else if(pendingGroupMembers!=0)throw new IOException("Source group member list was interrupted");
                            jobs.put(id,new Job(id,rule,inputs,outputs,Boolean.TRUE.equals(record.get("is_checkpoint")),previous==null?1:previous.informationRecords()+1,previous==null?0:previous.errorRecords(),previous==null?0:previous.finishes()));
                        }
                        case "job_error" -> { errors++;long id=integer(record.get("jobid"),0,Long.MAX_VALUE);Job previous=jobs.get(id);if(previous==null)throw new IOException("Source error refers to an unreported job");jobs.put(id,new Job(id,previous.rule(),previous.inputs(),previous.outputs(),previous.checkpoint(),previous.informationRecords(),previous.errorRecords()+1,previous.finishes())); }
                        case "job_finished" -> { long id=integer(record.get("job_id"),0,Long.MAX_VALUE);Job previous=jobs.get(id);if(previous==null||!scheduled.contains(id)&&!grouped.contains(id))throw new IOException("Source finished an unreported/unscheduled job");completed.add(id);jobs.put(id,new Job(id,previous.rule(),previous.inputs(),previous.outputs(),previous.checkpoint(),previous.informationRecords(),previous.errorRecords(),previous.finishes()+1)); }
                        case "progress" -> { int current=(int)integer(record.get("done"),0,Integer.MAX_VALUE),target=(int)integer(record.get("total"),0,Integer.MAX_VALUE);if(current<done||current>target)throw new IOException("Source progress is nonmonotone or exceeds its total");done=current;total=target; }
                        case "error","group_error" -> errors++;
                        default -> { /* All other original records remain in the complete captured stream. */ }
                    }
                }
            }
            if(!closed||sequence<2||!pin(path,maxBytes,check).equals(before))throw new IOException("Truncated/unclosed/changed collector stream");
        }
        scheduled.addAll(grouped);
        if(workflows!=1||pendingGroupMembers!=0||startedGroups!=reportedGroups||!completed.equals(jobs.keySet())||!scheduled.equals(jobs.keySet())||done!=total||done!=completed.size()||requireJobs&&jobs.isEmpty())throw new IOException("Original controller did not completely finish its reported scheduled job family; no accepted workflow result");
        return new Result(pid,run,files.size(),sourceRows,batches,done,total,errors,jobs,diagnostics);
    }
    private static Map<String,String> stringMap(Object value) throws IOException {var result=new TreeMap<String,String>();for(var entry:object(value).entrySet())result.put(text(entry.getKey()),text(entry.getValue()));return result;}
    private static String readLine(BufferedReader reader,int maximum,Runnable check) throws IOException {
        var text=new StringBuilder();int value;
        while((value=reader.read())!=-1&&value!='\n') { if((text.length()&1023)==0)check.run();if(text.length()>=maximum)throw new IOException("Source event line exceeds metadata_bytes; no truncated event admitted");text.append((char)value); }
        if(value==-1) { if(text.isEmpty())return null;throw new IOException("Unterminated source event line"); }
        return text.toString();
    }
}
