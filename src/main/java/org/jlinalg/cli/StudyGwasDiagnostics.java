/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import jdistlib.Normal;
import org.jlinalg.plot.PlotOptions;
import org.jlinalg.plot.LegendPosition;
import org.jlinalg.plot.Plots;
import org.jlinalg.study.StudyGwasDiagnosticPolicy;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.study.StudyOperationExecutor.*;

/** Source-specific observed QQ/Manhattan diagnostics; never genomic-control correction. */
final class StudyGwasDiagnostics implements StudyOperationExecutor {
    static final String CAVEAT="QQ and the median one-df chi-square transform of P values are descriptive diagnostics, not a calibration certificate. LD, polygenicity, sample size, discrete tests and ascertainment affect their interpretation; inflation alone does not establish population stratification. Source completeness, ancestry, assay and model calibration are not independently certified. No genomic-control correction, P-value replacement, exclusion or primary-result selection is performed. Selected-only, unknown or unresolved exports cannot establish a genome-wide null reference or lambda. Manhattan plots use available declared coordinates only; missing coordinates and builds remain limitations. Display thinning/capping does not alter retained numerical tables or tests.";
    static final List<String> SUMMARY_COLUMNS=List.of("source_id","contrast","stratum","testing_coverage","observed_tests","diagnostic_tests","unresolved_tests","lambda_p_chisq1","lambda_status","coordinate_tests","coordinate_status");
    private record Source(String id,String dataset,Path path,String hash,Map<String,Object> manifest,Path directory,boolean preQc) { }
    private record Point(String feature,double logP,String chromosome,long position,String build,Map<String,String> labels) { }
    private record Group(String contrast,String stratum) { }
    private record Coordinates(String chromosome,long position,String build,int contributors,String status) { }
    private static final class CoordinateAccumulator {
        final Set<String> datasets=new LinkedHashSet<>();String chromosome="",build="";long position;boolean missing,missingBuild,conflict;
        void add(String dataset,String chr,long pos,String genomeBuild){
            if(dataset.isBlank()||!datasets.add(dataset))conflict=true;
            if(chr.isBlank()||pos==0)missing=true;
            else if(chromosome.isBlank()){chromosome=chr;position=pos;}else if(!chromosome.equals(chr)||position!=pos)conflict=true;
            if(missingBuild(genomeBuild))missingBuild=true;
            else{String normalized=StudyPoolingAudit.canonical("genome_build",genomeBuild);if(build.isBlank())build=normalized;else if(!build.equals(normalized))conflict=true;}
        }
        Coordinates result(){return new Coordinates(missing||conflict?"":chromosome,missing||conflict?0:position,missingBuild?"":build,datasets.size(),conflict?"conflicting_contributors":missing?"unavailable_contributing_coordinates":"contributing_coordinate_consensus");}
    }
    private final List<String> outputs=new ArrayList<>();
    @Override public Result execute(Context c)throws Exception {
        outputs.clear();Files.createDirectories(c.outputDirectory());var policy=StudyGwasDiagnosticPolicy.resolve(map(StudyResultTables.analysis(c).get("gwas_diagnostics")));
        var summary=new LinkedHashMap<String,Object>();summary.put("effective_policy",policy);summary.put("required_manuscript_caveat",CAVEAT);
        var sources=sources(c);var receipts=new ArrayList<Object>();var statistics=new ArrayList<String[]>();var needs=new ArrayList<String>();int index=0;
        for(var source:sources){String prefix=String.format(java.util.Locale.ROOT,"source-%03d",++index);diagnose(c,policy,source,prefix,statistics,receipts,needs);}
        boolean performed=receipts.stream().map(StudyPublicEvidence::map).anyMatch(r->r.get("diagnostic_tests") instanceof Number n&&n.longValue()>0);
        boolean rowQc=receipts.stream().map(StudyPublicEvidence::map).anyMatch(r->"observed_export_diagnosed".equals(r.get("status"))&&r.get("source_rows") instanceof Number n&&n.longValue()>0);
        summary.put("sources",receipts);summary.put("source_count",sources.size());summary.put("summary_rows",statistics.size());summary.put("analysis_performed",performed);summary.put("row_qc_performed",rowQc);
        if(sources.isEmpty())needs.add("Supply a sealed genomic association result or a supplied/native primary meta-analysis; absent results are not null GWAS diagnostics");
        summary.put("method_report",Map.of("operation","gwas_diagnostics","performed",performed,"row_qc_performed",rowQc,"parameters",policy,"source_count",sources.size(),"interpretation",CAVEAT));
        var typed=new ArrayList<Object>();for(String need:needs)typed.add(StudyNeed.distinctReview("gwas-diagnostics:"+c.operation().get("hypothesis"),"What is needed for GWAS diagnostics?",need,true).document());summary.put("needs",typed);
        FollowupSupport.table(c.outputDirectory().resolve("gwas-diagnostic-summary.tsv"),SUMMARY_COLUMNS,statistics);outputs.add("gwas-diagnostic-summary.tsv");
        write(c.outputDirectory().resolve("gwas-diagnostics.yaml"),summary);outputs.add("gwas-diagnostics.yaml");
        return new Result(needs.isEmpty()?State.COMPLETE:State.WAITING_FOR_INPUT,needs.isEmpty()?"Retained source-specific GWAS diagnostics without changing inference":"Retained available GWAS diagnostics; source/bounds need review",List.copyOf(outputs),summary);
    }
    private static List<Source> sources(Context c)throws IOException {
        var datasets=new LinkedHashMap<String,Map<String,Object>>();for(Object raw:list(c.study().get("datasets"))){var d=map(raw);if(!"retired".equals(d.get("status")))datasets.put(text(d.get("id")),d);}
        Set<String> selected=new LinkedHashSet<>(list(c.operation().get("datasets")).stream().map(StudyPublicEvidence::text).toList());if(selected.isEmpty())selected.addAll(datasets.keySet());
        var producers=StudyPrimaryResults.producers(c);var owned=new LinkedHashSet<String>();
        for(var producer:producers)if(producer.type().equals("association"))for(String name:map(producer.manifest().get("output_sha256")).keySet())if(name.matches("standard-results/[^/]+\\.tsv"))owned.add(Path.of(name).getFileName().toString().replaceFirst("\\.tsv$",""));
        var result=new TreeMap<String,Source>();
        for(var producer:producers){var hashes=map(producer.manifest().get("output_sha256"));
            if(Set.of("meta_analysis","discovery_meta_analysis").contains(producer.type())){
                String name=producer.type().equals("meta_analysis")?"meta-analysis.tsv":"discovery-results.tsv";
                if(hashes.containsKey(name))add(result,new Source(producer.type()+":"+producer.id(),"",producer.directory().resolve(name),text(hashes.get(name)),producer.manifest(),producer.directory(),false));
                continue;
            }
            var ids=new LinkedHashSet<String>();for(String name:hashes.keySet())if(name.matches("(?:standard-results|post-effect-qc-results)/[^/]+\\.tsv"))ids.add(Path.of(name).getFileName().toString().replaceFirst("\\.tsv$",""));
            for(String id:ids){var dataset=datasets.get(id);if(!selected.contains(id)||dataset==null||!Set.of("gwas","genomics").contains(dataset.get("type"))||producer.type().equals("validate_harmonize")&&owned.contains(id))continue;
                String post="post-effect-qc-results/"+id+".tsv",name=hashes.containsKey(post)?post:"standard-results/"+id+".tsv";
                add(result,new Source("dataset:"+id,id,producer.directory().resolve(name),text(hashes.get(name)),producer.manifest(),producer.directory(),false));
                String before="pre-effect-qc-results/"+id+".tsv";if(hashes.containsKey(before)&&!hashes.get(before).equals(hashes.get(name)))add(result,new Source("dataset:"+id+":pre_effect_qc",id,producer.directory().resolve(before),text(hashes.get(before)),producer.manifest(),producer.directory(),true));
            }
        }
        return List.copyOf(result.values());
    }
    private static void add(Map<String,Source> sources,Source source)throws IOException{if(sources.putIfAbsent(source.id(),source)!=null)throw new IOException("Ambiguous GWAS diagnostic source; scope the study instead of combining duplicate producers: "+source.id());}
    private void diagnose(Context c,Map<String,Object> policy,Source source,String prefix,List<String[]> statistics,List<Object> receipts,List<String> needs)throws Exception {
        StudyFindingInventory.verified(source.directory(),source.directory().relativize(source.path()).toString(),source.hash());
        Path snapshot=c.outputDirectory().resolve(prefix+"-statistics.tsv");Files.copy(source.path(),snapshot);if(!source.hash().equals(FollowupSupport.hash(snapshot))||!source.hash().equals(FollowupSupport.hash(source.path())))throw new IOException("GWAS diagnostic source changed during snapshot");outputs.add(snapshot.getFileName().toString());
        var receipt=new LinkedHashMap<String,Object>();receipt.put("source_id",source.id());receipt.put("producer_id",source.manifest().get("operation_id"));receipt.put("producer_type",source.manifest().get("operation_type"));receipt.put("source_sha256",source.hash());receipt.put("snapshot",snapshot.getFileName().toString());receipt.put("pre_effect_qc",source.preQc());
        Path manifest=source.directory().resolve("manifest.yaml"),manifestSnapshot=c.outputDirectory().resolve(prefix+"-producer.yaml");String manifestHash=FollowupSupport.hash(manifest);Files.copy(manifest,manifestSnapshot);if(!manifestHash.equals(FollowupSupport.hash(manifestSnapshot))||!manifestHash.equals(FollowupSupport.hash(manifest))||!StudyYaml.loadArtifact(manifestSnapshot).equals(source.manifest()))throw new IOException("GWAS diagnostic producer changed during snapshot");outputs.add(manifestSnapshot.getFileName().toString());receipt.put("producer_manifest_sha256",manifestHash);receipt.put("producer_manifest_snapshot",manifestSnapshot.getFileName().toString());
        String declaredBuild="";
        if(!source.dataset().isBlank())for(Object raw:list(c.study().get("datasets"))){var d=map(raw);if(source.dataset().equals(d.get("id")))declaredBuild=text(d.get("genome_build"));}
        Path metadata=source.directory().resolve("supplied-analysis.yaml");if(Files.isRegularFile(metadata)){String hash=text(map(source.manifest().get("output_sha256")).get("supplied-analysis.yaml"));StudyFindingInventory.verified(source.directory(),"supplied-analysis.yaml",hash);Path copy=c.outputDirectory().resolve(prefix+"-supplied-analysis.yaml");Files.copy(metadata,copy);if(!hash.equals(FollowupSupport.hash(copy)))throw new IOException("GWAS diagnostic supplied metadata changed during snapshot");outputs.add(copy.getFileName().toString());receipt.put("supplied_metadata_sha256",hash);receipt.put("supplied_metadata_snapshot",copy.getFileName().toString());declaredBuild=text(map(StudyYaml.loadArtifact(copy).get("scope")).get("genome_build"));}
        if(missingBuild(declaredBuild))declaredBuild="";receipt.put("registered_source_genome_build",declaredBuild.isBlank()?"unknown":declaredBuild);
        int lines=0;try(var reader=Files.newBufferedReader(snapshot)){reader.readLine();for(String line;(line=reader.readLine())!=null;)if(!line.isBlank()&&++lines>integer(policy,"maximum_rows"))break;}
        if(lines>integer(policy,"maximum_rows")){receipt.put("source_rows_at_least",lines);receipt.put("status","row_bound_exceeded");receipts.add(receipt);needs.add("GWAS diagnostic source exceeds maximum_rows; no top-hit subset was substituted");return;}
        var table=DelimitedData.readResult(snapshot);table.column("feature_id");table.column("p_value");receipt.put("source_rows",table.rows().size());
        var projected=pooledCoordinates(c,source,prefix,table,receipt);
        var groups=new LinkedHashMap<Group,List<Point>>();var contrasts=new LinkedHashMap<String,Integer>();var unresolved=new LinkedHashMap<String,Integer>();var audit=new ArrayList<String[]>();var identities=new LinkedHashSet<String>();int observed=0;
        for(String[] row:table.rows()){
            String feature=at(table,row,"feature_id"),contrast=at(table,row,"term");if(contrast.isBlank())contrast="pooled";
            if(!identities.add(contrast+"\u001f"+feature))throw new IOException("Duplicate GWAS diagnostic feature/contrast; independent tests cannot be deduplicated by P value");
            String status=at(table,row,"status");double log=logProbability(at(table,row,"p_value"),at(table,row,"log_p_value"));String disposition;
            boolean eligible=status.isBlank()||Set.of("ok","tested","complete","single_cohort").contains(status.toLowerCase(java.util.Locale.ROOT));
            if(!eligible)disposition="source_nonestimable_or_excluded";
            else if(!Double.isFinite(log)){disposition="unresolved_probability_or_log_conflict";unresolved.merge(contrast,1,Integer::sum);}
            else disposition="diagnostic_test";
            if(eligible)contrasts.merge(contrast,1,Integer::sum);
            audit.add(new String[]{feature,contrast,disposition,Double.isFinite(log)?Double.toString(log):"NA"});
            if(!disposition.equals("diagnostic_test"))continue;observed++;
            var labels=new LinkedHashMap<String,String>();for(String field:List.of("minor_allele_frequency","minor_allele_count","imputation_info","test_method","calibration","statistic_type"))labels.put(field,at(table,row,field));
            String rowBuild=at(table,row,"genome_build");boolean missingBuild=missingBuild(rowBuild);labels.put("genome_build_origin",missingBuild?declaredBuild.isBlank()?"unknown":"registered_source":"source_row");
            String chr=chromosome(at(table,row,"chromosome"));long pos=position(at(table,row,"position"));String build=missingBuild?declaredBuild:rowBuild;
            var coordinate=projected.get(feature);if(coordinate!=null){
                double contributors=parse(at(table,row,table.header().contains("n_sources")?"n_sources":"n_cohorts"));boolean conflict=coordinate.status().equals("conflicting_contributors")||!Double.isFinite(contributors)||contributors!=coordinate.contributors();
                if(!chr.isBlank()&&!coordinate.chromosome().isBlank()&&!chr.equals(coordinate.chromosome())||pos>0&&coordinate.position()>0&&pos!=coordinate.position()||!build.isBlank()&&!coordinate.build().isBlank()&&!StudyPoolingAudit.canonical("genome_build",build).equals(coordinate.build()))conflict=true;
                labels.put("coordinate_projection_status",conflict?"conflicting_contributors":coordinate.status());
                if(!conflict&&coordinate.status().equals("contributing_coordinate_consensus")){if(chr.isBlank())chr=coordinate.chromosome();if(pos==0)pos=coordinate.position();if(build.isBlank()){build=coordinate.build();labels.put("genome_build_origin","native_contributing_coordinate_consensus");}}
            }
            var point=new Point(feature,log,chr,pos,build,labels);
            groups.computeIfAbsent(new Group(contrast,"all"),k->new ArrayList<>()).add(point);
            for(String field:List.of("maf","mac","info")){String column=field.equals("maf")?"minor_allele_frequency":field.equals("mac")?"minor_allele_count":"imputation_info";groups.computeIfAbsent(new Group(contrast,field+":"+bin(point.labels().get(column),list(policy.get(field+"_breaks")))),k->new ArrayList<>()).add(point);}
            String test=label(at(table,row,"test_method")),calibration=label(at(table,row,"calibration"));
            groups.computeIfAbsent(new Group(contrast,"test:"+test+";calibration:"+calibration),k->new ArrayList<>()).add(point);
        }
        FollowupSupport.table(c.outputDirectory().resolve(prefix+"-row-audit.tsv"),List.of("feature_id","contrast","disposition","log_p_value"),audit);outputs.add(prefix+"-row-audit.tsv");
        String coverage=coverage(c,policy,source,observed,receipts);receipt.put("testing_coverage",coverage);receipt.put("diagnostic_tests",observed);receipt.put("unresolved_tests",unresolved.values().stream().mapToInt(Integer::intValue).sum());
        if(groups.size()>integer(policy,"maximum_strata")){receipt.put("status","stratum_bound_exceeded");receipts.add(receipt);needs.add("GWAS diagnostic strata exceed maximum_strata; no contrast or stratum was silently omitted");return;}
        int groupNumber=0;
        for(var entry:groups.entrySet()){
            Group group=entry.getKey();var points=entry.getValue();String stem=prefix+String.format(java.util.Locale.ROOT,"-group-%03d",++groupNumber);
            var sorted=points.stream().sorted(Comparator.comparingDouble(Point::logP).thenComparing(Point::feature)).toList();
            boolean complete=coverage.equals("complete")&&unresolved.getOrDefault(group.contrast(),0)==0;
            String lambdaStatus=!complete?"unavailable_incomplete_or_unknown_export":points.size()<integer(policy,"minimum_lambda_tests")?"unavailable_too_few_tests":"descriptive_P_transform";
            double lambda=lambdaStatus.equals("descriptive_P_transform")?lambda(points.stream().mapToDouble(Point::logP).toArray()):Double.NaN;
            if(!Double.isFinite(lambda)&&lambdaStatus.equals("descriptive_P_transform"))lambdaStatus="unavailable_numeric_transform";
            var qq=new ArrayList<String[]>();int n=sorted.size();
            for(int i=0;i<n;i++){var point=sorted.get(i);double expected=-Math.log10((i+1.0)/(n+1.0)),observedLog=-point.logP()/Math.log(10);qq.add(new String[]{point.feature(),Integer.toString(i+1),Double.toString(expected),Double.toString(observedLog),Boolean.toString(observedLog>number(policy,"plot_y_limit"))});}
            FollowupSupport.table(c.outputDirectory().resolve(stem+"-qq.tsv"),List.of("feature_id","rank","expected_neg_log10_uniform_order_mean","observed_neg_log10_p","display_capped"),qq);outputs.add(stem+"-qq.tsv");
            plotQq(c,policy,stem,sorted,complete);
            String coordinateStatus="not_requested_for_stratum";int coordinates=0;
            if(group.stratum().equals("all")){var coordinate=manhattan(c,policy,stem,sorted,declaredBuild);coordinateStatus=text(coordinate.get("status"));coordinates=((Number)coordinate.get("coordinate_tests")).intValue();receipt.put("coordinate_"+groupNumber,coordinate);}
            statistics.add(new String[]{source.id(),group.contrast(),group.stratum(),coverage,Integer.toString(contrasts.getOrDefault(group.contrast(),0)),Integer.toString(n),Integer.toString(unresolved.getOrDefault(group.contrast(),0)),Double.isFinite(lambda)?Double.toString(lambda):"NA",lambdaStatus,Integer.toString(coordinates),coordinateStatus});
        }
        receipt.put("status","observed_export_diagnosed");receipt.put("strata",groups.size());receipt.put("qq_plots_performed",!groups.isEmpty());receipt.put("qq_display_selection","Retain the strongest up to 1000 points or half the point budget, then evenly spaced full-universe ranks including the last rank");receipt.put("display_y_limit",policy.get("plot_y_limit"));receipt.put("maximum_plot_points",policy.get("maximum_plot_points"));receipts.add(receipt);
    }
    static double logProbability(String rawP,String rawLog){
        double p=parse(rawP),log=parse(rawLog);if(!StudyResultShape.missing(rawP)&&!Double.isFinite(p)||!StudyResultShape.missing(rawLog)&&!Double.isFinite(log)||Double.isFinite(p)&&(p<0||p>1)||Double.isFinite(log)&&log>0)return Double.NaN;
        if(Double.isFinite(log)){if(Double.isFinite(p)&&p>0&&Math.abs(Math.log(p)-log)>Math.max(1e-8,Math.abs(log)*1e-7))return Double.NaN;return log;}
        return p>0&&p<=1?Math.log(p):Double.NaN;
    }
    static double lambda(double[] logP){if(logP.length==0)return Double.NaN;var x=new double[logP.length];for(int i=0;i<x.length;i++){double z=Normal.quantile(logP[i]-Math.log(2),0,1,false,true);x[i]=z*z;}java.util.Arrays.sort(x);double median=x.length%2==1?x[x.length/2]:x[x.length/2-1]/2+x[x.length/2]/2;double z=Normal.quantile(.75,0,1,true,false);return median/(z*z);}
    private static String coverage(Context c,Map<String,Object> policy,Source source,int tested,List<Object> receipts)throws IOException {
        if(!"complete".equals(source.manifest().get("state"))||Boolean.FALSE.equals(map(source.manifest().get("details")).get("primary_selection_eligible")))return "incomplete_producer";
        Path metadata=source.directory().resolve("supplied-analysis.yaml");
        if(Files.isRegularFile(metadata)){var hash=map(source.manifest().get("output_sha256")).get("supplied-analysis.yaml");StudyFindingInventory.verified(source.directory(),"supplied-analysis.yaml",text(hash));var m=StudyYaml.loadArtifact(metadata);var family=map(m.getOrDefault("resolved_family",m.get("family")));if(!"complete".equals(family.get("coverage")))return "selected_only_or_unknown";if(family.get("tested_count") instanceof Number n&&n.longValue()>tested)return "incomplete_export";return "complete";}
        if(source.manifest().get("operation_type").equals("association"))return "complete";
        if(!source.dataset().isBlank())return text(map(policy.get("dataset_coverage")).getOrDefault(source.dataset(),"unknown"));
        var expected=new LinkedHashSet<>(list(c.operation().get("datasets")).stream().map(StudyPublicEvidence::text).toList());
        if(source.manifest().get("operation_type").equals("discovery_meta_analysis"))expected=new LinkedHashSet<>(list(map(StudyBioinformatics.hypothesis(c).get("inputs")).get("discovery")).stream().map(StudyPublicEvidence::text).toList());
        if(expected.isEmpty())for(Object raw:list(c.study().get("datasets"))){var d=map(raw);if(!"retired".equals(d.get("status")))expected.add(text(d.get("id")));}
        var complete=new LinkedHashSet<String>();for(Object raw:receipts){var r=map(raw);String id=text(r.get("source_id"));if(id.startsWith("dataset:")&&!Boolean.TRUE.equals(r.get("pre_effect_qc"))&&"complete".equals(r.get("testing_coverage")))complete.add(id.substring("dataset:".length()));}
        return !expected.isEmpty()&&complete.containsAll(expected)?"complete":"unknown";
    }
    private void plotQq(Context c,Map<String,Object> p,String stem,List<Point> points,boolean complete)throws IOException {
        int n=points.size(),count=Math.min(n,integer(p,"maximum_plot_points"));double[] x=new double[count],y=new double[count];
        int tail=n<=count?count:Math.min(1000,count/2);
        for(int i=0;i<count;i++){int at=n<=count?i:i<tail?i:count-tail==1?n-1:tail+(int)((long)(i-tail)*(n-tail-1)/(count-tail-1));x[i]=-Math.log10((at+1.0)/(n+1.0));y[i]=Math.min(number(p,"plot_y_limit"),-points.get(at).logP()/Math.log(10));}
        var builder=Plots.xy().options(PlotOptions.builder().title(complete?"Observed QQ: declared complete export":"Ranked P export: partial/unknown coverage").xLabel(complete?"Uniform order mean -log10(P)":"Exported-rank plotting position; not a null reference").yLabel("-log10(P), capped at "+number(p,"plot_y_limit")+" for display").legend(complete?LegendPosition.INSIDE_NORTH_WEST:LegendPosition.NONE).size(850,650).build()).points("observed export",x,y);
        if(complete)builder.line("Uniform reference; not an LD-adjusted band",new double[]{0,x[0]},new double[]{0,x[0]},Color.GRAY);
        var plot=builder.build();plot.save(c.outputDirectory().resolve(stem+"-qq.png"));plot.save(c.outputDirectory().resolve(stem+"-qq.pdf"));outputs.add(stem+"-qq.png");outputs.add(stem+"-qq.pdf");
    }
    private Map<String,Coordinates> pooledCoordinates(Context c,Source source,String prefix,DelimitedData pool,Map<String,Object> receipt)throws IOException {
        String filename="cohort-result-metadata.tsv";var hashes=map(source.manifest().get("output_sha256"));
        if(Files.isRegularFile(source.directory().resolve("supplied-analysis.yaml"))||!Set.of("meta_analysis","discovery_meta_analysis").contains(source.manifest().get("operation_type"))||!hashes.containsKey(filename))return Map.of();
        String hash=text(hashes.get(filename));StudyFindingInventory.verified(source.directory(),filename,hash);Path snapshot=c.outputDirectory().resolve(prefix+"-contributing-metadata.tsv");Files.copy(source.directory().resolve(filename),snapshot);if(!hash.equals(FollowupSupport.hash(snapshot)))throw new IOException("Native pooled coordinate metadata changed during snapshot");outputs.add(snapshot.getFileName().toString());receipt.put("contributing_coordinate_metadata_sha256",hash);receipt.put("contributing_coordinate_metadata_snapshot",snapshot.getFileName().toString());
        Set<String> targets=new LinkedHashSet<>();for(String[] row:pool.rows())targets.add(row[pool.column("feature_id")]);var accumulated=new LinkedHashMap<String,CoordinateAccumulator>();var datasetLabels=new LinkedHashMap<String,String>();
        try(var reader=Files.newBufferedReader(snapshot)){
            String first=reader.readLine();if(first==null)throw new IOException("Empty native pooled coordinate metadata");var header=DelimitedData.parse(first,'\t',1,snapshot);int dataset=header.indexOf("dataset_id"),feature=header.indexOf("feature_id"),chr=header.indexOf("chromosome"),pos=header.indexOf("position"),build=header.indexOf("genome_build");if(dataset<0||feature<0)throw new IOException("Native pooled metadata needs dataset and feature identities");
            long lineNumber=1;for(String line;(line=reader.readLine())!=null;){lineNumber++;if(line.isBlank())continue;var fields=DelimitedData.parse(line,'\t',lineNumber,snapshot);if(fields.size()!=header.size())throw new IOException("Malformed native pooled coordinate metadata");String id=fields.get(feature);if(!targets.contains(id))continue;String cohort=datasetLabels.computeIfAbsent(fields.get(dataset),v->v);accumulated.computeIfAbsent(id,k->new CoordinateAccumulator()).add(cohort,chr<0?"":chromosome(fields.get(chr)),pos<0?0:position(fields.get(pos)),build<0?"":fields.get(build));}
        }
        var result=new LinkedHashMap<String,Coordinates>();var rows=new ArrayList<String[]>();
        for(var e:accumulated.entrySet()){var value=e.getValue().result();result.put(e.getKey(),value);rows.add(new String[]{e.getKey(),Integer.toString(value.contributors()),value.chromosome().isBlank()?"NA":value.chromosome(),value.position()==0?"NA":Long.toString(value.position()),value.build().isBlank()?"NA":value.build(),value.status()});}
        FollowupSupport.table(c.outputDirectory().resolve(prefix+"-coordinate-projection.tsv"),List.of("feature_id","contributing_sources","chromosome","position","genome_build","disposition"),rows);outputs.add(prefix+"-coordinate-projection.tsv");receipt.put("coordinate_projection","native fitted-contributor metadata consensus only; source counts are not deduplicated cohorts/participants; cohort P/MAC/MAF/INFO not projected");receipt.put("coordinate_projection_features",result.size());receipt.put("coordinate_projection_conflicts",result.values().stream().filter(v->v.status().equals("conflicting_contributors")).count());return result;
    }
    private Map<String,Object> manhattan(Context c,Map<String,Object> p,String stem,List<Point> points,String declaredBuild)throws IOException {
        var builds=new LinkedHashSet<String>();if(!declaredBuild.isBlank())builds.add(StudyPoolingAudit.canonical("genome_build",declaredBuild));for(var point:points)if(!missingBuild(point.build()))builds.add(StudyPoolingAudit.canonical("genome_build",point.build()));
        var located=points.stream().filter(v->!v.chromosome().isBlank()&&v.position()>0).sorted(Comparator.comparing(Point::chromosome,StudyGwasDiagnostics::compareChromosome).thenComparingLong(Point::position).thenComparing(Point::feature)).toList();
        if(points.stream().anyMatch(v->v.labels().getOrDefault("coordinate_projection_status","").equals("conflicting_contributors")))return Map.of("status","held_contributor_coordinate_conflicts","coordinate_tests",located.size(),"missing_coordinates",points.size()-located.size());
        if(builds.size()>1)return Map.of("status","held_mixed_genome_builds","coordinate_tests",located.size(),"missing_coordinates",points.size()-located.size());
        if(located.isEmpty())return Map.of("status","unavailable_coordinates","coordinate_tests",0,"missing_coordinates",points.size());
        var offsets=new LinkedHashMap<String,Long>();long offset=0;String previous="";long last=0;var rows=new ArrayList<String[]>();var xx=new ArrayList<Double>();var yy=new ArrayList<Double>();var plottedChromosomes=new ArrayList<String>();
        int step=Math.max(1,(int)Math.ceil((double)located.size()/integer(p,"maximum_plot_points")));int selected=0;
        for(int i=0;i<located.size();i++){var point=located.get(i);if(!point.chromosome().equals(previous)){if(last>9007199254740991L-offset)return Map.of("status","held_coordinate_range","coordinate_tests",located.size(),"missing_coordinates",points.size()-located.size());offset+=last;last=0;previous=point.chromosome();offsets.put(previous,offset);}last=Math.max(last,point.position());if(point.position()>9007199254740991L-offset)return Map.of("status","held_coordinate_range","coordinate_tests",located.size(),"missing_coordinates",points.size()-located.size());double x=offset+(double)point.position(),y=-point.logP()/Math.log(10);rows.add(new String[]{point.feature(),point.chromosome(),Long.toString(point.position()),Double.toString(x),Double.toString(y),Boolean.toString(y>number(p,"plot_y_limit"))});if(i%step==0)selected=i;else if(point.logP()<located.get(selected).logP())selected=i;if(i%step==step-1||i==located.size()-1){var peak=located.get(selected);double px=offsets.get(peak.chromosome())+(double)peak.position();xx.add(px);yy.add(Math.min(number(p,"plot_y_limit"),-peak.logP()/Math.log(10)));plottedChromosomes.add(peak.chromosome());}}
        FollowupSupport.table(c.outputDirectory().resolve(stem+"-manhattan.tsv"),List.of("feature_id","chromosome","position","cumulative_position","observed_neg_log10_p","display_capped"),rows);outputs.add(stem+"-manhattan.tsv");
        var chart=Plots.xy().options(PlotOptions.builder().title("Manhattan: available declared coordinates").xLabel("Chromosome:position (Mb); observed chromosome offsets").yLabel("-log10(P), capped at "+number(p,"plot_y_limit")+" for display").legend(LegendPosition.NONE).size(1100,650).build()).xAxisFormatter(value->{String chromosome=offsets.keySet().iterator().next();long base=0;for(var e:offsets.entrySet())if(value>=e.getValue()){chromosome=e.getKey();base=e.getValue();}return chromosome+":"+String.format(java.util.Locale.ROOT,"%.3f",(value-base)/1000000);});
        int color=0;for(String chromosome:new LinkedHashSet<>(plottedChromosomes)){var cx=new ArrayList<Double>();var cy=new ArrayList<Double>();for(int i=0;i<xx.size();i++)if(plottedChromosomes.get(i).equals(chromosome)){cx.add(xx.get(i));cy.add(yy.get(i));}chart.points(chromosome,cx.stream().mapToDouble(Double::doubleValue).toArray(),cy.stream().mapToDouble(Double::doubleValue).toArray(),color++%2==0?new Color(0,114,178):new Color(213,94,0));}
        var plot=chart.build();plot.save(c.outputDirectory().resolve(stem+"-manhattan.png"));plot.save(c.outputDirectory().resolve(stem+"-manhattan.pdf"));outputs.add(stem+"-manhattan.png");outputs.add(stem+"-manhattan.pdf");
        return Map.of("status",located.size()==points.size()?builds.isEmpty()?"observed_coordinates_build_unknown":"observed_coordinates":"partial_coordinates","coordinate_tests",located.size(),"missing_coordinates",points.size()-located.size(),"genome_build_status",builds.isEmpty()?"unknown":"declared_single_known_build","unknown_build_tests",points.stream().filter(v->missingBuild(v.build())).count(),"chromosome_offsets",offsets,"plotted_points",xx.size(),"display_selection","minimum-P peak per consecutive coordinate bin; full numerical table retained");
    }
    private static int compareChromosome(String a,String b){int left=chromosomeOrder(a),right=chromosomeOrder(b);return left==right?a.compareTo(b):Integer.compare(left,right);}
    private static int chromosomeOrder(String value){try{int n=Integer.parseInt(value);return n>0&&n<=22?n:100;}catch(NumberFormatException e){return switch(value){case "X"->23;case "Y"->24;case "XY"->25;case "MT"->26;default->100;};}}
    private static String chromosome(String value){if(StudyResultShape.missing(value))return "";String v=value.replaceFirst("(?i)^chr","").toUpperCase(java.util.Locale.ROOT);if(v.equals("M"))v="MT";if(v.matches("0+[0-9]+"))try{v=Integer.toString(Integer.parseInt(v));}catch(NumberFormatException largeContig){return v;}return v;}
    private static long position(String value){double n=parse(value);return Double.isFinite(n)&&n>0&&n==Math.rint(n)&&n<=9007199254740991.0?(long)n:0;}
    private static String bin(String value,List<?> breaks){double x=parse(value);if(!Double.isFinite(x))return "unknown";if(x<0)return "negative_source_value";double lower=0;for(Object raw:breaks){double upper=((Number)raw).doubleValue();if(x<upper)return "["+lower+","+upper+")";lower=upper;}return "["+lower+",infinity)";}
    private static String label(String value){return value.isBlank()||StudyResultShape.missing(value)?"unknown":value.length()<=160&&!value.chars().anyMatch(Character::isISOControl)?value:"unavailable_invalid_label";}
    private static boolean missingBuild(String value){return StudyResultShape.missing(value)||Set.of("unknown","unspecified","unavailable","auto").contains(value.toLowerCase(java.util.Locale.ROOT));}
    private static double parse(String value){try{return Double.parseDouble(value);}catch(NumberFormatException e){return Double.NaN;}}
    private static String at(DelimitedData table,String[] row,String field){return table.header().contains(field)?row[table.column(field)]:"";}
    private static int integer(Map<String,Object> p,String field){return ((Number)p.get(field)).intValue();}
    private static double number(Map<String,Object> p,String field){return ((Number)p.get(field)).doubleValue();}
}
