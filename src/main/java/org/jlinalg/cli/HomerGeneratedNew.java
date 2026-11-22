/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.sequence.HomerGeneratedSequences;
import org.jlinalg.sequence.HomerGenomicSequences;
import org.jlinalg.sequence.IndexedSequences;

/** Native audit of all retained targets and the actual new-selector draw. */
final class HomerGeneratedNew {
    private HomerGeneratedNew() { }
    private record Geometry(String contig,long start0,long end0,char strand) {
        HomerGenomeData.Span span(){return new HomerGenomeData.Span(start0+1,end0,strand);}
        static Geometry of(HomerGenomeData.Region r){return new Geometry(r.contig(),r.extraction().start1()-1,r.extraction().end1(),r.extraction().strand());}
        String prefix(){return contig+"_"+start0+"_"+(strand=='+'?0:1)+"_";}
    }
    private record Selected(Geometry geometry,String dna,int bin) { }

    static HomerGenomeData.Data complete(HomerGeneratedBackground.Prepared p,HomerGenomeCli.Plan plan,HomerGenomeData.Limits limits,Map<String,String> o,Path work,Map<String,Object> session,List<String> warnings)throws Exception {
        Path dir=work.resolve("original-out");var allTargets=new HashMap<String,HomerGenomeData.Region>();var eligible=new LinkedHashMap<Geometry,List<HomerGenomeData.Region>>();
        var rows=new ArrayList<HomerGenomeData.Region>();var gc=new ArrayList<Double>();var targetLengths=new HashMap<String,Long>();
        for(var r:p.data().regions()){
            allTargets.put(r.alias(),r);
            if(r.quality()==HomerGenomicSequences.Disposition.RETAINED){var g=Geometry.of(r);eligible.computeIfAbsent(g,k->new ArrayList<>()).add(r);Long previous=targetLengths.put(g.prefix(),g.end0()-g.start0());if(previous!=null&&previous!=g.end0()-g.start0())throw new IOException("New final identity loses distinct same-start target lengths");gc.add(HomerGeneratedSequences.newGc(r.dna()));}
            else rows.add(r);
        }
        HomerGenomeAudit.positions(dir.resolve("target"+(plan.width()==0?"given":Integer.toString(plan.width()))+".pos"),allTargets,true);
        double[] values=new double[gc.size()];for(int i=0;i<values.length;i++)values[i]=gc.get(i);
        double[] thresholds=HomerGeneratedSequences.newBinThresholds(values,p.settings().bins());
        var hashes=new LinkedHashMap<>(p.data().hashes());hashes.put(work.resolve("reference.tsv"),FollowupSupport.hash(work.resolve("reference.tsv")));
        for(String role:List.of("target","bg"))for(String suffix:List.of("positions.bed","stats.txt","sequences.fasta","freq.txt")){
            Path file=dir.resolve("homerBg."+role+"."+suffix);HomerFasta.regular(file,limits.workspace());hashes.put(file,FollowupSupport.hash(file));
        }
        var budget=HomerGeneratedBackground.budget(work,limits);long deadline=System.nanoTime()+limits.timeout()*1000000000L;
        HomerRuntime.Verify check=()->{if(System.nanoTime()>deadline)throw new IOException("Native new-background audit timed out;review explicit timeout and populations");p.runtime().marker();};
        Map<String,Selected> target,bg;
        try(var reference=new IndexedSequences(work.resolve("stage-genome.fa"))){
            target=population(dir,"target",p,reference,thresholds,limits,check);
            bg=population(dir,"bg",p,reference,thresholds,limits,check);
        }
        var expectedCounts=new HashMap<Geometry,Integer>();eligible.forEach((g,list)->expectedCounts.put(g,list.size()));
        var observedCounts=new HashMap<Geometry,Integer>();for(var s:target.values())observedCounts.merge(s.geometry(),1,Integer::sum);
        if(!observedCounts.equals(expectedCounts))throw new IOException("Original new target stats/FASTA omit or add native eligible geometries/multiplicities");
        profiles(dir,"target",target.values().stream().map(Selected::dna).toList(),p,limits);
        profiles(dir,"bg",bg.values().stream().map(Selected::dna).toList(),p,limits);
        var groups=HomerGenomeAudit.groups(dir.resolve("group.adj"));if(groups.size()>limits.records()||groups.size()!=target.size()+bg.size())throw new IOException("Original new final group exceeds capacity or changes selected population size");
        var finalTargets=new HashMap<Geometry,Integer>();var seen=new HashSet<String>();long extracted=0;long backgrounds=0,ambiguous=0,targetIndex=0,bgIndex=0;
        for(var r:rows)extracted=Math.addExact(extracted,r.dna().length());
        try(var reader=HomerCoreAudit.reader(dir.resolve("seq.tsv"));var ledger=budget.writer(work.resolve("generated-target-mapping.tsv"));var selected=budget.writer(work.resolve("generated-selected.tsv"))){
            ledger.write("final_runtime_id\tstaged_contig\tstart0\tend0\tstrand\tcompatible_input_aliases_json\tidentity_status\n");
            selected.write("final_runtime_id\trole\tstaged_contig\tstart0\tend0\tstrand\tnative_bases\tnative_gc\tnative_bin\tinitial_weight\n");
            for(String line;(line=HomerCoreAudit.next(reader,limits.line()))!=null;){
                String[] f=line.split("\t",-1);if(f.length!=2||!groups.containsKey(f[0])||!seen.add(f[0])||seen.size()>limits.records())throw new IOException("Malformed/duplicate/ungrouped original new final sequence");
                boolean isTarget=groups.get(f[0])[0].equals("1");Geometry geometry;String dna;HomerGenomeData.Region original=null;int bin;
                if(isTarget){
                    int last=f[0].lastIndexOf('_');if(last<0)throw new IOException("Malformed new target identity");Long targetLength=targetLengths.get(f[0].substring(0,last+1));if(targetLength==null)throw new IOException("Original new target ID absent from native eligible starts/strands");geometry=identity(f[0],p,++targetIndex-1,targetLength);var candidates=eligible.get(geometry);
                    if(candidates==null)throw new IOException("Original new final target names an ineligible geometry");
                    int n=finalTargets.merge(geometry,1,Integer::sum);if(n>candidates.size())throw new IOException("Original new final target exceeds native geometry multiplicity");
                    dna=candidates.get(0).dna();bin=HomerGeneratedSequences.newBin(HomerGeneratedSequences.newGc(dna),thresholds);
                    if(candidates.size()==1)original=candidates.get(0);else ambiguous++;
                    ledger.write(f[0]+"\t"+geometry.contig()+"\t"+geometry.start0()+"\t"+geometry.end0()+"\t"+geometry.strand()+"\t"+RegenieRuntime.json(candidates.stream().map(HomerGenomeData.Region::alias).toList())+"\t"+(original==null?"geometry-multiplicity;unique-source-assignment-unavailable":"unique-geometry")+"\n");
                }else{
                    var s=bg.get(f[0]);if(s==null)throw new IOException("Original new final background absent from selected stats/FASTA");geometry=s.geometry();dna=s.dna();bin=s.bin();identity(f[0],p,++bgIndex-1,p.fragment());backgrounds++;
                }
                if(!dna.equals(f[1]))throw new IOException("Original new final DNA differs from complete native extraction: "+f[0]);
                extracted=Math.addExact(extracted,dna.length());if(rows.size()>=limits.records()||extracted>limits.extracted()||extracted*4+rows.size()*1024L>Runtime.getRuntime().maxMemory()/2)throw new IOException("Original new selected native population exceeds record/residue/heap capacity");
                var span=geometry.span();rows.add(new HomerGenomeData.Region(f[0],f[0],isTarget?"targets":"background",original!=null?original.ordinal():isTarget?0:backgrounds,original!=null?original.sourceId():isTarget?"compatible-source-aliases:generated-target-mapping.tsv":f[0],geometry.contig(),original!=null?original.original():span,span,dna,false,HomerGenomicSequences.Disposition.RETAINED,HomerGenomicSequences.frequency(dna),"pending",1));
                selected.write(f[0]+"\t"+(isTarget?"targets":"background")+"\t"+geometry.contig()+"\t"+geometry.start0()+"\t"+geometry.end0()+"\t"+geometry.strand()+"\t"+dna.length()+"\t"+HomerGeneratedSequences.newGc(dna)+"\t"+bin+"\t1\n");
                if((seen.size()&1023)==0)check.run();
            }
        }
        if(!seen.equals(groups.keySet())||!finalTargets.equals(expectedCounts)||backgrounds!=bg.size())throw new IOException("Original new final sequence/group population changes native target multiplicities or selected backgrounds");
        overlap(bg,eligible,o,p.settings().allowSelfOverlap());HomerGeneratedBackground.count(p,backgrounds,p.settings().count(),session,warnings);
        session.put("generated_native_target_geometry_multiplicity_audit","passed-all-input-targets-and-mode-specific-quality-exclusions");session.put("generated_ambiguous_target_source_assignments",ambiguous);
        session.put("generated_native_selected_background_audit","passed-all-selected-coordinates-DNA-GC-bins-and-unit-initial-weights");session.put("generated_native_kmer_profile_audit","passed-complete-target-and-selected-background-canonical-frequencies");
        warnings.add("NEW GENERATED IDENTITY AND MODEL REVIEW: the original5.1 selector discards input target IDs and reindexes outputs after GC-bin selection. Native checks join exact geometry and multiplicity;ambiguous source identities="+ambiguous+". All compatible source aliases remain in generated-target-mapping.tsv and original rows in regions.tsv. Target/background stats,BED,FASTA and final sequences were checked against native extraction;fixed N fraction0.5,GC bins and complete canonical kmer frequencies were checked. Initial weights are1 before optional oligonucleotide normalization. Required action: retain the geometry mapping and all original files;ProfileScore is an upstream matching score,not P/q/E,and neither the optimizer nor random selection probabilities are reconstructed.");
        warnings.add("NEW GENERATED OVERLAP REVIEW: target exclusion uses all retained target geometries on both strands and inclusive source windows [start0,end0],so touching counts as overlap. Background self-overlap policy="+(p.settings().allowSelfOverlap()?"allow":"forbid")+" uses the original equal-length start-distance rule. Required action: inspect generated-selected.tsv and original positions;this audit checks the actual selected population,not the full unretained adaptive candidate stream. NN is a screening request rather than a hard candidate cap.");
        check.run();return HomerGenomeData.finish(rows,p.data().known(),p.data().comparison(),hashes,p.data().duplicates(),p.longest(),o,plan,limits,work);
    }

