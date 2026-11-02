/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jlinalg.sequence.FastpReadFilter;

/** Complete disk-grouped read conservation and source-filter/prefix/correction-envelope witnesses. */
final class FastpJoin {
    private FastpJoin() { }
    record Limits(int familyRecords,long familyBytes,long matchingOperations,long solverSteps) {
        Limits {if(familyRecords<2||familyRecords>100000||familyBytes<65536||familyBytes>1_073_741_824L||matchingOperations<1||matchingOperations>1_000_000_000_000L||solverSteps<1||solverSteps>1_000_000_000L)throw new IllegalArgumentException("Invalid explicit complete fastp matching capacities");}
    }
    record Counts(long inputReads,long representedReads,long unrepresentedReads,long ambiguousFamilies,long visibleCorrectedReads,long visibleCorrectedBases,List<Long> representedSourceFilterCounts) { }
    private record Pair(long ordinal,FastpFastq.Read first,FastpFastq.Read second) { }
    private record Out(String kind,String failure,FastpFastq.Read read) { }
    private record Candidate(int slot,int sourceCode,int changes) { }
    static String input(FastpFastq.Read first,FastpFastq.Read second,FastpFastq.Encoding encoding,FastpFastq.Pairing policy)throws IOException {
        String key=key(first,policy);if(second!=null&&!key.equals(key(second,policy)))throw new FastpRuntime.Unavailable("Complete fastp output matching requires a shared declared pair identity;unverified unequal ordinal-only IDs are not silently renamed");
        return key+"\tI\t"+String.format(java.util.Locale.ROOT,"%019d",first.ordinal())+"\t"+fields(first,encoding)+"\t"+(second==null?"\t\t\t":fields(second,encoding));
    }
    static String output(String kind,FastpFastq.Read read,FastpFastq.Pairing policy)throws IOException {
        String failure="";String header=read.header();
        if(kind.equals("FAILED")){
            int at=header.lastIndexOf(' ');if(at<1)throw new IOException("fastp failed FASTQ lacks its original failure tag");failure=header.substring(at+1);code(failure);header=header.substring(0,at);
        }
        var originalHeader=new FastpFastq.Read(read.ordinal(),header,read.sequence(),read.quality(),read.originalPlus());
        return key(originalHeader,policy)+"\tO\t"+kind+"\t"+String.format(java.util.Locale.ROOT,"%019d",read.ordinal())+"\t"+failure+"\t"+fields(originalHeader,FastpFastq.Encoding.PHRED33);
    }
    private static String key(FastpFastq.Read read,FastpFastq.Pairing policy){String token=read.token();if(policy==FastpFastq.Pairing.TERMINAL_MATE_SUFFIX&&(token.endsWith("/1")||token.endsWith("/2")))token=token.substring(0,token.length()-2);return encoded(token);}
    private static String fields(FastpFastq.Read read,FastpFastq.Encoding encoding){
        String quality=read.quality();if(encoding==FastpFastq.Encoding.PHRED64){var shifted=new StringBuilder(quality.length());for(int i=0;i<quality.length();i++)shifted.append((char)(quality.charAt(i)-31));quality=shifted.toString();}
        return encoded(read.header())+"\t"+encoded(read.sequence())+"\t"+encoded(quality)+"\t"+encoded(read.originalPlus());
    }
    static Counts reconcile(Path sorted,Path newLedger,boolean paired,FastpParameters.Options options,Limits limits,int maximumLine,FastpProcess.Check disk,Runnable check)throws IOException {
        if(limits.familyBytes()>Runtime.getRuntime().maxMemory()/4)throw new IOException("fastp family matching memory exceeds one quarter of current Java heap");
        var totals=new long[6];var counters=new long[32];var operations=new Operations(limits,check);long groups=0;
        try(var in=Files.newBufferedReader(sorted,StandardCharsets.US_ASCII);var out=FastpSort.writer(newLedger)){
            out.write("family_base64\tinput_pair_ordinal\tread1_disposition\tread2_disposition\tassignment_is_uniquely_identified\n");String pending=FastpSort.next(in,maximumLine);
            while(pending!=null){
                int tab=pending.indexOf('\t');if(tab<1)throw new IOException("Malformed complete fastp join key");String key=pending.substring(0,tab);var rows=new ArrayList<String>();long held=0;
                do{held=Math.addExact(held,Math.addExact(256,8L*pending.length()));if(rows.size()==limits.familyRecords()||held>limits.familyBytes())throw new IOException("Complete duplicate-header family exceeds explicit memory/record capacity;do not truncate or invent identities");rows.add(pending);pending=FastpSort.next(in,maximumLine);}while(pending!=null&&pending.startsWith(key+"\t"));
                Family family=new Family(rows,paired,options,operations,held);family.solve();long[] values=family.summary(counters);for(int i=0;i<totals.length;i++)totals[i]=Math.addExact(totals[i],values[i]);family.ledger(key,out);if((++groups&511)==0){out.flush();check.run();disk.verify();}
            }
        }
        disk.verify();var saved=new ArrayList<Long>();for(long value:counters)saved.add(value);return new Counts(totals[0],totals[1],totals[2],totals[3],totals[4],totals[5],List.copyOf(saved));
    }
    private static final class Operations {
        final Limits limits;final Runnable check;long operations,steps;
        Operations(Limits limits,Runnable check){this.limits=limits;this.check=check;}
        void add(long amount)throws IOException{operations=Math.addExact(operations,amount);if(operations>limits.matchingOperations())throw new IOException("Complete fastp sequence/correction matching exceeds explicit work capacity");check.run();}
        void step()throws IOException{if(++steps>limits.solverSteps())throw new IOException("Complete fastp duplicate-header assignment exceeds explicit solver capacity;no partial matching is admitted");if((steps&1023)==0)check.run();}
    }
    private static final class Family {
        final List<Pair> inputs=new ArrayList<>();final List<Out> outputs=new ArrayList<>();final boolean paired;final FastpParameters.Options options;final Operations operations;
        List<List<Candidate>> candidates;Integer[] order;Candidate[] chosen;Out[] slots;boolean ambiguous;
        Family(List<String> rows,boolean paired,FastpParameters.Options options,Operations operations,long held)throws IOException {
            this.paired=paired;this.options=options;this.operations=operations;
            for(String row:rows){String[] fields=row.split("\t",-1);
                try{
                    if(fields.length==11&&fields[1].equals("I")){long ordinal=Long.parseLong(fields[2]);if(ordinal<1)throw new IOException("Invalid source occurrence ordinal");inputs.add(new Pair(ordinal,read(fields,3,ordinal),paired?read(fields,7,ordinal):null));}
                    else if(fields.length==9&&fields[1].equals("O")){long ordinal=Long.parseLong(fields[3]);if(!Set.of("MAIN1","MAIN2","UNPAIRED1","UNPAIRED2","FAILED").contains(fields[2]))throw new IOException("Unknown fastp output role");outputs.add(new Out(fields[2],fields[4],read(fields,5,ordinal)));}
                    else throw new IOException("Malformed typed fastp join record");
                }catch(IllegalArgumentException invalid){throw new IOException("Invalid complete fastp index record",invalid);}
            }
            if(inputs.isEmpty()||outputs.size()>inputs.size()*(paired?2:1))throw new IOException("fastp output population has no sufficient original occurrences");
            candidates=new ArrayList<>();slots=new Out[inputs.size()*(paired?2:1)];chosen=new Candidate[outputs.size()];order=new Integer[outputs.size()];
            long candidateBytes=held;
            for(int o=0;o<outputs.size();o++){
                Out output=outputs.get(o);var possible=new ArrayList<Candidate>();
                for(int i=0;i<inputs.size();i++)for(int mate=0;mate<(paired?2:1);mate++){
                    if(output.kind().endsWith("1")&&mate!=0||output.kind().endsWith("2")&&mate!=1)continue;Pair pair=inputs.get(i);Candidate c=match(pair,output,mate,options,operations);if(c!=null){candidateBytes=Math.addExact(candidateBytes,96);if(candidateBytes>operations.limits.familyBytes())throw new IOException("Complete fastp duplicate-header candidate graph exceeds explicit memory");possible.add(new Candidate(i*(paired?2:1)+mate,c.sourceCode(),c.changes()));}
                }
                if(possible.isEmpty())throw new IOException("fastp output header/plus/sequence/quality/filter has no valid original-source envelope");if(possible.size()>1)ambiguous=true;candidates.add(possible);order[o]=o;
            }
            java.util.Arrays.sort(order,Comparator.comparingInt(i->candidates.get(i).size()));
        }
        void solve()throws IOException {
            int[] next=new int[order.length];int at=0;
            while(at>=0){
                operations.step();
                if(at==order.length){boolean valid=true;for(int i=0;i<inputs.size();i++)if(!validPair(i)){valid=false;break;}if(valid)return;at--;continue;}
                int o=order[at];if(chosen[o]!=null){slots[chosen[o].slot()]=null;chosen[o]=null;}
                List<Candidate> possible=candidates.get(o);Candidate selected=null;
                while(next[at]<possible.size()){Candidate c=possible.get(next[at]++);if(slots[c.slot()]==null){selected=c;break;}}
                if(selected==null){next[at]=0;at--;continue;}
                slots[selected.slot()]=outputs.get(o);chosen[o]=selected;at++;if(at<order.length)next[at]=0;
            }
            throw new IOException("Complete fastp output multiset cannot be assigned to original occurrences with valid paired dispositions");
        }
        boolean validPair(int pair)throws IOException {
            Out a=slots[pair*(paired?2:1)],b=paired?slots[pair*2+1]:null;
            if(a==null&&b==null)return options.duplication().deduplicate()||paired&&canFail(inputs.get(pair).first(),0)&&canFail(inputs.get(pair).second(),1);
            if(!paired)return a!=null&&(a.kind().equals("MAIN1")||a.kind().equals("FAILED")&&!a.failure().equals("paired_read_is_failing"));
            if(a==null||b==null)return false;
            if(a.kind().equals("MAIN1")&&b.kind().equals("MAIN2"))return true;
            if(options.storeUnpaired())return a.kind().equals("UNPAIRED1")&&ownFailure(b)||ownFailure(a)&&b.kind().equals("UNPAIRED2");
            return a.kind().equals("FAILED")&&b.kind().equals("FAILED")&&(a.failure().equals("paired_read_is_failing")&&ownFailure(b)||ownFailure(a)&&b.failure().equals("paired_read_is_failing"));
        }
        private boolean canFail(FastpFastq.Read read,int mate)throws IOException {
            operations.add(read.sequence().length()+1L);var g=options.global();var c=options.cutting();
            var cut=FastpReadFilter.trimAndCut(read.sequence(),read.quality(),new FastpReadFilter.Trimming(mate==0?g.front1():g.front2(),mate==0?g.tail1():g.tail2(),c.front(),c.right(),c.tail()));
            if(!cut.present())return true;
            // An enabled upstream trimming algorithm can retain an empty prefix,
            // which the source rejects even when its length filter is disabled.
            // This is a conservative envelope, not a reconstruction of that choice.
            if(options.adapters().enabled()||options.polyG()!=FastpParameters.PolyG.DISABLE||options.polyX())return true;
            cut=FastpReadFilter.maximumLength(cut,mate==0?g.maximum1():g.maximum2());
            return FastpReadFilter.assess(cut,options.filters()).reason()!=FastpReadFilter.Reason.PASS;
        }
        private boolean ownFailure(Out out)throws IOException{return out.kind().equals("FAILED")&&code(out.failure())>0;}
        long[] summary(long[] source)throws IOException {
            long represented=outputs.size(),total=inputs.size()*(paired?2L:1L),changes=0,changedReads=0;
            for(Candidate c:chosen){changes+=c.changes();if(c.changes()>0)changedReads++;}
            for(int i=0;i<inputs.size();i++){
                Out a=slots[i*(paired?2:1)],b=paired?slots[i*2+1]:null;if(a==null)continue;
                int severity=Math.max(dispositionCode(a),paired?dispositionCode(b):0);source[severity]+=paired?2:1;
            }
            return new long[]{total,represented,total-represented,ambiguous?1:0,changedReads,changes};
        }
        void ledger(String key,BufferedWriter out)throws IOException {for(int i=0;i<inputs.size();i++){String a=label(slots[i*(paired?2:1)]),b=paired?label(slots[i*2+1]):"NOT_PAIRED";out.write(key+"\t"+inputs.get(i).ordinal()+"\t"+a+"\t"+b+"\t"+!ambiguous+"\n");}}
    }
    private static Candidate match(Pair pair,Out output,int mate,FastpParameters.Options options,Operations operations)throws IOException {
        FastpFastq.Read original=mate==0?pair.first():pair.second(),other=mate==0?pair.second():pair.first(),read=output.read();if(!original.header().equals(read.header())||!original.originalPlus().equals(read.originalPlus()))return null;
        operations.add(4L*(original.sequence().length()+read.sequence().length()+(other==null?0:other.sequence().length()))+1);
        var g=options.global();var cut=options.cutting();var trimmed=FastpReadFilter.trimAndCut(original.sequence(),original.quality(),new FastpReadFilter.Trimming(mate==0?g.front1():g.front2(),mate==0?g.tail1():g.tail2(),cut.front(),cut.right(),cut.tail()));
        if(!trimmed.present())return output.kind().equals("FAILED")&&output.failure().equals("failed_too_short")&&original.sequence().equals(read.sequence())&&original.quality().equals(read.quality())?new Candidate(0,16,0):null;
        int maximum=mate==0?g.maximum1():g.maximum2();if(read.sequence().length()>trimmed.sequence().length()||maximum>0&&read.sequence().length()>maximum)return null;
        if(!options.adapters().enabled()&&options.polyG()==FastpParameters.PolyG.DISABLE&&!options.polyX()&&read.sequence().length()!=(maximum==0?trimmed.sequence().length():Math.min(maximum,trimmed.sequence().length())))return null;
        int changed=0;Set<Integer> inserts=null;FastpReadFilter.Trimmed partner=null;
        for(int i=0;i<read.sequence().length();i++){
            char base=read.sequence().charAt(i),quality=read.quality().charAt(i);
            if(base==trimmed.sequence().charAt(i)){if(quality!=trimmed.quality().charAt(i))return null;continue;}
            if(!options.correction()||other==null||trimmed.quality().charAt(i)>33+14||quality<33+30)return null;
            if(partner==null)partner=FastpReadFilter.trimAndCut(other.sequence(),other.quality(),new FastpReadFilter.Trimming(mate==0?g.front2():g.front1(),mate==0?g.tail2():g.tail1(),cut.front(),cut.right(),cut.tail()));
            if(!partner.present())return null;var candidates=new HashSet<Integer>();
            for(int j=0;j<partner.sequence().length();j++){
                if((j&8191)==0)operations.add(Math.min(8192,partner.sequence().length()-j));int insert=i+j+1;
                if(partner.quality().charAt(j)==quality&&complement(partner.sequence().charAt(j))==base&&Math.min(trimmed.sequence().length(),insert)-Math.max(0,insert-partner.sequence().length())>=options.overlap().minimumLength())candidates.add(insert);
            }
            if(inserts==null)inserts=candidates;else inserts.retainAll(candidates);if(inserts.isEmpty())return null;changed++;
        }
        var actual=new FastpReadFilter.Trimmed(true,read.sequence(),read.quality(),0,read.sequence().length(),0);int actualCode=sourceCode(FastpReadFilter.assess(actual,options.filters()).reason());
        if(output.kind().equals("FAILED")){if(code(output.failure())!=actualCode)return null;}else if(actualCode!=0)return null;
        return new Candidate(0,actualCode,changed);
    }
    private static int dispositionCode(Out output)throws IOException{return output.kind().equals("FAILED")?code(output.failure()):0;}
    private static String label(Out output){return output==null?"UNREPRESENTED_PAIRED_FAILURE_OR_DEDUP":output.kind()+(output.failure().isEmpty()?"":":"+output.failure());}
    static int code(String tag)throws IOException {return switch(tag){case "paired_read_is_failing"->0;case "failed_too_many_n_bases"->12;case "failed_too_short"->16;case "failed_too_long"->17;case "failed_quality_filter"->20;case "failed_low_complexity"->24;default->throw new IOException("Unknown original fastp failure disposition: "+tag);};}
    private static int sourceCode(FastpReadFilter.Reason reason){return switch(reason){case PASS->0;case N_BASE->12;case TOO_SHORT->16;case TOO_LONG->17;case QUALITY->20;case COMPLEXITY->24;};}
    private static char complement(char value){return switch(value){case 'A'->'T';case 'T'->'A';case 'C'->'G';case 'G'->'C';case 'N'->'N';default->0;};}
    private static String encoded(String value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    private static String decoded(String value)throws IOException {try{return FastpFiles.utf8(Base64.getUrlDecoder().decode(value));}catch(IllegalArgumentException invalid){throw new IOException("Invalid owned fastp index encoding",invalid);}}
    private static FastpFastq.Read read(String[] fields,int start,long ordinal)throws IOException{return new FastpFastq.Read(ordinal,decoded(fields[start]),decoded(fields[start+1]),decoded(fields[start+2]),decoded(fields[start+3]));}
}
