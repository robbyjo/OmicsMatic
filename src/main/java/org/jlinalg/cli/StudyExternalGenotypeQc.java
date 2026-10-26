/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyOperationExecutor.Context;

/** Scoped external QC reuse. Validation does not re-execute or certify the source analysis. */
final class StudyExternalGenotypeQc {
    static final List<String> ROLES=List.of("sample_qc_report","sample_qc_samples","sample_qc_pcs");
    static final String CAVEAT="Sample QC and relatedness decisions are externally produced, source-reported evidence. Artifact hashes, exact matrix/sample binding and internal consistency were checked; the source genotypes, pair computations, calibration and scientific adequacy were not independently reproduced. Imported PCs preserve their original population and basis rather than becoming native PCA. A changed phenotype subset does not refit imported PCs. Source-specific limitations require local review before scientific impact or manuscript claims; private source notes are not copied into AI evidence. These records do not verify caller quality, biological sex/PAR, contamination, identity, ancestry labels or a correct dependence model, and do not retrospectively validate independently supplied results.";
    private StudyExternalGenotypeQc() { }
    private record Sample(String decision,String reason,double callRate,boolean basis) { }
    record Loaded(boolean sampleComplete,List<String[]> retained,List<String[]> exclusions,Set<String> basis,
                  Map<String,double[]> pcScores,List<String> pcColumns,Map<String,Object> summary,
                  List<String> outputs,List<String> problems,Map<String,String> sourceHashes) { }

