/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.enrichment.FumaDegEnrichment;
import org.jlinalg.enrichment.FumaGeneExpression;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed local precomputed-expression selection and source DEG enrichment. */
final class FumaExpressionCli {
    private FumaExpressionCli() { }
    private static final Set<String> ALLOWED=Set.of("method","expression-options-file","build","annotation-id","expression-id","deg-id","symbols","query","background","log2","norm","deg-sets","duplicates","outside-query","max-bytes","max-genes","max-labels","max-values","max-terms","max-memberships","timeout","out");
    private static final Set<String> PATHS=Set.of("symbols","query","background","log2","norm","deg-sets","out");
    private static final String SCOPE="SUPPLIED PRECOMPUTED EXPRESSION AND DEG SETS ONLY. Exact resolved Ensembl IDs are required. Matrix values, row order and independent log2/norm labels come from your files. No ID conversion, expression normalization, DEG generation, heatmap clustering, cell-type MAGMA or hosted FUMA analysis is performed. Required action: supply the intended genome/annotation/expression/DEG releases and complete background; inspect every warning, source hash, gate and test family before interpreting results.";
    static int run(String[] args,PrintStream out,PrintStream err) {
        if(Arrays.asList(args).contains("--help")){out.println(help());return 0;}
        try {
            var o=parse(args);if(o.containsKey("method")&&!o.get("method").equals("expression"))throw new IOException("--method must be expression");
            for(String key:List.of("build","annotation-id","expression-id","deg-id","symbols","query","background","log2","norm","deg-sets","duplicates","outside-query","out"))required(o,key);
            int[] code={0};transaction(o,"fuma-expression",(directory,manifest)->{
                var warnings=new ArrayList<String>();warnings.add(SCOPE);var session=new LinkedHashMap<String,Object>();var sources=new LinkedHashMap<Path,String>();
                session.put("source_commit","bdfbf06b5dd24037ec3c7497d773fad3c2656150");session.put("resolved_namespace","exact-ensembl");
                for(String key:List.of("build","annotation-id","expression-id","deg-id"))session.put(key,o.get(key));
                try {
                    int maxBytes=integer(o,"max-bytes",268435456,1,1073741824),maxGenes=integer(o,"max-genes",1000000,2,10000000),maxLabels=integer(o,"max-labels",10000,2,100000);
                    int maxValues=integer(o,"max-values",20000000,1,100000000),maxTerms=integer(o,"max-terms",100000,1,1000000),maxMembers=integer(o,"max-memberships",10000000,1,100000000);
                    long timeout=integer(o,"timeout",300,1,86400),deadline=System.nanoTime()+timeout*1000000000L;
                    FumaDegEnrichment.Check check=()->{if(Thread.currentThread().isInterrupted()||System.nanoTime()>deadline)throw new IllegalStateException("Expression analysis cancelled or deadline exceeded; increase --timeout or reduce complete input populations");};
                    String multiplicity=required(o,"duplicates"),outside=required(o,"outside-query");
                    if(!Set.of("error","original-count").contains(multiplicity))throw new IOException("--duplicates must be error or original-count");
                    if(!Set.of("error","source-size").contains(outside))throw new IOException("--outside-query must be error or source-size");
                    List<String> query=ids(Path.of(o.get("query")),sources,maxBytes,maxGenes,check),background=ids(Path.of(o.get("background")),sources,maxBytes,maxGenes,check);
                    var symbols=genes(Path.of(o.get("symbols")),sources,maxBytes,maxGenes,check);
                    var log2=matrix(Path.of(o.get("log2")),sources,maxBytes,maxGenes,maxLabels,maxValues,check);
                    var norm=matrix(Path.of(o.get("norm")),sources,maxBytes,maxGenes,maxLabels,maxValues,check);
                    var sets=sets(Path.of(o.get("deg-sets")),sources,maxBytes,maxTerms,maxMembers,check);
                    if(multiplicity.equals("error")&&(new HashSet<>(query).size()!=query.size()||new HashSet<>(background).size()!=background.size()))throw new IOException("Repeated resolved query/background IDs; select deliberate original-count policy or correct inputs");
                    var expression=FumaGeneExpression.select(query,symbols,log2,norm,new FumaGeneExpression.Limits(maxGenes,maxLabels,maxValues),check);
                    session.put("query_entries",query.size());session.put("background_entries_before_expression",background.size());session.put("expression_query_hits",expression.queryEntriesInLog2());session.put("expression_source_gate_opened",expression.sourceGateOpened());session.put("log2_output_rows",expression.log2Rows().size());session.put("norm_output_rows",expression.normRows().size());
                    session.put("same_ordered_expression_population",expression.sameOrderedPopulation());
                    if(expression.sourceGateOpened()) {
                        expression(directory.resolve("log2-expression.tsv"),expression.log2Labels(),expression.log2Rows());expression(directory.resolve("norm-expression.tsv"),expression.normLabels(),expression.normRows());
                        if(expression.normRows().isEmpty())warnings.add("EMPTY NORMALIZED EXPRESSION POPULATION: the original R output omits the ensg header for this empty subset. This typed table keeps its complete header and has no data rows. Required action: supply compatible normalized-resource coverage; an empty table is not zero expression.");
                        if(!expression.sameOrderedPopulation())warnings.add("EXPRESSION MATRICES DIFFER IN ORDER/POPULATION/LABELS. Independent source subsets are retained. Required action: inspect both tables; align intended resources before joint visualization or comparison.");
                    }else warnings.add("EXPRESSION OUTPUT SKIPPED: fewer than two query entries occur in the log2 resource, as in the source gate. Required action: inspect query IDs and the declared expression release; supply compatible coverage if expression output is needed.");
                    var universe=new HashSet<>(background);long hits=query.stream().filter(universe::contains).count();session.put("query_entries_in_user_background",hits);session.put("deg_source_gate_opened",hits>1);
                    if(hits>1) {
                        var result=FumaDegEnrichment.test(query,background,sets,multiplicity.equals("error")?FumaDegEnrichment.Multiplicity.ERROR:FumaDegEnrichment.Multiplicity.RETAIN_SOURCE,outside.equals("error")?FumaDegEnrichment.OutsideQuery.ERROR:FumaDegEnrichment.OutsideQuery.RETAIN_SOURCE_SIZE,new FumaDegEnrichment.Limits(maxGenes,maxTerms,maxMembers),check);
                        deg(directory,result);session.put("deg_complete_rows",result.rows().size());session.put("expressed_background_entries",result.expressedBackground().size());session.put("query_outside_expressed_background",result.queryOutsideExpressedBackground());session.put("duplicate_query_entries",result.duplicateQueryEntries());session.put("duplicate_background_entries",result.duplicateBackgroundEntries());
                        long invalid=result.rows().stream().filter(r->!r.finitePopulationModel()).count(),underflow=result.rows().stream().filter(FumaDegEnrichment.Row::underflowedP).count();
                        session.put("deg_non_unique_population_rows",invalid);session.put("deg_underflow_rows",underflow);
                        if(invalid>0||!result.queryOutsideExpressedBackground().isEmpty())warnings.add("SOURCE POPULATION DIAGNOSTIC: source multiplicity and/or full query size outside the expressed background was deliberately retained. These values do not describe sampling unique genes from the declared universe. Required action: inspect deg-tests.tsv and query-outside-background.tsv; correct inputs or explicitly justify this source-count reproduction before biological interpretation.");
                        if(underflow>0)warnings.add("PROBABILITY UNDERFLOW: direct source double P is zero in "+underflow+" rows. Separate finite native natural-log tails are retained. Required action: use log_p/log_adjusted_p; do not report displayed zero as an exact probability.");
                        if(result.rows().isEmpty())warnings.add("EMPTY DEG CORRECTION FAMILIES: no source row matches the literal .up/.down/.twoside markers. Required action: inspect DEG resource names; no enrichment result can be inferred from an empty table.");
                    }else warnings.add("DEG OUTPUT SKIPPED: fewer than two query entries occur in the user background, as in the source gate. Required action: review resolved IDs and background; do not interpret missing DEG output as no enrichment.");
                    check.run();for(var source:sources.entrySet())if(!source.getValue().equals(hash(source.getKey())))throw new IOException("Input changed during expression analysis: "+source.getKey());
                    session.put("status","completed-with-review-warnings");manifest.put("analysis_status","completed-with-review-warnings");
                }catch(Exception failure){code[0]=2;session.put("status","failed");session.put("cause",failure.toString());manifest.put("analysis_status","failed");RnaToolRuntime.quarantine(directory);session.put("retained_files_location","partial/");warnings.add("ANALYSIS FAILED: "+failure.getMessage()+". Required action: inspect partial/, correct the named input/policy/capacity cause and rerun to a new output directory. Retained tables are partial diagnostics and are not an admitted result.");}
                var identities=new LinkedHashMap<String,String>();sources.forEach((p,h)->identities.put(p.toString(),h));manifest.put("input_sha256",identities);session.put("input_sha256",identities);
                Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n");Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",session.get("status")))+"\n");
                Files.writeString(directory.resolve("WARNINGS.md"),(code[0]==0?"# ANALYSIS WARNINGS\n\n":"# ANALYSIS FAILED\n\n")+String.join("\n\n",warnings)+"\n");
            });
            err.println((code[0]==0?"EXPRESSION WARNINGS: ":"EXPRESSION FAILED: ")+Path.of(o.get("out")).toAbsolutePath().resolve("WARNINGS.md"));if(code[0]==0)out.println("Supplied expression/DEG workflow completed: "+o.get("out"));return code[0];
        }catch(Exception failure){err.println("EXPRESSION FAILED: "+failure.getMessage()+"\nRequired action: correct the named option, resource or output path and rerun to a new directory.");return 2;}
    }
    private static Map<String,String> parse(String[] args)throws Exception {
        var direct=options(args,ALLOWED);if(!direct.containsKey("expression-options-file"))return direct;
        Path path=Path.of(direct.get("expression-options-file")).toAbsolutePath().normalize();byte[] raw=AdmixtureInputs.raw(path,1048576);var o=new LinkedHashMap<String,String>();
        for(var e:AdmixtureInputs.decode(raw).entrySet()) {
            String key=e.getKey().toString();if(!ALLOWED.contains(key)||key.equals("expression-options-file")||!(e.getValue() instanceof String value))throw new IOException("Unknown, recursive or non-string expression option: "+key);
            o.put(key,PATHS.contains(key)?path.getParent().resolve(value).normalize().toString():value);
        }o.putAll(direct);o.put("options-sha256",AdmixtureInputs.digest(raw));return o;
    }
    private static List<String> lines(Path path,Map<Path,String> sources,int bytes,FumaDegEnrichment.Check check)throws Exception {
        check.run();path=path.toAbsolutePath().normalize();byte[] raw=AdmixtureInputs.raw(path,bytes);String digest=AdmixtureInputs.digest(raw),prior=sources.putIfAbsent(path,digest);if(prior!=null&&!prior.equals(digest))throw new IOException("Input changed: "+path);
        String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString();if(text.indexOf('\0')>=0)throw new IOException("NUL in expression input: "+path);check.run();return text.lines().toList();
    }
    private static List<String> ids(Path p,Map<Path,String> sources,int bytes,int max,FumaDegEnrichment.Check check)throws Exception {
        var rows=new ArrayList<String>();for(String line:lines(p,sources,bytes,check)){check.run();if(line.isBlank()||!line.equals(line.strip())||line.codePoints().anyMatch(Character::isWhitespace))throw new IOException("Require one exact resolved gene ID per line: "+p);rows.add(line);if(rows.size()>max)throw new IOException("Expression ID row capacity exceeded");}if(rows.isEmpty())throw new IOException("Empty expression ID list");return rows;
    }
    private static List<FumaGeneExpression.Gene> genes(Path p,Map<Path,String> sources,int bytes,int max,FumaDegEnrichment.Check check)throws Exception {
        var lines=lines(p,sources,bytes,check);if(lines.isEmpty()||!lines.get(0).equals("ensg\tsymbol")||lines.size()-1>max)throw new IOException("Symbols require bounded ensg/symbol TSV with exact header");var rows=new ArrayList<FumaGeneExpression.Gene>();for(int i=1;i<lines.size();i++){check.run();String[] f=lines.get(i).split("\t",-1);if(f.length!=2)throw new IOException("Malformed symbol row "+(i+1));rows.add(new FumaGeneExpression.Gene(f[0],f[1]));}return rows;
    }
    private static FumaGeneExpression.Matrix matrix(Path p,Map<Path,String> sources,int bytes,int genes,int labels,int values,FumaDegEnrichment.Check check)throws Exception {
        var lines=lines(p,sources,bytes,check);if(lines.isEmpty()||lines.size()-1>genes)throw new IOException("Empty expression matrix or complete row capacity exceeded");String[] h=lines.get(0).split("\t",-1);if(h.length<3||!h[0].equals("ensg")||h.length-1>labels||(long)(lines.size()-1)*(h.length-1)>values)throw new IOException("Expression matrix requires ensg plus at least two bounded columns and complete value capacity");
        var rows=new ArrayList<FumaGeneExpression.InputRow>();for(int i=1;i<lines.size();i++){check.run();String[] f=lines.get(i).split("\t",-1);if(f.length!=h.length)throw new IOException("Expression matrix width differs at row "+(i+1));var row=new ArrayList<Double>();for(int j=1;j<f.length;j++)row.add(finite(f[j],"expression at row "+(i+1)));rows.add(new FumaGeneExpression.InputRow(f[0],row));}return new FumaGeneExpression.Matrix(Arrays.asList(Arrays.copyOfRange(h,1,h.length)),rows);
    }
    private static List<FumaDegEnrichment.DegSet> sets(Path p,Map<Path,String> sources,int bytes,int terms,int members,FumaDegEnrichment.Check check)throws Exception {
        var lines=lines(p,sources,bytes,check);if(lines.isEmpty()||lines.size()>terms)throw new IOException("Empty DEG table or complete term capacity exceeded");var rows=new ArrayList<FumaDegEnrichment.DegSet>();long count=0;for(String line:lines){check.run();String[] f=line.split("\t",-1);if(f.length!=3)throw new IOException("DEG rows require exactly name/declared-count/colon-members without a header");List<String> ids=f[2].isEmpty()?List.of():Arrays.asList(f[2].split(":",-1));count+=ids.size();if(count>members)throw new IOException("Complete DEG membership capacity exceeded");rows.add(new FumaDegEnrichment.DegSet(f[0],Long.parseLong(f[1]),ids));}return rows;
    }
    private static void expression(Path path,List<String> labels,List<FumaGeneExpression.Row> rows)throws IOException {
        var header=new ArrayList<String>(List.of("ensg","symbol"));header.addAll(labels);var output=new ArrayList<String[]>();for(var row:rows){var fields=new ArrayList<String>(List.of(row.gene(),row.symbol()));for(double v:row.values())fields.add(Double.toString(v));output.add(fields.toArray(String[]::new));}table(path,header,output);
    }
    private static void deg(Path dir,FumaDegEnrichment.Result result)throws IOException {
        var source=new ArrayList<String[]>();var audit=new ArrayList<String[]>();for(var r:result.rows()) {
            source.add(new String[]{r.category().sourceName(),r.label(),""+r.setSize(),""+r.overlap(),""+r.pValue(),""+r.adjustedPValue(),String.join(":",r.overlapGenes())});
            audit.add(new String[]{r.category().sourceName(),""+r.sourceRow(),r.sourceName(),""+r.backgroundSize(),""+r.querySize(),""+r.declaredSize(),""+r.inputMembers(),""+r.setSize(),""+r.duplicateSetMembers(),""+r.overlap(),""+r.familyTests(),""+r.pValue(),""+r.logPValue(),""+r.adjustedPValue(),""+r.logAdjustedPValue(),""+r.finitePopulationModel(),""+r.underflowedP()});
        }table(dir.resolve("DEG.txt"),List.of("Category","GeneSet","N_genes","N_overlap","p","adjP","genes"),source);
        table(dir.resolve("deg-tests.tsv"),List.of("category","source_row","source_name","background_size","query_size","declared_size","input_members","set_size","duplicate_set_members","overlap","family_tests","p","log_p","adjusted_p","log_adjusted_p","finite_population_model","underflowed_p"),audit);
        table(dir.resolve("expressed-background.tsv"),List.of("ensg"),result.expressedBackground().stream().map(g->new String[]{g}).toList());table(dir.resolve("query-outside-background.tsv"),List.of("ensg"),result.queryOutsideExpressedBackground().stream().map(g->new String[]{g}).toList());
    }
    static String help(){return """
        fuma-tools --method expression --expression-options-file OPTIONS.json
        Or supply all typed options directly:
          --build BUILD --annotation-id RELEASE --expression-id RELEASE --deg-id RELEASE
          --symbols ensg-symbol.tsv --query resolved-query.txt --background resolved-background.txt
          --log2 precomputed-log2.tsv --norm precomputed-normalized.tsv --deg-sets supplied-DEG.txt
          --duplicates error|original-count --outside-query error|source-size --out NEW_DIRECTORY
        Query/background: exact resolved Ensembl IDs, one per line; no ID conversion.
        Symbols: exact ensg/symbol header. Matrices: ensg then >=2 distinct labels;
          finite values, distinct row IDs, resource order retained without transformation.
        DEG: headerless name/declared-count/colon-members TSV, including exact all row.
        Source query size is retained after background intersection only with source-size.
        Separate up/down/twoside Bonferroni families; all rows retained without selection.
        Options-file scalar values must be strings; paths are relative to that file.
        Limits: --max-bytes 268435456 --max-genes 1000000 --max-labels 10000
          --max-values 20000000 --max-terms 100000 --max-memberships 10000000 --timeout 300
        Read WARNINGS.md, session.json, deg-tests.tsv and both expression matrices.
        Exit 0: complete workflow with review warnings, possibly source-gated omissions;
          2: failed; retained partial tables are diagnostics. Existing outputs are preserved.
        Java only; no external software or automatic installation. This does not perform
          DEG generation, heatmap clustering, cell-type MAGMA or hosted FUMA analysis.
        """;}
}
