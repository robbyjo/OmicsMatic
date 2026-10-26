/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyAssayQcPolicy;
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.study.StudyNeed;

/** Source-population QC with fixed denominators, immutable inputs and no imputation. */
final class StudyAssayQc implements StudyOperationExecutor {
    static final String CAVEAT="Source-population measurement QC reads no phenotype, tunes no thresholds and performs no imputation. Sample missingness uses every original source feature; feature QC uses every original source sample, without iterative filtering. This does not establish assay chemistry validity, independent statistical filtering, model assumptions or FDR calibration. Native association may separately mean-impute within its analyzed population; score preprocessing must be fitted within training folds.";
    private final Map<String,Object> summary=new LinkedHashMap<>();
    private Map<String,Object> p;
    private long memory,maximumMemory,features,invalid,masked,detectionFailed,flaggedFeatures;
    private final Map<String,Long> featureReasons=new LinkedHashMap<>();
    private int n;
    private boolean supplied,filter;
    private List<String> samples;
    private final Map<String,Boolean> featureKeep=new LinkedHashMap<>();
    private boolean[] sampleKeep;
    private long[] observed,sourceMissing;
    private long[] libraries;
    private BigInteger[] overflowLibraries;
    private final Map<String,String> inputs=new LinkedHashMap<>();
    private Map<String,Decision> suppliedSamples=Map.of(),suppliedFeatures=Map.of();
    private record Decision(boolean keep,String reason) { }

