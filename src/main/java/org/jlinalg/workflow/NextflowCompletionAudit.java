/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.workflow;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reconciles original engine completion counters with the complete attempt trace.
 * Recovered retries retain their failed attempts. This audits execution metadata;
 * callers must still verify declared outputs, inputs and runtime provenance.
 * An EMPTY outcome reports no task execution and does not establish an analysis.
 */
public final class NextflowCompletionAudit {
    private NextflowCompletionAudit() { }
    public record Limits(long maxLogBytes,int maxLogLineChars,NextflowTraceAudit.Limits trace) {
        public Limits {if(maxLogBytes<1||maxLogLineChars<1)throw new IllegalArgumentException("Invalid Nextflow completion capacities");Objects.requireNonNull(trace);}
        public static Limits defaults(){return new Limits(67108864,32768,NextflowTraceAudit.Limits.defaults());}
    }
    public enum Outcome { COMPLETE, COMPLETE_AFTER_RETRIES, EMPTY, FAILED_OR_INCOMPLETE }
    public record Stats(long succeeded,long failed,long ignored,long cached,long pending,long submitted,long running,long retries,long aborted) { }
    public record Result(Stats stats,NextflowTraceAudit.Summary trace,Outcome outcome,long logBytes,String logSha256) {
        /** Engine/trace consistency only; even true requires separate output admission. */
        public boolean executionSucceeded(){return outcome!=Outcome.FAILED_OR_INCOMPLETE;}
        public boolean hasObservedTasks(){return trace.taskRows()!=0;}
    }
    private static final Pattern VECTOR=Pattern.compile("\\b(?:nextflow|n)\\.trace\\.WorkflowStatsObserver\\s+-\\s+Workflow completed > WorkflowStats\\[(.*)\\]\\s*$");
    private static final Pattern FINISHED=Pattern.compile("\\b(?:nextflow|n)\\.script\\.ScriptRunner\\s+-\\s+> Execution complete -- Goodbye\\s*$");
    private static final String[] COUNTERS={"succeededCount","failedCount","ignoredCount","cachedCount","pendingCount","submittedCount","runningCount","retriesCount","abortedCount"};

    public static Result read(Path engineLog,Path trace,int actualExitCode,Limits limits,Runnable cancellation)throws IOException {
        Objects.requireNonNull(limits);Objects.requireNonNull(cancellation);
        if(actualExitCode<0)throw new IllegalArgumentException("Declare the actual engine exit code");
        if(actualExitCode!=0)throw new IOException("Original Nextflow engine failed with exit "+actualExitCode+"; no execution result is admitted");
        Stats[] stats={null};long[] vectorLine={0},finishedLine={0};
        TextPin log=scan(engineLog,limits.maxLogBytes,limits.maxLogLineChars,cancellation,(line,index)->{
            var match=VECTOR.matcher(line);
            if(match.find()){if(stats[0]!=null)throw new IOException("Multiple Nextflow completion vectors; use one complete new engine log");stats[0]=stats(match.group(1));vectorLine[0]=index;}
            if(FINISHED.matcher(line).find()){if(finishedLine[0]!=0)throw new IOException("Multiple Nextflow terminal engine markers");finishedLine[0]=index;}
        });
        if(stats[0]==null||finishedLine[0]<=vectorLine[0])throw new IOException("Missing complete original Nextflow counter/terminal receipt");
        Stats counts=stats[0];long rows;
        try{rows=Math.addExact(Math.addExact(counts.succeeded,counts.cached),Math.addExact(counts.failed,counts.aborted));}
        catch(ArithmeticException overflow){throw new IOException("Nextflow complete task family overflows capacity",overflow);}
        if(rows>limits.trace.maxRows())throw new IOException("Nextflow complete task family exceeds trace row capacity");
        var summary=NextflowTraceAudit.read(trace,rows,actualExitCode,limits.trace,cancellation);
        Map<String,Long> statuses=new HashMap<>();int[] statusColumn={-1},exitColumn={-1};
        TextPin reread=scan(trace,limits.trace.maxBytes(),limits.trace.maxLineChars(),cancellation,(line,index)->{
            String[] cells=line.split("\t",-1);
            if(index==1){for(int i=0;i<cells.length;i++){if(cells[i].equals("status"))statusColumn[0]=i;if(cells[i].equals("exit"))exitColumn[0]=i;}if(statusColumn[0]<0||exitColumn[0]<0)throw new IOException("Missing validated Nextflow trace status/exit columns");return;}
            if(cells.length<=Math.max(statusColumn[0],exitColumn[0]))throw new IOException("Nextflow trace changed during completion reconciliation");
            String status=cells[statusColumn[0]];
            if(!Set.of("COMPLETED","CACHED","FAILED","ABORTED").contains(status))throw new IOException("Nonterminal task in Nextflow completion trace: "+status);
            if(status.equals("FAILED")&&cells[exitColumn[0]].equals("0"))throw new IOException("Failed Nextflow attempt has exit0");
            statuses.merge(status,1L,Math::addExact);
        });
        if(reread.bytes!=summary.bytes()||!reread.sha256.equals(summary.sha256()))throw new IOException("Nextflow trace bytes changed between full audits");
        if(statuses.getOrDefault("COMPLETED",0L)!=counts.succeeded||statuses.getOrDefault("CACHED",0L)!=counts.cached||statuses.getOrDefault("FAILED",0L)!=counts.failed||statuses.getOrDefault("ABORTED",0L)!=counts.aborted)throw new IOException("Original Nextflow task counters and complete trace statuses disagree");
        boolean complete=counts.ignored==0&&counts.pending==0&&counts.submitted==0&&counts.running==0&&counts.aborted==0&&counts.failed==counts.retries;
        Outcome outcome=!complete?Outcome.FAILED_OR_INCOMPLETE:rows==0?Outcome.EMPTY:counts.retries>0?Outcome.COMPLETE_AFTER_RETRIES:Outcome.COMPLETE;
        return new Result(counts,summary,outcome,log.bytes,log.sha256);
    }

