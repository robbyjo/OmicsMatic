/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.MetaXcanMath;
import org.jlinalg.genetics.PredictDb;
import static org.jlinalg.cli.NextflowFiles.*;

/** Native PredictDB/GWAS/covariance workflow, with complete deterministic replay. */
final class MetaxcanSummary {
    private MetaxcanSummary(){ }
    record Pair(String one,String two) { }
    static final class Group {
        final Map<Pair,Double> entries=new LinkedHashMap<>();
        final Set<Pair> seen=new HashSet<>();
        final Set<String> firstIds=new LinkedHashSet<>();
        List<String> axis(Set<String> allowed){var result=new LinkedHashSet<String>();for(var e:entries.entrySet())if(allowed.contains(e.getKey().one())&&allowed.contains(e.getKey().two()))result.add(e.getKey().one());return List.copyOf(result);}
        double[] matrix(List<String> ids)throws IOException {
            double[] a=new double[Math.multiplyExact(ids.size(),ids.size())];
            for(int i=0;i<ids.size();i++)for(int j=0;j<ids.size();j++) {
                Double v=entries.get(new Pair(ids.get(i),ids.get(j)));if(v==null)v=entries.get(new Pair(ids.get(j),ids.get(i)));
                require(v!=null,"Missing original matrix pair "+ids.get(i)+"/"+ids.get(j)+"; original inputs are not zero-filled");a[i*ids.size()+j]=v;
            }return a;
        }
    }
    static final class Work {
        final MetaxcanRequest.Capacity cap;final Runnable check;long bytes,peakBytes,operations;
        Work(MetaxcanRequest.Capacity cap,Runnable check){this.cap=cap;this.check=check;}
        void add(long memory,long ops)throws IOException{check.run();bytes=Math.addExact(bytes,memory);peakBytes=Math.max(peakBytes,bytes);operations=Math.addExact(operations,ops);require(peakBytes<=cap.numericBytes()&&operations<=cap.multiplyAdds(),"Complete native MetaXcan memory/work estimate exceeds numeric_bytes or multiply_adds");}
        void matrix(int n,boolean svd)throws IOException {long cells=Math.multiplyExact((long)n,n),temporary=Math.multiplyExact(cells,svd?128:32);add(temporary,Math.multiplyExact(cells,Math.multiplyExact((long)n,64)));bytes-=temporary;}
    }
    /** Write or compare exact expected UTF-8 lines, never trust a saved numeric audit. */
    static final class Sink implements AutoCloseable {
        final boolean saved;final long maximum;long count;final Map<String,OutputStream> outputs=new TreeMap<>();final Map<String,InputStream> inputs=new TreeMap<>();
        Sink(Path root,boolean saved,boolean joint,long maximum)throws IOException {
            this.saved=saved;this.maximum=maximum;var names=new ArrayList<>(List.of("GWAS.jsonl","MATRICES.jsonl","MODEL-SNPS.jsonl","GENES.jsonl","outputs/summary.tsv"));if(joint)names.add("outputs/joint.tsv");
            try{for(String name:names)if(saved)inputs.put(name,new BufferedInputStream(Files.newInputStream(root.resolve(name))));else outputs.put(name,new BufferedOutputStream(Files.newOutputStream(root.resolve(name),StandardOpenOption.CREATE_NEW)));}
            catch(IOException|RuntimeException e){try{close();}catch(IOException closing){e.addSuppressed(closing);}throw e;}
        }
        void line(String name,String value)throws IOException {
            byte[] raw=(value+"\n").getBytes(StandardCharsets.UTF_8);require(raw.length<=maximum-count,"Complete generated MetaXcan output_bytes exceeded");count+=raw.length;
            if(saved)require(Arrays.equals(raw,inputs.get(name).readNBytes(raw.length)),"Captured numeric/input disposition differs: "+name);else outputs.get(name).write(raw);
        }
        void json(String name,Object value)throws IOException{line(name,NextflowJson.json(value));}
        void finish()throws IOException{if(saved)for(var e:inputs.entrySet())require(e.getValue().read()==-1,"Extra saved MetaXcan result/disposition: "+e.getKey());}
        @Override public void close()throws IOException {IOException error=null;var all=new ArrayList<Closeable>();all.addAll(outputs.values());all.addAll(inputs.values());for(Closeable stream:all)try{stream.close();}catch(IOException e){if(error==null)error=e;else error.addSuppressed(e);}if(error!=null)throw error;}
    }
    static Map<String,Object> run(Path root,MetaxcanRequest.Spec s,boolean saved,Runnable check)throws IOException {
        require(s.nativeEngine()&&s.summary(),"Native summary computation requires its explicit engine/mode");var work=new Work(s.capacity(),check);var decoded=new MetaxcanTable.Budget(s.capacity().decodedBytes(),check);
        try(var sink=new Sink(root,saved,s.joint(),s.capacity().outputBytes())) {
            var f=s.texts().get("gwas");
            var gwas=MetaxcanGwas.read(root.resolve(f.member()),f.gzip(),s.columns(),(Boolean)s.settings().get("keep_non_rsid"),s.settings().get("pvalue_policy").equals("source-default-divergent-floor"),s.capacity(),decoded,r->sink.json("GWAS.jsonl",map("ordinal",r.ordinal(),"id",r.id(),"status",r.status(),"unrounded_z",r.unroundedZ(),"source_z",r.sourceZ(),"source_beta",r.sourceBeta())));
            work.add(Math.multiplyExact((long)gwas.rows().size(),512),Math.multiplyExact(gwas.sourceRows(),32));
            var covariance=matrices(root,s,"covariance",false,decoded,work,sink);
            var product=s.joint()?matrices(root,s,"model_product",true,decoded,work,sink):Map.<String,Group>of();
            var summaries=new LinkedHashMap<String,Map<String,Double>>();var dispositions=new TreeMap<String,Long>();var orientation=new HashMap<String,List<String>>();var names=new HashMap<String,String>();long totalWeights=0,totalGenes=0,results=0,negativePhi=0,pUnderflow=0;
            sink.line("outputs/summary.tsv","model\tgene\tgene_name\tzscore\tpvalue\tlog_pvalue\teffect_size\tvar_g\tn_snps_used\tactual_weight_count\tannotated_n_snps_in_model\tn_snps_in_cov\tuncalibrated_zscore\tuncalibrated_pvalue\tsource_phi\tapplied_phi\tpred_perf_r2\tpred_perf_pval\tpred_perf_qval");
            var calibration=object(s.settings().get("calibration"));boolean calibrated="source-phi".equals(calibration.get("mode"));
            for(var model:s.models()) {
                check.run();long left=s.capacity().numericBytes()-work.bytes;int weights=(int)Math.min(s.capacity().variants(),Math.min(Integer.MAX_VALUE-1,left/512));require(weights>0,"No PredictDB memory allowance remains");
                var db=PredictDb.read(root.resolve(model.member()),new PredictDb.Limits(s.capacity().inputBytes(),weights,s.capacity().genes(),(int)Math.max(1,Math.min(Integer.MAX_VALUE,s.capacity().wallMillis()/1000))),check);
                work.add(Math.multiplyExact((long)db.weights().size(),512),Math.multiplyExact((long)db.weights().size(),16));totalWeights+=db.weights().size();
                for(var w:db.weights()) {var pair=List.of(w.referenceAllele(),w.effectAllele());var old=orientation.putIfAbsent(w.variant(),pair);require(old==null||old.equals(pair),"Across-tissue model allele orientation conflicts for "+w.variant()+"; covariance has no allele metadata, so no orientation is guessed");}
                var genes=new TreeSet<String>(db.byGene().keySet());genes.addAll(db.extra().keySet());require(genes.size()<=s.capacity().genes(),"Complete model gene capacity exceeded");
                for(String gene:genes) {
                    check.run();totalGenes++;var annotation=db.extra().get(gene);var all=db.byGene().getOrDefault(gene,List.of());var aligned=new LinkedHashMap<String,Integer>();
                    for(var w:all){var g=gwas.rows().get(w.variant());int sign=g==null?0:g.effectAllele().equals(w.effectAllele())&&g.otherAllele().equals(w.referenceAllele())?1:g.effectAllele().equals(w.referenceAllele())&&g.otherAllele().equals(w.effectAllele())?-1:0;if(sign!=0)aligned.put(w.variant(),sign);}
                    Group group=covariance.get(gene);var axis=group==null?List.<String>of():group.axis(aligned.keySet());var used=new HashSet<>(axis);
                    for(var w:all) {String status=!gwas.rows().containsKey(w.variant())?"NO_FINITE_GWAS":!aligned.containsKey(w.variant())?"ALLELE_MISMATCH_NO_STRAND_REPAIR":!used.contains(w.variant())?"NOT_IN_SOURCE_COVARIANCE_AXIS":annotation==null?"SOURCE_NO_EXTRA_ANNOTATION":"USED";sink.json("MODEL-SNPS.jsonl",map("model",model.name(),"gene",gene,"variant",w.variant(),"weight",w.value(),"model_reference_allele",w.referenceAllele(),"model_effect_allele",w.effectAllele(),"alignment_sign",aligned.get(w.variant()),"status",status));}
                    String status=annotation==null?"NO_EXTRA_ANNOTATION":all.isEmpty()?"NO_WEIGHTS":aligned.isEmpty()?"NO_ALIGNED_FINITE_GWAS":axis.isEmpty()?"NO_COVARIANCE_INTERSECTION":"ASSOCIATION";
                    if(status.equals("ASSOCIATION")) {
                        work.matrix(axis.size(),false);double[] a=group.matrix(axis),w=new double[axis.size()],z=new double[axis.size()],beta=s.columns().beta()!=null||s.columns().oddsRatio()!=null?new double[axis.size()]:null;var byVariant=new HashMap<String,PredictDb.Weight>();for(var weight:all)byVariant.put(weight.variant(),weight);
                        for(int i=0;i<axis.size();i++){String id=axis.get(i);int sign=aligned.get(id);w[i]=byVariant.get(id).value();z[i]=gwas.rows().get(id).zscore()*sign;if(beta!=null)beta[i]=gwas.rows().get(id).beta()*sign;}
                        MetaXcanMath.Summary raw;try{raw=MetaXcanMath.summary(w,z,beta,a);}catch(IllegalArgumentException e){throw new IOException("Unreportable native summary for "+model.name()+"/"+gene+"; no matrix/zero-variance repair",e);}
                        double outZ=raw.zscore(),outP=raw.pvalue(),outLog=raw.logPvalue();Double sourcePhi=null,appliedPhi=null;
                        if(calibrated) {require(db.hasPhi()&&annotation.phi()!=null,"Missing phi for "+model.name()+"/"+gene+"; the original inner join would drop this gene. Supply complete compatible models or explicitly choose uncalibrated analysis");sourcePhi=annotation.phi();MetaXcanMath.Calibrated corrected;try{corrected=MetaXcanMath.calibrate(raw,sourcePhi,integer(calibration.get("gwas_samples"),1,Long.MAX_VALUE),MetaxcanRequest.finite(calibration.get("gwas_heritability")));}catch(IllegalArgumentException e){throw new IOException("Invalid source phi correction for "+gene,e);}outZ=corrected.zscore();outP=corrected.pvalue();outLog=corrected.logPvalue();appliedPhi=corrected.appliedPhi();if(corrected.negativePhiReplacedByZero())negativePhi++;}
                        if(outP==0)pUnderflow++;results++;
                        if(s.joint()){work.add(128,1);summaries.computeIfAbsent(gene,key->new LinkedHashMap<>()).put(model.name(),outZ);}
                        require(!names.containsKey(gene)||Objects.equals(names.get(gene),annotation.name()),"Conflicting gene names across selected models for "+gene+"; no first-name selection");names.put(gene,annotation.name());
                        sink.line("outputs/summary.tsv",tsv(model.name(),gene,annotation.name(),outZ,outP,outLog,raw.effectSize(),raw.variance(),axis.size(),all.size(),annotation.annotatedSnps(),group.firstIds.size(),raw.zscore(),raw.pvalue(),sourcePhi,appliedPhi,annotation.performanceR2(),annotation.performanceP(),annotation.performanceQ()));
                    }
                    dispositions.merge(status,1L,Long::sum);sink.json("GENES.jsonl",map("model",model.name(),"gene",gene,"status",status,"actual_weights",all.size(),"annotated_snps",annotation==null?null:annotation.annotatedSnps(),"finite_allele_aligned_variants",aligned.size(),"used_variants",axis,"source_covariance_first_ids",group==null?null:group.firstIds.size()));
                }
            }
            require(results>0,"No native summary associations remain; inspect all retained gene/SNP/GWAS dispositions");long joint=0,jointUnderflow=0;
            if(s.joint()) {
                sink.line("outputs/joint.tsv","gene\tgene_name\tpvalue\tlog_pvalue\tstatistic\tn\tn_indep\tp_i_best\tt_i_best\tp_i_worst\tt_i_worst\teigen_max\teigen_min\teigen_min_kept\tz_min\tz_max\tz_mean\tz_sd\ttmi\tstatus");
                var selected=object(s.settings().get("joint_cutoff"));var cutoff=new MetaXcanMath.Cutoff(MetaXcanMath.CutoffKind.valueOf(text(selected.get("kind")).replace('-','_').toUpperCase(Locale.ROOT)),MetaxcanRequest.finite(selected.get("value")));Double regularization=s.settings().get("regularization")==null?null:MetaxcanRequest.finite(s.settings().get("regularization"));
                for(var row:summaries.entrySet()) {
                    check.run();Group group=product.get(row.getKey());require(group!=null,"Missing model-product gene: "+row.getKey());var labels=group.axis(row.getValue().keySet());require(new HashSet<>(labels).equals(row.getValue().keySet()),"Model-product tissue population differs for "+row.getKey()+"; no tissue is silently dropped");work.matrix(labels.size(),true);double[] z=new double[labels.size()];for(int i=0;i<z.length;i++)z[i]=row.getValue().get(labels.get(i));MetaXcanMath.Joint j;
                    try{j=MetaXcanMath.joint(z,group.matrix(labels),cutoff,regularization);}catch(IllegalArgumentException e){throw new IOException("Unreportable joint matrix for "+row.getKey()+"; no regularization/model is inferred",e);}joint++;if(j.sourceStatus()==-4)jointUnderflow++;
                    sink.line("outputs/joint.tsv",tsv(row.getKey(),names.get(row.getKey()),j.pvalue(),j.logPvalue(),j.statistic(),z.length,j.components(),j.bestTissuePvalue(),labels.get(j.bestTissueIndex()),j.worstTissuePvalue(),labels.get(j.worstTissueIndex()),j.maximumSingularValue(),j.minimumSingularValue(),j.minimumKeptSingularValue(),j.minimumZ(),j.maximumZ(),j.meanZ(),j.sdZ(),j.traceMatrixInverse(),j.sourceStatus()));
                }
            }
            sink.finish();return map("schema_version",1,"engine","native","edition_contract",s.edition(),"mode",s.mode(),"complete_gwas_rows",gwas.sourceRows(),"finite_gwas_rows",gwas.rows().size(),"missing_gwas_ids",gwas.missingIds(),"non_rsid_filtered",gwas.nonRsIds(),"nonfinite_z_filtered",gwas.nonfiniteZ(),"gwas_z_scheme",gwas.zscoreScheme(),"odds_ratio_overrides_beta",gwas.oddsRatioOverridesBeta(),"divergent_p_floor",gwas.divergentFloor(),"selected_models",s.models().size(),"complete_weight_rows",totalWeights,"complete_model_gene_dispositions",totalGenes,"gene_dispositions",dispositions,"summary_results",results,"joint_results",joint,"negative_phi_replaced_by_zero",negativePhi,"summary_p_underflows",pUnderflow,"joint_source_status_minus4",jointUnderflow,"decoded_bytes_read",decoded.decoded,"numeric_memory_estimate_bytes",work.peakBytes,"work_estimate",work.operations,"generated_scientific_bytes",sink.count,"original_executable_run",false,"whole_paper_reproduced",false);
        }
    }
    private static Map<String,Group> matrices(Path root,MetaxcanRequest.Spec s,String slot,boolean product,MetaxcanTable.Budget decoded,Work work,Sink sink)throws IOException {
        var ref=s.texts().get(slot);var result=new LinkedHashMap<String,Group>();String previous=null;long ordinal=0;
        try(var table=new MetaxcanTable(root.resolve(ref.member()),ref.gzip(),"whitespace",s.capacity().multiplyAdds()/8,decoded,true)) {
            require(table.header().equals(product?List.of("gene","model1","model2","value"):List.of("GENE","RSID1","RSID2","VALUE")),"Unexpected original "+slot+" header");
            for(List<String> row;(row=table.next())!=null;) {
                work.add(512,8);ordinal++;String gene=row.get(0),one=row.get(1),two=row.get(2);for(String id:List.of(gene,one,two))require(!id.isBlank()&&id.chars().noneMatch(c->c<=32||c==127),"Invalid matrix identifier");
                if(!gene.equals(previous)){require(!result.containsKey(gene),"Original matrix gene blocks must be contiguous: "+gene);require(result.size()<s.capacity().genes(),"Complete matrix gene capacity exceeded");previous=gene;result.put(gene,new Group());}
                Group group=result.get(gene);Pair pair=one.compareTo(two)<=0?new Pair(one,two):new Pair(two,one);require(group.seen.add(pair),"Duplicate/conflicting matrix pair for "+gene+"/"+one+"/"+two+"; no last-row overwrite");double value=MetaxcanGwas.number(row.get(3));
                if(!Double.isNaN(value)){require(Double.isFinite(value),"Nonfinite original covariance/model-product value");group.entries.put(new Pair(one,two),value);group.firstIds.add(one);}
                sink.json("MATRICES.jsonl",map("slot",slot,"ordinal",ordinal,"gene",gene,"id1",one,"id2",two,"status",Double.isNaN(value)?"SOURCE_MISSING_VALUE_IGNORED":"FINITE_ORIGINAL_ENTRY","value",Double.isNaN(value)?null:value));
            }
        }return result;
    }
    static Map<String,Object> map(Object... entries){var result=new LinkedHashMap<String,Object>();for(int i=0;i<entries.length;i+=2)result.put((String)entries[i],entries[i+1]);return result;}
    private static String tsv(Object... values){var parts=new ArrayList<String>();for(Object v:values)parts.add(v==null?"NA":v.toString());return String.join("\t",parts);}
    private static void require(boolean condition,String message)throws IOException{if(!condition)throw new IOException(message);}
}
