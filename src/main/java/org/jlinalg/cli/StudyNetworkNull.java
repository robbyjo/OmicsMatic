/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jlinalg.network.NetworkLabelPermutation;
import org.jlinalg.network.NetworkLabelPermutation.Edge;
import org.jlinalg.study.StudyNetworkNullPolicy;
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.study.StudyNeed;

/** A separate conditional test on frozen source graphs; never selects association findings. */
final class StudyNetworkNull implements StudyOperationExecutor {
    static final String CAVEAT="Conditional topology test: reference memberships and edge/sign patterns are fixed before target-label permutations within the shared-node universe and optional prespecified strata. The null assumes target node-label exchangeability within those strata; source declarations do not certify it. Target topology, signs and stratum sizes are retained. Missing nodes are excluded, not treated as zero correlations. Selected thresholded edges are not full raw correlations. These P values do not measure participant-sampling uncertainty, independently validate source graphs or source selection, establish independent replication, implement WGCNA Zsummary, or establish biological function or causality. BY/BH have their respective dependence assumptions; Bonferroni controls familywise error, and NONE is nominal without FDR control. Shared participants, gene properties and data-selected reference modules require scientific review. No association FDR, primary cohort set or official graph is changed.";
    static final List<String> COLUMNS=List.of("reference_graph","target_graph","module_id","module_type","statistic","universe_nodes","reference_edges","observed_statistic","permutation_method","permutations","extreme_count","p_value","p_resolution","tail_lower_95","tail_upper_95","adjusted_p","adjustment","family_size","family_status","significant","disposition","seed");
    private record Graph(List<String> nodes,List<String[]> edges) { }
    private record Task(String[] row,Graph reference,Graph target,List<String> universe,List<String> members,int[][] strata,NetworkLabelPermutation.Plan plan) { }

