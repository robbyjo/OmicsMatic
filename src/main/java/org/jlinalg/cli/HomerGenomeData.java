/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.jlinalg.sequence.HomerGenomicSequences;
import org.jlinalg.sequence.IndexedSequences;

/** Streaming reference intake and complete native genomic population planning. */
final class HomerGenomeData {
    private HomerGenomeData() { }
    record Limits(long input,long reference,long extracted,int records,int motifs,long motifRows,int line,
                  long operations,long neighborhoods,long workspace,long rss,int timeout) {
        static Limits read(Map<String,String> o)throws IOException{return new Limits(HomerCoreData.positive(o,"maximum-input-bytes",8589934592L),HomerCoreData.positive(o,"maximum-reference-bases",8000000000L),HomerCoreData.positive(o,"maximum-extracted-bases",100000000),HomerCoreData.integer(o,"maximum-records",100000,1,1000000),HomerCoreData.integer(o,"maximum-motifs",20000,1,100000),HomerCoreData.positive(o,"maximum-motif-rows",500000),HomerCoreData.integer(o,"maximum-line-characters",1048576,256,1048576),HomerCoreData.positive(o,"maximum-operations",1000000000),HomerCoreData.positive(o,"maximum-neighborhoods",1000000000),HomerCoreData.positive(o,"workspace-bytes",34359738368L),HomerCoreData.positive(o,"maximum-upstream-memory-bytes",2147483648L),HomerCoreData.integer(o,"timeout",3600,1,86400));}
    }
    record Contig(String alias,String sourceId,String header,long length) { }
    record Span(long start1,long end1,char strand) {
        Span {if(start1< -100001||end1>Integer.MAX_VALUE||end1<start1||strand!='+'&&strand!='-')throw new IllegalArgumentException("Invalid bounded genomic span");}
        static Span of(HomerGenomicSequences.Interval p){return new Span(p.start1(),p.end1(),p.strand());}
        long length(){return end1-start1+1;}
    }
    record Region(String alias,String stagedId,String role,long ordinal,String sourceId,String contig,
                  Span original,Span extraction,String dna,
                  boolean overlapExcluded,HomerGenomicSequences.Disposition quality,HomerGenomicSequences.Frequency frequency,
                  String disposition,double preWeight) {
        boolean sequenceRetained(){return !overlapExcluded&&quality==HomerGenomicSequences.Disposition.RETAINED;}
        boolean analytic(){return disposition.equals("retained");}
    }
    record Model(String alias,HomerGenomeMatrices.Matrix motif) { }
    record Data(List<Region> regions,List<Model> known,List<Model> comparison,Map<Path,String> hashes,
                long duplicates,long operations,long upstreamEstimate) {
        Data {regions=List.copyOf(regions);known=List.copyOf(known);comparison=List.copyOf(comparison);hashes=Map.copyOf(hashes);}
        void verify()throws IOException{for(var e:hashes.entrySet()){HomerFasta.regular(e.getKey(),Long.MAX_VALUE);if(!FollowupSupport.hash(e.getKey()).equals(e.getValue()))throw new IOException("Genomic immutable source/staging changed: "+e.getKey());}}
    }
    static Data stage(Map<String,String> o,HomerGenomeCli.Plan plan,Limits limits,HomerGenomeRuntime.Context runtime,Path work)throws Exception{return stage(o,plan,limits,runtime,work,false);}
    static Data targets(Map<String,String> o,HomerGenomeCli.Plan plan,Limits limits,HomerGenomeRuntime.Context runtime,Path work)throws Exception{return stage(o,plan,limits,runtime,work,true);}
    private static Data stage(Map<String,String> o,HomerGenomeCli.Plan plan,Limits limits,HomerGenomeRuntime.Context runtime,Path work,boolean targetsOnly)throws Exception{
        var hashes=new LinkedHashMap<Path,String>();long input=0;
        for(String key:List.of("genome","peaks","background","known-motifs","comparison-motifs","options-file")){
            if(!o.containsKey(key))continue;String value=o.get(key);Path source;
            if((key.equals("known-motifs")||key.equals("comparison-motifs"))&&value.equals("profile"))source=runtime.home().resolve("data/knownTFs/all/"+(key.equals("known-motifs")?"known.motifs":"all.motifs"));
            else source=Path.of(value).toAbsolutePath().normalize();
            HomerFasta.regular(source,limits.input);input=Math.addExact(input,Files.size(source));if(input>limits.input||input>limits.workspace/3)throw new IOException("Combined genomic raw snapshots exceed input-byte or third-workspace capacity");String digest=FollowupSupport.hash(source);if(key.equals("options-file")&&!digest.equals(o.get("options-sha256")))throw new IOException("Genomic options changed before snapshot");Path copy=work.resolve("input-"+key);Files.copy(source,copy);if(!digest.equals(FollowupSupport.hash(copy))||!digest.equals(FollowupSupport.hash(source)))throw new IOException("Genomic input changed during snapshot");hashes.put(source,digest);hashes.put(copy,digest);
        }
        var contigs=reference(work,limits,o.get("alphabet").equals("strict-dna"));var byId=new HashMap<String,Contig>();for(var c:contigs)byId.put(c.sourceId,c);
        var rows=new ArrayList<Region>();long[] counts={0,0};
        try(var reference=new IndexedSequences(work.resolve("stage-genome.fa"));var ledger=Files.newBufferedWriter(work.resolve("regions.tsv"),StandardCharsets.UTF_8)){
            ledger.write("role\tregion_ordinal\tsource_id_json\tsource_contig_json\tstaged_contig\tstaged_id\toriginal_runtime_id\tstart1\tend1\textraction_start1\textraction_end1\tstrand\tsource_row_json\n");
            for(String role:targetsOnly?List.of("targets"):List.of("targets","background")){
                var ids=new HashSet<String>();long ordinal=0;boolean bed=o.get(role.equals("targets")?"peak-format":"background-format").equals("bed6");
                try(var in=HomerCoreAudit.reader(work.resolve("input-"+(role.equals("targets")?"peaks":"background")));var out=Files.newBufferedWriter(work.resolve("stage-"+role+".pos"),StandardCharsets.UTF_8)){
                    for(String line;(line=next(in,limits.line))!=null;){
                        if(line.isBlank()||line.startsWith("#"))continue;if(rows.size()>=limits.records)throw new IOException("Combined genomic region-record capacity exceeded");String[] f=line.split("\t",-1);if(bed?f.length!=6:f.length!=6&&f.length!=7)throw new IOException("Require exactly BED6 or HOMER6/7;columns are not silently discarded");String id=bed?f[3]:f[0],chr=bed?f[0]:f[1],direction=bed?f[5]:f[4],score=bed?f[4]:f[5];if(id.isBlank()||id.codePoints().anyMatch(Character::isISOControl)||chr.isBlank())throw new IOException("Require nonempty control-free genomic region identities");
                        if(!ids.add(id)){counts[0]++;if(o.get("duplicate-ids").equals("error"))throw new IOException("Duplicate region ID;choose retain-ordinal explicitly to retain records");}Contig c=byId.get(chr);if(c==null)throw new IOException("Region contig absent from exact supplied FASTA: "+chr);
                        long start=HomerGenomeCli.signed(bed?f[1]:f[2]),end=HomerGenomeCli.signed(bed?f[2]:f[3]);if(bed){if(start<0)throw new IOException("Negative BED start");start++;}if(start<1||end<start||end>c.length)throw new IOException("Region empty,reversed or outside exact supplied reference");
                        if(!SetValues.STRANDS.contains(direction))throw new IOException("Strand requires +,-,dot or original HOMER0/1");if(bed&&(direction.equals("0")||direction.equals("1")))throw new IOException("BED6 strand requires +,- or dot");if(direction.equals(".")&&o.get("unknown-strand").equals("error"))throw new IOException("Dot strand requires explicit original-plus policy");char strand=direction.equals("-")||direction.equals("1")?'-':'+';HomerGenomeCli.number(score,-Double.MAX_VALUE,Double.MAX_VALUE);if(f.length==7&&!f[6].equals("NA"))HomerGenomeCli.number(f[6],-Double.MAX_VALUE,Double.MAX_VALUE);
                        var original=new HomerGenomicSequences.Interval(start,end,strand);var extraction=plan.width()==0?original:HomerGenomicSequences.resize(original,plan.width(),plan.move());counts[1]=Math.addExact(counts[1],extraction.length());if(counts[1]>limits.extracted||counts[1]*4+rows.size()*1024L>Runtime.getRuntime().maxMemory()/2)throw new IOException("Native extracted sequence/region heap capacity exceeded;review heap and declared capacities");
                        String dna=HomerGenomicSequences.extract(reference.sequence(c.alias),extraction,o.get("mask-lowercase").equals("yes"),100001);var quality=targetsOnly&&o.get("background-mode").equals("generated-new")?org.jlinalg.sequence.HomerGeneratedSequences.newSelectionDisposition(dna):HomerGenomicSequences.disposition(dna,plan.maximumN());var frequency=dna.chars().anyMatch(ch->ch!='N')?HomerGenomicSequences.frequency(dna):null;
                        String staged=String.format(Locale.ROOT,(role.equals("targets")?"T":"B")+"%09d",++ordinal),alias=(role.equals("targets")?"":"BG")+staged;
                        rows.add(new Region(alias,staged,role,ordinal,id,c.alias,Span.of(original),Span.of(extraction),dna,false,quality,frequency,"pending",0));out.write(staged+"\t"+c.alias+"\t"+start+"\t"+end+"\t"+strand+"\t"+score+(f.length==7?"\t"+f[6]:"")+"\n");ledger.write(role+"\t"+ordinal+"\t"+RegenieRuntime.json(id)+"\t"+RegenieRuntime.json(chr)+"\t"+c.alias+"\t"+staged+"\t"+alias+"\t"+start+"\t"+end+"\t"+extraction.start1()+"\t"+extraction.end1()+"\t"+strand+"\t"+RegenieRuntime.json(line)+"\n");
                    }
                }
                if(ordinal==0)throw new IOException("Empty genomic "+role+" population");
            }
        }
        var known=plan.analysis().known?models(work,"known",limits,o):List.<Model>of();var comparison=plan.analysis().discovery?models(work,"comparison",limits,o):List.<Model>of();
        long longest=contigs.stream().mapToLong(Contig::length).max().orElseThrow();
        if(targetsOnly){
            var stagedNames=new ArrayList<>(List.of("stage-genome.fa","stage-targets.pos"));if(plan.analysis().known)stagedNames.add("stage-known.motif");if(plan.analysis().discovery)stagedNames.add("stage-comparison.motif");
            for(String name:stagedNames)hashes.put(work.resolve(name),FollowupSupport.hash(work.resolve(name)));HomerRuntime.bound(work,limits.workspace);return new Data(rows,known,comparison,hashes,counts[0],0,Math.addExact(67108864,Math.multiplyExact(longest,3)));
        }
        return finish(rows,known,comparison,hashes,counts[0],longest,o,plan,limits,work);
    }
    static Data finish(List<Region> prepared,List<Model> known,List<Model> comparison,Map<Path,String> sourceHashes,long duplicates,long longest,Map<String,String> o,HomerGenomeCli.Plan plan,Limits limits,Path work)throws Exception{
        var rows=new ArrayList<>(prepared);var hashes=new LinkedHashMap<>(sourceHashes);
        var spans=new HashMap<String,List<long[]>>();for(var r:rows)if(r.role.equals("targets"))spans.computeIfAbsent(r.contig,k->new ArrayList<>()).add(overlapSpan(r.original,plan.width()));
        var index=new HashMap<String,OverlapIndex>();spans.forEach((key,value)->index.put(key,new OverlapIndex(value)));
        var members=new ArrayList<HomerGenomicSequences.Member>();var staged=new ArrayList<Region>();
        for(var r:rows){boolean overlap=r.role.equals("background")&&o.get("background-mode").equals("supplied")&&o.get("background-overlap").equals("remove-original")&&index.containsKey(r.contig)&&index.get(r.contig).overlaps(overlapSpan(r.original,plan.width()));String disposition=overlap?"original-overlap-excluded":r.quality!=HomerGenomicSequences.Disposition.RETAINED?r.quality.toString():r.frequency==null?"no-canonical-frequency-row":"retained";
            staged.add(new Region(r.alias,r.stagedId,r.role,r.ordinal,r.sourceId,r.contig,r.original,r.extraction,r.dna,overlap,r.quality,r.frequency,disposition,disposition.equals("retained")?1:0));if(disposition.equals("retained"))members.add(new HomerGenomicSequences.Member(r.alias,r.role.equals("targets"),o.get("weighting").equals("cpg")?r.frequency.cpgBin():r.frequency.gcBin(),1));
        }
        var weights=new HashMap<String,HomerGenomicSequences.Weighted>();if(!o.get("weighting").equals("none")&&!members.isEmpty())for(var w:HomerGenomicSequences.weightBins(members))weights.put(w.member().alias(),w);
        rows=new ArrayList<>();long t=0,b=0;
        try(var ledger=Files.newBufferedWriter(work.resolve("population-plan.tsv"));var seq=Files.newBufferedWriter(work.resolve("native-sequences.tsv"));var group=Files.newBufferedWriter(work.resolve("native-pre-normalization.group.tsv"))){
            ledger.write("original_runtime_id\trole\tsource_ordinal\tdisposition\textracted_bases\tgc\tcpg\tgc_bin\tcpg_bin\tpre_oligo_weight\n");
            for(var r:staged){var w=weights.get(r.alias);boolean unsupported=w!=null&&!w.retained();String disposition=unsupported?"unsupported-frequency-bin":r.disposition;double weight=unsupported?0:w==null?r.preWeight:w.weight();var next=new Region(r.alias,r.stagedId,r.role,r.ordinal,r.sourceId,r.contig,r.original,r.extraction,r.dna,r.overlapExcluded,r.quality,r.frequency,disposition,weight);rows.add(next);if(next.analytic()){if(next.role.equals("targets"))t++;else b++;group.write(next.alias+"\t"+(next.role.equals("targets")?1:0)+"\t"+String.format(Locale.ROOT,"%.5f",weight)+"\n");}if(next.sequenceRetained())seq.write(next.alias+"\t"+next.dna+"\n");ledger.write(next.alias+"\t"+next.role+"\t"+next.ordinal+"\t"+disposition+"\t"+next.dna.length()+"\t"+(next.frequency==null?"NA\tNA\tNA\tNA":next.frequency.gc()+"\t"+next.frequency.cpg()+"\t"+next.frequency.gcBin()+"\t"+next.frequency.cpgBin())+"\t"+weight+"\n");}
        }
        if(t<1||b<2)throw new IOException("Native original population plan leaves fewer than one target or two background records;review overlap,quality and bin support");
        if(plan.analysis().discovery){
            long canonicalTotal=0;int seedTargets=0,seedBackground=0;
            try(var eligibility=Files.newBufferedWriter(work.resolve("discovery-input.tsv"))){eligibility.write("original_runtime_id\trole\tcanonical_motif_width_windows\tseed_eligible\tfinal_population_member\n");for(var r:rows){long good=canonicalWindows(r.dna,plan.motifWidth());if(r.analytic()&&good>0){canonicalTotal=Math.addExact(canonicalTotal,good);if(r.role.equals("targets"))seedTargets++;else seedBackground++;}eligibility.write(r.alias+"\t"+r.role+"\t"+good+"\t"+(r.analytic()&&good>0)+"\t"+r.analytic()+"\n");}}
            if(canonicalTotal<4||seedTargets<1||seedBackground<1)throw new IOException("Discovery requires canonical motif-width windows in both roles and at least four total;review sequence masking and requested width");
        }
        long windows=0,residues=0,operations=0;
        for(var r:rows)if(r.analytic()){if(plan.analysis().discovery)windows+=Math.max(0,r.dna.length()-plan.motifWidth()+1);residues+=r.dna.length();for(var m:known)operations=Math.addExact(operations,Math.multiplyExact(Math.max(0,r.dna.length()-m.motif.width()+1L),2L*m.motif.width()));}
        if(plan.analysis().discovery)operations=Math.addExact(operations,Math.multiplyExact(windows,Math.multiplyExact(plan.motifWidth(),4L*plan.motifs())));if(operations>limits.operations)throw new IOException("Native complete requested-phase audit exceeds maximum-operations");
        long oligos=plan.analysis().discovery?Math.min(windows,1L<<(2*plan.motifWidth())):0;long neighbors=1,combination=1,power=1;
        if(plan.analysis().discovery){for(int i=1;i<=plan.mismatches();i++){combination=combination*(plan.motifWidth()-i+1)/i;power*=3;neighbors+=combination*power;}if(Math.multiplyExact(oligos,neighbors)>limits.neighborhoods)throw new IOException("Discovery mismatch neighborhood estimate exceeds maximum-neighborhoods");}
        long finalCache=(long)Math.min(500000000.0,4.0*(t+b+1)*(t+b+2));long estimate=Math.addExact(134217728,Math.addExact(Math.multiplyExact(oligos,2*(256L+4*plan.motifWidth()+32*plan.threads())),Math.addExact(residues*10*Math.max(1,plan.motifs()),Math.addExact(finalCache,plan.cache()*1000000L+24*(1L<<(2*plan.norm()))))));estimate=Math.max(estimate,Math.addExact(67108864,Math.multiplyExact(longest,3)));if(estimate>limits.rss)throw new IOException("Conservative genomic extraction/requested-phase memory estimate exceeds maximum-upstream-memory-bytes");if(Math.multiplyExact(known.size()+comparison.size(),128L)+limits.motifRows*40>Runtime.getRuntime().maxMemory()/2)throw new IOException("Declared model audit capacity exceeds half Java maximum heap");
        var immutable=new ArrayList<>(List.of("stage-genome.fa","stage-targets.pos","stage-background.pos","native-pre-normalization.group.tsv","native-sequences.tsv"));if(plan.analysis().known)immutable.add("stage-known.motif");if(plan.analysis().discovery)immutable.add("stage-comparison.motif");
        for(String name:immutable)if(!name.equals("stage-background.pos")||o.get("background-mode").equals("supplied"))hashes.put(work.resolve(name),FollowupSupport.hash(work.resolve(name)));HomerRuntime.bound(work,limits.workspace);return new Data(rows,known,comparison,hashes,duplicates,operations,estimate);
    }
    private static List<Contig> reference(Path work,Limits limits,boolean strict)throws Exception{
        var result=new ArrayList<Contig>();var ids=new HashSet<String>();String id=null,header=null,alias=null;long length=0,total=0,headerCharacters=0;int column=0;
        try(var in=referenceReader(work.resolve("input-genome"));var out=Files.newBufferedWriter(work.resolve("stage-genome.fa"));var ledger=Files.newBufferedWriter(work.resolve("reference.tsv"))){ledger.write("staged_contig\tsource_id_json\tfull_source_header_json\tlength\n");
            for(String line;(line=next(in,limits.line))!=null;){if(line.isEmpty())continue;if(line.startsWith(">")){
                    if(id!=null){if(length<1)throw new IOException("Empty genomic FASTA contig");if(column>0)out.write('\n');result.add(new Contig(alias,id,header,length));ledger.write(alias+"\t"+RegenieRuntime.json(id)+"\t"+RegenieRuntime.json(header)+"\t"+length+"\n");}
                    header=line.substring(1);headerCharacters=Math.addExact(headerCharacters,header.length());if(headerCharacters*4+(result.size()+1L)*1024>Runtime.getRuntime().maxMemory()/8)throw new IOException("Genomic header/contig heap capacity exceeded");if(header.isBlank()||Character.isWhitespace(header.charAt(0)))throw new IOException("Invalid genomic FASTA header");id=header.split("[ \t]",2)[0];if(!ids.add(id)||id.length()>256||ids.size()>1000000)throw new IOException("Duplicate,oversized or excessive genomic FASTA identities");alias=String.format(Locale.ROOT,"C%09d",ids.size());out.write(">"+alias+"\n");length=0;column=0;
                }else{if(id==null)throw new IOException("Genomic FASTA sequence before first header");for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c<'A'||c>'Z'&&c<'a'||c>'z'||strict&&"ACGTNacgtn".indexOf(c)<0)throw new IOException("Invalid genomic reference alphabet;use explicit mask-n for noncanonical letters,never digits/gaps/whitespace");if(++total>limits.reference||++length>Integer.MAX_VALUE-10L)throw new IOException("Genomic reference residue/contig capacity exceeded");out.write(c);if(++column==80){out.write('\n');column=0;}}}
            }
            if(id==null||length<1)throw new IOException("Empty genomic FASTA/contig");if(column>0)out.write('\n');result.add(new Contig(alias,id,header,length));ledger.write(alias+"\t"+RegenieRuntime.json(id)+"\t"+RegenieRuntime.json(header)+"\t"+length+"\n");
        }return List.copyOf(result);
    }
    static BufferedReader referenceReader(Path path)throws IOException{
        var raw=new java.io.BufferedInputStream(Files.newInputStream(path));try{raw.mark(2);int first=raw.read(),second=raw.read();raw.reset();var source=first==31&&second==139?new java.util.zip.GZIPInputStream(raw):raw;return new BufferedReader(new java.io.InputStreamReader(source,StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)));}catch(IOException failure){raw.close();throw failure;}
    }
    private static List<Model> models(Path work,String kind,Limits limits,Map<String,String> o)throws Exception{
        boolean weights=o.get(kind.equals("known")?"known-motifs":"comparison-motifs").equals("profile")||o.getOrDefault("matrix-values","probabilities").equals("original-weights");var motifs=HomerGenomeMatrices.read(work.resolve("input-"+(kind.equals("known")?"known-motifs":"comparison-motifs")),limits,weights);var result=new ArrayList<Model>();
        try(var out=Files.newBufferedWriter(work.resolve("stage-"+kind+".motif"));var ledger=Files.newBufferedWriter(work.resolve(kind+"-models.tsv"))){ledger.write("alias\tsource_ordinal\tname_json\tconsensus_json\tthreshold\tsource_logp_metadata\textra_header_json\tmatrix_values\tnonunit_rows\n");for(var m:motifs){String alias=String.format(Locale.ROOT,(kind.equals("known")?"K":"M")+"%09d",m.ordinal());result.add(new Model(alias,m));out.write(">N\t"+alias+"\t"+m.threshold()+"\t"+(m.sourceLogP()==null?"0":m.sourceLogP())+(m.extraHeader().isEmpty()?"":"\t"+String.join("\t",m.extraHeader()))+"\n");for(double[] row:m.values())out.write(row[0]+"\t"+row[1]+"\t"+row[2]+"\t"+row[3]+"\n");ledger.write(alias+"\t"+m.ordinal()+"\t"+RegenieRuntime.json(m.name())+"\t"+RegenieRuntime.json(m.consensus())+"\t"+m.threshold()+"\t"+m.sourceLogP()+"\t"+RegenieRuntime.json(m.extraHeader())+"\t"+(m.originalWeights()?"original-weights":"probabilities")+"\t"+m.nonunitRows()+"\n");}}return List.copyOf(result);
    }
    static long canonicalWindows(String dna,int width){int run=0;long total=0;for(int i=0;i<dna.length();i++){run=dna.charAt(i)=='N'?0:run+1;if(run>=width)total++;}return total;}
    static long[] overlapSpan(HomerGenomicSequences.Interval p,int width){return overlapSpan(Span.of(p),width);}
    static long[] overlapSpan(Span p,int width){if(width==0)return new long[]{p.start1(),p.end1()};long mid=(p.start1()+p.end1())/2,half=width/2;return new long[]{mid-half,mid+half};}
    static final class OverlapIndex {
        private final long[] start,prefixEnd;
        OverlapIndex(List<long[]> spans){spans=new ArrayList<>(spans);spans.sort(Comparator.comparingLong(a->a[0]));start=new long[spans.size()];prefixEnd=new long[start.length];long maximum=Long.MIN_VALUE;for(int i=0;i<start.length;i++){start[i]=spans.get(i)[0];maximum=Math.max(maximum,spans.get(i)[1]);prefixEnd[i]=maximum;}}
        boolean overlaps(long[] span){int lo=0,hi=start.length;while(lo<hi){int mid=(lo+hi)>>>1;if(start[mid]<=span[1])lo=mid+1;else hi=mid;}return lo>0&&prefixEnd[lo-1]>=span[0];}
    }
    private static final class SetValues {private static final java.util.Set<String> STRANDS=java.util.Set.of("+","-",".","0","1");}
    static String next(BufferedReader reader,int maximum)throws IOException{var s=new StringBuilder();int c;while((c=reader.read())!=-1&&c!='\n'){if(c=='\r'){int after=reader.read();if(after!='\n'&&after!=-1)throw new IOException("Embedded genomic carriage return");break;}if(s.length()>=maximum||c<32&&c!='\t')throw new IOException("Oversized/control-containing genomic line");s.append((char)c);}return c==-1&&s.isEmpty()?null:s.toString();}
}
