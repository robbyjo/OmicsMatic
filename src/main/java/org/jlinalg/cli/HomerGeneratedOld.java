/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jlinalg.sequence.HomerGeneratedSequences;
import org.jlinalg.sequence.HomerGenomicSequences;
import org.jlinalg.sequence.IndexedSequences;
import org.jlinalg.sequence.SeqtkMethods;

/** Complete native pool and actual-draw audit for the original old generator. */
final class HomerGeneratedOld {
    private HomerGeneratedOld() { }
    static HomerGenomeData.Data complete(HomerGeneratedBackground.Prepared p,HomerGenomeCli.Plan plan,HomerGenomeData.Limits limits,Map<String,String> o,Path work,Map<String,Object> session,List<String> warnings)throws Exception{
        var settings=p.settings();Path directory=work.resolve("original-out");var groups=HomerGenomeAudit.groups(directory.resolve("group.adj"));
        if(groups.size()>limits.records())throw new IOException("Generated final group exceeds maximum-records");
        var selected=new HashMap<String,String[]>();for(var entry:groups.entrySet())if(entry.getValue()[0].equals("0"))selected.put(entry.getKey(),entry.getValue());
        if(selected.size()<2)throw new IOException("Original old generation leaves fewer than two background records");
        var targets=new HashMap<String,HomerGenomeData.Region>();for(var r:p.data().regions())targets.put(r.alias(),r);
        HomerGenomeAudit.positions(directory.resolve("target"+(plan.width()==0?"given":Integer.toString(plan.width()))+".pos"),targets,true);
        String prefix="stage-genome.fa"+(o.get("mask-lowercase").equals("yes")?"r":"")+"."+p.fragment();Path pool=work.resolve("private-preparsed");
        var hashes=new LinkedHashMap<>(p.data().hashes());for(String suffix:List.of("pos","seq","cgfreq","cgbins","gcbins")){Path file=pool.resolve(prefix+"."+suffix);HomerFasta.regular(file,limits.workspace());hashes.put(file,FollowupSupport.hash(file));}hashes.put(work.resolve("reference.tsv"),FollowupSupport.hash(work.resolve("reference.tsv")));
        var budget=HomerGeneratedBackground.budget(work,limits);long deadline=System.nanoTime()+limits.timeout()*1000000000L;long[] checks={0};
        HomerRuntime.Verify check=()->{if(System.nanoTime()>deadline)throw new IOException("Native old-background audit timed out;review explicit timeout and pool capacities");if((++checks[0]&63)==0)p.runtime().marker();};
        var sorter=new HomerGeneratedSort(work.resolve("generated-sort"),budget,settings.sortMemory(),limits.line(),settings.poolRecords(),check);
        Path expected=work.resolve("generated-pool-native.unsorted.tsv"),identities=work.resolve("generated-pool-identities.unsorted.tsv");
        var index=new HashMap<String,HomerGenomeData.OverlapIndex>();var spans=new HashMap<String,List<long[]>>();boolean sameStrand=o.get("strands").equals("forward"),removesTargets=HomerGeneratedBackground.oldRemovesTargets(o,p.runtime().base().version());
        if(removesTargets)for(var r:p.data().regions()){String key=r.contig()+(sameStrand?Character.toString(r.extraction().strand()):"");spans.computeIfAbsent(key,k->new ArrayList<>()).add(new long[]{r.extraction().start1(),r.extraction().end1()});}spans.forEach((key,value)->index.put(key,new HomerGenomeData.OverlapIndex(value)));
        var available=new HashMap<Integer,Long>();var chosen=new HashMap<Integer,Long>();var targetBins=new HashMap<Integer,Long>();
        for(var r:p.data().regions())if(r.quality()==HomerGenomicSequences.Disposition.RETAINED&&r.frequency()!=null)targetBins.merge(bin(o,r.frequency()),1L,Long::sum);
        var rows=new ArrayList<>(p.data().regions());long raw=0,retained=0,bases=0,selectedCount=0,selectedBoundaries=0,excludedOverlap=0,notProduced=0;long extracted=0;for(var r:rows)extracted+=r.dna().length();
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var reference=new IndexedSequences(work.resolve("stage-genome.fa"));var reader=HomerCoreAudit.reader(pool.resolve(prefix+".pos"));var nativeRows=budget.writer(expected);var ids=budget.writer(identities);var ledger=budget.writer(work.resolve("generated-pool.tsv"))){
            ledger.write("original_id\tstaged_contig\traw_start1\traw_end1\tstrand\tboundary\tdisposition\tquality_retained\tpasses_original_version_overlap_rule\tselected\tnative_bases\n");
            String current=null;SeqtkMethods.Bases sequence=null;
            for(String line;(line=HomerCoreAudit.next(reader,limits.line()))!=null;){
                if(++raw>settings.poolRecords())throw new IOException("Old generated candidate record capacity exceeded");String[] f=line.split("\t",-1);if(f.length!=5||!p.lengths().containsKey(f[1]))throw new IOException("Malformed old preparsed position/contig");
                long start=HomerGeneratedBackground.integer(f[2],-100001,Integer.MAX_VALUE),end=HomerGeneratedBackground.integer(f[3],-100001,Integer.MAX_VALUE);char strand=f[4].equals("0")?'+':f[4].equals("1")?'-':'?';if(strand=='?'||end-start!=2L*(p.fragment()/2))throw new IOException("Original preparsed span/strand differs from declared fragment size");
                if(!f[1].equals(current)){current=f[1];sequence=reference.sequence(current);if(sequence.length()!=p.lengths().get(current))throw new IOException("Reference dictionary length changed");}
                boolean boundary=start<1||end>sequence.length();String dna=HomerGeneratedSequences.oldPreparsedExtraction(sequence,start,end,strand,o.get("mask-lowercase").equals("yes"),200004);boolean produced=dna!=null;
                var quality=produced?HomerGenomicSequences.disposition(dna,.7):null;boolean good=quality==HomerGenomicSequences.Disposition.RETAINED;
                if(produced){bases=Math.addExact(bases,dna.length());if(bases>settings.poolBases())throw new IOException("Old generated native pool residue capacity exceeded");}else notProduced++;
                String key=current+(sameStrand?Character.toString(strand):"");boolean overlap=index.containsKey(key)&&index.get(key).overlaps(new long[]{start,end});boolean picked=selected.containsKey(f[0]);
                ids.write(f[0]+"\t"+start+"\t"+end+"\n");
                var frequency=good?HomerGenomicSequences.frequency(dna):null;
                if(good){
                    retained++;nativeRows.write(String.format(Locale.ROOT,"%s\t%s\t%.6f\t%.6f\t%.6f\t%.6f\t%d\t%d\t%d\n",f[0],hash(digest,dna),frequency.cpg(),frequency.gc(),frequency.ag(),frequency.ac(),frequency.canonicalBases(),frequency.cpgBin(),frequency.gcBin()));
                    if(!overlap)available.merge(bin(o,frequency),1L,Long::sum);else excludedOverlap++;
                }
                ledger.write(f[0]+"\t"+current+"\t"+start+"\t"+end+"\t"+strand+"\t"+(boundary?"original-extractor-boundary-behavior":"within-reference")+"\t"+(produced?quality:"not-produced-past-contig")+"\t"+good+"\t"+!overlap+"\t"+picked+"\t"+(produced?dna.length():"NA")+"\n");
                if(picked){
                    if(!good||overlap)throw new IOException("Original selected an ineligible old generated candidate: "+f[0]);
                    if(boundary){selectedBoundaries++;if(settings.boundary().equals("error"))throw new IOException("Selected original generated boundary region "+f[0]+" has raw coordinates "+start+","+end+" and produced length "+dna.length()+". Required action: inspect generated-pool.tsv and original inputs;choose background-boundary-policy retain-original only to retain these exact upstream padding/truncation semantics,or use an appropriate explicit contrast and NEW paths");}
                    if(rows.size()>=limits.records())throw new IOException("Generated selected region capacity exceeded");extracted=Math.addExact(extracted,dna.length());if(extracted>limits.extracted()||extracted*4+rows.size()*1024L>Runtime.getRuntime().maxMemory()/2)throw new IOException("Generated selected extraction/heap capacity exceeded");
                    var span=new HomerGenomeData.Span(start,end,strand);rows.add(new HomerGenomeData.Region(f[0],f[0],"background",++selectedCount,f[0],current,span,span,dna,false,quality,frequency,"pending",0));chosen.merge(bin(o,frequency),1L,Long::sum);
                }
                if((raw&1023)==0)check.run();
            }
        }
        if(selectedCount!=selected.size())throw new IOException("Original background group names a candidate absent from its complete preparsed position population");
        var expectedSorted=sorter.sort(expected,"expected-pool");sorter.sort(identities,"all-position-identities");
        Path actualHashes=work.resolve("generated-pool-original-hashes.tsv");
        try(var reader=HomerCoreAudit.reader(pool.resolve(prefix+".seq"));var writer=budget.writer(actualHashes)){
            long n=0;for(String line;(line=HomerCoreAudit.next(reader,limits.line()))!=null;){String[] f=line.split("\t",-1);if(f.length!=2||++n>settings.poolRecords())throw new IOException("Malformed/oversized original preparsed sequence population");writer.write(f[0]+"\t"+hash(digest,f[1])+"\n");if((n&1023)==0)check.run();}
        }
        var actualSorted=sorter.sort(actualHashes,"actual-pool");match(expectedSorted.path(),actualSorted.path(),new int[]{0,1},new int[]{0,1},limits.line(),check);
        var freq=sorter.sort(pool.resolve(prefix+".cgfreq"),"original-frequencies");match(expectedSorted.path(),freq.path(),new int[]{0,2,3,4,5,6},new int[]{0,1,2,3,4,5},limits.line(),check);
        var cpg=sorter.sort(pool.resolve(prefix+".cgbins"),"original-cpg-bins");match(expectedSorted.path(),cpg.path(),new int[]{0,7},new int[]{0,1},limits.line(),check);
        var gc=sorter.sort(pool.resolve(prefix+".gcbins"),"original-gc-bins");match(expectedSorted.path(),gc.path(),new int[]{0,8},new int[]{0,1},limits.line(),check);
        long desired=settings.total()-p.qualifiedTargets();Map<Integer,Long> quotas=HomerGeneratedSequences.oldSelectionQuotas(targetBins,available,desired);
        for(int bin=0;bin<=11;bin++)if(!quotas.getOrDefault(bin,0L).equals(chosen.getOrDefault(bin,0L)))throw new IOException("Original old selected-bin count differs from native availability/quota/descending-leftover audit,bin="+bin);
        HomerGeneratedBackground.count(p,selectedCount,desired,session,warnings);session.put("generated_pool_records",raw);session.put("generated_pool_quality_retained",retained);session.put("generated_pool_not_produced",notProduced);session.put("generated_pool_source_overlap_excluded",excludedOverlap);session.put("generated_selected_boundary_records",selectedBoundaries);session.put("generated_native_quota_audit",quotas);session.put("generated_complete_pool_dna_frequency_bin_audit","passed-native-DNA-SHA256-and-all-original-frequency/bin-fields");
        warnings.add("OLD GENERATED GEOMETRY REVIEW: preparsing used original unseeded proportional10000 reference anchors on the isolated custom FASTA,without a named TSS resource. Complete pool positions,quality dispositions,DNA hashes,frequencies and bins were checked natively. Version-specific target interval exclusion="+removesTargets+";5.1 uses literal inclusive intervals and forward requests apply same-strand masking,while4.11/4.11.1 retains overlaps. Selected boundary records="+selectedBoundaries+" under policy="+settings.boundary()+". Original left padding can extend the copied body,and right overhangs truncate;no coordinates or bases were repaired. Required action: inspect generated-pool.tsv and retained exact reference/staging;native quota agreement does not certify random-draw probabilities or biological calibration.");
        check.run();return HomerGenomeData.finish(rows,p.data().known(),p.data().comparison(),hashes,p.data().duplicates(),p.longest(),o,plan,limits,work);
    }
    private static int bin(Map<String,String> o,HomerGenomicSequences.Frequency f){return o.get("weighting").equals("none")?1:o.get("weighting").equals("gc")?f.gcBin():f.cpgBin();}
    private static String hash(MessageDigest digest,String dna){return java.util.HexFormat.of().formatHex(digest.digest(dna.getBytes(StandardCharsets.US_ASCII)));}
    private static void match(Path expected,Path actual,int[] left,int[] right,int lineLimit,HomerRuntime.Verify check)throws IOException{
        try(var a=HomerCoreAudit.reader(expected);var b=HomerCoreAudit.reader(actual)){
            long rows=0;for(;;){String x=HomerCoreAudit.next(a,lineLimit),y=HomerCoreAudit.next(b,lineLimit);if(x==null||y==null){if(x!=null||y!=null)throw new IOException("Original/native generated pool membership differs: "+actual);break;}String[] f=x.split("\t",-1),g=y.split("\t",-1);for(int i=0;i<left.length;i++)if(left[i]>=f.length||right[i]>=g.length||!f[left[i]].equals(g[right[i]]))throw new IOException("Original/native generated pool value differs for "+f[0]+" in "+actual);if((++rows&1023)==0)check.run();}
        }
    }
}
