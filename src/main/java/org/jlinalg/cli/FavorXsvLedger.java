/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Complete parent admission of the exact sequential offline FAVOR xsv process plan. */
final class FavorXsvLedger {
    private static final Set<String> REQUIRED=Set.of("schema_version","request_sha256","started_at","supervisor_pid",
            "arguments","status","planned_step","xsv","xsv_sha256","command","child_pid","child_exit_code",
            "exit_code","finished_at","owned_child_alive");
    record Limits(int eventBytes,long totalBytes,int events) {
        Limits {if(eventBytes<1||totalBytes<1||events<1||events>10000)throw new IllegalArgumentException("Invalid complete FAVOR event-ledger capacity");}
    }
    record Event(int step,String filename,String sha256,long bytes,long supervisorPid,long childPid,
            Instant started,Instant finished,List<String> arguments) {
        Event {arguments=List.copyOf(arguments);}
    }
    record Ledger(Path directory,List<Event> events,long bytes,String sha256) {
        Ledger {events=List.copyOf(events);}
        void verify(Runnable check)throws IOException {
            var expected=new HashSet<String>();for(var e:events){check.run();expected.add(e.filename());Path file=directory.resolve(e.filename());regular(file);if(Files.size(file)!=e.bytes()||!FollowupSupport.hash(file).equals(e.sha256()))throw new IOException("FAVOR terminal event changed after parent admission: "+e.filename());}
            if(!filenames(directory,events.size(),check).equals(expected))throw new IOException("FAVOR terminal event population changed after parent admission");
        }
    }
    private FavorXsvLedger() { }

