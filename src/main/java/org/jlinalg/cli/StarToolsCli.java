/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.*;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jlinalg.sequence.FastaIndexes;
import org.jlinalg.sequence.FastqIO;
import org.jlinalg.sequence.GtfAnnotations;
import org.jlinalg.sequence.ReadContentAudit;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed STAR RNA alignment with sealed reference/annotation indices and audited native inputs. */
final class StarToolsCli {
    private StarToolsCli() { }
    private static final String VERSION="2.7.11b";
    private static final Set<String> COMMON=Set.of("method","out");
    private static final Set<String> RUNTIME=Set.of("star","threads","timeout");
    private static final Set<String> INDEX=Set.of("reference","gtf","sjdb-overhang","sa-index-bases","chr-bin-bits","memory-mb");
    private static final Set<String> ALIGN=Set.of("index","reads","reads2","sample","read-group","library","gene-counts","two-pass","sj-junctions","sjdb-overhang",
        "ends","filter-type","intron-motifs","multimap-max","match-min","score-min","match-fraction","score-fraction","mismatch-max","intron-min","intron-max","unmapped-fastq","seed");
    private static final Set<String> CORE=Set.of("Genome","SA","SAindex","genomeParameters.txt","chrName.txt","chrLength.txt","chrStart.txt","chrNameLength.txt");
    private static final Set<String> ANNOTATION=Set.of("geneInfo.tab","transcriptInfo.tab","exonInfo.tab","exonGeTrInfo.tab","sjdbInfo.txt","sjdbList.out.tab","sjdbList.fromGTF.out.tab");

