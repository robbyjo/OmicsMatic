/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.*;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.PlinkBedDataset;

/** Original unphased-hard-call LD pruning with explicit founder reference and order. */
final class PlinkPruneCli {
    private PlinkPruneCli() { }
    static final Set<String> ALLOWED=Set.of("task","engine","home","bfile","samples","out","options-file","genome-build",
        "chromosome-scope","unresolved-alleles","reference-population","window-unit","window-size","step-size","r2","pair-order","missing-genotypes","minimum-reference-calls",
        "threads","memory-mib","timeout-seconds","maximum-samples","maximum-variants","maximum-cells","maximum-metadata-mib","maximum-retained-mib","maximum-line-bytes","maximum-bed-mib","maximum-samples-mib","maximum-report-mib");
    private static final String RECOVERY="Required action: read WARNINGS.md, session.yaml, variant-reference.tsv and original upstream logs. Correct the named input, reference population, window units/order, minimum call count or resource bound, then rerun to a new output directory. Resolve nonvariable/unobserved markers with a reviewed QC workflow. No filter, threshold or --bad-ld override is inferred. "+PlinkRuntime.setup();

    static int run(PlinkToolsCli.Parsed parsed,PrintStream output,PrintStream error) {
        var options=parsed.options();try {
            PlinkToolsCli.validateOptions(options,ALLOWED);if(!required(options,"task").equals("prune"))throw new IllegalArgumentException("Require --task prune");required(options,"out");required(options,"bfile");required(options,"samples");
            String engine=choice(options,"engine",Set.of("plink1","plink2")),unresolved=choice(options,"unresolved-alleles",Set.of("error","exclude"));
            choice(options,"chromosome-scope",Set.of("autosomes"));choice(options,"reference-population",Set.of("founders"));String build=choice(options,"genome-build",Set.of("GRCh37","GRCh38","unspecified"));
            String unit=choice(options,"window-unit",Set.of("variants","kb")),order=choice(options,"pair-order",Set.of("legacy","backward")),missing=choice(options,"missing-genotypes",Set.of("error","upstream"));
            int window=integer(options,"window-size",null),step=integer(options,"step-size",null),minimum=integer(options,"minimum-reference-calls",null);double r2=Double.parseDouble(required(options,"r2"));
            if(minimum<3||!Double.isFinite(r2)||r2<=0||r2>=1||unit.equals("variants")&&window<2)throw new IllegalArgumentException("Require at least3reference calls, count window>=2 and finite r2 strictly between0and1");
            if(engine.equals("plink1")&&!order.equals("legacy"))throw new IllegalArgumentException("PLINK1 has no backward --indep-order option");if(engine.equals("plink2")&&unit.equals("kb")&&step!=1)throw new IllegalArgumentException("PLINK2 requires step-size1 with a kilobase pruning window");
            int threads=integer(options,"threads",1),memory=integer(options,"memory-mib",engine.equals("plink1")?128:640),timeout=integer(options,"timeout-seconds",3600);if(threads>256||timeout>86400||memory<(engine.equals("plink1")?64:640))throw new IllegalArgumentException("Invalid threads,timeout or engine-specific memory bound");
            var bounds=new PlinkGlmInputs.Bounds(integer(options,"maximum-samples",100000),integer(options,"maximum-variants",2000000),positive(options,"maximum-cells",500000000L),mib(options,"maximum-metadata-mib",512),mib(options,"maximum-retained-mib",512),integer(options,"maximum-line-bytes",16384),mib(options,"maximum-bed-mib",65536),mib(options,"maximum-samples-mib",64));if(bounds.line()<64)throw new IllegalArgumentException("--maximum-line-bytes must be at least64");long reportBytes=mib(options,"maximum-report-mib",4096);
            String home=options.getOrDefault("home",System.getenv("JLINALG_PLINK_HOME"));if(home!=null&&!home.isBlank()){Path runtimeHome=PlinkToolsCli.resolved(Path.of(home)),destination=PlinkToolsCli.resolved(Path.of(options.get("out")));if(destination.startsWith(runtimeHome)||runtimeHome.startsWith(destination))throw new IllegalArgumentException("Pruning output must be outside the sealed runtime home");}
            int[] code={0};boolean[] warned={false};
            externalTransaction(options,"plink-original-autosomal-hardcall-LD-pruning",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("scope","Original --indep-pairwise autosomal biallelic hard-call pruning. Explicit recorded-founder reference, window units, shift and pair order. No native pruning estimator, global all-pairs independence guarantee, haplotype/phase-aware pruning, dosage processing, ancestry/relatedness certification or whole-paper replay. Outputs are variant-ID lists,not filtered genotypes.");session.put("pruning_policy",new LinkedHashMap<>(options));
                try {
                    if(home==null||home.isBlank())throw new PlinkRuntime.Unavailable("No --home or JLINALG_PLINK_HOME supplied",null);Path upstream=directory.resolve("upstream");Files.createDirectory(upstream);var runtime=new PlinkRuntime(Path.of(home),engine,upstream,session);
                    var input=PlinkQcInputs.prepare(Path.of(options.get("bfile")).toAbsolutePath().normalize(),Path.of(options.get("samples")).toAbsolutePath().normalize(),upstream,unresolved,bounds);manifest.put("input_sha256",input.inputHashes());session.put("staged_input_sha256",input.stagedHashes());
                    var reference=PlinkQcInputs.population(input,input.initialSamples(),"founders",false);if(reference.size()<(engine.equals("plink2")?50:3))throw new IOException("The admitted "+engine+" pruning profile requires at least "+(engine.equals("plink2")?50:3)+" selected recorded founders;no --bad-ld override is inferred");if(reference.size()<minimum)throw new IOException("Reference population is smaller than --minimum-reference-calls");
                    referenceStats(input,reference,directory,missing,minimum,session,warnings);
                    Path keep=upstream.resolve("reference.keep"),extract=upstream.resolve("reference.extract");PlinkQcInputs.keep(keep,reference);PlinkQcInputs.snps(extract,input.initialVariants());String keepSha=hash(keep),extractSha=hash(extract);
                    var args=new ArrayList<>(List.of("--bfile","input","--keep","reference.keep","--extract","reference.extract","--autosome","--threads",Integer.toString(threads),"--memory",Integer.toString(memory)));
                    if(engine.equals("plink1"))args.addAll(List.of("--keep-allele-order","--allow-no-sex"));else args.addAll(List.of("--no-psam-pheno","--indep-order",order.equals("legacy")?"1":"2"));
                    args.addAll(List.of("--indep-pairwise",Integer.toString(window)+(unit.equals("kb")?"kb":""),Integer.toString(step),Double.toString(r2),"--out","prune"));runtime.execute(args,upstream,"prune",timeout);
                    if(!keepSha.equals(hash(keep))||!extractSha.equals(hash(extract)))throw new IOException("Pruning reference masks changed during original execution");
                    var retained=PlinkQcReports.snps(upstream.resolve("prune.prune.in"),input.initialVariants(),reportBytes,bounds.line());var removed=PlinkQcReports.snps(upstream.resolve("prune.prune.out"),input.initialVariants(),reportBytes,bounds.line());var union=new HashSet<>(retained);for(String id:removed)if(!union.add(id))throw new IOException("Original retained/removed pruning families overlap");if(!union.equals(input.initialVariants())||retained.isEmpty())throw new IOException("Original pruning families are incomplete or contain no retained variants");
                    PlinkQcInputs.verify(input,upstream);runtime.verify();
                    if(options.containsKey("options-file")){Path recipe=Path.of(options.get("options-file"));if(!parsed.recipeSha256().equals(hash(recipe)))throw new IOException("Pruning recipe changed after parsing");Files.copy(recipe,directory.resolve("options-input.json"));if(!parsed.recipeSha256().equals(hash(directory.resolve("options-input.json"))))throw new IOException("Pruning recipe snapshot differs");}
                    publish(input,reference,retained,removed,directory);PlinkQcInputs.verify(input,upstream);runtime.verify();if(options.containsKey("options-file")&&!parsed.recipeSha256().equals(hash(Path.of(options.get("options-file")))))throw new IOException("Pruning recipe changed during analysis");session.put("source_samples",input.samples().size());session.put("initial_samples",input.initialSamples().size());session.put("reference_founders",reference.size());session.put("submitted_variants",input.initialVariants().size());session.put("retained_variants",retained.size());session.put("removed_variants",removed.size());session.put("reference_contract","Only sample-table include=yes individuals with both recorded FAM parent IDs0 enter LD pruning. The group column is preserved in the ledger but never filters controls/cases. Founder reference selection does not declare biological unrelatedness and does not publish a final-cohort sample exclusion mask.");
                    if(build.equals("unspecified"))warnings.add("Genome build is unspecified. Required action: establish assembly/coordinate provenance before genomic interpretation;the window uses the supplied BIM bp coordinates.");
                    if(input.initialVariants().size()<input.variants().size()||input.initialSamples().size()<input.samples().size())warnings.add("Initial sample include/autosome/unresolved-allele policies excluded source rows before pruning. Required action: inspect sample-reference.tsv and variant-decisions.tsv;those exclusions are distinct from the original LD-pruned family.");
                    try(var paths=Files.list(upstream)){for(Path path:paths.filter(p->p.getFileName().toString().endsWith(".stdout")||p.getFileName().toString().endsWith(".stderr")||p.getFileName().toString().endsWith(".log")).toList())try(var lines=Files.lines(path)){if(lines.anyMatch(line->line.stripLeading().toLowerCase(java.util.Locale.ROOT).startsWith("warning:")))warnings.add("Original "+engine+" emitted a warning in upstream/"+path.getFileName()+". Required action: read the complete retained file before downstream use.");}}
                    warned[0]=!warnings.isEmpty();String state=warned[0]?"completed-with-pruning-warnings":"completed";manifest.put("analysis_status",state);status(directory,state);if(warned[0])Files.writeString(directory.resolve("WARNINGS.md"),"# ORIGINAL LD PRUNING WARNINGS\n\n"+String.join("\n\n",warnings)+"\n");
                }catch(Exception failure){code[0]=failure instanceof PlinkRuntime.Unavailable?3:2;String state=code[0]==3?"skipped-dependency-unavailable":"failed";manifest.put("analysis_status",state);manifest.put("error",failure.toString());session.put("error",failure.toString());status(directory,state);for(String name:List.of("prune.in","prune.out","variant-decisions.tsv"))if(Files.exists(directory.resolve(name))){Files.createDirectories(directory.resolve("partial"));Files.move(directory.resolve(name),directory.resolve("partial").resolve(name));}Files.writeString(directory.resolve("WARNINGS.md"),"# ORIGINAL LD PRUNING "+(code[0]==3?"SKIPPED":"FAILED")+"\n\n"+failure+"\n\n"+String.join("\n\n",warnings)+"\n\n"+RECOVERY+"\n");}
                Files.writeString(directory.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));
            });
            if(code[0]!=0)error.println("ORIGINAL LD PRUNING "+(code[0]==3?"SKIPPED":"FAILED")+": see "+Path.of(options.get("out")).resolve("WARNINGS.md")+" for the cause and required action.");else{output.println("Original LD pruning completed: "+options.get("out"));if(warned[0])error.println("LD PRUNING WARNINGS: read "+Path.of(options.get("out")).resolve("WARNINGS.md")+" before downstream use.");}return code[0];
        }catch(Exception failure){error.println("ORIGINAL LD PRUNING NOT STARTED: "+failure.getMessage()+". "+RECOVERY+" Review plink-tools --task prune --help.");return 2;}
    }

    private static void referenceStats(PlinkQcInputs.Prepared input,Set<PlinkQcInputs.Id> reference,Path directory,String missing,int minimum,Map<String,Object> session,List<String> warnings) throws Exception {
        var lastPositions=new LinkedHashMap<String,Long>();for(String id:input.initialVariants()){var variant=input.variants().get(id);long position=Long.parseLong(variant.bp());if(position<1||position<lastPositions.getOrDefault(variant.chromosome(),0L))throw new IOException("Pruning requires positive, sorted within-chromosome bp coordinates;prepare a separate reviewed fileset instead of inferring an order");lastPositions.put(variant.chromosome(),position);}
        var requested=new LinkedHashSet<String>();for(String id:input.initialVariants())requested.add(input.variants().get(id).original());int[] indices=reference.stream().mapToInt(id->input.samples().get(id).ordinal()).toArray();long missingCalls=0;int invalid=0;
        var limits=new PlinkBedDataset.Limits(input.bounds().samples(),input.bounds().variants(),input.bounds().variants(),input.bounds().metadata(),input.bounds().retained(),input.bounds().line());
        try(var data=PlinkBedDataset.open(input.prefix(),requested,limits);var writer=Files.newBufferedWriter(directory.resolve("variant-reference.tsv"))){writer.write("variant\tcalled\tmissing\treference_samples\tminimum_dosage\tmaximum_dosage\tstatus\n");for(var variant:data.selectedVariants()){byte[] row=data.readPackedRow(variant);int called=0,lo=3,hi=-1;for(int sample:indices){int code=(row[sample/4]>>>(2*(sample%4)))&3;if(code==1)continue;int call=code==0?2:code==2?1:0;called++;lo=Math.min(lo,call);hi=Math.max(hi,call);}int absent=indices.length-called;missingCalls+=absent;String state=called<minimum?"INSUFFICIENT_CALLS":lo==hi?"NONVARIABLE_DOSAGE":absent>0&&missing.equals("error")?"MISSING_CALLS_PROHIBITED":"ADMITTED";if(!state.equals("ADMITTED"))invalid++;writer.write(variant.id()+"\t"+called+"\t"+absent+"\t"+indices.length+"\t"+(called==0?"":lo)+"\t"+(called==0?"":hi)+"\t"+state+"\n");}}
        session.put("reference_missing_genotype_cells",missingCalls);session.put("reference_unreportable_variants",invalid);if(invalid>0)throw new IOException(invalid+" pruning variants fail the declared reference-call/variable-dosage/missing policy;see variant-reference.tsv. No marker was silently removed");
        if(missingCalls>0)warnings.add(missingCalls+" reference genotype cells are missing under --missing-genotypes upstream. Required action: review variant-reference.tsv and the selected original version's unphased-hard-call pruning behavior. Marginal variable-call checks do not establish reportability of every pair or a global independence guarantee;no native imputation was applied.");
    }
    private static void publish(PlinkQcInputs.Prepared input,Set<PlinkQcInputs.Id> reference,Set<String> retained,Set<String> removed,Path directory) throws IOException {
        try(var in=Files.newBufferedWriter(directory.resolve("prune.in"));var out=Files.newBufferedWriter(directory.resolve("prune.out"));var decisions=Files.newBufferedWriter(directory.resolve("variant-decisions.tsv"))){decisions.write("source_ordinal\tvariant\tupstream_variant\tchromosome\tposition\toriginal_a1\toriginal_a2\tinitial_include\tretained_for_pruned_set\tdecision\n");for(var variant:input.variants().values()){String id=variant.staged();boolean eligible=input.initialVariants().contains(id),keep=retained.contains(id);String decision=!variant.autosomal()?"PREEXCLUDED_NONAUTOSOMAL":!eligible?"PREEXCLUDED_UNRESOLVED_ALLELES":keep?"ORIGINAL_RETAINED":"ORIGINAL_PRUNED";decisions.write((variant.ordinal()+1)+"\t"+variant.original()+"\t"+id+"\t"+variant.chromosome()+"\t"+variant.bp()+"\t"+variant.a1()+"\t"+variant.a2()+"\t"+eligible+"\t"+keep+"\t"+decision+"\n");if(keep)in.write(variant.original()+"\n");if(removed.contains(id))out.write(variant.original()+"\n");}}
        try(var writer=Files.newBufferedWriter(directory.resolve("sample-reference.tsv"))){writer.write("fid\tiid\tupstream_fid\tupstream_iid\tgroup_unused\tinitial_include\tFAM_founder\tused_for_reference\n");for(var sample:input.samples().values())writer.write(sample.original().fid()+"\t"+sample.original().iid()+"\t"+sample.staged().fid()+"\t"+sample.staged().iid()+"\t"+sample.group()+"\t"+sample.initialInclude()+"\t"+sample.founder()+"\t"+reference.contains(sample.staged())+"\n");}
    }
    private static void status(Path directory,String state) throws IOException {Files.writeString(directory.resolve("analysis-status.json"),"{\"status\":\""+state+"\"}\n");}
    private static String choice(Map<String,String> options,String key,Set<String> choices){String value=required(options,key);if(!choices.contains(value))throw new IllegalArgumentException("--"+key+" must be one of "+choices);return value;}
    private static long positive(Map<String,String> options,String key,Long fallback){long value=Long.parseLong(options.containsKey(key)?options.get(key):fallback==null?required(options,key):fallback.toString());if(value<1)throw new IllegalArgumentException("--"+key+" must be positive");return value;}
    private static int integer(Map<String,String> options,String key,Integer fallback){return Math.toIntExact(positive(options,key,fallback==null?null:fallback.longValue()));}
    private static long mib(Map<String,String> options,String key,long fallback){return Math.multiplyExact(positive(options,key,fallback),1048576);}
    static String help(){return """
        plink-tools --task prune --engine plink1|plink2 --home SEALED_PRIVATE_HOME
          --bfile BED_PREFIX --samples QC_SAMPLES.tsv --out NEW_DIRECTORY
          --genome-build GRCh37|GRCh38|unspecified --chromosome-scope autosomes
          --unresolved-alleles error|exclude --reference-population founders
          --window-unit variants|kb --window-size INTEGER --step-size INTEGER
          --r2 FRACTION_STRICTLY_BETWEEN_0_AND_1 --pair-order legacy|backward
          --missing-genotypes error|upstream --minimum-reference-calls INTEGER_AT_LEAST_3
        Original --indep-pairwise only: unphased hard-call LD; no VIF/phase-aware/dosage mode.
        QC sample TSV: fid,iid,include,group (tabs);every source FAM pair exactly once.
        Include=yes plus recorded FAM parents both0 selects the reference. Group labels
        never select cases or controls. Requires at least3reference founders for PLINK1
        and50for PLINK2;no --bad-ld override is inferred. Founders need not be unrelated.
        PLINK1 requires legacy pair order;PLINK2 explicitly selects legacy1/backward2.
        PLINK2 kb windows require step-size1. Nonvariable/insufficient reference calls
        fail with diagnostics and never trigger an inferred QC threshold or exclusion.
        Outputs prune.in/prune.out are a complete disjoint partition of submitted variants,
        mapped to original IDs in BIM order. Separate tables retain initial exclusions,
        reference identities and call diagnostics. These lists do not filter the current
        genotype files. Review upstream reports/warnings before use;windowed greedy
        pruning is not a guarantee of independence of all remaining marker pairs.
        Requires positive, sorted within-chromosome bp coordinates;input is never sorted.
        Common runtime/resource/options-file flags match plink-tools QC. Analysis never
        installs software;unavailable/changed/forbidden original runtime=>skip exit3 and
        WARNINGS.md. Input/scientific/process failure=>exit2;failed lists go to partial/.
        """;}
}
