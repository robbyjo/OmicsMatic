/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
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
import org.jlinalg.enrichment.FumaGeneSetEnrichment;
import static org.jlinalg.cli.FollowupSupport.*;

/** Native FUMA GENE2FUNC gene-set tests with explicit local reference resources. */
final class FumaToolsCli {
    private FumaToolsCli() { }
    private static final Set<String> ALLOWED=Set.of("method","annotations","query","background","biotypes",
        "gmt-manifest","exclude-mhc","correction","adjusted-cutoff","min-overlap","duplicates",
        "annotation-id","max-bytes","max-genes","max-terms","max-memberships","max-hommel-operations","out");
    private static final String SCOPE="NATIVE GENE-SET MODULE ONLY: this implements the supplied-annotation/GMT GENE2FUNC gene-set test. SNP2GENE, LD clumping, MAGMA, variant annotation, DEG, expression and hosted FUMA workflows remain separate. Required action: provide the intended annotation and every GMT correction family, and retain their identities and hashes; no historical paper data or defaults are inferred.";
    static int run(String[] args,PrintStream out,PrintStream err) {
        for (int i=0;i<args.length;i++) if (args[i].equals("--site-options-file")||args[i].equals("--method")&&i+1<args.length&&args[i+1].equals("transcript-sites")) return TranscriptSiteCli.run(args,out,err);
        for (int i=0;i<args.length;i++) if (args[i].equals("--fisher-options-file")||args[i].equals("--method")&&i+1<args.length&&args[i+1].equals("fisher-enrichment")) return FumaFisherCli.run(args,out,err);
        for (int i=0;i<args.length;i++) if (args[i].equals("--known-options-file")||args[i].equals("--method")&&i+1<args.length&&args[i+1].equals("known-associations")) return FumaAssociationCli.run(args,out,err);
        for (int i=0;i<args.length;i++) if (args[i].equals("--novelty-options-file")||args[i].equals("--method")&&i+1<args.length&&args[i+1].equals("locus-novelty")) return FumaNoveltyCli.run(args,out,err);
        for (int i=0;i<args.length;i++) if (args[i].equals("--magma-report-options-file")||args[i].equals("--method")&&i+1<args.length&&args[i+1].equals("magma-report")) return FumaMagmaReportCli.run(args,out,err);
        for (int i=0;i<args.length;i++) if (args[i].equals("--annotation-options-file")||args[i].equals("--method")&&i+1<args.length&&args[i+1].equals("annotate-variants")) return FumaAnnotationCli.run(args,out,err);
        for (int i=0;i<args.length;i++) if (args[i].equals("--celltype-options-file")||args[i].equals("--method")&&i+1<args.length&&args[i+1].equals("cell-type")) return FumaCelltypeCli.run(args,out,err);
        for (int i=0;i<args.length;i++) if (args[i].equals("--expression-options-file")||args[i].equals("--method")&&i+1<args.length&&args[i+1].equals("expression")) return FumaExpressionCli.run(args,out,err);
        for (int i=0;i<args.length;i++) if (args[i].equals("--workflow-options-file")||args[i].equals("--method")&&i+1<args.length&&args[i+1].equals("workflow")) return FumaWorkflowCli.run(args,out,err);
        for (int i=0;i<args.length;i++) if (args[i].equals("--mapping-options-file")||args[i].equals("--method")&&i+1<args.length&&args[i+1].equals("map-genes")) return FumaMappingCli.run(args,out,err);
        for (int i=0;i<args.length;i++) if (args[i].equals("--locus-options-file")||args[i].equals("--method")&&i+1<args.length&&args[i+1].equals("loci")) return FumaLociCli.run(args,out,err);
        if (Arrays.asList(args).contains("--help")) {out.println(help());return 0;}
        try {
            Map<String,String> o=options(args,ALLOWED);
            if (!required(o,"method").equals("gene-set")) throw new IllegalArgumentException("--method must be gene-set");
            for (String key:List.of("annotations","query","gmt-manifest","exclude-mhc","correction","adjusted-cutoff","min-overlap","duplicates","annotation-id","out")) required(o,key);
            if (o.containsKey("background")==o.containsKey("biotypes")) throw new IllegalArgumentException("Supply exactly one of --background and --biotypes");
            int[] code={0};transaction(o,"fuma-gene-set",(directory,manifest)->{
                Map<String,Object> session=new LinkedHashMap<>();Map<Path,String> sources=new LinkedHashMap<>();List<String> warnings=new ArrayList<>();warnings.add(SCOPE);
                try {
                    int maxBytes=integer(o,"max-bytes",268435456,1,1073741824),maxGenes=integer(o,"max-genes",1000000,2,2000000);
                    int maxTerms=integer(o,"max-terms",100000,1,1000000),maxMembers=integer(o,"max-memberships",5000000,1,20000000);
                    long maxHommel=Long.parseLong(o.getOrDefault("max-hommel-operations","100000000"));
                    if (maxHommel<1) throw new IllegalArgumentException("--max-hommel-operations must be positive");
                    var correction=FumaGeneSetEnrichment.Adjustment.parse(o.get("correction"));
                    String duplicate=o.get("duplicates");
                    if (!Set.of("error","original-count").contains(duplicate)) throw new IllegalArgumentException("--duplicates must be error or original-count");
                    double cutoff=number(o,"adjusted-cutoff",.05);int overlap=integer(o,"min-overlap",2,0,maxGenes);
                    if (cutoff<0 || cutoff>1) throw new IllegalArgumentException("--adjusted-cutoff must be in [0,1]");
                    boolean mhc=bool(o,"exclude-mhc",false);
                    List<FumaGeneSetEnrichment.GeneRow> annotation=annotations(Path.of(o.get("annotations")),sources,maxBytes,maxGenes);
                    List<String> query=ids(Path.of(o.get("query")),sources,maxBytes,maxGenes);
                    List<String> background=o.containsKey("background")?ids(Path.of(o.get("background")),sources,maxBytes,maxGenes):null;
                    Set<String> biotypes=null;
                    if (o.containsKey("biotypes")) {
                        String[] values=o.get("biotypes").split(":",-1);biotypes=new HashSet<>();
                        for (String value:values) if (value.isBlank() || !biotypes.add(value)) throw new IllegalArgumentException("--biotypes requires distinct nonempty colon-separated annotation biotypes");
                    }
                    var data=FumaGeneSetEnrichment.prepare(annotation,query,background,biotypes,mhc);
                    var families=families(Path.of(o.get("gmt-manifest")),sources,maxBytes,maxTerms,maxMembers);
                    var results=FumaGeneSetEnrichment.test(data,families,correction,cutoff,overlap,duplicate.equals("error")?FumaGeneSetEnrichment.Duplicates.ERROR:FumaGeneSetEnrichment.Duplicates.ORIGINAL_COUNT,maxHommel);
                    session.put("annotation_id",o.get("annotation-id"));session.put("query_namespace",data.queryNamespace());session.put("background_namespace",data.backgroundNamespace());session.put("mhc_removed_annotation_rows",data.mhcRemovedRows());session.put("universe_size",data.universe().size());session.put("selected_size",data.selected().size());session.put("correction",correction.id());session.put("two_stage_alpha",.05);session.put("gmt_family_count",families.size());session.put("tested_rows",results.size());session.put("retained_rows",results.stream().filter(FumaGeneSetEnrichment.Result::retained).count());
                    List<String[]> mappings=new ArrayList<>();
                    Set<String> finalUniverse=new HashSet<>(data.universe());
                    for (String role:List.of("query","background")) for (var mapping:role.equals("query")?data.queryMapping():data.backgroundMapping()) {
                        var retained=mapping.entrez().stream().filter(finalUniverse::contains).toList();
                        mappings.add(new String[]{role,mapping.input(),mapping.upperInput(),mapping.namespace(),String.join(":",mapping.entrez()),String.join(":",retained),retained.isEmpty()?"dropped":"retained"});
                    }
                    table(directory.resolve("id-mapping.tsv"),List.of("role","input","upper_input","global_namespace","mapped_entrez","in_final_background","disposition"),mappings);
                    table(directory.resolve("universe.tsv"),List.of("entrez"),data.universe().stream().map(v->new String[]{v}).toList());
                    table(directory.resolve("selected.tsv"),List.of("entrez"),data.selected().stream().map(v->new String[]{v}).toList());
                    List<String[]> shown=new ArrayList<>(),audit=new ArrayList<>();long repeated=0,underflow=0;
                    for (var r:results) {
                        if (r.retained()) shown.add(new String[]{r.category(),r.name(),Integer.toString(r.setSize()),Integer.toString(r.overlap()),Double.toString(r.pValue()),Double.toString(r.adjustedPValue()),String.join(":",r.genes()),r.link()});
                        audit.add(new String[]{r.category(),Integer.toString(r.row()),r.name(),Integer.toString(r.universeSize()),Integer.toString(r.selectedSize()),Integer.toString(r.setSize()),Integer.toString(r.overlap()),Integer.toString(r.duplicateMembers()),Double.toString(r.pValue()),Double.toString(r.logPValue()),Double.toString(r.adjustedPValue()),Double.toString(r.logAdjustedPValue()),Boolean.toString(r.retained())});
                        repeated+=r.duplicateMembers();if (r.pValue()==0 || r.adjustedPValue()==0) underflow++;
                    }
                    table(directory.resolve("GS.txt"),List.of("Category","GeneSet","N_genes","N_overlap","p","adjP","genes","link"),shown);
                    table(directory.resolve("all-tests.tsv"),List.of("category","gmt_row","term","universe_size","selected_size","set_size","overlap","duplicate_background_members","p","log_p","adjusted_p","log_adjusted_p","retained"),audit);
                    if (repeated>0) warnings.add("DUPLICATE GMT MEMBERS COUNTED: "+repeated+" repeated background memberships contribute to set sizes as in the original Python backend. Required action: inspect all-tests.tsv and confirm this deliberate source-count policy; choose --duplicates error to require unique effective memberships.");
                    if (underflow>0) warnings.add("PROBABILITY UNDERFLOW: "+underflow+" rows have a displayed probability rounded to zero. Finite natural-log values remain in all-tests.tsv. Required action: use those log values and do not report rounded zero as an exact probability.");
                    warnings.add("NUMERICAL AND INPUT SCOPE: Java uses log-space probability/correction formulas; tiny floating-point differences from legacy SciPy/statsmodels can affect a strict cutoff at its rounding boundary. Equal-P rows keep GMT row order. Annotation rows require complete positive Entrez IDs and valid coordinates. Required action: review id-mapping.tsv, all-tests.tsv and the declared correction family, especially mixed namespaces, MHC exclusion and threshold-boundary decisions; do not silently replace an invalid annotation table or deduplicate GMT members.");
                    Map<String,String> hashes=new LinkedHashMap<>();
                    for (var e:sources.entrySet()) {if (!hash(e.getKey()).equals(e.getValue())) throw new IOException("Input changed during analysis: "+e.getKey());hashes.put(e.getKey().toString(),e.getValue());}
                    session.put("source_sha256",hashes);manifest.put("input_sha256",hashes);manifest.put("analysis_status","completed-with-review-warnings");
                } catch (Exception failure) {
                    code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.toString());RnaToolRuntime.quarantine(directory);session.put("retained_files_location","partial/");
                    warnings.add(0,"ANALYSIS FAILED: "+failure.getMessage()+". Required action: inspect session.json and partial/ audits, correct the named input, parameter or resource-budget problem, and rerun to a new output directory. No completed result table is admitted.");
                }
                session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n");Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n");Files.writeString(directory.resolve("WARNINGS.md"),"# FUMA "+(code[0]==0?"GENE-SET WARNINGS REQUIRE REVIEW":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n");
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":"ANALYSIS FAILED: ")+Path.of(o.get("out"),"WARNINGS.md"));if (code[0]==0) out.println("Native FUMA gene-set module completed: "+o.get("out"));return code[0];
        } catch (Exception failure) {err.println("ANALYSIS FAILED: "+failure.getMessage()+"\nRequired action: correct the named option or file and select a new writable --out directory.\n"+SCOPE);return 2;}
    }
    private static List<String> lines(Path path,Map<Path,String> sources,int maxBytes) throws Exception {
        path=path.toAbsolutePath().normalize();byte[] raw=AdmixtureInputs.raw(path,maxBytes);String digest=AdmixtureInputs.digest(raw);
        String old=sources.putIfAbsent(path,digest);if (old!=null && !old.equals(digest)) throw new IOException("Input changed during analysis: "+path);
        String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString();List<String> result=text.lines().toList();
        for (String line:result) if (line.length()>1048576 || line.indexOf('\0')>=0) throw new IOException("Oversized or NUL-containing input line: "+path);
        return result;
    }
    private static List<String> ids(Path path,Map<Path,String> sources,int maxBytes,int maxGenes) throws Exception {
        List<String> values=new ArrayList<>();
        for (String line:lines(path,sources,maxBytes)) {String value=line.strip();if (!value.isEmpty()) values.add(value);if (values.size()>maxGenes) throw new IOException("ID row limit exceeded: "+path);}
        if (values.isEmpty()) throw new IOException("Empty ID list: "+path);return values;
    }
    private static List<FumaGeneSetEnrichment.GeneRow> annotations(Path path,Map<Path,String> sources,int maxBytes,int maxGenes) throws Exception {
        List<String> input=lines(path,sources,maxBytes);if (input.isEmpty()) throw new IOException("Empty annotation table");
        List<String> header=Arrays.asList(input.get(0).split("\t",-1));
        if (new HashSet<>(header).size()!=header.size()) throw new IOException("Duplicate annotation columns");
        String[] required={"ensembl_gene_id","entrezID","external_gene_name","gene_biotype","chromosome_name","start_position","end_position"};int[] columns=new int[required.length];
        for (int i=0;i<required.length;i++) {columns[i]=header.indexOf(required[i]);if (columns[i]<0) throw new IOException("Missing annotation column: "+required[i]);}
        List<FumaGeneSetEnrichment.GeneRow> rows=new ArrayList<>();
        for (int i=1;i<input.size();i++) {
            String[] f=input.get(i).split("\t",-1);if (f.length!=header.size()) throw new IOException("Annotation row width differs at row "+(i+1));
            try {rows.add(new FumaGeneSetEnrichment.GeneRow(f[columns[0]],f[columns[1]],f[columns[2]],f[columns[3]],f[columns[4]],Long.parseLong(f[columns[5]]),Long.parseLong(f[columns[6]])));}
            catch (IllegalArgumentException invalid) {throw new IOException("Invalid annotation row "+(i+1)+": "+invalid.getMessage()+"; explicitly clean missing/NA Entrez IDs and coordinates before running",invalid);}
            if (rows.size()>maxGenes) throw new IOException("Annotation row limit exceeded");
        }
        return rows;
    }
    private static List<FumaGeneSetEnrichment.Family> families(Path manifest,Map<Path,String> sources,int maxBytes,int maxTerms,int maxMembers) throws Exception {
        manifest=manifest.toAbsolutePath().normalize();List<String> list=lines(manifest,sources,maxBytes);
        if (list.isEmpty() || !list.get(0).equals("path")) throw new IOException("GMT manifest requires a single path header");
        List<FumaGeneSetEnrichment.Family> families=new ArrayList<>();Set<String> categories=new HashSet<>();int terms=0,members=0;
        for (int i=1;i<list.size();i++) {
            String name=list.get(i);if (name.isBlank() || name.indexOf('\t')>=0) throw new IOException("Invalid GMT manifest row "+(i+1));
            Path path=manifest.getParent().resolve(name).normalize();if (!path.getFileName().toString().endsWith(".gmt")) throw new IOException("GMT path must end in .gmt");
            String category=path.getFileName().toString().replace(".gmt","");if (!categories.add(category)) throw new IOException("Duplicate GMT category: "+category);
            List<FumaGeneSetEnrichment.Term> rows=new ArrayList<>();
            for (String line:lines(path,sources,maxBytes)) {
                String[] f=line.strip().split("\t",-1);if (f.length<3) {rows.add(new FumaGeneSetEnrichment.Term(f[0].isBlank()?"ignored-empty-row":f[0],"",List.of()));continue;}
                if (++terms>maxTerms) throw new IOException("GMT term limit exceeded");members+=f.length-2;if (members>maxMembers) throw new IOException("GMT membership limit exceeded");
                rows.add(new FumaGeneSetEnrichment.Term(f[0],f[1],Arrays.asList(Arrays.copyOfRange(f,2,f.length))));
            }
            families.add(new FumaGeneSetEnrichment.Family(category,rows));
            if (families.size()>10000) throw new IOException("GMT file limit exceeded");
        }
        return families;
    }
    private static String help() {return """
        Native FUMA GENE2FUNC gene-set module (local files; no Python or FUMA account).
          fuma-tools --method gene-set --annotations genes.tsv --query selected.txt
            (--background background.txt | --biotypes protein_coding:lncRNA | --biotypes all)
            --gmt-manifest collections.tsv --annotation-id DECLARED_RELEASE
            --exclude-mhc true|false --correction METHOD --adjusted-cutoff 0.05
            --min-overlap 2 --duplicates error|original-count --out NEW_DIRECTORY
        METHOD: bonferroni, sidak, holm-sidak, holm, simes-hochberg, hommel,
                fdr_bh, fdr_by, fdr_tsbh, fdr_tsbky.
        Required annotation headers: ensembl_gene_id, entrezID, external_gene_name,
          gene_biotype, chromosome_name, start_position, end_position (plain TSV).
        Query/background: headerless, one ID per line. IDs use the original global
          symbol > Ensembl > Entrez priority, independently for each input list.
        GMT manifest: single column path; paths relative to manifest; each .gmt file
          defines a correction family. Include every intended predefined/custom file.
        Corrections precede display filters. Two-stage alpha is fixed at 0.05;
          display uses strict adjusted-P < cutoff. At least two selected genes required.
        Optional limits: --max-bytes 268435456 --max-genes 1000000 --max-terms 100000
          --max-memberships 5000000 --max-hommel-operations 100000000.
        Read WARNINGS.md, session.json, id-mapping.tsv and all-tests.tsv in every run.
        Exit 0: completed with review warnings; 2: failed. Existing outputs are preserved.
        Annotation acquisition, expression generation and hosted FUMA reconstruction remain unavailable.
        Supplied-reference locus construction: fuma-tools --method loci --help
        Supplied-resource positional/eQTL mapping: fuma-tools --method map-genes --help
        Typed local stage orchestration: fuma-tools --method workflow --help
        Supplied expression/DEG resources: fuma-tools --method expression --help
        Classic cell-type original MAGMA workflow: fuma-tools --method cell-type --help
        Supplied SNP annotation postprocessing: fuma-tools --method annotate-variants --help
        Supplied MAGMA gene/set report: fuma-tools --method magma-report --help
        Supplied-reference locus novelty: fuma-tools --method locus-novelty --help
        Declared family/baseline association union: fuma-tools --method known-associations --help
        Complete-family Fisher enrichment: fuma-tools --method fisher-enrichment --help
        Explicit transcript TSS/TES mapping: fuma-tools --method transcript-sites --help
        """;}
}
