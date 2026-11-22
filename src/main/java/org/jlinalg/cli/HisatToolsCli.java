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
import java.util.Objects;
import java.util.Set;
import org.jlinalg.sequence.FastqIO;
import org.jlinalg.sequence.GeneFeatures;
import org.jlinalg.sequence.ReadContentAudit;
import static org.jlinalg.cli.FollowupSupport.*;

/** HISAT2 linear/annotated graph indexes and splice-aware RNA alignment with sealed inputs. */
final class HisatToolsCli {
    private HisatToolsCli() { }
    private static final Set<String> COMMON=Set.of("method","out","home","threads","timeout","seed");
    private static final Set<String> INDEX=Set.of("reference","annotation","index-mode");
    private static final Set<String> ALIGN=Set.of("index","reads","reads2","sample","read-group","library","strand","min-intron","max-intron","dta","softclip","spliced","max-alignments","score-intercept","score-slope","known-splicesites","mixed","discordant","min-fragment","max-fragment","novel-splice-learning");
    static int run(String[] args,PrintStream output,PrintStream error) {
        if(Arrays.asList(args).contains("--help")){output.println(help());return 0;}
        try {
            var allowed=new HashSet<>(COMMON);allowed.addAll(INDEX);allowed.addAll(ALIGN);var o=options(args,allowed);String method=required(o,"method");
            if(!Set.of("index","align").contains(method))throw new IllegalArgumentException("hisat-tools method must be index or align");
            var applicable=new HashSet<>(COMMON);applicable.addAll(method.equals("index")?INDEX:ALIGN);
            for(String key:o.keySet())if(!applicable.contains(key))throw new IllegalArgumentException("--"+key+" does not apply to "+method);
            required(o,"home");integer(o,"threads",1,1,64);integer(o,"timeout",3600,1,86400);integer(o,"seed",1,0,Integer.MAX_VALUE);
            if(method.equals("index"))required(o,"reference");else validateAlignment(o);
            int[] code={0};
            externalTransaction(o,"hisat2-"+method,(dir,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();
                try {
                    var runtime=new RnaToolRuntime(Path.of(o.get("home")),"hisat2",dir,session);
                    if(method.equals("index"))build(o,dir,manifest,session,runtime);else align(o,dir,manifest,session,runtime,warnings);
                    runtime.verify();warnings.addAll(RnaToolRuntime.warnings(dir));manifest.put("analysis_status",warnings.isEmpty()?"completed":"completed-with-warnings");
                    if(!warnings.isEmpty())Files.writeString(dir.resolve("WARNINGS.md"),"# HISAT2 WARNINGS\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: inspect the explicit reference, strand, splice learning and mapping settings before downstream analysis. SAM is alignment output; use a separately validated quantifier for counts.\n");
                }catch(Exception failure){
                    code[0]=failure instanceof RnaToolRuntime.Unavailable?3:2;manifest.put("analysis_status",code[0]==3?"skipped-dependency-unavailable":"failed");manifest.put("error",String.valueOf(failure.getMessage()));RnaToolRuntime.quarantine(dir);
                    Files.writeString(dir.resolve("WARNINGS.md"),"# "+(code[0]==3?"ANALYSIS SKIPPED - HISAT2 UNAVAILABLE":"HISAT2 ANALYSIS FAILED")+"\n\n"+failure.getMessage()+"\n\nRequired action: "+(code[0]==3?RnaToolRuntime.setup("hisat2"):"Inspect the named input/index/option cause and retained logs. Correct it and rerun to a new output directory.")+" Partial artifacts are retained under partial/; no successful results.tsv is published.\n");
                }
                Files.writeString(dir.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));Files.writeString(dir.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n");
            });
            if(code[0]!=0||Files.exists(Path.of(o.get("out"),"WARNINGS.md")))error.println((code[0]==3?"SKIPPED ANALYSIS":code[0]==2?"ANALYSIS FAILED":"ANALYSIS WARNINGS")+": see "+Path.of(o.get("out"),"WARNINGS.md"));if(code[0]==0)output.println("HISAT2 operation completed: "+o.get("out"));return code[0];
        }catch(Exception failure){error.println("jlinalg: "+failure.getMessage());return 2;}
    }
    private static void validateAlignment(Map<String,String> o){
        for(String key:List.of("index","reads","sample"))required(o,key);
        for(String key:List.of("sample","read-group","library"))if(o.containsKey(key)&&!o.get(key).matches("[A-Za-z0-9_.-]{1,128}"))throw new IllegalArgumentException("Sample/read-group/library IDs require 1..128 letters/digits/underscore/dot/hyphen");
        var strands=o.containsKey("reads2")?Set.of("unstranded","FR","RF"):Set.of("unstranded","F","R");if(!strands.contains(o.getOrDefault("strand","unstranded")))throw new IllegalArgumentException("strand must be unstranded, single F|R or paired FR|RF");
        if(integer(o,"max-intron",500000,1,Integer.MAX_VALUE)<integer(o,"min-intron",20,1,Integer.MAX_VALUE))throw new IllegalArgumentException("max-intron must be >= min-intron");
        if(integer(o,"max-fragment",500,0,10_000_000)<integer(o,"min-fragment",0,0,10_000_000))throw new IllegalArgumentException("max-fragment must be >= min-fragment");
        if(!o.containsKey("reads2")&&o.keySet().stream().anyMatch(Set.of("mixed","discordant","min-fragment","max-fragment")::contains))throw new IllegalArgumentException("Paired alignment options require reads2");
        integer(o,"max-alignments",10,1,100000);number(o,"score-intercept",0);double slope=number(o,"score-slope",-.2);if(slope>0)throw new IllegalArgumentException("score-slope must be nonpositive");
        for(String key:List.of("dta","softclip","spliced","mixed","discordant","novel-splice-learning"))bool(o,key,!key.equals("dta"));
        if(!bool(o,"spliced",true)&&o.keySet().stream().anyMatch(Set.of("strand","min-intron","max-intron","dta","known-splicesites","novel-splice-learning")::contains))throw new IllegalArgumentException("Splice options do not apply with --spliced false");
    }
    private static void build(Map<String,String> o,Path dir,Map<String,Object> manifest,Map<String,Object> session,RnaToolRuntime runtime)throws Exception{
        Path ref=RnaToolRuntime.file(o.get("reference"));var hashes=new LinkedHashMap<Path,String>();hashes.put(ref,hash(ref));var dict=RnaToolRuntime.dictionary(ref);long bases=0;for(long length:dict.values())bases=Math.addExact(bases,length);
        String mode=o.getOrDefault("index-mode","auto");if(!Set.of("auto","small","large").contains(mode))throw new IllegalArgumentException("index-mode must be auto, small or large");if(mode.equals("auto"))mode=bases>=4_000_000_000L?"large":"small";if(mode.equals("small")&&bases>=4_000_000_000L)throw new IllegalArgumentException("Small HISAT2 indexes require fewer than four billion reference bases");
        var command=new ArrayList<>(List.of(runtime.checkedBinary("hisat2-build-"+(mode.equals("small")?"s":"l"),dir),"--wrapper","jlinalg-typed-1","-p",o.getOrDefault("threads","1"),"--seed",o.getOrDefault("seed","1")));
        boolean annotated=o.containsKey("annotation");
        if(annotated){
            Path gtf=RnaToolRuntime.file(o.get("annotation"));hashes.put(gtf,hash(gtf));var a=GeneFeatures.read(gtf,"GTF",Set.of("exon"),"gene_id",dict);
            var sites=GeneFeatures.spliceSites(a,5);GeneFeatures.writeSpliceSites(sites,dir.resolve("known-splicesites.tsv"));GeneFeatures.writeIndexExons(a,dir.resolve("index-exons.tsv"));
            command.addAll(List.of("--ss",runtime.path(dir.resolve("known-splicesites.tsv"),dir),"--exon",runtime.path(dir.resolve("index-exons.tsv"),dir)));session.put("annotated_splice_sites",sites.size());
        }
        command.add(listPath(runtime,ref,dir));command.add(runtime.path(dir.resolve("reference"),dir));runtime.execute(command,dir,"index",null,integer(o,"timeout",3600,1,86400));
        var files=new LinkedHashMap<String,String>();for(String name:indexFiles(mode))files.put(name,hash(RnaToolRuntime.file(dir.resolve(name).toString())));
        if(annotated)for(String name:List.of("known-splicesites.tsv","index-exons.tsv"))files.put(name,hash(dir.resolve(name)));
        var metadata=new LinkedHashMap<String,Object>();metadata.put("schema_version",1);metadata.put("engine","hisat2");metadata.put("version","2.2.3");metadata.put("mode",mode);metadata.put("prefix","reference");metadata.put("reference_sha256",hashes.get(ref));metadata.put("reference_sequences",dict);metadata.put("annotated",annotated);metadata.put("index_sha256",files);
        Files.writeString(dir.resolve("hisat-index.json"),RegenieRuntime.json(metadata)+"\n");RegenieRuntime.verify(hashes);manifest.put("source_sha256",strings(hashes));session.put("direct_binary_contract","Native gzip staging and explicit small/large family replace wrapper dispatch; no SRA, archive streaming, shell decompression or wrapper-only filters");
        table(dir.resolve("results.tsv"),List.of("metric","value"),List.<String[]>of(new String[]{"reference_bases",Long.toString(bases)},new String[]{"reference_sequences",Integer.toString(dict.size())},new String[]{"index_family",mode},new String[]{"annotated",Boolean.toString(annotated)}));
    }
    private static List<String> indexFiles(String mode){var names=new ArrayList<String>();for(int i=1;i<=8;i++)names.add("reference."+i+"."+(mode.equals("small")?"ht2":"ht2l"));return names;}
    private record Index(String mode,Map<String,Long> dictionary,boolean annotated,Map<Path,String> hashes){}
    private static Index index(Path directory)throws Exception{
        Path metadata=RnaToolRuntime.file(directory.resolve("hisat-index.json").toString());var m=RegenieRuntime.object(metadata);
        if(!(m.get("schema_version") instanceof Number n)||n.intValue()!=1||!Objects.equals(m.get("engine"),"hisat2")||!Objects.equals(m.get("version"),"2.2.3")||!Objects.equals(m.get("prefix"),"reference")||!(m.get("annotated") instanceof Boolean annotated)||!(m.get("reference_sha256") instanceof String sha)||!sha.matches("[0-9a-f]{64}"))throw new IOException("Invalid HISAT2 sealed index identity/schema");
        String mode=String.valueOf(m.get("mode"));if(!Set.of("small","large").contains(mode)||!(m.get("index_sha256") instanceof Map<?,?> files)||!(m.get("reference_sequences") instanceof Map<?,?> sequences)||sequences.isEmpty())throw new IOException("Invalid HISAT2 index family/dictionary");
        var expected=new HashSet<>(indexFiles(mode));if(annotated)expected.addAll(List.of("known-splicesites.tsv","index-exons.tsv"));if(!files.keySet().equals(expected))throw new IOException("HISAT2 index component family changed");
        var hashes=new LinkedHashMap<Path,String>();hashes.put(metadata,hash(metadata));for(var e:files.entrySet()){String value=String.valueOf(e.getValue());if(!value.matches("[0-9a-f]{64}"))throw new IOException("Invalid index component hash");hashes.put(RegenieRuntime.contained(directory,e.getKey().toString()),value);}RegenieRuntime.verify(hashes);
        var dictionary=new LinkedHashMap<String,Long>();for(var e:sequences.entrySet()){if(!(e.getValue() instanceof Number length)||length.longValue()<1||length.longValue()>Integer.MAX_VALUE||length.doubleValue()!=length.longValue())throw new IOException("Invalid index reference length");dictionary.put(e.getKey().toString(),length.longValue());}
        return new Index(mode,dictionary,annotated,hashes);
    }
    private static void align(Map<String,String> o,Path dir,Map<String,Object> manifest,Map<String,Object> session,RnaToolRuntime runtime,List<String> warnings)throws Exception{
        Path bundle=Path.of(o.get("index")).toAbsolutePath().normalize();var index=index(bundle);var hashes=new LinkedHashMap<Path,String>(index.hashes());
        Path r1=RnaToolRuntime.file(o.get("reads")),r2=o.containsKey("reads2")?RnaToolRuntime.file(o.get("reads2")):null;hashes.put(r1,hash(r1));if(r2!=null)hashes.put(r2,hash(r2));
        var inspection=FastqIO.inspect(r1,r2);if(inspection.read1().reads()==0)throw new IOException("Input FASTQ is empty");
        Path a=dir.resolve("reads1.fastq"),b=r2==null?null:dir.resolve("reads2.fastq");
        try(var audit=new ReadContentAudit(dir.resolve("read-audit"),100_000)){
            stage(r1,a,r2==null?0:1,audit);if(r2!=null)stage(r2,b,2,audit);
            var command=new ArrayList<>(List.of(runtime.checkedBinary("hisat2-align-"+(index.mode().equals("small")?"s":"l"),dir),"--wrapper","jlinalg-typed-1","-x",runtime.path(bundle.resolve("reference"),dir),"-q","--phred33","--reorder","-p",o.getOrDefault("threads","1"),"--seed",o.getOrDefault("seed","1"),"-k",o.getOrDefault("max-alignments","10"),"--score-min","L,"+Double.toString(number(o,"score-intercept",0))+","+Double.toString(number(o,"score-slope",-.2)),"--rg-id",o.getOrDefault("read-group","JL1"),"--rg","SM:"+o.get("sample"),"--rg","PL:ILLUMINA","--novel-splicesite-outfile",runtime.path(dir.resolve("novel-splicesites.tsv"),dir),"--summary-file",runtime.path(dir.resolve("alignment-summary.txt"),dir),"--new-summary","-S",runtime.path(dir.resolve("alignment.sam"),dir)));
            if(o.containsKey("library"))command.addAll(List.of("--rg","LB:"+o.get("library")));
            if(r2==null)command.addAll(List.of("-U",listPath(runtime,a,dir)));else{command.addAll(List.of("-1",listPath(runtime,a,dir),"-2",listPath(runtime,b,dir),"-I",o.getOrDefault("min-fragment","0"),"-X",o.getOrDefault("max-fragment","500")));if(!bool(o,"mixed",true))command.add("--no-mixed");if(!bool(o,"discordant",true))command.add("--no-discordant");}
            if(bool(o,"spliced",true)){
                command.addAll(List.of("--min-intronlen",o.getOrDefault("min-intron","20"),"--max-intronlen",o.getOrDefault("max-intron","500000")));
                if(!o.getOrDefault("strand","unstranded").equals("unstranded"))command.addAll(List.of("--rna-strandness",o.get("strand")));
                if(bool(o,"dta",false))command.add("--dta");if(!bool(o,"novel-splice-learning",true))command.add("--no-temp-splicesite");
                Path known=o.containsKey("known-splicesites")?RnaToolRuntime.file(o.get("known-splicesites")):index.annotated()?bundle.resolve("known-splicesites.tsv"):null;
                if(known!=null){hashes.put(known,hash(known));validateSites(known,index.dictionary());command.addAll(List.of("--known-splicesite-infile",runtime.path(known,dir)));}
            }else command.add("--no-spliced-alignment");
            if(!bool(o,"softclip",true))command.add("--no-softclip");runtime.execute(command,dir,"alignment",null,integer(o,"timeout",3600,1,86400));
            inspect(dir.resolve("alignment.sam"),index.dictionary(),r2!=null,o.getOrDefault("read-group","JL1"),o.get("sample"),audit,session);audit.verify();
        }
        Files.delete(a);if(b!=null)Files.delete(b);RegenieRuntime.verify(hashes);manifest.put("source_sha256",strings(hashes));
        session.put("scope","HISAT2 2.2.3 RNA alignment; canonical Phred+33 FASTQ staged natively; direct small/large binaries, explicit linear score function and complete primary content audit. No HISAT-genotype SNP/haplotype graph, SRA wrapper, CIRIquant pseudo-circle construction, StringTie assembly or quantification.");
        if(bool(o,"spliced",true)&&bool(o,"novel-splice-learning",true)&&integer(o,"threads",1,1,64)>1)warnings.add("SPLICE LEARNING: newly discovered splice sites may be reused during alignment. Parallel scheduling can change this learning; use one thread or explicitly disable novel-splice-learning when that behavior matters.");
        if(Long.valueOf(0).equals(session.get("mapped_primary_records")))warnings.add("ZERO MAPPED PRIMARY READS: check reference, strand, library preprocessing and alignment settings; this does not establish biological absence.");
        var rows=new ArrayList<String[]>();for(String key:List.of("primary_records","mapped_primary_records","secondary_records","supplementary_records"))rows.add(new String[]{key,session.get(key).toString()});table(dir.resolve("results.tsv"),List.of("metric","value"),rows);
    }
    private static void validateSites(Path path,Map<String,Long> dictionary)throws IOException{
        try(var reader=Files.newBufferedReader(path)){String line;while((line=reader.readLine())!=null){String[] f=line.split("\t",-1);if(f.length!=4)throw new IOException("Known splice sites require four tab-delimited columns");long left=Long.parseLong(f[1]),right=Long.parseLong(f[2]);Long length=dictionary.get(f[0]);if(length==null||left<0||right<=left+1||right>=length||!Set.of("+","-").contains(f[3]))throw new IOException("Known splice site violates reference or zero-based exonic-flank contract");}}
    }
    private static String listPath(RnaToolRuntime runtime,Path path,Path directory)throws Exception{
        String value=runtime.path(path,directory);if(value.contains(","))throw new IOException("HISAT2 treats commas in reference/read paths as file-list separators. Choose reference/output paths without commas; original files were preserved.");return value;
    }
    private static void stage(Path source,Path target,int mate,ReadContentAudit audit)throws IOException{
        try(var in=new FastqIO.Reader(source);var out=new FastqIO.Writer(target)){FastqIO.Read r;while((r=in.next())!=null){String bases=r.sequence().toUpperCase(Locale.ROOT);if(!bases.matches("[ACGTN]+"))throw new IOException("FASTQ adapter requires canonical A/C/G/T/N DNA");audit.expected(r.pairId(),mate,bases,r.quality());out.write(new FastqIO.Read("@"+r.pairId()+(mate==0?"":"/"+mate),bases,r.quality()));}}
    }
    private static void inspect(Path sam,Map<String,Long> expected,boolean paired,String group,String sample,ReadContentAudit audit,Map<String,Object> session)throws Exception{
        long primary=0,mapped=0,secondary=0,supplementary=0;
        try(var reader=SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).open(sam);var it=reader.iterator()){
            var dict=new LinkedHashMap<String,Long>();for(var s:reader.getFileHeader().getSequenceDictionary().getSequences())dict.put(s.getSequenceName(),(long)s.getSequenceLength());if(!dict.equals(expected))throw new IOException("HISAT2 changed the reference dictionary");var rg=reader.getFileHeader().getReadGroup(group);if(rg==null||!sample.equals(rg.getSample()))throw new IOException("HISAT2 changed the sample/read group");
            while(it.hasNext()){var r=it.next();if(r.isSecondaryAlignment()){secondary++;continue;}if(r.getSupplementaryAlignmentFlag()){supplementary++;continue;}if(r.getReadPairedFlag()!=paired||paired&&r.getFirstOfPairFlag()==r.getSecondOfPairFlag()||!group.equals(r.getStringAttribute("RG")))throw new IOException("HISAT2 changed pairing/read-group flags");String bases=r.getReadString(),q=r.getBaseQualityString();if(r.getReadNegativeStrandFlag()){bases=MinimapToolsCli.reverse(bases);q=new StringBuilder(q).reverse().toString();}audit.observed(r.getReadName().replaceFirst("/[12]$",""),paired?(r.getFirstOfPairFlag()?1:2):0,bases,q);primary++;if(!r.getReadUnmappedFlag())mapped++;}
        }
        session.put("primary_records",primary);session.put("mapped_primary_records",mapped);session.put("secondary_records",secondary);session.put("supplementary_records",supplementary);
    }
    private static Map<String,String> strings(Map<Path,String> hashes){var map=new LinkedHashMap<String,String>();hashes.forEach((p,h)->map.put(p.toString(),h));return map;}
    static String help(){return """
        hisat-tools --method index|align --home HOME --out NEW_DIR
        HOME/bin contains pinned HISAT2 2.2.3 align-s/l, build-s/l and runtime libraries.
        index: --reference ref.fa [--annotation exons.gtf --index-mode auto|small|large]
          Uncompressed reference; exact dictionary and SHA-256 retained. Native transcript exon
          helpers produce known splice sites and genomic exon unions for upstream --ss/--exon
          graph construction. Small indexes require <4 billion bases; auto selects the family.
          All eight .ht2/.ht2l files and annotation helpers are sealed in hisat-index.json.
        align: --index INDEX_BUNDLE --reads R1.fastq[.gz] [--reads2 R2.fastq[.gz]] --sample ID
          --read-group JL1 [--library ID] --strand unstranded|F|R (single) or unstranded|FR|RF (paired)
          --min-intron 20 --max-intron 500000 --dta false --softclip true --spliced true
          --max-alignments 10 --score-intercept 0 --score-slope -0.2 (explicit linear function)
          [--known-splicesites FILE: chrom, zero-based LEFT and RIGHT EXONIC flanks, +/- strand]
          Annotated indexes supply their sealed splice list unless an explicit list is supplied.
          --novel-splice-learning true; learned sites retained in novel-splicesites.tsv.
          paired: --mixed true --discordant true --min-fragment 0 --max-fragment 500.
        Both: --threads 1 --seed 1 --timeout 3600. Native strict canonical A/C/G/T/N Phred+33
          FASTQ/pair validation and gzip staging replace shell/wrapper dispatch. Each primary
          name/mate/base/quality and exact reference/RG are audited; all alignments remain in SAM.
          Explicit -k=1 does NOT establish uniqueness; inspect NH/secondary alignments and MAPQ.
          No trimming, quantification, transcript assembly, SRA input, SNP/haplotype graph indexing
          or pseudo-circular reference construction. Missing runtime skips 3; failures exit 2 with
          actionable WARNINGS.md and quarantined partial artifacts.
        """;}
}
