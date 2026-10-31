/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.jlinalg.sequence.HomerEnrichment;
import org.jlinalg.sequence.HomerMotifs;
import static org.jlinalg.cli.FollowupSupport.*;

/** Original discovery planning, oligo admission and precision-aware final audits. */
final class HomerDiscovery {
    private HomerDiscovery() { }
    private static final Pattern COUNTS=Pattern.compile("T:([0-9]+\\.[0-9])\\(([0-9]+\\.[0-9]{2})%\\),B:([0-9]+\\.[0-9])\\(([0-9]+\\.[0-9]{2})%\\),P:1e(-?[0-9]+)");
    record Plan(int width,int mismatches,int motifs,int trials,int normalization,int normalizationIterations,
            String mode,int buffer,double expectedSites,double maxBackground,String seedText,
            int threads,int cacheMb,long maxNeighborhoods,long maxUpstreamBytes,long maxUniqueOligos) {
        static Plan read(Map<String,String> o,HomerCoreData.Limits limits)throws IOException{
            int width=whole(o,"motif-length",-1,1,50),mismatches=whole(o,"mismatches",-1,0,width),motifs=whole(o,"motifs-to-find",-1,1,limits.motifs());
            int threads=whole(o,"threads",1,1,256),trials=whole(o,"trials",10,2,4096);
            if(trials%threads!=0)throw new IOException("Choose --trials divisible by --threads; otherwise original HOMER changes the requested trial count");
            String normalization=required(o,"normalization");int n;
            if(normalization.equals("none"))n=0;
            else if(normalization.matches("oligo[1-9][0-9]?"))n=Integer.parseInt(normalization.substring(5));
            else throw new IOException("Discovery normalization must be none or oligo1..oligo15");
            if(n>15||n>width)throw new IOException("Normalization order must be <=15 and <=motif-length to preserve original signed integer indexing");
            String mode=required(o,"discovery-mode");if(!List.of("full-mask","quick-mask-fixed","quick-mask-optimize").contains(mode))throw new IOException("Choose discovery-mode full-mask,quick-mask-fixed or quick-mask-optimize");
            String buffer=o.getOrDefault("redundancy-buffer","auto");int blen=buffer.equals("auto")?(width<8?1:1+(width-8)/4):whole(o,"redundancy-buffer",-1,0,width);
            String expected=o.getOrDefault("expected-sites-per-base","auto");
            double rate=expected.equals("auto")?.005*(width<9?8:width==9?4:width==10?2:1):number(expected,"expected-sites-per-base",Double.MIN_VALUE,1);
            String seed=o.getOrDefault("minimum-seed-logp","auto");
            if(!seed.equals("auto")){double value=number(seed,"minimum-seed-logp",-1000000,0);if(value>-10.01&&value<-9.99)throw new IOException("Original treats seed logP near -10 as automatic; choose auto explicitly or a value outside that band");}
            if((long)width*motifs>limits.rows())throw new IOException("Requested discovery motif rows exceed maximum-motif-rows");
            return new Plan(width,mismatches,motifs,trials,n,whole(o,"normalization-iterations",160,1,10000),mode,blen,rate,
                    number(o.getOrDefault("maximum-background-fraction","0.5"),"maximum-background-fraction",0,1),seed,threads,whole(o,"stat-cache-mb",8,1,4096),
                    HomerCoreData.positive(o,"maximum-neighborhoods",1000000000),HomerCoreData.positive(o,"maximum-upstream-memory-bytes",1073741824),
                    HomerCoreData.positive(o,"maximum-unique-oligos",1000000));
        }
        List<String> command(Map<String,String> o){
            var args=new ArrayList<String>(List.of("denovo","-i","stage-targets.fa","-b","stage-background.fa","-len",Integer.toString(width),"-mis",Integer.toString(mismatches),"-S",Integer.toString(motifs),"-T",Integer.toString(trials),
                    "-p",Integer.toString(threads),"-strand",switch(o.get("strands")){case "forward"->"+";case "reverse"->"-";default->"both";},"-stat",o.get("statistic").equals("hypergeometric")?"hypergeo":"binomial",
                    "-olen",Integer.toString(normalization),"-omax",Integer.toString(normalizationIterations),"-cache",Integer.toString(cacheMb),"-blen",Integer.toString(buffer),"-e",Double.toString(expectedSites),
                    "-maxBack",Double.toString(maxBackground),"-minlp",seedText.equals("auto")?"-10":seedText,"-tmp","original-temporary.motif","-oligos","original-oligo-enrichment.tsv"));
            args.add(mode.equals("full-mask")?"-fullMask":"-quickMask");args.add(mode.equals("quick-mask-fixed")?"-nozoops":"-zoops");
            if(normalization>0)args.addAll(List.of("-oout","original-normalization.tsv"));return args;
        }
    }
    record Oligo(String representative,double target,double background) { }
    record Prepared(Plan plan,Map<String,Oligo> oligos,long rawWindows,long validWindows,long eligibleTargets,long eligibleBackground,
            long averageSequenceLength,double targetOligos,double backgroundOligos,double effectiveBackground,long nativeOperations,long estimatedUpstreamBytes) {
        Prepared {oligos=Map.copyOf(oligos);}
    }
    static Prepared prepare(HomerCoreData.Data data,Plan plan,Map<String,String> options,Path work)throws Exception{
        long residues=0,rawWindows=0,validWindows=0,eligibleT=0,eligibleB=0;for(var s:data.sequences())residues=Math.addExact(residues,s.dna().length());
        long average=residues/data.sequences().size();var good=new long[data.sequences().size()];double targetMass=0,backgroundMass=0;
        for(int i=0;i<data.sequences().size();i++){
            var s=data.sequences().get(i);rawWindows=Math.addExact(rawWindows,Math.max(0,s.dna().length()-plan.width+1));
            int consecutive=0;for(int j=0;j<s.dna().length();j++){consecutive=s.dna().charAt(j)=='N'?0:consecutive+1;if(consecutive>=plan.width)good[i]++;}
            validWindows=Math.addExact(validWindows,good[i]);if(good[i]==0)continue;
            double mass=Math.min(good[i],average);
            if(s.role().equals("targets")){eligibleT++;targetMass+=mass;}else{eligibleB++;backgroundMass+=mass;}
        }
        if(rawWindows<4||eligibleT<1||eligibleB<1||targetMass<=0||backgroundMass<=0)throw new IOException("Discovery requires candidate windows in both supplied roles and at least four total windows; inspect short/N-only records without silently removing them");
        double effectiveBg=backgroundMass/(targetMass/eligibleT);
        int minimum=options.get("statistic").equals("binomial")?2:1;
        if(effectiveBg<minimum||5*(eligibleT+effectiveBg)>Integer.MAX_VALUE)throw new IOException("Original coverage-adjusted discovery background/ZOOPS population is degenerate or exceeds signed integer capacity; inspect the full supplied sequence lengths/roles");
        long maxOligos=Math.min(rawWindows,plan.width<20?(long)((StrictMath.pow(4,plan.width)+10)/(data.strands()==HomerMotifs.Strands.BOTH?1.9:1)):Integer.MAX_VALUE);
        if(maxOligos>Integer.MAX_VALUE/5)throw new IOException("Original oligo prefix-tree signed indexing capacity would overflow");
        long treeBytes=Math.multiplyExact(maxOligos,256L+4L*plan.width+32L*plan.threads);
        long cache=(long)plan.cacheMb*1000000,normalizationBytes=plan.normalization==0?0:24L*(1L<<(2*plan.normalization));
        long normalizationRows=0;for(int i=1;i<=plan.normalization;i++)normalizationRows+=1L<<(2*i);
        if(Math.multiplyExact(normalizationRows,200)>data.limits().workspace()/4)throw new IOException("Normalization snapshot identity audit exceeds quarter-workspace memory allowance; review workspace/heap capacities");
        long finalCache=(long)Math.min(500000000.0,4.0*(data.targetCount()+data.backgroundCount()+1)*(data.targetCount()+data.backgroundCount()+2));
        long sites=plan.mode.equals("full-mask")?Math.multiplyExact(Math.multiplyExact(residues,10L),plan.motifs):0;
        long estimate=Math.addExact(Math.addExact(Math.multiplyExact(treeBytes,2),sites),Math.addExact(Math.addExact(cache,finalCache),Math.addExact(normalizationBytes,(long)(80*(eligibleT+effectiveBg)))));
        if(estimate>plan.maxUpstreamBytes)throw new IOException("Conservative discovery memory estimate exceeds maximum-upstream-memory-bytes; review explicit capacities before increasing them");
        BigInteger neighborhood=BigInteger.ONE,combination=BigInteger.ONE;
        for(int k=1;k<=plan.mismatches;k++){combination=combination.multiply(BigInteger.valueOf(plan.width-k+1)).divide(BigInteger.valueOf(k));neighborhood=neighborhood.add(combination.multiply(BigInteger.valueOf(3).pow(k)));}
        if(neighborhood.multiply(BigInteger.valueOf(maxOligos)).compareTo(BigInteger.valueOf(plan.maxNeighborhoods))>0)throw new IOException("Discovery mismatch-neighborhood estimate exceeds maximum-neighborhoods");
        long nativeWork=Math.multiplyExact(Math.multiplyExact(rawWindows,plan.width),3L+2L*plan.motifs*(data.strands()==HomerMotifs.Strands.BOTH?2:1));
        if(nativeWork>data.limits().operations())throw new IOException("Discovery oligo/presence-envelope audit exceeds maximum-operations");
        var values=new LinkedHashMap<String,double[]>();var representatives=new LinkedHashMap<String,String>();long retained=0;
        try(var ledger=data.budget().writer(work.resolve("discovery-input-eligibility.tsv"))){
            ledger.write("sequence_alias\trole\tresidues\tcandidate_windows\tcanonical_windows\tseed_phase_eligible\toligo_weight_per_window\tfinal_population_retained\n");
            for(int i=0;i<data.sequences().size();i++){
                var s=data.sequences().get(i);double weight=good[i]>average?(double)average/good[i]:1;
                ledger.write(s.alias()+"\t"+s.role()+"\t"+s.dna().length()+"\t"+Math.max(0,s.dna().length()-plan.width+1)+"\t"+good[i]+"\t"+(good[i]>0?1:0)+"\t"+weight+"\t1\n");
                String dna=data.strands()==HomerMotifs.Strands.REVERSE?reverse(s.dna()):s.dna();int consecutive=0;
                for(int j=0;j<dna.length();j++){
                    consecutive=dna.charAt(j)=='N'?0:consecutive+1;if(consecutive<plan.width)continue;
                    String word=dna.substring(j-plan.width+1,j+1),key=canonical(word,data.strands());double[] count=values.get(key);
                    if(count==null){retained=Math.addExact(retained,256L+4L*plan.width);if(values.size()>=plan.maxUniqueOligos||retained>data.limits().workspace()/4)throw new IOException("Native unique-oligo audit exceeds declared count or quarter-workspace memory allowance");count=new double[2];values.put(key,count);representatives.put(key,word);}
                    count[s.role().equals("targets")?0:1]+=weight;
                }
            }
        }
        var oligos=new TreeMap<String,Oligo>();values.forEach((k,v)->oligos.put(k,new Oligo(representatives.get(k),v[0],v[1])));
        try(var ledger=data.budget().writer(work.resolve("native-oligo-counts.tsv"))){
            ledger.write("canonical_key\tfirst_original_orientation\ttarget_weighted_occurrences\tbackground_weighted_occurrences_before_normalization\n");
            for(var e:oligos.entrySet())ledger.write(e.getKey()+"\t"+e.getValue().representative+"\t"+e.getValue().target+"\t"+e.getValue().background+"\n");
        }
        data.verify();HomerRuntime.bound(work,data.limits().workspace());
        return new Prepared(plan,oligos,rawWindows,validWindows,eligibleT,eligibleB,average,targetMass,backgroundMass,effectiveBg,nativeWork,estimate);
    }
    static long audit(Path work,HomerCoreData.Data data,Prepared prepared,Map<String,String> options,Map<String,Object> session,List<String> warnings)throws Exception{
        long unscored=auditOligos(work,data,prepared);boolean cleaned=false,finalized=false;
        try(var input=HomerCoreAudit.reader(work.resolve("original.stderr"))){
            for(String line;(line=HomerCoreAudit.next(input,65536))!=null;){
                cleaned|=line.contains("Cleaning up temporary sequence and group files:");finalized|=line.contains("Finalizing Enrichment Statistics")||line.contains("Finalizing enrichment with proper zoops scoring");
                for(String error:List.of("Out of room in oligo TREE","ERROR IN TREE GENERATION","ERROR WITH OLIGO CREATION","Ran out of room for more oligos","Something is wrong","X is not right in betai"))if(line.contains(error))throw new IOException("Original discovery reported an internal error: "+error+"; inspect retained original.stderr");
            }
        }
        if(!cleaned||!finalized)throw new IOException("Original discovery did not reach its final scoring/cleanup markers; exit zero alone is insufficient");
        Path source=work.resolve("original.stdout");List<HomerMotifs.Motif> motifs=Files.size(source)==0?List.of():HomerMotifs.read(source,new HomerMotifs.Limits(prepared.plan.motifs,prepared.plan.width,data.limits().rows(),data.limits().workspace(),data.limits().decoded(),data.limits().line(),data.limits().workspace()/4));
        Files.copy(source,work.resolve("learned-motifs.motif"));data.budget().add(Files.size(source));long ambiguous=0,nominalDifferences=0;double previous=Double.NEGATIVE_INFINITY;
        try(var results=data.budget().writer(work.resolve("results.tsv"));var models=data.budget().writer(work.resolve("learned-models.tsv"));var ledger=data.budget().writer(work.resolve("discovery-sequence-counts.tsv"))){
            results.write("motif_ordinal\tname_json\tconsensus_json\tprinted_threshold\tsource_log_p\tsource_coarse_p\tsource_target_with_motif\tsource_target_percent\tsource_background_with_motif\tsource_background_percent\tnative_log_p_from_source_counts\tdefinite_target_presence\tpossible_target_presence\tprinted_model_target_presence\tdefinite_background_presence\tpossible_background_presence\tprinted_model_background_presence\tpresence_audit\n");
            models.write("motif_ordinal\tname_json\twidth\tprinted_threshold\tsource_log_p\tcell_radius\tthreshold_radius\textra_source_header_json\n");
            ledger.write("motif_ordinal\tsequence_alias\trole\twindows\tambiguous_windows\tprinted_model_sites\tdefinite_internal_sites\tpossible_internal_sites\tbest_lower_score\tbest_upper_score\n");
            for(var motif:motifs){
                if(motif.width()!=prepared.plan.width||!motif.name().matches(motif.ordinal()+"-[ACGTRYSWKMBDHVN]{"+motif.width()+"}")||!motif.consensus().matches("[ACGTRYSWKMBDHVN]{"+motif.width()+"}")||motif.sourceLogP()==null||motif.extraHeader().size()<2||!motif.extraHeader().get(0).equals("0"))throw new IOException("Unexpected original discovery model identity,width or header schema");
                var match=COUNTS.matcher(motif.extraHeader().get(1));if(!match.matches())throw new IOException("Malformed original discovery count/percentage/coarse-P metadata");
                int t=count(match.group(1)),b=count(match.group(3));var counts=new HomerEnrichment.Counts(Math.toIntExact(data.targetCount()),t,Math.toIntExact(data.backgroundCount()),b);
                var statistic=options.get("statistic").equals("hypergeometric")?HomerEnrichment.Statistic.HYPERGEOMETRIC:HomerEnrichment.Statistic.BINOMIAL;
                var expected=HomerEnrichment.test(counts,statistic);
                if(Integer.parseInt(match.group(5))!=expected.originalDisplayExponent()||Math.abs(expected.logP()-motif.sourceLogP())>2e-6+1e-10*Math.abs(expected.logP()))throw new IOException("Original discovery final unweighted log P/coarse display differs from complete supplied populations and reported counts");
                if(Math.abs(Double.parseDouble(match.group(2))-100.0*t/counts.targets())>.005001||Math.abs(Double.parseDouble(match.group(4))-100.0*b/counts.background())>.005001)throw new IOException("Original discovery final percentages differ from complete supplied populations");
                if(motif.sourceLogP()<previous)throw new IOException("Original discovered motifs are not ordered by final log P");previous=motif.sourceLogP();
                var envelope=HomerDiscoveryIntervals.compile(motif);long[] definite={0,0},possible={0,0},nominal={0,0};
                for(var seq:data.sequences()){
                    var s=HomerDiscoveryIntervals.scan(seq.dna(),envelope,data.strands());int role=seq.role().equals("targets")?0:1;
                    if(s.definitePresence())definite[role]++;if(s.possiblePresence())possible[role]++;if(s.nominalSites()>0)nominal[role]++;
                    ledger.write(motif.ordinal()+"\t"+seq.alias()+"\t"+seq.role()+"\t"+s.windows()+"\t"+s.ambiguousWindows()+"\t"+s.nominalSites()+"\t"+s.definiteSites()+"\t"+s.possibleSites()+"\t"+finite(s.bestLowerScore())+"\t"+finite(s.bestUpperScore())+"\n");
                }
                if(t<definite[0]||t>possible[0]||b<definite[1]||b>possible[1])throw new IOException("Reported original discovery sequence presence lies outside the conservative printed-model round-trip envelope");
                boolean certain=definite[0]==possible[0]&&definite[1]==possible[1];if(!certain)ambiguous++;if(t!=nominal[0]||b!=nominal[1])nominalDifferences++;
                results.write(motif.ordinal()+"\t"+RegenieRuntime.json(motif.name())+"\t"+RegenieRuntime.json(motif.consensus())+"\t"+motif.threshold()+"\t"+motif.sourceLogP()+"\t1e"+match.group(5)+"\t"+match.group(1)+"\t"+match.group(2)+"%\t"+match.group(3)+"\t"+match.group(4)+"%\t"+expected.logP()+"\t"+definite[0]+"\t"+possible[0]+"\t"+nominal[0]+"\t"+definite[1]+"\t"+possible[1]+"\t"+nominal[1]+"\t"+(certain?"exact-presence-determined":"compatible-precision-uncertainty")+"\n");
                models.write(motif.ordinal()+"\t"+RegenieRuntime.json(motif.name())+"\t"+motif.width()+"\t"+motif.threshold()+"\t"+motif.sourceLogP()+"\t"+HomerDiscoveryIntervals.CELL_RADIUS+"\t"+HomerDiscoveryIntervals.THRESHOLD_RADIUS+"\t"+RegenieRuntime.json(motif.extraHeader())+"\n");
            }
        }
        session.put("learned_motifs",motifs.size());session.put("models_with_presence_uncertainty",ambiguous);session.put("models_with_printed_rescan_count_differences",nominalDifferences);
        session.put("original_unscored_seed_sentinels",unscored);
        session.put("original_learned_motif_sha256",hash(work.resolve("learned-motifs.motif")));session.put("discovery_average_length_integer_floor",prepared.averageSequenceLength);session.put("discovery_seed_eligible_targets",prepared.eligibleTargets);session.put("discovery_seed_eligible_background",prepared.eligibleBackground);session.put("discovery_coverage_adjusted_background",prepared.effectiveBackground);session.put("discovery_unique_oligos",prepared.oligos.size());session.put("estimated_upstream_bytes",prepared.estimatedUpstreamBytes);session.put("native_audit_operation_bound",prepared.nativeOperations);
        session.put("source_seed_logp_effective",prepared.plan.seedText.equals("auto")?-10+4-StrictMath.log10(prepared.eligibleTargets):Double.parseDouble(prepared.plan.seedText));
        warnings.add("DISCOVERY PRECISION REVIEW: original discovery scores its internal matrices before two getConsensus/expxform/justNormalize print round-trips. Source models are not reconstructed by a plain rescan of three-decimal output. The native audit uses a conservative +/-0.0081 cell envelope and +/-0.000000501 threshold envelope; it preserves original counts/log P and reports definite/possible sequence-presence bounds and a separate printed-model rescan. Models with unresolved presence="+ambiguous+", printed-rescan differences="+nominalDifferences+". Required action: inspect learned-models.tsv, discovery-sequence-counts.tsv and results.tsv. Compatible uncertain bounds are not a full-precision reconstruction; never replace original P/count metadata with a point rescan or infer q/E values.");
        warnings.add("DISCOVERY POPULATION REVIEW: seed discovery excludes sequences without a canonical motif-length window and weights unusually long sequences using the integer-floor average input length; background oligo coverage is adjusted before approximate-ZOOPS seed scoring. Final reported motif statistics use fresh complete unweighted sequence populations, retaining short/N-only records. Required action: review discovery-input-eligibility.tsv, native-oligo-counts.tsv and original-oligo-enrichment.tsv; no input record was silently removed from the final population.");
        warnings.add("SEED STATISTIC REVIEW: the original heuristic seed ledger retains "+unscored+" positive 1.000 sentinels for unscored/background-only or sufficiently low-target-count words; these are not log P values. Mismatch seed scores and weighted occurrences are also distinct from final unweighted motif presence statistics. Required action: use the final reported motif statistics for that documented test and retain seed diagnostics with their sentinel semantics; do not exponentiate unchecked seed sentinels or infer q/E values.");
        if(prepared.plan.normalization>0)warnings.add("NORMALIZATION SNAPSHOT REVIEW: original-normalization.tsv is the original emitted iteration snapshot, with leading empty fields and percentages despite its Counts column labels. The source's final normalization print is not written to that file. Required action: retain it with original.stderr and the post-normalization oligo ledger; do not interpret it as the final per-oligo weights. Native target oligo counts are checked; normalized background weights remain original-engine values.");
        if(motifs.isEmpty())warnings.add("NO DISCOVERED MOTIFS: original reached final scoring and cleanup with an empty motif output. Required action: review the supplied contrast, canonical windows, requested length/search settings and original.stderr; this does not establish absence of regulatory signal.");
        return motifs.size();
    }
    private static long auditOligos(Path work,HomerCoreData.Data data,Prepared prepared)throws Exception{
        var seen=new java.util.HashSet<String>();long unscored=0;Path path=work.resolve("original-oligo-enrichment.tsv");HomerFasta.regular(path,data.limits().workspace());
        try(var input=HomerCoreAudit.reader(path)){
            String expected="OligoSequence\tTarget Occurences\tBackground Occurences\tEnrichment(ln p, up to "+prepared.plan.mismatches+" mismatches)";
            if(!expected.equals(HomerCoreAudit.next(input,1024)))throw new IOException("Original discovery oligo ledger header differs from requested mismatch search");
            for(String line;(line=HomerCoreAudit.next(input,1024))!=null;){
                String[] f=line.split("\t",-1);
                if(f.length!=4||!f[0].matches("[ACGT]{"+prepared.plan.width+"}"))throw new IOException("Malformed original discovery oligo ledger");
                String key=canonical(f[0],data.strands());var raw=prepared.oligos.get(key);
                if(raw==null||!seen.add(key))throw new IOException("Original oligo ledger has an unknown or duplicate canonical word");
                double t=number(f[1],"source oligo target",0,Double.MAX_VALUE),b=number(f[2],"source oligo background",0,Double.MAX_VALUE),seed=number(f[3],"source mismatch seed log P",-Double.MAX_VALUE,Double.MAX_VALUE);
                if(seed>0){if(seed!=1||raw.target>.010000001)throw new IOException("Original seed ledger has an invalid positive log P/sentinel");unscored++;}
                if(Math.abs(t-raw.target)>.050001+1e-9*raw.target||prepared.plan.normalization==0&&Math.abs(b-raw.background)>.050001+1e-9*raw.background)throw new IOException("Original discovery raw oligo counts differ from independent weighted window counts");
            }
        }
        if(seen.size()!=prepared.oligos.size())throw new IOException("Original discovery oligo ledger omits canonical words");
        if(prepared.plan.normalization>0){
            Path norm=work.resolve("original-normalization.tsv");HomerFasta.regular(norm,data.limits().workspace());long rows=0,expectedRows=0;for(int i=1;i<=prepared.plan.normalization;i++)expectedRows+=1L<<(2*i);var words=new java.util.HashSet<String>();
            try(var input=HomerCoreAudit.reader(norm)){
                if(!"Oligo\tTargetCounts\tBackgroundCounts\tNormalizationFactor".equals(HomerCoreAudit.next(input,1024)))throw new IOException("Unexpected original normalization snapshot header");
                for(String line;(line=HomerCoreAudit.next(input,1024))!=null;){
                    String[] f=line.split("\t",-1);
                    if(f.length!=6||!f[0].isEmpty()||!f[1].isEmpty()||!f[2].matches("[ACGT]{1,"+prepared.plan.normalization+"}")||!words.add(f[2]))throw new IOException("Malformed/duplicate original normalization snapshot word");
                    for(int i=3;i<=4;i++){if(!f[i].endsWith("%"))throw new IOException("Original normalization snapshot expected percentages");number(f[i].substring(0,f[i].length()-1),"normalization percentage",0,100.005);}
                    number(f[5],"normalization factor",0,Double.MAX_VALUE);if(++rows>expectedRows)throw new IOException("Original normalization snapshot row count exceeds requested word family");
                }
            }
            if(rows!=expectedRows)throw new IOException("Incomplete original normalization snapshot word family");
        }
        return unscored;
    }
    static String canonical(String word,HomerMotifs.Strands strands){if(strands!=HomerMotifs.Strands.BOTH)return word;String other=reverse(word);return word.compareTo(other)<=0?word:other;}
    static String reverse(String dna){var result=new StringBuilder(dna.length());for(int i=dna.length()-1;i>=0;i--)result.append(switch(dna.charAt(i)){case 'A'->'T';case 'C'->'G';case 'G'->'C';case 'T'->'A';default->'N';});return result.toString();}
    private static String finite(double value){return Double.isFinite(value)?Double.toString(value):"NA";}
    private static int count(String text)throws IOException{double value=number(text,"discovery presence count",0,Integer.MAX_VALUE);if(value!=StrictMath.rint(value))throw new IOException("Final original discovery count is not unweighted integer presence");return (int)value;}
    private static int whole(Map<String,String> o,String key,int fallback,int min,int max)throws IOException{
        try{int n=o.containsKey(key)?Integer.parseInt(o.get(key)):fallback;if(n<min||n>max)throw new NumberFormatException();return n;}catch(NumberFormatException failure){throw new IOException("Require integer --"+key+" in ["+min+","+max+"]",failure);}
    }
    private static double number(String text,String key,double min,double max)throws IOException{
        try{double n=Double.parseDouble(text);if(!Double.isFinite(n)||n<min||n>max)throw new NumberFormatException();return n;}catch(NumberFormatException failure){throw new IOException("Invalid finite "+key,failure);}
    }
}