    private static Map<String,Selected> population(Path dir,String role,HomerGeneratedBackground.Prepared p,IndexedSequences reference,double[] thresholds,HomerGenomeData.Limits limits,HomerRuntime.Verify check)throws Exception {
        var result=new LinkedHashMap<String,Selected>();long bases=0,index=0;
        try(var bed=HomerCoreAudit.reader(dir.resolve("homerBg."+role+".positions.bed"));var stats=HomerCoreAudit.reader(dir.resolve("homerBg."+role+".stats.txt"));var fasta=HomerCoreAudit.reader(dir.resolve("homerBg."+role+".sequences.fasta"))){
            if(!"SeqID\tchr\tposition\tstrand\tweight\tProfileScore\tGC\tbin".equals(HomerCoreAudit.next(stats,limits.line())))throw new IOException("Original new stats header changed");
            for(;;){String b=HomerCoreAudit.next(bed,limits.line()),s=HomerCoreAudit.next(stats,limits.line()),h=HomerCoreAudit.next(fasta,limits.line());if(b==null||s==null||h==null){if(b!=null||s!=null||h!=null)throw new IOException("Original new BED/stats/FASTA populations differ");break;}
                String[] f=b.split("\t",-1),g=s.split("\t",-1);if(f.length!=6||g.length!=8||result.size()>=limits.records()||!f[4].equals("1.000000")||!g[4].equals("1.000000"))throw new IOException("Malformed/oversized/nonunit new selected population");
                long start=HomerGeneratedBackground.integer(f[1],0,Integer.MAX_VALUE),end=HomerGeneratedBackground.integer(f[2],0,Integer.MAX_VALUE);Geometry geometry=identity(f[3],p,index++,end-start);
                if(!geometry.equals(new Geometry(f[0],start,end,strand(f[5])))||!g[0].equals(f[3])||!g[1].equals(f[0])||!g[2].equals(f[1])||!g[3].equals(f[5])||!h.equals(">"+f[3]+" 1.000000"))throw new IOException("Original new BED/stats/FASTA geometry/identity differs");
                if(role.equals("bg")&&(end-start!=p.fragment()||start>=p.lengths().get(f[0])-p.fragment()))throw new IOException("Original new background outside its strict j < contigLength-fragment candidate geometry");
                String dna=HomerGenomicSequences.extract(reference.sequence(geometry.contig()),new HomerGenomicSequences.Interval(start+1,end,geometry.strand()),false,100001);
                if(HomerGeneratedSequences.newSelectionDisposition(dna)!=HomerGenomicSequences.Disposition.RETAINED)throw new IOException("Original new selected population violates fixed N filter");
                bases=Math.addExact(bases,dna.length());if(bases>limits.extracted()||bases*4+result.size()*1024L>Runtime.getRuntime().maxMemory()/2)throw new IOException("Original new population audit exceeds residue/heap capacity");
                if(!dna.equals(HomerCoreAudit.next(fasta,limits.line())))throw new IOException("Original new selected FASTA differs from native exact reference extraction");
                double gc=HomerGeneratedSequences.newGc(dna);int bin=HomerGeneratedSequences.newBin(gc,thresholds);
                if(Math.abs(HomerGenomeAudit.value(g[6])-gc)>.000000501||HomerGeneratedBackground.integer(g[7],0,p.settings().bins()-1)!=bin)throw new IOException("Original new GC/bin differs from native full-precision rank thresholds");
                HomerGenomeAudit.value(g[5]);if(result.put(f[3],new Selected(geometry,dna,bin))!=null)throw new IOException("Duplicate original new selected identity");if((index&1023)==0)check.run();
            }
        }
        if(result.isEmpty())throw new IOException("Empty original new selected "+role+" population");return result;
    }

