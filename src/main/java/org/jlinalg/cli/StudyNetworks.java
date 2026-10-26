/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.jlinalg.compute.BackendPolicy;
import org.jlinalg.enrichment.EnrichmentAnalysis;
import org.jlinalg.enrichment.EnrichmentResult;
import org.jlinalg.network.NetworkAnalysis;
import org.jlinalg.network.NetworkModules;
import org.jlinalg.ols.Ols;
import org.jlinalg.ols.OlsOptions;
import org.jlinalg.pipeline.DelimitedMatrixSource;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyNetworkPolicy;
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.study.StudyOperationExecutor.*;

/** Scoped descriptive networks, retained module memberships and joint module GO. */
final class StudyNetworks implements StudyOperationExecutor {
    private static final String CAVEAT = "Association/overlap edges and module GO are descriptive hypotheses, not validated regulatory interactions, causal mechanisms or experimentally established functions. Correlation thresholds and module choices must not be tuned for preferred GO results.";
    private static final List<String> GO_COLUMNS=List.of("module_id","module_type","term_id","term_name","universe_genes","module_genes","term_genes","overlap_genes","p_value","log_p_value","adjusted_p","log_adjusted_p","hit_genes");
    private static Map<String,Object> policy(Context c) {return StudyNetworkPolicy.resolve(map(map(StudyBioinformatics.hypothesis(c).get("analysis")).get("network")));}
    @Override public Result execute(Context c) throws Exception {
        Files.createDirectories(c.outputDirectory());
        return switch(text(c.operation().get("type"))) {
            case "network_analysis" -> correlation(c,policy(c));
            case "pathway_network" -> pathway(c,policy(c));
            case "network_modules" -> modules(c,policy(c));
            case "module_gene_ontology" -> go(c,policy(c));
            default -> throw new IllegalArgumentException("Unsupported network operation");
        };
    }
    private static Result finish(Context c,String file,Map<String,Object> summary,List<String> outputs,boolean complete,String message) throws IOException {
        summary.put("required_manuscript_caveat",CAVEAT + " " + text(summary.get("required_manuscript_caveat")));
        boolean performed=summary.get("analysis_performed") instanceof Boolean declared?declared:complete||summary.containsKey("memberships");
        summary.put("method_report",Map.of("performed",performed,"complete",complete,"operation",c.operation().get("type"),"parameters",policy(c),"software","JLinAlg "+JLinAlgCli.version(),"interpretation",message));
        if(!complete) summary.put("needs",List.of(StudyNeed.review("network:"+c.operation().get("hypothesis")+":"+c.operation().getOrDefault("id",c.operation().get("type")),"What is needed for this network analysis?",message,true).document()));
        write(c.outputDirectory().resolve(file),summary);var all=new ArrayList<>(outputs);all.add(file);
        // Seal every locally generated companion, including participant-local
        // sample audits and optional annotation resource receipts.
        try(var paths=Files.walk(c.outputDirectory())){for(Path path:paths.filter(Files::isRegularFile).sorted().toList()){String name=c.outputDirectory().relativize(path).toString().replace('\\','/');if(!all.contains(name))all.add(name);}}
        return new Result(complete?State.COMPLETE:State.WAITING_FOR_INPUT,message,all,summary);
    }
    private static int integer(Map<String,Object> p,String key){return ((Number)p.get(key)).intValue();}
    private static double number(Map<String,Object> p,String key){return ((Number)p.get(key)).doubleValue();}
    private static String mapped(Map<String,Object> dictionary,String key){return text(dictionary.getOrDefault(key,key));}
    private static boolean missing(String value){return value==null||Set.of("","na","nan","null",".").contains(value.trim().toLowerCase(java.util.Locale.ROOT));}