    static Loaded load(Context c,Map<String,Object> dataset,Map<String,Object> hypothesis,DelimitedData phenotype,
                       List<String[]> candidates,List<String> matrixIds,Map<String,Object> policy,Map<String,String> originals)throws IOException {
        var files=map(dataset.get("files"));var problems=new ArrayList<String>();var outputs=new ArrayList<String>();
        var summary=new LinkedHashMap<String,Object>();var hashes=new LinkedHashMap<String,String>();var samples=new LinkedHashMap<String,Sample>();
        var pcScores=new HashMap<String,double[]>();var pcColumns=new ArrayList<String>();var basis=new HashSet<String>();
        summary.put("execution_origin","externally_produced");summary.put("external_qc_reexecuted",false);summary.put("native_panel_filters_applied",false);
        summary.put("contains_private_source_metadata",true);summary.put("privacy","Local source methods, provenance and limitations may include private text. Only the finding-inventory allowlist is prepared for aggregate AI evidence.");
        summary.put("dataset_id",dataset.get("id"));summary.put("required_manuscript_caveat",CAVEAT);
        Path dir=Files.createDirectories(c.outputDirectory().resolve("genotype-qc/external"));boolean sampleComplete=false;
        try {
            for(String role:List.of("sample_qc_report","sample_qc_samples"))check(files.containsKey(role),"Supply the external sample-QC report and full sample-decision table");
            for(String role:ROLES)if(files.containsKey(role)) {
                Path source=c.project().resolve(text(files.get(role)));check(Files.size(source)<=integer(policy,"maximum_import_bytes"),"External QC artifact exceeds its registered byte bound");
                String digest=FollowupSupport.hash(source);check(digest.equals(originals.get(role)),"External QC inputs changed after source alignment");
                Path copy=dir.resolve(role+"."+(role.equals("sample_qc_report")?"yaml":"tsv"));Files.copy(source,copy);check(digest.equals(FollowupSupport.hash(copy)),"External QC snapshot changed during acquisition");
                hashes.put(role,digest);outputs.add(c.outputDirectory().relativize(copy).toString().replace('\\','/'));
            }
            Path reportPath=c.project().resolve(text(files.get("sample_qc_report")));check(Files.size(reportPath)<=2000000,"External QC report exceeds the two-megabyte metadata bound");
            var report=StudyYaml.loadArtifact(reportPath);check(number(report,"schema_version")==1,"External QC report requires schema_version 1");
            label(report,"analysis_id");check(dataset.get("id").equals(report.get("dataset_id")),"External QC report names a different dataset");
            check(policy.get("genome_build").equals(report.get("genome_build")),"External QC genome build differs from the registered QC build");
            check(originals.get("matrix").equals(report.get("analysis_matrix_sha256")),"External QC report is not bound to the current association matrix bytes");
            var declaredHashes=map(report.get("file_sha256"));for(var e:hashes.entrySet())if(!e.getKey().equals("sample_qc_report"))check(e.getValue().equals(declaredHashes.get(e.getKey())),"External QC data bytes differ from the source report hashes");
            check(Set.of("complete","partial","not_estimable").contains(text(report.get("status"))),"External QC report needs a declared completion status");
            var sq=map(report.get("sample_qc"));var kin=map(report.get("relatedness"));var pca=map(report.get("pca"));
            label(sq,"software");label(sq,"software_version");label(kin,"software");label(kin,"software_version");label(kin,"support");
            check("observed_autosomal_hard_calls".equals(sq.get("call_rate_scope")),"External sample call-rate scope must identify observed autosomal hard calls");
            double required=map(map(hypothesis.get("analysis")).get("association")).get("minimum_sample_call_rate") instanceof Number v?v.doubleValue():.98;
            double sourceMinimum=probability(sq,"minimum_call_rate");check(sourceMinimum>=required,"Source sample call-rate threshold is weaker than the registered analysis requirement");
            check("KING_between_family".equals(kin.get("estimator")),"The scoped external relatedness adapter requires a declared KING between-family estimator");
            check(Math.abs(number(kin,"kinship_threshold")-number(policy,"kinship_threshold"))<=1e-12,"Source kinship threshold differs from the registered threshold");
            check(policy.get("relatedness_action").equals(kin.get("action")),"Source relatedness action differs from the registered action");
            check(count(kin,"minimum_shared_markers")>=integer(policy,"minimum_pair_markers")&&count(kin,"minimum_pair_heterozygotes")>=integer(policy,"minimum_pair_heterozygotes"),"External relatedness support is below the registered floors");
            check(matrixIds.size()<=integer(policy,"maximum_import_samples"),"External sample population exceeds the registered linear import bound");
            Set<String> population=new HashSet<>(matrixIds);check(population.size()==matrixIds.size(),"Matrix sample IDs are not unique");
            boolean[] basisPresent={false};
            stream(c.project().resolve(text(files.get("sample_qc_samples"))),policy,(header,row)->{
                String id=cell(header,row,"sample_id");safeId(id);check(population.contains(id),"External QC sample table contains a sample outside the matrix population");
                String status=cell(header,row,"status");check(status.equals("complete"),"External sample decisions include unresolved rows");
                String decision=cell(header,row,"decision"),reason=cell(header,row,"reason");check(Set.of("keep","exclude").contains(decision),"External sample decisions must be keep or exclude");
                double rate=value(cell(header,row,"observed_call_rate"));check(rate>=0&&rate<=1,"External sample call rate is outside [0,1]");
                check(!decision.equals("keep")||rate>=sourceMinimum,"An externally retained sample fails its declared call-rate threshold");
                check(decision.equals("keep")?reason.isEmpty()||reason.equals("retained"):Set.of("call_rate","relatedness","other_qc").contains(reason),"External sample exclusions require a typed reason");
                boolean anchor=false;if(header.containsKey("pca_basis")){basisPresent[0]=true;String flag=cell(header,row,"pca_basis");check(Set.of("true","false").contains(flag),"External basis membership must be explicit true or false");anchor=Boolean.parseBoolean(flag);}
                check(samples.put(id,new Sample(decision,reason,rate,anchor))==null,"External sample decisions repeat a sample ID");
            });
            check(samples.keySet().equals(population),"External sample-QC table does not cover the exact matrix sample universe");
            long sourceN=matrixIds.size(),pairN=sourceN*(sourceN-1)/2,related=count(kin,"related_pairs"),unresolved=count(kin,"unresolved_pairs");
            check(count(sq,"samples")==sourceN&&count(kin,"evaluated_samples")==sourceN&&count(kin,"evaluated_pairs")==pairN,"External QC source sample/pair counts do not cover the declared matrix universe");
            check(related<=pairN&&unresolved<=pairN,"External pair counts exceed their declared universe");
            long kept=samples.values().stream().filter(s->s.decision().equals("keep")).count(),removed=samples.values().stream().filter(s->s.reason().equals("relatedness")).count();
            check("exclude".equals(kin.get("action"))||removed==0,"Source review/model actions cannot claim relatedness-pruning exclusions");
            check(!"review".equals(kin.get("action"))||related==0,"External relatedness review still has above-threshold pairs");
            check(!"exclude".equals(kin.get("action"))||removed/(double)Math.max(1,kept+removed)<=number(policy,"maximum_excluded_fraction"),"External relatedness exclusions exceed the registered fraction");
            check(Boolean.TRUE.equals(kin.get("retained_population_compatible")),"External retained-population compatibility is unresolved");
            for(var e:samples.entrySet())if(e.getValue().basis())basis.add(e.getKey());
            summary.put("external_source_samples",sourceN);summary.put("external_qc_retained_samples",kept);summary.put("external_relatedness_excluded_samples",removed);summary.put("external_evaluated_pairs",pairN);summary.put("external_related_pairs",related);summary.put("external_unresolved_pairs",unresolved);if(basisPresent[0])summary.put("external_basis_samples",basis.size());summary.put("external_basis_membership_present",basisPresent[0]);summary.put("external_independent_basis_complete",Boolean.TRUE.equals(kin.get("independent_basis_complete")));
            summary.put("external_analysis_id",report.get("analysis_id"));summary.put("external_method",Map.of("sample_qc",sq,"relatedness",kin,"pca",pca));
            summary.put("external_limitations",list(report.get("limitations")));summary.put("external_limitations_review_required",true);
            summary.put("external_provenance",map(report.get("provenance")));
            sampleComplete="complete".equals(report.get("sample_universe"))&&"complete".equals(sq.get("status"))&&"complete".equals(kin.get("status"))&&unresolved==0&&!"not_estimable".equals(report.get("status"));
            if(!sampleComplete)problems.add("External sample QC or relatedness scope is partial, unestimable or incomplete; source decisions cannot approve the population");
            if("native".equals(policy.get("pca_source"))&&integer(policy,"pca_components")>0)try {
                check(Boolean.TRUE.equals(kin.get("independent_basis_complete")),"External independent-basis scope is incomplete for requested native PCA");
                check(basisPresent[0]&&basis.size()>=3,"Native missing-PCA completion requires explicit external basis membership");check(basis.stream().allMatch(id->samples.get(id).decision().equals("keep")),"Native PCA cannot use source-QC-excluded basis members");
            }catch(IOException invalid){problems.add(invalid.getMessage());}
            if("supplied".equals(policy.get("pca_source"))&&integer(policy,"pca_components")>0) {
                try {
                    check("complete".equals(pca.get("status")),"Requested external PCA remains incomplete");label(pca,"software");label(pca,"software_version");label(pca,"basis");
                    for(Object name:list(pca.get("columns"))){check(name instanceof String,"External PCA columns must be ordered names");String column=text(name);safeId(column);check(!pcColumns.contains(column),"External PCA column names repeat");pcColumns.add(column);}
                    check(pcColumns.size()>=integer(policy,"pca_components")&&pcColumns.size()<=100,"External PCA has fewer components than requested or exceeds the supported count");
                    long basisCount=count(pca,"basis_samples");String basisScope=text(pca.getOrDefault("basis_scope","matrix_population"));check(Set.of("matrix_population","external_reference").contains(basisScope),"External PCA needs a declared matrix or external-reference basis scope");
                    check(basisCount>=3&&pcColumns.size()<basisCount,"External PCA basis count or component rank is invalid");
                    if(basisScope.equals("matrix_population"))check(basisCount<=sourceN,"Matrix-population PCA basis count exceeds the declared source sample universe");
                    else{label(pca,"reference_id");check(text(pca.get("reference_sha256")).matches("[a-f0-9]{64}"),"External-reference PCA needs the declared fitted reference identity and SHA-256");summary.put("external_pca_reference_sha256",pca.get("reference_sha256"));}
                    summary.put("external_pca_basis_scope",basisScope);
                    check(files.containsKey("sample_qc_pcs"),"Supply the completed external PCA score table or explicitly request native missing-PCA completion");
                    stream(c.project().resolve(text(files.get("sample_qc_pcs"))),policy,(header,row)->{
                        String id=cell(header,row,"sample_id");check(population.contains(id),"External PCA scores include a sample outside the matrix population");double[] scores=new double[(int)integer(policy,"pca_components")];
                        for(int k=0;k<pcColumns.size();k++){double score=value(cell(header,row,pcColumns.get(k)));if(k<scores.length)scores[k]=score;}
                        check(pcScores.put(id,scores)==null,"External PCA scores repeat a sample ID");
                    });
                    check(samples.entrySet().stream().filter(e->e.getValue().decision().equals("keep")).allMatch(e->pcScores.containsKey(e.getKey())),"External PCA scores do not cover every source-QC-retained sample");
                    summary.put("external_pca_basis_samples",count(pca,"basis_samples"));
                }catch(IOException invalid){problems.add(invalid instanceof InvalidArtifact?invalid.getMessage():"Requested external PCA could not be loaded; inspect the local import diagnostics");Files.writeString(dir.resolve("pca-import-errors.txt"),invalid.toString()+"\n");outputs.add("genotype-qc/external/pca-import-errors.txt");pcScores.clear();}
            }
        }catch(IOException|IllegalArgumentException invalid){sampleComplete=false;problems.add("External sample-QC artifact failed scope or consistency validation; inspect the local import diagnostics");Files.writeString(dir.resolve("import-errors.txt"),invalid.toString()+"\n");outputs.add("genotype-qc/external/import-errors.txt");}
        var retained=new ArrayList<String[]>();var exclusions=new ArrayList<String[]>();int idAt=phenotype.column(text(map(dataset.get("columns")).getOrDefault("sample_id","id")));
        for(String[] row:candidates)if(!sampleComplete||samples.get(row[idAt]).decision().equals("keep"))retained.add(row);else {var sample=samples.get(row[idAt]);exclusions.add(new String[]{row[idAt],"external_qc_"+sample.reason(),Double.toString(sample.callRate())});}
        boolean stable=stable(c,dataset,originals);if(!stable){sampleComplete=false;retained=new ArrayList<>(candidates);exclusions.clear();problems.add("External QC inputs changed during validation; no population was approved");}
        summary.put("external_sample_qc_complete",sampleComplete);summary.put("external_report_sha256",hashes.getOrDefault("sample_qc_report",""));summary.put("external_data_sha256",hashes);summary.put("inputs_stable",stable);
        return new Loaded(sampleComplete,retained,exclusions,basis,pcScores,pcColumns,summary,outputs,problems,hashes);
    }

