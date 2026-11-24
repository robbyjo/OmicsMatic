/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.math.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Independent streaming hard-call reconstruction; original C&T and fitting remain upstream. */
final class PrsiceAudit {
    private PrsiceAudit() { }
    record Bin(double category,double threshold) { }
    record Selected(PrsiceData.Variant base,PrsiceGenotypes.Bim target,PrsiceGenotypes.Orientation orientation,PrsiceGenotypes.Bim reference,boolean referenceFlip,Bin bin) { }
    static final class Product implements AutoCloseable {
        final Path file;final boolean inspect;final MessageDigest digest;final Writer writer;
        Product(Path file,boolean inspect)throws IOException {this.file=file;this.inspect=inspect;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}writer=new BufferedWriter(new OutputStreamWriter(new DigestOutputStream(inspect?OutputStream.nullOutputStream():Files.newOutputStream(file,StandardOpenOption.CREATE_NEW),digest),StandardCharsets.UTF_8));}
        void row(Object... values)throws IOException {for(int i=0;i<values.length;i++){if(i>0)writer.write('\t');writer.write(PrsiceTables.tsv(String.valueOf(values[i])));}writer.write('\n');}
        @Override public void close()throws IOException {writer.close();if(inspect)require(HexFormat.of().formatHex(digest.digest()).equals(readSha(file)),"Changed derived scientific product "+file.getFileName());}
        private static String readSha(Path file)throws IOException {return text(pin(file).get("sha256"),"product hash");}
    }
    static Bin bin(Map<String,Object> request,double p)throws IOException {
        var scoring=object(object(request.get("parameters")).get("scoring"));var t=object(scoring.get("thresholds"));
        if("LEVELS".equals(t.get("kind"))){var levels=list(t.get("levels"));for(int i=0;i<levels.size();i++){double v=PrsiceContracts.finite(levels.get(i),"threshold");if(p<v||PrsiceContracts.equal(p,v))return new Bin(i,v);}throw new IOException("Original selected SNP exceeds the explicitly requested maximum threshold");}
        double lower=PrsiceContracts.finite(t.get("lower"),"lower"),upper=PrsiceContracts.finite(t.get("upper"),"upper"),step=PrsiceContracts.finite(t.get("interval"),"interval");boolean full=Boolean.TRUE.equals(t.get("include_full"));
        require(p<=upper||full,"Original selected SNP exceeds the requested grid without full model");double category=p>upper&&full?Math.ceil((upper+.1-lower)/step):p>lower||PrsiceContracts.equal(p,lower)?Math.ceil((p-lower)/step):0;
        // The source changes globally to SNP-by-SNP evaluation beyond uint64.
        // Refuse this distinct mode instead of silently using a Java grid.
        if(!Double.isFinite(category)||category>=0x1.0p64)throw new PrsiceContracts.Unavailable("Original tiny-step SNP-by-SNP fallback is outside this grid audit; dependent analysis skipped. Use explicitly justified LEVELS, or a separately qualified fallback implementation; do not silently enlarge the interval.");
        return new Bin(category,p>upper&&full?1:category*step+lower);
    }
    static Map<String,Object> evaluate(Path root,Map<String,Object> request,PrsiceData.Inputs inputs,boolean inspect)throws IOException {
        var p=object(request.get("parameters"));var warnings=new ArrayList<>(inputs.warnings);boolean keep="KEEP_ORIGINAL_WITH_WARNING".equals(object(p.get("alleles")).get("palindromic"));boolean score=!"CLUMP".equals(request.get("operation"));
        var selected=new ArrayList<Selected>();var selectedIds=new HashSet<String>();int complemented=0,ambiguous=0;Path snps=root.resolve("worker/original.snp");
        try(var rows=new PrsiceTables.Rows(snps,request,false)){
            var header=PrsiceTables.header(rows.next());require(header.keySet().equals(Set.of("CHR","SNP","BP","P","Base")),"Unexpected original single-Base SNP schema");
            for(String[] row;(row=rows.next())!=null;){
                require(row.length==5,"Original SNP row width changed");String id=row[header.get("SNP")];require(selectedIds.add(id),"Duplicate original selected SNP ID");var v=inputs.base.get(id);var b=inputs.target.byId.get(id);
                require(v!=null&&b!=null&&!inputs.target.duplicateVariantIds.contains(id),"Original selected SNP lacks an unambiguous original base/target ID: "+id);require(v.status().equals("CANDIDATE"),"Original selected an unadmitted base row: "+id+" "+v.status());
                require(v.chromosome()==b.chromosome()&&v.position()==b.position()&&v.chromosome()>=1&&v.chromosome()<=22,"Selected base/target coordinate or autosomal scope differs: "+id);
                require(PrsiceGenotypes.chromosome(row[header.get("CHR")])==b.chromosome()&&PrsiceGenotypes.nonnegative(row[header.get("BP")],"original SNP BP")==b.position(),"Original SNP coordinates differ from target BIM");require(row[header.get("Base")].equals("1"),"Unexpected original Base membership");rounded(row[header.get("P")],v.p(),6,"original SNP P");
                var orientation=PrsiceGenotypes.orient(v.a1(),v.a2(),b.a1(),b.a2(),keep);require(orientation.matched(),"Selected SNP has incompatible original base/target alleles: "+id);if(orientation.complemented()){complemented++;require(!"EXACT".equals(object(p.get("alleles")).get("strand")),"Strand-complemented SNP violates explicit EXACT strand policy: "+id);}if(orientation.palindromic())ambiguous++;
                PrsiceGenotypes.Bim reference=null;boolean referenceFlip=false;
                if(inputs.reference!=null){reference=inputs.reference.byId.get(id);require(reference!=null&&!inputs.reference.duplicateVariantIds.contains(id),"Selected SNP lacks an unambiguous reference BIM ID: "+id);require(reference.chromosome()==b.chromosome()&&reference.position()==b.position(),"Reference/target coordinate differs: "+id);var orient=PrsiceGenotypes.orient(b.a1(),b.a2(),reference.a1(),reference.a2(),keep);require(orient.matched(),"Incompatible reference/target alleles: "+id);if(orient.complemented()){complemented++;require(!"EXACT".equals(object(p.get("alleles")).get("strand")),"Reference complement violates EXACT policy");}referenceFlip=orientation.flipped()^orient.flipped();}
                selected.add(new Selected(v,b,orientation,reference,referenceFlip,score?bin(request,v.p()):null));require(selected.size()<=PrsiceContracts.cap(request,"maximum_variants"),"Selected SNP capacity exceeded");
            }
        }
        require(!selected.isEmpty(),"No original selected SNPs; no complete analysis is admitted");
        if(complemented>0)warnings.add(complemented+" source allele matches use the original single-base complement convention under the declared policy.");if(ambiguous>0)warnings.add(ambiguous+" palindromic selected SNPs use original literal dosage orientation; no frequency-based strand resolution is inferred.");
        long unselected=inputs.base.values().stream().filter(v->v.status().equals("CANDIDATE")&&!selectedIds.contains(v.id())).count();if(unselected>0)PrsiceData.policy(request,"unmatched_variants",unselected+" candidate base rows were not retained by original selection/QC/clumping/thresholds. The ledger records nonselection without inventing its cause.",warnings);
        try(var ledger=new Product(root.resolve("variant-ledger.tsv"),inspect)){
            ledger.row("source_line","SNP","source_status","original_selected","target_BIM_row","effect_allele_flipped","strand_complemented","threshold");
            var map=new HashMap<String,Selected>();for(var v:selected)map.put(v.base.id(),v);
            for(var v:inputs.base.values()){var s=map.get(v.id());ledger.row(v.line(),v.id(),v.status(),s!=null,s==null?"NA":s.target.index()+1,s==null?"NA":s.orientation.flipped(),s==null?"NA":s.orientation.complemented(),s==null||s.bin==null?"NA":s.bin.threshold());}
        }
        try(var ledger=new Product(root.resolve("sample-ledger.tsv"),inspect)){
            ledger.row("FID","IID","original_FAM_row","compact_row","founder","source_regression_flag","model_row","standardization_row","phenotype_status","covariate_status");for(var s:inputs.samples)ledger.row(s.sample().fid(),s.sample().iid(),s.sample().index()+1,s.compactIndex()+1,s.sample().founder(),s.sourceRegressionFlag(),s.modelRow(),s.standardizationRow(),s.phenotypeStatus(),s.covariateStatus());
        }
        Map<String,Object> scores=score?scores(root,request,inputs,selected):map("requested",false);Map<String,Object> fits="PREDICT".equals(request.get("operation"))?fits(root,request,inputs,selected.size(),warnings,inspect):map("requested",false);
        var originalWarnings=new ArrayList<String>();for(String file:List.of("worker/original.log","worker/analysis.stdout.log","worker/analysis.stderr.log")){
            Path path=root.resolve(file);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))continue;
            try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){for(String line;(line=reader.readLine())!=null;)if(line.toLowerCase(Locale.ROOT).matches(".*\\b(warning|error|failed|nan|infinity)\\b.*")){require(originalWarnings.size()<10000,"Original warning count exceeds audit capacity");originalWarnings.add(file+": "+line);}}
        }
        if(!originalWarnings.isEmpty())PrsiceData.policy(request,"original_warnings","Original runtime emitted reviewable warnings/errors: "+String.join(" | ",originalWarnings),warnings);
        return map("results_admitted",true,"original_selected_variants",selected.size(),"candidate_rows_not_selected",unselected,"base_target_complement_matches",complemented,"palindromic_selected_variants",ambiguous,"scores",scores,"fits",fits,"warnings",warnings,"original_warning_lines",originalWarnings,"clumping_engine","Original PRSice haplotype LD clumping; no Java Pearson-r substitution","independent_score_reconstruction",score,"independent_statistical_refit",false,"whole_tool_or_paper_completion",false);
    }
    static void rounded(String raw,double expected,int digits,String label)throws IOException {
        double observed=PrsiceTables.finite(raw,label);require(Double.isFinite(expected),"Nonfinite reconstructed "+label);
        double unit=expected==0?Double.MIN_VALUE:Math.pow(10,Math.floor(Math.log10(Math.abs(expected)))-digits+1);
        double bound=.50001*unit+16*Math.ulp(expected);require(Math.abs(observed-expected)<=bound,"Original "+label+" differs from native reconstruction beyond its "+digits+"-significant-digit export rounding: observed="+raw+", native="+expected+", bound="+bound);
    }
    private static double weight(String model,int copies){return switch(model){case "add"->copies;case "dom"->copies==0?0:1;case "rec"->copies==2?1:0;default->copies==1?1:0;};}
    private static Map<String,Object> scores(Path root,Map<String,Object> request,PrsiceData.Inputs inputs,List<Selected> selected)throws IOException {
        var s=object(object(request.get("parameters")).get("scoring"));String model=text(s.get("model"),"model"),missing=text(s.get("missing"),"missing"),scale=text(s.get("scale"),"scale");boolean cumulative=Boolean.TRUE.equals(s.get("cumulative")),ref="REFERENCE".equals(s.get("frequency_source"));
        selected.sort(Comparator.comparingDouble((Selected v)->v.bin.category).thenComparingInt(v->v.target.index()));var groups=new ArrayList<Integer>();for(int i=0;i<selected.size();i++)if(i+1==selected.size()||selected.get(i).bin.category!=selected.get(i+1).bin.category)groups.add(i+1);
        int n=inputs.target.selected.size(),t=groups.size();require(t<=PrsiceContracts.cap(request,"maximum_thresholds"),"Observed threshold capacity exceeded");require(Math.multiplyExact((long)n,selected.size())<=PrsiceContracts.cap(request,"maximum_native_cells"),"Native reconstruction cells exceed declared capacity; explicitly raise maximum_native_cells where permitted, otherwise analysis is unadmitted");long scratch=Math.multiplyExact(Math.multiplyExact((long)n,t),8);require(scratch<=PrsiceContracts.cap(request,"maximum_scratch_bytes"),"Score audit scratch exceeds declared capacity");
        Path temporary=Files.createTempFile("omicsmatic-prsice-audit-",".bin");Object identity=Files.readAttributes(temporary,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();
        try {
            double[] sums=new double[n];long[] counts=new long[n];byte[] target=inputs.target.bed.buffer(),reference=inputs.reference==null?null:inputs.reference.bed.buffer();var thresholds=new ArrayList<Double>();var numbers=new ArrayList<Integer>();int first=0,totalInformative=0;
            try(var output=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary,StandardOpenOption.WRITE)))){
                for(int end:groups){if(!cumulative){Arrays.fill(sums,0);Arrays.fill(counts,0);totalInformative=0;}for(int v=first;v<end;v++){
                    var variant=selected.get(v);inputs.target.bed.row(variant.target.index(),target);var panel=ref?inputs.reference:inputs.target;byte[] frequency=target;boolean flip=variant.orientation.flipped();if(ref){inputs.reference.bed.row(variant.reference.index(),reference);frequency=reference;flip=variant.referenceFlip;}
                    double f0=weight(model,flip?2:0),f1=weight(model,1),f2=weight(model,flip?0:2),weighted=0;long called=0;
                    for(var sample:panel.selected)if(sample.founder()){int g=PrsiceGenotypes.Bed.copies(frequency,sample.index());if(g>=0){called++;weighted+=g==2?f0:g==1?f1:f2;}}
                    if(called==0)continue;double mean=2*(1-weighted/(2*called)),effect=variant.base.effect(),adjustment=missing.equals("CENTER")?2*effect*(mean/2):0,imputed=missing.equals("MEAN_IMPUTE")?2*effect*(mean/2):0;
                    double w0=weight(model,variant.orientation.flipped()?2:0),w1=weight(model,1),w2=weight(model,variant.orientation.flipped()?0:2);totalInformative++;
                    for(int i=0;i<n;i++){int g=PrsiceGenotypes.Bed.copies(target,inputs.target.selected.get(i).index());double value;if(g<0){value=imputed;if(!missing.equals("SET_ZERO"))counts[i]+=2;}else{value=(g==0?w0:g==1?w1:w2)*effect-adjustment;counts[i]+=2;}sums[i]=totalInformative==1?value:sums[i]+value;}
                }
                require(totalInformative>0,"No informative founder calls at an original score threshold");double mean=0,m2=0;int standardN=0;boolean standard=scale.equals("std")||scale.equals("con-std");
                if(standard)for(int i=0;i<n;i++)if(inputs.samples.get(i).standardizationRow()){double value=counts[i]==0?0:sums[i]/counts[i],delta=value-mean,deltaN=delta/(++standardN);m2+=delta*deltaN*(standardN-1);mean+=deltaN;}
                double sd=Math.sqrt(m2/(standardN-1.0));if(standard)require(standardN>=2&&sd>0&&Double.isFinite(sd),"Source standardization has insufficient rows or nonpositive/nonfinite SD; raw upstream output is retained without admission");
                for(int i=0;i<n;i++){double value=scale.equals("sum")?sums[i]:counts[i]==0?0:sums[i]/counts[i];if(standard)value=(value-mean)/sd;require(Double.isFinite(value),"Native score is nonfinite; no repair is performed");output.writeDouble(value);}thresholds.add(selected.get(end-1).bin.threshold);numbers.add(cumulative?end:end-first);first=end;
                }
            }
            require(Files.size(temporary)==scratch,"Native score scratch byte count differs");long comparisons=0;double maximumError=0;
            try(var rows=new PrsiceTables.Rows(root.resolve("worker/original.all_score"),request,false);var input=FileChannel.open(temporary,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
                String[] header=rows.next();require(header!=null&&header.length==t+2&&header[0].equals("FID")&&header[1].equals("IID"),"Original all_score header differs from expected samples/thresholds");for(int j=0;j<t;j++){require(header[j+2].startsWith("Pt_"),"Unknown score column");rounded(header[j+2].substring(3),thresholds.get(j),6,"threshold label");}
                int block=Math.toIntExact(Math.min(n,Math.max(1,PrsiceContracts.cap(request,"scratch_block_bytes")/(8L*t))));double[][] values=new double[t][block];
                for(int start=0;start<n;start+=block){int count=Math.min(block,n-start);for(int j=0;j<t;j++){ByteBuffer bytes=ByteBuffer.allocate(count*8);long position=((long)j*n+start)*8;while(bytes.hasRemaining()){int got=input.read(bytes,position);require(got>0,"Native scratch truncated");position+=got;}bytes.flip();for(int i=0;i<count;i++)values[j][i]=bytes.getDouble();}
                    for(int i=0;i<count;i++){String[] row=rows.next();var sample=inputs.target.selected.get(start+i);require(row!=null&&row.length==header.length&&row[0].equals(sample.fid())&&row[1].equals(sample.iid()),"Original all_score sample identity/order differs from selected FAM rows");for(int j=0;j<t;j++){rounded(row[j+2],values[j][i],9,"score for "+sample.key()+" at "+thresholds.get(j));maximumError=Math.max(maximumError,Math.abs(PrsiceTables.finite(row[j+2],"score")-values[j][i]));comparisons++;}}
                }require(rows.next()==null,"Unexpected extra original score rows");
            }
            return map("requested",true,"samples",n,"thresholds",thresholds,"SNP_counts",numbers,"native_original_value_comparisons",comparisons,"maximum_absolute_export_error",maximumError,"export_significant_digits",9,"frequency_source",s.get("frequency_source"),"dense_genotype_matrix_allocated",false,"temporary_score_bytes",scratch,"temporary_removed_before_return",true,"models_refitted",false);
        }finally {require(!Files.isSymbolicLink(temporary)&&Objects.equals(identity,Files.readAttributes(temporary,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey()),"Native scratch identity changed; preserve and investigate "+temporary);Files.delete(temporary);}
    }
    private static Map<String,Object> fits(Path root,Map<String,Object> request,PrsiceData.Inputs inputs,int selected,List<String> warnings,boolean inspect)throws IOException {
        if("REQUIRE_REPORTED_CONVERGENCE".equals(object(request.get("policies")).get("solver_convergence")))throw new PrsiceContracts.Unavailable("Selected PRSice source does not expose original GLM convergence flags; dependent fitting is skipped under REQUIRE_REPORTED_CONVERGENCE. Keep the skip or explicitly review SOURCE_UNREPORTED_WITH_WARNING; finite output is not a convergence certificate.");
        warnings.add("ORIGINAL FIT CONVENTIONS: original quantitative P uses Student t with df=N; standard errors use the residual variance convention. Original GLM convergence flags are not forwarded; finite fields do not establish convergence. Threshold R2 and summary PRS.R2 have different source definitions. Original trait '-' labels are retained separately from the declared phenotype column.");
        long rowsCount=0;try(var rows=new PrsiceTables.Rows(root.resolve("worker/original.prsice"),request,false);var output=new Product(root.resolve("results.tsv"),inspect)){
            var header=PrsiceTables.header(rows.next());require(header.keySet().containsAll(Set.of("Pheno","Set","Threshold","R2","P","Coefficient","Standard.Error","Num_SNP")),"Missing original regression columns");output.row("original_phenotype","declared_phenotype","Set","Threshold","R2","P","Coefficient","Standard.Error","Num_SNP","evaluation_role");
            for(String[] row;(row=rows.next())!=null;){require(row.length==header.size(),"Original fit row width differs");require(row[header.get("Set")].equals("Base"),"Unexpected original set");double probability=PrsiceTables.finite(row[header.get("P")],"original P"),se=PrsiceTables.finite(row[header.get("Standard.Error")],"original SE");PrsiceTables.finite(row[header.get("Coefficient")],"original coefficient");PrsiceTables.finite(row[header.get("Threshold")],"original threshold");require(probability>=0&&probability<=1&&se>0,"Original fitted P/SE outside admissible range");long snps=PrsiceGenotypes.nonnegative(row[header.get("Num_SNP")],"original fitted SNP count");require(snps>0&&snps<=selected,"Original fitted SNP count outside selected population");
                // R2 may legitimately be negative or unavailable. Retain its
                // literal source field; never clip it or fill it with zero.
                String r2=row[header.get("R2")];if(!PrsiceTables.missing(r2))PrsiceTables.finite(r2,"original R2");else warnings.add("Original R2 unavailable at "+row[header.get("Threshold")]+"; literal field retained.");if(probability==0)warnings.add("Original exported P is zero at "+row[header.get("Threshold")]+"; this is a floating point/export boundary, not an exact mathematical zero.");
                var pheno=object(object(request.get("parameters")).get("phenotype"));output.row(row[header.get("Pheno")],pheno.get("column")==null?"FAM_COLUMN_6":pheno.get("column"),row[header.get("Set")],row[header.get("Threshold")],r2,row[header.get("P")],row[header.get("Coefficient")],row[header.get("Standard.Error")],snps,object(object(request.get("parameters")).get("regression")).get("evaluation_role"));rowsCount++;
            }
        }require(rowsCount>0,"No finite original fitted result rows");require(Files.isRegularFile(root.resolve("worker/original.summary"))&&Files.size(root.resolve("worker/original.summary"))>0,"Missing original fit summary");
        return map("requested",true,"rows",rowsCount,"model_map_rows",inputs.samples.stream().filter(PrsiceData.SampleState::modelRow).count(),"convergence_flag_exposed_by_source",false,"export_significant_digits",6,"independent_refit_performed_here",false,"in_sample_tuning",object(object(object(request.get("parameters")).get("regression"))).get("evaluation_role").equals("IN_SAMPLE_TUNING"));
    }
}