    private static Result correlation(Context c,Map<String,Object> p) throws Exception {
        var summary=new LinkedHashMap<String,Object>();summary.put("network_type","molecular_correlation");summary.put("edge_rule","abs(rho) >= threshold; signed weights retained");
        String id=text(c.operation().get("dataset_id"));
        var dataset=StudyBioinformatics.datasets(c).stream().filter(d->id.equals(d.get("id"))).findFirst().orElse(null);
        if(dataset==null||!StudyNetworkPolicy.MOLECULAR.contains(text(dataset.get("type"))))return finish(c,"network-analysis.yaml",summary,List.of(),false,"Select one supported bulk molecular matrix per correlation network; genotype, single-cell, spatial and compositional adapters require an appropriate independent-unit model");
        var files=map(dataset.get("files"));var columns=map(dataset.get("columns"));var hypothesis=StudyBioinformatics.hypothesis(c);
        if(!files.containsKey("matrix"))return finish(c,"network-analysis.yaml",summary,List.of(),false,"Correlation networks require a feature-by-sample matrix");
        var source=DelimitedMatrixSource.open(StudyAssayQcSources.matrix(c,dataset));var metadata=source.metadata();
        int samples=metadata.sampleIds().size();
        if(metadata.rowCount()>integer(p,"max_features")||samples>integer(p,"max_samples")||metadata.rowCount()*samples>20_000_000L)
            return finish(c,"network-analysis.yaml",summary,List.of(),false,"Matrix exceeds registered sample/feature/storage bounds; register an outcome-independent feature universe or a scalable external network adapter, without silently selecting top hits");
        int features=(int)metadata.rowCount();
        List<String> covariates=list(hypothesis.get("covariates")).stream().map(StudyPublicEvidence::text).toList();
        Map<String,String[]> phenotype=new LinkedHashMap<>();DelimitedData pheno=null;int sampleColumn=-1;
        if(!covariates.isEmpty()) {
            if(!files.containsKey("phenotype"))return finish(c,"network-analysis.yaml",summary,List.of(),false,"Network covariate residualization needs files.phenotype and sample-ID/covariate mappings");
            pheno=DelimitedData.read(c.project().resolve(text(files.get("phenotype"))));sampleColumn=pheno.column(text(columns.getOrDefault("sample_id","id")));
            for(String[] row:pheno.rows())if(row[sampleColumn].isBlank()||phenotype.putIfAbsent(row[sampleColumn],row)!=null)throw new IOException("Network phenotypes need unique nonblank independent-unit IDs");
        }
        List<String> names=new ArrayList<>();List<double[]> vectors=new ArrayList<>();Set<String> unique=new HashSet<>();
        try(var reader=source.open((int[])null)){org.jlinalg.pipeline.NumericBlock block;while((block=reader.read(256))!=null)for(var row:block.rows()) {if(!unique.add(row.id()))throw new IOException("Duplicate network feature ID");names.add(row.id());vectors.add(row.values());}}
        var keep=new ArrayList<Integer>();var exclusions=new ArrayList<String[]>();
        for(int i=0;i<samples;i++) {
            String sample=metadata.sampleIds().get(i);String reason="";
            for(double[] row:vectors)if(!Double.isFinite(row[i])){reason="missing_matrix_value_listwise";break;}
            if(reason.isEmpty()&&!covariates.isEmpty()) {
                String[] row=phenotype.get(sample);if(row==null)reason="no_covariate_record";
                else for(String key:covariates)if(missing(row[pheno.column(mapped(map(columns.get("covariates")),key))])){reason="missing_covariate";break;}
            }
            if(reason.isEmpty())keep.add(i);else exclusions.add(new String[]{sample,reason});
        }
        FollowupSupport.table(c.outputDirectory().resolve("sample-exclusions.tsv"),List.of("sample_id","reason"),exclusions);
        FollowupSupport.table(c.outputDirectory().resolve("analysis-samples.tsv"),List.of("sample_id"),keep.stream().map(i->new String[]{metadata.sampleIds().get(i)}).toList());
        summary.put("dataset_id",id);summary.put("species",dataset.getOrDefault("species","unknown"));summary.put("edge_direction","undirected");summary.put("input_samples",samples);summary.put("analysis_samples",keep.size());summary.put("missingness","listwise over the declared complete feature universe and covariates; no outcome-based selection");summary.put("preprocessing",p.get("preprocessing"));
        if(keep.size()<integer(p,"minimum_samples"))return finish(c,"network-analysis.yaml",summary,List.of("sample-exclusions.tsv"),false,"Too few complete independent units for the registered minimum_samples");
        long work=(long)features*(features-1)/2*keep.size()*(1L+integer(p,"bootstraps"));
        if(work>integer(p,"max_pair_work"))return finish(c,"network-analysis.yaml",summary,List.of("sample-exclusions.tsv"),false,"Network correlation/bootstrap work exceeds max_pair_work; register a feasible outcome-independent universe or external adapter");
        if(integer(p,"bootstraps")>0&&!Boolean.TRUE.equals(p.get("independent_samples")))return finish(c,"network-analysis.yaml",summary,List.of("sample-exclusions.tsv"),false,"Row bootstrap needs an explicit independent_samples declaration; clustered/related samples require a supported cluster-aware adapter");
        var terms=new ArrayList<String>(List.of("intercept"));var designColumns=new ArrayList<double[]>();double[] intercept=new double[keep.size()];Arrays.fill(intercept,1);designColumns.add(intercept);
        Set<String> categorical=new HashSet<>(list(p.get("categorical_covariates")).stream().map(StudyPublicEvidence::text).toList());
        for(String key:covariates) {
            String column=mapped(map(columns.get("covariates")),key);int at=pheno.column(column);
            if(categorical.contains(key)) {
                var levels=new TreeSet<String>();for(int i:keep)levels.add(phenotype.get(metadata.sampleIds().get(i))[at]);
                if(levels.size()>100)throw new IOException("Network categorical covariate has >100 levels; check independent-unit/design scope");
                var values=new ArrayList<>(levels);for(int j=1;j<values.size();j++){double[] v=new double[keep.size()];for(int k=0;k<v.length;k++)v[k]=phenotype.get(metadata.sampleIds().get(keep.get(k)))[at].equals(values.get(j))?1:0;designColumns.add(v);terms.add(key+"="+values.get(j)+" (reference "+values.get(0)+")");}
            } else {double[] v=new double[keep.size()];for(int k=0;k<v.length;k++){try{v[k]=Double.parseDouble(phenotype.get(metadata.sampleIds().get(keep.get(k)))[at]);}catch(NumberFormatException invalid){throw new IOException("Declare nonnumeric network covariate "+key+" in categorical_covariates");}if(!Double.isFinite(v[k]))throw new IOException("Invalid network covariate");}designColumns.add(v);terms.add(key);}
        }
        double[][] design=new double[keep.size()][designColumns.size()];for(int i=0;i<design.length;i++)for(int j=0;j<designColumns.size();j++)design[i][j]=designColumns.get(j)[i];
        if(keep.size()-designColumns.size()<4)return finish(c,"network-analysis.yaml",summary,List.of("sample-exclusions.tsv"),false,"Network covariate design leaves fewer than four residual degrees of freedom");
        var retainedNames=new ArrayList<String>();var responses=new ArrayList<double[]>();var featureQc=new ArrayList<String[]>();
        for(int j=0;j<names.size();j++) {
            double[] y=new double[keep.size()];for(int i=0;i<y.length;i++)y[i]=vectors.get(j)[keep.get(i)];
            double rawScale=Arrays.stream(y).map(Math::abs).max().orElse(0);
            double[] residual=Ols.fit(y,design,OlsOptions.defaults(),BackendPolicy.CPU).residuals();
            double norm=0;for(double v:residual)norm+=v*v;
            boolean variable=Double.isFinite(norm)&&norm>Math.max(Double.MIN_NORMAL,rawScale*rawScale*1e-24*y.length);
            featureQc.add(new String[]{names.get(j),variable?"included":"constant_or_fully_explained_by_covariates"});
            if(variable){retainedNames.add(names.get(j));responses.add(residual);}
        }
        FollowupSupport.table(c.outputDirectory().resolve("feature-qc.tsv"),List.of("feature_id","status"),featureQc);
        summary.put("covariate_design",terms);summary.put("residual_df",keep.size()-designColumns.size());summary.put("input_features",features);summary.put("eligible_features",retainedNames.size());summary.put("numerical_variation_rule","Retain finite residual sum of squares > max(Double.MIN_NORMAL, max_abs_input^2 * 1e-24 * N); otherwise record a constant/fully explained/numerically unresolved feature");
        if(retainedNames.size()<2)return finish(c,"network-analysis.yaml",summary,List.of("sample-exclusions.tsv","feature-qc.tsv"),false,"Fewer than two variable residual features remain");
        double[][] values=new double[keep.size()][responses.size()];for(int j=0;j<responses.size();j++)for(int i=0;i<values.length;i++)values[i][j]=responses.get(j)[i];
        double[][] rho=correlations(values,text(p.get("correlation")));double threshold=number(p,"threshold");
        var edgeRows=new ArrayList<String[]>();for(int a=0;a<retainedNames.size();a++)for(int b=a+1;b<retainedNames.size();b++)if(Math.abs(rho[a][b])>=threshold)edgeRows.add(new String[]{retainedNames.get(a),retainedNames.get(b),Double.toString(rho[a][b]),Integer.toString(keep.size()),"descriptive_correlation"});
        var nodes=retainedNames.stream().map(name->new String[]{name}).toList();
        FollowupSupport.table(c.outputDirectory().resolve("network-nodes.tsv"),List.of("node_id"),nodes);FollowupSupport.table(c.outputDirectory().resolve("network-edges.tsv"),List.of("source","target","weight","n","edge_type"),edgeRows);
        graphml(c,nodes,edgeRows);
        var sensitivity=new ArrayList<String[]>();var primary=edgeIds(rho,threshold);
        for(Object v:list(p.get("thresholds"))){double t=((Number)v).doubleValue();var edges=edgeIds(rho,t);var intersection=new HashSet<>(primary);intersection.retainAll(edges);var union=new HashSet<>(primary);union.addAll(edges);sensitivity.add(new String[]{String.valueOf(t),String.valueOf(edges.size()),String.valueOf(union.isEmpty()?1.0:(double)intersection.size()/union.size())});}
        FollowupSupport.table(c.outputDirectory().resolve("threshold-stability.tsv"),List.of("threshold","edges","edge_jaccard_vs_registered_primary"),sensitivity);
        var frequencies=new LinkedHashMap<Long,Integer>();Random random=new Random(integer(p,"seed"));
        for(int iteration=0;iteration<integer(p,"bootstraps");iteration++) {
            double[][] boot=new double[values.length][];for(int i=0;i<boot.length;i++)boot[i]=values[random.nextInt(values.length)].clone();
            double[][] bootRho=correlationsAllowConstants(boot,text(p.get("correlation")));
            for(long pair:primary){int a=(int)(pair>>>32),b=(int)pair;double r=bootRho[a][b];if(Double.isFinite(r)&&Math.abs(r)>=threshold)frequencies.merge(pair,1,Integer::sum);}
        }
        var stability=new ArrayList<String[]>();for(long pair:primary){int a=(int)(pair>>>32),b=(int)pair;stability.add(new String[]{retainedNames.get(a),retainedNames.get(b),integer(p,"bootstraps")==0?"NA":String.valueOf((double)frequencies.getOrDefault(pair,0)/integer(p,"bootstraps"))});}
        FollowupSupport.table(c.outputDirectory().resolve("edge-stability.tsv"),List.of("source","target","bootstrap_selection_frequency"),stability);
        summary.put("nodes",nodes.size());summary.put("edges",edgeRows.size());summary.put("correlation",p.get("correlation"));summary.put("threshold",threshold);summary.put("bootstrap_replicates",p.get("bootstraps"));summary.put("bootstrap_interpretation","Row bootstrap of the frozen covariate-residual matrix; degeneracy counts as nonselection. Frequencies are stability diagnostics, not edge P values or FDR.");
        summary.put("required_manuscript_caveat","No edge significance/FDR or dependence correction is inferred. Normalization/preprocessing: "+p.get("preprocessing")+". " +(integer(p,"bootstraps")==0?"Bootstrap stability was not assessed.":"Bootstrap conditions on the fitted preprocessing/covariate adjustment."));
        List<String> outputs=new ArrayList<>(List.of("sample-exclusions.tsv","analysis-samples.tsv","feature-qc.tsv","network-nodes.tsv","network-edges.tsv","network.graphml","threshold-stability.tsv","edge-stability.tsv"));
        FollowupSupport.table(c.outputDirectory().resolve("network-feature-universe.tsv"),List.of("feature_id"),retainedNames.stream().map(name->new String[]{name}).toList());outputs.add("network-feature-universe.tsv");
        outputs.addAll(StudyFeatureAnnotations.write(c,Map.of(id,c.outputDirectory().resolve("network-feature-universe.tsv"))));
        return finish(c,"network-analysis.yaml",summary,outputs,true,"Constructed a cohort-specific descriptive molecular network without outcome-based feature selection");
    }
    static double[][] correlations(double[][] values,String method) {return NetworkAnalysis.correlation(method.equals("spearman")?rankColumns(values):values);}
    private static double[][] rankColumns(double[][] input) {
        double[][] result=new double[input.length][input[0].length];
        for(int j=0;j<input[0].length;j++){final int column=j;Integer[] order=new Integer[input.length];for(int i=0;i<order.length;i++)order[i]=i;Arrays.sort(order,Comparator.comparingDouble(i->input[i][column]));
            for(int start=0;start<order.length;){int end=start+1;while(end<order.length&&input[order[end]][j]==input[order[start]][j])end++;double rank=(start+1.0+end)/2;for(int k=start;k<end;k++)result[order[k]][j]=rank;start=end;}}
        return result;
    }
    private static double[][] correlationsAllowConstants(double[][] x,String method) {
        double[][] data=method.equals("spearman")?rankColumns(x):x;int n=data.length,p=data[0].length;double[] mean=new double[p],norm=new double[p];
        for(double[] row:data)for(int j=0;j<p;j++)mean[j]+=row[j]/n;
        for(double[] row:data)for(int j=0;j<p;j++)norm[j]+=(row[j]-mean[j])*(row[j]-mean[j]);
        double[][] result=new double[p][p];for(int a=0;a<p;a++)for(int b=a+1;b<p;b++){double cross=0;for(double[] row:data)cross+=(row[a]-mean[a])*(row[b]-mean[b]);result[a][b]=result[b][a]=norm[a]>0&&norm[b]>0?Math.max(-1,Math.min(1,cross/Math.sqrt(norm[a]*norm[b]))):Double.NaN;}return result;
    }
    private static Set<Long> edgeIds(double[][] rho,double t){var result=new LinkedHashSet<Long>();for(int a=0;a<rho.length;a++)for(int b=a+1;b<rho.length;b++)if(Math.abs(rho[a][b])>=t)result.add(((long)a<<32)|b);return result;}

