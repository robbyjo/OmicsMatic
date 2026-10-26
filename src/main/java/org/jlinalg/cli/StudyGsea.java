/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.enrichment.EnrichmentAnalysis;
import org.jlinalg.enrichment.GeneSet;
import org.jlinalg.enrichment.RankedEnrichment;
import org.jlinalg.study.StudyGseaPolicy;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.study.StudyOperationExecutor.*;

/** Full-universe preranked enrichment, separately identified from phenotype permutation. */
final class StudyGsea implements StudyOperationExecutor {
    private static final String CAVEAT="Preranked gene-set permutation conditions on the supplied ranking and assumes the gene-position null; it does not preserve inter-gene/sample dependence or reproduce phenotype/block permutations. NES and enrichment are association context, not pathway activity, causality or experimentally established function. Adjusted P values use the named BH/BY/NONE rule, not Broad's pooled-NES empirical GSEA FDR. Finite Monte Carlo estimates retain counts/resolution/intervals and can change near a threshold.";
    @Override public Result execute(Context c) throws Exception {
        Files.createDirectories(c.outputDirectory());
        var p=StudyGseaPolicy.resolve(map(StudyResultTables.analysis(c).get("gsea")));
        var summary=new LinkedHashMap<String,Object>();summary.put("null_model",p.get("null_model"));summary.put("gene_id_type",p.get("gene_id_type"));
        if(!text(c.operation().get("dataset_id")).isBlank())summary.put("dataset_id",c.operation().get("dataset_id"));
        boolean phenotypeNull="phenotype".equals(p.get("null_model"));
        summary.put("required_manuscript_caveat",phenotypeNull?StudyGseaNullRanks.CAVEAT:CAVEAT);
        summary.put("rank_interpretation",p.getOrDefault("rank_interpretation","Higher/lower source ranking statistic; biological activity and contrast orientation require the source model/protocol"));
        if(phenotypeNull&&!p.containsKey("phenotype")&&!p.containsKey("null_rankings"))return finish(c,p,summary,false,false,"Phenotype GSEA cannot substitute a gene-set null. Declare gsea.phenotype for registered independent Gaussian OLS residual permutations, or supply complete ranking plus model-matched null_rankings.");
        StudyGseaNullRanks nullRanks=null;
        try {
        var ranks=new ArrayList<RankedEnrichment.Rank>();var audit=new ArrayList<String[]>();
        Path source=null,snapshot=null;
        if(phenotypeNull&&p.containsKey("phenotype")) {
            try{nullRanks=StudyGseaNullRanks.nativeOls(c,p,summary);}catch(IllegalArgumentException prerequisite){return finish(c,p,summary,false,false,prerequisite.getMessage());}
            ranks.addAll(nullRanks.observed);source=snapshot=nullRanks.observedFile;
        } else if(p.containsKey("ranking")) {
            var ranking=map(p.get("ranking"));source=c.project().resolve(text(ranking.get("file"))).normalize();
            if(!Files.isRegularFile(source))return finish(c,p,summary,false,false,"Supply the complete local gsea.ranking.file; other independent branches can continue");
            if(!"complete".equals(ranking.get("coverage"))||!ranking.containsKey("tested_genes"))return finish(c,p,summary,false,false,"A ranking file needs coverage: complete and its original tested_genes count; selected/significant-only rankings cannot define GSEA");
            snapshot=snapshot(c,source,source.toString().endsWith(".csv")?"supplied-ranking.csv":"supplied-ranking.tsv");
            var table=DelimitedData.readResult(snapshot);int gene=table.column(text(ranking.get("gene_column"))),score=table.column(text(ranking.get("statistic_column")));
            if(table.rows().size()!=((Number)ranking.get("tested_genes")).intValue())return finish(c,p,summary,false,false,"Declared tested_genes does not match the supplied full ranking; preserve the complete ranked universe");
            for(String[] row:table.rows())ranks.add(new RankedEnrichment.Rank(row[gene],finite(row[score],"ranking statistic")));
            summary.put("ranking_origin","externally_produced_rank_list_not_refitted");summary.put("ranking_provenance",ranking.getOrDefault("provenance",Map.of()));
            summary.put("required_manuscript_caveat",summary.get("required_manuscript_caveat")+" Ranking completeness, feature eligibility and original model calibration are source declarations; table counts/identities do not independently reproduce them.");
        } else {
            String scopedDataset=text(c.operation().get("dataset_id"));
            var primary=scopedDataset.isBlank()?StudyPrimaryResults.official(c):StudyPrimaryResults.dataset(c,scopedDataset,"dataset_ranking");
            source=primary==null?null:primary.source().path();if(source==null)return finish(c,p,summary,false,false,"Ranked GSEA needs one completed scoped all-feature association/meta-analysis or an explicit complete ranking file");
            if(variantRanking(c,source))return finish(c,p,summary,false,false,"Variant-level GWAS results need a complete gene-based ranking with an appropriate LD-aware model. Native GSEA does not turn nearest-gene SNP beta/SE values into independent gene scores.");
            var supplied=primary.family();
            summary.put("primary_selection",primary.receipt());
            if(!supplied.isEmpty()&&!"complete".equals(supplied.get("coverage")))return finish(c,p,summary,false,false,"A selected-only supplied result cannot establish the complete GSEA ranking, even when its source FDR is known");
            snapshot=snapshot(c,source,"source-statistics.tsv");var table=primary.table();int feature=table.column("feature_id");
            if(supplied.get("tested_count") instanceof Number count&&count.longValue()>primary.original().rows().size())return finish(c,p,summary,false,false,"Supplied full-family denominator exceeds the available feature rows. GSEA needs the complete eligible ranking/dispositions, not only source-adjusted selected rows relabeled as complete.");
            if("beta_se".equals(p.get("statistic"))){table.column("beta");table.column("se");}else table.column(text(p.get("statistic_column")));
            if("beta_se".equals(p.get("statistic"))&&table.rows().stream().anyMatch(row->StudyResultShape.pOnly(table,row)))return finish(c,p,summary,false,false,"P-only eligible tests do not define a complete signed beta/SE ranking. Supply a complete registered ranking or an appropriate explicit statistic; no test-only rows were silently dropped.");
            var scores=new LinkedHashMap<String,Double>();
            for(String[] row:table.rows()) {
                if(table.header().contains("status")&&!Set.of("ok","single_cohort","tested","complete").contains(row[table.column("status")].toLowerCase(java.util.Locale.ROOT))){audit.add(new String[]{row[feature],"","unestimable_source_row","NA"});continue;}
                double statistic;
                try {
                    if(table.header().contains("p_value")){double sourceP=finite(row[table.column("p_value")],"source P");if(sourceP<0||sourceP>1)throw new IllegalArgumentException("Source P outside [0,1]");}
                    statistic="beta_se".equals(p.get("statistic"))?finite(row[table.column("beta")],"beta")/positive(row[table.column("se")]):finite(row[table.column(text(p.get("statistic_column")))],"statistic");
                } catch(IllegalArgumentException invalid) {audit.add(new String[]{row[feature],"","unrankable_source_statistic","NA"});continue;}
                if(!Double.isFinite(statistic)){audit.add(new String[]{row[feature],"","unrankable_source_statistic","NA"});continue;}
                if(scores.putIfAbsent(row[feature],statistic)!=null)throw new IOException("Ambiguous source contrast/duplicate feature for GSEA: "+row[feature]);
            }
            String dataset=text(c.operation().get("dataset_id"));
            Context mappingContext=c;
            if(!dataset.isBlank()){
                StudyFeatureAnnotations.write(c,Map.of(dataset,snapshot));var dependencies=new LinkedHashMap<>(c.dependencyDirectories());dependencies.put("gsea-local-mapping",c.outputDirectory());mappingContext=new Context(c.project(),c.outputDirectory(),c.operation(),c.study(),c.plan(),dependencies);
            }
            var mappings=new StudyCliExecutor().geneMappings(mappingContext,scores.keySet());var genes=new LinkedHashMap<String,List<Double>>();
            for(var entry:scores.entrySet()) {
                var mapping=mappings.get(entry.getKey());String gene=mapping==null?"":"ensembl_gene".equals(p.get("gene_id_type"))?mapping.ensembl():"symbol".equals(p.get("gene_id_type"))?mapping.symbol():"";
                if(gene.isBlank()){audit.add(new String[]{entry.getKey(),"","unmapped_gene_namespace",String.valueOf(entry.getValue())});continue;}
                genes.computeIfAbsent(gene,k->new ArrayList<>()).add(entry.getValue());audit.add(new String[]{entry.getKey(),gene,"mapped",String.valueOf(entry.getValue())});
            }
            FollowupSupport.table(c.outputDirectory().resolve("feature-rank-mapping.tsv"),List.of("feature_id","gene_id","status","statistic"),audit);
            long duplicates=genes.values().stream().filter(values->values.size()>1).count();summary.put("genes_with_multiple_features",duplicates);summary.put("source_estimable_features",scores.size());summary.put("mapped_genes",genes.size());summary.put("source_FDR_filter_applied",false);
            if(duplicates>0&&"require_unique".equals(p.get("collapse")))return finish(c,p,summary,false,false,"Multiple measured features map to the same gene. Register a scientifically coherent mean/median collapse or supply a complete gene-level ranking; no best-P feature was selected.");
            for(var entry:genes.entrySet()) {
                List<Double> values=entry.getValue();double value;
                if("median".equals(p.get("collapse"))){var ordered=values.stream().sorted().toList();int middle=ordered.size()/2;value=ordered.size()%2==0?ordered.get(middle-1)/2+ordered.get(middle)/2:ordered.get(middle);}
                else {value=0;for(double v:values)value+=v/values.size();}
                ranks.add(new RankedEnrichment.Rank(entry.getKey(),value));
            }
            summary.put("ranking_origin","complete_scoped_source_statistics_no_significance_filter");summary.put("source_testing_family",supplied);
            Path manifest=sourceManifest(source);if(manifest!=null){var producer=StudyYaml.loadArtifact(manifest);summary.put("required_manuscript_caveat",CAVEAT+" "+text(map(producer.get("details")).get("required_manuscript_caveat")));}
        }
        summary.put("source_sha256",FollowupSupport.hash(snapshot));
        if(phenotypeNull&&nullRanks==null)try{nullRanks=StudyGseaNullRanks.supplied(c,p,ranks,snapshot,summary);}catch(IllegalArgumentException prerequisite){return finish(c,p,summary,false,false,prerequisite.getMessage());}
        if(ranks.size()<2)return finish(c,p,summary,false,false,"At least two uniquely mapped ranked genes are required; inspect feature/mapping dispositions");
        if(ranks.size()>integer(p,"max_genes"))return finish(c,p,summary,false,false,"Complete ranked universe exceeds max_genes; no top-gene truncation was applied");
        String species=species(c,p,source);summary.put("species",species);
        if(species.equals("unknown"))return finish(c,p,summary,false,false,"Declare GSEA species or consistent source scope before selecting a species-matched collection");
        var sets=geneSets(c,p,summary,species);if(sets==null)return finish(c,p,summary,false,false,"GSEA needs a verified species/namespace-matched gene-set collection; supply gene_sets GMT or enable the appropriate resource acquisition");
        if(sets.isEmpty())return finish(c,p,summary,false,false,"The gene-set collection is empty; supply a valid collection instead of treating absent resource data as an enrichment null");
        if(sets.size()>integer(p,"max_sets"))return finish(c,p,summary,false,false,"Complete collection exceeds max_sets; register an outcome-independent collection rather than truncating terms");
        RankedEnrichment.Analysis result;
        try {
            var options=new RankedEnrichment.Options(((Number)p.get("weight_exponent")).doubleValue(),integer(p,"minimum_size"),integer(p,"maximum_size"),integer(p,"permutations"),integer(p,"maximum_exact_states"),integer(p,"seed"),integer(p,"maximum_work")-(nullRanks==null?0:nullRanks.modelWork),"gene_id".equals(p.get("tie_policy")),EnrichmentAnalysis.Fdr.valueOf(text(p.get("adjustment"))));
            result=nullRanks==null?RankedEnrichment.analyze(ranks,sets,options):RankedEnrichment.analyzeRankings(ranks,sets,options,nullRanks.draws,nullRanks.exhaustive,nullRanks.method,nullRanks::draw);
        } catch(IllegalArgumentException protocol){return finish(c,p,summary,false,false,protocol.getMessage());}
        FollowupSupport.table(c.outputDirectory().resolve("ranked-genes.tsv"),List.of("gene_id","statistic","rank"),java.util.stream.IntStream.range(0,result.ranking().size()).mapToObj(i->new String[]{result.ranking().get(i).gene(),String.valueOf(result.ranking().get(i).statistic()),String.valueOf(i+1)}).toList());
        FollowupSupport.table(c.outputDirectory().resolve("term-dispositions.tsv"),List.of("term_id","status"),result.dispositions().entrySet().stream().map(e->new String[]{e.getKey(),e.getValue()}).toList());
        var rows=new ArrayList<String[]>();var leading=new ArrayList<String[]>();StringBuilder gmt=new StringBuilder();
        for(var r:result.results()) {
            rows.add(new String[]{r.id(),r.name(),String.valueOf(r.size()),r.status(),value(r.enrichmentScore()),value(r.normalizedScore()),value(r.pValue()),value(r.adjustedP()),r.nullMethod(),String.valueOf(r.nullStates()),String.valueOf(r.sameSignStates()),String.valueOf(r.tailStates()),value(r.pResolution()),value(r.pMonteCarloLower()),value(r.pMonteCarloUpper()),String.join(";",r.leadingEdge())});
            for(String gene:r.leadingEdge())leading.add(new String[]{r.id(),gene,r.enrichmentScore()>=0?"positive":"negative"});
            gmt.append(r.id()).append('\t').append(r.name().replace('\t',' ')).append('\t').append(String.join("\t",r.members())).append('\n');
        }
        String output=result.completeFamily()?"ranked-gsea.tsv":"ranked-gsea-limited.tsv";
        FollowupSupport.table(c.outputDirectory().resolve(output),List.of("term_id","term_name","ranked_members","status","enrichment_score","normalized_enrichment_score","p_value","adjusted_p","null_method","null_states","same_sign_states","tail_states","p_resolution","p_mc_lower_95","p_mc_upper_95","leading_edge_genes"),rows);
        FollowupSupport.table(c.outputDirectory().resolve("leading-edge.tsv"),List.of("term_id","gene_id","direction"),leading);Files.writeString(c.outputDirectory().resolve("tested-gene-sets.gmt"),gmt);
        summary.put("ranked_genes",result.ranking().size());summary.put("tested_terms",result.results().size());summary.put("complete_testing_family",result.completeFamily());summary.put("adjustment",p.get("adjustment"));summary.put("tied_genes",result.tiedGenes());summary.put("work_units",result.workUnits());summary.put("effective_gene_sets","tested-gene-sets.gmt");summary.put("canonical_results",output);summary.put("analysis_performed",!result.results().isEmpty());
        summary.put("total_work_units",result.workUnits()+(nullRanks==null?0:nullRanks.modelWork));
        summary.put("p_value_rule","Same-sign ES tail: exhaustive count/conditional states, or (tail+1)/(same-sign draws+1); no zero Monte Carlo P values; comparisons include 1e-12 floating tolerance. Exhaustive residual enumeration does not imply exact nuisance-model calibration.");summary.put("normalization",phenotypeNull?"Observed ES / that term's mean absolute same-sign model-null ES; complete null rankings shared across all terms, preserving fixed feature dependence":"Observed ES / mean absolute same-sign null ES; null samples are shared by set size within the fixed ranking");
        summary.put("score_rule","Hit increments proportional to |statistic|^weight_exponent, uniform miss decrements; largest absolute running-sum extremum, with positive ES for extrema within 1e-12; negative leading edges follow the minimum, positive edges end at the maximum");
        summary.put("monte_carlo_interval","95% Wilson binomial interval for raw tail counts conditional on the same-sign draws; this interval is not adjusted for multiple testing or uncertainty in the ranking/null model");
        if(result.tiedGenes()>0)summary.put("required_manuscript_caveat",summary.get("required_manuscript_caveat")+" Ranking ties were ordered lexicographically by gene ID under an explicit protocol; pathway membership can be sensitive to this unresolved ordering.");
        if(result.completeFamily())curves(c,p,result,summary);
        return finish(c,p,summary,result.completeFamily(),!result.results().isEmpty(),result.completeFamily()?(result.results().isEmpty()?"No gene sets met the prespecified ranked-universe/size eligibility; GSEA was not tested":"Completed "+(phenotypeNull?"model-null":"preranked gene-set null")+" GSEA with a complete declared term family and retained leading edges"):"Some eligible terms have an undefined score or insufficient same-sign null; retained results have no official correction family");
        } finally {if(nullRanks!=null)nullRanks.close();}
    }