    static int run(String[] args,PrintStream out,PrintStream err) {
        if(Arrays.asList(args).contains("--help")){out.println(help());return 0;}
        try {
            var allowed=new HashSet<>(COMMON);allowed.addAll(RUNTIME);allowed.addAll(INDEX);allowed.addAll(ALIGN);
            Map<String,String> o=options(args,allowed);String method=required(o,"method");
            if(!Set.of("index","align","inspect-gtf").contains(method))throw new IllegalArgumentException("Unknown star-tools method");
            var applicable=new HashSet<>(COMMON);
            if(!method.equals("inspect-gtf"))applicable.addAll(RUNTIME);
            applicable.addAll(method.equals("align")?ALIGN:method.equals("index")?INDEX:Set.of("reference","gtf"));
            for(String key:o.keySet())if(!applicable.contains(key))throw new IllegalArgumentException("--"+key+" does not apply to "+method);
            if(!method.equals("inspect-gtf")){required(o,"star");integer(o,"threads",1,1,256);integer(o,"timeout",3600,1,86400);}
            if(method.equals("align"))validateAlignOptions(o);
            else {required(o,"reference");if(method.equals("inspect-gtf"))required(o,"gtf");}
            if(method.equals("index")){
                required(o,"memory-mb");integer(o,"memory-mb",512,128,1_000_000);
                integer(o,"sa-index-bases",3,1,14);integer(o,"chr-bin-bits",8,1,18);
                if(o.containsKey("gtf"))required(o,"sjdb-overhang");
                else if(o.containsKey("sjdb-overhang"))throw new IllegalArgumentException("sjdb-overhang applies only with a GTF");
                if(o.containsKey("gtf"))integer(o,"sjdb-overhang",0,1,10000);
            }
            int[] exit={0};externalTransaction(o,"star-"+method,(dir,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("method",method);
                session.put("scope","STAR 2.7.11b bulk RNA splice alignment, explicit reference/annotation/settings; native GTF and FASTQ validation. No STARsolo, chimeric fusion, WASP, UMI deduplication or featureCounts equivalence.");
                try {
                    if(method.equals("align"))align(o,dir,manifest,session,warnings);
                    else reference(o,method,dir,manifest,session,warnings);
                    try(var paths=Files.walk(dir)){
                        for(Path log:paths.filter(Files::isRegularFile).filter(p->p.getFileName().toString().endsWith(".log")||p.getFileName().toString().endsWith("Log.out")).toList())
                            try(var lines=Files.lines(log)){lines.filter(s->s.matches("(?i).*\\bwarning\\b.*")).limit(100).forEach(s->warnings.add(dir.relativize(log)+": "+s));}
                    }
                    if(!method.equals("inspect-gtf")&&windows())warnings.add("WINDOWS BUILD: upstream lists Linux/macOS support. Use this private runtime only within locally tested operations; retain its source/toolchain/build report. NoSharedMemory and native FASTQ decompression are forced.");
                    manifest.put("analysis_status",warnings.isEmpty()?"completed":"completed-with-warnings");
                    if(!warnings.isEmpty())Files.writeString(dir.resolve("WARNINGS.md"),"# STAR WARNINGS\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: inspect the named logs, counts and explicit reference/settings before downstream analysis. Correct annotation/low-mapping issues before interpreting counts. For an unsupported platform/operation, use the pinned upstream runtime on its supported host and validate that operation.\n");
                }catch(Exception failure){
                    exit[0]=failure instanceof DependencyUnavailable?3:2;manifest.put("analysis_status",exit[0]==3?"skipped-dependency-unavailable":"failed");manifest.put("error",String.valueOf(failure.getMessage()));quarantine(dir);
                    Files.writeString(dir.resolve("WARNINGS.md"),"# "+(exit[0]==3?"STAR UNAVAILABLE - ANALYSIS SKIPPED":"STAR ANALYSIS FAILED")+"\n\n"+failure.getMessage()+"\n\nRequired action: "+(exit[0]==3?"Where permitted run python tools/literature/install_star.py --allow-download --allow-source-build and supply --star build/omics-star/bin/STAR.exe. If installation/building is forbidden, skip the analysis until an administrator supplies STAR 2.7.11b and its runtime libraries.":"Inspect the named input/index contract and retained native logs, correct the cause and rerun to a new output directory.")+" Partial artifacts are retained under partial/. No successful results.tsv is published.\n");
                }
                session.put("warnings",warnings);Files.writeString(dir.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));
                Files.writeString(dir.resolve("analysis-status.json"),"{\"status\":\""+manifest.get("analysis_status")+"\"}\n");
            });
            if(exit[0]!=0)err.println((exit[0]==3?"SKIPPED ANALYSIS":"STAR ANALYSIS FAILED")+": see "+Path.of(o.get("out"),"WARNINGS.md")+" for the cause and required action.");
            else {out.println("STAR operation completed: "+o.get("out"));if(Files.exists(Path.of(o.get("out"),"WARNINGS.md")))err.println("STAR WARNINGS: see "+Path.of(o.get("out"),"WARNINGS.md"));}
            return exit[0];
        }catch(Exception failure){err.println("jlinalg: "+failure.getMessage());return 2;}
    }

    private static void validateAlignOptions(Map<String,String> o) {
        for(String key:List.of("index","reads","sample"))required(o,key);
        identifier(o.get("sample"));identifier(o.getOrDefault("read-group","JL1"));if(o.containsKey("library"))identifier(o.get("library"));
        choice(o,"two-pass","none",Set.of("none","basic"));choice(o,"ends","local",Set.of("local","end-to-end"));
        choice(o,"filter-type","normal",Set.of("normal","by-sj-out"));choice(o,"intron-motifs","all",Set.of("all","canonical-only"));
        if(o.containsKey("sj-junctions")&&!o.getOrDefault("two-pass","none").equals("none"))throw new IllegalArgumentException("Explicit second-pass junctions require --two-pass none; Basic is a distinct automatic two-pass workflow");
        if(o.containsKey("sjdb-overhang")){
            integer(o,"sjdb-overhang",0,1,10000);
            if(!o.containsKey("sj-junctions")&&!o.getOrDefault("two-pass","none").equals("basic"))throw new IllegalArgumentException("Align sjdb-overhang applies only to Basic or supplied-junction insertion");
        }
        integer(o,"multimap-max",10,1,10000);integer(o,"match-min",0,0,10_000_000);integer(o,"score-min",0,0,10_000_000);
        integer(o,"mismatch-max",10,0,10_000_000);integer(o,"intron-min",21,1,1_000_000);int max=integer(o,"intron-max",0,0,Integer.MAX_VALUE);
        if(max>0&&max<integer(o,"intron-min",21,1,1_000_000))throw new IllegalArgumentException("intron-max must be zero (upstream derived) or >= intron-min");
        for(String key:List.of("match-fraction","score-fraction")){double v=number(o,key,.66);if(v<0||v>1)throw new IllegalArgumentException(key+" must be in [0,1]");}
        bool(o,"gene-counts",false);bool(o,"unmapped-fastq",false);integer(o,"seed",777,0,Integer.MAX_VALUE);
    }

    private static void reference(Map<String,String> o,String method,Path dir,Map<String,Object> manifest,Map<String,Object> session,List<String> warnings)throws Exception {
        String binary=method.equals("index")?runtime(o,dir,session):null;
        Path fasta=file(required(o,"reference"));String refHash=hash(fasta);var fai=FastaIndexes.build(fasta);
        var lengths=new LinkedHashMap<String,Long>();var sequences=new ArrayList<Map<String,Object>>();long bases=0;
        for(var entry:fai){if(entry.getSize()>Integer.MAX_VALUE)throw new IOException("Reference contig exceeds SAM coordinate limit");lengths.put(entry.getContig(),entry.getSize());sequences.add(Map.of("name",entry.getContig(),"length",entry.getSize()));bases=Math.addExact(bases,entry.getSize());}
        manifest.put("reference_sha256",refHash);session.put("reference_bases",bases);session.put("reference_sequences",sequences.size());fai.write(dir.resolve("reference.fai"));
        Path gtf=o.containsKey("gtf")?file(o.get("gtf")):null;String gtfHash=gtf==null?null:hash(gtf);GtfAnnotations.Summary annotation=gtf==null?null:GtfAnnotations.inspect(gtf,lengths);
        if(annotation!=null){
            manifest.put("gtf_sha256",gtfHash);session.put("annotation_exons",annotation.exons());session.put("annotation_genes",annotation.genes());session.put("annotation_transcripts",annotation.transcripts());
            Files.write(dir.resolve("gene-ids.txt"),new TreeSet<>(annotation.geneIds()));
            if(annotation.unknownStrandExons()>0)warnings.add("GTF has "+annotation.unknownStrandExons()+" exons with unknown strand; stranded gene counts require an appropriate annotation and library-strand interpretation.");
        }
        if(method.equals("inspect-gtf")){
            if(!refHash.equals(hash(fasta))||!Objects.equals(gtfHash,hash(gtf)))throw new IOException("Reference/GTF changed during inspection");
            table(dir.resolve("results.tsv"),List.of("metric","value"),List.of(new String[]{"reference_bases",Long.toString(bases)},new String[]{"exons",Long.toString(annotation.exons())},new String[]{"genes",Integer.toString(annotation.genes())},new String[]{"transcripts",Integer.toString(annotation.transcripts())}));return;
        }
        int sa=integer(o,"sa-index-bases",Math.max(1,Math.min(14,(int)Math.floor(Math.log(bases)/Math.log(2)/2-1))),1,14);
        int bits=integer(o,"chr-bin-bits",Math.max(1,Math.min(18,(int)Math.floor(Math.log(Math.max(2,bases/sequences.size()))/Math.log(2)))),1,18);
        Path inputs=Files.createDirectory(dir.resolve("inputs"));Files.copy(fasta,inputs.resolve("reference.fa"));if(gtf!=null)Files.copy(gtf,inputs.resolve("annotations.gtf"));
        var command=new ArrayList<>(List.of(binary,"--runMode","genomeGenerate","--genomeDir","index","--genomeFastaFiles","inputs/reference.fa",
            "--runThreadN",o.getOrDefault("threads","1"),"--genomeSAindexNbases",Integer.toString(sa),"--genomeChrBinNbits",Integer.toString(bits),
            "--limitGenomeGenerateRAM",Long.toString(integer(o,"memory-mb",512,128,1_000_000)*1024L*1024L)));
        if(gtf!=null)command.addAll(List.of("--sjdbGTFfile","inputs/annotations.gtf","--sjdbOverhang",o.get("sjdb-overhang")));
        execute(command,dir,"index",integer(o,"timeout",3600,1,86400),session);
        var components=new TreeMap<String,String>();try(var paths=Files.list(dir.resolve("index"))){for(Path p:paths.filter(Files::isRegularFile).toList())components.put(p.getFileName().toString(),hash(p));}
        var required=new HashSet<>(CORE);if(annotation!=null)required.addAll(ANNOTATION);
        if(!components.keySet().containsAll(required))throw new IOException("STAR omitted required reference/annotation index components");
        if(!refHash.equals(hash(fasta))||!refHash.equals(hash(inputs.resolve("reference.fa")))||(gtf!=null&&(!Objects.equals(gtfHash,hash(gtf))||!Objects.equals(gtfHash,hash(inputs.resolve("annotations.gtf"))))))throw new IOException("Reference/GTF changed during indexing");
        var index=new LinkedHashMap<String,Object>();index.put("schema_version",1);index.put("engine","STAR");index.put("version",VERSION);index.put("reference_sha256",refHash);index.put("reference_sequences",sequences);
        index.put("gtf_sha256",gtfHash);index.put("gene_ids_sha256",annotation==null?null:hash(dir.resolve("gene-ids.txt")));index.put("sjdb_overhang",annotation==null?0:Integer.parseInt(o.get("sjdb-overhang")));index.put("sa_index_bases",sa);index.put("chr_bin_bits",bits);index.put("index_sha256",components);
        Files.writeString(dir.resolve("star-index.json"),json(index)+"\n");
        Files.delete(inputs.resolve("reference.fa"));if(gtf!=null)Files.delete(inputs.resolve("annotations.gtf"));Files.delete(inputs);
        table(dir.resolve("results.tsv"),List.of("metric","value"),List.of(new String[]{"reference_bases",Long.toString(bases)},new String[]{"reference_sequences",Integer.toString(sequences.size())},new String[]{"annotation_genes",annotation==null?"0":Integer.toString(annotation.genes())}));
    }

    private static void align(Map<String,String> o,Path dir,Map<String,Object> manifest,Map<String,Object> session,List<String> warnings)throws Exception {
        String binary=runtime(o,dir,session);Path indexDir=Path.of(o.get("index")).toAbsolutePath().normalize();Path metadata=file(indexDir.resolve("star-index.json").toString());
        if(Files.size(metadata)>64*1024*1024)throw new IOException("STAR index manifest exceeds 64 MiB");Object parsed=SimpleJson.parse(Files.readString(metadata));
        if(!(parsed instanceof Map<?,?> index)||!(index.get("schema_version") instanceof Number n)||n.intValue()!=1||!Objects.equals(index.get("engine"),"STAR")||!Objects.equals(index.get("version"),VERSION))throw new IOException("STAR index engine/version/schema differs from this adapter");
        verifyIndex(indexDir,index);String metadataHash=hash(metadata);manifest.put("star_index_manifest_sha256",metadataHash);manifest.put("reference_sha256",index.get("reference_sha256"));manifest.put("gtf_sha256",index.get("gtf_sha256"));
        boolean counts=bool(o,"gene-counts",false);if(counts&&index.get("gtf_sha256")==null)throw new IOException("GeneCounts requires an index built with a validated GTF");
        boolean insert=o.containsKey("sj-junctions")||o.getOrDefault("two-pass","none").equals("basic");
        int overhang=index.get("sjdb_overhang") instanceof Number value?value.intValue():-1;
        if(overhang<0||overhang>10000)throw new IOException("Invalid index annotation overhang");
        if(insert&&overhang==0&&!o.containsKey("sjdb-overhang"))throw new IOException("Unannotated-index junction insertion requires explicit --sjdb-overhang (normally intended read length minus one)");
        if(o.containsKey("sjdb-overhang")&&overhang>0&&overhang!=Integer.parseInt(o.get("sjdb-overhang")))throw new IOException("Supplied sjdb-overhang differs from the sealed annotated index");
        Path reads=file(o.get("reads")),reads2=o.containsKey("reads2")?file(o.get("reads2")):null;
        String readHash=hash(reads),read2Hash=reads2==null?null:hash(reads2);manifest.put("reads_sha256",readHash);if(reads2!=null)manifest.put("reads2_sha256",read2Hash);
        var inspection=FastqIO.inspect(reads,reads2);if(inspection.read1().reads()==0)throw new IOException("FASTQ contains no reads");
        stageFastq(reads,dir.resolve("reads1.fastq"));if(reads2!=null)stageFastq(reads2,dir.resolve("reads2.fastq"));
        long fragments=inspection.read1().reads();session.put("input_fragments",fragments);session.put("fastq_staging","Native decompression; upper-case canonical A/C/G/T/N and original quality strings. No readFilesCommand, FIFO, trimming or shell.");
        var command=new ArrayList<>(List.of(binary,"--genomeDir",portable(indexDir.resolve("index")),"--genomeLoad","NoSharedMemory","--runThreadN",o.getOrDefault("threads","1"),"--readFilesIn","reads1.fastq"));if(reads2!=null)command.add("reads2.fastq");
        command.addAll(List.of("--outFileNamePrefix","rna.","--outSAMtype","SAM","--outSAMorder",o.getOrDefault("filter-type","normal").equals("normal")?"PairedKeepInputOrder":"Paired","--outSAMunmapped","Within","KeepPairs","--outSAMattributes","NH","HI","AS","nM","NM","MD","--outSAMprimaryFlag","OneBestScore","--outSAMattrRGline","ID:"+o.getOrDefault("read-group","JL1"),"SM:"+o.get("sample"),"PL:ILLUMINA"));
        if(o.containsKey("library"))command.add("LB:"+o.get("library"));
        command.addAll(List.of("--twopassMode",o.getOrDefault("two-pass","none").equals("basic")?"Basic":"None","--alignEndsType",o.getOrDefault("ends","local").equals("local")?"Local":"EndToEnd",
            "--outFilterType",o.getOrDefault("filter-type","normal").equals("normal")?"Normal":"BySJout","--outFilterIntronMotifs",o.getOrDefault("intron-motifs","all").equals("all")?"None":"RemoveNoncanonical"));
        String[][] switches={{"multimap-max","outFilterMultimapNmax","10"},{"match-min","outFilterMatchNmin","0"},{"score-min","outFilterScoreMin","0"},{"match-fraction","outFilterMatchNminOverLread","0.66"},{"score-fraction","outFilterScoreMinOverLread","0.66"},{"mismatch-max","outFilterMismatchNmax","10"},{"intron-min","alignIntronMin","21"},{"intron-max","alignIntronMax","0"},{"seed","runRNGseed","777"}};
        for(String[] s:switches)command.addAll(List.of("--"+s[1],o.getOrDefault(s[0],s[2])));
        if(insert)command.addAll(List.of("--sjdbOverhang",overhang>0?Integer.toString(overhang):o.get("sjdb-overhang")));
        if(counts)command.addAll(List.of("--quantMode","GeneCounts"));if(bool(o,"unmapped-fastq",false))command.addAll(List.of("--outReadsUnmapped","Fastx"));
        Path junctions=o.containsKey("sj-junctions")?file(o.get("sj-junctions")):null;String junctionHash=junctions==null?null:hash(junctions);
        if(junctions!=null){stageJunctions(junctions,dir.resolve("junctions.tab"),index,session);command.addAll(List.of("--sjdbFileChrStartEnd","junctions.tab"));manifest.put("junctions_sha256",junctionHash);warnings.add("EXPLICIT SECOND PASS: supplied splice junctions are inserted into this alignment; generation and filtering of the first-pass junction list remain the user's responsibility. Basic is a separate workflow.");}
        execute(command,dir,"alignment",integer(o,"timeout",3600,1,86400),session);
        validateSam(dir.resolve("rna.Aligned.out.sam"),dir.resolve("reads1.fastq"),reads2==null?null:dir.resolve("reads2.fastq"),index,o,session);
        if(counts)validateCounts(dir.resolve("rna.ReadsPerGene.out.tab"),indexDir.resolve("gene-ids.txt"),fragments);
        verifyIndex(indexDir,index);
        if(!metadataHash.equals(hash(metadata))||!readHash.equals(hash(reads))||(reads2!=null&&!Objects.equals(read2Hash,hash(reads2)))||(junctions!=null&&!Objects.equals(junctionHash,hash(junctions))))throw new IOException("Index manifest, FASTQ or junctions changed during alignment");
        Files.delete(dir.resolve("reads1.fastq"));if(reads2!=null)Files.delete(dir.resolve("reads2.fastq"));
        if(Long.valueOf(0).equals(session.get("mapped_primary_records")))warnings.add("ZERO MAPPED PRIMARY READS: check reference, library and filtering settings; this does not establish biological absence.");
        if(counts)warnings.add("GENE COUNTS: STAR's three count columns are unstranded, read-1 strand and read-2 strand, respectively. Select the column matching the actual library protocol. These are raw fragment counts, without UMI deduplication, normalization, or featureCounts equivalence.");
        if(!counts)warnings.add("NO GENE COUNTS REQUESTED: SAM alignment alone is not an expression matrix. Request --gene-counts true with an annotated index or use an appropriate separately validated quantifier.");
        int effectiveOverhang=overhang>0?overhang:o.containsKey("sjdb-overhang")?Integer.parseInt(o.get("sjdb-overhang")):0;
        if(effectiveOverhang>0&&effectiveOverhang!=Math.max(inspection.read1().maxLength(),inspection.read2()==null?0:inspection.read2().maxLength())-1)warnings.add("ANNOTATION OVERHANG: sjdbOverhang="+effectiveOverhang+" differs from maximum input read length minus one. Confirm that this is the intended reference/read protocol.");
        var rows=new ArrayList<String[]>();for(String key:List.of("input_fragments","primary_records","mapped_primary_records","spliced_primary_records","secondary_records","supplementary_records","proper_pair_records"))rows.add(new String[]{key,String.valueOf(session.get(key))});table(dir.resolve("results.tsv"),List.of("metric","value"),rows);
    }

    private static void stageFastq(Path input,Path output)throws IOException {
        try(var in=new FastqIO.Reader(input);var out=new FastqIO.Writer(output)){FastqIO.Read r;while((r=in.next())!=null){String bases=r.sequence().toUpperCase(Locale.ROOT);if(!bases.matches("[ACGTN]+"))throw new IOException("STAR FASTQ contract requires nonempty canonical A/C/G/T/N; ambiguous IUPAC or RNA U must be explicitly processed first");out.write(new FastqIO.Read(r.header(),bases,r.quality()));}}
    }
    private static void stageJunctions(Path input,Path output,Map<?,?> index,Map<String,Object> session)throws IOException {
        var dictionary=dictionary(index);long count=0;var seen=new HashSet<String>();
        try(var in=Files.newBufferedReader(input);var out=Files.newBufferedWriter(output)){String line;while((line=in.readLine())!=null){if(line.isBlank()||line.startsWith("#"))continue;String[] f=line.split("\t",-1);if(f.length!=4&&f.length!=9)throw new IOException("Junctions require STAR four-column input or nine-column SJ.out.tab, not BED");
            try{long start=Long.parseLong(f[1]),end=Long.parseLong(f[2]);int strand=Integer.parseInt(f[3]);if(!dictionary.containsKey(f[0])||start<1||end<start||end>dictionary.get(f[0])||strand<0||strand>2)throw new IOException("Junction contig/one-based inclusive intron/strand is invalid");}catch(NumberFormatException e){throw new IOException("Noninteger junction coordinates/strand",e);}
            String key=String.join("\t",Arrays.copyOf(f,4));if(!seen.add(key))throw new IOException("Duplicate splice junction");if(++count>20_000_000)throw new IOException("Junction count exceeds twenty million");out.write(key);out.newLine();}}
        if(count==0)throw new IOException("Junction file is empty");session.put("supplied_junctions",count);
    }
    private static Map<String,Long> dictionary(Map<?,?> index)throws IOException {
        if(!(index.get("reference_sequences") instanceof List<?> sequences)||sequences.isEmpty())throw new IOException("Index has no reference dictionary");var result=new LinkedHashMap<String,Long>();
        for(Object value:sequences){if(!(value instanceof Map<?,?> row)||!(row.get("name") instanceof String name)||name.isBlank()||!(row.get("length") instanceof Number n)||n.longValue()<1||n.longValue()>Integer.MAX_VALUE||result.put(name,n.longValue())!=null)throw new IOException("Invalid/duplicate reference dictionary entry");}return result;
    }
    private static void verifyIndex(Path dir,Map<?,?> index)throws IOException {
        dictionary(index);if(!(index.get("reference_sha256") instanceof String sha)||!sha.matches("[0-9a-f]{64}"))throw new IOException("Index omits reference hash");
        if(!(index.get("index_sha256") instanceof Map<?,?> hashes)||!hashes.keySet().containsAll(CORE))throw new IOException("STAR index omits components");
        var actual=new HashSet<String>();try(var paths=Files.list(dir.resolve("index"))){for(Path p:paths.toList()){if(!Files.isRegularFile(p))throw new IOException("Unexpected STAR index subdirectory");actual.add(p.getFileName().toString());}}
        if(!actual.equals(hashes.keySet()))throw new IOException("STAR index component set changed");
        for(var entry:hashes.entrySet()){
            if(!(entry.getKey() instanceof String name)||!name.matches("[A-Za-z0-9_.-]+")||name.equals(".")||name.equals("..")||!Objects.equals(entry.getValue(),hash(file(dir.resolve("index").resolve(name).toString()))))throw new IOException("STAR index component changed or is corrupt: "+entry.getKey());
        }
        if(index.get("gtf_sha256")!=null){if(!(index.get("gtf_sha256") instanceof String gtf)||!gtf.matches("[0-9a-f]{64}")||!hashes.keySet().containsAll(ANNOTATION)||!Objects.equals(index.get("gene_ids_sha256"),hash(file(dir.resolve("gene-ids.txt").toString()))))throw new IOException("STAR annotation index/gene IDs changed");}
    }

    private static void validateSam(Path sam,Path reads,Path reads2,Map<?,?> index,Map<String,String> o,Map<String,Object> session)throws IOException {
        long primary=0,mapped=0,spliced=0,secondary=0,supplementary=0,proper=0;Map<String,Long> expectedDictionary=dictionary(index);
        boolean unordered=o.getOrDefault("filter-type","normal").equals("by-sj-out");
        try(var first=new FastqIO.Reader(reads);var second=reads2==null?null:new FastqIO.Reader(reads2);var audit=unordered?new ReadContentAudit(sam.getParent().resolve("read-audit-tmp"),100_000):null;var reader=SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).open(sam);var records=reader.iterator()){
            var dictionary=reader.getFileHeader().getSequenceDictionary();if(dictionary.size()!=expectedDictionary.size())throw new IOException("STAR changed reference dictionary size");int i=0;
            for(var entry:expectedDictionary.entrySet()){var seq=dictionary.getSequence(i++);if(!seq.getSequenceName().equals(entry.getKey())||seq.getSequenceLength()!=entry.getValue())throw new IOException("STAR changed reference dictionary names/lengths/order");}
            String group=o.getOrDefault("read-group","JL1");var rg=reader.getFileHeader().getReadGroup(group);if(rg==null||!o.get("sample").equals(rg.getSample()))throw new IOException("STAR changed read group/sample");
            if(unordered){FastqIO.Read read;while((read=first.next())!=null)audit.expected(read.pairId(),second==null?0:1,read.sequence(),read.quality());if(second!=null)while((read=second.next())!=null)audit.expected(read.pairId(),2,read.sequence(),read.quality());}
            FastqIO.Read a=null,b=null;boolean next=true,mate2=false;
            while(records.hasNext()){
                SAMRecord record=records.next();if(record.isSecondaryAlignment()){secondary++;continue;}if(record.getSupplementaryAlignmentFlag()){supplementary++;continue;}
                if(record.getReadPairedFlag()!=(second!=null)||!group.equals(record.getStringAttribute("RG")))throw new IOException("STAR changed primary pairing or read group");
                if(unordered){
                    if(second!=null&&record.getFirstOfPairFlag()==record.getSecondOfPairFlag())throw new IOException("STAR changed primary mate flags");
                    String bases=record.getReadString(),quality=record.getBaseQualityString();if(record.getReadNegativeStrandFlag()){bases=reverseComplement(bases);quality=new StringBuilder(quality).reverse().toString();}
                    audit.observed(record.getReadName().replaceFirst("/[12]$",""),second==null?0:record.getFirstOfPairFlag()?1:2,bases,quality);
                }else{
                    if(next){a=first.next();b=second==null?null:second.next();if(a==null||(second!=null&&b==null))throw new IOException("STAR emitted extra primary observations");next=false;}
                    FastqIO.Read expected=mate2?b:a;
                    if(!record.getReadName().replaceFirst("/[12]$","").equals(expected.pairId()))throw new IOException("STAR changed primary identities/order");
                    if(second!=null&&(record.getFirstOfPairFlag()==mate2||record.getSecondOfPairFlag()!=mate2))throw new IOException("STAR changed primary mate flags");
                    String bases=expected.sequence(),quality=expected.quality();if(record.getReadNegativeStrandFlag()){bases=reverseComplement(bases);quality=new StringBuilder(quality).reverse().toString();}
                    if(!bases.equals(record.getReadString())||!quality.equals(record.getBaseQualityString()))throw new IOException("STAR changed primary sequence/quality beyond strand orientation");
                }
                primary++;if(!record.getReadUnmappedFlag()){mapped++;if(record.getCigar().containsOperator(CigarOperator.N))spliced++;}if(record.getReadPairedFlag()&&record.getProperPairFlag())proper++;
                if(!unordered){if(second==null||mate2){next=true;mate2=false;}else mate2=true;}
            }
            if(unordered)audit.verify();else if(!next||mate2||first.next()!=null||(second!=null&&second.next()!=null))throw new IOException("STAR omitted primary observations/mates");
            session.put("primary_content_validation",unordered?"Disk-backed sorted SHA-256 multiset of length-prefixed name/mate/original-orientation bases/qualities, including duplicate multiplicities; BySJout output order is upstream native":"Exact streaming primary input order/name/mate/strand-adjusted bases/qualities");
        }
        session.put("primary_records",primary);session.put("mapped_primary_records",mapped);session.put("spliced_primary_records",spliced);session.put("secondary_records",secondary);session.put("supplementary_records",supplementary);session.put("proper_pair_records",proper);
    }
    private static void validateCounts(Path counts,Path genes,long fragments)throws IOException {
        var expected=new HashSet<>(Files.readAllLines(genes));expected.addAll(Set.of("N_unmapped","N_multimapping","N_noFeature","N_ambiguous"));var seen=new HashSet<String>();long[] totals=new long[3];
        try(var lines=Files.newBufferedReader(counts)){String line;while((line=lines.readLine())!=null){String[] f=line.split("\t",-1);if(f.length!=4||!expected.contains(f[0])||!seen.add(f[0]))throw new IOException("GeneCounts has missing/extra/duplicate gene IDs or columns");for(int i=0;i<3;i++)try{long n=Long.parseLong(f[i+1]);if(n<0)throw new NumberFormatException();totals[i]=Math.addExact(totals[i],n);}catch(NumberFormatException|ArithmeticException failure){throw new IOException("GeneCounts has invalid/overflowing raw counts",failure);}}}
        if(!seen.equals(expected)||Arrays.stream(totals).anyMatch(n->n!=fragments))throw new IOException("GeneCounts genes or strand-column fragment totals differ from inputs");
    }
    private static String runtime(Map<String,String> o,Path dir,Map<String,Object> session)throws Exception {
        Path path=Path.of(o.get("star")).toAbsolutePath().normalize();if(!Files.isRegularFile(path))throw new DependencyUnavailable("STAR executable is unavailable: "+path);
        session.put("executable_sha256",hash(path));var dlls=new TreeMap<String,String>();try(var paths=Files.list(path.getParent())){for(Path dll:paths.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".dll")).toList())dlls.put(dll.getFileName().toString(),hash(dll));}session.put("runtime_dll_sha256",dlls);
        try{execute(List.of(path.toString(),"--version"),dir,"version",30,session);if(!Files.readString(dir.resolve("version.log")).trim().equals(VERSION))throw new IOException("Runtime differs from pinned STAR "+VERSION);}
        catch(IOException failure){throw new DependencyUnavailable(failure.getMessage());}return path.toString();
    }
    private static void execute(List<String> command,Path dir,String log,int timeout,Map<String,Object> session)throws Exception {
        session.put(log+"_command",List.copyOf(command));ProcessBuilder builder=new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(dir.resolve(log+".log").toFile());builder.environment().put("LC_ALL","C");
        Process process=builder.start();try{if(!process.waitFor(timeout,TimeUnit.SECONDS)){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();throw new IOException("STAR timed out; inspect "+log+".log");}session.put(log+"_exit_code",process.exitValue());if(process.exitValue()!=0)throw new IOException("STAR exited "+process.exitValue()+"; inspect "+log+".log");}
        catch(InterruptedException failure){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();Thread.currentThread().interrupt();throw failure;}
    }
    private static void quarantine(Path dir)throws IOException {try(var paths=Files.list(dir)){for(Path p:paths.toList()){String name=p.getFileName().toString();if(name.endsWith(".log")||name.equals("partial"))continue;Files.createDirectories(dir.resolve("partial"));Files.move(p,dir.resolve("partial").resolve(name));}}}
    private static Path file(String value)throws IOException {Path p=Path.of(value).toAbsolutePath().normalize();if(!Files.isRegularFile(p))throw new IOException("Local input does not exist: "+p);return p;}
    private static String identifier(String value){if(!value.matches("[A-Za-z0-9_.-]{1,128}"))throw new IllegalArgumentException("Sample/read-group/library IDs require 1..128 letters/digits/underscore/dot/hyphen");return value;}
    private static String choice(Map<String,String> o,String key,String fallback,Set<String> values){String v=o.getOrDefault(key,fallback);if(!values.contains(v))throw new IllegalArgumentException(key+" must be one of "+values);return v;}
    private static String portable(Path path){return path.toString().replace('\\','/');}
    private static boolean windows(){return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");}
    private static String reverseComplement(String s){var b=new StringBuilder(s.length());for(int i=s.length()-1;i>=0;i--)b.append(switch(s.charAt(i)){case'A'->'T';case'C'->'G';case'G'->'C';case'T'->'A';default->'N';});return b.toString();}
    private static final class DependencyUnavailable extends IOException {private static final long serialVersionUID=1L;DependencyUnavailable(String text){super(text);}}
    private static String json(Object value){
        if(value==null)return"null";if(value instanceof Number||value instanceof Boolean)return value.toString();
        if(value instanceof Map<?,?> map){var entries=new ArrayList<String>();for(var e:map.entrySet())entries.add(json(e.getKey().toString())+":"+json(e.getValue()));return"{"+String.join(",",entries)+"}";}
        if(value instanceof List<?> list)return"["+String.join(",",list.stream().map(StarToolsCli::json).toList())+"]";
        var b=new StringBuilder("\"");for(char c:value.toString().toCharArray())switch(c){case'"'->b.append("\\\"");case'\\'->b.append("\\\\");default->{if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}return b.append('"').toString();
    }
    static String help(){return"""
        star-tools --method index|align|inspect-gtf --out NEW_DIR
        Runtime: --star EXE (STAR 2.7.11b), --threads 1 --timeout 3600.
        index: --reference UNCOMPRESSED.fa --memory-mb MB [--gtf GTF9 --sjdb-overhang INTEGER].
          Overhang is required with annotation; normally intended read length minus one. No inferred assembly.
          Optional --sa-index-bases 1..14 --chr-bin-bits 1..18; defaults adapt to reference size.
          Native FASTA/GTF validation, exact contig names and one-based inclusive exon bounds.
          Every generated index component, reference/GTF hash and gene-ID list are sealed in star-index.json.
        inspect-gtf: --reference FASTA --gtf GTF9; native Java, no STAR dependency.
        align: --index INDEX_BUNDLE --reads R1.fastq[.gz] [--reads2 R2.fastq[.gz]] --sample ID
          [--read-group JL1 --library ID --gene-counts false --two-pass none|basic --seed 777].
          --sj-junctions FILE accepts STAR 4-column intron input or 9-column SJ.out.tab for an explicit
          second pass; requires --two-pass none. First-pass generation/filtering is a separate decision.
          Basic or supplied-junction insertion with an unannotated index requires --sjdb-overhang;
          annotated indices retain their sealed overhang, and a conflicting override is rejected.
          --ends local|end-to-end --filter-type normal|by-sj-out --intron-motifs all|canonical-only
          --multimap-max 10 --match-min 0 --score-min 0 --match-fraction 0.66 --score-fraction 0.66
          --mismatch-max 10 --intron-min 21 --intron-max 0 (upstream derived), --unmapped-fastq false.
          Absolute and fractional score/match thresholds BOTH apply. GeneCounts requires an annotated index.
        Strict four-line canonical A/C/G/T/N FASTQ, Phred+33; native gzip decompression and uppercase
          staging, original qualities, synchronized pair IDs/order after terminal /1 or /2 removal.
          Fixed NoSharedMemory; no readFilesCommand, shell pipeline, FIFO or automatic installation.
        Output rna.Aligned.out.sam retains unmapped mates, one best primary plus accepted secondary hits.
          Every primary identity/mate/base/quality, reference dictionary and RG is checked. Normal mode
          preserves input order. BySJout requires native output order: bounded disk-backed SHA-256
          content multisets check all primary reads and duplicate multiplicities without assuming order.
          MAPQ 255 for STAR unique alignments is not a calibrated posterior confidence probability.
          Optional raw rna.ReadsPerGene.out.tab: unstranded/read-1-strand/read-2-strand columns;
          select by library protocol. Counts include all category totals and are checked per input fragment.
          No STARsolo/fusions/WASP, UMI deduplication, featureCounts equivalence or paper-pipeline equivalence.
        Missing/wrong/unloadable runtime: skip 3; other failure 2, actionable WARNINGS.md and partial/.
          Setup where permitted: python tools/literature/install_star.py --allow-download --allow-source-build.
          Private Windows x86_64 MSYS2/SSE4.1 build has local evidence; upstream supports Linux/macOS.
        """;}
}