    private static Result pathway(Context c,Map<String,Object> p) throws Exception {
        String kind=text(p.get("source_kind"));String filename=switch(kind){case "gene_ontology"->"gene-ontology.tsv";case "pathway_enrichment"->"pathway-enrichment.tsv";default->"ranked-gsea.tsv";};
        Path source=StudyResultTables.artifact(c,kind,filename);var summary=new LinkedHashMap<String,Object>();summary.put("network_type","pathway_gene_overlap");summary.put("source_kind",kind);
        if(source==null)return finish(c,"network-analysis.yaml",summary,List.of(),false,"Pathway networks need one completed scoped "+kind+" result");
        var producer=StudyYaml.loadArtifact(source.getParent().resolve("manifest.yaml"));summary.put("required_manuscript_caveat",map(producer.get("details")).getOrDefault("required_manuscript_caveat",""));
        summary.put("source_testing_family",org.jlinalg.study.StudySuppliedResultPolicy.family(source));
        var table=DelimitedData.readResult(source);Map<String,Double> probabilities=new LinkedHashMap<>();for(String[] row:table.rows())if(!missing(row[table.column("p_value")])&&(!table.header().contains("status")||Set.of("ok","tested","complete").contains(row[table.column("status")].toLowerCase(java.util.Locale.ROOT))))probabilities.put(row[table.column(table.header().contains("enrichment_id")?"enrichment_id":"term_id")],Double.parseDouble(row[table.column("p_value")]));
        Map<String,Double> q=StudySuppliedFamilies.adjusted(source,table,probabilities,StudyCliExecutor.bh(probabilities));
        if(!Files.isRegularFile(source.getParent().resolve("supplied-analysis.yaml"))){String adjusted=table.header().contains("adjusted_p")?"adjusted_p":table.header().contains("FDR")?"FDR":null;if(adjusted!=null)for(String[] row:table.rows())if(probabilities.containsKey(row[table.column(table.header().contains("enrichment_id")?"enrichment_id":"term_id")]))q.put(row[table.column(table.header().contains("enrichment_id")?"enrichment_id":"term_id")],Double.parseDouble(row[table.column(adjusted)]));}
        Map<String,Object> nativeGsea=Files.isRegularFile(source.getParent().resolve("gsea-analysis.yaml"))?StudyYaml.loadArtifact(source.getParent().resolve("gsea-analysis.yaml")):Map.of();
        Map<String,Set<String>> membership=new LinkedHashMap<>();Path gmt=null;
        if(p.containsKey("gene_sets"))gmt=c.project().resolve(text(p.get("gene_sets")));
        else if(!p.containsKey("membership_column")&&!nativeGsea.isEmpty()&&Files.isRegularFile(source.getParent().resolve("tested-gene-sets.gmt")))gmt=source.getParent().resolve("tested-gene-sets.gmt");
        else if(!p.containsKey("membership_column")&&Files.isRegularFile(source.getParent().resolve("supplied-analysis.yaml"))){var imported=StudyYaml.loadArtifact(source.getParent().resolve("supplied-analysis.yaml"));var definition=map(map(imported.get("source_files")).get("gene_sets"));if(definition.containsKey("snapshot"))gmt=source.getParent().resolve(text(definition.get("snapshot")));}
        if(gmt!=null&&Files.isRegularFile(gmt))for(String line:Files.readAllLines(gmt)){if(line.isBlank())continue;String[] fields=line.split("\t",-1);if(fields.length<3||membership.putIfAbsent(fields[0],new LinkedHashSet<>(Arrays.asList(fields).subList(2,fields.length)))!=null)throw new IOException("Invalid/duplicate pathway GMT identity");}
        else if(p.containsKey("membership_column")) {
            int at=table.column(text(p.get("membership_column")));for(String[] row:table.rows())membership.put(row[table.column(table.header().contains("enrichment_id")?"enrichment_id":"term_id")],new LinkedHashSet<>(Arrays.stream(row[at].split("[;,|]",-1)).filter(s->!s.isBlank()).toList()));
            summary.put("required_manuscript_caveat",summary.getOrDefault("required_manuscript_caveat","")+" Pathway membership uses the explicitly declared table column "+p.get("membership_column")+"; leading-edge/selected-hit overlap is conditional and differs from full gene-set overlap.");
        } else return finish(c,"network-analysis.yaml",summary,List.of(),false,"Supply gene_sets GMT or explicitly declare membership_column; term counts cannot reconstruct memberships");
        var selected=q.entrySet().stream().filter(e->e.getValue()<number(p,"term_alpha")).map(Map.Entry::getKey).sorted().toList();
        if(selected.stream().anyMatch(id->!membership.containsKey(id)||membership.get(id).isEmpty()))return finish(c,"network-analysis.yaml",summary,List.of(),false,"Every selected pathway needs its exact declared gene membership; absent sets are not empty/null pathways");
        if(selected.size()>integer(p,"max_features"))return finish(c,"network-analysis.yaml",summary,List.of(),false,"Selected pathway network exceeds max_features; no top-term truncation was applied");
        var nodes=selected.stream().map(id->new String[]{id}).toList();var edges=new ArrayList<String[]>();var genes=new ArrayList<String[]>();
        for(String id:selected)for(String gene:new TreeSet<>(membership.get(id)))genes.add(new String[]{id,gene});
        for(int a=0;a<selected.size();a++)for(int b=a+1;b<selected.size();b++){var intersection=new HashSet<>(membership.get(selected.get(a)));intersection.retainAll(membership.get(selected.get(b)));var union=new HashSet<>(membership.get(selected.get(a)));union.addAll(membership.get(selected.get(b)));double jaccard=(double)intersection.size()/union.size();if(jaccard>=number(p,"pathway_threshold"))edges.add(new String[]{selected.get(a),selected.get(b),String.valueOf(jaccard),"gene_set_jaccard"});}
        FollowupSupport.table(c.outputDirectory().resolve("network-nodes.tsv"),List.of("node_id"),nodes);FollowupSupport.table(c.outputDirectory().resolve("network-edges.tsv"),List.of("source","target","weight","edge_type"),edges);FollowupSupport.table(c.outputDirectory().resolve("pathway-genes.tsv"),List.of("node_id","gene_id"),genes);
        summary.put("membership_gene_id_type","unknown".equals(p.get("membership_gene_id_type"))?nativeGsea.getOrDefault("gene_id_type","unknown"):p.get("membership_gene_id_type"));
        if(nativeGsea.containsKey("dataset_id"))summary.put("dataset_id",nativeGsea.get("dataset_id"));
        graphml(c,nodes,edges);
        summary.put("nodes",nodes.size());summary.put("edges",edges.size());summary.put("edge_direction","undirected");summary.put("species",!nativeGsea.isEmpty()?nativeGsea.getOrDefault("species","unknown"):Files.isRegularFile(source.getParent().resolve("supplied-analysis.yaml"))?map(StudyYaml.loadArtifact(source.getParent().resolve("supplied-analysis.yaml")).get("scope")).getOrDefault("species","unknown"):"human");summary.put("membership_basis",p.containsKey("membership_column")&&gmt==null?"explicit_table_column":"full_gene_sets");summary.put("term_alpha",p.get("term_alpha"));summary.put("threshold",p.get("pathway_threshold"));summary.put("source_sha256",FollowupSupport.hash(source));
        return finish(c,"network-analysis.yaml",summary,List.of("network-nodes.tsv","network-edges.tsv","pathway-genes.tsv","network.graphml"),true,"Constructed an enrichment-overlap graph; no molecular correlation or new GSEA was fitted");
    }