    private static Path sourceManifest(Path source){Path parent=source.getParent();for(int i=0;i<2&&parent!=null;i++,parent=parent.getParent())if(Files.isRegularFile(parent.resolve("manifest.yaml")))return parent.resolve("manifest.yaml");return null;}
    private static boolean variantRanking(Context c,Path source) throws IOException {
        Set<String> genes=Set.of("gene_symbol","ensembl_gene");Set<String> selected=new java.util.HashSet<>(list(c.operation().get("datasets")).stream().map(StudyPublicEvidence::text).toList());
        for(var dataset:StudyBioinformatics.datasets(c))if(selected.contains(text(dataset.get("id")))&&Set.of("gwas","genomics").contains(text(dataset.get("type")))&&!genes.contains(text(dataset.get("feature_id_type"))))return true;
        Path metadata=source.getParent().resolve("supplied-analysis.yaml");if(Files.isRegularFile(metadata)){var scope=map(StudyYaml.loadArtifact(metadata).get("scope"));return "variant".equals(scope.get("feature_id_type"))||Set.of("gwas","genomics").contains(text(scope.get("modality")))&&!genes.contains(text(scope.get("feature_id_type")));}return false;
    }
    private static String species(Context c,Map<String,Object> p,Path source) throws IOException {
        if(p.containsKey("species"))return text(p.get("species"));Path parent=source.getParent();
        if(Files.isRegularFile(parent.resolve("supplied-analysis.yaml"))){var scope=map(StudyYaml.loadArtifact(parent.resolve("supplied-analysis.yaml")).get("scope"));if(scope.containsKey("species"))return text(scope.get("species"));}
        var values=new LinkedHashSet<String>();Set<String> selected=new LinkedHashSet<>(list(c.operation().get("datasets")).stream().map(StudyPublicEvidence::text).toList());for(var dataset:StudyBioinformatics.datasets(c))if(selected.contains(text(dataset.get("id")))&&dataset.containsKey("species"))values.add(text(dataset.get("species")));return values.size()==1?values.iterator().next():"unknown";
    }
    static List<GeneSet> geneSets(Context c,Map<String,Object> p,Map<String,Object> summary,String species) throws Exception {
        if(p.containsKey("gene_sets")) {
            Path source=c.project().resolve(text(p.get("gene_sets")));if(!Files.isRegularFile(source))return null;
            String name=source.toString().endsWith(".gz")?"gene-sets.gmt.gz":"gene-sets.gmt";source=snapshot(c,source,name);summary.put("collection_source_sha256",FollowupSupport.hash(source));summary.put("collection_release","User-supplied GMT; version/namespace/species suitability not independently certified");summary.put("collection_provenance",p.getOrDefault("collection_provenance",Map.of()));
            var sets=new ArrayList<GeneSet>();long memberships=0;
            try(var input=Files.newInputStream(source);var decoded=source.toString().endsWith(".gz")?new java.util.zip.GZIPInputStream(input):input;var reader=new java.io.BufferedReader(new java.io.InputStreamReader(decoded,java.nio.charset.StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){if(line.isBlank())continue;String[] fields=line.split("\t",-1);if(fields.length<3)throw new IOException("GMT requires term ID, description and members");var genes=new LinkedHashSet<String>();for(int i=2;i<fields.length;i++){if(fields[i].isBlank())throw new IOException("Blank GMT member");genes.add(fields[i]);}memberships+=genes.size();if(memberships>5000000L||sets.size()>=integer(p,"max_sets"))throw new IOException("Gene-set resource exceeds registered set/5-million-membership storage bounds");sets.add(new GeneSet(fields[0],fields[1],genes));}}
            return sets;
        }
        if(!Set.of("GO","Reactome").contains(text(p.get("collection"))))return null;
        if(!Set.of("human","Homo sapiens","9606").contains(species))return null;
        String namespace="GO".equals(p.get("collection"))?"symbol":"ensembl_gene";if(!namespace.equals(p.get("gene_id_type")))return null;
        Path database=StudyResourceManager.resourceRoot(c.project(),c.study()).resolve("enrichment/"+(namespace.equals("symbol")?"go-human":"reactome-human"));
        write(c.outputDirectory().resolve("resource-acquisition-intent.yaml"),Map.of("collection",p.get("collection"),"species",species,"gene_id_type",p.get("gene_id_type"),"resource_directory",database.toString(),"request_needed",!Files.isRegularFile(database.resolve("manifest.properties")),"rationale","Preranked complete-gene-universe enrichment; inbound annotation only, no private gene or participant query"));
        if(!Files.isRegularFile(database.resolve("manifest.properties"))){if(!StudyResourceManager.automatic(c.study()))return null;Files.createDirectories(database.getParent());var log=new java.io.ByteArrayOutputStream();int status=EnrichmentCli.run(new String[]{"--enrichment",text(p.get("collection")),"--download",database.toString(),"--species","human","--gene-id-type",namespace.equals("symbol")?"symbol":"ensembl"},new java.io.PrintStream(log),new java.io.PrintStream(log));Files.writeString(c.outputDirectory().resolve("resource-download.log"),log.toString(java.nio.charset.StandardCharsets.UTF_8));if(status!=0)return null;}
        var db=EnrichmentDatabase.load(database,text(p.get("collection")),Map.of("--gene-id-type",namespace.equals("symbol")?"symbol":"ensembl","--species","human"));Files.copy(database.resolve("manifest.properties"),c.outputDirectory().resolve("collection-manifest.properties"));summary.put("collection_release",new LinkedHashMap<>(db.metadata));return db.sets();
    }
    private static int integer(Map<String,Object> p,String key){return ((Number)p.get(key)).intValue();}
    private static void curves(Context c,Map<String,Object> p,RankedEnrichment.Analysis analysis,Map<String,Object> summary) throws IOException {
        var selected=analysis.results().stream().sorted(java.util.Comparator.comparingDouble(RankedEnrichment.Result::adjustedP).thenComparing(java.util.Comparator.comparingDouble((RankedEnrichment.Result r)->Math.abs(r.normalizedScore())).reversed()).thenComparing(RankedEnrichment.Result::id)).limit(integer(p,"curve_plots")).toList();
        double maximum=analysis.ranking().stream().mapToDouble(r->Math.abs(r.statistic())).max().orElse(0),exponent=((Number)p.get("weight_exponent")).doubleValue();
        var index=new ArrayList<String[]>();int ordinal=0;
        for(var term:selected) {
            var members=new java.util.HashSet<>(term.members());double mass=0;for(var rank:analysis.ranking())if(members.contains(rank.gene()))mass+=exponent==0?1:Math.pow(Math.abs(rank.statistic())/maximum,exponent);
            int n=analysis.ranking().size();double[] x=new double[n+1],y=new double[n+1];var rows=new ArrayList<String[]>();double running=0;
            for(int i=0;i<n;i++){var rank=analysis.ranking().get(i);boolean hit=members.contains(rank.gene());running+=hit?(exponent==0?1:Math.pow(Math.abs(rank.statistic())/maximum,exponent))/mass:-1.0/(n-members.size());x[i+1]=i+1;y[i+1]=running;rows.add(new String[]{rank.gene(),String.valueOf(i+1),String.valueOf(running),String.valueOf(hit)});}
            String stem="gsea-curve-"+String.format(java.util.Locale.ROOT,"%03d",++ordinal);FollowupSupport.table(c.outputDirectory().resolve(stem+".tsv"),List.of("gene_id","rank","running_es","member"),rows);
            var plot=org.jlinalg.plot.Plots.line(x,y,org.jlinalg.plot.PlotOptions.builder().title(term.id()+"; adjusted P="+String.format(java.util.Locale.ROOT,"%.3g",term.adjustedP())).xLabel("Rank (descending statistic)").yLabel("Running enrichment score").build());
            for(String extension:List.of("png","pdf"))plot.save(c.outputDirectory().resolve(stem+"."+extension));
            index.add(new String[]{term.id(),stem+".tsv",stem+".png",stem+".pdf",String.valueOf(term.enrichmentScore()),String.valueOf(term.adjustedP())});
        }
        FollowupSupport.table(c.outputDirectory().resolve("curve-plots.tsv"),List.of("term_id","curve","png","pdf","enrichment_score","adjusted_p"),index);summary.put("diagnostic_curve_count",selected.size());summary.put("curve_selection_rule","At most curve_plots diagnostic curves by adjusted P, absolute NES and term ID; complete tested family remains in ranked-gsea.tsv and is not truncated");
    }
    private static Path snapshot(Context c,Path source,String name) throws IOException {String hash=FollowupSupport.hash(source);Path copy=c.outputDirectory().resolve(name);Files.copy(source,copy);if(!hash.equals(FollowupSupport.hash(copy))||!hash.equals(FollowupSupport.hash(source)))throw new IOException("GSEA input changed while taking its immutable snapshot: "+source);return copy;}
    private static double finite(String value,String label){double d;try{d=Double.parseDouble(value);}catch(NumberFormatException invalid){throw new IllegalArgumentException("Invalid "+label);}if(!Double.isFinite(d))throw new IllegalArgumentException("Nonfinite "+label);return d;}
    private static double positive(String value){double d=finite(value,"SE");if(d<=0)throw new IllegalArgumentException("SE must be positive");return d;}
    private static String value(double value){return Double.isFinite(value)?String.valueOf(value):"NA";}
    private static Result finish(Context c,Map<String,Object> p,Map<String,Object> summary,boolean complete,boolean performed,String message) throws IOException {
        summary.put("method_report",Map.of("performed",performed,"operation","ranked_gsea","method","phenotype".equals(p.get("null_model"))?"Weighted running-sum enrichment with complete shared model-null rankings":"Weighted preranked running-sum enrichment with exact/Monte Carlo same-size gene-set nulls","parameters",p,"software","JLinAlg "+JLinAlgCli.version(),"references","phenotype".equals(p.get("null_model"))?List.of("https://doi.org/10.1073/pnas.0506580102","https://doi.org/10.1016/j.neuroimage.2014.01.060"):List.of("https://doi.org/10.1073/pnas.0506580102","https://gsea-msigdb.github.io/gseapreranked-gpmodule/v7/"),"interpretation",message));
        if(!complete)summary.put("needs",List.of(StudyNeed.review("gsea:"+c.operation().get("hypothesis")+":"+c.operation().getOrDefault("id","ranking"),"What is needed for the registered GSEA analysis?",message,true).document()));
        summary.put("interpretation",message);write(c.outputDirectory().resolve("gsea-analysis.yaml"),summary);var outputs=new ArrayList<String>();try(var paths=Files.walk(c.outputDirectory())){for(Path path:paths.filter(Files::isRegularFile).sorted().toList())outputs.add(c.outputDirectory().relativize(path).toString().replace('\\','/'));}
        return new Result(complete?State.COMPLETE:State.WAITING_FOR_INPUT,message,outputs,summary);
    }
}