    static Ledger read(Path supplied,String requestSha256,String xsv,int chromosome,int shards,
            Instant parentStarted,Instant parentFinished,Limits limits,Runnable check)throws IOException {
        check.run();if(requestSha256==null||!requestSha256.matches("[a-f0-9]{64}")||xsv==null||!xsv.matches("/[A-Za-z0-9._/-]+")||xsv.contains("//")||List.of(xsv.substring(1).split("/",-1)).stream().anyMatch(s->s.isEmpty()||s.equals(".")||s.equals("..")))throw new IOException("Invalid exact parent FAVOR request/executable binding");
        if(parentStarted==null||parentFinished==null||parentFinished.isBefore(parentStarted))throw new IOException("Invalid actual parent execution time window");
        List<List<String>> plan=FavorXsvSupervisor.plan(chromosome,shards);if(plan.size()>limits.events())throw new IOException("Complete expected FAVOR event count exceeds its capacity");
        Path directory=supplied.toAbsolutePath().normalize();Set<String> files=filenames(directory,plan.size(),check);
        if(files.size()!=plan.size())throw new IOException("Missing or extra FAVOR nested command events;R exit0 and matching scalar values cannot admit this workflow");
        var byStep=new LinkedHashMap<Integer,Event>();long bytes=0;
        for(String filename:files.stream().sorted().toList()) {
            check.run();if(!filename.matches("event-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}[.]json"))throw new IOException("Unexpected,temporary or nonterminal FAVOR event filename: "+filename);
            Path path=directory.resolve(filename);regular(path);byte[] raw=AdmixtureInputs.raw(path,limits.eventBytes());
            if(raw.length>limits.totalBytes()-bytes)throw new IOException("Complete FAVOR event byte capacity exceeded");bytes+=raw.length;
            Map<?,?> m=AdmixtureInputs.decode(raw);
            if(!"completed".equals(m.get("status")))throw new IOException("FAVOR nested event is not completed (status="+m.get("status")+",exit="+m.get("exit_code")+");retain event diagnostics and reject this workflow");
            var required=new HashSet<>(REQUIRED);if(m.containsKey("timed_out")){required.add("timed_out");if(!Boolean.FALSE.equals(m.get("timed_out")))throw new IOException("Timed-out FAVOR nested command cannot be admitted");}
            if(!m.keySet().equals(required)||whole(m,"schema_version",1,1)!=1||!requestSha256.equals(m.get("request_sha256"))||!xsv.equals(m.get("xsv"))||!FavorXsvSupervisor.XSV_SHA256.equals(m.get("xsv_sha256")))throw new IOException("FAVOR event schema,request or original executable binding differs");
            int step=(int)whole(m,"planned_step",1,plan.size());List<String> arguments=strings(m.get("arguments"));var command=new ArrayList<>(List.of(xsv));command.addAll(plan.get(step-1));
            if(!arguments.equals(plan.get(step-1))||!strings(m.get("command")).equals(command))throw new IOException("FAVOR event does not match the exact staged original command plan");
            if(whole(m,"child_exit_code",0,0)!=0||whole(m,"exit_code",0,0)!=0||!Boolean.FALSE.equals(m.get("owned_child_alive")))throw new IOException("Failed or live owned FAVOR child cannot be admitted");
            long supervisor=whole(m,"supervisor_pid",1,9_007_199_254_740_991L),child=whole(m,"child_pid",1,9_007_199_254_740_991L);if(supervisor==child)throw new IOException("FAVOR supervisor and child process identities must differ");
            Instant start=time(m,"started_at"),finish=time(m,"finished_at");
            if(start.isBefore(parentStarted)||finish.isAfter(parentFinished)||finish.isBefore(start))throw new IOException("FAVOR event is outside its actual parent execution window");
            var event=new Event(step,filename,AdmixtureInputs.digest(raw),raw.length,supervisor,child,start,finish,arguments);
            if(byStep.putIfAbsent(step,event)!=null)throw new IOException("Repeated FAVOR nested command step: "+step);
        }
        var events=new ArrayList<Event>();var digest=new StringBuilder();Instant previous=parentStarted;
        for(int step=1;step<=plan.size();step++) {
            Event event=byStep.get(step);if(event==null)throw new IOException("Missing expected FAVOR nested command step: "+step);
            if(event.started().isBefore(previous))throw new IOException("FAVOR nested commands overlap or run out of source-plan order");previous=event.finished();events.add(event);
            digest.append(step).append('\t').append(event.filename()).append('\t').append(event.sha256()).append('\n');
        }
        var result=new Ledger(directory,events,bytes,AdmixtureInputs.digest(digest.toString().getBytes(StandardCharsets.UTF_8)));result.verify(check);return result;
    }
    private static Set<String> filenames(Path directory,int maximum,Runnable check)throws IOException {
        if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(directory)||!directory.toRealPath().equals(directory))throw new IOException("Missing or linked FAVOR terminal-event directory");
        var names=new HashSet<String>();try(var listing=Files.list(directory)){var iterator=listing.iterator();while(iterator.hasNext()){check.run();Path p=iterator.next();regular(p);if(!names.add(p.getFileName().toString())||names.size()>maximum)throw new IOException("Extra or repeated FAVOR terminal-event files");}}
        return Set.copyOf(names);
    }
    private static void regular(Path file)throws IOException {if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(file)||!file.toRealPath().equals(file))throw new IOException("Nonregular or linked FAVOR terminal event: "+file);}
    private static long whole(Map<?,?> m,String key,long min,long max)throws IOException {Object v=m.get(key);if(!(v instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=Math.rint(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)throw new IOException("Invalid FAVOR terminal event number: "+key);return (long)n.doubleValue();}
    private static List<String> strings(Object value)throws IOException {if(!(value instanceof List<?> list))throw new IOException("Require exact FAVOR event argument array");var result=new ArrayList<String>();for(Object item:list){if(!(item instanceof String s))throw new IOException("Invalid FAVOR event argument");result.add(s);}return List.copyOf(result);}
    private static Instant time(Map<?,?> m,String key)throws IOException {try{if(!(m.get(key) instanceof String s)||s.length()>64)throw new IOException("Invalid FAVOR event timestamp");Instant value=Instant.parse(s);if(!value.toString().equals(s))throw new IOException("Noncanonical FAVOR event timestamp");return value;}catch(DateTimeParseException invalid){throw new IOException("Invalid FAVOR event timestamp",invalid);}}
}
