/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jlinalg.sequence.HomerRnaCounts;
import org.jlinalg.sequence.HomerRnaExpression;
import org.jlinalg.sequence.HomerRnaTranscripts;

/** Complete reference expansion, independent counter audit and source expression population checks. */
final class HomerRnaQuantification {
    private HomerRnaQuantification() { }
    record Limits(int transcripts,int parts,int features,int chromosomeExons,int rows,int contributors) {
        static Limits read(Map<String,String> o,int samples,int tags)throws IOException {
            var l=new Limits(integer(o,"maximum-transcripts",200000,10000000),integer(o,"maximum-exon-parts",2000000,100000000),integer(o,"maximum-features-per-transcript",10000,100000),integer(o,"maximum-exons-per-chromosome",1000000,1000000),integer(o,"maximum-output-rows",2000000,10000000),integer(o,"maximum-contributors",10000,1000000));
            long estimate=Math.addExact(Math.addExact(Math.multiplyExact((long)l.parts,256L+8L*samples),Math.multiplyExact((long)l.transcripts,1024)),Math.multiplyExact((long)tags,64));
            if(estimate>Runtime.getRuntime().maxMemory()*0.70)throw new HomerRuntime.Unavailable("RNA configured reference/tag populations need at least "+((estimate*10+6)/7)+" bytes Java heap by the conservative intake estimate; choose explicit capacities/heap for the complete population");return l;
        }
    }
    static void run(Map<String,String> o,HomerRnaInputs.Data data,HomerRnaTags.Audited tags,Map<String,Object> session)throws Exception {
        var limits=Limits.read(o,data.replicates().size(),data.limits().chromosomeTags());Path work=data.work(),resource=data.runtime().genomeFile(o.get("genome"),o.get("genome")+".rna");
        data.check();String resourceHash=FollowupSupport.hash(resource);var transcripts=new ArrayList<HomerRnaTranscripts.Transcript>();var chromosomes=new LinkedHashMap<String,List<HomerRnaCounts.Exon>>();
        var partCounts=new HashMap<String,long[]>();HomerRnaTranscripts.Summary summary;
        try(var input=HomerCoreAudit.reader(resource);var rows=writer(work.resolve("transcripts.tsv"));var lines=writer(work.resolve("reference-lines.tsv"));var expanded=writer(work.resolve("native-expanded.tsv"));var exons=writer(work.resolve("exons.tsv"))) {
            rows.write("source_line\traw_id_json\texecution_id_json\tcondensed_id_json\tcontig\tstart1\tend1\tstrand\tsource_effective_length\texon_parts\tmissing_reference\toverrun\tempty_parts\tone_base_parts\n");lines.write("source_line\tdisposition\traw_line_json\n");exons.write("part_id\texecution_id\tcontig\tstart1\tend1\tstrand\tsource_effective_length\n");
            summary=HomerRnaTranscripts.scan(input,data.reference(),policy(o.get("reference-irregularities")),policy(o.get("short-exon-parts")),new HomerRnaTranscripts.Limits(limits.transcripts,limits.parts,limits.features,1000000,data.limits().decoded()),t->{
                transcripts.add(t);rows.write(t.lineNumber()+"\t"+RegenieRuntime.json(t.rawId())+"\t"+RegenieRuntime.json(t.executionId())+"\t"+RegenieRuntime.json(t.condensedId())+"\t"+t.contig()+"\t"+t.start1()+"\t"+t.end1()+"\t"+t.strand()+"\t"+t.sourceEffectiveLength()+"\t"+t.exons().size()+"\t"+t.missingReferenceContig()+"\t"+t.outsideReference()+"\t"+t.emptyExonParts()+"\t"+t.oneBaseExonParts()+"\n");
                for(var p:t.exons()) {
                    var f=p.feature();var group=chromosomes.computeIfAbsent(t.contig(),k->new ArrayList<>());if(group.size()>=limits.chromosomeExons)throw new IOException("Complete per-chromosome exon capacity exceeded");group.add(p.exon(t.strand()));
                    if(partCounts.putIfAbsent(p.id(),new long[data.replicates().size()])!=null)throw new IOException("Duplicate complete exon identity");
                    expanded.write(p.id()+"\t"+t.contig()+"\t"+f.start1()+"\t"+f.end1()+"\t"+t.strand()+"\tE:"+f.start1()+"\t0\t"+t.start1()+"\t"+t.end1()+"\n");
                    exons.write(p.id()+"\t"+t.executionId()+"\t"+t.contig()+"\t"+f.start1()+"\t"+f.end1()+"\t"+t.strand()+"\t"+p.effectiveLength()+"\n");
                }
            },(n,line,d)->lines.write(n+"\t"+d+"\t"+RegenieRuntime.json(line)+"\n"),data::check);
        }
        if(summary.transcripts()==0||summary.exonParts()==0||summary.exonParts()!=partCounts.size())throw new IOException("Empty or inconsistent complete RNA reference population");
        var pinned=new LinkedHashMap<Path,String>();pinned.put(resource,resourceHash);for(String name:List.of("native-expanded.tsv","transcripts.tsv","reference-lines.tsv","exons.tsv"))pinned.put(work.resolve(name),FollowupSupport.hash(work.resolve(name)));
        HomerRnaCli.step(data,"analyzeRepeats.pl",List.of("rna",o.get("genome"),"-count","exons"),"expanded",session);
        expansion(work.resolve("expanded.stdout"),work.resolve("native-expanded.tsv"),data);
        String strand=switch(o.get("relative-strand")){case "both"->"both";case "same"->"+";case "opposite"->"-";default->throw new IOException("Invalid relative strand");};
        var relative=switch(strand){case "both"->HomerRnaCounts.Strand.BOTH;case "+"->HomerRnaCounts.Strand.SAME;default->HomerRnaCounts.Strand.OPPOSITE;};long audited=0,departures=0,discarded=0,families=0;
        try(var ledger=writer(work.resolve("part-counts.tsv"));var boundaries=writer(work.resolve("boundary-families.tsv"))) {
            ledger.write("replicate\tpart_id\tliteral_half_units\tsource_interior_half_units\toriginal_half_units\n");boundaries.write("replicate\tcontig\tfamily_json\n");
            for(int sample=0;sample<data.replicates().size();sample++) {
                var replicate=data.replicates().get(sample);var role=tags.roles().get(replicate.alias());String label=HomerRnaCli.processLabel("parts",replicate.alias());
                HomerRnaCli.step(data,"getPeakTags",List.of("native-expanded.tsv",role.directory().toString(),"-fixed","-count","-strand",strand,"-tagAdjust","0","-tbp","0"),label,session);
                readPartCounts(work.resolve(label+".stdout"),partCounts,sample,data);pinned.put(work.resolve(label+".stdout"),FollowupSupport.hash(work.resolve(label+".stdout")));
                for(var entry:chromosomes.entrySet()) {
                    data.check();String contig=entry.getKey();var original=new HashMap<String,Double>();for(var exon:entry.getValue())original.put(exon.id(),partCounts.get(exon.id())[sample]/2.0);
                    Long length=data.reference().get(contig);
                    if(length==null) {
                        if(role.contigs().contains(contig))throw new IOException("RNA resource contig absent from reference but contains actual tags");
                        for(var exon:entry.getValue()){long units=partCounts.get(exon.id())[sample];if(units!=0)throw new IOException("Original missing-reference-contig count is nonzero");ledger.write(replicate.alias()+"\t"+exon.id()+"\t0\t0\t0\n");audited++;}continue;
                    }
                    var sourceTags=role.contigs().contains(contig)?HomerRnaTags.chromosome(data,role,contig):List.<org.jlinalg.sequence.HomerCoverage.Tag>of();
                    var result=HomerRnaCounts.count(sourceTags,entry.getValue(),relative,length,data.limits().chromosomeTags(),limits.chromosomeExons,data::check);HomerRnaCounts.audit(result,original);
                    for(var count:result.exons()){long units=partCounts.get(count.exon().id())[sample];if(units!=count.literalHalfUnits())departures++;ledger.write(replicate.alias()+"\t"+count.exon().id()+"\t"+count.literalHalfUnits()+"\t"+count.sourceInteriorHalfUnits()+"\t"+units+"\n");audited++;}
                    for(var family:result.boundaryFamilies()){boundaries.write(replicate.alias()+"\t"+contig+"\t"+RegenieRuntime.json(Map.of("id",family.id(),"members",family.members(),"half_units",family.halfUnits(),"discarded_half_units",family.discardedHalfUnits()))+"\n");families++;discarded=Math.addExact(discarded,family.discardedHalfUnits());}
                }
                ledger.flush();boundaries.flush();data.check();
            }
        }
        if(audited!=Math.multiplyExact(summary.exonParts(),data.replicates().size()))throw new IOException("RNA native part-count audit population incomplete");
        var mode=switch(o.get("condensation")){case "copy-families"->HomerRnaExpression.Mode.TRANSCRIPT_COPY_FAMILIES;case "distinct-transcripts"->HomerRnaExpression.Mode.DISTINCT_TRANSCRIPTS;case "exon-parts"->HomerRnaExpression.Mode.EXON_PARTS;default->throw new IOException("Invalid RNA condensation");};
        double[] masses=new double[data.replicates().size()];for(int s=0;s<masses.length;s++)masses[s]=tags.roles().get(data.replicates().get(s).alias()).halfUnits()/2.0;
        long finalRows=0,finalValues=0;var matrixHashes=new TreeMap<String,String>();
        for(String norm:o.get("normalization").equals("both")?List.of("raw","rpkm"):List.of(o.get("normalization"))) {
            var builder=new HomerRnaExpression.Builder(masses.length,mode,limits.transcripts,limits.parts,limits.rows,limits.contributors,data::check);
            for(var t:transcripts){var vectors=new ArrayList<long[]>();for(var p:t.exons())vectors.add(partCounts.get(p.id()));builder.add(t,vectors);}
            var expected=builder.finish(masses,norm.equals("raw")?HomerRnaExpression.Normalization.RAW:HomerRnaExpression.Normalization.RPKM);
            var arguments=new ArrayList<String>(List.of("rna",o.get("genome"),"-count","exons","-strand",strand,"-"+norm,"-d"));for(var replicate:data.replicates())arguments.add(tags.roles().get(replicate.alias()).directory().toString());
            if(mode!=HomerRnaExpression.Mode.TRANSCRIPT_COPY_FAMILIES)arguments.add("-noCondensing");if(mode==HomerRnaExpression.Mode.EXON_PARTS)arguments.add("-noCondensingParts");
            HomerRnaCli.step(data,"analyzeRepeats.pl",arguments,norm,session);matrix(expected,work.resolve(norm+".stdout"),data,tags,work.resolve("native-"+norm+".tsv"));
            finalRows+=expected.rows().size();finalValues+=Math.multiplyExact((long)expected.rows().size(),masses.length);matrixHashes.put(norm,FollowupSupport.hash(work.resolve(norm+".stdout")));matrixHashes.put("native-"+norm,FollowupSupport.hash(work.resolve("native-"+norm+".tsv")));
            if(!Files.exists(work.resolve("contributors.tsv")))contributors(expected,work.resolve("contributors.tsv"),data);
        }
        for(var entry:pinned.entrySet())if(!entry.getValue().equals(FollowupSupport.hash(entry.getKey())))throw new IOException("RNA audited input/output changed: "+entry.getKey());data.verify();tags.verify();data.runtime().verify();
        session.put("reference_transcripts",summary.transcripts());session.put("reference_exon_parts",summary.exonParts());session.put("reference_empty_parts",summary.emptyExonParts());session.put("reference_one_base_parts",summary.oneBaseExonParts());session.put("missing_reference_transcripts",summary.missingReferenceRows());session.put("outside_reference_transcripts",summary.outsideReferenceRows());session.put("audited_part_sample_counts",audited);session.put("literal_count_departures",departures);session.put("boundary_families",families);session.put("discarded_boundary_half_units",discarded);session.put("audited_final_rows",finalRows);session.put("audited_final_sample_values",finalValues);session.put("matrix_sha256",matrixHashes);session.put("whole_directory_masses",java.util.Arrays.stream(masses).boxed().toList());session.put("complete_native_audit","passed");
        var hashes=new TreeMap<String,String>();data.hashes().forEach((p,h)->hashes.put(p.toString(),h));pinned.forEach((p,h)->hashes.put(p.toString(),h));session.put("immutable_input_and_expansion_sha256",hashes);session.put("actual_tag_ledger_sha256",tags.actual().sha256());
    }
    private static void expansion(Path source,Path nativeFile,HomerRnaInputs.Data data)throws IOException {
        Path body=data.work().resolve("original-expanded.tsv");
        try(var in=Files.newInputStream(source);var out=Files.newOutputStream(body,StandardOpenOption.CREATE_NEW)) {
            var header=new StringBuilder();for(int c;(c=in.read())!=-1&&c!='\n';){if(header.length()>=1000000||c>127)throw new IOException("Original expansion header invalid");header.append((char)c);}if(!header.toString().startsWith("RepeatID (cmd="))throw new IOException("Original expansion header missing");
            byte[] buffer=new byte[1048576];for(int n;(n=in.read(buffer))!=-1;){out.write(buffer,0,n);data.check();}
        }
        if(Files.mismatch(body,nativeFile)!=-1)throw new IOException("Complete original RNA exon expansion differs from independent parser");
    }
    private static void readPartCounts(Path source,Map<String,long[]> expected,int sample,HomerRnaInputs.Data data)throws IOException {
        var seen=new java.util.HashSet<String>();try(var in=HomerCoreAudit.reader(source)){for(String text;(text=HomerCoreAudit.next(in,4096))!=null;){String[] c=text.split("\t",-1);if(c.length!=2||!seen.add(c[0]))throw new IOException("Original part-count shape/identity differs");long[] vector=expected.get(c[0]);if(vector==null)throw new IOException("Unexpected original part-count identity");vector[sample]=half(c[1]);if((seen.size()&4095)==0)data.check();}}if(seen.size()!=expected.size())throw new IOException("Incomplete original part-count population");
    }
    static long half(String text)throws IOException {
        try{double value=Double.parseDouble(text);long units=new BigDecimal(text).multiply(BigDecimal.valueOf(2)).longValueExact();if(!Double.isFinite(value)||units<0||units>(1L<<52)||value*2!=units)throw new NumberFormatException();return units;}catch(ArithmeticException|NumberFormatException failure){throw new IOException("RNA count is not an exact finite nonnegative half unit",failure);}
    }
    static String length(double value){return new BigDecimal(value).setScale(1,RoundingMode.HALF_EVEN).toPlainString();}
    private static void matrix(HomerRnaExpression.Table table,Path source,HomerRnaInputs.Data data,HomerRnaTags.Audited tags,Path target)throws IOException {
        var expected=new HashMap<String,HomerRnaExpression.Row>();for(var row:table.rows())if(expected.put(row.id(),row)!=null)throw new IOException("Duplicate native matrix row");
        try(var in=HomerCoreAudit.reader(source);var out=writer(target)) {
            String header=HomerCoreAudit.next(in,1000000);if(header==null)throw new IOException("Missing original RNA matrix header");String[] fields=header.split("\t",-1);if(fields.length!=8+table.samples()||!fields[0].startsWith("Transcript/RepeatID (cmd="))throw new IOException("Original RNA matrix header differs");
            for(int s=0;s<table.samples();s++){var r=data.replicates().get(s);var role=tags.roles().get(r.alias());sampleHeader(fields[8+s],role.directory(),table.normalization(),role.halfUnits()/2.0);}
            out.write("row_id\tsource_effective_length\tcopies");for(var r:data.replicates())out.write("\t"+r.alias());out.write('\n');long seen=0;
            for(String text;(text=HomerCoreAudit.next(in,1000000))!=null;) {
                String[] c=text.split("\t",-1);if(c.length!=8+table.samples())throw new IOException("Original RNA matrix row width differs");var row=expected.remove(c[0]);if(row==null)throw new IOException("Unexpected/duplicate original matrix identity: "+c[0]);
                if(!length(row.effectiveLength()).equals(c[5])||!Integer.toString(row.copies()).equals(c[6]))throw new IOException("RNA effective length/copy population differs: "+c[0]);
                boolean coordinates=false,strand=false;for(var member:row.contributors()){if(member.contig().equals(c[1])&&Integer.toString(member.start1()).equals(c[2])&&Integer.toString(member.end1()).equals(c[3]))coordinates=true;if(Character.toString(member.strand()).equals(c[4]))strand=true;}
                if(!coordinates||!strand)throw new IOException("Original metadata lies outside complete contributor families: "+c[0]);out.write(row.id()+"\t"+length(row.effectiveLength())+"\t"+row.copies());
                for(int s=0;s<table.samples();s++){if(!row.printedValues().get(s).equals(c[8+s]))throw new IOException("RNA original raw/RPKM value differs: "+c[0]+",sample"+s);out.write("\t"+row.printedValues().get(s));}out.write('\n');if((++seen&4095)==0){out.flush();data.check();}
            }
            if(!expected.isEmpty())throw new IOException("Original RNA matrix omits complete reference rows");
        }
    }
    private static void contributors(HomerRnaExpression.Table table,Path target,HomerRnaInputs.Data data)throws IOException {
        try(var out=writer(target)){out.write("row_id\texecution_id\traw_id_json\tcontig\tstart1\tend1\tstrand\tsource_effective_length\thalf_counts_json\n");long n=0;for(var row:table.rows())for(var c:row.contributors()){out.write(row.id()+"\t"+c.executionId()+"\t"+RegenieRuntime.json(c.rawId())+"\t"+c.contig()+"\t"+c.start1()+"\t"+c.end1()+"\t"+c.strand()+"\t"+c.effectiveLength()+"\t"+RegenieRuntime.json(c.halfCounts())+"\n");if((++n&4095)==0){out.flush();data.check();}}}
    }
    static void sampleHeader(String actual,Path directory,HomerRnaExpression.Normalization normalization,double mass)throws IOException {
        if(!Double.isFinite(mass)||mass<=0)throw new IOException("Invalid audited RNA sample mass");
        String expected=directory+(normalization==HomerRnaExpression.Normalization.RAW?" ("+String.format(java.util.Locale.ROOT,"%.1f",mass)+" total)":" FPKM");
        if(!expected.equals(actual))throw new IOException("RNA output sample order/identity/normalization differs; expected="+expected+", actual="+actual);
    }
    private static java.io.BufferedWriter writer(Path path)throws IOException{return Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);}
    private static HomerRnaTranscripts.Policy policy(String text){return text.equals("error")?HomerRnaTranscripts.Policy.ERROR:HomerRnaTranscripts.Policy.RETAIN_ORIGINAL;}
    private static int integer(Map<String,String> o,String key,int fallback,int maximum)throws IOException{return FollowupSupport.integer(o,key,fallback,1,maximum);}
}
