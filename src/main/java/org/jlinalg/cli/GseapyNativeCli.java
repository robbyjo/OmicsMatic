/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.enrichment.GseapyLocalEnrichment;
import org.jlinalg.enrichment.GseapyStatistics;
import static org.jlinalg.cli.FollowupSupport.*;

/** Native local Enrichr analysis with complete source preparation and explicit scope. */
public final class GseapyNativeCli {
    private GseapyNativeCli() { }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream out,PrintStream err) {
        if(List.of(args).contains("--help")){out.println("gseapy-native --input PREPARED.json --edition 1.0.4|1.1.2|1.1.3|1.1.8 --identifier-policy source|literal --out NEW_DIR [--alpha 0.05]\nJSON: schema_version=1, query=[strings], gene_sets={term:[strings]}, background=[strings]|integer|null. Optional numeric_dtype=float64. Explicit null uses the complete library union. Source policy follows original identifier preparation and original errors; literal policy accepts already prepared identities. One-sided local ORA only; Python, remote Enrichr, permutations and GSVA are not used.");return 0;}
        try {
            var options=options(args,Set.of("input","edition","identifier-policy","out","alpha"));
            var edition=switch(required(options,"edition")) {
                case "1.0.4"->GseapyStatistics.Edition.V1_0_4;case "1.1.2"->GseapyStatistics.Edition.V1_1_2;
                case "1.1.3"->GseapyStatistics.Edition.V1_1_3;case "1.1.8"->GseapyStatistics.Edition.V1_1_8;
                default->throw new IllegalArgumentException("Unsupported GSEApy edition");
            };
            var policy=switch(required(options,"identifier-policy")) {
                case "source"->GseapyLocalEnrichment.Identifiers.SOURCE;case "literal"->GseapyLocalEnrichment.Identifiers.LITERAL;
                default->throw new IllegalArgumentException("identifier-policy must be source or literal");
            };
            double alpha=number(options,"alpha",0.05);
            final int[] exit={0};
            transaction(options,"native-gseapy-local-ora",(directory,manifest)->{
                try {
                    Path input=Path.of(required(options,"input"));String before=hash(input);
                    if(Files.size(input)>16777216)throw new IOException("Prepared JSON exceeds 16 MiB");
                    Object parsed=SimpleJson.parse(Files.readString(input));
                    if(!(parsed instanceof Map<?,?> raw))throw new IOException("Input must be a JSON object");
                    for(Object key:raw.keySet())if(!Set.of("schema_version","query","gene_sets","background","numeric_dtype").contains(key))throw new IOException("Unknown prepared input field: "+key);
                    if(!Long.valueOf(1).equals(raw.get("schema_version"))||!raw.containsKey("background"))throw new IOException("Require schema_version1 and explicit background, including deliberate null");
                    if(raw.containsKey("numeric_dtype")&&!"float64".equals(raw.get("numeric_dtype")))throw new IOException("Only numeric_dtype float64 is supported");
                    var query=strings(raw.get("query"));
                    if(!(raw.get("gene_sets") instanceof Map<?,?> library))throw new IOException("Require local gene_sets object");
                    var sets=new LinkedHashMap<String,List<String>>();long memberships=query.size();
                    for(var entry:library.entrySet()) {
                        if(!(entry.getKey() instanceof String term))throw new IOException("Term identity must be a string");
                        var ids=strings(entry.getValue());memberships=Math.addExact(memberships,ids.size());sets.put(term,ids);
                    }
                    Object bg=raw.get("background");
                    if(bg instanceof Long n) {if(n<0||n>Integer.MAX_VALUE)throw new IOException("Numeric population must fit nonnegative int");bg=n.intValue();}
                    else if(bg!=null){bg=strings(bg);memberships+=((List<?>)bg).size();}
                    if(memberships>2000000)throw new IOException("Complete identifier membership capacity exceeds 2 million");
                    var result=GseapyLocalEnrichment.analyze(edition,query,sets,bg,policy,alpha);
                    if(!before.equals(hash(input)))throw new IOException("Prepared input changed during analysis");
                    var rows=new ArrayList<String[]>();var hits=new ArrayList<String[]>();var warnings=new ArrayList<String>();
                    for(var row:result.rows()) {
                        rows.add(new String[]{row.term(),row.overlap()+"/"+row.termSize(),Double.toString(row.pValue()),Double.toString(row.adjustedPValue()),Double.toString(row.oddsRatio()),row.combinedScoreAvailable()?Double.toString(row.combinedScore()):"",String.join(";",row.hits())});
                        for(String hit:row.hits())hits.add(new String[]{row.term(),hit});warnings.addAll(row.warnings());
                    }
                    table(directory.resolve("results.tsv"),List.of("Term","Overlap","P-value","Adjusted P-value","Odds Ratio","Combined Score","Genes"),rows);
                    table(directory.resolve("hits.tsv"),List.of("term","gene"),hits);
                    table(directory.resolve("query-preparation.tsv"),List.of("original","effective","integer_identity"),result.query().stream().map(id->new String[]{id.original(),id.effective(),Boolean.toString(id.integer())}).toList());
                    table(directory.resolve("omitted-terms.tsv"),List.of("term","reason"),result.omittedTerms().stream().map(term->new String[]{term,"zero overlap after background intersection; excluded before BH"}).toList());
                    manifest.put("engine","native-java");manifest.put("edition",options.get("edition"));manifest.put("identifier_policy",options.get("identifier-policy"));manifest.put("input_sha256",Map.of("input",before));
                    manifest.put("population",result.population());manifest.put("query_uppercase_conversion",result.uppercased());manifest.put("integer_query",result.integerQuery());manifest.put("tested_terms",result.rows().size());manifest.put("analysis_status","completed");manifest.put("result_admitted",true);
                    if(result.rows().isEmpty())warnings.add("No overlapping terms were tested. An empty table is not evidence of pathway absence.");
                    warnings.add("One-sided local ORA; BH includes only positive-overlap terms. Hit membership is deterministic Unicode order; semicolon presentation is ambiguous for literal semicolons, so use hits.tsv. No permutations, remote database, annotation lookup or biological calibration. SOURCE uses current JVM Unicode tables; inspect query-preparation.tsv for identifier changes.");
                    Files.writeString(directory.resolve("WARNINGS.md"),String.join("\n\n",warnings)+"\n");
                    Files.writeString(directory.resolve("analysis-status.json"),"{\"status\":\"completed\",\"result_admitted\":true,\"engine\":\"native-java\"}\n");
                }catch(Exception failure){exit[0]=2;manifest.put("analysis_status","failed");manifest.put("result_admitted",false);manifest.put("error",failure.toString());NativeToolSupport.quarantine(directory);Files.writeString(directory.resolve("WARNINGS.md"),"Native local enrichment failed: "+failure+"\nRequired action: correct the explicit input/edition/policy and retry a new output directory. No partial result is admitted.\n");Files.writeString(directory.resolve("analysis-status.json"),"{\"status\":\"failed\",\"result_admitted\":false}\n");}
            });
            if(exit[0]==0)out.println("Native local enrichment completed: "+options.get("out"));else err.println("Native local enrichment failed; read WARNINGS.md in "+options.get("out"));
            return exit[0];
        }catch(Exception failure){err.println("Native local enrichment not started: "+failure.getMessage());return 2;}
    }
    private static List<String> strings(Object value)throws IOException {
        if(!(value instanceof List<?> values))throw new IOException("Expected an array of string identifiers");
        var result=new ArrayList<String>();for(Object id:values) {if(!(id instanceof String text))throw new IOException("Every identifier must be a string");if(text.indexOf('\n')>=0||text.indexOf('\r')>=0)throw new IOException("Multiline identifiers are outside the native table contract");result.add(text);}
        return result;
    }
}