    static org.jlinalg.cli.StudyGenotypeSampleQc.Output finish(Context c,Map<String,Object> dataset,Map<String,Object> hypothesis,
            DelimitedData phenotype,Loaded external,Map<String,Object> policy,Map<String,String> originals,List<String> extraProblems)throws IOException {
        var problems=new ArrayList<>(external.problems());problems.addAll(extraProblems);var summary=new LinkedHashMap<>(external.summary());var outputs=new ArrayList<>(external.outputs());
        if(external.retained().isEmpty())problems.add("No eligible participants remain after external sample QC");
        problems.addAll(designProblems(dataset,hypothesis));List<String> pcs=org.jlinalg.study.StudyGenotypeSampleQcPolicy.pcCovariates(policy);int requested=(int)integer(policy,"pca_components");
        boolean suppliedPca=requested>0&&"supplied".equals(policy.get("pca_source"))&&!external.pcScores().isEmpty();
        summary.put("candidate_samples",external.retained().size());summary.put("pca_computed",false);summary.put("pca_origin",suppliedPca?"externally_produced":requested==0?"not_requested":"unresolved");summary.put("pca_status",suppliedPca?"externally_supplied":requested==0?"not_requested":"unresolved");summary.put("native_relatedness_computed",false);summary.put("pc_covariates",pcs);
        Path path=null;var header=new ArrayList<>(phenotype.header());if(pcs.stream().anyMatch(header::contains))problems.add("Imported PC columns collide with existing phenotype columns; no overwrite is permitted");
        if(problems.isEmpty()) {
            header.addAll(pcs);int idAt=phenotype.column(text(map(dataset.get("columns")).getOrDefault("sample_id","id")));var rows=new ArrayList<String[]>();
            for(String[] original:external.retained()){String[] row=Arrays.copyOf(original,header.size());for(int k=0;k<pcs.size();k++)row[phenotype.header().size()+k]=Double.toString(external.pcScores().get(original[idAt])[k]);rows.add(row);}
            path=c.outputDirectory().resolve("genotype-qc/phenotype.tsv");FollowupSupport.table(path,header,rows);outputs.add("genotype-qc/phenotype.tsv");
            if(suppliedPca){var pcRows=new ArrayList<String[]>();var pcHeader=new ArrayList<>(List.of("sample_id"));for(int k=1;k<=requested;k++)pcHeader.add(policy.get("pc_prefix")+Integer.toString(k));for(String[] row:external.retained()){var values=new ArrayList<String>();values.add(row[idAt]);for(double v:external.pcScores().get(row[idAt]))values.add(Double.toString(v));pcRows.add(values.toArray(String[]::new));}FollowupSupport.table(c.outputDirectory().resolve("genotype-qc/pcs.tsv"),pcHeader,pcRows);outputs.add("genotype-qc/pcs.tsv");}
        }
        boolean stable=stable(c,dataset,originals);if(!stable)problems.add("External QC inputs changed before approval; no population was approved");
        summary.put("inputs_stable",stable);summary.put("complete",problems.isEmpty());summary.put("analysis_samples",problems.isEmpty()?external.retained().size():0);summary.put("association_markers_removed",false);summary.put("patient_data_remain_local",true);summary.put("problems",problems.stream().distinct().toList());
        summary.put("method_report",StudyMethods.record("Validation and reuse of externally produced sample QC/relatedness"+(suppliedPca?" and PCA":"; no native PCA"),Map.of("external_source",methodContext(summary),"native_analysis_reexecuted",false,"reuse_rationale",policy.get("rationale")),List.of(),policy.get("rationale"),List.of("external sample QC","external PCA")));
        write(c.outputDirectory().resolve("genotype-qc-summary.yaml"),summary);outputs.add("genotype-qc-summary.yaml");return new StudyGenotypeSampleQc.Output(problems.isEmpty(),path,outputs,summary,List.of());
    }

