/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.assembly.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Independent complete FASTA, scalar, spectrum and position-track replay. No model is fitted. */
final class MerquryEvaluationAudit {
    private MerquryEvaluationAudit() { }
    static Map<String,Object> run(Path root,MerquryRequest.Spec spec,Runnable check)throws IOException {
        var db=MerquryPreparationAudit.databases(root,spec,check);var read=db.get("reads.meryl");require(read!=null&&!read.isEmpty(),"A complete nonempty read database is required");
        int k=spec.settings().k();var cap=spec.capacity();Path work=root.resolve("engine/work");long cutoff=Long.parseLong(Files.readString(work.resolve("reads.filt")).trim());
        var effective=object(json(root.resolve("effective.json"),1048576));var inputs=list(effective.get("inputs"));int n=inputs.size()-1;
        List<String> qv=lines(work.resolve("result.qv"),cap.outputBytes()),complete=lines(work.resolve(spec.edition().equals("1.1")?"completeness.stats":"result.completeness.stats"),cap.outputBytes());
        require(qv.size()==(n==2?3:1)&&complete.size()==qv.size(),"Missing/extra global QV or completeness population");
        var assemblies=new ArrayList<Map<String,Long>>();var results=new ArrayList<MerquryAssessment.Assessment>();var expected=new TreeMap<String,Map<String,Long>>();expected.put("reads.meryl",read);
        long bases=0,records=0,decoded=0,ambiguous=0,trackWindows=0;var duplication=new ArrayList<Object>();
        for(int i=1;i<=n;i++) {
            String name="asm"+i;var item=object(inputs.get(i));Path fasta=root.resolve(member(text(item.get("captured"))));
            // Original 1.1 emits TDF rather than WIG; native BED checks still cover EVERY missing occurrence.
            Path wig=work.resolve(name+"_only.wig");
            try(var bed=Files.newBufferedReader(work.resolve(name+"_only.bed"),StandardCharsets.UTF_8);
                    var depth=spec.edition().equals("1.1")?null:Files.newBufferedReader(wig,StandardCharsets.UTF_8);
                    var input=Files.newInputStream(fasta)) {
                var counted=MerquryFastx.countFasta(input,k,read.keySet(),new MerquryFastx.Limits(cap.decodedBytes(),cap.lineCharacters(),cap.recordBases(),cap.records(),cap.bases(),cap.distinctKmers()),check,
                    (record,sequence)->tracks(record,sequence,k,spec.edition().equals("1.1"),read.keySet(),bed,depth,check));
                require(bed.readLine()==null&&(depth==null||depth.readLine()==null),"Extra original BED/WIG position or sequence");
                if(spec.edition().equals("1.1")) {
                    var fai=lines(work.resolve(text(item.get("staged"))+".fai"),cap.outputBytes());require(fai.size()==counted.sequences().size(),"Original FAI does not represent the complete FASTA record population");var ids=new HashSet<String>();
                    for(int row=0;row<fai.size();row++){var sequence=counted.sequences().get(row);String[] f=fai.get(row).split("\t",-1);require(f.length==5&&ids.add(sequence.id())&&f[0].equals(sequence.id())&&Long.parseLong(f[1])==sequence.bases(),"Original TDF reference dictionary differs from complete FASTA identities/order/lengths; repeated FAI identities are not deduplicated by this adapter");}
                }
                bases=Math.addExact(bases,counted.bases());records=Math.addExact(records,counted.sequences().size());decoded=Math.addExact(decoded,counted.decodedBytes());ambiguous=Math.addExact(ambiguous,counted.ambiguousBases());
                require(bases<=cap.bases()&&records<=cap.records()&&decoded<=cap.decodedBytes(),"Combined complete FASTA population exceeds capacity");
                var table=db.get(name+".meryl");require(table!=null,"Missing complete assembly Meryl database");MerquryTables.equalCounts(counted.counts(),table,"complete assembly "+name);expected.put(name+".meryl",table);assemblies.add(table);
                var result=MerquryAssessment.assess(read,table,k,cutoff,check);results.add(result);MerquryTables.quality(qv.get(i-1).split("\t",-1),name,result.quality());completeness(complete.get(i-1),name,result.completeness());
                MerquryTables.spectrum(work.resolve("result."+name+".spectra-cn.hist"),result.spectra(),check);only(work.resolve("result."+name+".only.hist"),List.of("1\t0\t"+result.assemblyOnlyDistinct(),"2\t0\t"+result.assemblyOnlyExtraOccurrences()),cap.outputBytes());
                var per=lines(work.resolve("result."+name+".qv"),cap.outputBytes());require(per.size()==counted.sequences().size(),"Missing/extra original per-sequence QV population");
                for(int row=0;row<per.size();row++) {
                    check.run();var sequence=counted.sequences().get(row);double error=1-Math.pow(1-(double)sequence.absentReadWindows()/sequence.validWindows(),1.0/k);
                    MerquryTables.quality(per.get(row).split("\t",-1),sequence.id(),new MerquryAssessment.Quality(sequence.absentReadWindows(),sequence.validWindows(),error,-10*Math.log(error)/Math.log(10)));
                    trackWindows=Math.addExact(trackWindows,sequence.absentReadWindows());
                }
                plots(work,"result."+name+".spectra-cn",check);
                if(spec.settings().falseDuplications())duplication.add(falseDup(root,name,result.spectra(),check));
            }
        }
        require(expected.keySet().equals(db.keySet()),"Missing/extra final evaluation Meryl database population");
        if(n==2) {
            var union=MerquryAssessment.unionSum(assemblies,k,check);var result=MerquryAssessment.assess(read,union,k,cutoff,check);
            MerquryTables.quality(qv.get(2).split("\t",-1),"Both",result.quality());completeness(complete.get(2),"both",result.completeness());MerquryTables.spectrum(work.resolve("result.spectra-cn.hist"),result.spectra(),check);
            only(work.resolve("result.only.hist"),List.of("1\t0\t"+result.assemblyOnlyDistinct(),"2\t0\t"+result.assemblyOnlyExtraOccurrences()),cap.outputBytes());plots(work,"result.spectra-cn",check);
        }
        var spectra=new TreeMap<String,Long>();long firstOnly=0,secondOnly=0,sharedOnly=0;
        for(var e:read.entrySet()) {
            check.run();boolean a=assemblies.get(0).containsKey(e.getKey()),b=n==2&&assemblies.get(1).containsKey(e.getKey());String group=n==1?(a?"asm1":"read-only"):a?(b?"shared":"asm1-only"):(b?"asm2-only":"read-only");spectra.merge(group+"\t"+e.getValue(),1L,Math::addExact);
        }
        for(String key:assemblies.get(0).keySet())if(!read.containsKey(key)){if(n==2&&assemblies.get(1).containsKey(key))sharedOnly++;else firstOnly++;}
        if(n==2)for(String key:assemblies.get(1).keySet())if(!read.containsKey(key)&&!assemblies.get(0).containsKey(key))secondOnly++;
        histogram(work.resolve("result.spectra-asm.hist"),"Assembly\tkmer_multiplicity\tCount",spectra,check);
        only(work.resolve("result.dist_only.hist"),n==1?List.of("asm1\t0\t"+firstOnly):List.of("asm1-only\t0\t"+firstOnly,"asm2-only\t0\t"+secondOnly,"shared\t0\t"+sharedOnly),cap.outputBytes());plots(work,"result.spectra-asm",check);
        return map("status","PASS_COMPLETE_EVALUATION_EQUATIONS_AND_POSITION_TRACKS","assemblies",n,"records",records,"bases",bases,"ambiguous_bases",ambiguous,"missing_occurrence_bed_intervals",trackWindows,"false_duplications",duplication,"tdf_requires_original_inspection",spec.edition().equals("1.1"),"scientific_model_refit",false,"full_workflow_admitted",false);
    }
    static List<String> lines(Path path,long maximum)throws IOException {require(Files.size(path)<=maximum,"Complete result exceeds byte capacity");return Files.readAllLines(path,StandardCharsets.UTF_8);}
    private static void only(Path path,List<String> expected,long maximum)throws IOException {require(lines(path,maximum).equals(expected),"Complete original assembly-only count population differs: "+path.getFileName());}
    private static void completeness(String line,String name,MerquryAssessment.Completeness expected)throws IOException {
        String[] f=line.split("\t",-1);require(f.length==5&&f[0].equals(name)&&f[1].equals("all")&&Long.parseLong(f[2])==expected.recoveredDistinct()&&Long.parseLong(f[3])==expected.solidReadDistinct(),"Changed original completeness population");MerquryTables.numbers(expected.percent(),f[4],name+" completeness");
    }
    static void histogram(Path path,String header,Map<String,Long> expected,Runnable check)throws IOException {
        var remaining=new HashMap<>(expected);
        try(var in=Files.newBufferedReader(path,StandardCharsets.US_ASCII)) {
            require(header.equals(in.readLine()),"Different original complete spectrum header");
            for(String line=in.readLine();line!=null;line=in.readLine()){check.run();String[] f=line.split("\t",-1);require(f.length==3,"Malformed complete spectrum row");Long value=remaining.remove(f[0]+"\t"+f[1]);require(value!=null&&value==MerquryTables.positive(f[2]),"Changed/extra complete spectrum bin");}
        }
        require(remaining.isEmpty(),"Missing original spectrum bins");
    }
    private static void tracks(MerquryFastx.Sequence record,String literal,int k,boolean legacyBed21,Set<String> read,BufferedReader bed,BufferedReader wig,Runnable check)throws IOException {
        int[] delta=wig==null?null:new int[Math.addExact(literal.length(),1)];var word=new StringBuilder(k);
        for(int position=0;position<literal.length();position++) {
            if((position&4095)==0)check.run();char c=Character.toUpperCase(literal.charAt(position));if("ACGT".indexOf(c)<0){word.setLength(0);continue;}word.append(c);if(word.length()<k)continue;
            int start=position-k+1;if(!read.contains(MerquryAssessment.canonical(word.toString()))) {
                require((record.id()+"\t"+start+"\t"+(legacyBed21?start+21:position+1)).equals(bed.readLine()),"Changed/missing complete original BED interval (record "+record.ordinal()+")");
                if(delta!=null){delta[start]=Math.addExact(delta[start],1);delta[position+1]=Math.subtractExact(delta[position+1],1);}
            }
            word.deleteCharAt(0);
        }
        if(wig!=null) {
            require(("variableStep chrom="+record.id()).equals(wig.readLine()),"Missing/reordered complete WIG sequence header");int depth=0;
            for(int i=0;i<literal.length();i++){if((i&4095)==0)check.run();depth=Math.addExact(depth,delta[i]);if(depth>0)require(((i+1)+"\t"+depth).equals(wig.readLine()),"Changed/missing complete original WIG depth");}
        }
    }
    static void plots(Path work,String prefix,Runnable check)throws IOException {
        for(String kind:List.of("ln","fl","st")) {
            check.run();Path path=work.resolve(prefix+"."+kind+".png");require(Files.size(path)>1024,"Missing/empty original plot: "+path.getFileName());
            var image=javax.imageio.ImageIO.read(path.toFile());require(image!=null&&image.getWidth()>=100&&image.getHeight()>=100,"Unreadable original plot");
            var colors=new HashSet<Integer>();for(int y=0;y<image.getHeight();y+=3)for(int x=0;x<image.getWidth();x+=3)colors.add(image.getRGB(x,y));require(colors.size()>32,"Blank or degenerate original plot");
        }
    }
    private static Map<String,Object> falseDup(Path root,String name,List<MerquryAssessment.Spectrum> spectra,Runnable check)throws IOException {
        var expected=MerquryAssessment.falseDuplications(spectra,check);require(expected.sourceWellDefined(),"Requested false-duplication rate is undefined in the source because copy classes are absent: "+expected.absentSourceFields()+". Retain this attempt; inspect spectra and choose an explicit scientifically appropriate analysis in a NEW request");
        return MerquryTables.falseDuplicationReport(lines(root.resolve("engine/commands/false-duplications-"+name.substring(3)+"-stdout"),1048576),name,expected);
    }
}
