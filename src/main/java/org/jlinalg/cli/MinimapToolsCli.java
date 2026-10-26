/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.*;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.FastqIO;
import org.jlinalg.sequence.PafAlignments;
import org.jlinalg.sequence.ReadContentAudit;
import static org.jlinalg.cli.FollowupSupport.*;

/** Explicit minimap2 presets with native FASTQ/content and PAF geometry audits. */
final class MinimapToolsCli {
    private MinimapToolsCli() { }
    static int run(String[] args,PrintStream output,PrintStream error) {
        if (Arrays.asList(args).contains("--help")) { output.println(help()); return 0; }
        try {
            var o=options(args,Set.of("out","home","reference","query","query2","query-format","format","preset","secondary","md","eqx","cs","noncanonical-penalty","min-dp-score","threads","timeout","bed","index-batch-bases"));
            for (String key:List.of("home","reference","query","query-format","format","preset")) required(o,key);
            String format=required(o,"format"),queryFormat=required(o,"query-format"),preset=required(o,"preset");
            if (!Set.of("sam","paf").contains(format)||!Set.of("fastq","fasta").contains(queryFormat)||!Set.of("map-ont","map-hifi","map-pb","lr:hq","splice","splice:hq","sr","asm5","asm10","asm20").contains(preset)) throw new IllegalArgumentException("Invalid minimap2 output/query format or explicit preset");
            if (format.equals("sam")&&!queryFormat.equals("fastq")) throw new IllegalArgumentException("SAM output requires FASTQ; assembly FASTA queries use PAF");
            if (o.containsKey("query2")&&(!format.equals("sam")||!preset.equals("sr"))) throw new IllegalArgumentException("Paired queries require FASTQ, SAM output and preset sr");
            if (bool(o,"bed",false)&&!format.equals("paf")) throw new IllegalArgumentException("BED spans are only exported from PAF");
            for (String key:List.of("secondary","md","eqx")) bool(o,key,!key.equals("eqx"));
            if (!Set.of("none","short","long").contains(o.getOrDefault("cs","none"))) throw new IllegalArgumentException("cs must be none, short or long");
            if (o.containsKey("noncanonical-penalty")&&!preset.startsWith("splice")) throw new IllegalArgumentException("noncanonical-penalty only applies to splice presets");
            integer(o,"noncanonical-penalty",9,0,1_000_000); integer(o,"min-dp-score",40,0,Integer.MAX_VALUE);integer(o,"threads",1,1,64);integer(o,"timeout",3600,1,86400);
            int[] code={0};
            externalTransaction(o,"minimap2-align",(dir,manifest)->{
                var session=new LinkedHashMap<String,Object>(); var warnings=new ArrayList<String>();
                try {
                    align(o,dir,manifest,session,warnings);warnings.addAll(RnaToolRuntime.warnings(dir));manifest.put("analysis_status",warnings.isEmpty()?"completed":"completed-with-warnings");
                    if (!warnings.isEmpty()) Files.writeString(dir.resolve("WARNINGS.md"),"# MINIMAP2 WARNINGS\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: inspect the named mapping/reference/preset assumptions and raw alignments before downstream analysis.\n");
                } catch (Exception failure) {
                    code[0]=failure instanceof RnaToolRuntime.Unavailable?3:2;manifest.put("analysis_status",code[0]==3?"skipped-dependency-unavailable":"failed");manifest.put("error",String.valueOf(failure.getMessage()));RnaToolRuntime.quarantine(dir);
                    Files.writeString(dir.resolve("WARNINGS.md"),"# "+(code[0]==3?"ANALYSIS SKIPPED - MINIMAP2 UNAVAILABLE":"MINIMAP2 ANALYSIS FAILED")+"\n\n"+failure.getMessage()+"\n\nRequired action: "+(code[0]==3?RnaToolRuntime.setup("minimap2"):"Inspect retained logs and the exact input/option cause; correct it and rerun to a new output directory.")+" Partial artifacts are retained under partial/; no successful results.tsv is published.\n");
                }
                Files.writeString(dir.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));Files.writeString(dir.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n");
            });
            if (code[0]!=0||Files.exists(Path.of(o.get("out"),"WARNINGS.md"))) error.println((code[0]==3?"SKIPPED ANALYSIS":code[0]==2?"ANALYSIS FAILED":"ANALYSIS WARNINGS")+": see "+Path.of(o.get("out"),"WARNINGS.md"));
            if (code[0]==0) output.println("Minimap2 alignment completed: "+o.get("out"));return code[0];
        } catch (Exception failure) { error.println("jlinalg: "+failure.getMessage());return 2; }
    }
    private static void align(Map<String,String> o,Path dir,Map<String,Object> manifest,Map<String,Object> session,List<String> warnings) throws Exception {
        var runtime=new RnaToolRuntime(Path.of(o.get("home")),"minimap2",dir,session);
        Path reference=RnaToolRuntime.file(o.get("reference")),query=RnaToolRuntime.file(o.get("query")),query2=o.containsKey("query2")?RnaToolRuntime.file(o.get("query2")):null;
        var hashes=new LinkedHashMap<Path,String>();for (Path p:query2==null?List.of(reference,query):List.of(reference,query,query2)) hashes.put(p,hash(p));
        var targets=RnaToolRuntime.dictionary(reference);var queries=new LinkedHashMap<String,Long>();
        long total=0;for (long length:targets.values()) total=Math.addExact(total,length);
        long batch=Long.parseLong(o.getOrDefault("index-batch-bases",Long.toString(Math.addExact(total,1))));
        if (batch<1) throw new IllegalArgumentException("index-batch-bases must be positive");
        boolean split=batch<total;
        if (split&&(bool(o,"md",true)||!o.getOrDefault("cs","none").equals("none"))) throw new IllegalArgumentException("Upstream --split-prefix cannot be combined with MD/cs tags; use --md false --cs none for multi-part indexes or choose a batch holding the complete reference");
        Path staged1=dir.resolve("query1.fastq"),staged2=query2==null?null:dir.resolve("query2.fastq"); long inputCount;
        boolean sam=o.get("format").equals("sam");
        if (bool(o,"md",true)&&!o.getOrDefault("cs","none").equals("none")) warnings.add("DIFFERENCE TAG POLICY: minimap2 2.31 emits MD instead of cs when both --MD and --cs are requested. Set --md false to obtain the selected cs tag.");
        session.put("effective_difference_tag",bool(o,"md",true)?"MD":o.getOrDefault("cs","none").equals("none")?"none":"cs");
        try (var audit=sam?new ReadContentAudit(dir.resolve("read-audit"),100_000):null) {
            if (o.get("query-format").equals("fasta")) { queries.putAll(RnaToolRuntime.dictionary(query));inputCount=queries.size(); }
            else {
                var inspection=FastqIO.inspect(query,query2);inputCount=inspection.read1().reads();if (inputCount==0) throw new IOException("Input FASTQ is empty");
                stage(query,staged1,query2==null?0:1,audit,queries,!sam); if (query2!=null) stage(query2,staged2,2,audit,queries,false);
            }
            var command=new ArrayList<>(List.of(runtime.binary("minimap2"),"-x",o.get("preset"),sam?"-a":"-c","-t",Integer.toString(integer(o,"threads",1,1,64)),"--secondary="+(bool(o,"secondary",true)?"yes":"no"),"-I",Long.toString(batch)));
            if (split) command.addAll(List.of("--split-prefix",runtime.path(dir.resolve("split-index"),dir)));
            if (bool(o,"md",true)) command.add("--MD");if (bool(o,"eqx",false)) command.add("--eqx");
            if (!o.getOrDefault("cs","none").equals("none")) command.add("--cs="+o.get("cs"));
            if (o.containsKey("noncanonical-penalty")) command.addAll(List.of("-C",o.get("noncanonical-penalty")));
            if (o.containsKey("min-dp-score")) command.addAll(List.of("-s",o.get("min-dp-score")));
            command.add(runtime.path(reference,dir));command.add(runtime.path(o.get("query-format").equals("fasta")?query:staged1,dir));if (staged2!=null) command.add(runtime.path(staged2,dir));
            runtime.execute(command,dir,"alignment",dir.resolve(sam?"alignment.sam":"alignment.paf"),integer(o,"timeout",3600,1,86400));
            long mapped;
            if (sam) mapped=inspectSam(dir.resolve("alignment.sam"),targets,query2!=null,audit,session);else mapped=inspectPaf(dir,queries,targets,bool(o,"bed",false),session);
            if (mapped==0) warnings.add("ZERO MAPPED QUERIES: inspect the reference, query type and preset; this is not evidence of biological absence.");
            if (bool(o,"bed",false)&&o.get("preset").startsWith("splice")) warnings.add("BED SPANS INCLUDE INTRONS: mappings.bed exports complete target spans, not exon blocks. Use CIGAR-aware split-block processing for exon-only coverage.");
            session.put("preset",o.get("preset"));session.put("input_reads_or_pairs_or_sequences",inputCount);session.put("reference_dictionary",targets);session.put("scope","Explicit minimap2 2.31 preset/options and original reference; PAF base-alignment CIGAR always requested. No variant/SV calling, transcript quantification, assembly filtering, orthology or library trimming is inferred.");
            session.put("index_batch_bases",batch);session.put("multi_part_merge_requested",split);
            table(dir.resolve("results.tsv"),List.of("metric","value"),List.<String[]>of(new String[]{"input_reads_or_pairs_or_sequences",Long.toString(inputCount)},new String[]{sam?"mapped_primary_records":"queries_with_alignments",Long.toString(mapped)}));
        }
        Files.deleteIfExists(staged1);if (staged2!=null) Files.deleteIfExists(staged2);
        RegenieRuntime.verify(hashes);runtime.verify();var h=new LinkedHashMap<String,String>();hashes.forEach((p,s)->h.put(p.toString(),s));manifest.put("source_sha256",h);
    }
    private static void stage(Path input,Path output,int mate,ReadContentAudit audit,Map<String,Long> names,boolean unique) throws IOException {
        try (var in=new FastqIO.Reader(input);var out=new FastqIO.Writer(output)) {
            FastqIO.Read r;while ((r=in.next())!=null) {
                String bases=r.sequence().toUpperCase(Locale.ROOT);if (!bases.matches("[ACGTN]+")) throw new IOException("FASTQ adapter requires canonical A/C/G/T/N DNA; convert RNA U/IUPAC ambiguity explicitly upstream");
                if (unique && names.putIfAbsent(r.pairId(),(long)bases.length())!=null) throw new IOException("PAF requires unique query names; duplicate "+r.pairId());
                if (unique&&names.size()>5_000_000) throw new IOException("PAF query-name audit exceeds five million sequences; split inputs explicitly");
                if (audit!=null) audit.expected(r.pairId(),mate,bases,r.quality());out.write(new FastqIO.Read("@"+r.pairId()+(mate==0?"":"/"+mate),bases,r.quality()));
            }
        }
    }
    private static long inspectSam(Path path,Map<String,Long> targets,boolean paired,ReadContentAudit audit,Map<String,Object> session) throws Exception {
        long primary=0,mapped=0,secondary=0,supplementary=0;
        try (var reader=SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).open(path);var it=reader.iterator()) {
            var actual=new LinkedHashMap<String,Long>();for (var s:reader.getFileHeader().getSequenceDictionary().getSequences()) actual.put(s.getSequenceName(),(long)s.getSequenceLength());
            if (!actual.equals(targets)) throw new IOException("SAM dictionary differs from the exact reference");
            while (it.hasNext()) {
                var r=it.next();if (r.isSecondaryAlignment()) {secondary++;continue;}if (r.getSupplementaryAlignmentFlag()) {supplementary++;continue;}
                if (r.getReadPairedFlag()!=paired||paired&&r.getFirstOfPairFlag()==r.getSecondOfPairFlag()) throw new IOException("SAM pairing flags differ from query input");
                String bases=r.getReadString(),q=r.getBaseQualityString();if (r.getReadNegativeStrandFlag()) { bases=reverse(bases);q=new StringBuilder(q).reverse().toString(); }
                audit.observed(r.getReadName().replaceFirst("/[12]$",""),paired?(r.getFirstOfPairFlag()?1:2):0,bases,q);primary++;if (!r.getReadUnmappedFlag()) mapped++;
            }
        }
        audit.verify();session.put("primary_records",primary);session.put("mapped_primary_records",mapped);session.put("secondary_records",secondary);session.put("supplementary_records",supplementary);return mapped;
    }
    static String reverse(String bases) throws IOException {
        var b=new StringBuilder(bases.length());for (int i=bases.length()-1;i>=0;i--) b.append(switch(bases.charAt(i)){case 'A'->'T';case 'C'->'G';case 'G'->'C';case 'T'->'A';case 'N'->'N';default->throw new IOException("Noncanonical output sequence");});return b.toString();
    }
    private static long inspectPaf(Path dir,Map<String,Long> queries,Map<String,Long> targets,boolean bed,Map<String,Object> session) throws Exception {
        var seen=new HashSet<String>();long alignments=0;
        try (var reader=Files.newBufferedReader(dir.resolve("alignment.paf"));var output=Files.newBufferedWriter(dir.resolve("mappings.tsv"));var bedOut=bed?Files.newBufferedWriter(dir.resolve("mappings.bed")):null) {
            output.write("query\tquery_length\tquery_start\tquery_end\tstrand\ttarget\ttarget_length\ttarget_start\ttarget_end\tmatches\tblock_length\tmapq\ttype\tcigar\n");
            String line;while ((line=reader.readLine())!=null) {
                var a=PafAlignments.parse(line,queries,targets,true);seen.add(a.query());alignments++;
                output.write(a.query()+"\t"+a.queryLength()+"\t"+a.queryStart()+"\t"+a.queryEnd()+"\t"+a.strand()+"\t"+a.target()+"\t"+a.targetLength()+"\t"+a.targetStart()+"\t"+a.targetEnd()+"\t"+a.matches()+"\t"+a.blockLength()+"\t"+a.mapq()+"\t"+a.type()+"\t"+a.cigar()+"\n");
                if (bedOut!=null) bedOut.write(a.target()+"\t"+a.targetStart()+"\t"+a.targetEnd()+"\t"+a.query()+"\t"+a.mapq()+"\t"+a.strand()+"\n");
            }
        }
        try (var out=Files.newBufferedWriter(dir.resolve("query-status.tsv"))) { out.write("query\tlength\tstatus\n");for (var e:queries.entrySet()) out.write(e.getKey()+"\t"+e.getValue()+"\t"+(seen.contains(e.getKey())?"aligned":"unmapped")+"\n"); }
        session.put("paf_alignments",alignments);session.put("queries_with_alignments",seen.size());session.put("paf_coordinate_convention","zero-based half-open, forward query coordinates even on minus strand; complete target spans in BED");return seen.size();
    }
    static String help() { return """
        minimap-tools --home HOME --reference ref.fa --query FILE --query-format fastq|fasta
          --preset map-ont|map-hifi|map-pb|lr:hq|splice|splice:hq|sr|asm5|asm10|asm20
          --format sam|paf --out NEW_DIR [--query2 R2.fastq only with sr/SAM/FASTQ]
          --secondary true --md true --eqx false --cs none|short|long
          When MD and cs are both selected, upstream 2.31 emits MD only (prominent warning).
          [--noncanonical-penalty INTEGER only with splice presets] [--min-dp-score INTEGER]
          --threads 1 --timeout 3600 --bed false (PAF target-span BED6; introns included).
        HOME/bin/minimap2 must be 2.31-r1302. Canonical Phred+33 FASTQ is natively staged/audited;
          plain uncompressed FASTA assembly queries use PAF with exact sequence-length dictionaries.
          Reference is uncompressed FASTA; indexes are built by upstream per run with the selected
          preset. Default index batch holds the complete reference (needs sufficient RAM).
          --index-batch-bases INTEGER selects a smaller batch with --split-prefix merging; upstream
          then requires --md false --cs none. Existing .mmi files are not reused.
        SAM retains unmapped reads and audits every primary name/mate/base/quality, irrespective of
          output order. Secondary/supplementary alignments remain in the original SAM.
        PAF always requests -c, validates exact names/lengths and CIGAR geometry, and retains all raw
          tags. mappings.tsv and query-status.tsv include unmapped queries explicitly. FASTQ PAF
          requires unique names and at most five million queries. PAF output has no paired-mate contract.
        No assembly/SV/variant calling, paftools/k8, library trimming or transcript abundance estimation.
          Missing runtime skips 3; failures exit 2 with retained partial artifacts and actionable warnings.
        """; }
}