    static List<String> designProblems(Map<String,Object> dataset,Map<String,Object> hypothesis) {
        var problems=new ArrayList<String>();var files=map(dataset.get("files"));var analysis=map(hypothesis.get("analysis"));var association=map(analysis.get("association"));var p=map(analysis.get("genotype_sample_qc"));
        boolean appropriate=("lmm".equals(association.get("model"))||"reml".equals(association.get("model"))&&!list(analysis.get("goals")).contains("association"))&&files.containsKey("grm")&&(!association.containsKey("family")||Set.of("gaussian","normal").contains(text(association.get("family"))));
        if("model".equals(p.get("relatedness_action"))&&!appropriate)problems.add("Retaining related samples requires an explicit supported quantitative GRM model and files.grm");
        if((files.containsKey("pedigree")||Boolean.TRUE.equals(map(dataset.get("metadata")).get("related_participants")))&&!appropriate)problems.add("Known pedigree/relatedness is not removed by an external QC assertion; use an appropriate dependence model");
        if(Boolean.TRUE.equals(map(dataset.get("metadata")).get("repeated_measures")))problems.add("External sample QC cannot resolve the observation/covariance model for repeated measurements");return problems;
    }
    static Map<String,Object> methodContext(Map<String,Object> summary) {
        var result=new LinkedHashMap<String,Object>();result.put("execution_origin","externally_produced");result.put("metadata_review_required",true);result.put("source_report_sha256",summary.getOrDefault("external_report_sha256",""));
        for(String key:List.of("external_source_samples","external_qc_retained_samples","external_relatedness_excluded_samples","external_evaluated_pairs","external_related_pairs","external_unresolved_pairs","external_basis_samples","external_basis_membership_present","external_independent_basis_complete","external_pca_basis_samples","external_pca_basis_scope","external_pca_reference_sha256"))if(summary.containsKey(key))result.put(key,summary.get(key));
        var methods=map(summary.get("external_method"));var sample=map(methods.get("sample_qc"));var kin=map(methods.get("relatedness"));
        var thresholds=new LinkedHashMap<String,Object>();for(String key:List.of("kinship_threshold","minimum_shared_markers","minimum_pair_heterozygotes"))if(kin.containsKey(key))thresholds.put(key,kin.get(key));if(sample.containsKey("minimum_call_rate"))thresholds.put("minimum_call_rate",sample.get("minimum_call_rate"));result.put("source_reported_compatible_thresholds",thresholds);
        result.put("source_metadata","Review the local immutable source report for original software, versions, parameters, basis, provenance and limitations before authoring Methods. Private free text is not copied into automatic Methods records.");return result;
    }
    static boolean stable(Context c,Map<String,Object> dataset,Map<String,String> originals)throws IOException {
        var files=map(dataset.get("files"));for(var entry:originals.entrySet())if(files.containsKey(entry.getKey())&&!entry.getValue().equals(FollowupSupport.hash(c.project().resolve(text(files.get(entry.getKey()))))))return false;return true;
    }
    @FunctionalInterface private interface RowConsumer {void accept(Map<String,Integer> header,String[] row)throws IOException;}
    private static void stream(Path path,Map<String,Object> policy,RowConsumer consumer)throws IOException {
        check(Files.size(path)<=integer(policy,"maximum_import_bytes"),"External QC table exceeds its registered byte bound");
        try(var reader=Files.newBufferedReader(path)){String first=line(reader);check(first!=null,"External QC table has no header");String[] names=first.split("\t",-1);check(names.length<=256,"External QC table has too many columns");var header=new HashMap<String,Integer>();for(int i=0;i<names.length;i++){safeId(names[i]);check(header.put(names[i],i)==null,"External QC header repeats a column");}
            long rows=0,cells=0;for(String value;(value=line(reader))!=null;){check(!value.isBlank(),"External QC table contains a blank row");check(++rows<=integer(policy,"maximum_import_samples"),"External QC row bound exhausted");cells+=names.length;check(cells<=integer(policy,"maximum_import_cells"),"External QC cell bound exhausted");String[] row=value.split("\t",-1);check(row.length==names.length,"External QC row width differs from its header");consumer.accept(header,row);}}
    }
    private static String line(BufferedReader reader)throws IOException {var s=new StringBuilder();int ch;while((ch=reader.read())!=-1){if(ch=='\n')break;check(s.length()<65536,"External QC line exceeds the supported length");s.append((char)ch);}if(ch==-1&&s.isEmpty())return null;if(!s.isEmpty()&&s.charAt(s.length()-1)=='\r')s.setLength(s.length()-1);return s.toString();}
    private static String cell(Map<String,Integer> header,String[] row,String name)throws IOException {check(header.containsKey(name),"External QC table is missing a required column");return row[header.get(name)];}
    private static void safeId(String s)throws IOException {check(!s.isBlank()&&s.length()<=1000&&s.chars().noneMatch(Character::isISOControl),"External QC identifiers must be unique bounded nonblank text");}
    private static void label(Map<String,Object> p,String key)throws IOException {check(p.get(key) instanceof String,"External QC method metadata is missing");safeId(text(p.get(key)));}
    private static double value(String text)throws IOException {try{double v=Double.parseDouble(text);check(Double.isFinite(v),"External QC numeric values must be finite");return v;}catch(NumberFormatException invalid){throw new IOException("External QC contains an invalid numeric value",invalid);}}
    private static double number(Map<String,Object> p,String key)throws IOException {check(p.get(key) instanceof Number,"External QC is missing a required numeric field");return value(p.get(key).toString());}
    private static long count(Map<String,Object> p,String key)throws IOException {double n=number(p,key);check(n>=0&&n==Math.rint(n)&&n<=9007199254740991L,"External QC counts must be nonnegative exact integers");return (long)n;}
    private static double probability(Map<String,Object> p,String key)throws IOException {double v=number(p,key);check(v>=0&&v<=1,"External QC probability or rate is outside [0,1]");return v;}
    private static long integer(Map<String,Object> p,String key){return ((Number)p.get(key)).longValue();}
    private static final class InvalidArtifact extends IOException {private static final long serialVersionUID=1L;InvalidArtifact(String message){super(message);}}
    private static void check(boolean condition,String message)throws IOException {if(!condition)throw new InvalidArtifact(message);}
}
