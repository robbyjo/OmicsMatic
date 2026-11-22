/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.sequence.HomerMotifs;
import static org.jlinalg.cli.FollowupSupport.*;

/** Immutable bounded inputs, safe upstream aliases and complete native presence/site counts. */
final class HomerCoreData {
    private HomerCoreData() { }
    record Limits(long input,long decoded,long residues,int sequences,int perSequence,int motifs,
            long rows,long operations,long hits,long workspace,int line) {
        static Limits from(Map<String,String> o)throws IOException{
            return from(o,false);
        }
        static Limits from(Map<String,String> o,boolean discovery)throws IOException{
            var limits=new Limits(positive(o,"maximum-input-bytes",268435456),positive(o,"maximum-decoded-characters",1073741824),
                    positive(o,"maximum-residues",10000000),integer(o,"maximum-sequences",10000,1,1000000),
                    integer(o,"maximum-sequence-residues",500000,1,999999),integer(o,"maximum-motifs",10000,1,100000),
                    positive(o,"maximum-motif-rows",100000),positive(o,"maximum-operations",1000000000),positive(o,"maximum-hits",100000),
                    positive(o,"workspace-bytes",536870912),integer(o,"maximum-line-characters",1048576,16,1048576));
            if(limits.workspace>Runtime.getRuntime().maxMemory()*3/4)throw new IOException("Declared HOMER workspace exceeds three quarters of Java maximum heap; review heap/resource capacities");
            if(!discovery&&Math.multiplyExact(512L,limits.hits)>limits.workspace/2)throw new IOException("Declared hit audit requires more than half the workspace; review maximum-hits and heap/workspace capacities");
            return limits;
        }
    }
    record Sequence(String alias,String role,long ordinal,String id,String header,String dna,long lowercase,long masked) { }
    record Model(String alias,HomerMotifs.Compiled compiled) { }
    record Data(List<Sequence> sequences,List<Model> models,long[][] presence,long hits,long operations,
            long targetCount,long backgroundCount,long lower,long masked,long duplicates,long overlappingIds,long overlappingDna,
            Map<Path,String> hashes,HomerMotifs.Strands strands,Integer offset,Limits limits,Budget budget) {
        Data {sequences=List.copyOf(sequences);models=List.copyOf(models);hashes=Map.copyOf(hashes);}
        void verify()throws IOException{
            for(var entry:hashes.entrySet()){HomerFasta.regular(entry.getKey(),Long.MAX_VALUE);if(!hash(entry.getKey()).equals(entry.getValue()))throw new IOException("HOMER immutable original/snapshot/staged input changed: "+entry.getKey());}
        }
    }
    static Data stage(Map<String,String> o,String mode,Path work,Limits limits)throws Exception{
        var hashes=new LinkedHashMap<Path,String>();long inputBytes=0;var budget=new Budget(limits.workspace);
        for(String key:List.of("targets","background","motifs","options-file")){
            if(!o.containsKey(key)){if(key.equals("targets")||key.equals("motifs")&&!mode.equals("denovo")||key.equals("background")&&!mode.equals("find"))required(o,key);continue;}
            Path source=Path.of(required(o,key)).toAbsolutePath().normalize();HomerFasta.regular(source,limits.input);
            inputBytes=Math.addExact(inputBytes,Files.size(source));if(inputBytes>limits.input)throw new IOException("Combined original HOMER inputs exceed maximum-input-bytes");
            String digest=hash(source);if(key.equals("options-file")&&!digest.equals(o.get("options-sha256")))throw new IOException("HOMER options file changed before snapshot");
            hashes.put(source,digest);Path snapshot=work.resolve("input-"+key);budget.add(Files.size(source));Files.copy(source,snapshot);
            if(!hash(snapshot).equals(digest))throw new IOException("HOMER input changed during immutable snapshot");hashes.put(snapshot,digest);
        }
        if(inputBytes>limits.workspace/2)throw new IOException("Raw HOMER input snapshots exceed half the workspace");
        List<HomerMotifs.Motif> sourceModels=mode.equals("denovo")?List.of():HomerMotifs.read(work.resolve("input-motifs"),new HomerMotifs.Limits(limits.motifs,4096,limits.rows,limits.input,limits.decoded,limits.line,limits.workspace/4));
        var models=new ArrayList<Model>();long modelRows=0;
        try(var motifs=budget.writer(work.resolve("stage-motifs.motif"));var ledger=budget.writer(work.resolve("models.tsv"))){
            ledger.write("alias\tmotif_ordinal\tname_json\tconsensus_label_json\twidth\tfixed_threshold\tsource_log_p_metadata\textra_header_json\n");
            for(var motif:sourceModels){
                String alias="M"+String.format(java.util.Locale.ROOT,"%09d",motif.ordinal());models.add(new Model(alias,HomerMotifs.compile(motif)));
                motifs.write(">N\t"+alias+"\t"+motif.threshold()+"\t0\n");
                for(double[] row:motif.probabilities()){motifs.write(row[0]+"\t"+row[1]+"\t"+row[2]+"\t"+row[3]+"\n");modelRows++;}
                ledger.write(alias+"\t"+motif.ordinal()+"\t"+RegenieRuntime.json(motif.name())+"\t"+RegenieRuntime.json(motif.consensus())+"\t"+motif.width()+"\t"+motif.threshold()+"\t"+(motif.sourceLogP()==null?"":motif.sourceLogP())+"\t"+RegenieRuntime.json(motif.extraHeader())+"\n");
            }
        }
        String strand=required(o,"strands");HomerMotifs.Strands strands=switch(strand){case "forward"->HomerMotifs.Strands.FORWARD;case "reverse"->HomerMotifs.Strands.REVERSE;case "both"->HomerMotifs.Strands.BOTH;default->throw new IOException("Choose strands forward,reverse or both");};
        Integer offset=mode.equals("find")&&!required(o,"offset").equals("midpoint")?Integer.valueOf(o.get("offset")):null;
        if(offset!=null&&offset==-12345678)throw new IOException("Original reserved midpoint sentinel is not a literal offset; select midpoint explicitly");
        boolean mask=required(o,"alphabet").equals("mask-n"),duplicates=required(o,"duplicate-ids").equals("retain-ordinal");
        var sequences=new ArrayList<Sequence>();long[] totals={0,0,0,0,0,0,0}; // residues,targets,bg,lowercase,masked,duplicates,retained estimate
        totals[6]=Math.addExact(Math.multiplyExact(128L,modelRows),mode.equals("denovo")?0:Math.multiplyExact(512L,limits.hits));
        var targetIds=new HashSet<String>();var targetDna=new HashSet<String>();long[] overlap={0,0};
        try(var ledger=budget.writer(work.resolve("sequences.tsv"))){
            ledger.write("alias\trole\tsequence_ordinal\tid_json\theader_json\tresidues\tdna_sha256\tlowercase_promoted\tmasked_residues\n");
            for(String role:mode.equals("find")?List.of("targets"):List.of("targets","background")){
                try(var fasta=budget.writer(work.resolve("stage-"+role+".fa"))){
                    HomerFasta.read(work.resolve("input-"+role),new HomerFasta.Limits(limits.input,limits.decoded,limits.residues,limits.sequences,limits.perSequence,limits.line),duplicates,seq->{
                        if(seq.residues().isEmpty())throw new IOException("Empty FASTA sequences cannot be preserved as original HOMER FASTA populations; correct the empty record explicitly: "+seq.id());
                        if(sequences.size()>=limits.sequences)throw new IOException("Combined target/background sequence count exceeds maximum-sequences");
                        totals[0]=Math.addExact(totals[0],seq.residues().length());if(totals[0]>limits.residues)throw new IOException("Combined target/background residues exceed maximum-residues");
                        totals[6]=Math.addExact(totals[6],12L*seq.residues().length()+16L*seq.header().length()+8192);
                        if(totals[6]>limits.workspace/2||totals[6]>Runtime.getRuntime().maxMemory()/2)throw new IOException("Retained HOMER sequence/model/identity/hit audit estimate exceeds half the workspace or Java heap");
                        var dna=new StringBuilder(seq.residues().length());long lower=0,masked=0;
                        for(int i=0;i<seq.residues().length();i++){
                            char c=seq.residues().charAt(i);if(c>='a'&&c<='z'){c=(char)(c-32);if("ACGTN".indexOf(c)>=0)lower++;}
                            if("ACGTN".indexOf(c)<0){if(!mask||c<33||c>126)throw new IOException("Noncanonical FASTA residue requires explicit mask-n; controls/non-ASCII are never masked");c='N';masked++;}dna.append(c);
                        }
                        String alias=(role.equals("targets")?"T":"B")+String.format(java.util.Locale.ROOT,"%09d",seq.ordinal());
                        String sequence=dna.toString(),digest=AdmixtureInputs.digest(sequence.getBytes(StandardCharsets.US_ASCII));
                        if(role.equals("targets")){targetIds.add(seq.id());targetDna.add(digest);totals[1]++;}else{if(targetIds.contains(seq.id()))overlap[0]++;if(targetDna.contains(digest))overlap[1]++;totals[2]++;}
                        totals[3]+=lower;totals[4]+=masked;sequences.add(new Sequence(alias,role,seq.ordinal(),seq.id(),seq.header(),sequence,lower,masked));
                        fasta.write(">"+alias+"\n");for(int p=0;p<sequence.length();p+=80)fasta.write(sequence.substring(p,Math.min(p+80,sequence.length()))+"\n");
                        ledger.write(alias+"\t"+role+"\t"+seq.ordinal()+"\t"+RegenieRuntime.json(seq.id())+"\t"+RegenieRuntime.json(seq.header())+"\t"+sequence.length()+"\t"+digest+"\t"+lower+"\t"+masked+"\n");
                    });
                }
            }
        }
        // Duplicate aliases never reach the original parser; preserve actual source-name multiplicity.
        for(String role:List.of("targets","background")){
            var ids=new HashSet<String>();for(var seq:sequences)if(seq.role.equals(role)&&!ids.add(seq.id))totals[5]++;
        }
        if(!mode.equals("find")&&totals[2]<2)throw new IOException("Enrichment/discovery core audit requires at least two background sequences for the original rate floor/ceiling");
        if((overlap[0]>0||overlap[1]>0)&&required(o,"overlap-policy").equals("error"))throw new IOException("Target/background IDs or normalized sequences overlap; review source populations and choose retain explicitly if intended");
        long[][] presence=new long[models.size()][2];long[] scanTotals={0,0,0};
        try(var counts=budget.writer(work.resolve("native-scan-counts.tsv"))){
            counts.write("motif_alias\tsequence_alias\twindows\tambiguous_windows\tscored_sites\thits\tsequence_present\tmaximum_possible_operations\tresult_verification_operations_reserved\n");
            for(var sequence:sequences){
                long chosenOffset=offset==null?-(long)(sequence.dna.length()/2):offset;
                if(chosenOffset<Integer.MIN_VALUE||chosenOffset+sequence.dna.length()-1>Integer.MAX_VALUE)throw new IOException("Offset plus original sequence anchor exceeds original HOMER signed integer coordinates");
                for(int index=0;index<models.size();index++){
                    var model=models.get(index);long operations=Math.multiplyExact(Math.multiplyExact(Math.max(0L,(long)sequence.dna.length()-model.compiled.motif().width()+1),model.compiled.motif().width()),strands==HomerMotifs.Strands.BOTH?2:1);
                    if(operations>limits.operations-scanTotals[0])throw new IOException("Complete HOMER native audit exceeds maximum-operations");
                    var summary=HomerMotifs.scan(sequence.dna,model.compiled,new HomerMotifs.Options(strands,offset,false,limits.perSequence,Math.max(1,limits.operations-scanTotals[0]),limits.hits),hit->{});
                    scanTotals[0]=Math.addExact(scanTotals[0],summary.maximumPossibleOperations());scanTotals[1]=Math.addExact(scanTotals[1],summary.hits());
                    long verification=mode.equals("find")?Math.multiplyExact(summary.hits(),model.compiled.motif().width()):0;
                    scanTotals[2]=Math.addExact(scanTotals[2],verification);if(Math.addExact(scanTotals[0],scanTotals[2])>limits.operations)throw new IOException("HOMER scan plus complete result verification exceeds maximum-operations");
                    if(scanTotals[1]>limits.hits)throw new IOException("Complete HOMER native audit exceeds global maximum-hits; no truncation is allowed");
                    if(summary.hits()>0)presence[index][sequence.role.equals("targets")?0:1]++;
                    counts.write(model.alias+"\t"+sequence.alias+"\t"+summary.windows()+"\t"+summary.ambiguousWindows()+"\t"+summary.scoredSites()+"\t"+summary.hits()+"\t"+(summary.hits()>0?1:0)+"\t"+summary.maximumPossibleOperations()+"\t"+verification+"\n");
                }
            }
        }
        for(String name:mode.equals("find")?List.of("stage-targets.fa","stage-motifs.motif"):List.of("stage-targets.fa","stage-background.fa","stage-motifs.motif"))hashes.put(work.resolve(name),hash(work.resolve(name)));
        var data=new Data(sequences,models,presence,scanTotals[1],Math.addExact(scanTotals[0],scanTotals[2]),totals[1],totals[2],totals[3],totals[4],totals[5],overlap[0],overlap[1],hashes,strands,offset,limits,budget);
        data.verify();HomerRuntime.bound(work,limits.workspace);return data;
    }
    static long positive(Map<String,String> o,String key,long fallback)throws IOException{
        try{long value=o.containsKey(key)?Long.parseLong(o.get(key)):fallback;if(value<1)throw new NumberFormatException();return value;}
        catch(NumberFormatException failure){throw new IOException("Require positive integer --"+key,failure);}
    }
    static int integer(Map<String,String> o,String key,int fallback,int min,int max)throws IOException{
        long value=positive(o,key,fallback);if(value<min||value>max)throw new IOException("Out-of-range --"+key);return (int)value;
    }
    static final class Budget {
        private final long maximum;private long bytes;
        Budget(long maximum){this.maximum=maximum;}
        void add(long value)throws IOException{bytes=Math.addExact(bytes,value);if(bytes>maximum)throw new IOException("HOMER native input/audit byte budget exceeded");}
        BufferedWriter writer(Path path)throws IOException{
            var raw=Files.newOutputStream(path,java.nio.file.StandardOpenOption.CREATE_NEW);
            return new BufferedWriter(new OutputStreamWriter(new java.io.FilterOutputStream(raw){
                @Override public void write(int value)throws IOException{add(1);out.write(value);}
                @Override public void write(byte[] value,int start,int length)throws IOException{add(length);out.write(value,start,length);}
            },StandardCharsets.UTF_8));
        }
    }
}
