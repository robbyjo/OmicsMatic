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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jlinalg.genetics.BigWigSignal;
import org.jlinalg.sequence.HomerCoverage;
import org.jlinalg.sequence.HomerRegionCounts;

/** Complete original region, printed coverage and raw BigWig output audits. */
final class HomerAtacResults {
    private HomerAtacResults() { }
    private static final List<String> BASE=List.of("#PeakID","chr","start","end","strand","Normalized Tag Count","region size","findPeaks Score");
    private static final Set<String> REQUIRED=Set.of("total peaks","peak size","fragment length","genome size","Total tags","Total tags in peaks","effective number of tags used for normalization","FDR rate threshold");
    record Peak(String id,String contig,int start,int end,List<String> fields) {Peak {fields=List.copyOf(fields);}}
    record Result(long peaks,long coverageRows,long bigwigRows,long effectiveGenome,long requestedGenome,
                  long comparatorTies,long mixedComparatorTies,Map<String,Float> terminalResidues,
                  Map<String,String> auditedOutputs) {
        Result {terminalResidues=Map.copyOf(terminalResidues);auditedOutputs=Map.copyOf(auditedOutputs);}
        void verify(Path work)throws IOException{for(var e:auditedOutputs.entrySet())if(!FollowupSupport.hash(work.resolve(e.getKey())).equals(e.getValue()))throw new IOException("Audited original/native ATAC output changed: "+e.getKey());}
    }
    static Result audit(HomerAtacInputs.Data data,HomerAtacTags.Audited tags,Map<String,String> o)throws Exception {
        Path work=data.work(),peaksFile=work.resolve("peaks.stdout"),coverageFile=work.resolve("coverage.stdout"),bigwigFile=work.resolve("coverage.bw");var hashes=new TreeMap<String,String>();for(Path p:List.of(peaksFile,coverageFile,bigwigFile)){HomerFasta.regular(p,data.limits().workspace());hashes.put(p.getFileName().toString(),FollowupSupport.hash(p));}
        var metadata=new HashMap<String,String>();var peaks=new ArrayList<Peak>();var identities=new HashSet<String>();List<String> header=null;boolean both=false,region=false;var merged=tags.roles().get("MERGED");
        try(var in=HomerCoreAudit.reader(peaksFile)) {
            if(!"# HOMER Peaks".equals(HomerCoreAudit.next(in,data.limits().line())))throw new IOException("Original region output lacks complete HOMER header");
            for(String line;(line=HomerCoreAudit.next(in,data.limits().line()))!=null;){
                if(line.equals("# peaks found using tags on both strands"))both=true;if(line.equals("# Individual peaks have been stitched together into variable length regions"))region=true;
                if(line.startsWith("#PeakID\t")){if(header!=null)throw new IOException("Duplicate original peak column header");header=List.of(line.split("\t",-1));if(header.size()<8||!header.subList(0,8).equals(BASE))throw new IOException("Original region primary columns differ");var expected=new ArrayList<>(BASE);if(number(o.getOrDefault("peak-local-fold","4"))>0)expected.addAll(List.of("Fold Change vs Local","p-value vs Local"));if(number(o.getOrDefault("peak-clonal-fold","2"))>0)expected.add("Clonal Fold Change");if(!header.equals(expected))throw new IOException("Original region filter columns differ from explicit controls");continue;}
                if(line.startsWith("#")){int split=line.indexOf(" = ");if(split>=0&&line.startsWith("# ")){String key=line.substring(2,split);if(REQUIRED.contains(key)&&metadata.putIfAbsent(key,line.substring(split+3))!=null)throw new IOException("Duplicate original peak population header");}continue;}
                if(line.isBlank())throw new IOException("Blank original peak data row");if(header==null||peaks.size()>=data.limits().regions())throw new IOException("Missing original peak header or peak row capacity exceeded");String[] f=line.split("\t",-1);if(f.length!=header.size()||!f[0].matches("[A-Za-z0-9_.-]{1,256}")||!identities.add(f[0])||!merged.contigs().contains(f[1])||!Set.of("+","-").contains(f[4]))throw new IOException("Unknown,duplicated or malformed original region row");int start=(int)whole(f[2],1,data.reference().get(f[1])),end=(int)whole(f[3],start,data.reference().get(f[1]));for(int i=5;i<f.length;i++)if(number(f[i])<0)throw new IOException("Negative original region count/size/score/filter diagnostic");int local=header.indexOf("p-value vs Local");if(local>=0&&number(f[local])>1)throw new IOException("Original local P diagnostic outside[0,1]");peaks.add(new Peak(f[0],f[1],start,end,List.of(f)));if((peaks.size()&4095)==0)data.check();
            }
        }
        if(header==null||!both||!region||!metadata.keySet().equals(REQUIRED)||whole(metadata.get("total peaks"),0,data.limits().regions())!=peaks.size()||whole(metadata.get("peak size"),300,300)!=300||whole(metadata.get("fragment length"),150,150)!=150||!metadata.get("Total tags").equals(decimal(data.totalWeight(),1))||!metadata.get("effective number of tags used for normalization").equals("10000000.0")||!metadata.get("FDR rate threshold").equals(decimal(number(o.getOrDefault("peak-fdr","0.001")),9)))throw new IOException("Original region mode, population or parameter header differs from staged complete inputs");
        long requested=whole(FollowupSupport.required(o,"effective-genome-size"),1,1000000000000L),estimate=Long.parseLong(merged.metadata().get("gsizeEstimate"));long effective=requested==2000000000L&&estimate>10&&estimate<requested?estimate:requested;
        if(effective!=requested&&!"retain-original-estimate".equals(o.get("genome-size-policy")))throw new IOException("Original HOMER replaced requested default2b genome size by its tag-anchor estimate="+effective+". Review effective genome and explicitly choose retain-original-estimate or an appropriate explicit effective-genome-size");if(whole(metadata.get("genome size"),1,1000000000000L)!=effective)throw new IOException("Original effective genome denominator differs from the declared source policy");
        var perChromosome=new TreeMap<String,List<Peak>>();for(var peak:peaks)perChromosome.computeIfAbsent(peak.contig(),ignored->new ArrayList<>()).add(peak);var counts=new HashMap<String,HomerRegionCounts.Count>();long coverageRows=0,ties=0,mixed=0;double rawTotal=0;var residues=new TreeMap<String,Float>();double normalizationLength=number(FollowupSupport.required(o,"coverage-length-normalization"));boolean cap=FollowupSupport.required(o,"coverage-endpoints").equals("original-cap");
        try(var original=HomerCoreAudit.reader(coverageFile);var nativeRows=Files.newBufferedWriter(work.resolve("native-coverage.bedgraph"),StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            for(String contig:new java.util.TreeSet<>(merged.contigs())) {
                data.check();var chromosome=HomerAtacTags.chromosome(data,merged,contig);var selected=perChromosome.getOrDefault(contig,List.of());var sorted=new ArrayList<>(selected);sorted.sort(Comparator.comparingInt(Peak::start).thenComparingInt(Peak::end));var queries=sorted.stream().map(p->new HomerRegionCounts.Region(p.id(),p.start(),p.end())).toList();var audit=HomerRegionCounts.count(chromosome,queries,data.reference().get(contig),150,data.totalWeight(),10000000,data.limits().chromosomeTags(),data.limits().regions(),data::check);rawTotal+=audit.totalInRegions();for(var count:audit.regions())counts.put(count.region().id(),count);
                var replay=HomerCoverage.replay(chromosome,data.reference().get(contig),data.totalWeight(),new HomerCoverage.Controls(-4,0,'.',cap,10000000,normalizationLength),data.limits().chromosomeTags(),data::check);if(replay.outOfReferenceBins()>0)throw new IOException("Original full-fragment coverage extends beyond exact reference; source bins remain retained. Review alignments,reference and endpoint policy; no clipping is inferred");ties=Math.addExact(ties,replay.tiedComparatorGroups());mixed=Math.addExact(mixed,replay.mixedValueComparatorGroups());residues.put(contig,replay.terminalResidue());
                for(var bin:replay.bins()) {if(++coverageRows>data.limits().coverageRows())throw new IOException("Complete printed coverage record capacity exceeded");String expected=contig+"\t"+bin.start0()+"\t"+bin.end()+"\t"+bin.printedValue();String actual=HomerCoreAudit.next(original,data.limits().line());if(!expected.equals(actual))throw new IOException("Complete original/native coverage differs at row"+coverageRows+". Source-float32 event order,clamping,full lengths and two-decimal output must match exactly; no arbitrary tolerance or silent replacement is allowed");nativeRows.write(expected);nativeRows.write('\n');if((coverageRows&4095)==0)data.check();}
            }
            if(HomerCoreAudit.next(original,data.limits().line())!=null)throw new IOException("Original coverage includes unexpected extra rows/contigs");
        }
        if(!metadata.get("Total tags in peaks").equals(decimal(rawTotal,1))||counts.size()!=peaks.size())throw new IOException("Original all-region raw total differs from complete inclusive native counts");
        try(var output=Files.newBufferedWriter(work.resolve("regions.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            output.write("region_id\tcontig\toriginal_start\toriginal_end\toriginal_strand\toriginal_normalized_tags\toriginal_region_size\toriginal_findPeaks_score\toriginal_local_fold\toriginal_local_p\toriginal_clonal_fold\tnative_raw_tags\tnative_float32_normalized_tags\tnative_inclusive_span\n");
            for(var peak:peaks){var count=counts.get(peak.id());if(count==null||!peak.fields().get(5).equals(count.printedNormalizedTags()))throw new IOException("Original region printed normalized count differs from the exact whole-directory native denominator");String local="NA",p="NA",clonal="NA";int index=header.indexOf("Fold Change vs Local");if(index>=0){local=peak.fields().get(index);p=peak.fields().get(index+1);}index=header.indexOf("Clonal Fold Change");if(index>=0)clonal=peak.fields().get(index);output.write(String.join("\t",peak.fields().subList(0,8))+"\t"+local+"\t"+p+"\t"+clonal+"\t"+count.rawTags()+"\t"+count.normalizedTags()+"\t"+((long)peak.end()-peak.start()+1)+"\n");}
        }
        var binary=BigWigSignal.read(bigwigFile,data.limits().workspace(),data.limits().coverageRows(),67108864);var used=new HashSet<String>();int index=0;
        try(var input=HomerCoreAudit.reader(coverageFile)){for(String line;(line=HomerCoreAudit.next(input,data.limits().line()))!=null;){if(index>=binary.segments().size())throw new IOException("BigWig raw population omits original coverage");String[] f=line.split("\t",-1);var segment=binary.segments().get(index++);if(f.length!=4||!segment.chromosome().equals(f[0])||segment.start()!=whole(f[1],0,1000000000)||segment.end()!=whole(f[2],1,1000000000)||segment.value()!=(double)Float.parseFloat(f[3]))throw new IOException("BigWig raw interval/value differs from exact float32 quantization of original printed coverage");used.add(f[0]);if((index&4095)==0)data.check();}}
        if(index!=binary.segments().size()||index!=coverageRows||index==0||!binary.dictionary().keySet().containsAll(used)||!data.reference().entrySet().containsAll(binary.dictionary().entrySet()))throw new IOException("BigWig complete raw/dictionary population differs; an empty or partial converter artifact is not a completed track");
        for(var e:hashes.entrySet())if(!FollowupSupport.hash(work.resolve(e.getKey())).equals(e.getValue()))throw new IOException("Original ATAC output changed while audited");for(String name:List.of("native-coverage.bedgraph","regions.tsv"))hashes.put(name,FollowupSupport.hash(work.resolve(name)));var result=new Result(peaks.size(),coverageRows,index,effective,requested,ties,mixed,residues,hashes);tags.verify();data.verify();result.verify(work);return result;
    }
    private static String decimal(double value,int places){return new BigDecimal(value).setScale(places,RoundingMode.HALF_EVEN).toPlainString();}
    private static double number(String text)throws IOException{try{double result=Double.parseDouble(text);if(!Double.isFinite(result))throw new NumberFormatException();return result;}catch(NumberFormatException failure){throw new IOException("Nonfinite/malformed original ATAC numeric value",failure);}}
    private static long whole(String text,long low,long high)throws IOException{try{if(text==null||!text.matches("[0-9]+"))throw new NumberFormatException();long value=Long.parseLong(text);if(value<low||value>high)throw new NumberFormatException();return value;}catch(NumberFormatException failure){throw new IOException("Original ATAC integer outside explicit bounds: "+text,failure);}}
}
