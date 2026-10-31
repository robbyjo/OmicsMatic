/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.HomerGeneratedSequences;
import org.jlinalg.sequence.HomerGenomicSequences;

/** Explicit generated-background choices and complete selected-population audits. */
final class HomerGeneratedBackground {
    private HomerGeneratedBackground() { }
    static final Set<String> OPTIONS=Set.of("generated-admission","background-total","background-count","background-candidates","background-bins","background-sub-bins","background-kmer","background-kmer-mode","background-self-overlap","background-randomness","background-boundary-policy","background-count-policy","maximum-background-pool-records","maximum-background-pool-bases","generated-sort-memory-bytes","maximum-background-virtual-bytes");
    private static final Set<String> OLD_ONLY=Set.of("background-total","background-boundary-policy","maximum-background-pool-records","maximum-background-pool-bases","generated-sort-memory-bytes");
    private static final Set<String> NEW_ONLY=Set.of("background-count","background-candidates","background-bins","background-sub-bins","background-kmer","background-kmer-mode","maximum-background-virtual-bytes");
    record Settings(String mode,Path admission,int total,int count,long candidates,int bins,int subBins,
                    int kmer,boolean positional,boolean allowSelfOverlap,String boundary,String countPolicy,
                    long poolRecords,long poolBases,long sortMemory,long virtualBytes) {
        boolean old(){return mode.equals("generated-old");}
        static Settings read(Map<String,String> o,HomerGenomeCli.Plan plan)throws IOException{
            String mode=HomerGeneratedBackground.mode(o);if(mode.equals("supplied"))throw new IOException("Generated settings require an explicit generated mode");boolean old=mode.equals("generated-old");
            for(String key:old?NEW_ONLY:OLD_ONLY)if(o.containsKey(key))throw new IOException("Remove unused --"+key+" for "+mode);
            if(o.containsKey("background")||o.containsKey("background-format"))throw new IOException("Generated backgrounds do not consume supplied background/format;remove those options");
            require(o,"background-randomness",Set.of("original-unseeded"));require(o,"background-count-policy",Set.of("exact","retain-original"));
            Path admission=Path.of(FollowupSupport.required(o,"generated-admission")).toAbsolutePath().normalize();
            int total=old?explicit(o,"background-total",2,1000000):0,count=old?0:explicit(o,"background-count",2,1000000);
            long candidates=old?0:explicit(o,"background-candidates",0,100000000);
            int bins=old?0:explicit(o,"background-bins",1,100),sub=old?0:explicit(o,"background-sub-bins",1,100),kmer=old?0:explicit(o,"background-kmer",0,4);
            if(old){
                require(o,"background-boundary-policy",Set.of("error","retain-original"));
                if(o.containsKey("background-self-overlap")&&!o.get("background-self-overlap").equals("original-allow"))throw new IOException("Old generated selection has original-allow self-overlap semantics");
            }else{
                require(o,"background-kmer-mode",Set.of("overall","positional"));require(o,"background-self-overlap",Set.of("forbid","allow"));
                if(!o.get("weighting").equals("none"))throw new HomerRuntime.Unavailable("New5.1 selection always matches GC bins and optional kmer profiles;it does not use old GC/CpG group weights. Choose weighting none explicitly to mean no additional old group weighting");
                if(!o.get("mask-lowercase").equals("no"))throw new HomerRuntime.Unavailable("Original new5.1 wrapper does not forward lowercase masking to its background generator;mask-lowercase yes is unsupported,not silently ignored");
                if(plan.maximumN()!=.5)throw new HomerRuntime.Unavailable("Original new5.1 background generator uses fixed maximum N fraction0.5 for both roles;requested maximum-n differs and cannot be forwarded by the wrapper");
            }
            long poolRecords=old?HomerCoreData.positive(o,"maximum-background-pool-records",10000000):0,poolBases=old?HomerCoreData.positive(o,"maximum-background-pool-bases",4000000000L):0,sort=old?HomerCoreData.positive(o,"generated-sort-memory-bytes",33554432):0,virtual=old?0:HomerCoreData.positive(o,"maximum-background-virtual-bytes",137438953472L);
            if(old&&(poolRecords>100000000||sort<1048576||sort>Runtime.getRuntime().maxMemory()/4))throw new IOException("Generated pool/sort capacity invalid;review explicit heap and memory limits");
            return new Settings(mode,admission,total,count,candidates,bins,sub,kmer,!old&&o.get("background-kmer-mode").equals("positional"),!old&&o.get("background-self-overlap").equals("allow"),old?o.get("background-boundary-policy"):"not-applicable",o.get("background-count-policy"),poolRecords,poolBases,sort,virtual);
        }
    }
    record Prepared(Settings settings,HomerGeneratedRuntime.Context runtime,HomerGenomeData.Data data,
                    Map<String,Long> lengths,long referenceBases,long longest,int fragment,int qualifiedTargets) { }
    static String mode(Map<String,String> o)throws IOException{
        String value=FollowupSupport.required(o,"background-mode");if(!Set.of("supplied","generated-old","generated-new").contains(value))throw new HomerRuntime.Unavailable("Requested genomic background mode is unavailable: "+value+";no different background is substituted");return value;
    }
    static void rejectSuppliedExtras(Map<String,String> o)throws IOException{for(String key:OPTIONS)if(o.containsKey(key))throw new IOException("Remove generated-only --"+key+" for supplied background");}
    private static int explicit(Map<String,String> o,String key,int min,int max)throws IOException{return HomerGenomeCli.integerText(FollowupSupport.required(o,key),min,max);}
    private static void require(Map<String,String> o,String key,Set<String> values)throws IOException{HomerGenomeCli.choice(o,key,values);}
    static void arguments(List<String> args,Map<String,String> o){
        String mode=o.get("background-mode");
        if(mode.equals("supplied")){args.addAll(List.of("-bg","stage-background.pos"));return;}
        if(mode.equals("generated-old")){args.addAll(List.of("-N",o.get("background-total"),"-preparse"));return;}
        args.addAll(List.of("-useNewBg","-N",o.get("background-count"),"-NN",o.get("background-candidates"),"-numBins",o.get("background-bins"),"-numSubBins",o.get("background-sub-bins"),o.get("background-kmer-mode").equals("positional")?"-pkmer":"-ikmer",o.get("background-kmer")));
        if(o.get("background-overlap").equals("retain"))args.add("-allowTargetOverlaps");if(o.get("background-self-overlap").equals("allow"))args.add("-allowBgOverlaps");
    }
    static Prepared prepare(Map<String,String> o,HomerGenomeCli.Plan plan,HomerGenomeData.Limits limits,HomerGenomeRuntime.Context base,Path work,Map<String,Object> session,List<String> warnings)throws Exception{
        Settings settings=Settings.read(o,plan);var runtime=HomerGeneratedRuntime.open(base,settings.admission,settings.mode);runtime.record(session);if(settings.old())oldRemovesTargets(o,base.version());var data=HomerGenomeData.targets(o,plan,limits,base,work);
        var lengths=new LinkedHashMap<String,Long>();long reference=0,longest=0;
        try(var reader=HomerCoreAudit.reader(work.resolve("reference.tsv"))){if(!"staged_contig\tsource_id_json\tfull_source_header_json\tlength".equals(HomerCoreAudit.next(reader,limits.line())))throw new IOException("Reference dictionary schema changed");for(String line;(line=HomerCoreAudit.next(reader,limits.line()))!=null;){String[] f=line.split("\t",-1);if(f.length!=4)throw new IOException("Reference dictionary malformed");long length=Long.parseLong(f[3]);if(length<1||length>Integer.MAX_VALUE||lengths.put(f[0],length)!=null)throw new IOException("Reference dictionary duplicate/bounds invalid");reference=Math.addExact(reference,length);longest=Math.max(longest,length);}}
        int qualified=0;long totalSpan=0,totalLength=0,extracted=0,maximumTargetLength=0;
        var targetLengths=new java.util.HashMap<String,Long>();var gc=new ArrayList<Double>();for(var region:data.regions()){
            totalSpan=Math.addExact(totalSpan,region.original().end1()-region.original().start1());extracted=Math.addExact(extracted,region.dna().length());
            if(!settings.old()&&(region.extraction().start1()<1||region.extraction().end1()>lengths.get(region.contig())))throw new HomerRuntime.Unavailable("Original new generator omits resized targets outside the reference;this operation requires extraction spans wholly within their contigs. Choose an appropriate explicit size/reference or retain SKIP");
            if(region.quality()==HomerGenomicSequences.Disposition.RETAINED&&region.frequency()!=null){qualified++;totalLength=Math.addExact(totalLength,region.dna().length());maximumTargetLength=Math.max(maximumTargetLength,region.dna().length());if(!settings.old()){gc.add(HomerGeneratedSequences.newGc(region.dna()));String key=region.contig()+"_"+region.extraction().start1()+"_"+region.extraction().strand();Long previous=targetLengths.put(key,region.extraction().length());if(previous!=null&&previous!=region.extraction().length())throw new HomerRuntime.Unavailable("New final IDs discard end coordinates and input identities;distinct same-start/same-strand target lengths cannot be associated uniquely. Choose unambiguous explicit regions/size or retain SKIP;targets are not renamed or dropped");}}
        }
        if(qualified<1)throw new IOException("Generated foreground has no eligible target sequences after the original mode-specific quality filter");
        int fragment=settings.old()?(plan.width()>0?plan.width():(int)(totalSpan/data.regions().size())):(int)(totalLength/qualified);
        long desired=settings.old()?settings.total-qualified:settings.count;
        if(fragment<1||!settings.old()&&fragment<2)throw new IOException("Original generated fragment size is too small;review exact target spans");
        if(settings.old()&&settings.total<2L*qualified)throw new IOException("Original old wrapper would increase N to twice eligible targets;choose background-total >= "+(2L*qualified)+" explicitly");
        if(data.regions().size()+desired+11>limits.records())throw new IOException("Requested generated population plus original quota-rounding reserve exceeds maximum-records;increase the explicit capacity where appropriate");
        long maxBackgroundLength=settings.old()?2L*(fragment/2)+1+fragment/2+1:fragment;
        if(Math.addExact(extracted,Math.multiplyExact(desired+11,maxBackgroundLength))>limits.extracted())throw new IOException("Generated extracted-residue estimate exceeds maximum-extracted-bases");
        var estimates=new LinkedHashMap<String,Object>();estimates.put("reference_bases",reference);estimates.put("longest_contig",longest);estimates.put("original_fragment_size",fragment);estimates.put("qualified_targets_before_bin_support",qualified);
        if(settings.old()){
            long anchors=0,represented=0;for(long length:lengths.values()){long n=(long)Math.floor(10000.0*length/reference);anchors+=n;if(n>0)represented++;}
            if(anchors==0)throw new IOException("Original proportional10000-anchor allocation gives no contig an anchor;choose an appropriate explicit background operation/reference");
            long perAnchor=1+2L*(49999/fragment),upper=Math.min(Math.multiplyExact(anchors,perAnchor),2*anchors+(reference+50000*represented)/fragment+represented);
            if(longest>Integer.MAX_VALUE-100001L)throw new IOException("Old preparsing may overflow original32-bit coordinates near contig ends;unsupported contig length");
            if(upper>settings.poolRecords||Math.multiplyExact(upper,maxBackgroundLength)>settings.poolBases)throw new IOException("Original old candidate-pool estimate exceeds explicit pool record/residue limits");
            estimates.put("anchor_allocation_before_random_collisions",anchors);estimates.put("candidate_record_upper_estimate",upper);estimates.put("pool_residue_upper_estimate",upper*maxBackgroundLength);
        }else{
            if(longest>=400000000)throw new IOException("Original5.1 new generator has a400000000-byte per-contig buffer;contig length exceeds its safe bound");
            boolean possible=false;for(long length:lengths.values())if(length>fragment)possible=true;if(!possible)throw new IOException("Original new generator has no j < contigLength-fragment windows");
            double[] values=new double[gc.size()];for(int i=0;i<values.length;i++)values[i]=gc.get(i);double[] thresholds=HomerGeneratedSequences.newBinThresholds(values,settings.bins);session.put("new_negative_gc_thresholds",thresholds);
            long maximumCandidates=Math.multiplyExact(reference,2);if(settings.candidates==0&&maximumCandidates>Integer.MAX_VALUE)throw new HomerRuntime.Unavailable("All-candidate selection would exceed original32-bit iteration capacity;choose a positive screening request or another explicit operation");
            long estimatedCandidates=settings.candidates==0?maximumCandidates:Math.min(maximumCandidates,Math.addExact(4*settings.candidates,200002L*settings.bins));
            if(settings.positional&&maximumTargetLength>fragment)throw new HomerRuntime.Unavailable("Variable retained target lengths are unsupported for original new positional matching: its profiles allocate average-length rows but index individual sequence positions. Required action: select scientifically appropriate equal-length regions/size explicitly,choose overall matching,or retain SKIP;no source patch or target truncation is applied");
            // The upstream allocator reserves seqLen rows even for overall profiles.
            long profileRows=fragment,profileCells=Math.multiplyExact(Math.multiplyExact(profileRows,1L<<(2*Math.max(1,settings.kmer))),settings.bins);
            long resident=Math.addExact(134217728,Math.addExact(2*reference+longest,Math.addExact(Math.multiplyExact(estimatedCandidates+qualified,160),Math.multiplyExact(profileCells,64))));
            long nativeCells=Math.multiplyExact(settings.positional?fragment:1,1L<<(2*Math.max(1,settings.kmer)));if(nativeCells*8>Runtime.getRuntime().maxMemory()/8)throw new IOException("Native selected-profile audit exceeds heap capacity;review explicit heap,kmer and positional choices before original execution");
            long virtual=Math.addExact(32*reference,Math.addExact(resident,424000000));
            if(resident>limits.rss())throw new IOException("Original new generator resident-memory estimate="+resident+" exceeds maximum-upstream-memory-bytes;review available memory and explicit screening/bins/capacities");
            if(virtual>settings.virtualBytes)throw new IOException("Original new generator address-space estimate="+virtual+" exceeds maximum-background-virtual-bytes;NN does not remove genome-sized pointer reservations");
            String processLimits=Files.readString(Path.of("/proc/self/limits"));var match=java.util.regex.Pattern.compile("(?m)^Max address space\\s+(\\d+|unlimited)\\s+").matcher(processLimits);if(match.find()&&!match.group(1).equals("unlimited")&&Long.parseLong(match.group(1))<virtual)throw new HomerRuntime.Unavailable("Host address-space limit forbids original new generator allocation;raise only where permitted or retain SKIP");
            estimates.put("candidate_screening_request_is_not_a_hard_cap",settings.candidates);estimates.put("resident_memory_estimate",resident);estimates.put("address_space_estimate_including_untouched_pointer_reservations",virtual);
        }
        session.put("generated_preflight",estimates);session.put("generated_randomness","original-unseeded;actual candidate/selected populations retained;no seed or independent-draw equality is claimed");
        warnings.add("GENERATED BACKGROUND REVIEW: mode="+settings.mode+". Old N means target+background before unsupported-bin exclusions;new N means background sequences. Actual selected counts may differ and use explicit count-policy="+settings.countPolicy+". Old5.1 excludes literal inclusive target-interval overlaps,with strand matching under forward selection;legacy4.11/4.11.1 retains target overlaps and old self-overlaps are allowed. New selection uses fixed N fraction0.5,no forwarded lowercase mask,GC strata and optional kmer matching with unit initial weights. Required action: inspect generated ledgers and actual populations;do not infer selection probabilities or exact reproducibility from an unseeded source workflow.");
        return new Prepared(settings,runtime,data,java.util.Collections.unmodifiableMap(lengths),reference,longest,fragment,qualified);
    }
    static HomerGenomeData.Data complete(Prepared prepared,HomerGenomeCli.Plan plan,HomerGenomeData.Limits limits,Map<String,String> o,Path work,Map<String,Object> session,List<String> warnings)throws Exception{
        HomerGenomeAudit.diagnostics(work,prepared.runtime.base().version());prepared.runtime.verify();
        var result=prepared.settings.old()?HomerGeneratedOld.complete(prepared,plan,limits,o,work,session,warnings):HomerGeneratedNew.complete(prepared,plan,limits,o,work,session,warnings);
        prepared.runtime.verify();session.put("generated_coordinate_audit","passed-complete-native-selected-population");return result;
    }
    static void count(Prepared p,long selected,long desired,Map<String,Object> session,List<String> warnings)throws IOException{
        session.put("generated_requested_background",desired);session.put("generated_selected_background",selected);session.put("generated_count_policy",p.settings.countPolicy);
        if(selected==desired)return;
        String message="Original generated background selected "+selected+" versus requested "+desired+" background records. Required action: review availability,strata,matching and exact retained populations;use explicit background-count-policy retain-original only when this original contrast is acceptable,or choose scientifically appropriate parameters and NEW paths.";
        if(p.settings.countPolicy.equals("exact"))throw new IOException(message);warnings.add("GENERATED COUNT MISMATCH RETAINED: "+message);
    }
    static HomerCoreData.Budget budget(Path work,HomerGenomeData.Limits limits)throws IOException{var budget=new HomerCoreData.Budget(limits.workspace());try(var paths=Files.walk(work)){for(Path p:paths.filter(Files::isRegularFile).toList())budget.add(Files.size(p));}return budget;}
    static boolean oldRemovesTargets(Map<String,String> o,String version)throws HomerRuntime.Unavailable{
        boolean removes=version.equals("5.1");String required=removes?"remove-original":"retain";
        if(!required.equals(o.get("background-overlap")))throw new HomerRuntime.Unavailable("Original "+version+" old generated workflow requires explicit background-overlap "+required+(removes?";it always applies literal inclusive interval masking,same-strand for forward selection":";it does not filter generated backgrounds against target intervals")+". No target/background population is repaired or substituted");return removes;
    }
    static long integer(String text,long min,long max)throws IOException{try{if(!text.matches("-?[0-9]+"))throw new NumberFormatException();long value=Long.parseLong(text);if(value<min||value>max)throw new NumberFormatException();return value;}catch(NumberFormatException failure){throw new IOException("Generated coordinate/count outside supported range: "+text,failure);}}
}
