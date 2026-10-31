/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.enrichment.FumaMagmaReport;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed native report postprocessing; original MAGMA fitting is separate. */
final class FumaMagmaReportCli {
    private FumaMagmaReportCli() { }
    private static final Set<String> ALLOWED=Set.of("method","magma-report-options-file","build","annotation-id","analysis-id","gene-results","symbols","gsa","sets","sets-format","duplicates","missing-p","placeholders","max-bytes","max-rows","max-line-bytes","max-cells","max-snapshot-bytes","max-output-bytes","max-output-rows","max-operations","timeout","out");
    private static final Set<String> PATHS=Set.of("gene-results","symbols","gsa","sets","out");
    private static final List<String> REQUIRED=List.of("build","annotation-id","analysis-id","gene-results","symbols","gsa","sets","sets-format","duplicates","missing-p","placeholders","out");
    private static final String SCOPE="SUPPLIED MAGMA GENE/SET REPORT ONLY. Java joins exact gene symbols and reproduces the pinned source report. It does not fit or certify MAGMA models, generate annotations, reconstruct historical resources or infer a paper's correction policy. Pbon is the original name for Holm-adjusted P, not Bonferroni. Required action: verify assembly, exact gene namespace, original model/results and the complete correction family; inspect every source hash, gene join, full test and display-row status before interpreting results. Cell Ranger remains excluded.";
    static int run(String[] args,PrintStream stdout,PrintStream stderr) {
        if(Arrays.asList(args).contains("--help")){stdout.println(help());return 0;}
        try {
            var options=parse(args);if(options.containsKey("method")&&!options.get("method").equals("magma-report"))throw new IOException("--method must be magma-report");for(String key:REQUIRED)required(options,key);int[] code={0};
            externalTransaction(options,"fuma-magma-report",(directory,manifest)->{
                var warnings=new ArrayList<String>();warnings.add(SCOPE);var session=new LinkedHashMap<String,Object>();session.put("source_commit","bdfbf06b5dd24037ec3c7497d773fad3c2656150");session.put("source_magma_gene_R_sha256","33d7c07dd6c5fbe7d7f871fd973f347f6b083d417193bc5939122c3028f4790d");session.put("external_runtime_required",false);FumaMagmaReportInputs input=null;String phase="options and supplied input preparation";
                try {
                    for(String key:List.of("build","annotation-id","analysis-id")){String value=options.get(key);if(value.isBlank()||value.length()>512||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Require bounded printable MAGMA report identity: "+key);session.put(key,value);}if(!options.get("build").matches("[A-Za-z0-9_.-]{1,80}"))throw new IOException("Declare an explicit simple report build; no inference/liftover");
                    String duplicate=AdmixtureInputs.choice(options,"duplicates",Set.of("error","source-first")),missing=AdmixtureInputs.choice(options,"missing-p",Set.of("error","source-na")),placeholder=AdmixtureInputs.choice(options,"placeholders",Set.of("error","source-rows")),format=AdmixtureInputs.choice(options,"sets-format",Set.of("tsv","magma-skip3"));
                    boolean gsa=!options.get("gsa").equals("none");if(!gsa&&options.get("sets").equals("none"))throw new IOException("Supply gsa or sets results; both cannot be none");String selected=gsa?"gsa":"sets";session.put("selected_set_source",selected);session.put("ignored_sets_fallback",gsa&&!options.get("sets").equals("none"));for(String key:List.of("sets-format","duplicates","missing-p","placeholders"))session.put(key,options.get(key));
                    long started=System.nanoTime(),timeout=integer(options,"timeout",300,1,86400);Runnable check=()->{if(Thread.currentThread().isInterrupted()||System.nanoTime()-started>timeout*1000000000L)throw new IllegalStateException("MAGMA report interrupted or --timeout exceeded; correct complete populations/limits and rerun to a NEW output");};
                    var limits=new FumaMagmaReportInputs.Limits(integer(options,"max-bytes",268435456,1,1073741824),integer(options,"max-rows",1000000,1,10000000),integer(options,"max-line-bytes",1048576,64,16777216),whole(options,"max-cells",50000000,500000000),whole(options,"max-snapshot-bytes",1073741824,1099511627776L),whole(options,"max-output-bytes",1073741824,1099511627776L));input=new FumaMagmaReportInputs(directory,limits,check);
                    if(options.containsKey("magma-report-options-file")){byte[] carrier=input.raw(Path.of(options.get("magma-report-options-file")),"options-carrier",1048576);if(!AdmixtureInputs.digest(carrier).equals(options.get("options-sha256")))throw new IOException("MAGMA report options carrier changed after parsing");}
                    var genes=input.read(Path.of(options.get("gene-results")),"supplied-gene-results",0);var annotation=input.read(Path.of(options.get("symbols")),"supplied-gene-symbols",0);int id=annotation.columns().indexOf("ensembl_gene_id"),symbol=annotation.columns().indexOf("external_gene_name");if(id<0||symbol<0)throw new IOException("Symbols require ensembl_gene_id and external_gene_name columns");var resource=new ArrayList<FumaMagmaReport.Gene>();for(var row:annotation.rows()){check.run();resource.add(new FumaMagmaReport.Gene(row.get(id),row.get(symbol)));}
                    var sets=input.read(Path.of(options.get(selected)),"supplied-"+selected+"-set-results",format.equals("magma-skip3")?3:0);input.verify();phase="native complete Holm family and source report";
                    var result=FumaMagmaReport.report(genes,resource,sets,duplicate.equals("error")?FumaMagmaReport.Duplicates.ERROR:FumaMagmaReport.Duplicates.SOURCE_FIRST,missing.equals("error")?FumaMagmaReport.MissingP.ERROR:FumaMagmaReport.MissingP.SOURCE_NA,placeholder.equals("error")?FumaMagmaReport.Placeholders.ERROR:FumaMagmaReport.Placeholders.SOURCE_ROWS,new FumaMagmaReport.Limits(limits.rows(),limits.cells(),whole(options,"max-output-rows",10000000,100000000),whole(options,"max-operations",100000000,10000000000L)),check::run);
                    phase="complete report publication";var writer=input.output;writer.table(directory.resolve("genes.tsv"),result.genes());writer.table(directory.resolve("sets-all.tsv"),result.allSets());writer.table(directory.resolve("sets-top.tsv"),result.displayedSets());
                    writer.table(directory.resolve("gene-joins.tsv"),List.of("source_row","gene","annotation_row","symbol","status"),result.geneJoins().size(),i->{var row=result.geneJoins().get(i);return new String[]{""+row.sourceRow(),row.gene(),""+row.annotationRow(),row.symbol()==null?"NA":row.symbol(),row.status()};});
                    writer.table(directory.resolve("full-tests.tsv"),List.of("source_row","P","Pbon","significant","adjustment_family_size"),result.allTests().size(),i->{var row=result.allTests().get(i);return new String[]{""+row.sourceRow(),row.p()==null?"NA":row.p().toString(),row.holm()==null?"NA":row.holm().toString(),""+row.significant(),""+result.correctionFamilySize()};});
                    writer.table(directory.resolve("display-rows.tsv"),List.of("display_row","source_row","status"),result.displayRows().size(),i->{var row=result.displayRows().get(i);return new String[]{""+row.displayRow(),""+row.sourceRow(),row.status()};});input.verify();check.run();
                    session.put("gene_rows",result.genes().rows().size());session.put("annotation_rows",resource.size());session.put("set_rows",result.allSets().rows().size());session.put("finite_adjustment_family",result.correctionFamilySize());session.put("missing_P_rows",result.missingPValues());session.put("significant_sets",result.significantSets());session.put("display_branch",result.displayBranch());session.put("display_rows",result.displayRows().size());session.put("placeholder_rows",result.placeholderRows());session.put("operations",result.operations());session.put("input_cells",input.cells());session.put("snapshot_bytes",input.snapshotBytes());session.put("scientific_files_and_snapshot_bytes",writer.bytes());
                    warnings.add("HOLM DESPITE Pbon NAME: the source uses default R p.adjust(P), meaning Holm, with n evaluated after missing-P removal. The family includes every finite supplied set row before display selection. Pbon is not Bonferroni, BH-FDR, or a paper-specific adjustment across traits/datasets. Required action: inspect full-tests.tsv, all input rows and the declared analysis family; do not reinterpret this source report as the paper's chosen inference.");
                    if(result.placeholderRows()>0)warnings.add("SOURCE REPORT PLACEHOLDERS: "+result.placeholderRows()+" all-missing rows arise from top-ten padding or significant-branch NA logical indices. These are not fitted/tested sets. Required action: use display-rows.tsv to distinguish every placeholder from original rows and inspect sets-all.tsv/full-tests.tsv before interpreting the abbreviated report.");
                    if(result.missingPValues()>0)warnings.add("MISSING P FAMILY: "+result.missingPValues()+" original rows have NA P and are excluded from the Holm family. Required action: inspect the original model/coverage and missing-P policy; missing tests are not null findings.");
                    if(result.correctionFamilySize()==0)warnings.add("NO FINITE SET TESTS: this is a supplied missing-value report with zero statistical evidence. Required action: supply valid original tested results or keep interpretation unperformed; all-NA rows do not establish lack of enrichment.");
                    if(result.allTests().stream().anyMatch(row->row.p()!=null&&row.p()==0))warnings.add("ZERO/UNDERFLOW P: supplied numeric P includes exact or rounded/underflow zero. Required action: inspect original precision and logs; reporting a zero does not establish an exact probability.");
                    if(result.missingSymbols()>0||result.emptySymbols()>0||result.duplicateAnnotationRows()>0)warnings.add("SYMBOL RESOURCE REVIEW: missing symbols="+result.missingSymbols()+", empty symbols="+result.emptySymbols()+", repeated annotation rows="+result.duplicateAnnotationRows()+". First matched annotation rows are retained; unmatched/missing symbols are NA and empty symbols stay empty. Required action: inspect gene-joins.tsv and source snapshots; no gene-ID normalization, fallback ID or alternate symbol is inferred.");
                    if(gsa&&!options.get("sets").equals("none"))warnings.add("DECLARED GSA PREFERENCE: gsa is selected; the declared sets fallback is unused, unparsed and not snapshotted. Required action: inspect selected_set_source and the exact selected input hash. To use fallback alone declare gsa=none. A missing explicitly declared gsa input fails rather than silently substituting a different analysis.");
                    manifest.put("analysis_status","completed-with-review-warnings");session.put("status","completed-with-review-warnings");
                }catch(Exception failure){code[0]=2;manifest.put("analysis_status","failed");manifest.put("error",failure.toString());session.put("status","failed");session.put("cause",failure.toString());session.put("retained_files_location","partial/");session.put("failed_phase",phase);if(input!=null)session.put("last_input_context",input.context());RnaToolRuntime.quarantine(directory);warnings.add(0,"MAGMA REPORT FAILED during "+phase+": "+failure.getMessage()+". Required action: inspect the named input/resource/capacity cause and partial/ diagnostics; correct it and rerun to a NEW output directory.");}
                var hashes=new LinkedHashMap<String,String>();if(input!=null)input.hashes.forEach((p,h)->hashes.put(p.toString(),h));manifest.put("source_sha256",hashes);session.put("source_sha256",hashes);session.put("warnings",warnings);Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n");Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status"),"exit_code",code[0]))+"\n");Files.writeString(directory.resolve("WARNINGS.md"),"# "+(code[0]==0?"MAGMA REPORT REVIEW REQUIRED":"MAGMA REPORT FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: review source/input/model identities and every full-test/display/symbol ledger, correct failures and rerun to a NEW output. Existing outputs remain intact. Supplied postprocessing requires Java only. If required upstream MAGMA software cannot be installed/used where permitted, SKIP that affected fitting analysis and supply valid permitted original outputs; this command does not install or substitute fits.\n");
            });stderr.println((code[0]==0?"MAGMA REPORT REVIEW REQUIRED: ":"MAGMA REPORT FAILED: ")+Path.of(options.get("out"),"WARNINGS.md"));if(code[0]==0)stdout.println("Supplied MAGMA report completed: "+options.get("out"));return code[0];
        }catch(Exception failure){stderr.println("MAGMA REPORT FAILED: "+failure.getMessage()+"\nRequired action: correct the named options/path/output cause and rerun to a NEW output directory. Existing outputs remain intact.\n"+SCOPE);return 2;}
    }
    private static long whole(Map<String,String> options,String key,long fallback,long maximum){String value=options.getOrDefault(key,""+fallback);if(!value.matches("[0-9]+"))throw new IllegalArgumentException("Require positive integer --"+key);long number=Long.parseLong(value);if(number<1||number>maximum)throw new IllegalArgumentException("Unsupported --"+key+" capacity");return number;}
    private static Map<String,String> parse(String[] args)throws Exception {
        var direct=options(args,ALLOWED);if(!direct.containsKey("magma-report-options-file"))return direct;Path path=Path.of(direct.get("magma-report-options-file")).toAbsolutePath().normalize();byte[] bytes=FumaMagmaReportInputs.bounded(path,1048576,"options-carrier");var result=new LinkedHashMap<String,String>();
        for(var entry:AdmixtureInputs.decode(bytes).entrySet()){String key=entry.getKey().toString();if(!ALLOWED.contains(key)||key.equals("magma-report-options-file")||!(entry.getValue() instanceof String value))throw new IOException("Unknown, recursive or non-string MAGMA report option: "+key);result.put(key,PATHS.contains(key)&&!((key.equals("gsa")||key.equals("sets"))&&value.equals("none"))?path.getParent().resolve(value).normalize().toString():value);}result.putAll(direct);result.put("options-sha256",AdmixtureInputs.digest(bytes));return result;
    }
    static String help(){return """
        fuma-tools --method magma-report --magma-report-options-file OPTIONS.json
        Required: --build BUILD --annotation-id RELEASE --analysis-id MODEL_RESULTS_ID
          --gene-results GENES.tsv --symbols SYMBOLS.tsv --gsa PATH|none --sets PATH|none
          --sets-format tsv|magma-skip3 --duplicates error|source-first
          --missing-p error|source-na --placeholders error|source-rows --out NEW_DIRECTORY
        Gene results require GENE; existing SYMBOL is overwritten in place. Symbols need
          ensembl_gene_id/external_gene_name. Empty and NA symbols remain distinct.
        Set results need FULL_NAME,NGENES,BETA,BETA_STD,SE,P. magma-skip3 skips exactly
          three initial # comment lines. TSV fields preserve raw supplied formatting.
          Declared gsa takes precedence; fallback sets is unused/unparsed. Missing declared
          gsa fails; to select fallback explicitly use gsa=none. Both none fails.
        Source Pbon is Holm, not Bonferroni: missing P is excluded from R's default family.
          Adjust the complete finite family before P sorting/display. Strict adjusted P<0.05
          selects all significant when at least10; otherwise display top10 including
          explicit all-NA padding. Source NA logical indices can add all-NA report rows.
          Source placeholders are not tests. Empty populations and invalid P fail.
        Bounds: --max-bytes 268435456 per file --max-rows 1000000 per table
          --max-line-bytes 1048576 --max-cells 50000000 --max-snapshot-bytes 1073741824
          --max-output-bytes 1073741824 scientific files plus exact raw snapshots
          --max-output-rows 10000000 across six result tables/ledgers
          --max-operations 100000000 --timeout 300 seconds; periodic checks, not OS quotas.
        JSON option values are strings; paths resolve relative to the carrier. On Windows
          use an ASCII carrier path with Unicode resource/output paths inside. Exit0 needs
          review; failures2 retain partial/ diagnostics. Java-only postprocessing; upstream
          MAGMA fitting is separate. Unavailable/forbidden fitting software means SKIP that
          affected upstream analysis; supply permitted valid results without invented fits.
        """+SCOPE;}
}