    @Override public Result execute(Context c) throws IOException {
        Files.createDirectories(c.outputDirectory());
        try {return run(c);}catch(IOException|IllegalArgumentException invalidInput){
            Files.deleteIfExists(c.outputDirectory().resolve("assay-matrix.tsv"));Files.deleteIfExists(c.outputDirectory().resolve("assay-matrix.pending.tsv"));
            summary.put("complete",false);summary.put("problem",invalidInput.getMessage());summary.put("required_manuscript_caveat",CAVEAT);
            summary.put("needs",List.of(StudyNeed.review("assay-qc:"+c.operation().get("dataset_id"),"What is needed for assay QC?",invalidInput.getMessage(),true).document()));
            return finish(c,false,"Assay QC prerequisites are incomplete; downstream matrix use is held");
        }
    }
    private Result run(Context c) throws IOException {
        String id=text(c.operation().get("dataset_id"));var d=StudyBioinformatics.datasets(c).stream().filter(v->id.equals(v.get("id"))).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown assay dataset"));
        if(!StudyAssayQcPolicy.MODALITIES.contains(d.get("type")))throw new IOException("Assay QC needs a supported bulk molecular matrix");
        p=c.operation().containsKey("assay_qc_policy")?StudyAssayQcPolicy.resolve(map(c.operation().get("assay_qc_policy"))):StudyAssayQcPolicy.forDataset(StudyAssayQcPolicy.resolve(map(map(StudyBioinformatics.hypothesis(c).get("analysis")).get("assay_qc"))),id);
        p=new LinkedHashMap<>(p);p.remove("dataset_overrides");supplied="supplied".equals(p.get("source"));filter=supplied||"filter".equals(p.get("mode"));maximumMemory=integer("maximum_retained_mib")*1048576;
        Path matrix=c.project().resolve(text(map(d.get("files")).get("matrix")));
        checkFile(matrix);String matrixHash=FollowupSupport.hash(matrix);inputs.put("matrix",matrixHash);
        summary.put("schema_version",1);summary.put("dataset_id",id);summary.put("source",p.get("source"));summary.put("mode",supplied?"supplied_decisions":p.get("mode"));summary.put("value_scale",p.get("value_scale"));summary.put("policy_sha256",StudyAssayQcPolicy.fingerprint(p));summary.put("matrix_sha256",matrixHash);summary.put("required_manuscript_caveat",CAVEAT);summary.put("imputation","none");
        if(supplied)loadSupplied(c,id,matrixHash);
        Path detection=null;
        if(!supplied&&!"none".equals(p.get("detection_kind"))){String role=text(map(d.get("files")).get("assay_detection"));if(role.isBlank())throw new IOException("Declared detection QC needs files.assay_detection");detection=c.project().resolve(role);checkFile(detection);inputs.put("detection",FollowupSupport.hash(detection));}
        try(var source=new StudyAssayMatrix(matrix,p);var detections=detection==null?null:new StudyAssayMatrix(detection,p)) {
            samples=source.matrixSamples(p);n=samples.size();charge(512L*n+4*integer("maximum_record_mib")*1048576);
            if(detections!=null&&!samples.equals(detections.matrixSamples(p)))throw new IOException("Detection matrix sample IDs/order must exactly match the source");
            sampleKeep=new boolean[n];observed=new long[n];sourceMissing=new long[n];libraries=new long[n];overflowLibraries=new BigInteger[n];
            if(supplied&&!suppliedSamples.keySet().equals(new HashSet<>(samples)))throw new IOException("Supplied sample decisions must cover exactly the current matrix samples");
            try(var output=Files.newBufferedWriter(c.outputDirectory().resolve("assay-feature-qc.tsv"),StandardCharsets.UTF_8)) {
                row(output,List.of("feature_id","source_samples","observed","missing","invalid","detection_failed","mean","sd","value_scale_factor","scaled_mean","scaled_sd","log_sd","constant","eligible","decision","reason","statistics_origin"));
                List<String> values;
                while((values=source.next())!=null){
                    if(++features>integer("maximum_features")||features>integer("maximum_cells")/n)throw new IOException("Assay matrix exceeds maximum_features/maximum_cells");
                    if(values.size()!=n+1)throw new IOException("Assay matrix row width does not match the sample header");String feature=values.get(0).trim();
                    if(!StudyAssayMatrix.validId(feature)||featureKeep.containsKey(feature))throw new IOException("Assay feature IDs must be unique, nonblank and free of control characters");charge(512+4L*feature.length());
                    var detect=detectionRow(detections,feature);
                    if(supplied){var decision=suppliedFeatures.get(feature);if(decision==null)throw new IOException("Supplied feature decisions do not cover the current matrix");featureKeep.put(feature,decision.keep());
                        if(decision.keep())for(int i=0;i<n;i++)if(suppliedSamples.get(samples.get(i)).keep())value(values.get(i+1),true);
                        row(output,List.of(feature,String.valueOf(n),"NA","NA","NA","NA","NA","NA","NA","NA","NA","NA","NA","NA",decision.keep()?"keep":"exclude",decision.reason(),"supplied_decision_no_native_rethresholding"));continue;}
                    var numeric=new double[n];long bad=0,failed=0,missing=0;double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
                    for(int i=0;i<n;i++){String raw=values.get(i+1);double v=value(raw,false);boolean isBad=!isMissing(raw)&&Double.isNaN(v);boolean detectionBad=detect!=null&&!detected(detect.get(i+1));
                        if(isBad){bad++;invalid++;}if(detectionBad){failed++;detectionFailed++;}if(isMissing(raw))sourceMissing[i]++;
                        if(isBad||detectionBad){if(!Double.isNaN(v)||isBad){masked++;}v=Double.NaN;}
                        numeric[i]=v;if(Double.isNaN(v)){missing++;}else{observed[i]++;min=Math.min(min,v);max=Math.max(max,v);if("counts".equals(p.get("value_scale")))addCount(i,(long)v);}}
                    long count=n-missing;Moments stats=moments(numeric,min,max,count);var reasons=new ArrayList<String>();
                    if(missing/(double)n>number("maximum_feature_missing"))reasons.add("missingness");if(count<integer("minimum_observed"))reasons.add("too_few_observed");if(Boolean.TRUE.equals(p.get("drop_constant"))&&stats.constant)reasons.add("constant");
                    if(number("minimum_sd")>0&&(count<2||stats.logSd<Math.log(number("minimum_sd"))))reasons.add("low_sd");boolean eligible=reasons.isEmpty(),keep=!filter||eligible;if(!eligible)flaggedFeatures++;for(String reason:reasons)featureReasons.merge(reason,1L,Long::sum);featureKeep.put(feature,keep);
                    row(output,List.of(feature,String.valueOf(n),String.valueOf(count),String.valueOf(missing),String.valueOf(bad),String.valueOf(failed),display(stats.mean),display(stats.sd),display(stats.scale),display(stats.scaledMean),display(stats.scaledSd),display(stats.logSd),String.valueOf(stats.constant),String.valueOf(eligible),keep?"keep":"exclude",String.join(";",reasons),"native_source_population"));
                }
                if(detections!=null&&detections.next()!=null)throw new IOException("Detection matrix has extra features");
            }
        }
        if(features==0)throw new IOException("Assay source has no features");
        if(supplied){if(!suppliedFeatures.keySet().equals(featureKeep.keySet()))throw new IOException("Supplied feature decisions must cover exactly the current matrix features");var sourceReport=map(summary.get("supplied_method_report"));if(integerField(sourceReport,"sample_count")!=n||integerField(sourceReport,"feature_count")!=features)throw new IOException("Supplied report sample_count/feature_count must match the current matrix");}
        int retainedSamples=0,flaggedSamples=0;try(var output=Files.newBufferedWriter(c.outputDirectory().resolve("assay-sample-qc.tsv"),StandardCharsets.UTF_8)){
            row(output,List.of("sample_id","source_features","observed","missing","missing_fraction","source_missing","library_size","eligible","decision","reason","statistics_origin"));
            for(int i=0;i<n;i++){long missing=features-observed[i];boolean eligible=missing/(double)features<=number("maximum_sample_missing");String reason=eligible?"":"missingness";
                if(supplied){var decision=suppliedSamples.get(samples.get(i));sampleKeep[i]=decision.keep();reason=decision.reason();}else sampleKeep[i]=!filter||eligible;
                if(sampleKeep[i])retainedSamples++;if(!supplied&&!eligible)flaggedSamples++;
                row(output,List.of(samples.get(i),String.valueOf(features),supplied?"NA":String.valueOf(observed[i]),supplied?"NA":String.valueOf(missing),supplied?"NA":display(missing/(double)features),supplied?"NA":String.valueOf(sourceMissing[i]),!supplied&&"counts".equals(p.get("value_scale"))?library(i):"NA",supplied?"NA":String.valueOf(eligible),sampleKeep[i]?"keep":"exclude",reason,supplied?"supplied_decision_no_native_rethresholding":"native_source_population"));}
        }
        long retainedFeatures=featureKeep.values().stream().filter(Boolean::booleanValue).count();
        summary.put("source_samples",n);summary.put("source_features",features);summary.put("retained_samples",retainedSamples);summary.put("retained_features",retainedFeatures);summary.put("excluded_samples",n-retainedSamples);summary.put("excluded_features",features-retainedFeatures);
        if(!supplied){summary.put("flagged_features",flaggedFeatures);summary.put("flagged_samples",flaggedSamples);summary.put("feature_flag_counts",featureReasons);summary.put("invalid_values",invalid);summary.put("detection_failed_values",detectionFailed);summary.put("proposed_masked_values",masked);summary.put("applied_masked_values",filter?masked:0);summary.put("parameters",p);}
        if(!supplied&&"counts".equals(p.get("value_scale")))summary.put("library_size_definition","Exact sum of valid, detection-passing source counts over all original source features; not normalization or depth calibration");
        summary.put("sample_missingness_denominator","all original source features, fixed before feature decisions");summary.put("feature_statistics_population","all original source samples, before sample decisions");
        if(!supplied&&invalid>0&&"hold".equals(p.get("invalid_values")))throw new IOException("Invalid/out-of-scale assay values require correction or explicit native filter plus invalid_values: mask");
        if(retainedSamples==0||retainedFeatures==0)throw new IOException("QC leaves no eligible samples or features; no derived matrix was promoted");
        if(filter)materialize(c,matrix,detection);
        checkStable(matrix,matrixHash);if(detection!=null)checkStable(detection,inputs.get("detection"));
        for(String key:List.of("supplied_report","supplied_samples","supplied_features"))if(supplied)checkStable(c.project().resolve(text(p.get(key))),inputs.get(key));
        summary.put("input_sha256",inputs);summary.put("complete",true);summary.put("analysis_matrix",filter?"assay-matrix.tsv":"original_source");
        if(filter)summary.put("analysis_matrix_sha256",FollowupSupport.hash(c.outputDirectory().resolve("assay-matrix.tsv")));
        summary.put("method_report",Map.of("performed",true,"complete",true,"name",supplied?"import_complete_assay_qc_decisions":"source_assay_qc","software","JLinAlg "+JLinAlgCli.version(),"interpretation",supplied?"Source-declared QC decisions, not independently certified; native thresholds were not reapplied":CAVEAT));
        return finish(c,true,supplied?"Applied complete supplied assay QC decisions":"Completed source-population assay QC");
    }
    private void loadSupplied(Context c,String id,String hash) throws IOException {
        Path folder=c.outputDirectory().resolve("supplied-inputs");Files.createDirectories(folder);
        for(String key:List.of("supplied_report","supplied_samples","supplied_features")){Path path=c.project().resolve(text(p.get(key)));checkFile(path);if(key.equals("supplied_report")&&Files.size(path)>8388608)throw new IOException("Supplied assay report exceeds 8 MiB");inputs.put(key,FollowupSupport.hash(path));String suffix=key.equals("supplied_report")?".yaml":path.toString().toLowerCase(Locale.ROOT).endsWith(".csv")?".csv":".tsv";Files.copy(path,folder.resolve(key+suffix));}
        var report=StudyYaml.loadArtifact(folder.resolve("supplied_report.yaml"));
        if(integerField(report,"schema_version")!=1||!id.equals(report.get("dataset_id"))||!hash.equals(report.get("matrix_sha256"))||!Set.of("before_qc","post_qc").contains(report.get("input_state")))throw new IOException("Supplied QC report must identify schema 1, exact dataset/matrix and before_qc/post_qc input_state");
        var method=map(report.get("method"));if(text(method.get("name")).isBlank()||text(method.get("version")).isBlank()||!(method.get("parameters") instanceof Map))throw new IOException("Supplied QC report needs method name, version and parameters");
        summary.put("supplied_method_report",report);summary.put("appraisal_status","source_declared_not_independently_certified");
        suppliedSamples=decisions(c.project().resolve(text(p.get("supplied_samples"))),"sample_id",integer("maximum_samples"));suppliedFeatures=decisions(c.project().resolve(text(p.get("supplied_features"))),"feature_id",integer("maximum_features"));
    }
    private Map<String,Decision> decisions(Path file,String id,long maximum) throws IOException {
        var result=new LinkedHashMap<String,Decision>();try(var reader=new StudyAssayMatrix(file,p)){int at=reader.header.indexOf(id),decision=reader.header.indexOf("decision"),reason=reader.header.indexOf("reason");if(at<0||decision<0||new HashSet<>(reader.header).size()!=reader.header.size())throw new IOException("Supplied decisions need unique "+id+", decision and optional reason columns");List<String> row;
            while((row=reader.next())!=null){if(row.size()!=reader.header.size())throw new IOException("Supplied decision row width mismatch");String key=row.get(at).trim(),value=row.get(decision).trim();if(!StudyAssayMatrix.validId(key)||!Set.of("keep","exclude").contains(value)||result.containsKey(key))throw new IOException("Supplied decisions need unique IDs and keep/exclude decisions");String why=reason<0?"":row.get(reason);charge(512+4L*(key.length()+why.length()));if(result.size()>=maximum)throw new IOException("Supplied decisions exceed row bound");result.put(key,new Decision(value.equals("keep"),why));}}
        return result;
    }
    private void materialize(Context c,Path matrix,Path detection) throws IOException {
        Path pending=c.outputDirectory().resolve("assay-matrix.pending.tsv");long appliedMasks=0;
        try(var source=new StudyAssayMatrix(matrix,p);var detections=detection==null?null:new StudyAssayMatrix(detection,p);var output=Files.newBufferedWriter(pending,StandardCharsets.UTF_8)){
            if(!samples.equals(source.matrixSamples(p))||detections!=null&&!samples.equals(detections.matrixSamples(p)))throw new IOException("Assay source sample header changed during QC");
            var header=new ArrayList<String>();header.add("feature_id");for(int i=0;i<n;i++)if(sampleKeep[i])header.add(samples.get(i));row(output,header);List<String> values;long count=0;
            for(var entry:featureKeep.entrySet()){values=source.next();if(values==null||values.size()!=n+1||!entry.getKey().equals(values.get(0).trim()))throw new IOException("Assay feature order changed during QC");count++;var detect=detectionRow(detections,entry.getKey());if(!entry.getValue())continue;
                var out=new ArrayList<String>();out.add(entry.getKey());for(int i=0;i<n;i++)if(sampleKeep[i]){String raw=values.get(i+1);double v=value(raw,supplied);if(detect!=null&&!detected(detect.get(i+1)))v=Double.NaN;if(!supplied&&!isMissing(raw)&&Double.isNaN(v))appliedMasks++;out.add(Double.isNaN(v)?"NA":Double.toString(v));}row(output,out);}
            if(count!=features||source.next()!=null||detections!=null&&detections.next()!=null)throw new IOException("Assay source feature scope changed during QC");
        }
        Files.move(pending,c.outputDirectory().resolve("assay-matrix.tsv"),StandardCopyOption.ATOMIC_MOVE);if(!supplied)summary.put("applied_masked_values",appliedMasks);
    }
    private List<String> detectionRow(StudyAssayMatrix reader,String feature) throws IOException {if(reader==null)return null;var row=reader.next();if(row==null||row.size()!=n+1||!feature.equals(row.get(0).trim()))throw new IOException("Detection matrix features/order must exactly match the source");return row;}
    private boolean detected(String raw) throws IOException {
        if(isMissing(raw))return false;
        try {double v=Double.parseDouble(raw.trim());if(!Double.isFinite(v))throw new NumberFormatException();var decimal=new BigDecimal(raw.trim());
            if(decimal.signum()<0||decimal.compareTo(BigDecimal.ONE)>0)throw new NumberFormatException();
            if("detected".equals(p.get("detection_kind"))){if(decimal.signum()!=0&&decimal.compareTo(BigDecimal.ONE)!=0)throw new NumberFormatException();return decimal.compareTo(BigDecimal.ONE)==0;}
            return decimal.compareTo(BigDecimal.valueOf(number("detection_threshold")))<=0;
        }catch(NumberFormatException invalid){throw new IOException("Detection values must be decimal probabilities in [0,1] or exact binary detected flags");}
    }
    private double value(String raw,boolean strict) throws IOException {
        if(isMissing(raw))return Double.NaN;double v;boolean valid=true;
        try {v=Double.parseDouble(raw.trim());valid=Double.isFinite(v);String scale=text(p.get("value_scale"));if((scale.equals("counts")||scale.equals("nonnegative")||scale.equals("proportion"))&&v<0)valid=false;if(scale.equals("proportion")&&(v>1||new BigDecimal(raw.trim()).compareTo(BigDecimal.ONE)>0))valid=false;
            if(scale.equals("counts")&&valid){if(v>9007199254740991L)valid=false;else{var decimal=new BigDecimal(raw.trim()).stripTrailingZeros();if(decimal.scale()>0||decimal.signum()<0||decimal.compareTo(BigDecimal.valueOf(9007199254740991L))>0)valid=false;}}
            else if(v==0&&new BigDecimal(raw.trim()).signum()!=0)valid=false;
        }catch(NumberFormatException|ArithmeticException invalidValue){v=Double.NaN;valid=false;}
        if(!valid){if(strict)throw new IOException("Retained supplied assay values must satisfy the declared finite/value-scale contract; QC decisions are not recomputed");return Double.NaN;}return v;
    }
    private record Moments(double mean,double sd,double scale,double scaledMean,double scaledSd,double logSd,boolean constant) { }
    private static Moments moments(double[] values,double min,double max,long count){
        if(count==0)return new Moments(Double.NaN,Double.NaN,0,Double.NaN,Double.NaN,Double.NaN,false);
        boolean constant=min==max;double scale=Math.max(Math.abs(min),Math.abs(max));if(scale==0)return new Moments(0,count>1?0:Double.NaN,0,0,count>1?0:Double.NaN,count>1?Double.NEGATIVE_INFINITY:Double.NaN,true);
        double sum=0,correction=0;for(double v:values)if(Double.isFinite(v)){double delta=Double.isFinite(v-min)?(v-min)/scale:v/scale-min/scale;double y=delta-correction,t=sum+y;correction=(t-sum)-y;sum=t;}double mean=sum/count;
        double squares=0;correction=0;for(double v:values)if(Double.isFinite(v)){double delta=Double.isFinite(v-min)?(v-min)/scale:v/scale-min/scale;double residual=delta-mean,y=residual*residual-correction,t=squares+y;correction=(t-squares)-y;squares=t;}
        double sd=count>1?Math.sqrt(squares/(count-1)):Double.NaN;double logSd=sd>0?Math.log(scale)+Math.log(sd):sd==0?Double.NEGATIVE_INFINITY:Double.NaN;
        double scaledMean=min/scale+mean;double actualMean=Double.isFinite(scale*mean)?min+scale*mean:scale*scaledMean;
        return new Moments(actualMean,scale*sd,scale,scaledMean,sd,logSd,constant);
    }
    private void addCount(int i,long v){if(overflowLibraries[i]!=null){overflowLibraries[i]=overflowLibraries[i].add(BigInteger.valueOf(v));return;}try{libraries[i]=Math.addExact(libraries[i],v);}catch(ArithmeticException overflow){overflowLibraries[i]=BigInteger.valueOf(libraries[i]).add(BigInteger.valueOf(v));}}
    private String library(int i){return overflowLibraries[i]==null?String.valueOf(libraries[i]):overflowLibraries[i].toString();}
    private static boolean isMissing(String raw){return Set.of("","na","nan","null",".").contains(raw.trim().toLowerCase(Locale.ROOT));}
    private static String display(double value){return Double.isNaN(value)?"NA":value==Double.POSITIVE_INFINITY?"overflow":value==Double.NEGATIVE_INFINITY?"-Infinity":Double.toString(value);}
    private long integer(String key){return ((Number)p.get(key)).longValue();}
    private double number(String key){return ((Number)p.get(key)).doubleValue();}
    private static long integerField(Map<String,Object> report,String key) throws IOException {
        Object raw=report.get(key);if(!(raw instanceof Number v)||!Double.isFinite(v.doubleValue()))throw new IOException("Supplied QC report needs nonnegative integer "+key);
        try {long exact=new BigDecimal(v.toString()).longValueExact();if(exact<0)throw new ArithmeticException();return exact;}
        catch(NumberFormatException|ArithmeticException invalid){throw new IOException("Supplied QC report needs an exact nonnegative signed-long integer "+key);}
    }
    private void charge(long bytes) throws IOException {memory=Math.addExact(memory,bytes);if(memory>maximumMemory)throw new IOException("Assay metadata/record reserve exceeds maximum_retained_mib");}
    private void checkFile(Path path) throws IOException {if(!Files.isRegularFile(path)||Files.size(path)>integer("maximum_source_mib")*1048576)throw new IOException("Assay input missing or exceeds maximum_source_mib");}
    private static void checkStable(Path path,String hash) throws IOException {if(!Files.isRegularFile(path)||!hash.equals(FollowupSupport.hash(path)))throw new IOException("Assay source changed during QC");}
    private static void row(BufferedWriter output,List<String> cells) throws IOException {for(int i=0;i<cells.size();i++){if(i>0)output.write('\t');output.write(StudyAssayMatrix.cell(cells.get(i)));}output.newLine();}
    private Result finish(Context c,boolean complete,String message) throws IOException {
        write(c.outputDirectory().resolve("assay-qc.yaml"),summary);List<String> outputs;
        try(var paths=Files.walk(c.outputDirectory())){outputs=paths.filter(Files::isRegularFile).map(path->c.outputDirectory().relativize(path).toString().replace('\\','/')).sorted().toList();}
        return new Result(complete?State.COMPLETE:State.WAITING_FOR_INPUT,message,outputs,summary);
    }
}