    private static Geometry identity(String id,HomerGeneratedBackground.Prepared p,long index,long knownLength)throws IOException {
        String[] f=id.split("_",-1);if(f.length!=4||!p.lengths().containsKey(f[0]))throw new IOException("Malformed original new generated identity");
        long start=HomerGeneratedBackground.integer(f[1],0,Integer.MAX_VALUE);char strand=f[2].equals("0")?'+':f[2].equals("1")?'-':'?';
        if(strand=='?'||HomerGeneratedBackground.integer(f[3],0,Integer.MAX_VALUE)!=index)throw new IOException("Original new identity strand/order changed");
        long length=knownLength;
        if(length<1||start+length>p.lengths().get(f[0]))throw new IOException("Original new selected identity outside reference or absent from eligible targets");return new Geometry(f[0],start,start+length,strand);
    }
    private static char strand(String value)throws IOException{if(value.equals("+"))return '+';if(value.equals("-"))return '-';throw new IOException("Invalid original new BED strand");}

    private static void profiles(Path dir,String role,List<String> dna,HomerGeneratedBackground.Prepared p,HomerGenomeData.Limits limits)throws Exception {
        int kmer=Math.max(1,p.settings().kmer()),columns=1<<(2*kmer);double[][] nativeProfile=HomerGeneratedSequences.newKmerProfile(dna,kmer,p.settings().positional(),p.fragment());
        try(var reader=HomerCoreAudit.reader(dir.resolve("homerBg."+role+".freq.txt"))){
            String header=HomerCoreAudit.next(reader,limits.line());var expected=new StringBuilder("Position");for(int j=0;j<columns;j++){expected.append('\t');for(int k=0;k<kmer;k++)expected.append("ACGT".charAt((j>>(2*k))&3));}if(!expected.toString().equals(header))throw new IOException("Original new kmer profile column order differs");
            for(int i=0;i<nativeProfile.length;i++){String line=HomerCoreAudit.next(reader,limits.line());if(line==null)throw new IOException("Original new profile omits rows");String[] f=line.split("\t",-1);if(f.length!=columns+1||!f[0].equals(p.settings().positional()?Integer.toString(i):"PositionIndependent"))throw new IOException("Original new profile position schema differs");for(int j=0;j<columns;j++)if(Math.abs(HomerGenomeAudit.value(f[j+1])-nativeProfile[i][j])>.000000501)throw new IOException("Original new kmer profile differs from complete native selected sequence frequencies");}if(HomerCoreAudit.next(reader,limits.line())!=null)throw new IOException("Original new profile adds rows");
        }
    }
    private static void overlap(Map<String,Selected> bg,Map<Geometry,List<HomerGenomeData.Region>> targets,Map<String,String> o,boolean allowSelf)throws IOException {
        var spans=new HashMap<String,List<long[]>>();for(var g:targets.keySet())spans.computeIfAbsent(g.contig(),k->new ArrayList<>()).add(new long[]{g.start0(),g.end0()});var index=new HashMap<String,HomerGenomeData.OverlapIndex>();spans.forEach((chr,list)->index.put(chr,new HomerGenomeData.OverlapIndex(list)));
        var byChr=new HashMap<String,List<Geometry>>();for(var s:bg.values()){var g=s.geometry();if(o.get("background-overlap").equals("remove-original")&&index.containsKey(g.contig())&&index.get(g.contig()).overlaps(new long[]{g.start0(),g.end0()}))throw new IOException("Original new selected background violates retained-target inclusive overlap exclusion");byChr.computeIfAbsent(g.contig(),k->new ArrayList<>()).add(g);}
        if(!allowSelf)for(var list:byChr.values()){list.sort(java.util.Comparator.comparingLong(Geometry::start0));for(int i=1;i<list.size();i++)if(list.get(i).start0()<=list.get(i-1).end0())throw new IOException("Original new selected background violates requested self-overlap exclusion");}
    }
}