    @Override public Result execute(Context c)throws Exception {
        Files.createDirectories(c.outputDirectory());
        var policy=StudyNetworkNullPolicy.resolve(map(map(StudyBioinformatics.hypothesis(c).get("analysis")).get("network_preservation_null")));
        var needs=new ArrayList<String>();var outputs=new ArrayList<String>();
        var inputDependencies=new LinkedHashMap<String,Path>();
        for(var e:c.dependencyDirectories().entrySet()){
            var m=StudyYaml.loadArtifact(e.getValue().resolve("manifest.yaml"));
            if(List.of("network_analysis","network_modules").contains(text(m.get("operation_type"))))inputDependencies.put(e.getKey(),e.getValue());
        }
        // Read/verify graphs and memberships through the existing descriptive contract.
        // Supplied descriptive statistics cannot stand in for unavailable edges.
        Path prepared=c.outputDirectory().resolve("prepared-preservation");Files.createDirectories(prepared);
        var operation=new LinkedHashMap<>(c.operation());operation.put("type","network_preservation");operation.remove("supplied_result_id");
        Result source=new StudyNetworkPreservation().execute(new Context(c.project(),prepared,operation,c.study(),c.plan(),inputDependencies,c.resumeCompletedWork()));
        for(String name:source.outputs())outputs.add("prepared-preservation/"+name);
        if(source.state()!=State.COMPLETE)needs.add("Complete compatible source graphs and frozen reference memberships are required; see prepared-preservation/network-preservation.yaml");
        if(!policy.containsKey("exchangeability_rationale"))needs.add("Declare the prespecified target-label exchangeability rationale, including relevant gene properties and source dependence");
        var graphs=new LinkedHashMap<String,Graph>();
        for(Object raw:list(source.details().get("sources"))){var descriptor=map(raw);String alias=text(descriptor.get("graph_alias"));if(alias.isBlank())continue;var files=map(descriptor.get("snapshots"));var nodes=DelimitedData.readResult(prepared.resolve(text(files.get("network-nodes.tsv"))));var edges=DelimitedData.readResult(prepared.resolve(text(files.get("network-edges.tsv"))));
            graphs.put(alias,new Graph(nodes.rows().stream().map(row->row[nodes.column("node_id")]).sorted().toList(),edges.rows().stream().map(row->new String[]{row[edges.column("source")],row[edges.column("target")],edges.header().contains("weight")?row[edges.column("weight")]:"NA"}).toList()));
        }
        var memberships=DelimitedData.readResult(prepared.resolve("network-preservation-memberships.tsv"));
        var members=new LinkedHashMap<List<String>,List<String>>();
        for(String[] row:memberships.rows())members.computeIfAbsent(List.of(row[0],row[1],row[2]),ignored->new ArrayList<>()).add(row[3]);
        Files.copy(prepared.resolve("network-preservation-memberships.tsv"),c.outputDirectory().resolve("network-preservation-memberships.tsv"));outputs.add("network-preservation-memberships.tsv");
        var strata=new LinkedHashMap<String,String>();boolean strataAvailable=true;
        if(policy.containsKey("strata_file"))try{
            Path file=c.project().resolve(text(policy.get("strata_file"))).normalize();
            if(!Files.isRegularFile(file)||Files.size(file)>16*1024*1024)throw new IOException("Strata file is missing or exceeds 16 MiB");
            var table=DelimitedData.readResult(file);if(table.rows().size()>200000)throw new IOException("Strata file exceeds 200000 nodes");
            for(String[] row:table.rows()){String node=row[table.column("node_id")],group=row[table.column("stratum")];if(node.isBlank()||group.isBlank()||node.length()>512||group.length()>512||node.chars().anyMatch(Character::isISOControl)||group.chars().anyMatch(Character::isISOControl)||strata.putIfAbsent(node,group)!=null)throw new IOException("Strata need unique explicit node IDs and bounded stratum labels");}
            Files.copy(file,c.outputDirectory().resolve("permutation-strata.tsv"));outputs.add("permutation-strata.tsv");
        }catch(IOException|IllegalArgumentException error){strataAvailable=false;needs.add("Review permutation strata: "+error.getMessage());}
        var sourceTable=DelimitedData.readResult(prepared.resolve("network-preservation.tsv"));
        var rows=new ArrayList<String[]>();var tasks=new ArrayList<Task>();long work=0,nullRows=0;
        for(String[] input:sourceTable.rows()){
            String[] row=new String[COLUMNS.size()];Arrays.fill(row,"NA");for(int i=0;i<4;i++)row[i]=input[i];
            row[4]=text(policy.get("statistic"));row[16]=text(policy.get("adjustment"));row[19]="false";row[20]=input[15].equals("held_source_context")?"held_source_context":"not_estimable_"+input[15];rows.add(row);
            if(!input[15].equals("assessed"))continue;
            Graph a=graphs.get(input[0]),b=graphs.get(input[1]);var common=new TreeSet<>(a.nodes);common.retainAll(b.nodes);var universe=List.copyOf(common);
            var module=members.getOrDefault(List.of(input[0],input[2],input[3]),List.of());
            row[5]=Integer.toString(universe.size());row[6]=input[8];row[20]="held_prerequisite";
            if(!policy.containsKey("exchangeability_rationale")||!strataAvailable)continue;
            try{
                var groups=new TreeMap<String,List<Integer>>();for(int i=0;i<universe.size();i++){String label=policy.containsKey("strata_file")?strata.get(universe.get(i)):"all";if(label==null)throw new IllegalArgumentException("Strata omit shared node "+universe.get(i));groups.computeIfAbsent(label,ignored->new ArrayList<>()).add(i);}
                int[][] partition=groups.values().stream().map(values->values.stream().mapToInt(Integer::intValue).toArray()).toArray(int[][]::new);
                int edgeCount=Integer.parseInt(input[8]);
                var plan=NetworkLabelPermutation.plan(universe.size(),edgeCount,b.edges.size(),partition,text(policy.get("method")),number(policy,"exact_maximum"),number(policy,"draws"));
                work+=plan.work();nullRows+=Math.min((long)edgeCount+1,plan.states());
                tasks.add(new Task(row,a,b,universe,module,partition,plan));row[20]="planned";
            }catch(IllegalArgumentException error){needs.add("Review "+identity(row)+": "+error.getMessage());}
        }
        boolean budget=work>((Number)policy.get("max_work")).longValue()||nullRows>((Number)policy.get("max_null_rows")).longValue();
        if(budget)needs.add("Complete null work exceeds max_work or max_null_rows; no preferred comparison prefix was tested");
        var probabilities=new LinkedHashMap<String,Double>();var histogramRows=new ArrayList<String[]>();int tested=0;
        for(Task task:tasks){String[] row=task.row;if(budget){row[20]="held_budget";continue;}
            var ids=new LinkedHashMap<String,Integer>();for(int i=0;i<task.universe.size();i++)ids.put(task.universe.get(i),i);
            var module=new java.util.HashSet<>(task.members);var pattern=edges(task.reference,ids,module);var target=edges(task.target,ids,null);
            if(pattern.size()!=Integer.parseInt(row[6]))throw new IOException("Frozen reference pattern disagrees with descriptive edge counts");
            long seed=seed(number(policy,"seed"),identity(row));
            var result=NetworkLabelPermutation.test(task.universe.size(),pattern,target,task.strata,row[4].equals("signed_edge_overlap"),task.plan,seed);
            row[7]=Integer.toString(result.observed());row[8]=task.plan.method();row[9]=Long.toString(result.states());row[10]=Long.toString(result.extreme());row[11]=Double.toString(result.pValue());row[12]=Double.toString(result.resolution());row[13]=Double.toString(result.tailLower95());row[14]=Double.toString(result.tailUpper95());row[20]="tested";row[21]=Long.toString(seed);
            probabilities.put(identity(row),result.pValue());tested++;
            for(var e:new TreeMap<>(result.histogram()).entrySet())histogramRows.add(new String[]{row[0],row[1],row[2],row[3],Integer.toString(e.getKey()),Long.toString(e.getValue())});
        }
        boolean complete=needs.isEmpty();String familyStatus=complete?"complete":"provisional_unresolved_tests";
        var adjusted=complete?StudyMrMultiplicity.adjust(probabilities,rows.size(),text(policy.get("adjustment"))):Map.<String,Double>of();
        for(String[] row:rows){row[17]=complete?Integer.toString(rows.size()):"NA";row[18]=familyStatus;Double q=adjusted.get(identity(row));if(q!=null){row[15]=Double.toString(q);row[19]=Boolean.toString(q<((Number)policy.get("alpha")).doubleValue());}}
        FollowupSupport.table(c.outputDirectory().resolve("network-preservation-tests.tsv"),COLUMNS,rows);outputs.add("network-preservation-tests.tsv");
        FollowupSupport.table(c.outputDirectory().resolve("network-null-statistic-counts.tsv"),List.of("reference_graph","target_graph","module_id","module_type","null_statistic","count"),histogramRows);outputs.add("network-null-statistic-counts.tsv");
        var summary=new LinkedHashMap<String,Object>();summary.put("effective_policy",policy);summary.put("family_status",familyStatus);summary.put("family_size",complete?rows.size():"unresolved");summary.put("observed_comparisons",rows.size());summary.put("tested",tested);summary.put("planned_work_upper_bound",work);summary.put("planned_null_rows_upper_bound",nullRows);summary.put("analysis_performed",tested>0);summary.put("required_manuscript_caveat",CAVEAT);summary.put("primary_selection_eligible",false);summary.put("tail_interval_interpretation","Clopper-Pearson interval for the conditional extreme-tail fraction in sampled mode; exact mode repeats its exact P value. The interval is Monte Carlo precision, not participant-sampling uncertainty.");summary.put("source_graphs",source.details().get("sources"));
        var methodPolicy=new LinkedHashMap<>(policy);methodPolicy.remove("exchangeability_rationale");methodPolicy.remove("strata_file");methodPolicy.put("local_exchangeability_rationale_recorded",policy.containsKey("exchangeability_rationale"));methodPolicy.put("local_strata_supplied",policy.containsKey("strata_file"));
        var method=new LinkedHashMap<>(StudyMethods.record("Conditional frozen-edge target-label permutation test",methodPolicy,List.of(),"Review the registered local exchangeability rationale; it is not scientifically certified by this software.",List.of("Langfelder module preservation 2011","Hemerik Goeman random permutation testing 2018")));method.put("performed",tested>0);method.put("tested_comparisons",tested);method.put("required_manuscript_caveat",CAVEAT);summary.put("method_report",method);
        summary.put("needs",needs.stream().map(n->StudyNeed.distinctReview("network-null:"+c.operation().get("hypothesis"),"Resolve the conditional network-label test",n,true).document()).toList());
        write(c.outputDirectory().resolve("network-preservation-null.yaml"),summary);outputs.add("network-preservation-null.yaml");
        return new Result(complete?State.COMPLETE:State.WAITING_FOR_INPUT,complete?"Tested frozen graph wiring under the registered conditional label null":"Retained conditional network tests with unresolved source, exchangeability or work contracts",outputs,summary);
    }
    private static int number(Map<String,Object> p,String field){return ((Number)p.get(field)).intValue();}
    private static String identity(String[] row){return StudyAssistance.json(List.of(row[0],row[1],row[2],row[3]));}
    private static long seed(int seed,String id)throws java.security.NoSuchAlgorithmException{return java.nio.ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest((seed+"\u0000"+id).getBytes(StandardCharsets.UTF_8))).getLong();}
    private static List<Edge> edges(Graph g,Map<String,Integer> ids,java.util.Set<String> members){var result=new ArrayList<Edge>();for(String[] row:g.edges)if(ids.containsKey(row[0])&&ids.containsKey(row[1])&&(members==null||members.contains(row[0])&&members.contains(row[1])))result.add(new Edge(ids.get(row[0]),ids.get(row[1]),Double.parseDouble(row[2])>0?1:-1));return result;}
    static void validateSupplied(List<String> h,List<String[]> rows,Map<String,Object> definition)throws IOException{
        var family=map(definition.get("family"));var scopes=new java.util.HashSet<List<String>>();var identities=new java.util.HashSet<List<String>>();
        Object configuredPrecision=map(definition.get("method")).getOrDefault("precision_tolerance",1e-10);
        if(!(configuredPrecision instanceof Number precision)||!Double.isFinite(precision.doubleValue())||precision.doubleValue()<0||precision.doubleValue()>.005)throw new IOException("Graph-null precision_tolerance must be in [0,.005]");
        double tolerance=precision.doubleValue();
        for(String[] row:rows){for(String field:COLUMNS)if(row[h.indexOf(field)].length()>8000||row[h.indexOf(field)].chars().anyMatch(Character::isISOControl))throw new IOException("Unbounded supplied network null field");
            for(String field:COLUMNS.subList(0,4))if(row[h.indexOf(field)].isBlank())throw new IOException("Empty graph-null identity");
            if(!identities.add(COLUMNS.subList(0,4).stream().map(field->row[h.indexOf(field)]).toList()))throw new IOException("Duplicate graph-null comparison");
            scopes.add(List.of(row[h.indexOf("statistic")],row[h.indexOf("adjustment")],row[h.indexOf("family_size")],row[h.indexOf("family_status")]));
            if(scopes.size()>1)throw new IOException("A supplied graph-null family has inconsistent statistic/adjustment/count/status");
            if(row[h.indexOf("reference_graph")].equals(row[h.indexOf("target_graph")]))throw new IOException("A graph cannot test itself");
            if(!List.of("edge_overlap","signed_edge_overlap").contains(row[h.indexOf("statistic")]))throw new IOException("Unsupported supplied graph-null statistic");
            if(!List.of("true","false").contains(row[h.indexOf("significant")]))throw new IOException("Invalid graph-null significance flag");
            if(!List.of("tested","held_prerequisite","held_budget","held_source_context","not_estimable_below_node_coverage","not_estimable_below_minimum_nodes","not_estimable_assessed_no_reference_edges").contains(row[h.indexOf("disposition")]))throw new IOException("Unknown supplied graph-null disposition");
            if(!List.of("complete","provisional_unresolved_tests").contains(row[h.indexOf("family_status")]))throw new IOException("Unknown graph-null family status");
            if(!List.of("BH","BY","bonferroni","NONE").contains(row[h.indexOf("adjustment")]))throw new IOException("Unknown graph-null adjustment");
            if(!row[h.indexOf("family_status")].equals("complete")&&(!row[h.indexOf("adjusted_p")].equals("NA")||row[h.indexOf("significant")].equals("true")))throw new IOException("Unresolved graph-null family claims adjustment/discovery");
            if(row[h.indexOf("family_status")].equals("complete")&&(row[h.indexOf("disposition")].startsWith("held")||row[h.indexOf("family_size")].equals("NA")))throw new IOException("Complete graph-null family contains unresolved tests");
            if(!row[h.indexOf("family_size")].equals("NA")){double size=numeric(h,row,"family_size");if(size!=Math.rint(size)||size<rows.size()||size>1000000000||family.get("tested_count") instanceof Number n&&size!=n.doubleValue())throw new IOException("Inconsistent source graph-null family size");}
            if(!row[h.indexOf("disposition")].equals("tested")){for(String field:List.of("observed_statistic","permutation_method","permutations","extreme_count","p_value","p_resolution","tail_lower_95","tail_upper_95","adjusted_p"))if(!row[h.indexOf(field)].equals("NA"))throw new IOException("Untested graph-null row claims performed inference");if(row[h.indexOf("significant")].equals("true"))throw new IOException("Untested graph-null row claims significance");continue;}
            try{long states=Long.parseLong(row[h.indexOf("permutations")]),extreme=Long.parseLong(row[h.indexOf("extreme_count")]);String method=row[h.indexOf("permutation_method")];double p=Double.parseDouble(row[h.indexOf("p_value")]);if(states<1||states>1000000000||extreme<0||extreme>states||method.equals("exact")&&extreme==0||!List.of("exact","monte_carlo").contains(method)||!Double.isFinite(p)||p<=0||p>1||Math.abs(p-(method.equals("exact")?(double)extreme/states:(double)(extreme+1)/(states+1)))>tolerance)throw new IOException("Supplied permutation P conflicts with count/method");
                double nodes=numeric(h,row,"universe_nodes"),edges=numeric(h,row,"reference_edges"),observed=numeric(h,row,"observed_statistic");if(nodes!=Math.rint(nodes)||nodes<2||nodes>100000||edges!=Math.rint(edges)||edges<1||edges>nodes*(nodes-1)/2||observed!=Math.rint(observed)||observed<0||observed>edges)throw new IOException("Invalid graph-null universe/pattern/statistic counts");
                double resolution=numeric(h,row,"p_resolution"),lower=numeric(h,row,"tail_lower_95"),upper=numeric(h,row,"tail_upper_95");
                double expectedLower=method.equals("exact")?p:extreme==0?0:jdistlib.Beta.quantile(.025,extreme,states-extreme+1,true,false),expectedUpper=method.equals("exact")?p:extreme==states?1:jdistlib.Beta.quantile(.975,extreme+1,states-extreme,true,false);
                if(resolution<=0||resolution>1||Math.abs(resolution-1.0/(states+(method.equals("exact")?0:1)))>tolerance||lower<0||upper>1||lower>upper||Math.abs(lower-expectedLower)>tolerance||Math.abs(upper-expectedUpper)>tolerance)throw new IOException("Supplied graph-null resolution/precision interval conflicts with its count model");
                String q=row[h.indexOf("adjusted_p")];if(row[h.indexOf("family_status")].equals("complete")&&q.equals("NA"))throw new IOException("A completed tested graph-null row lacks source-adjusted P");
                if(!q.equals("NA")){double adjusted=numeric(h,row,"adjusted_p");if(adjusted<0||adjusted>1||adjusted+tolerance<p)throw new IOException("Invalid source-adjusted graph-null P");if(family.containsKey("alpha")){double alpha=((Number)family.get("alpha")).doubleValue();if(Boolean.parseBoolean(row[h.indexOf("significant")])!=(adjusted<alpha)&&Math.abs(adjusted-alpha)>tolerance)throw new IOException("Source significance disagrees with its declared alpha");}else if(row[h.indexOf("significant")].equals("true"))throw new IOException("Reported graph-null significance requires an explicit source alpha");}
            }catch(NumberFormatException error){throw new IOException("Invalid supplied graph-null numeric fields",error);}
        }
        if("complete".equals(family.get("coverage"))&&family.get("tested_count") instanceof Number n&&n.intValue()!=rows.size())throw new IOException("Complete supplied graph-null family does not match its reported row count");
    }
    private static double numeric(List<String> h,String[] row,String field)throws IOException{try{double n=Double.parseDouble(row[h.indexOf(field)]);if(!Double.isFinite(n))throw new NumberFormatException();return n;}catch(NumberFormatException error){throw new IOException("Invalid supplied graph-null "+field,error);}}
}
