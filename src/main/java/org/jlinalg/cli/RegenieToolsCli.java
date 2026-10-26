/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.genetics.RegenieInputs;
import org.jlinalg.genetics.RegenieTables;
import static org.jlinalg.cli.FollowupSupport.*;
import static org.jlinalg.cli.RegenieRuntime.*;

/** Typed whole-genome prediction, single-variant association and gene-mask testing. */
final class RegenieToolsCli {
    private RegenieToolsCli() { }
    private static final Set<String> COMMON=Set.of("method","runtime","format","genotypes","sample","allele-order","genome-build","block-size","threads","timeout","seed","extract","out");
    private static final Set<String> FIT=Set.of("phenotypes","phenotype-columns","trait","binary-coding","rint","missing","covariates","covariate-columns","categorical","folds");
    private static final Set<String> TEST=Set.of("model","test","min-mac","correction","p-threshold","firth-se");
    private static final Set<String> GENES=Set.of("annotations","set-list","masks","mask-rule","aaf-bins","vc-max-aaf","gene-tests","sbat-draws");
    static int run(String[] args,PrintStream out,PrintStream err){
        if(Arrays.asList(args).contains("--help")){out.println(help());return 0;}
        try{
            var allowed=new HashSet<>(COMMON);allowed.addAll(FIT);allowed.addAll(TEST);allowed.addAll(GENES);
            Map<String,String> o=options(args,allowed);String method=required(o,"method");choice(o,"method",null,Set.of("fit","test","genes"));
            var applicable=new HashSet<>(COMMON);applicable.addAll(method.equals("fit")?FIT:TEST);if(method.equals("genes"))applicable.addAll(GENES);
            for(String key:o.keySet())if(!applicable.contains(key))throw new IllegalArgumentException("--"+key+" does not apply to "+method);
            validate(o,method);int[] exit={0};externalTransaction(o,"regenie-"+method,(dir,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new LinkedHashSet<String>();
                session.put("method",method);session.put("engine","REGENIE "+VERSION);session.put("scope","Explicit homogeneous-trait fit/test/gene-mask subset. Original genotype bytes and raw upstream association fields retained. No cohort/ancestry QC, phenotype inference, meta-analysis, privacy perturbation, clinical interpretation or paper-wide/version parity.");
                try{
                    var runtime=new RegenieRuntime(Path.of(o.get("runtime")),dir,session);var inputs=new LinkedHashMap<Path,String>();
                    Path genotypeInput=Path.of(o.get("genotypes")).toAbsolutePath().normalize();
                    for(String suffix:o.get("format").equals("bed")?List.of(".bed",".bim",".fam"):o.get("format").equals("pgen")?List.of(".pgen",".pvar",".psam"):List.of("")){Path p=file(genotypeInput+suffix);inputs.put(p,hash(p));}
                    if(o.containsKey("sample")){Path p=file(o.get("sample"));inputs.put(p,hash(p));}
                    var genotype=RegenieInputs.inspect(o.get("format"),genotypeInput,o.containsKey("sample")?Path.of(o.get("sample")):null);
                    session.put("genotype_samples",genotype.sampleKeys().size());session.put("genotype_variants",genotype.variants());
                    session.put("genotype_validation","Native metadata/sample/header checks; original binary probability/genotype decoding is performed by upstream REGENIE. No conversion or dosage recoding.");
                    Path work=Files.createDirectory(dir.resolve("upstream"));
                    Map<String,Object> model=method.equals("fit")?prepareFit(o,dir,inputs,warnings):loadModel(o,dir,inputs);
                    var columns=strings(model.get("phenotype_columns"));var covariates=strings(model.get("covariate_columns"));var categorical=new HashSet<>(strings(model.get("categorical")));
                    RegenieTables.Kind kind=kind(String.valueOf(model.get("trait")),String.valueOf(model.get("binary_coding")));
                    var phenotypes=RegenieTables.read(dir.resolve("phenotypes.tsv"),columns,Set.of(),kind);
                    var covs=covariates.isEmpty()?null:RegenieTables.read(dir.resolve("covariates.tsv"),covariates,categorical,RegenieTables.Kind.COVARIATES);
                    var eligible=eligible(phenotypes,covs,genotype.sampleKeys(),String.valueOf(model.get("missing")),warnings,session);
                    if(!method.equals("fit"))verifyPredictions(dir,columns,eligible);
                    var command=new ArrayList<>(List.of(runtime.executable(),"--step",method.equals("fit")?"1":"2","--"+genotype.format(),runtime.path(genotype.input(),dir),
                        "--phenoFile","../phenotypes.tsv","--phenoColList",String.join(",",columns),"--bsize",o.get("block-size"),"--threads",o.getOrDefault("threads","1"),"--seed",o.getOrDefault("seed","17"),"--par-region",o.get("genome-build").equals("b37")?"hg19":"hg38","--out",method.equals("fit")?"fit":"assoc"));
                    if(genotype.sample()!=null)command.addAll(List.of("--sample",runtime.path(genotype.sample(),dir)));
                    if(o.get("allele-order").equals("first"))command.add("--ref-first");
                    if(o.containsKey("extract")){Path extract=file(o.get("extract"));inputs.put(extract,hash(extract));validateIds(extract);command.addAll(List.of("--extract",runtime.path(extract,dir)));}
                    if(!covariates.isEmpty()){command.addAll(List.of("--covarFile","../covariates.tsv","--covarColList",String.join(",",covariates)));if(!categorical.isEmpty())command.addAll(List.of("--catCovarList",String.join(",",new TreeSet<>(categorical))));}
                    if(model.get("trait").equals("binary")){command.add("--bt");if(model.get("binary_coding").equals("1/2"))command.add("--cc12");}
                    else {command.addAll(List.of("--qt","--force-qt"));if(Boolean.TRUE.equals(model.get("rint")))command.add("--apply-rint");}
                    if(model.get("missing").equals("complete"))command.add("--strict");
                    if(method.equals("fit"))command.addAll(List.of("--cv",o.getOrDefault("folds","5"),"--lowmem","--lowmem-prefix","lowmem","--use-relative-path"));
                    else{
                        var predictions=new ArrayList<String>();for(String line:Files.readAllLines(dir.resolve("predictions.list"))){String[] f=line.strip().split("\\s+");predictions.add(f[0]+" ../"+f[1]);}Files.writeString(work.resolve("predictions.list"),String.join("\n",predictions)+"\n");
                        command.addAll(List.of("--pred","predictions.list","--minMAC",o.get("min-mac"),"--test",o.get("test")));correction(o,model,command);
                        if(method.equals("genes"))genes(o,dir,inputs,command,warnings);
                    }
                    runtime.execute(command,work,"analysis",integer(o,"timeout",3600,1,86400));
                    if(method.equals("fit"))sealModel(dir,model,eligible,session);
                    else auditResults(work,columns,eligible,o,session,warnings);
                    RegenieRuntime.verify(inputs);runtime.verify();session.put("source_sha256",pathHashes(inputs));
                    collectWarnings(work,warnings);
                    if(RegenieRuntime.windows())warnings.add("WINDOWS SOURCE BUILD: upstream recommends Linux/macOS or WSL. This private Cygwin profile has local validation for the documented subset; retain the installer source/compatibility/compiler report. Gzipped phenotype/covariate/output text is disabled; internal BGEN compression remains available.");
                    if(method.equals("genes")&&o.get("mask-rule").equals("comphet"))warnings.add("COMPHET MASK: upstream unphased alternate-allele collapsing is not proof of variants in trans or of clinical pathogenicity. Review phase, ancestry, carrier thresholds and annotation before interpretation.");
                    manifest.put("analysis_status",warnings.isEmpty()?"completed":"completed-with-warnings");
                    table(dir.resolve("results.tsv"),List.of("metric","value"),List.of(new String[]{"method",method},new String[]{"phenotypes",Integer.toString(columns.size())},new String[]{"genotype_samples",Integer.toString(genotype.sampleKeys().size())},new String[]{"genotype_variants",Long.toString(genotype.variants())},new String[]{"association_rows",String.valueOf(session.getOrDefault("association_rows",0))}));
                }catch(Exception failure){
                    exit[0]=failure instanceof RegenieRuntime.Unavailable?3:2;manifest.put("analysis_status",exit[0]==3?"skipped-dependency-unavailable":"failed");manifest.put("error",String.valueOf(failure.getMessage()));quarantine(dir);
                    warnings.add((exit[0]==3?"REGENIE UNAVAILABLE - ANALYSIS SKIPPED: ":"REGENIE ANALYSIS FAILED: ")+failure.getMessage());
                }
                if(!warnings.isEmpty())Files.writeString(dir.resolve("WARNINGS.md"),"# REGENIE "+(exit[0]==3?"UNAVAILABLE - ANALYSIS SKIPPED":exit[0]==2?"ANALYSIS FAILED":"WARNINGS")+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: "+(exit[0]==3?"Where permitted run python tools/literature/install_regenie.py --allow-download (Windows also --allow-source-build --cygwin-root C:/cygwin64), then supply --runtime build/omics-regenie. If prerequisites/installations are forbidden, skip this analysis and request a permitted pinned runtime on a supported host. Preserve corrupt/unmanaged installations and choose a new destination.":"Inspect the retained upstream logs, sample exclusions, phenotype/covariate contract, alleles, filters, convergence and missing tests before interpretation. Correct the named cause and rerun to a new output directory. Failure publishes no successful results.tsv or model bundle; partial artifacts remain under partial/.")+"\n");
                session.put("warnings",new ArrayList<>(warnings));Files.writeString(dir.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));Files.writeString(dir.resolve("analysis-status.json"),json(Map.of("status",manifest.get("analysis_status")))+"\n");
            });
            if(exit[0]!=0)err.println((exit[0]==3?"SKIPPED REGENIE ANALYSIS":"REGENIE ANALYSIS FAILED")+": see "+Path.of(o.get("out"),"WARNINGS.md")+" for the cause and required action.");
            else{out.println("REGENIE operation completed: "+o.get("out"));if(Files.exists(Path.of(o.get("out"),"WARNINGS.md")))err.println("REGENIE WARNINGS: see "+Path.of(o.get("out"),"WARNINGS.md"));}return exit[0];
        }catch(Exception failure){err.println("jlinalg: "+failure.getMessage());return 2;}
    }
    private static void validate(Map<String,String> o,String method){
        for(String name:List.of("runtime","format","genotypes","allele-order","genome-build","block-size"))required(o,name);
        choice(o,"format",null,Set.of("bed","bgen","pgen"));choice(o,"genome-build",null,Set.of("b37","b38"));choice(o,"allele-order",null,o.get("format").equals("pgen")?Set.of("pvar"):Set.of("first","last"));
        if(o.containsKey("sample")&&!o.get("format").equals("bgen"))throw new IllegalArgumentException("sample applies only to BGEN");
        integer(o,"block-size",100,1,1_000_000);integer(o,"threads",1,1,256);integer(o,"seed",17,0,Integer.MAX_VALUE);integer(o,"timeout",3600,1,86400);
        if(method.equals("fit")){
            for(String name:List.of("phenotypes","phenotype-columns","trait","rint","missing"))required(o,name);
            choice(o,"trait",null,Set.of("quantitative","binary"));choice(o,"missing",null,Set.of("per-trait","complete"));boolean rint=bool(o,"rint",false);
            if(o.get("trait").equals("binary")){choice(o,"binary-coding",null,Set.of("0/1","1/2"));if(rint)throw new IllegalArgumentException("rint applies only to quantitative traits");}
            else if(o.containsKey("binary-coding"))throw new IllegalArgumentException("binary-coding applies only to binary traits");
            if(o.containsKey("covariates")!=o.containsKey("covariate-columns"))throw new IllegalArgumentException("covariates and covariate-columns must be supplied together");
            if(o.containsKey("categorical")&&!o.containsKey("covariates"))throw new IllegalArgumentException("categorical requires selected covariates");
            integer(o,"folds",5,2,1000);names(o.get("phenotype-columns"));
        }else{
            for(String name:List.of("model","test","min-mac","correction"))required(o,name);choice(o,"test",null,Set.of("additive","dominant","recessive"));choice(o,"correction",null,Set.of("none","firth","firth-approx","spa"));
            if(number(o,"min-mac",5)<0)throw new IllegalArgumentException("min-mac must be nonnegative");bool(o,"firth-se",false);
            if(o.get("correction").equals("none")&&(o.containsKey("p-threshold")||o.containsKey("firth-se")))throw new IllegalArgumentException("p-threshold/firth-se require the selected binary correction");
            if(!o.get("correction").equals("none")){required(o,"p-threshold");double p=number(o,"p-threshold",.05);if(p<=0||p>1)throw new IllegalArgumentException("p-threshold must be in (0,1]");}
            if(o.get("correction").equals("spa")&&o.containsKey("firth-se"))throw new IllegalArgumentException("firth-se does not apply to SPA");
            if(method.equals("genes")){
                for(String name:List.of("annotations","set-list","masks","mask-rule","aaf-bins","vc-max-aaf","gene-tests"))required(o,name);
                choice(o,"mask-rule",null,Set.of("max","sum","comphet"));choice(o,"gene-tests",null,Set.of("burden","rgc"));
                if(o.get("mask-rule").equals("sum")&&!o.get("test").equals("additive"))throw new IllegalArgumentException("sum masks support only additive tests in pinned REGENIE");
                double last=0;for(String value:o.get("aaf-bins").split(",",-1)){double v=finite(value,"aaf-bins");if(v<=last||v>1)throw new IllegalArgumentException("aaf-bins must be strictly increasing values in (0,1]");last=v;}
                double max=number(o,"vc-max-aaf",.01);if(max<=0||max>1)throw new IllegalArgumentException("vc-max-aaf must be in (0,1]");integer(o,"sbat-draws",10,1,1_000_000);
                if(o.get("gene-tests").equals("burden")&&o.containsKey("sbat-draws"))throw new IllegalArgumentException("sbat-draws requires rgc gene tests");
            }
        }
    }
    private static Map<String,Object> prepareFit(Map<String,String> o,Path dir,Map<Path,String> inputs,Set<String> warnings)throws Exception {
        Path source=file(o.get("phenotypes"));inputs.put(source,hash(source));String coding=o.getOrDefault("binary-coding","none");
        var phenotypes=RegenieTables.read(source,names(o.get("phenotype-columns")),Set.of(),kind(o.get("trait"),coding));phenotypes.write(dir.resolve("phenotypes.tsv"));
        List<String> covariates=o.containsKey("covariates")?names(o.get("covariate-columns")):List.of();Set<String> categorical=o.containsKey("categorical")?new LinkedHashSet<>(names(o.get("categorical"))):Set.of();
        if(!covariates.isEmpty()){Path cov=file(o.get("covariates"));inputs.put(cov,hash(cov));RegenieTables.read(cov,covariates,categorical,RegenieTables.Kind.COVARIATES).write(dir.resolve("covariates.tsv"));}
        if(o.get("trait").equals("quantitative"))for(int j=0;j<phenotypes.columns().size();j++){
            var unique=new HashSet<Double>();for(var row:phenotypes.rows())if(!row.cells().get(j).equals("NA"))unique.add(Double.valueOf(row.cells().get(j)));
            if(unique.size()<3)warnings.add("FEW QUANTITATIVE VALUES: "+phenotypes.columns().get(j)+" has "+unique.size()+" distinct observed values. Declared quantitative regression is forced; no binary/Firth/SPA model is inferred. Verify this scientific choice.");
        }
        var model=new LinkedHashMap<String,Object>();model.put("schema_version",1);model.put("engine","REGENIE");model.put("version",VERSION);model.put("trait",o.get("trait"));model.put("binary_coding",coding);model.put("rint",bool(o,"rint",false));model.put("missing",o.get("missing"));model.put("phenotype_columns",phenotypes.columns());model.put("covariate_columns",covariates);model.put("categorical",new ArrayList<>(categorical));model.put("genome_build",o.get("genome-build"));model.put("fit_allele_order",o.get("allele-order"));return model;
    }
    private static Map<String,Object> loadModel(Map<String,String> o,Path dir,Map<Path,String> inputs)throws Exception {
        Path root=Path.of(o.get("model")).toAbsolutePath().normalize(),metadata=file(root.resolve("regenie-model.json").toString());Map<?,?> raw=object(metadata);inputs.put(metadata,hash(metadata));
        if(!(raw.get("schema_version") instanceof Number schema)||schema.intValue()!=1||!Objects.equals(raw.get("engine"),"REGENIE")||!Objects.equals(raw.get("version"),VERSION)||!Objects.equals(raw.get("genome_build"),o.get("genome-build")))throw new IOException("Model schema/engine/version/declared genome build differs from this analysis");
        choiceValue(raw,"trait",Set.of("quantitative","binary"));choiceValue(raw,"missing",Set.of("complete","per-trait"));if(!(raw.get("rint") instanceof Boolean))throw new IOException("Model lacks a declared RINT choice");
        if(raw.get("trait").equals("binary")){choiceValue(raw,"binary_coding",Set.of("0/1","1/2"));if(Boolean.TRUE.equals(raw.get("rint")))throw new IOException("Binary model cannot apply RINT");}else choiceValue(raw,"binary_coding",Set.of("none"));
        var columns=strings(raw.get("phenotype_columns"));if(columns.isEmpty())throw new IOException("Model lacks phenotype columns");names(String.join(",",columns));
        if(!(raw.get("files_sha256") instanceof Map<?,?> files)||files.isEmpty())throw new IOException("Model lacks sealed prediction/input files");
        for(var entry:files.entrySet()){
            String relative=String.valueOf(entry.getKey());Path source=contained(root,relative);String expected=String.valueOf(entry.getValue());
            if(!Set.of("phenotypes.tsv","covariates.tsv","predictions.list").contains(relative)&&!relative.matches("predictions/p[0-9]+\\.loco"))throw new IOException("Unexpected sealed model file: "+relative);
            if(!expected.matches("[0-9a-f]{64}")||!Files.isRegularFile(source)||!expected.equals(hash(source)))throw new IOException("Sealed model file is missing/changed: "+relative);inputs.put(source,expected);
            Path target=contained(dir,relative);Files.createDirectories(target.getParent());Files.copy(source,target);
        }
        var model=new LinkedHashMap<String,Object>();for(var e:raw.entrySet())model.put(String.valueOf(e.getKey()),e.getValue());return model;
    }
    private static Map<String,Set<String>> eligible(RegenieTables.Table phenotypes,RegenieTables.Table covs,List<String> genotypeKeys,String missing,Set<String> warnings,Map<String,Object> session)throws IOException {
        var pheno=new HashMap<String,RegenieTables.Row>();for(var row:phenotypes.rows())pheno.put(row.sample().upstreamKey(),row);var cov=new HashMap<String,RegenieTables.Row>();if(covs!=null)for(var row:covs.rows())cov.put(row.sample().upstreamKey(),row);
        var result=new LinkedHashMap<String,Set<String>>();for(String column:phenotypes.columns())result.put(column,new HashSet<>());long absent=0,covMissing=0,phenoMissing=0;
        for(String key:genotypeKeys){
            var row=pheno.get(key);if(row==null){absent++;continue;}if(covs!=null&&(!cov.containsKey(key)||cov.get(key).cells().contains("NA"))){covMissing++;continue;}
            if(missing.equals("complete")&&row.cells().contains("NA")){phenoMissing++;continue;}
            for(int j=0;j<phenotypes.columns().size();j++)if(!row.cells().get(j).equals("NA"))result.get(phenotypes.columns().get(j)).add(key);
        }
        for(var entry:result.entrySet())if(entry.getValue().size()<3)throw new IOException("Fewer than three eligible observed samples for "+entry.getKey()+" after exact genotype/phenotype/covariate matching");
        var genotypeSet=new HashSet<>(genotypeKeys);long outside=pheno.keySet().stream().filter(k->!genotypeSet.contains(k)).count();
        session.put("eligible_observed_samples",result.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().size(),(a,b)->a,LinkedHashMap::new)));
        session.put("genotype_samples_without_phenotype_row",absent);session.put("samples_without_complete_covariates",covMissing);session.put("samples_excluded_by_complete_phenotype_policy",phenoMissing);session.put("phenotype_rows_outside_genotypes",outside);
        session.put("phenotype_na_by_column",phenotypes.missing());session.put("missing_policy",missing);session.put("quantitative_missing_behavior","per-trait: upstream step-1 mean imputation and step-2 observed-trait testing; complete: --strict in both steps. Covariate-missing samples are removed.");
        if(absent+covMissing+phenoMissing+outside>0||phenotypes.missing().values().stream().anyMatch(n->n>0))warnings.add("SAMPLE EXCLUSIONS/MISSINGNESS: inspect session.yaml exact matched counts and phenotype NA counts. Genotype rows without phenotype="+absent+", incomplete covariates="+covMissing+", complete-policy exclusions="+phenoMissing+", phenotype rows outside genotype="+outside+". Confirm the intended cohort before interpreting tests.");
        return result;
    }
    private static void sealModel(Path dir,Map<String,Object> model,Map<String,Set<String>> eligible,Map<String,Object> session)throws Exception {
        Path prediction=file(dir.resolve("upstream/fit_pred.list").toString());var columns=strings(model.get("phenotype_columns"));var seen=new HashSet<String>();var lines=new ArrayList<String>();Path predictions=Files.createDirectory(dir.resolve("predictions"));
        for(String line:Files.readAllLines(prediction)){
            String[] f=line.strip().split("\\s+");if(f.length!=2||!columns.contains(f[0])||!seen.add(f[0]))throw new IOException("Upstream omitted/duplicated/added requested phenotype predictions");
            Path source=contained(dir.resolve("upstream"),f[1]);String name="predictions/p"+columns.indexOf(f[0])+".loco";Files.copy(file(source.toString()),dir.resolve(name));lines.add(f[0]+" "+name);
        }
        if(!seen.equals(new HashSet<>(columns)))throw new IOException("Some requested phenotypes did not produce converged predictions; inspect analysis.log");
        Files.writeString(dir.resolve("predictions.list"),String.join("\n",lines)+"\n");verifyPredictions(dir,columns,eligible);
        var hashes=new LinkedHashMap<String,String>();hashes.put("phenotypes.tsv",hash(dir.resolve("phenotypes.tsv")));if(Files.exists(dir.resolve("covariates.tsv")))hashes.put("covariates.tsv",hash(dir.resolve("covariates.tsv")));hashes.put("predictions.list",hash(dir.resolve("predictions.list")));
        try(var paths=Files.list(predictions)){for(Path p:paths.sorted().toList())hashes.put("predictions/"+p.getFileName(),hash(p));}
        model.put("files_sha256",hashes);Files.writeString(dir.resolve("regenie-model.json"),json(model)+"\n");session.put("model_sha256",hash(dir.resolve("regenie-model.json")));
    }
    private static void verifyPredictions(Path dir,List<String> columns,Map<String,Set<String>> eligible)throws IOException {
        var seen=new HashSet<String>();for(String line:Files.readAllLines(file(dir.resolve("predictions.list").toString()))){
            String[] f=line.strip().split("\\s+");if(f.length!=2||!columns.contains(f[0])||!seen.add(f[0]))throw new IOException("Model prediction list differs from requested phenotype set");
            try(var reader=Files.newBufferedReader(file(contained(dir,f[1]).toString()))){
                String first=reader.readLine();if(first==null)throw new IOException("Empty LOCO prediction file");String[] header=first.strip().split("\\s+");var keys=new HashSet<String>(List.of(header).subList(1,header.length));
                if(!header[0].equals("FID_IID")||keys.size()!=header.length-1||!keys.containsAll(eligible.get(f[0])))throw new IOException("LOCO IDs are duplicate or omit eligible test samples for "+f[0]+"; refit using the intended cohort");
                int rows=0;var chromosomes=new HashSet<String>();String row;while((row=reader.readLine())!=null){String[] cells=row.strip().split("\\s+");if(cells.length!=header.length||!chromosomes.add(cells[0]))throw new IOException("LOCO row width/chromosome duplication is invalid");for(int i=1;i<cells.length;i++){if(cells[i].equals("NA")){if(eligible.get(f[0]).contains(header[i]))throw new IOException("LOCO prediction is missing for an eligible sample: "+header[i]);}else finite(cells[i],"LOCO prediction");}rows++;}if(rows==0)throw new IOException("LOCO file has no chromosome predictions");
            }
        }if(!seen.equals(new HashSet<>(columns)))throw new IOException("Model lacks some requested phenotype predictions");
    }
    private static void correction(Map<String,String> o,Map<String,Object> model,List<String> command){
        String correction=o.get("correction");if(!model.get("trait").equals("binary")&&!correction.equals("none"))throw new IllegalArgumentException("Binary correction requires a fitted binary model; declared quantitative models never infer logistic/Firth/SPA");
        if(correction.startsWith("firth")){command.add("--firth");if(correction.equals("firth-approx"))command.add("--approx");if(bool(o,"firth-se",false))command.add("--firth-se");}
        if(correction.equals("spa"))command.add("--spa");if(!correction.equals("none"))command.addAll(List.of("--pThresh",o.get("p-threshold")));
    }
    private static void genes(Map<String,String> o,Path dir,Map<Path,String> inputs,List<String> command,Set<String> warnings)throws Exception {
        for(String[] field:new String[][]{{"annotations","anno-file"},{"set-list","set-list"},{"masks","mask-def"}}){Path p=file(o.get(field[0]));inputs.put(p,hash(p));Path copy=dir.resolve(field[0]+".txt");Files.copy(p,copy);command.addAll(List.of("--"+field[1],"../"+copy.getFileName()));}
        command.addAll(List.of("--build-mask",o.get("mask-rule"),"--aaf-bins",o.get("aaf-bins"),"--vc-maxAAF",o.get("vc-max-aaf"),"--write-mask-snplist"));
        if(o.get("mask-rule").equals("sum"))warnings.add("SUM MASK OUTPUT: pinned REGENIE cannot write sum-mask genotypes as PLINK BED. Variant membership lists and association results are retained; --write-mask is deliberately omitted. Sum supports additive testing only.");else command.add("--write-mask");
        if(o.get("gene-tests").equals("rgc"))command.addAll(List.of("--rgc-gene-p","--sbat-napprox",o.getOrDefault("sbat-draws","10")));
        warnings.add("GENE MASKS: annotations, set membership, allele-frequency bins, max/sum/comphet construction, MAC threshold and tested gene universe are caller-supplied scientific decisions. RGC strategy supplies version-specific burden/ACAT and, for additive tests, variance-component combinations; quantitative models also use SBAT. Non-additive tests omit variance-component tests. Raw tests/NA values are retained, without automatic FDR or variant pathogenicity inference.");
    }
    private static void auditResults(Path work,List<String> columns,Map<String,Set<String>> eligible,Map<String,String> o,Map<String,Object> session,Set<String> warnings)throws IOException {
        long total=0;var counts=new LinkedHashMap<String,Long>();var tests=new TreeSet<String>();long missing=0;
        for(String phenotype:columns){Path file=file(work.resolve("assoc_"+phenotype+".regenie").toString());long rows=0;
            try(var reader=Files.newBufferedReader(file)){
                String line;do{line=reader.readLine();}while(line!=null&&line.startsWith("##"));if(line==null)throw new IOException("Missing association header for "+phenotype);
                List<String> header=List.of(line.strip().split("\\s+"));if(new HashSet<>(header).size()!=header.size()||!header.containsAll(List.of("CHROM","GENPOS","ID","ALLELE0","ALLELE1","A1FREQ","N","TEST","BETA","SE","CHISQ","LOG10P")))throw new IOException("Unexpected REGENIE association schema");
                while((line=reader.readLine())!=null){String[] f=line.strip().split("\\s+");if(f.length!=header.size())throw new IOException("Association row width differs from header");
                    long n=Long.parseLong(f[header.indexOf("N")]);if(n<1||n>eligible.get(phenotype).size())throw new IOException("Association sample count is outside the matched observed phenotype cohort");tests.add(f[header.indexOf("TEST")]);
                    for(String name:List.of("A1FREQ","BETA","SE","CHISQ","LOG10P")){String cell=f[header.indexOf(name)];if(cell.equals("NA")){missing++;continue;}double value=finite(cell,name);if((name.equals("A1FREQ")&&(value<0||value>1))||!name.equals("BETA")&&value<0)throw new IOException("Invalid association statistic: "+name);}
                    rows++;
                }
            }counts.put(phenotype,rows);total+=rows;if(rows==0)warnings.add("ZERO ASSOCIATION ROWS: "+phenotype+" has no emitted tests after the supplied filters. Inspect allele/MAC/mask/convergence settings; this does not establish no biological association.");
        }
        session.put("association_rows",total);session.put("association_rows_by_phenotype",counts);session.put("emitted_tests",new ArrayList<>(tests));session.put("missing_statistic_cells",missing);if(missing>0)warnings.add("UNAVAILABLE STATISTICS: "+missing+" raw association statistic cells are NA; inspect upstream convergence/test diagnostics before downstream use.");
        if(o.get("method").equals("genes")&&o.get("gene-tests").equals("rgc")&&tests.stream().noneMatch(t->t.contains("GENE_P")))warnings.add("NO GENE_P EMITTED: requested RGC strategy produced no GENE_P rows. Inspect masks, carrier/MAF filters, degeneracy and omitted tests before interpreting the gene universe.");
    }
    private static void collectWarnings(Path work,Set<String> warnings)throws IOException {try(var paths=Files.list(work)){for(Path path:paths.filter(p->p.getFileName().toString().endsWith(".log")).toList())try(var lines=Files.lines(path)){lines.filter(s->s.matches("(?i).*\\b(warning|ignored|failed)\\b.*|(?i).*(not converg|did not converg|no variants|no phenotypes).*")).limit(100).forEach(s->warnings.add(path.getFileName()+": "+s));}}}
    private static void quarantine(Path dir)throws IOException {try(var paths=Files.list(dir)){for(Path p:paths.toList()){if(p.getFileName().toString().endsWith(".log")||p.getFileName().toString().equals("partial"))continue;Files.createDirectories(dir.resolve("partial"));Files.move(p,dir.resolve("partial").resolve(p.getFileName()));}}}
    private static RegenieTables.Kind kind(String trait,String coding){return trait.equals("quantitative")?RegenieTables.Kind.QUANTITATIVE:coding.equals("0/1")?RegenieTables.Kind.BINARY_ZERO_ONE:RegenieTables.Kind.BINARY_ONE_TWO;}
    private static List<String> names(String text){var names=List.of(text.split(",",-1));if(new HashSet<>(names).size()!=names.size()||names.stream().anyMatch(n->!n.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*")))throw new IllegalArgumentException("Column lists require unique nonempty safe names without expansion syntax");return names;}
    private static List<String> strings(Object value)throws IOException {if(!(value instanceof List<?> list)||list.stream().anyMatch(v->!(v instanceof String)))throw new IOException("Model lacks an expected string list");return list.stream().map(String.class::cast).toList();}
    private static void choiceValue(Map<?,?> model,String key,Set<String> choices)throws IOException {if(!choices.contains(String.valueOf(model.get(key))))throw new IOException("Invalid model field: "+key);}
    private static String choice(Map<String,String> o,String key,String fallback,Set<String> choices){String value=o.getOrDefault(key,fallback);if(value==null||!choices.contains(value))throw new IllegalArgumentException(key+" must be one of "+choices);return value;}
    private static Path file(String value)throws IOException {Path path=Path.of(value).toAbsolutePath().normalize();if(!Files.isRegularFile(path))throw new IOException("Missing local input/model/output file: "+path);return path;}
    private static Map<String,String> pathHashes(Map<Path,String> values){var result=new LinkedHashMap<String,String>();for(var e:values.entrySet())result.put(e.getKey().toString(),e.getValue());return result;}
    private static void validateIds(Path file)throws IOException {var seen=new HashSet<String>();try(var reader=Files.newBufferedReader(file)){String line;while((line=reader.readLine())!=null)if(line.isBlank()||!line.matches("\\S+")||!seen.add(line))throw new IOException("Extract list requires unique one-per-line variant IDs without a header");}if(seen.isEmpty())throw new IOException("Extract list is empty");}
    static String help(){return"""
        regenie-tools --method fit|test|genes --runtime MANAGED_HOME --out NEW_DIR
          --format bed|bgen|pgen --genotypes PREFIX_OR_BGEN --genome-build b37|b38
          --allele-order first|last (BED/BGEN) or pvar (PGEN) --block-size INTEGER
          [--sample OXFORD.sample (BGEN only) --extract VARIANT_IDS --threads 1 --seed 17 --timeout 3600]
        fit: --phenotypes FID_IID_TABLE --phenotype-columns Y1,Y2 --trait quantitative|binary
          --rint true|false --missing complete|per-trait [--binary-coding 0/1|1/2 --folds 5]
          [--covariates TABLE --covariate-columns age,sex,PC1 --categorical sex].
          Quantitative declaration forces QT, even for 0/1 values; no inferred binary/Firth/SPA.
          Homogeneous phenotype type/coding per bundle, explicit NA only. Reserved -999 fails.
          Exact FID IID matching; duplicate/underscore-colliding IDs fail. Selected tokens preserved.
          per-trait uses upstream step-1 mean imputation and step-2 observed-trait testing;
          complete passes --strict in both steps. Missing covariates remove those samples.
          Low-memory fit and relative predictions are fixed. Seal original selected tables and all
          requested phenotype LOCO predictions in regenie-model.json; missing predictions fail.
        test/genes: --model FIT_BUNDLE --test additive|dominant|recessive --min-mac NUMBER
          --correction none|firth|firth-approx|spa [--p-threshold (0,1] --firth-se false].
          Correction applies only to a declared binary model. Step 2 inherits the sealed phenotype,
          covariates, categorical columns, coding, RINT and missing policy; mismatched build/IDs fail.
          Same assembly is declared explicitly; array step-1 and sequencing/imputed step-2 variants
          may differ. No inferred REF or allele harmonization; allele-order is explicit for each input.
        genes additionally: --annotations FILE --set-list FILE --masks FILE --mask-rule max|sum|comphet
          --aaf-bins INCREASING_COMMA_LIST --vc-max-aaf (0,1] --gene-tests burden|rgc [--sbat-draws 10].
          Native REGENIE annotation/set/mask syntax, no automatic transcript/pathogenicity selection.
          RGC strategy: burden/ACAT; additive SKAT/ACATV combinations; quantitative SBAT. Non-additive
          variance-component omission is explicit in upstream logs. Comphet is not verified in-trans.
          Sum supports additive tests only; upstream cannot export sum-mask genotypes as BED.
          That export is omitted with a prominent warning; mask membership lists are retained.
        All methods retain actual commands, exit codes, source/runtime hashes, raw upstream outputs
          and tests/NA cells; results.tsv is a bundle summary, not transformed association statistics.
          No HTP/REMETA, conditional/interaction/multitrait, time-to-event, full-paper or old-version parity.
          Original BED/BGEN/PGEN decoding is upstream; native Java checks headers/sample/column contracts.
          Uncompressed FID/IID tables and PVAR/PSAM required; PGEN biallelic PVAR subset only.
        Missing/corrupt/unloadable runtime: skip 3. Analysis/model/input failure: 2, retained partial/
          and prominent WARNINGS.md with required action. No automatic installation from analysis.
          Setup where permitted: python tools/literature/install_regenie.py --allow-download
          (Windows also --allow-source-build --cygwin-root C:/cygwin64). Supply --runtime build/omics-regenie.
          Private Cygwin Windows source profile is locally tested; upstream recommends Linux/macOS/WSL.
        """;}
}