    private static Stats stats(String vector)throws IOException {
        var fields=new HashMap<String,String>();String[] parts=vector.split(";",-1);if(parts.length>128)throw new IOException("Nextflow completion field capacity exceeded");
        for(String part:parts){part=part.trim();if(part.isEmpty())continue;int equal=part.indexOf('=');if(equal<1)throw new IOException("Malformed Nextflow completion field");String name=part.substring(0,equal).trim(),value=part.substring(equal+1).trim();if(!name.matches("[A-Za-z_][A-Za-z0-9_]*")||fields.putIfAbsent(name,value)!=null)throw new IOException("Duplicate/invalid Nextflow completion field");}
        long[] numbers=new long[COUNTERS.length];
        for(int i=0;i<COUNTERS.length;i++){
            String value=fields.get(COUNTERS[i]);if(value==null||!value.matches("0|[1-9][0-9]*"))throw new IOException("Missing/noninteger Nextflow completion counter: "+COUNTERS[i]);
            try{numbers[i]=Long.parseLong(value);}catch(NumberFormatException tooLarge){throw new IOException("Nextflow completion counter capacity exceeded",tooLarge);}
        }
        if(numbers[2]>numbers[1]||numbers[7]>numbers[1])throw new IOException("Impossible ignored/retry failure counters in Nextflow receipt");
        return new Stats(numbers[0],numbers[1],numbers[2],numbers[3],numbers[4],numbers[5],numbers[6],numbers[7],numbers[8]);
    }

    private record TextPin(long bytes,String sha256) { }
    @FunctionalInterface private interface Line {void accept(String line,long index)throws IOException;}
    private static TextPin scan(Path path,long maximum,int lineMaximum,Runnable cancellation,Line visitor)throws IOException {
        cancellation.run();BasicFileAttributes before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.size()>maximum)throw new IOException("Missing, linked or oversized Nextflow execution metadata: "+path);
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);long bytes;
        try(var input=new Bounded(Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS),maximum);var reader=new BufferedReader(new InputStreamReader(new DigestInputStream(input,digest),decoder))){
            var line=new StringBuilder();long index=0;
            for(;;){
                int c=reader.read();if(c==-1){if(!line.isEmpty())throw new IOException("Unterminated Nextflow execution metadata; no prefix is admitted");break;}
                if(c=='\n'){if(!line.isEmpty()&&line.charAt(line.length()-1)=='\r')line.setLength(line.length()-1);cancellation.run();visitor.accept(line.toString(),++index);line.setLength(0);}
                else{if(line.length()>=lineMaximum)throw new IOException("Nextflow execution metadata line capacity exceeded");line.append((char)c);if((line.length()&1023)==0)cancellation.run();}
            }
            bytes=input.count;
        }
        var after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(bytes!=before.size()||bytes!=after.size()||!after.isRegularFile()||!before.lastModifiedTime().equals(after.lastModifiedTime())||!Objects.equals(before.fileKey(),after.fileKey()))throw new IOException("Nextflow execution metadata changed during complete audit");
        return new TextPin(bytes,HexFormat.of().formatHex(digest.digest()));
    }
    private static final class Bounded extends FilterInputStream {
        private final long maximum;private long count;
        Bounded(InputStream input,long maximum){super(input);this.maximum=maximum;}
        private void add(int n)throws IOException{if(n>0){count+=n;if(count>maximum)throw new IOException("Nextflow execution metadata byte capacity exceeded");}}
        @Override public int read()throws IOException{int c=in.read();if(c!=-1)add(1);return c;}
        @Override public int read(byte[] bytes,int offset,int length)throws IOException{int n=in.read(bytes,offset,length);add(n);return n;}
    }
}
