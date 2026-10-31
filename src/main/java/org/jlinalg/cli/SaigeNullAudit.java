/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.compute.BackendContext;
import org.jlinalg.compute.BackendPolicy;

/** Independent cohort, outcome, design-span and finite-model admission checks. */
final class SaigeNullAudit {
    private SaigeNullAudit() { }
    static Map<String,Object> inspect(SaigeNullInputs.Prepared inputs,SaigeRuntime.Context runtime,Path work,int timeout,Map<String,Object> session)throws Exception {
        Path original=work.resolve("original-null.rda"),ratio=work.resolve("original-null.varianceRatio.txt");
        if(!Files.isRegularFile(original,LinkOption.NOFOLLOW_LINKS)||Files.size(original)<1||Files.size(original)>1073741824L||!Files.isRegularFile(ratio,LinkOption.NOFOLLOW_LINKS)||Files.size(ratio)>1048576)throw new IOException("Original null model/variance-ratio output missing, linked or oversized");
        String modelHash=FollowupSupport.hash(original),ratioHash=FollowupSupport.hash(ratio);Path script=work.resolve("adapter-model-audit.R");
        try(var stream=SaigeNullAudit.class.getResourceAsStream("/org/jlinalg/omics/saige_model_audit.R")){if(stream==null)throw new IOException("Bundled model inspector unavailable");byte[] bytes=stream.readNBytes(1048577);if(bytes.length>1048576)throw new IOException("Oversized bundled model inspector");Files.write(script,bytes,StandardOpenOption.CREATE_NEW);}
        String scriptHash=FollowupSupport.hash(script);session.put("static_model_inspector_sha256",scriptHash);
        int exit=SaigeRuntime.execute(runtime,script,List.of(original.toString(),work.resolve("original-model.json").toString(),String.valueOf(inputs.spec().modelCells())),work,"model-audit",timeout,session);
        if(!scriptHash.equals(FollowupSupport.hash(script))||!modelHash.equals(FollowupSupport.hash(original))||!ratioHash.equals(FollowupSupport.hash(ratio)))throw new IOException("Original model, ratio or static inspector changed during inspection");
        if(exit!=0)throw new IOException("Original model inspection failed; inspect model-audit.stdout/stderr");
        Map<?,?> model=SaigeNullInputs.json(work.resolve("original-model.json"),512*1048576);Map<String,Object> audit=verify(inputs,model,ratio);
        Files.copy(original,work.resolve("null-model.rda"));Files.copy(ratio,work.resolve("variance-ratios.tsv"));
        if(!modelHash.equals(FollowupSupport.hash(work.resolve("null-model.rda")))||!ratioHash.equals(FollowupSupport.hash(work.resolve("variance-ratios.tsv"))))throw new IOException("Canonical null-model outputs differ from original bytes");
        audit.put("null_model_sha256",modelHash);audit.put("variance_ratios_sha256",ratioHash);audit.put("original_model_export_sha256",FollowupSupport.hash(work.resolve("original-model.json")));audit.put("static_model_inspector_sha256",scriptHash);return audit;
    }
    static Map<String,Object> verify(SaigeNullInputs.Prepared inputs,Map<?,?> model,Path ratio)throws Exception {
        var spec=inputs.spec();int n=inputs.sampleIds().size(),p=inputs.columns();
        if(!Boolean.TRUE.equals(model.get("converged"))||!spec.trait().equals(model.get("trait_type")))throw new IOException("Null-model convergence or trait changed");
        if(!Boolean.valueOf(spec.loco()).equals(model.get("loco"))||!Boolean.valueOf(spec.sparse()).equals(model.get("sparse_fit"))||!Boolean.valueOf(spec.covariateOffset()).equals(model.get("covariate_offset")))throw new IOException("Original null model changed explicit LOCO, sparse-fit or covariate-offset choices");
        if(!(model.get("sample_ids") instanceof List<?> ids)||!ids.equals(inputs.sampleIds()))throw new IOException("Original null model changed complete-case cohort/order");
        double[] expected=inputs.outcomes(),observed=vector(model.get("outcomes"),n),theta=vector(model.get("theta"),2);double outcomeError=0;
        for(int i=0;i<n;i++){double error=Math.abs(expected[i]-observed[i]);outcomeError=Math.max(outcomeError,error);if(error>(spec.trait().equals("binary")?0:1e-12*(1+Math.abs(expected[i]))))throw new IOException("Original null outcome differs from native explicit coding/average-rank IRNT");}
        if(theta[0]<=0||theta[1]<0||spec.trait().equals("binary")&&theta[0]!=1)throw new IOException("Invalid original residual/genetic variance components");
        double[] originalDesign=matrix(model.get("design"),n,p),nativeDesign=inputs.design();double spanError=0;
        try(BackendContext context=BackendContext.select(BackendPolicy.CPU)) {
            var qr=context.backend().dgeqp3(nativeDesign.clone(),n,p);if(qr.rank()!=p||context.backend().dgeqp3(originalDesign.clone(),n,p).rank()!=p)throw new IOException("Original/native null design is rank deficient");
            for(int column=0;column<p;column++){double[] target=new double[n];for(int i=0;i<n;i++)target[i]=originalDesign[i*p+column];double[] beta=qr.solveLeastSquares(target);for(int i=0;i<n;i++){double fitted=0;for(int j=0;j<p;j++)fitted+=nativeDesign[i*p+j]*beta[j];double error=Math.abs(fitted-target[i])/(1+Math.abs(target[i]));spanError=Math.max(spanError,error);if(error>1e-8)throw new IOException("Original design dropped/changed the declared complete-case covariate column space");}}
        }
        var residualAudits=new ArrayList<Map<String,Object>>();residualAudits.add(residuals(SaigeGrmInputs.map(model.get("base")),observed,spec.trait(),"base"));
        if(!(model.get("loco_models") instanceof List<?> loco)||loco.size()!=(spec.loco()?22:0))throw new IOException("Original null-model LOCO slot count changed");
        for(int i=0;i<loco.size();i++)residualAudits.add(residuals(SaigeGrmInputs.map(loco.get(i)),observed,spec.trait(),"autosome-"+(i+1)));
        Map<?,?> vr=SaigeGrmInputs.map(spec.parameters().get("variance_ratio"));int bins="single".equals(vr.get("kind"))?1:((List<?>)vr.get("lower_exclusive")).size();var ratios=new ArrayList<Map<String,Object>>();var seen=new HashSet<String>();
        try(var reader=Files.newBufferedReader(ratio,StandardCharsets.UTF_8)){for(String line;(line=SaigeGrmInputs.line(reader,16384))!=null;){if(line.isBlank())throw new IOException("Blank variance-ratio row");String[] f=line.strip().split("\\s+");if(f.length!=3)throw new IOException("Original variance ratio requires value,type,bin");double value=Double.parseDouble(f[0]);int bin=Integer.parseInt(f[2]);if(!Double.isFinite(value)||value<=0||bin<1||bin>bins||!Set.of("sparse","null","null_noXadj").contains(f[1])||!seen.add(f[1]+":"+bin))throw new IOException("Invalid/duplicate original variance-ratio entry");ratios.add(Map.of("value",value,"kind",f[1],"bin",bin));}}
        for(int bin=1;bin<=bins;bin++)for(String kind:List.of("null","null_noXadj"))if(!seen.contains(kind+":"+bin))throw new IOException("Missing original variance-ratio kind/bin");
        for(int bin=1;bin<=bins;bin++)if(spec.sparseRatio()&&!seen.contains("sparse:"+bin))throw new IOException("Missing requested sparse variance ratio");
        var result=new LinkedHashMap<String,Object>();result.put("status","completed-with-review-warnings");result.put("operation","fit-null");result.put("phenotype_admission",inputs.admission());result.put("cohort_samples",n);result.put("design_columns",p);result.put("native_outcome_maximum_absolute_error",outcomeError);result.put("native_design_span_maximum_scaled_error",spanError);result.put("theta",Arrays.stream(theta).boxed().toList());result.put("variance_ratios",ratios);result.put("models",residualAudits);result.put("scope","Original SAIGE GLMM/LMM and variance-ratio estimators retained. Native admission verifies explicit cohort/order/trait/IRNT, complete declared covariate span, convergence, finite variance components and all base/LOCO residual identities;it does not independently re-fit the mixed model or certify ancestry/GRM suitability.");return result;
    }
    private static Map<String,Object> residuals(Map<?,?> model,double[] y,String trait,String name)throws IOException {double[] mu=vector(model.get("fitted"),y.length),res=vector(model.get("residuals"),y.length);double maximum=0;for(int i=0;i<y.length;i++){if(trait.equals("binary")&&(mu[i]<=0||mu[i]>=1))throw new IOException("Binary null fitted probabilities reached 0/1; inspect separation/numerical convergence");double error=Math.abs(y[i]-mu[i]-res[i]);maximum=Math.max(maximum,error);if(error>1e-10*(1+Math.abs(y[i])+Math.abs(mu[i])))throw new IOException("Original null residual identity failed: "+name);}return Map.of("model",name,"maximum_residual_identity_error",maximum,"fitted_min",Arrays.stream(mu).min().orElseThrow(),"fitted_max",Arrays.stream(mu).max().orElseThrow());}
    static double[] vector(Object value,int size)throws IOException {if(!(value instanceof List<?> list)||list.size()!=size)throw new IOException("Original model vector dimension changed");double[] result=new double[size];for(int i=0;i<size;i++){if(!(list.get(i) instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IOException("Original model contains nonfinite/missing numeric values");result[i]=n.doubleValue();}return result;}
    private static double[] matrix(Object value,int rows,int columns)throws IOException {if(!(value instanceof List<?> list)||list.size()!=rows)throw new IOException("Original model design dimension changed");double[] result=new double[Math.multiplyExact(rows,columns)];for(int i=0;i<rows;i++)System.arraycopy(vector(list.get(i),columns),0,result,i*columns,columns);return result;}
}
