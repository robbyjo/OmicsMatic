/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.enrichment.GseaStatistics;
import static org.jlinalg.cli.FollowupSupport.*;

/** Independent checks of original identities,counts,score arithmetic and tested families. */
final class EnrichmentResultAudit {
    private EnrichmentResultAudit() { }
    private record LegacyHit(String gene,int rank,double running,double helperScore) { }
    static void empty(Path dir,Map<String,String> dispositions,List<String> warnings)throws IOException {
        var family=new ArrayList<String[]>();dispositions.forEach((t,d)->family.add(new String[]{t,d}));
        table(dir.resolve("reported-family.tsv"),List.of("term","status"),family);
        table(dir.resolve("results.tsv"),List.of("term","description","set_size","overlap","universe_size","query_size","enrichment_score","nes","p_value","upstream_adjusted_p","adjusted_p_value","q_value","native_log_hypergeometric_p","passes_threshold","status"),List.of());
        warnings.add("NO ELIGIBLE HYPOTHESES: the explicit empty-retain policy records zero tested terms and every filter disposition. No original inferential call or nominal statistic was fabricated.");
    }
    static void validate(Map<String,String> o,Path dir,Map<String,List<String>> sets,Map<String,String> names,
            List<String> genes,double[] statistics,List<String> universe,List<String> query,Map<String,String> dispositions,
            int querySize,List<String> warnings,int bytes,int maxLinks)throws Exception {
        boolean ora=o.get("method").equals("ora"),fgsea=o.get("engine").equals("fgsea");
        var metadata=RegenieRuntime.object(dir.resolve("upstream/metadata.json"));
        if(!Objects.equals(metadata.get("schema"),"jlinalg-enrichment-upstream-v1")||!Objects.equals(metadata.get("engine"),o.get("engine"))||!Objects.equals(metadata.get("method"),o.get("method")))throw new IOException("Original metadata engine/method identity differs");
        if(!(metadata.get("versions") instanceof Map<?,?> versions)||!Objects.equals(versions.get("fgsea"),"1.38.0")||!Objects.equals(versions.get("enrichit"),"0.2.6")||!Objects.equals(versions.get("jsonlite"),"2.0.0"))throw new IOException("Original loaded package versions differ");
        if(o.get("engine").equals("dose")&&!Objects.equals(versions.get("DOSE"),"4.5.0")||o.get("engine").equals("clusterprofiler")&&!Objects.equals(versions.get("clusterProfiler"),"4.20.3"))throw new IOException("Selected original API version differs");
        if(metadata.get("warnings") instanceof List<?> messages)for(Object message:messages)warnings.add("ORIGINAL ENGINE: "+message);
        var raw=EnrichmentToolsCli.read(dir.resolve("upstream/results.tsv"),bytes,Integer.parseInt(o.getOrDefault("max-terms","100000")));
        if(!(metadata.get("reported_terms") instanceof Number count)||count.intValue()!=raw.rows().size())throw new IOException("Original result/metadata row counts differ");
        if(raw.rows().isEmpty()&&o.get("empty").equals("error"))throw new IOException("Original engine returned no tested terms; inspect IDs,universe/size filters and original logs,or explicitly retain an empty analysis");
        Set<String> eligible=new HashSet<>();dispositions.forEach((t,d)->{if(d.equals("eligible"))eligible.add(t);});
        double[] weights=null;
        if(!ora) {
            var order=EnrichmentToolsCli.read(dir.resolve("upstream/ranking.tsv"),bytes,genes.size());
            if(!order.header().equals(List.of("gene","statistic"))||order.rows().size()!=genes.size())throw new IOException("Original ranking schema/dimensions differ");
            for(int i=0;i<genes.size();i++)if(!order.rows().get(i)[0].equals(genes.get(i))||!relative(scalar(order.rows().get(i)[1]),statistics[i],5e-15))throw new IOException("Original ranked gene order/statistic changed at row "+(i+1));
            var prepared=EnrichmentToolsCli.read(dir.resolve("upstream/weights.tsv"),bytes,genes.size());
            if(!prepared.header().equals(List.of("gene","weight"))||prepared.rows().size()!=genes.size())throw new IOException("Original prepared weight schema/dimensions differ");
            boolean integerProtocol=fgsea||o.get("engine").equals("dose")&&!o.get("algorithm").equals("dose")||o.get("algorithm").equals("multilevel");
            double exponent=Double.parseDouble(o.get("exponent"));double[] reference=integerProtocol?GseaStatistics.integerWeights(statistics,exponent):EnrichmentToolsCli.rawWeights(statistics,exponent);
            weights=new double[genes.size()];int rounded=0;
            for(int i=0;i<genes.size();i++) {
                String[] row=prepared.rows().get(i);weights[i]=scalar(row[1]);
                if(!row[0].equals(genes.get(i))||!Double.isFinite(weights[i])||weights[i]<0||integerProtocol&&weights[i]!=Math.rint(weights[i]))throw new IOException("Original prepared gene/weight is invalid at row "+(i+1));
                if(integerProtocol){if(Math.abs(reference[i]-weights[i])>2)throw new IOException("Original integer weight differs beyond floating rounding at row "+(i+1));if(reference[i]!=weights[i])rounded++;}
                else if(!relative(reference[i],weights[i],5e-14))throw new IOException("Original power weight differs at row "+(i+1));
            }
            if(rounded>0)warnings.add("POWER-WEIGHT ROUNDING: "+rounded+" original integer weights differed by at most2 units from independent FP64 preparation. Actual original weights are retained and used for the score audit; input statistics are unchanged.");
        }
        var edges=EnrichmentToolsCli.read(dir.resolve("upstream/leading-edges.tsv"),bytes,maxLinks);
        if(!edges.header().equals(List.of("term","gene","rank")))throw new IOException("Original leading-edge schema differs");
        var edgeGenes=new HashMap<String,List<String>>();var index=new HashMap<String,Integer>();if(genes!=null)for(int i=0;i<genes.size();i++)index.put(genes.get(i),i);
        for(String[] row:edges.rows()) {
            if(ora||!eligible.contains(row[0])||!sets.get(row[0]).contains(row[1])||!index.containsKey(row[1])||Integer.parseInt(row[2])!=index.get(row[1])+1)throw new IOException("Original leading-edge identity/rank differs");
            var list=edgeGenes.computeIfAbsent(row[0],t->new ArrayList<>());if(list.contains(row[1]))throw new IOException("Duplicate original leading-edge gene");list.add(row[1]);
        }
        var legacyHits=new HashMap<String,List<LegacyHit>>();var legacyRows=new ArrayList<String[]>();
        GseaStatistics.PreparedWeights legacyRaw=null;
        if(!ora&&o.get("engine").equals("dose")) {
            var helper=EnrichmentToolsCli.read(dir.resolve("upstream/legacy-leading-hits.tsv"),bytes,maxLinks);
            if(!helper.header().equals(List.of("term","gene","rank","running_score","helper_es")))throw new IOException("Original legacy helper schema differs");
            for(String[] row:helper.rows()) {
                if(!eligible.contains(row[0])||!index.containsKey(row[1])||!sets.get(row[0]).contains(row[1])||Integer.parseInt(row[2])!=index.get(row[1])+1)throw new IOException("Original legacy helper identity/rank differs");
                legacyHits.computeIfAbsent(row[0],t->new ArrayList<>()).add(new LegacyHit(row[1],Integer.parseInt(row[2]),scalar(row[3]),scalar(row[4])));
            }
            legacyRaw=GseaStatistics.prepareWeights(EnrichmentToolsCli.rawWeights(statistics,Double.parseDouble(o.get("exponent"))));
        }
        var preparedWeights=ora?null:GseaStatistics.prepareWeights(weights);
        var ids=new HashSet<String>();var canonical=new ArrayList<String[]>();var pvalues=new ArrayList<Double>();boolean complete=true;
        for(String[] row:raw.rows()) {
            String id=row[raw.column(fgsea?"pathway":"ID")];
            if(!eligible.contains(id)||!ids.add(id))throw new IOException("Original term is unexpected or duplicated: "+id);
            if(!fgsea&&raw.header().contains("Description")&&!row[raw.column("Description")].equals(names.get(id)))throw new IOException("Original description changed for term "+id);
            int size=sets.get(id).size(),hits=-1;double logp=Double.NaN,es=Double.NaN,nes=Double.NaN;
            double p=scalar(row[raw.column(fgsea?"pval":"pvalue")]),upstreamAdjusted=scalar(row[raw.column(fgsea?"padj":"p.adjust")]);
            double q=raw.header().contains("qvalue")?scalar(row[raw.column("qvalue")]):Double.NaN;
            if(ora) {
                Set<String> selected=new HashSet<>(query);List<String> members=sets.get(id);hits=(int)members.stream().filter(selected::contains).count();
                if(Integer.parseInt(row[raw.column("Count")])!=hits||!row[raw.column("GeneRatio")].equals(hits+"/"+querySize)||!row[raw.column("BgRatio")].equals(size+"/"+universe.size()))throw new IOException("Original ORA overlap/selection/universe counts differ for "+id);
                logp=GseaStatistics.logHypergeometric(universe.size(),querySize,size,hits);
                if(Double.isFinite(p)&&p>0&&Math.abs(Math.log(p)-logp)>2e-10+2e-12*Math.abs(logp))throw new IOException("Original ORA tail differs from independent hypergeometric arithmetic: "+id);
                if(p==0&&logp>Math.log(Double.MIN_VALUE))throw new IOException("Original ORA reports zero for a representable hypergeometric tail: "+id);
                if(raw.header().contains("geneID")){Set<String> expected=new HashSet<>();for(String g:members)if(selected.contains(g))expected.add(g);String original=row[raw.column("geneID")];Set<String> actual=original.isEmpty()?Set.of():new HashSet<>(List.of(original.split("/",-1)));if(!actual.equals(expected))throw new IOException("Original ORA hit identities differ for "+id);}
            } else {
                if(Integer.parseInt(row[raw.column(fgsea?"size":"setSize")])!=size)throw new IOException("Original GSEA set size differs: "+id);
                es=scalar(row[raw.column(fgsea?"ES":"enrichmentScore")]);nes=scalar(row[raw.column("NES")]);int[] selected=sets.get(id).stream().mapToInt(index::get).sorted().toArray();
                GseaStatistics.ExtremumTie tie=fgsea||o.get("engine").equals("dose")&&!o.get("algorithm").equals("dose")?GseaStatistics.ExtremumTie.ZERO:o.get("algorithm").equals("multilevel")?GseaStatistics.ExtremumTie.POSITIVE:GseaStatistics.ExtremumTie.NEGATIVE;
                var walk=preparedWeights.walk(selected,EnrichmentToolsCli.tail(o),tie);EnrichmentToolsCli.checkZero(o,id,walk,warnings);
                if(Double.isFinite(es)) {
                    if(!near(es,walk.score(),2e-11)) {
                        boolean balanced=Math.abs(Math.abs(walk.maximum())-Math.abs(walk.minimum()))<=2e-12&&(near(es,walk.maximum(),2e-11)||near(es,walk.minimum(),2e-11)||es==0);
                        if(!balanced)throw new IOException("Original GSEA score differs from independent walk: "+id);
                        warnings.add("BALANCED GSEA EXTREMA: term "+id+" has positive/negative extrema equal within2e-12; original accumulation/tie rules select its reported direction. Original score,leading edge and inference remain unchanged; compare engines only under a declared convention.");
                    }
                    List<String> expected;boolean exactOrder=fgsea;
                    if(legacyRaw!=null) {
                        List<LegacyHit> helper=legacyHits.getOrDefault(id,List.of());
                        if(helper.size()!=selected.length)throw new IOException("Legacy helper omitted/duplicated term members: "+id);
                        var independent=legacyRaw.walk(selected,GseaStatistics.Tail.STANDARD,GseaStatistics.ExtremumTie.NEGATIVE);
                        double[] prefix=independent.tops();double helperScore=helper.get(0).helperScore();int maximum=0,minimum=0;
                        for(int j=0;j<helper.size();j++) {
                            LegacyHit hit=helper.get(j);
                            if(!hit.gene().equals(genes.get(selected[j]))||!near(hit.running(),prefix[j],2e-11)||!near(hit.helperScore(),helperScore,0))throw new IOException("Legacy helper hit-prefix arithmetic/order differs: "+id);
                            if(hit.running()>helper.get(maximum).running())maximum=j;if(hit.running()<helper.get(minimum).running())minimum=j;
                        }
                        if(!near(helperScore,independent.score(),2e-11)&&!(Math.abs(Math.abs(independent.maximum())-Math.abs(independent.minimum()))<=2e-12&&(near(helperScore,independent.maximum(),2e-11)||near(helperScore,independent.minimum(),2e-11))))throw new IOException("Legacy helper scalar score differs from independent raw walk: "+id);
                        var legacyExpected=new ArrayList<String>();boolean boundary=helperScore<0&&minimum==0;
                        if(helperScore>=0){for(int j=0;j<=maximum;j++)legacyExpected.add(helper.get(j).gene());}
                        else for(int j=Math.max(1,minimum);j<helper.size();j++)legacyExpected.add(helper.get(j).gene());
                        expected=List.copyOf(legacyExpected);exactOrder=true;
                        legacyRows.add(new String[]{id,numeric(es),numeric(helperScore),Integer.toString(expected.size()),Boolean.toString(boundary),helperScore>=0?"raw-helper-positive-hit-maximum":"raw-helper-negative-hit-minimum"});
                        if(boundary)warnings.add("LEGACY DOSE LEADING-EDGE BOUNDARY: term "+id+" follows the original negative hit-row rule,including exclusion of the first hit when its minimum is first. Original core genes are retained; this differs from the standard fgsea leading edge. Read legacy-leading-audit.tsv before selecting genes.");
                        if(es==0&&helperScore!=0||Math.signum(es)!=Math.signum(helperScore))warnings.add("LEGACY DOSE SCORE/CORE DIFFERENCE: term "+id+" reports ES="+es+" but uses raw-helper ES="+helperScore+" for its core genes. Core membership alone is not evidence of regulation or significance. Original reported inference is unchanged.");
                    } else {
                        boolean directionalZero=es==0&&!o.get("tail").equals("std");
                        var direction=directionalZero?EnrichmentToolsCli.tail(o):es>0?GseaStatistics.Tail.POSITIVE:GseaStatistics.Tail.NEGATIVE;
                        var directional=preparedWeights.walk(selected,direction,tie);
                        expected=es==0&&!directionalZero?List.of():Arrays.stream(directional.leadingEdge()).mapToObj(genes::get).toList();
                        if(directionalZero&&!expected.isEmpty())warnings.add("ZERO ONE-TAILED SCORE/CORE: term "+id+" retains the original "+o.get("tail")+" tail's leading-edge genes despite ES0. This is an engine-defined boundary,not positive evidence of enrichment or significance.");
                    }
                    List<String> actual=edgeGenes.getOrDefault(id,List.of());
                    if(!new HashSet<>(actual).equals(new HashSet<>(expected))||exactOrder&&!actual.equals(expected))throw new IOException("Original leading-edge members/order differ from its declared engine/helper definition: "+id);
                }
            }
            boolean defined=probability(p)&&p>0&&probability(upstreamAdjusted)&&(ora||Double.isFinite(es)&&Double.isFinite(nes));
            if(!defined)complete=false;
            if(!Double.isNaN(q)&&!probability(q))throw new IOException("Original q-value is outside [0,1]: "+id);
            pvalues.add(p);canonical.add(new String[]{id,names.get(id),Integer.toString(size),ora?Integer.toString(hits):"NA",Integer.toString(universe.size()),ora?Integer.toString(querySize):"NA",numeric(es),numeric(nes),numeric(p),numeric(upstreamAdjusted),"NA",numeric(q),numeric(logp),"NA",defined?"tested":"unreportable-original-statistic"});
        }
        var missing=new HashSet<>(eligible);missing.removeAll(ids);if(!missing.isEmpty()){complete=false;warnings.add("UNREPORTED ELIGIBLE TERMS: original engine omitted "+missing.size()+" size-eligible terms. Read reported-family.tsv; no omitted P-value,NES or gene observation is fabricated.");}
        if(legacyRaw!=null)table(dir.resolve("legacy-leading-audit.tsv"),List.of("term","reported_es","raw_helper_es","leading_members","original_first_hit_excluded","definition"),legacyRows);
        var family=new ArrayList<String[]>();for(String id:dispositions.keySet())family.add(new String[]{id,ids.contains(id)?"reported-by-original":missing.contains(id)?"unreported-eligible":dispositions.get(id)});
        table(dir.resolve("reported-family.tsv"),List.of("term","status"),family);
        if(!complete&&o.get("undefined").equals("error"))throw new IOException("Original tested family has undefined,zero-resolution or unreported statistics; inspect raw outputs and reported-family.tsv,revise the engine/score/sampling model or explicitly retain unreportable results");
        double[] p=pvalues.stream().mapToDouble(Double::doubleValue).toArray();double[] adjusted=complete?GseaStatistics.adjust(p,GseaStatistics.Adjustment.valueOf(o.get("p-adjust").toUpperCase(Locale.ROOT))):new double[p.length];
        if(!complete){Arrays.fill(adjusted,Double.NaN);warnings.add("INCOMPLETE TESTED FAMILY: native adjusted P-values and significance flags are suppressed for every row. Original reported values remain unchanged; retain these results as unreportable until the named inference/family issue is resolved.");}
        for(int i=0;i<canonical.size();i++) {
            String[] row=canonical.get(i);row[10]=numeric(adjusted[i]);
            if(complete&&(o.get("engine").equals("fgsea")?o.get("p-adjust").equals("BH"):true)&&!relative(adjusted[i],scalar(row[9]),2e-10))throw new IOException("Original multiple-testing adjustment differs from the complete reported family: "+row[0]);
            double q=scalar(row[11]);boolean qAvailable=!o.containsKey("q-threshold")||Double.isFinite(q);
            if(o.containsKey("q-threshold")&&!qAvailable&&o.get("undefined").equals("error"))throw new IOException("Requested Storey q-value is undefined: "+row[0]+"; it cannot be replaced by a BH P-value");
            if(complete&&qAvailable)row[13]=Boolean.toString(p[i]<=Double.parseDouble(o.get("p-threshold"))&&adjusted[i]<=Double.parseDouble(o.get("p-threshold"))&&(!o.containsKey("q-threshold")||q<=Double.parseDouble(o.get("q-threshold"))));
            if(p[i]==0)warnings.add("ZERO ORIGINAL P-VALUE: term "+row[0]+" is below numeric or Monte Carlo resolution,not proof of a zero probability. The original value and any finite independent ORA log-tail are retained without an invented replacement.");
            if(!Double.isFinite(q)&&!o.get("engine").equals("fgsea"))warnings.add("STOREY Q-VALUE UNDEFINED: term "+row[0]+" retains NA; multiple-testing adjusted P-values are a different quantity.");
            if(o.get("algorithm")!=null&&o.get("algorithm").equals("multilevel")&&Double.parseDouble(o.get("eps"))>0&&p[i]==Double.parseDouble(o.get("eps")))row[14]="at-original-pvalue-floor";
        }
        table(dir.resolve("results.tsv"),List.of("term","description","set_size","overlap","universe_size","query_size","enrichment_score","nes","p_value","upstream_adjusted_p","adjusted_p_value","q_value","native_log_hypergeometric_p","passes_threshold","status"),canonical);
        if(raw.rows().isEmpty())warnings.add("EMPTY ORIGINAL ANALYSIS: no term was tested; the explicit retain policy publishes an empty table and audit statuses without nominal statistics.");
        if(!ora)warnings.add("ENGINE/NULL ACTION: original serial "+o.get("engine")+" "+o.get("algorithm")+" inference is conditional on the supplied ranked statistics,gene-set null,tail/exponent and seed. These are not phenotype-label permutations. Current clusterProfiler/enrichit and fgsea/legacy DOSE scores,NES,P-values and balanced-tail rules are not interchangeable.");
        else warnings.add("ORA FAMILY ACTION: gene-selection and universe/annotation choices are explicit. Original zero-overlap terms do not enter its adjustment family; every exclusion is retained. GeneRatio/BgRatio are audited; Storey q-values and adjusted P-values remain distinct.");
    }
    private static double scalar(String value){return switch(value){case "NA","NaN",""->Double.NaN;case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->Double.parseDouble(value);};}
    private static String numeric(double value){return Double.isNaN(value)?"NA":Double.toString(value);}
    private static boolean probability(double value){return Double.isFinite(value)&&value>=0&&value<=1;}
    private static boolean near(double a,double b,double tolerance){return Double.isFinite(a)&&Double.isFinite(b)&&Math.abs(a-b)<=tolerance*Math.max(1,Math.max(Math.abs(a),Math.abs(b)));}
    private static boolean relative(double a,double b,double tolerance){return Double.isFinite(a)&&Double.isFinite(b)&&(a==b||Math.abs(a-b)<=tolerance*Math.max(Math.abs(a),Math.abs(b)));}
}