    private record Graph(List<String> ids,List<BitSet> adjacency,Path directory,Map<String,Object> summary,boolean bounded) { }
    private static void graphml(Context c,List<String[]> nodes,List<String[]> edges) throws Exception {
        var index=new LinkedHashMap<String,Integer>();for(String[] node:nodes)index.put(node[0],index.size());
        try(var stream=Files.newOutputStream(c.outputDirectory().resolve("network.graphml"))){var xml=javax.xml.stream.XMLOutputFactory.newFactory().createXMLStreamWriter(stream,"UTF-8");
            xml.writeStartDocument("UTF-8","1.0");xml.writeStartElement("graphml");xml.writeDefaultNamespace("http://graphml.graphdrawing.org/xmlns");
            for(String key:List.of("label","weight")){xml.writeEmptyElement("key");xml.writeAttribute("id",key);xml.writeAttribute("for",key.equals("label")?"node":"edge");xml.writeAttribute("attr.name",key);xml.writeAttribute("attr.type",key.equals("label")?"string":"double");}
            xml.writeStartElement("graph");xml.writeAttribute("edgedefault","undirected");
            for(var entry:index.entrySet()){xml.writeStartElement("node");xml.writeAttribute("id","n"+entry.getValue());xml.writeStartElement("data");xml.writeAttribute("key","label");xml.writeCharacters(entry.getKey());xml.writeEndElement();xml.writeEndElement();}
            int count=0;for(String[] edge:edges){xml.writeStartElement("edge");xml.writeAttribute("id","e"+count++);xml.writeAttribute("source","n"+index.get(edge[0]));xml.writeAttribute("target","n"+index.get(edge[1]));xml.writeStartElement("data");xml.writeAttribute("key","weight");xml.writeCharacters(edge[2]);xml.writeEndElement();xml.writeEndElement();}
            xml.writeEndElement();xml.writeEndElement();xml.writeEndDocument();xml.close();
        }
    }
    private static Graph graph(Context c,Map<String,Object> policy) throws IOException {
        var matches=new ArrayList<Path>();for(Path dir:c.dependencyDirectories().values())if(Files.isRegularFile(dir.resolve("network-nodes.tsv"))&&Files.isRegularFile(dir.resolve("network-edges.tsv")))matches.add(dir);
        if(matches.size()!=1)throw new IOException("Require exactly one scoped source network per module operation");Path dir=matches.get(0);
        verifiedDirectory(dir);var nodes=DelimitedData.readResult(dir.resolve("network-nodes.tsv"));var edges=DelimitedData.readResult(dir.resolve("network-edges.tsv"));
        List<String> ids=nodes.rows().stream().map(row->row[nodes.column("node_id")]).toList();if(ids.size()>100000||new HashSet<>(ids).size()!=ids.size())throw new IOException("Duplicate nodes or network exceeds 100000-node bound");
        boolean suppliedModules=Files.isRegularFile(dir.resolve("network-modules.tsv"));
        if(!suppliedModules&&ids.size()>integer(policy,"max_features"))return new Graph(ids,List.of(),dir,Map.of(),true);
        var index=new HashMap<String,Integer>();var adjacency=new ArrayList<BitSet>();for(int i=0;i<ids.size();i++){index.put(ids.get(i),i);adjacency.add(new BitSet());}
        if(edges.rows().size()>1000000)throw new IOException("Network exceeds one million edges; supply a scalable module adapter");
        var pairs=new HashSet<Long>();for(String[] row:edges.rows()){Integer a=index.get(row[edges.column("source")]),b=index.get(row[edges.column("target")]);if(a==null||b==null||a.equals(b))throw new IOException("Invalid network edge endpoint/self loop");long pair=((long)Math.min(a,b)<<32)|Math.max(a,b);if(!pairs.add(pair))throw new IOException("Duplicate/reversed network edge; register a simple undirected evidence layer");if(!suppliedModules){adjacency.get(a).set(b);adjacency.get(b).set(a);}}
        Map<String,Object> summary=Files.isRegularFile(dir.resolve("network-analysis.yaml"))?StudyYaml.loadArtifact(dir.resolve("network-analysis.yaml")):Map.of();
        var imported=Files.isRegularFile(dir.resolve("supplied-analysis.yaml"))?StudyYaml.loadArtifact(dir.resolve("supplied-analysis.yaml")):Map.of();
        return new Graph(ids,adjacency,dir,summary,false);
    }
    private static void verifiedDirectory(Path directory) throws IOException {
        Path manifest=directory.resolve("manifest.yaml");if(!Files.isRegularFile(manifest))throw new IOException("Missing sealed network producer manifest");
        var doc=StudyYaml.loadArtifact(manifest);for(var output:map(doc.get("output_sha256")).entrySet()){Path file=directory.resolve(output.getKey()).normalize();if(!file.startsWith(directory.normalize())||!Files.isRegularFile(file)||!FollowupSupport.hash(file).equals(output.getValue()))throw new IOException("Network dependency integrity failure: "+output.getKey());}
    }
    private static Result modules(Context c,Map<String,Object> p) throws Exception {
        for(Path dir:c.dependencyDirectories().values())if(Files.isRegularFile(dir.resolve("supplied-analysis.yaml"))){var imported=StudyYaml.loadArtifact(dir.resolve("supplied-analysis.yaml"));if(!"undirected".equals(map(imported.get("method")).get("edge_direction")))return finish(c,"module-analysis.yaml",new LinkedHashMap<>(),List.of(),false,"Declare supplied method.edge_direction: undirected before undirected modules/cliques; directed graphs require a separately registered projection");}
        var g=graph(c,p);var summary=new LinkedHashMap<String,Object>();summary.put("source_network_sha256",FollowupSupport.hash(g.directory.resolve("manifest.yaml")));summary.put("source_network_output_sha256",map(StudyYaml.loadArtifact(g.directory.resolve("manifest.yaml")).get("output_sha256")));summary.put("nodes",g.ids.size());summary.put("source_network_type",g.summary.getOrDefault("network_type","externally_supplied"));
        if(g.bounded)return finish(c,"module-analysis.yaml",summary,List.of(),false,"Native topology exceeds max_features; supply prespecified module memberships or a scalable module adapter instead of truncating nodes");
        summary.put("required_manuscript_caveat",g.summary.getOrDefault("required_manuscript_caveat",""));
        for(String field:List.of("source_kind","dataset_id"))if(g.summary.containsKey(field))summary.put(field,g.summary.get(field));
        for(String field:List.of("execution_origin","reported_status","evidence_grade"))if(g.summary.containsKey(field))summary.put("source_"+field,g.summary.get(field));
        summary.put("species",g.summary.getOrDefault("species",map(g.summary.get("scope")).getOrDefault("species","unknown")));
        if(g.summary.containsKey("membership_gene_id_type"))summary.put("membership_gene_id_type",g.summary.get("membership_gene_id_type"));
        var rows=new ArrayList<String[]>();Path supplied=g.directory.resolve("network-modules.tsv");boolean complete=true;
        if(Files.isRegularFile(supplied)) {
            var table=DelimitedData.readResult(supplied);var identities=new HashSet<String>();var known=new HashSet<>(g.ids);for(String[] row:table.rows()){String node=row[table.column("node_id")],module=row[table.column("module_id")];if(!known.contains(node)||module.isBlank()||!identities.add(module+"\u001f"+node))throw new IOException("Invalid supplied module membership");rows.add(new String[]{node,"supplied:"+module,"supplied_module"});}
            summary.put("module_origin","supplied_memberships_reused");summary.put("clique_enumeration","not_requested_for_supplied_modules");
        } else {
            int component=0;for(var members:NetworkModules.components(g.adjacency)){String module="component-"+(++component);for(int member:members)rows.add(new String[]{g.ids.get(member),module,"connected_component"});}
            summary.put("connected_components",component);
            if(Boolean.TRUE.equals(p.get("cliques"))){var result=g.ids.size()>integer(p,"max_features")?new NetworkModules.Cliques(List.of(),false,"max_features",0):NetworkModules.maximalCliques(g.adjacency,integer(p,"minimum_clique_size"),integer(p,"max_cliques"),integer(p,"max_search_states"),integer(p,"max_clique_depth"));int clique=0;for(var members:result.members()){String module="clique-"+(++clique);for(int member:members)rows.add(new String[]{g.ids.get(member),module,"maximal_clique"});}complete=result.complete();summary.put("maximal_cliques",clique);summary.put("clique_search_complete",complete);summary.put("search_limit",result.limit());summary.put("search_states",result.searchStates());}
            summary.put("module_origin","native_connected_components_and_optional_maximal_cliques");
        }
        FollowupSupport.table(c.outputDirectory().resolve("network-modules.tsv"),List.of("node_id","module_id","module_type"),rows);
        Files.copy(g.directory.resolve("network-nodes.tsv"),c.outputDirectory().resolve("network-nodes.tsv"));List<String> outputs=new ArrayList<>(List.of("network-modules.tsv","network-nodes.tsv"));
        for(String file:List.of("feature-annotation.tsv","pathway-genes.tsv"))if(Files.isRegularFile(g.directory.resolve(file))){Files.copy(g.directory.resolve(file),c.outputDirectory().resolve(file));outputs.add(file);}
        summary.put("memberships",rows.size());summary.put("modules",rows.stream().map(row->row[1]).distinct().count());summary.put("functional_labels","Not assigned by topology; consult module GO and its limitations");
        return finish(c,"module-analysis.yaml",summary,outputs,complete,complete?"Retained connected components, overlapping maximal cliques or supplied module memberships":"Clique enumeration reached a registered bound; retained partial memberships, and joint module GO must wait for a complete declared family");
    }
    private static Result go(Context c,Map<String,Object> p) throws Exception {
        var sources=c.dependencyDirectories().values().stream().filter(dir->Files.isRegularFile(dir.resolve("module-analysis.yaml"))).toList();var summary=new LinkedHashMap<String,Object>();
        if(sources.size()!=1)return finish(c,"module-go.yaml",summary,List.of(),false,"Module GO needs exactly one scoped module analysis");Path dir=sources.get(0);verifiedDirectory(dir);var modules=StudyYaml.loadArtifact(dir.resolve("module-analysis.yaml"));
        summary.put("source_modules_sha256",FollowupSupport.hash(dir.resolve("manifest.yaml")));summary.put("required_manuscript_caveat",text(modules.get("required_manuscript_caveat")));
        if(Boolean.TRUE.equals(modules.get("multiple_network_scopes")))return finish(c,"module-go.yaml",summary,List.of(),false,"Supplied memberships span multiple network namespaces; register separate scoped module GO jobs or completed supplied module enrichment rather than inferring a pooled background");
        if(Boolean.FALSE.equals(modules.get("clique_search_complete")))return finish(c,"module-go.yaml",summary,List.of(),false,"Incomplete clique enumeration cannot define a complete module-by-term testing family; revise the registered bounds or supply a complete prespecified membership table");
        if(!Files.isRegularFile(dir.resolve("network-nodes.tsv")))return finish(c,"module-go.yaml",summary,List.of(),false,"Supply the complete node universe for these completed memberships; background is not inferred from module members or selected hits");
        var nodes=DelimitedData.readResult(dir.resolve("network-nodes.tsv"));Set<String> ids=new LinkedHashSet<>();for(String[] row:nodes.rows())ids.add(row[nodes.column("node_id")]);
        if(ids.isEmpty()){
            summary.put("background_genes",0);summary.put("tested_module_term_pairs",0);summary.put("analysis_performed",false);summary.put("disposition","no_eligible_network_nodes");
            FollowupSupport.table(c.outputDirectory().resolve("module-gene-ontology.tsv"),GO_COLUMNS,List.of());
            return finish(c,"module-go.yaml",summary,List.of("module-gene-ontology.tsv"),true,"No eligible network nodes remain; module GO was not tested, and no missing-mapping or biological null inference is made");
        }
        var mapping=new LinkedHashMap<String,Set<String>>();
        if(Files.isRegularFile(dir.resolve("pathway-genes.tsv"))){
            if(!"symbol".equals(modules.get("membership_gene_id_type")))return finish(c,"module-go.yaml",summary,List.of(),false,"Declare and map pathway membership_gene_id_type to symbol before using this GO adapter; arbitrary IDs are not inferred to be gene symbols");
            var table=DelimitedData.readResult(dir.resolve("pathway-genes.tsv"));for(String[] row:table.rows())mapping.computeIfAbsent(row[table.column("node_id")],k->new TreeSet<>()).add(row[table.column("gene_id")]);}
        else {var mapped=new StudyCliExecutor().geneMappings(c,ids);for(var entry:mapped.entrySet())if(!entry.getValue().symbol().isBlank())mapping.put(entry.getKey(),Set.of(entry.getValue().symbol()));}
        Set<String> universe=new TreeSet<>();mapping.values().forEach(universe::addAll);summary.put("network_nodes",ids.size());summary.put("mapped_nodes",mapping.size());summary.put("unmapped_nodes",ids.size()-mapping.size());summary.put("background_genes",universe.size());summary.put("background_policy","All uniquely mapped eligible network nodes, including isolates and genes absent from the GO collection; gene IDs deduplicated");
        var mappingRows=new ArrayList<String[]>();for(String id:ids)mappingRows.add(new String[]{id,String.join(";",mapping.getOrDefault(id,Set.of())),mapping.containsKey(id)?"mapped":"unmapped"});FollowupSupport.table(c.outputDirectory().resolve("node-to-gene.tsv"),List.of("node_id","gene_symbol","status"),mappingRows);
        Files.writeString(c.outputDirectory().resolve("background-genes.txt"),String.join("\n",universe)+(universe.isEmpty()?"":"\n"));var outputs=new ArrayList<String>(List.of("node-to-gene.tsv","background-genes.txt"));
        if(universe.isEmpty())return finish(c,"module-go.yaml",summary,outputs,false,"Supply compatible node-to-gene annotations; gene symbols are not inferred from arbitrary node labels");
        Path database=p.containsKey("go_database")?c.project().resolve(text(p.get("go_database"))):StudyResourceManager.resourceRoot(c.project(),c.study()).resolve("enrichment/go-human");
        String species=text(modules.get("species"));
        if(species.isBlank()||species.equals("unknown"))return finish(c,"module-go.yaml",summary,outputs,false,"Declare the source network species before selecting a species-matched GO resource");
        if(!p.containsKey("go_database")&&!Set.of("human","Homo sapiens","9606").contains(species))return finish(c,"module-go.yaml",summary,outputs,false,"Supply a verified species-matched network.go_database; the default GO resource is human");
        write(c.outputDirectory().resolve("resource-acquisition-intent.yaml"),Map.of("collection","GO","species",species,"namespace","symbol","resource_directory",database.toString(),"request_needed",!Files.isRegularFile(database.resolve("manifest.properties")),"rationale","Joint module-by-term ORA against all mapped network genes; inbound annotation only, no participant or feature identifiers submitted"));
        if(!Files.isRegularFile(database.resolve("manifest.properties"))){
            if(!StudyResourceManager.automatic(c.study())||!Set.of("human","Homo sapiens","9606").contains(species))return finish(c,"module-go.yaml",summary,outputs,false,"Module GO needs a verified species-matched GO resource; automatic acquisition is disabled or unsupported for this species");
            Files.createDirectories(database.getParent());var log=new java.io.ByteArrayOutputStream();int status=EnrichmentCli.run(new String[]{"--enrichment","GO","--download",database.toString(),"--species","human","--gene-id-type","symbol"},new java.io.PrintStream(log),new java.io.PrintStream(log));
            Files.writeString(c.outputDirectory().resolve("resource-download.log"),log.toString(java.nio.charset.StandardCharsets.UTF_8));
            if(status!=0)return finish(c,"module-go.yaml",summary,outputs,false,"GO resource acquisition failed; inspect resource-download.log and supply a verified resource or retry when available");
        }
        var db=EnrichmentDatabase.load(database,"GO",Map.of("--gene-id-type","symbol"));Files.copy(database.resolve("manifest.properties"),c.outputDirectory().resolve("go-resource-manifest.properties"));outputs.add("go-resource-manifest.properties");
        if(db.metadata.containsKey("species")&&!db.metadata.getProperty("species").equalsIgnoreCase(species)&&!(Set.of("human","Homo sapiens","9606").contains(species)&&Set.of("human","Homo sapiens","9606").contains(db.metadata.getProperty("species"))))throw new IOException("Network and GO resource species differ");
        var membership=DelimitedData.readResult(dir.resolve("network-modules.tsv"));var sets=new LinkedHashMap<String,Set<String>>();var types=new LinkedHashMap<String,String>();var names=new LinkedHashMap<String,String>();
        for(String[] row:membership.rows()){String label=row[membership.column("module_id")],type=row[membership.column("module_type")],id=StudyAssistance.json(List.of(label,type));sets.computeIfAbsent(id,k->new TreeSet<>()).addAll(mapping.getOrDefault(row[membership.column("node_id")],Set.of()));types.put(id,type);names.put(id,label);}
        long eligibleTerms=db.genes.values().stream().filter(genes->{long size=genes.stream().filter(universe::contains).count();return size>=integer(p,"go_min_size")&&size<=integer(p,"go_max_size");}).count();
        long eligibleModules=sets.values().stream().filter(genes->genes.size()>=integer(p,"go_min_module_genes")).count();
        if(eligibleTerms*eligibleModules>integer(p,"max_go_tests"))return finish(c,"module-go.yaml",summary,outputs,false,"Complete module-by-term family exceeds max_go_tests; register an outcome-independent module/term universe or a scalable external analysis, without selecting best terms");
        var tests=new ArrayList<EnrichmentResult>();var testedModules=new ArrayList<String>();var dispositions=new ArrayList<String[]>();
        for(var entry:sets.entrySet()) {if(entry.getValue().size()<integer(p,"go_min_module_genes")){dispositions.add(new String[]{names.get(entry.getKey()),types.get(entry.getKey()),entry.getValue().isEmpty()?"no_mapped_genes":"below_go_min_module_genes","0"});continue;}var result=EnrichmentAnalysis.ora(universe,entry.getValue(),db.sets(),integer(p,"go_min_size"),integer(p,"go_max_size"),EnrichmentAnalysis.Fdr.NONE);for(var term:result){tests.add(term);testedModules.add(entry.getKey());}dispositions.add(new String[]{names.get(entry.getKey()),types.get(entry.getKey()),result.isEmpty()?"no_eligible_GO_terms":"tested",String.valueOf(result.size())});}
        double[] adjusted=EnrichmentAnalysis.adjustLog(tests.stream().mapToDouble(EnrichmentResult::logPValue).toArray(),EnrichmentAnalysis.Fdr.valueOf(text(p.get("go_adjustment"))));var rows=new ArrayList<String[]>();
        for(int i=0;i<tests.size();i++){var r=tests.get(i);String module=testedModules.get(i);rows.add(new String[]{names.get(module),types.get(module),r.id(),r.name(),String.valueOf(r.universeSize()),String.valueOf(r.selectedSize()),String.valueOf(r.setSize()),String.valueOf(r.overlap()),String.valueOf(r.pValue()),String.valueOf(r.logPValue()),String.valueOf(Math.exp(adjusted[i])),String.valueOf(adjusted[i]),String.join(";",r.hitGenes())});}
        FollowupSupport.table(c.outputDirectory().resolve("module-gene-ontology.tsv"),GO_COLUMNS,rows);
        FollowupSupport.table(c.outputDirectory().resolve("module-dispositions.tsv"),List.of("module_id","module_type","status","tested_terms"),dispositions);outputs.addAll(List.of("module-gene-ontology.tsv","module-dispositions.tsv"));
        summary.put("tested_module_term_pairs",tests.size());summary.put("testing_family","All eligible terms for every module of this scoped graph, including components and cliques; no best-term/module filtering");summary.put("adjustment",p.get("go_adjustment"));summary.put("resource",new LinkedHashMap<>(db.metadata));summary.put("required_manuscript_caveat",summary.get("required_manuscript_caveat")+" Joint module-term ORA conditions on the observed graph and memberships; it does not account for their estimation, outcome-selected universes or feature-to-gene sampling biases.");
        summary.put("analysis_performed",!tests.isEmpty());
        return finish(c,"module-go.yaml",summary,outputs,true,tests.isEmpty()?"No eligible module-term tests remained; GO was not tested":"Completed module GO over the mapped network universe with joint module-by-term multiplicity control");
    }
}
