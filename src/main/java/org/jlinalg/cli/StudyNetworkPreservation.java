/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyNetworkPreservationPolicy;
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.study.StudyOperationExecutor.*;

/** Transfer frozen reference memberships to independently retained target graphs. */
final class StudyNetworkPreservation implements StudyOperationExecutor {
    static final String CAVEAT="These are descriptive comparisons of complete declared thresholded signed-correlation graphs, conditional on source preprocessing, node selection, thresholds and reference memberships. An absent thresholded edge is not an observed zero raw correlation. Node coverage, edge retention, Jaccard and sign agreement are not preservation P values, independent replication, WGCNA Zsummary, biological function or causal validation. Graphs may share participants or cohorts; declared scopes and namespaces do not certify independence or biological identity. No threshold, module, primary source or association FDR is selected by these diagnostics.";
    static final List<String> COLUMNS=List.of("reference_graph","target_graph","module_id","module_type","reference_nodes","shared_nodes","node_coverage","possible_pairs","reference_edges","target_edges","shared_edges","edge_retention","edge_jaccard","signed_agreement","thresholded_weight_cosine","disposition");
    static final List<String> MEMBERSHIPS=List.of("reference_graph","module_id","module_type","node_id");
    static final Set<String> DISPOSITIONS=Set.of("assessed","assessed_no_reference_edges","below_node_coverage","below_minimum_nodes","held_source_context");
    private record Edge(String a,String b){static Edge of(String a,String b){return a.compareTo(b)<0?new Edge(a,b):new Edge(b,a);}}
    private record Module(String id,String type,Set<String> nodes) { }
    private record Graph(String alias,Path directory,String manifestHash,Map<String,Object> metadata,Set<String> nodes,Map<Edge,Double> edges,List<Module> modules,Map<String,Object> descriptor) { }
    @Override public Result execute(Context c)throws Exception {
        Files.createDirectories(c.outputDirectory());var p=StudyNetworkPreservationPolicy.resolve(map(map(StudyBioinformatics.hypothesis(c).get("analysis")).get("network_preservation")));
        var summary=new LinkedHashMap<String,Object>();summary.put("effective_policy",p);summary.put("required_manuscript_caveat",CAVEAT);
        var outputs=new ArrayList<String>();var needs=new LinkedHashSet<String>();var sources=new ArrayList<Object>();var graphs=new LinkedHashMap<String,Graph>();var rows=new ArrayList<String[]>();var membershipRows=new ArrayList<String[]>();var contexts=new ArrayList<String[]>();
        var graphDirectories=new ArrayList<Path>();var moduleDirectories=new LinkedHashMap<String,List<Path>>();
        for(Path dir:new LinkedHashSet<>(c.dependencyDirectories().values())){
            var m=manifest(dir);String type=text(m.get("operation_type"));
            if(Set.of("network_modules","network_analysis").contains(type)&&!text(c.operation().get("hypothesis")).equals(text(m.get("hypothesis"))))throw new IOException("Network preservation cannot borrow another hypothesis's graph/module producer");
            if(type.equals("network_modules")){var details=map(m.get("details"));var sourceOutputs=map(details.get("source_network_output_sha256"));String key=sourceOutputs.isEmpty()?"manifest:"+text(details.get("source_network_sha256")):contentKey(sourceOutputs);moduleDirectories.computeIfAbsent(key,ignored->new ArrayList<>()).add(dir);}
            else if(type.equals("network_analysis")){
                if(!"complete".equals(m.get("state"))&&List.of("network-nodes.tsv","network-edges.tsv","network-analysis.yaml").stream().anyMatch(name->!Files.isRegularFile(dir.resolve(name)))){
                    needs.add("An incomplete source graph has no usable complete export");sources.add(Map.of("source_manifest_sha256",FollowupSupport.hash(dir.resolve("manifest.yaml")),"disposition","unavailable_incomplete_graph"));
                }else graphDirectories.add(dir);
            }
        }
        if(graphDirectories.size()>integer(p,"max_graphs"))needs.add("Graph count exceeds max_graphs; no preferred graph subset was selected");
        else {
            int index=0;
            for(Path dir:graphDirectories){
                try{Graph g=readGraph(c,dir,moduleDirectories,p,++index,outputs);if(graphs.putIfAbsent(g.alias,g)!=null)throw new IOException("Duplicate source graph alias");sources.add(g.descriptor);}
                catch(Bound exceeded){needs.add(exceeded.getMessage());sources.add(Map.of("source_manifest_sha256",FollowupSupport.hash(dir.resolve("manifest.yaml")),"disposition","source_bound_exceeded"));}
            }
        }
        Set<String> references=new LinkedHashSet<>(list(p.get("references")).stream().map(StudyPublicEvidence::text).toList());if(references.isEmpty())references.addAll(graphs.keySet());
        for(String reference:references)if(!graphs.containsKey(reference))needs.add("Requested reference graph is unavailable: "+reference);
        for(String alias:map(p.get("graph_namespaces")).keySet())if(!graphs.containsKey(alias))needs.add("A graph namespace names an unavailable graph: "+alias);
        if(graphs.size()<2)needs.add("Preservation needs at least two separately retained molecular source graphs; pooled graphs are not decomposed into cohorts");
        long comparisons=0,work=0;
        for(String reference:references){Graph g=graphs.get(reference);if(g==null)continue;comparisons+=(long)g.modules.size()*Math.max(0,graphs.size()-1);for(Module module:g.modules)work+=(long)module.nodes.size()*(module.nodes.size()-1)/2*Math.max(0,graphs.size()-1);}
        boolean bounded=comparisons>integer(p,"max_comparisons")||work>integer(p,"max_pair_work");
        if(bounded)needs.add("Complete reference-module/target work exceeds max_comparisons or max_pair_work; no best module/comparison prefix was selected");
        int assessed=0;
        if(!bounded)for(String reference:references){
            Graph g=graphs.get(reference);if(g==null)continue;
            if(g.modules.isEmpty()&&!"complete".equals(g.descriptor.get("module_producer_state")))needs.add("No complete reference memberships are available for "+reference);
            for(Module module:g.modules)for(String node:module.nodes)membershipRows.add(new String[]{reference,module.id,module.type,node});
            for(Graph target:graphs.values())if(!target.alias.equals(reference)){
                List<String> problems=compatibility(g,target,p);String problem=String.join(";",problems);
                contexts.add(new String[]{g.alias,target.alias,problems.isEmpty()?"declared_compatible_not_certified":"held",problem,text(g.descriptor.get("cohort_overlap_disposition")),String.join(";",cohorts(g.descriptor,target.descriptor)),g.manifestHash,target.manifestHash});
                if(!problems.isEmpty())needs.add("Review graph comparability for "+g.alias+" -> "+target.alias+": "+problem);
                for(Module module:g.modules){String[] row=compare(g,target,module,p,problems.isEmpty());rows.add(row);if(row[COLUMNS.indexOf("disposition")].startsWith("assessed"))assessed++;}
            }
        }
        FollowupSupport.table(c.outputDirectory().resolve("network-preservation.tsv"),COLUMNS,rows);outputs.add("network-preservation.tsv");
        FollowupSupport.table(c.outputDirectory().resolve("network-preservation-memberships.tsv"),MEMBERSHIPS,membershipRows);outputs.add("network-preservation-memberships.tsv");
        FollowupSupport.table(c.outputDirectory().resolve("graph-comparability.tsv"),List.of("reference_graph","target_graph","disposition","reasons","independence","shared_registered_cohorts","reference_manifest_sha256","target_manifest_sha256"),contexts);outputs.add("graph-comparability.tsv");
        summary.put("sources",sources);summary.put("graphs",graphs.size());summary.put("reference_graphs",List.copyOf(references));summary.put("planned_comparisons",comparisons);summary.put("planned_pair_work_upper_bound",work);summary.put("comparisons",rows.size());summary.put("assessed_comparisons",assessed);summary.put("analysis_performed",assessed>0);
        summary.put("method_report",Map.of("operation","network_preservation","performed",assessed>0,"parameters",p,"interpretation",CAVEAT));
        summary.put("needs",needs.stream().map(need->StudyNeed.distinctReview("network-preservation:"+c.operation().get("hypothesis"),"What is needed for network preservation?",need,true).document()).toList());
        write(c.outputDirectory().resolve("network-preservation.yaml"),summary);outputs.add("network-preservation.yaml");
        return new Result(needs.isEmpty()?State.COMPLETE:State.WAITING_FOR_INPUT,needs.isEmpty()?"Compared frozen reference modules across source graphs":"Retained available graph preservation diagnostics; unresolved source scope/bounds need review",outputs,summary);
    }
    private static int integer(Map<String,Object> p,String key){return ((Number)p.get(key)).intValue();}
    private static String contentKey(Map<String,Object> outputs){return "outputs:"+StudyAssistance.json(new java.util.TreeMap<>(outputs));}
    private static final class Bound extends IOException {private static final long serialVersionUID=1L;Bound(String message){super(message);}}
    private static void bound(Path file,int maximum)throws IOException{try(var lines=Files.lines(file)){if(lines.limit((long)maximum+2).count()>maximum+1L)throw new Bound("Source graph/membership table exceeds its registered row bound: "+file.getFileName());}}
    private static Map<String,Object> manifest(Path dir)throws IOException{
        Path receipt=dir.resolve("manifest.yaml");if(!Files.isRegularFile(receipt))throw new IOException("Missing graph producer receipt");var m=StudyYaml.loadArtifact(receipt);Path real=dir.toRealPath();
        for(var entry:map(m.get("output_sha256")).entrySet()){Path file=dir.resolve(entry.getKey()).normalize();if(!Files.isRegularFile(file)||!file.toRealPath().startsWith(real)||!FollowupSupport.hash(file).equals(entry.getValue()))throw new IOException("Graph/module dependency integrity failure: "+entry.getKey());}
        return m;
    }
    private static void required(Path dir,Map<String,Object> manifest,String name)throws IOException{if(!map(manifest.get("output_sha256")).containsKey(name)||!Files.isRegularFile(dir.resolve(name)))throw new IOException("Required graph/module table is not sealed: "+name);}
    private static String snapshot(Context c,Path directory,String name,String prefix,List<String> outputs)throws IOException{String destination=prefix+"-"+name;Files.copy(directory.resolve(name),c.outputDirectory().resolve(destination));outputs.add(destination);return destination;}
    private static Graph readGraph(Context c,Path dir,Map<String,List<Path>> moduleDirs,Map<String,Object> p,int index,List<String> outputs)throws IOException{
        var producer=manifest(dir);for(String name:List.of("network-nodes.tsv","network-edges.tsv","network-analysis.yaml"))required(dir,producer,name);
        var metadata=StudyYaml.loadArtifact(dir.resolve("network-analysis.yaml"));var scope=map(metadata.get("scope"));boolean supplied="externally_produced".equals(metadata.get("execution_origin"));
        String alias=(supplied?"supplied:":"dataset:")+text(metadata.get(supplied?"artifact_id":"dataset_id"));if(alias.endsWith(":"))throw new IOException("Graph has no stable dataset/supplied alias");
        String prefix=String.format(java.util.Locale.ROOT,"graph-%03d",index);String hash=FollowupSupport.hash(dir.resolve("manifest.yaml"));
        var descriptor=new LinkedHashMap<String,Object>();descriptor.put("graph_alias",alias);descriptor.put("source_manifest_sha256",hash);descriptor.put("producer_state",producer.get("state"));descriptor.put("execution_origin",supplied?"externally_produced":"jlinalg");descriptor.put("cohort_overlap_disposition","not_independently_certified");
        var snapshots=new LinkedHashMap<String,Object>();for(String name:List.of("network-nodes.tsv","network-edges.tsv","network-analysis.yaml","manifest.yaml"))snapshots.put(name,snapshot(c,dir,name,prefix,outputs));
        if(Files.isRegularFile(dir.resolve("supplied-analysis.yaml"))){required(dir,producer,"supplied-analysis.yaml");snapshots.put("supplied-analysis.yaml",snapshot(c,dir,"supplied-analysis.yaml",prefix,outputs));}descriptor.put("snapshots",snapshots);
        bound(dir.resolve("network-nodes.tsv"),integer(p,"max_nodes"));bound(dir.resolve("network-edges.tsv"),integer(p,"max_edges"));
        var nodesTable=DelimitedData.readResult(dir.resolve("network-nodes.tsv"));var nodes=new LinkedHashSet<String>();for(String[] row:nodesTable.rows()){String node=row[nodesTable.column("node_id")];if(node.isBlank()||!nodes.add(node))throw new IOException("Duplicate/blank graph node");}
        var edgesTable=DelimitedData.readResult(dir.resolve("network-edges.tsv"));var edges=new LinkedHashMap<Edge,Double>();boolean weightsAvailable=true;
        for(String[] row:edgesTable.rows()){String a=row[edgesTable.column("source")],b=row[edgesTable.column("target")];String raw=edgesTable.header().contains("weight")?row[edgesTable.column("weight")]:"NA";double weight=Set.of("","NA","NaN",".").contains(raw)?Double.NaN:Double.parseDouble(raw);if(!nodes.contains(a)||!nodes.contains(b)||a.equals(b)||edges.putIfAbsent(Edge.of(a,b),weight)!=null)throw new IOException("Preservation needs a simple graph with unique undirected non-self edges");if(!Double.isFinite(weight)||Math.abs(weight)>1||weight==0)weightsAvailable=false;}
        descriptor.put("signed_edge_weights_available",weightsAvailable);
        var data=StudyBioinformatics.datasets(c).stream().filter(d->text(d.get("id")).equals(text(metadata.get("dataset_id")))).findFirst().orElse(Map.of());
        for(String field:List.of("species","genome_build","feature_id_type","annotation_release","tissue","assay"))descriptor.put(field,supplied?scope.getOrDefault(field,field.equals("annotation_release")?map(metadata.get("method")).getOrDefault(field,""):""):data.getOrDefault(field,""));
        var registeredCohorts=new TreeSet<String>();for(Object id:list(supplied?scope.get("cohorts"):data.get("cohorts")))registeredCohorts.add(text(id));if(!supplied&&!text(data.get("cohort")).isBlank())registeredCohorts.add(text(data.get("cohort")));descriptor.put("cohorts",List.copyOf(registeredCohorts));
        var method=supplied?map(metadata.get("method")):metadata;
        descriptor.put("edge_direction",supplied?method.getOrDefault("edge_direction",""):"undirected");descriptor.put("edge_semantics",supplied?method.getOrDefault("edge_semantics",""):"signed_correlation");descriptor.put("edge_coverage",supplied?method.getOrDefault("edge_coverage",""):"complete_thresholded");descriptor.put("correlation",method.getOrDefault("correlation",""));descriptor.put("threshold",method.getOrDefault("threshold",""));
        if(!supplied&&!"molecular_correlation".equals(metadata.get("network_type")))descriptor.put("edge_semantics","unsupported_network_type");
        boolean referenceRequested=list(p.get("references")).isEmpty()||list(p.get("references")).contains(alias);
        var modules=new ArrayList<Module>();var matching=new LinkedHashSet<Path>();if(referenceRequested){matching.addAll(moduleDirs.getOrDefault(contentKey(map(producer.get("output_sha256"))),List.of()));matching.addAll(moduleDirs.getOrDefault("manifest:"+hash,List.of()));}var matches=new ArrayList<>(matching);
        if(matches.size()>1)throw new IOException("Ambiguous module producers for graph "+alias);
        if(matches.size()==1){Path moduleDir=matches.get(0);var m=manifest(moduleDir);descriptor.put("module_producer_state",m.get("state"));snapshot(c,moduleDir,"manifest.yaml",prefix+"-modules",outputs);
            if(!"complete".equals(m.get("state"))&&!Files.isRegularFile(moduleDir.resolve("network-modules.tsv")))return new Graph(alias,dir,hash,metadata,nodes,edges,modules,descriptor);
            required(moduleDir,m,"network-modules.tsv");snapshot(c,moduleDir,"network-modules.tsv",prefix+"-modules",outputs);
            bound(moduleDir.resolve("network-modules.tsv"),integer(p,"max_memberships"));var table=DelimitedData.readResult(moduleDir.resolve("network-modules.tsv"));var grouped=new LinkedHashMap<List<String>,Set<String>>();
            for(String[] row:table.rows()){String node=row[table.column("node_id")],id=row[table.column("module_id")],type=row[table.column("module_type")];if(!nodes.contains(node)||id.isBlank()||type.isBlank()||!grouped.computeIfAbsent(List.of(id,type),key->new LinkedHashSet<>()).add(node))throw new IOException("Invalid/duplicate reference module membership");}
            if(grouped.size()>integer(p,"max_modules"))throw new Bound("Complete source membership set exceeds max_modules");for(var entry:grouped.entrySet())modules.add(new Module(entry.getKey().get(0),entry.getKey().get(1),entry.getValue()));
        }else descriptor.put("module_producer_state",referenceRequested?"unavailable":"not_required_for_target");
        descriptor.put("nodes",nodes.size());descriptor.put("edges",edges.size());descriptor.put("modules",modules.size());
        return new Graph(alias,dir,hash,metadata,nodes,edges,modules,descriptor);
    }
    private static List<String> compatibility(Graph a,Graph b,Map<String,Object> p){
        var problems=new ArrayList<String>();var namespaces=map(p.get("graph_namespaces"));String left=text(namespaces.get(a.alias)),right=text(namespaces.get(b.alias));
        if(left.isBlank()||right.isBlank())problems.add("missing_registered_node_namespace");else if(!left.equals(right))problems.add("different_registered_node_namespaces");
        for(Graph g:List.of(a,b)){
            if(!"complete".equals(g.descriptor.get("producer_state")))problems.add("incomplete_graph_producer");
            if(!"complete".equals(g.descriptor.get("module_producer_state"))&&g==a)problems.add("incomplete_reference_modules");
            if(!"undirected".equals(g.descriptor.get("edge_direction"))||!"signed_correlation".equals(g.descriptor.get("edge_semantics"))||!"complete_thresholded".equals(g.descriptor.get("edge_coverage")))problems.add("unresolved_graph_semantics_or_coverage");
            if(!Boolean.TRUE.equals(g.descriptor.get("signed_edge_weights_available")))problems.add("unavailable_signed_edge_weights");
            if(!Set.of("pearson","spearman").contains(text(g.descriptor.get("correlation"))))problems.add("unknown_correlation_estimator");
            if(!(g.descriptor.get("threshold") instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<=0||n.doubleValue()>1)problems.add("unknown_threshold");
            else if(g.edges.values().stream().anyMatch(weight->Math.abs(weight)<n.doubleValue()))problems.add("edges_below_declared_threshold");
        }
        if(!text(a.descriptor.get("correlation")).equals(text(b.descriptor.get("correlation"))))problems.add("different_correlation_estimators");
        if(a.descriptor.get("threshold") instanceof Number x&&b.descriptor.get("threshold") instanceof Number y&&Double.compare(x.doubleValue(),y.doubleValue())!=0)problems.add("different_thresholds");
        String as=species(text(a.descriptor.get("species"))),bs=species(text(b.descriptor.get("species")));if(as.isBlank()||bs.isBlank())problems.add("unknown_species");else if(!as.equals(bs))problems.add("different_species");
        for(String field:List.of("feature_id_type","annotation_release","genome_build")){String av=text(a.descriptor.get(field)),bv=text(b.descriptor.get(field));if(!unknown(av)&&!unknown(bv)&&!canonical(field,av).equals(canonical(field,bv)))problems.add("different_"+field);}
        return List.copyOf(new LinkedHashSet<>(problems));
    }
    private static boolean unknown(String s){return s.isBlank()||Set.of("unknown","unspecified","auto","unavailable","na","n/a","nan","null","none",".").contains(s.strip().toLowerCase(java.util.Locale.ROOT));}
    private static String species(String s){String normalized=StudyPoolingAudit.canonical("species",s);return unknown(normalized)?"":normalized;}
    private static String canonical(String field,String s){return StudyPoolingAudit.canonical(field,s);}
    private static Set<String> cohorts(Map<String,Object> a,Map<String,Object> b){var result=new TreeSet<String>();for(Object raw:list(a.get("cohorts")))result.add(text(raw));var right=new HashSet<>(list(b.get("cohorts")).stream().map(StudyPublicEvidence::text).toList());result.retainAll(right);return result;}
    private static String[] compare(Graph a,Graph b,Module module,Map<String,Object> p,boolean compatible){
        var common=new TreeSet<>(module.nodes);common.retainAll(b.nodes);int shared=common.size(),total=module.nodes.size();double coverage=total==0?0:(double)shared/total;long possible=(long)shared*(shared-1)/2;
        String disposition=!compatible?"held_source_context":shared<integer(p,"minimum_common_nodes")?"below_minimum_nodes":coverage<((Number)p.get("minimum_node_coverage")).doubleValue()?"below_node_coverage":"assessed";
        String[] row=new String[COLUMNS.size()];java.util.Arrays.fill(row,"NA");String[] identifiers={a.alias,b.alias,module.id,module.type,String.valueOf(total),String.valueOf(shared),String.valueOf(coverage),String.valueOf(possible)};System.arraycopy(identifiers,0,row,0,identifiers.length);row[row.length-1]=disposition;
        if(!disposition.equals("assessed"))return row;
        var ids=new ArrayList<>(common);int ae=0,be=0,both=0,sameSign=0;double normA=0,normB=0,dot=0;
        for(int i=0;i<ids.size();i++)for(int j=i+1;j<ids.size();j++){Edge edge=Edge.of(ids.get(i),ids.get(j));Double x=a.edges.get(edge),y=b.edges.get(edge);if(x!=null){ae++;normA+=x*x;}if(y!=null){be++;normB+=y*y;}if(x!=null&&y!=null){both++;if(Math.signum(x)==Math.signum(y))sameSign++;dot+=x*y;}}
        row[8]=String.valueOf(ae);row[9]=String.valueOf(be);row[10]=String.valueOf(both);row[11]=ae==0?"NA":String.valueOf((double)both/ae);row[12]=ae+be-both==0?"NA":String.valueOf((double)both/(ae+be-both));row[13]=both==0?"NA":String.valueOf((double)sameSign/both);row[14]=normA==0||normB==0?"NA":String.valueOf(Math.max(-1,Math.min(1,dot/Math.sqrt(normA*normB))));if(ae==0)row[15]="assessed_no_reference_edges";return row;
    }
    static void validateSupplied(List<String> header,String[] row,Map<String,Object> definition)throws IOException{
        for(String field:COLUMNS.subList(0,4)){String s=row[header.indexOf(field)];if(s.isBlank()||s.length()>512||s.chars().anyMatch(Character::isISOControl))throw new IOException("Invalid preservation identity");}
        if(row[header.indexOf("reference_graph")].equals(row[header.indexOf("target_graph")]))throw new IOException("A graph cannot validate itself");
        String disposition=row[header.indexOf("disposition")];if(!DISPOSITIONS.contains(disposition))throw new IOException("Unknown preservation disposition");
        Object precision=map(definition.get("method")).getOrDefault("precision_tolerance",1e-10);if(!(precision instanceof Number tolerance)||!Double.isFinite(tolerance.doubleValue())||tolerance.doubleValue()<0||tolerance.doubleValue()>.005)throw new IOException("Preservation method.precision_tolerance must be in [0,.005]");double epsilon=tolerance.doubleValue();
        double n=value(header,row,"reference_nodes"),shared=value(header,row,"shared_nodes"),coverage=value(header,row,"node_coverage"),possible=value(header,row,"possible_pairs");
        if(n<1||n>100000||n!=Math.rint(n)||shared<0||shared!=Math.rint(shared)||shared>n||coverage<0||coverage>1||Math.abs(coverage-shared/n)>epsilon||possible!=shared*(shared-1)/2)throw new IOException("Inconsistent preservation node coverage/counts");
        if(!disposition.startsWith("assessed")){for(String field:COLUMNS.subList(8,15))if(!row[header.indexOf(field)].equals("NA"))throw new IOException("Unassessed preservation rows cannot claim edge metrics");return;}
        double ae=value(header,row,"reference_edges"),be=value(header,row,"target_edges"),both=value(header,row,"shared_edges");for(double count:new double[]{ae,be,both})if(count<0||count!=Math.rint(count)||count>possible)throw new IOException("Invalid preservation edge count");if(shared<2||both>Math.min(ae,be)||ae+be-both>possible||disposition.equals("assessed_no_reference_edges")!=(ae==0))throw new IOException("Inconsistent preservation edge overlap");
        ratio(header,row,"edge_retention",both,ae,epsilon);ratio(header,row,"edge_jaccard",both,ae+be-both,epsilon);
        metric(header,row,"signed_agreement",both==0,0,1);metric(header,row,"thresholded_weight_cosine",ae==0||be==0,-1,1);
    }
    private static double value(List<String> h,String[] row,String field)throws IOException{try{double n=Double.parseDouble(row[h.indexOf(field)]);if(!Double.isFinite(n))throw new NumberFormatException();return n;}catch(NumberFormatException bad){throw new IOException("Invalid supplied preservation "+field,bad);}}
    private static void ratio(List<String> h,String[] r,String field,double numerator,double denominator,double epsilon)throws IOException{if(denominator==0){if(!r[h.indexOf(field)].equals("NA"))throw new IOException("Undefined preservation ratio must be NA");}else{double v=value(h,r,field);if(v<0||v>1||Math.abs(v-numerator/denominator)>epsilon)throw new IOException("Preservation ratio conflicts with counts");}}
    private static void metric(List<String> h,String[] row,String field,boolean absent,double min,double max)throws IOException{if(absent){if(!row[h.indexOf(field)].equals("NA"))throw new IOException("Undefined preservation metric must be NA");}else{double n=value(h,row,field);if(n<min||n>max)throw new IOException("Invalid preservation metric range");}}
}
