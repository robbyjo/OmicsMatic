/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import org.jlinalg.genetics.AncestryMixtureLikelihood;

/** Identity-preserving original matrices, native likelihood audit and projection models. */
final class AdmixtureOutputs {
    private AdmixtureOutputs() { }
    static final class Model {
        final Path p;final List<String> labels;final Map<Path,String> immutable;
        Model(Path p,List<String> labels,Map<Path,String> immutable){this.p=p;this.labels=List.copyOf(labels);this.immutable=Map.copyOf(immutable);}
        void verify()throws IOException {for(var e:immutable.entrySet())if(!FollowupSupport.hash(e.getKey()).equals(e.getValue()))throw new IOException("ADMIXTURE projection model changed: "+e.getKey());}
    }
    static Model model(Path path,AdmixtureInputs.Spec spec,Path work)throws IOException {
        Path home=path.toAbsolutePath().normalize();Map<?,?> state=AdmixtureInputs.json(home.resolve("analysis-status.json"),1048576),session=AdmixtureInputs.json(home.resolve("session.json"),8*1048576),results=AdmixtureInputs.json(home.resolve("results.json"),1048576),m=AdmixtureInputs.json(home.resolve("model.json"),8*1048576);
        if(!"completed-with-review-warnings".equals(state.get("status"))||!"ADMIXTURE".equals(session.get("tool"))||!Set.of("fit","supervised").contains(session.get("operation")))throw new IOException("Projection requires a completed typed ADMIXTURE fit/supervised bundle");
        if(!Objects.equals(results.get("model_sha256"),FollowupSupport.hash(home.resolve("model.json"))))throw new IOException("Projection model metadata checksum differs");
        if(AdmixtureInputs.whole(m,"schema_version",1,1)!=1||!"ADMIXTURE-trained-QP".equals(m.get("kind"))||AdmixtureInputs.whole(m,"k",1,1024)!=spec.k()||!spec.version().equals(m.get("version"))||!spec.assembly().equals(m.get("assembly")))throw new IOException("Projection K,version or assembly differs from its fitted model");
        boolean samePloidy=AdmixtureInputs.whole(m,"model_ploidy",1,2)==spec.ploidy()&&spec.coding().equals(m.get("genotype_coding"));
        if(!samePloidy&&!spec.projectionPloidy().equals("explicit-target-model"))throw new IOException("Projection ploidy/coding differs; require a reviewed explicit-target-model policy rather than inferring conversion");
        Map<?,?> hashes=AdmixtureInputs.map(m.get("files"));if(!hashes.keySet().equals(Set.of("original.P","cohort.bim","component-labels.tsv")))throw new IOException("Projection model member family differs");
        var immutable=new LinkedHashMap<Path,String>();
        for(String name:List.of("analysis-status.json","session.json","results.json","model.json","manifest.yaml")){Path p=home.resolve(name);AdmixtureInputs.regular(p,spec.limits().bytes());immutable.put(p,FollowupSupport.hash(p));}
        Map<?,?> manifest;
        try(var r=Files.newBufferedReader(home.resolve("manifest.yaml"))){Object value=new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions())).load(r);manifest=AdmixtureInputs.map(value);}
        if(!"original-admixture-typed-audited-operation".equals(manifest.get("operation"))||!"completed-with-review-warnings".equals(manifest.get("analysis_status")))throw new IOException("Projection input is not a completed typed bundle");
        Map<?,?> published=AdmixtureInputs.map(manifest.get("output_sha256"));
        for(String name:List.of("model.json","original.P","cohort.bim","component-labels.tsv")){
            Path p=home.resolve(name);AdmixtureInputs.regular(p,spec.limits().bytes());String h=FollowupSupport.hash(p);if(!h.equals(published.get(name))||!name.equals("model.json")&&!h.equals(hashes.get(name)))throw new IOException("Projection member changed: "+name);immutable.put(p,h);
        }
        if(!FollowupSupport.hash(work.resolve("cohort.bim")).equals(hashes.get("cohort.bim")))throw new IOException("Projection requires byte-identical BIM marker/allele/order metadata; harmonization must be explicit before fitting");
        if(!(m.get("component_labels") instanceof List<?> list)||list.size()!=spec.k())throw new IOException("Invalid projection component-label family");var labels=new ArrayList<String>();for(Object value:list){if(!(value instanceof String s)||s.chars().anyMatch(c->c<32))throw new IOException("Invalid projection component label");labels.add(s);}
        return new Model(home.resolve("original.P"),labels,immutable);
    }
    static Map<String,Object> audit(AdmixtureInputs.Prepared p,Path work)throws IOException {
        var s=p.spec();long bytes=0;try(var files=Files.list(work)){for(Path f:files.toList())if(Files.isRegularFile(f)){bytes=Math.addExact(bytes,Files.size(f));if(bytes>s.limits().output())throw new IOException("ADMIXTURE output byte bound exceeded");}}
        long logBound=Math.min(64L*1048576,s.limits().retained()/4);String stdout=new String(AdmixtureInputs.raw(work.resolve("fit.stdout"),logBound),java.nio.charset.StandardCharsets.UTF_8);if(!stdout.contains("ADMIXTURE Version "+s.version()))throw new IOException("Original ADMIXTURE version header differs");
        if(!Pattern.compile("(?m)^Size of G: "+p.samples().size()+"x"+p.markers().size()+"\\s*$").matcher(stdout).find())throw new IOException("Original genotype dimensions differ from admitted inputs");
        if(!Pattern.compile("(?m)^Random seed: "+s.seed()+"\\s*$").matcher(stdout).find())throw new IOException("Original random seed differs");
        double reported=scalar(stdout,"(?m)^Loglikelihood: ([^\\r\\n]+)$",true),objective=scalar(stdout,"(?m)^Objective function: ([^\\r\\n]+)$",s.lambda()>0);
        double cv=scalar(stdout,"(?m)^CV error \\(K="+s.k()+"\\): ([^\\r\\n]+)$",s.cv()>0);if(s.cv()>0&&cv<0)throw new IOException("Invalid negative CV error");
        int start=stdout.indexOf("Starting main algorithm"),end=stdout.indexOf("Summary:",Math.max(0,start));if(start<0||end<start)throw new IOException("Missing original point-estimation completion section");
        var deltas=Pattern.compile("\\(delta\\): ([^\\s]+)").matcher(stdout.substring(start,end));double lastDelta=Double.NaN;String deltaToken="";while(deltas.find()){deltaToken=deltas.group(1);lastDelta=finite(deltaToken);}
        if(!Double.isFinite(lastDelta))throw new IOException("No finite point-estimation stopping diagnostic");
        double iterations=scalar(stdout,"(?m)^Converged in ([0-9]+) iterations.*$",true);
        if(s.major().kind().equals("delta")&&deltaLower(deltaToken).compareTo(java.math.BigDecimal.valueOf(s.major().value()))>0)throw new IOException("Original final point delta does not satisfy the declared tolerance at printed precision; log wording alone is insufficient");
        if(s.major().kind().equals("iterations")&&iterations!=s.major().value())throw new IOException("Original point iteration count differs from the explicit fixed stop");
        var replicates=Pattern.compile("(?m)^Bootstrap replicate #([0-9]+)\\s*$").matcher(stdout);int index=0;while(replicates.find()){if(Integer.parseInt(replicates.group(1))!=index++)throw new IOException("Bootstrap replicate sequence is incomplete or duplicated");}if(index!=s.boot())throw new IOException("Bootstrap replicate count differs from recipe");
        String base="cohort."+s.k();String qName=parameterName(work,base,"Q",s.lambda()>0),pName=parameterName(work,base,"P",s.lambda()>0);
        if(!qName.substring((base+".Q").length()).equals(pName.substring((base+".P").length())))throw new IOException("Original Q/P penalty suffixes differ");
        double[] q=matrix(work.resolve(qName),p.samples().size(),s.k(),0,1,s.limits().line(),true);
        var evaluator=new AncestryMixtureLikelihood.Evaluator(q,p.samples().size(),s.k(),0.0000005,false);
        try(var reader=Files.newBufferedReader(work.resolve(pName));var data=p.open()){
            for(var marker:p.markers()) {double[] row=matrixRow(reader,s.k(),0,1,s.limits().line(),true),calls=data.readAllele1Dosages(marker);for(int i=0;i<calls.length;i++)if(!Double.isNaN(calls[i]))calls[i]=2-calls[i];evaluator.addMarker(calls,row,s.ploidy(),0.0000005);}
            if(AdmixtureInputs.line(reader,s.limits().line())!=null)throw new IOException("Extra P marker rows");
        }
        var nativeReport=evaluator.summary(s.lambda(),s.epsilon());if(!nativeReport.containsLikelihood(reported,0.0000005))throw new IOException("Original likelihood lies outside native counted-BIM-allele2 printed-parameter bounds");
        if(s.lambda()>0&&(objective+0.0000005<nativeReport.objectiveLower()||objective-0.0000005>nativeReport.objectiveUpper()))throw new IOException("Original penalized objective differs from native declared lambda/epsilon bounds");
        if(nativeReport.precisionUnboundedCells()>0&&!s.allowUnbounded())throw new IOException("Printed zero produces unbounded likelihood uncertainty; explicitly review retain-unbounded-with-warning or improve parameter precision");
        if(p.model()!=null&&Files.mismatch(p.model().p,work.resolve(pName))!=-1)throw new IOException("Projection changed its fixed learned P matrix");
        List<String> labels=labels(p,q);writeQ(p,work.resolve(qName),work.resolve("Q.tsv"));writeP(p,work.resolve(pName),work.resolve("P.tsv"));
        Files.copy(work.resolve(qName),work.resolve("original.Q"));Files.copy(work.resolve(pName),work.resolve("original.P"));
        if(s.boot()>0){String se=auxiliary(work,base,qName,"se"),bias=auxiliary(work,base,qName,"bias");matrix(work.resolve(se),p.samples().size(),s.k(),0,1,s.limits().line(),false);matrix(work.resolve(bias),p.samples().size(),s.k(),-1,1,s.limits().line(),false);writeQ(p,work.resolve(se),work.resolve("Q-standard-errors.tsv"));writeQ(p,work.resolve(bias),work.resolve("Q-bias.tsv"));}
        try(var w=Files.newBufferedWriter(work.resolve("component-labels.tsv"))){w.write("component\tcaller_reference_label\torigin\n");for(int i=0;i<labels.size();i++)FollowupSupport.row(w,new String[]{"C"+(i+1),labels.get(i),p.model()!=null?"fixed-training-model":s.operation().equals("supervised")?"caller-supervision":"anonymous"});}
        var audit=new LinkedHashMap<String,Object>();audit.put("status","passed-native-identity-and-likelihood-audit");audit.put("samples",p.samples().size());audit.put("markers",p.markers().size());audit.put("components",s.k());audit.put("counted_allele","BIM allele2");audit.put("original_loglikelihood",reported);if(s.lambda()>0)audit.put("original_penalized_objective",objective);if(s.cv()>0)audit.put("original_cv_error",cv);audit.put("bootstrap_replicates",index);audit.put("last_point_delta",lastDelta);audit.put("point_iterations",iterations);audit.put("stop_kind",s.major().kind());audit.put("fixed_iteration_stop_certifies_convergence",false);audit.put("native_loglikelihood",value(nativeReport.logLikelihood()));audit.put("native_likelihood_lower",value(nativeReport.likelihoodLower()));audit.put("native_likelihood_upper",value(nativeReport.likelihoodUpper()));audit.put("native_objective_lower",value(nativeReport.objectiveLower()));audit.put("native_objective_upper",value(nativeReport.objectiveUpper()));audit.put("precision_unbounded_cells",nativeReport.precisionUnboundedCells());audit.put("central_probability_clips",nativeReport.centralProbabilityClips());audit.put("observed_genotype_cells",nativeReport.observedCells());audit.put("missing_genotype_cells",nativeReport.missingCells());audit.put("observed_model_alleles",nativeReport.observedAlleles());audit.put("original_P_filename",pName);audit.put("original_Q_filename",qName);audit.put("component_labels",labels);audit.put("scope","Native input identities and supplied-parameter likelihood compatibility. Original fitting,CV predictions and bootstrap calibration remain upstream; neither global optimum nor ancestry interpretation is certified.");
        audit.put("last_point_delta_token",deltaToken);audit.put("absolute_point_delta_lower_from_printed_precision",deltaLower(deltaToken).toEngineeringString());audit.put("point_delta_minimum_print_significant_digits",6);
        if(!s.operation().equals("project")){
            var model=new LinkedHashMap<String,Object>();model.put("schema_version",1);model.put("kind","ADMIXTURE-trained-QP");model.put("k",s.k());model.put("version",s.version());model.put("assembly",s.assembly());model.put("model_ploidy",s.ploidy());model.put("genotype_coding",s.coding());model.put("component_labels",labels);model.put("source_recipe_sha256",s.recipeHash());var files=new TreeMap<String,String>();for(String name:List.of("original.P","cohort.bim","component-labels.tsv"))files.put(name,FollowupSupport.hash(work.resolve(name)));model.put("files",files);Files.writeString(work.resolve("model.json"),RegenieRuntime.json(model)+"\n");audit.put("model_sha256",FollowupSupport.hash(work.resolve("model.json")));
        }
        return audit;
    }
    static java.math.BigDecimal deltaLower(String token)throws IOException {
        try {
            var value=new java.math.BigDecimal(token).abs();if(value.signum()==0)return java.math.BigDecimal.ZERO;
            int order=Math.subtractExact(Math.subtractExact(value.precision(),value.scale()),1),digits=Math.max(6,value.precision());
            var radius=java.math.BigDecimal.ONE.scaleByPowerOfTen(Math.addExact(Math.subtractExact(order,digits),1)).multiply(new java.math.BigDecimal("0.5"));return value.subtract(radius).max(java.math.BigDecimal.ZERO);
        }catch(NumberFormatException|ArithmeticException e){throw new IOException("Invalid bounded original delta precision",e);}
    }
    private static List<String> labels(AdmixtureInputs.Prepared p,double[] q)throws IOException {
        int k=p.spec().k();if(p.model()!=null)return p.model().labels;var result=new ArrayList<String>(Collections.nCopies(k,""));if(!p.spec().operation().equals("supervised"))return result;
        var components=new HashMap<String,Integer>();for(int i=0;i<p.samples().size();i++){
            String label=p.labels().get(i);if(label.equals("-"))continue;int best=0;for(int j=1;j<k;j++)if(q[i*k+j]>q[i*k+best])best=j;
            for(int j=0;j<k;j++){double expected=j==best?1-(k-1)*0.00001:0.00001;if(Math.abs(q[i*k+j]-expected)>0.000000500001)throw new IOException("Supervised reference row differs from original numeric-floor contract");}
            Integer old=components.putIfAbsent(label,best);if(old!=null&&old!=best||!result.get(best).isEmpty()&&!result.get(best).equals(label))throw new IOException("Supervised reference-label/component identity is inconsistent");result.set(best,label);
        }
        if(components.size()!=k||result.stream().anyMatch(String::isEmpty))throw new IOException("Incomplete supervised component anchors");return result;
    }
    private static Object value(double x){return Double.isFinite(x)?x:Double.toString(x);}
    private static String parameterName(Path directory,String base,String kind,boolean penalty)throws IOException {
        String prefix=base+'.'+kind;var matches=new ArrayList<String>();try(var files=Files.list(directory)){for(Path p:files.toList()){String n=p.getFileName().toString();if(n.equals(prefix)&&!penalty||penalty&&n.matches(Pattern.quote(prefix)+"\\.lambda\\.[0-9.eE+\\-]+"))matches.add(n);}}
        if(matches.size()!=1)throw new IOException("Expected exactly one original "+kind+" matrix; found "+matches);return matches.get(0);
    }
    private static String auxiliary(Path work,String base,String qName,String kind)throws IOException {var names=new LinkedHashSet<String>(List.of(base+".Q_"+kind,qName+'_'+kind,(base+".Q_"+kind)+qName.substring((base+".Q").length())));var found=names.stream().filter(n->Files.isRegularFile(work.resolve(n))).toList();if(found.size()!=1)throw new IOException("Missing/ambiguous original bootstrap Q_"+kind+" matrix");return found.get(0);}
    static double[] matrix(Path path,int rows,int columns,double min,double max,int line,boolean fixed)throws IOException {double[] result=new double[Math.multiplyExact(rows,columns)];try(var r=Files.newBufferedReader(path)){for(int i=0;i<rows;i++){double[] a=matrixRow(r,columns,min,max,line,fixed);System.arraycopy(a,0,result,i*columns,columns);}if(AdmixtureInputs.line(r,line)!=null)throw new IOException("Extra matrix rows: "+path);}return result;}
    private static double[] matrixRow(BufferedReader r,int columns,double min,double max,int maximum,boolean fixed)throws IOException {String line=AdmixtureInputs.line(r,maximum);if(line==null||line.isBlank())throw new IOException("Truncated or blank ADMIXTURE parameter matrix");String[] fields=line.strip().split("\\s+");if(fields.length!=columns)throw new IOException("ADMIXTURE matrix column count differs");double[] result=new double[columns];for(int i=0;i<columns;i++){if(fixed&&!fields[i].matches("[01]\\.[0-9]{6}"))throw new IOException("Expected original fixed-six-decimal parameter precision");result[i]=finite(fields[i]);if(result[i]<min||result[i]>max)throw new IOException("ADMIXTURE matrix value outside declared range");}return result;}
    private static double scalar(String text,String expression,boolean expected)throws IOException {var m=Pattern.compile(expression).matcher(text);if(!m.find()){if(expected)throw new IOException("Missing original scalar: "+expression);return Double.NaN;}double value=finite(m.group(1).strip());if(m.find()||!expected)throw new IOException("Unexpected/duplicate original scalar: "+expression);return value;}
    private static double finite(String text)throws IOException {try{double d=Double.parseDouble(text);if(!Double.isFinite(d))throw new NumberFormatException("nonfinite");return d;}catch(NumberFormatException e){throw new IOException("Invalid original numeric output: "+text,e);}}
    private static void writeQ(AdmixtureInputs.Prepared p,Path source,Path target)throws IOException {
        try(var r=Files.newBufferedReader(source);var w=Files.newBufferedWriter(target)){w.write("fid\tiid");for(int j=1;j<=p.spec().k();j++)w.write("\tC"+j);w.write('\n');for(var s:p.samples()){String line=AdmixtureInputs.line(r,p.spec().limits().line());if(line==null)throw new IOException("Truncated original Q export");var fields=new ArrayList<String>(List.of(s.familyId(),s.individualId()));fields.addAll(List.of(line.strip().split("\\s+")));FollowupSupport.row(w,fields.toArray(String[]::new));}}
    }
    private static void writeP(AdmixtureInputs.Prepared p,Path source,Path target)throws IOException {
        try(var r=Files.newBufferedReader(source);var w=Files.newBufferedWriter(target)){w.write("chromosome\tmarker\tposition\tcounted_BIM_allele2\tother_BIM_allele1");for(int j=1;j<=p.spec().k();j++)w.write("\tC"+j);w.write('\n');for(var m:p.markers()){String line=AdmixtureInputs.line(r,p.spec().limits().line());if(line==null)throw new IOException("Truncated original P export");var fields=new ArrayList<String>(List.of(m.chromosome(),m.id(),Long.toString(m.position()),m.allele2(),m.allele1()));fields.addAll(List.of(line.strip().split("\\s+")));FollowupSupport.row(w,fields.toArray(String[]::new));}}
    }
}
