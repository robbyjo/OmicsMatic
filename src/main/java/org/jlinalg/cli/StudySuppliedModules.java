/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import org.jlinalg.enrichment.EnrichmentAnalysis;
import org.jlinalg.study.StudyOperationExecutor.Context;

/** Completed memberships and module enrichment remain usable without original graphs. */
final class StudySuppliedModules {
    static final String CAVEAT="Module memberships and module GO are source-declared descriptive context. Source analyses, background selection, mapping, graph estimation, multiplicity and biological function are not independently certified. Completed enrichment does not require unavailable upstream raw matrices or graphs. Native missing GO requires an explicit complete node universe and compatible gene mapping; no background is inferred from significant hits or module members alone.";
    private StudySuppliedModules() { }
    static void normalize(List<String> header,List<String[]> rows,String kind) throws IOException {
        add(header,rows,"module_type","supplied_module");
        if(kind.equals("module_gene_ontology")) {
            probabilityLogs(header,rows,"p_value","log_p_value");probabilityLogs(header,rows,"adjusted_p","log_adjusted_p");
            for(String key:List.of("term_name","universe_genes","module_genes","term_genes","overlap_genes","hit_genes"))add(header,rows,key,key.equals("term_name")||key.equals("hit_genes")?"":"NA");
            for(String[] row:rows) {
                Map<String,Long> counts=new LinkedHashMap<>();
                for(String key:List.of("universe_genes","module_genes","term_genes","overlap_genes")){String raw=row[header.indexOf(key)];if(missing(raw))continue;
                    try {long value=new BigDecimal(raw.trim()).longValueExact();if(value<0)throw new ArithmeticException();counts.put(key,value);}catch(NumberFormatException|ArithmeticException invalid){throw new IOException("Supplied module GO counts must be exact nonnegative signed-long integers");}}
                Long n=counts.get("universe_genes"),m=counts.get("module_genes"),k=counts.get("term_genes"),x=counts.get("overlap_genes");
                if(n!=null&&(m!=null&&m>n||k!=null&&k>n)||x!=null&&(m!=null&&x>m||k!=null&&x>k))throw new IOException("Supplied module GO counts violate subset bounds");
                if(n!=null&&m!=null&&k!=null&&x!=null&&BigInteger.valueOf(m).add(BigInteger.valueOf(k)).subtract(BigInteger.valueOf(n)).compareTo(BigInteger.valueOf(x))>0)throw new IOException("Supplied module GO overlap is below the feasible intersection bound");
                String hits=row[header.indexOf("hit_genes")];var unique=new LinkedHashSet<String>();if(!missing(hits))for(String gene:hits.split(";",-1))if(!valid(gene)||!unique.add(gene))throw new IOException("Supplied module GO hit genes must be unique nonblank identifiers");
                if(x!=null&&unique.size()>x)throw new IOException("Supplied module GO hit list exceeds its declared overlap count");
            }
        }
        for(String[] row:rows)for(String key:List.of("node_id","module_id","module_type","term_id","network_id"))if(header.contains(key)&&!valid(row[header.indexOf(key)]))throw new IOException("Supplied modules need bounded nonblank IDs and types");
    }
    private static void probabilityLogs(List<String> header,List<String[]> rows,String probability,String logarithm)throws IOException {
        if(!header.contains(probability))return;add(header,rows,logarithm,"NA");
        for(String[] row:rows){String raw=row[header.indexOf(probability)],log=row[header.indexOf(logarithm)];if(missing(raw))continue;
            BigDecimal value;try{value=new BigDecimal(raw.trim());if(value.signum()<0||value.compareTo(BigDecimal.ONE)>0)throw new NumberFormatException();}catch(NumberFormatException invalid){throw new IOException("Supplied module GO probabilities must be decimal values in [0,1]");}
            double p=value.doubleValue(),derived;
            if(p>=Double.MIN_NORMAL)derived=Math.log(p);else if(value.signum()>0){String digits=value.unscaledValue().abs().toString();String lead=digits.substring(0,Math.min(16,digits.length()));double mantissa=Double.parseDouble(lead)/Math.pow(10,lead.length()-1);derived=Math.log(mantissa)+((long)value.precision()-value.scale()-1)*Math.log(10);}else derived=Double.NaN;
            if(missing(log)){if(Double.isFinite(derived))row[header.indexOf(logarithm)]=String.valueOf(derived);continue;}
            double source;try{source=Double.parseDouble(log);}catch(NumberFormatException invalid){throw new IOException("Supplied module GO log probabilities must be numeric");}
            if(!Double.isFinite(source)||source>0||Double.isFinite(derived)&&Math.abs(source-derived)>1e-8*Math.max(1,Math.abs(derived))||value.signum()==0&&Math.exp(source)!=0)throw new IOException("Supplied module GO P/log-P values disagree");
        }
    }
    static void adjust(List<String> header,List<String[]> rows,String method,List<String> needs) {
        if(!Set.of("BH","BY","BONFERRONI","NONE").contains(method)){needs.add("Supplied module GO requires source full-family adjusted P values for PROVIDED adjustment");return;}
        var positions=new ArrayList<Integer>();var values=new ArrayList<Double>();
        for(int i=0;i<rows.size();i++){String[] row=rows.get(i);if(missing(row[header.indexOf("p_value")]))continue;String log=row[header.indexOf("log_p_value")];if(missing(log)&&!method.equals("NONE")){needs.add("Complete module GO adjustment needs finite log P for a rounded zero P; supply source log P or source full-family adjusted values");return;}positions.add(i);values.add(missing(log)?Double.NEGATIVE_INFINITY:Double.parseDouble(log));}
        double[] logs=values.stream().mapToDouble(Double::doubleValue).toArray();double[] adjusted;
        if(method.equals("BONFERRONI")){adjusted=logs.clone();for(int i=0;i<adjusted.length;i++)adjusted[i]=Math.min(0,adjusted[i]+Math.log(logs.length));}
        else adjusted=EnrichmentAnalysis.adjustLog(logs,EnrichmentAnalysis.Fdr.valueOf(method));
        add(header,rows,"adjusted_p","NA");add(header,rows,"log_adjusted_p","NA");
        for(int i=0;i<positions.size();i++){var row=rows.get(positions.get(i));row[header.indexOf("adjusted_p")]=String.valueOf(Math.exp(adjusted[i]));row[header.indexOf("log_adjusted_p")]=Double.isFinite(adjusted[i])?String.valueOf(adjusted[i]):"NA";}
    }
    static String identity(List<String> header,String[] row) {
        var key=new ArrayList<String>();for(String name:List.of("network_id","module_id","module_type","term_id"))if(header.contains(name))key.add(row[header.indexOf(name)]);return StudyAssistance.json(key);
    }
    static void write(Context c,String kind,List<String> header,List<String[]> rows,Map<String,Object> definition,
            Map<String,Path> snapshots,Map<String,Object> metadata,List<String> outputs) throws IOException {
        metadata.put("required_manuscript_caveat",text(metadata.get("required_manuscript_caveat"))+" "+CAVEAT);
        if(kind.equals("module_gene_ontology")) {
            var summary=new LinkedHashMap<String,Object>();for(String key:List.of("execution_origin","analysis_executed_by_jlinalg","reported_status","resolved_family","result_rows","required_manuscript_caveat"))summary.put(key,metadata.get(key));
            summary.put("analysis_performed",false);summary.put("source_analysis_performed",true);summary.put("module_GO_recomputed",false);summary.put("source_background_policy","Source-reported; unavailable graph, membership or GO database is not a prerequisite for reuse");
            StudyPublicEvidence.write(c.outputDirectory().resolve("module-go.yaml"),summary);outputs.add("module-go.yaml");return;
        }
        var summary=new LinkedHashMap<String,Object>();var scope=map(definition.get("scope"));summary.put("module_origin","externally_produced_memberships");summary.put("execution_origin","externally_produced");summary.put("analysis_executed_by_jlinalg",false);summary.put("source_network_type","externally_supplied");summary.put("memberships",rows.size());
        summary.put("modules",rows.stream().map(row->StudyAssistance.json(List.of(row[header.indexOf("module_id")],row[header.indexOf("module_type")]))).distinct().count());summary.put("species",scope.getOrDefault("species","unknown"));summary.put("required_manuscript_caveat",metadata.get("required_manuscript_caveat"));summary.put("reported_status",definition.get("reported_status"));
        var graphParents=new ArrayList<Path>();
        for(Path dir:c.dependencyDirectories().values()){var file=dir.resolve("manifest.yaml");if(!Files.isRegularFile(file))continue;var m=StudyYaml.loadArtifact(file);if(!Set.of("network_analysis","pathway_network").contains(m.get("operation_type")))continue;
            if(!"complete".equals(m.get("state")))throw new IOException("A declared source graph is incomplete; memberships cannot certify its scope");
            for(var entry:map(m.get("output_sha256")).entrySet())StudyFindingInventory.verified(dir,entry.getKey(),text(entry.getValue()));graphParents.add(dir);}
        if(graphParents.size()>1)throw new IOException("Standalone supplied memberships require at most one declared source graph; separate independent graph scopes instead of implicitly merging them");
        if(!graphParents.isEmpty()){Path dir=graphParents.get(0);var m=StudyYaml.loadArtifact(dir.resolve("manifest.yaml"));summary.put("source_network_sha256",FollowupSupport.hash(dir.resolve("manifest.yaml")));summary.put("source_network_output_sha256",map(m.get("output_sha256")));
            if(Files.isRegularFile(dir.resolve("network-analysis.yaml"))){var graph=StudyYaml.loadArtifact(dir.resolve("network-analysis.yaml"));for(String key:List.of("dataset_id","source_kind","network_type","species","membership_gene_id_type"))if(graph.containsKey(key))summary.put(key.equals("network_type")?"source_network_type":key,graph.get(key));}
            for(String name:List.of("network-nodes.tsv","feature-annotation.tsv","pathway-genes.tsv"))if(Files.isRegularFile(dir.resolve(name))&&!snapshots.containsKey(name.equals("network-nodes.tsv")?"nodes":name.equals("pathway-genes.tsv")?"pathway_genes":"feature_annotation")){
                Files.copy(dir.resolve(name),c.outputDirectory().resolve(name));outputs.add(name);}
        }
        if(snapshots.containsKey("nodes")) {
            if(Files.size(snapshots.get("nodes"))>134217728L)throw new IOException("Supplied module node universe exceeds the 128 MiB import bound");
            var nodes=DelimitedData.readResult(snapshots.get("nodes"));var columns=map(definition.get("columns"));var mapped=columns.get("nodes") instanceof Map<?,?>?map(columns.get("nodes")):columns;String id=text(mapped.getOrDefault("node_id","node_id"));int at=nodes.column(id);var nodeHeader=new ArrayList<>(nodes.header());if(!id.equals("node_id")&&nodeHeader.contains("node_id"))throw new IOException("Ambiguous supplied module node_id mapping");nodeHeader.set(at,"node_id");
            FollowupSupport.table(c.outputDirectory().resolve("network-nodes.tsv"),nodeHeader,nodes.rows());outputs.add("network-nodes.tsv");
            nodes=DelimitedData.readResult(c.outputDirectory().resolve("network-nodes.tsv"));
            if(!graphParents.isEmpty()){var parent=DelimitedData.readResult(graphParents.get(0).resolve("network-nodes.tsv"));if(!nodeIds(parent).equals(nodeIds(nodes)))throw new IOException("Supplied membership node universe conflicts with its declared graph");}
        }
        Path nodes=c.outputDirectory().resolve("network-nodes.tsv");
        if(Files.isRegularFile(nodes)){Set<String> ids=nodeIds(DelimitedData.readResult(nodes));for(String[] row:rows)if(!ids.contains(row[header.indexOf("node_id")]))throw new IOException("Supplied module membership references a node outside the declared universe");summary.put("nodes",ids.size());summary.put("node_universe_available",true);}
        else {summary.put("node_universe_available",false);summary.put("node_universe_status","Unavailable; memberships remain reusable, but native missing module GO requires a separately declared complete universe");}
        if(header.contains("network_id")){var namespaces=new LinkedHashSet<String>();for(String[] row:rows)namespaces.add(row[header.indexOf("network_id")]);summary.put("network_namespaces",List.copyOf(namespaces));summary.put("multiple_network_scopes",namespaces.size()>1);}
        var kinds=new LinkedHashMap<String,String>();for(String[] row:rows){String id=row[header.indexOf("module_id")],type=row[header.indexOf("module_type")];String previous=kinds.putIfAbsent(id,type);if(previous!=null&&!previous.equals(type))summary.put("module_ids_require_type_qualification",true);}
        metadata.putAll(summary);StudyPublicEvidence.write(c.outputDirectory().resolve("module-analysis.yaml"),summary);outputs.add("module-analysis.yaml");
    }
    private static Set<String> nodeIds(DelimitedData table)throws IOException {var ids=new LinkedHashSet<String>();if(table.rows().size()>100000)throw new IOException("Supplied module node universe exceeds 100000-node bound");int at=table.column("node_id");for(String[] row:table.rows())if(!valid(row[at])||!ids.add(row[at]))throw new IOException("Supplied module node universe needs unique nonblank IDs");return ids;}
    private static boolean valid(String value){return !value.isBlank()&&value.length()<=4000&&value.chars().noneMatch(Character::isISOControl);}
    private static boolean missing(String value){return value.isBlank()||Set.of("na","nan","null",".").contains(value.toLowerCase(Locale.ROOT));}
    private static void add(List<String> header,List<String[]> rows,String name,String value){if(header.contains(name))return;header.add(name);for(int i=0;i<rows.size();i++){String[] before=rows.get(i),after=java.util.Arrays.copyOf(before,before.length+1);after[before.length]=value;rows.set(i,after);}}
}
