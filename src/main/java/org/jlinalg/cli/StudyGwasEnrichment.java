/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyGwasEnrichmentPolicy;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.xwas.StratifiedLdScoreRegression;
import org.jlinalg.xwas.SummaryMath;

/** Complete-regression-universe binary enrichment and continuous annotation effects. */
final class StudyGwasEnrichment implements StudyOperationExecutor {
    static final String CAVEAT="S-LDSC estimates observed-scale common-variant heritability conditional on the prespecified annotation model, matched population LD reference and source one-degree-of-freedom GWAS calibration. Binary category heritability uses reference cross-products; conditional coefficients are not category heritability. Only p_value/adjusted_p define the registered primary family; coefficient_p is secondary for binary annotations. Natural-log P/adjusted-P retain tails when linear columns underflow to zero. Enrichment is not pathway activation, a causal gene assignment, fine-mapping, a pathogenicity score or experimental function. Binary-trait heritability here is on the ascertained observed scale, not population liability scale. Negative coefficients, proportions outside [0,1] and enrichment below zero are retained as unconstrained regression estimates. Missing reference/QC coverage and model misspecification can bias inference; source declarations and dense-input counts do not independently establish GWAS calibration or ancestry matching.";
    static final String CONTINUOUS_CAVEAT="Continuous-annotation tau-star is tau * population-SD(annotation) / (total h2 / reference SNPs), conditional on all registered annotations. Its SE uses delete-block coefficient/total-h2 ratios with fixed full-reference moments. Its primary P value tests the raw conditional coefficient with a two-sided normal approximation, not tau-star divided by its ratio SE. A continuous annotation is not a binary category and has no category enrichment/complement test. Mixed-mode fixed initial weights use only all-SNP baseline LD/count; they differ from legacy aggregate-annotation weights and are retained for all deletes. Reference moments and source applicability remain declarations requiring scientific review.";
    private record Annotation(String id,String name,String role,double count,boolean binary) { }
    private record Ld(String id,int chromosome,int position,String ref,String alt,double weight,double[] scores) { }
    private record Matched(Ld ld,double chi,double n) { }
    @Override public Result execute(Context c)throws Exception{
        Files.createDirectories(c.outputDirectory());var p=StudyGwasEnrichmentPolicy.resolve(map(StudyResultTables.analysis(c).get("gwas_enrichment")));
        var summary=new LinkedHashMap<String,Object>();summary.put("adjustment",p.get("adjustment"));summary.put("required_manuscript_caveat",CAVEAT);
        boolean mixed="mixed".equals(p.get("annotation_mode"));summary.put("annotation_mode",p.get("annotation_mode"));
        summary.put("weight_model",mixed?"fixed_all_snp_baseline_initial_weights":"fixed_aggregate_annotation_initial_weights");
        if(mixed)summary.put("required_manuscript_caveat",CAVEAT+" "+CONTINUOUS_CAVEAT);
        summary.put("method_model",mixed?"simultaneous_mixed_annotation_s_ldsc_fixed_baseline_weights":"simultaneous_overlapping_binary_annotation_s_ldsc_fixed_initial_weights");summary.put("official_family_complete",false);
        try{return run(c,p,summary);}catch(StudyPrimaryResults.Unavailable unavailable){summary.put("primary_selection",unavailable.receipt());return finish(c,p,summary,false,false,unavailable.getMessage());}
        catch(IllegalArgumentException invalid){return finish(c,p,summary,false,false,invalid.getMessage());}
    }
    private Result run(Context c,Map<String,Object> p,Map<String,Object> summary)throws Exception{
        var source=map(p.get("summary"));var reference=map(p.get("reference"));
        for(String field:List.of("coverage","tested_snps","population","genome_build","calibration","model","qc","ascertainment"))if(!source.containsKey(field))return finish(c,p,summary,false,false,"Declare gwas_enrichment.summary."+field+" for the complete calibrated GWAS source");
        if(!"complete".equals(source.get("coverage")))return finish(c,p,summary,false,false,"S-LDSC requires a complete GWAS source, not selected/significant-only variants");
        for(String field:List.of("population","genome_build","release","common_variant_definition","regression_snp_definition","annotation_model"))if(!reference.containsKey(field))return finish(c,p,summary,false,false,"Declare gwas_enrichment.reference."+field+" for the LD and annotation-count reference");
        if(!source.get("population").equals(reference.get("population"))||!source.get("genome_build").equals(reference.get("genome_build")))return finish(c,p,summary,false,false,"GWAS and LD-reference population/build declarations must match; register a population-specific analysis");
        summary.put("summary_scope",source);summary.put("reference_scope",reference);
        for(String role:List.of("ld_scores","annotations","overlap"))if(!p.containsKey(role)||!Files.isRegularFile(c.project().resolve(text(p.get(role)))))return finish(c,p,summary,false,false,"Supply gwas_enrichment."+role+"; no LD, annotation universe or overlaps were inferred from significant SNPs");
        Path annotation=snapshot(c,c.project().resolve(text(p.get("annotations"))),"annotations.tsv");
        var at=DelimitedData.readResult(annotation);var annotations=new ArrayList<Annotation>();var ids=new LinkedHashSet<String>();boolean mixed="mixed".equals(p.get("annotation_mode"));
        for(String[] r:at.rows()){
            String id=r[at.column("term_id")],role=r[at.column("role")],name=r[at.column("term_name")];
            if(!id.matches("[A-Za-z][A-Za-z0-9_.-]*")||!ids.add(id)||!Set.of("all","target","nuisance").contains(role)||name.isBlank())throw new IllegalArgumentException("Invalid or duplicate annotation ID/name/role");
            String kind=mixed?r[at.column("annotation_type")]:"binary";
            if(!Set.of("binary","continuous").contains(kind))throw new IllegalArgumentException("Mixed annotations need explicit binary/continuous annotation_type");
            if(!mixed&&at.header().contains("annotation_type")&&!r[at.column("annotation_type")].equals("binary"))throw new IllegalArgumentException("Continuous annotations require annotation_mode: mixed; no binary-category interpretation was substituted");
            double sum=number(r[at.column(mixed?"sum":"count")],mixed?"reference annotation sum":"annotation count");
            if(mixed&&at.header().contains("count")&&!Set.of("","NA","na",".").contains(r[at.column("count")])&&(!kind.equals("binary")||number(r[at.column("count")],"binary annotation count")!=sum))throw new IllegalArgumentException("Mixed continuous annotations have a reference sum, not a SNP count; conflicting count metadata is invalid");
            annotations.add(new Annotation(id,name,role,sum,kind.equals("binary")));
        }
        int count=annotations.size();
        if(count<2||count>integer(p,"maximum_annotations")||!annotations.get(0).role.equals("all")||!annotations.get(0).binary||annotations.stream().filter(a->a.role.equals("all")).count()!=1||annotations.stream().noneMatch(a->a.role.equals("target")))return finish(c,p,summary,false,false,"Declare one first binary all-SNP baseline, all registered nuisance annotations and at least one prespecified target within maximum_annotations");
        double[] m=annotations.stream().mapToDouble(Annotation::count).toArray();double[][] overlap=new double[count][count];
        var ot=DelimitedData.readResult(snapshot(c,c.project().resolve(text(p.get("overlap"))),"overlap.tsv"));var seen=new LinkedHashSet<String>();
        for(String[] row:ot.rows()){
            String id=row[ot.column("term_id")];int i=new ArrayList<>(ids).indexOf(id);if(i<0||!seen.add(id))throw new IllegalArgumentException("Unknown or duplicate annotation overlap row");
            for(int j=0;j<count;j++)overlap[i][j]=number(row[ot.column(annotations.get(j).id)],"annotation overlap");
        }
        if(seen.size()!=count)throw new IllegalArgumentException("Incomplete annotation overlap matrix");
        boolean[] binary=new boolean[count];for(int j=0;j<count;j++)binary[j]=annotations.get(j).binary;
        if(mixed)StratifiedLdScoreRegression.validateMixedMoments(m,overlap,binary);else StratifiedLdScoreRegression.validateMoments(m,overlap);
        for(var a:annotations)if(a.binary&&a.role.equals("target")&&a.count==m[0])throw new IllegalArgumentException("A target covering all reference SNPs has no complement enrichment test");
        summary.put("annotation_count",count);summary.put("target_count",annotations.stream().filter(a->a.role.equals("target")).count());summary.put("reference_snps",m[0]);
        summary.put("continuous_target_count",annotations.stream().filter(a->a.role.equals("target")&&!a.binary).count());summary.put("binary_target_count",annotations.stream().filter(a->a.role.equals("target")&&a.binary).count());
        var lt=DelimitedData.readResult(snapshot(c,c.project().resolve(text(p.get("ld_scores"))),"ld-scores.tsv"));
        if(lt.rows().size()>integer(p,"maximum_snps"))return finish(c,p,summary,false,false,"LD reference exceeds maximum_snps; no top-SNP truncation was applied");
        var ld=new LinkedHashMap<String,Ld>();var identities=new LinkedHashSet<String>();
        for(String[] row:lt.rows()){
            String id=row[lt.column("variant_id")],ref=allele(row[lt.column("ref")]),alt=allele(row[lt.column("alt")]);
            int chr=chromosome(row[lt.column("chromosome")]),pos=positiveInteger(row[lt.column("position")],"LD position");
            if(id.isBlank()||ref.equals(alt)||!identities.add(alleleIdentity(chr,pos,ref,alt)))throw new IllegalArgumentException("Invalid/duplicate allele-resolved LD reference SNP, including reversed coding under another ID");
            double[] scores=new double[count];for(int j=0;j<count;j++)scores[j]=number(row[lt.column("ld_"+annotations.get(j).id)],"LD score");
            if(ld.putIfAbsent(id,new Ld(id,chr,pos,ref,alt,number(row[lt.column("weight_ld")],"weight LD"),scores))!=null)throw new IllegalArgumentException("Duplicate LD-reference variant_id");
        }
        Path sourcePath;DelimitedData st;
        if(source.containsKey("file")){
            sourcePath=c.project().resolve(text(source.get("file")));if(!Files.isRegularFile(sourcePath))return finish(c,p,summary,false,false,"Supply the declared complete GWAS summary file");
            st=DelimitedData.readResult(snapshot(c,sourcePath,"gwas-summary.tsv"));summary.put("source_origin","externally_produced_complete_summary");
        }else{
            var official=StudyPrimaryResults.official(c);if(official==null)return finish(c,p,summary,false,false,"Supply a complete calibrated GWAS summary or select a completed official GWAS result with its allele/coordinate/N columns");
            sourcePath=official.source().path();snapshot(c,sourcePath,"gwas-summary.tsv");st=official.table();summary.put("primary_selection",official.receipt());summary.put("source_origin","official_scoped_results");
            if(!official.family().isEmpty()&&!"complete".equals(official.family().get("coverage")))return finish(c,p,summary,false,false,"Selected-only supplied primary results cannot define the full S-LDSC regression source");
        }
        if(st.rows().size()!=((Number)source.get("tested_snps")).intValue())return finish(c,p,summary,false,false,"Declared GWAS tested_snps does not match the complete supplied source row count");
        int idColumn=st.column(text(source.get("id_column"))),nColumn=st.column(text(source.get("n_column")));
        int chrColumn=st.column(text(source.get("chromosome_column"))),posColumn=st.column(text(source.get("position_column"))),refColumn=st.column(text(source.get("ref_column"))),altColumn=st.column(text(source.get("alt_column")));
        String statistic=text(source.get("statistic"));int statColumn=statistic.equals("beta_se")?st.column("beta"):st.column(text(source.get("statistic_column")));int seColumn=statistic.equals("beta_se")?st.column("se"):-1;
        double maxN=0;var sourceIds=new LinkedHashSet<String>();var sourceIdentity=new LinkedHashSet<String>();var matched=new ArrayList<Matched>();var audit=new ArrayList<String[]>();var dispositions=new LinkedHashMap<String,Integer>();int candidate=0;
        for(String[] row:st.rows()){
            String id=row[idColumn];if(id.isBlank()||!sourceIds.add(id))throw new IllegalArgumentException("Duplicate/empty GWAS variant identity");Ld item=ld.get(id);if(item==null){disposition(dispositions,"not_in_regression_reference");continue;}candidate++;
            String status="eligible";double chi=Double.NaN,n=Double.NaN;
            try{
                int chr=chromosome(row[chrColumn]),pos=positiveInteger(row[posColumn],"GWAS position");String ref=allele(row[refColumn]),alt=allele(row[altColumn]);
                if(ref.equals(alt)||!sourceIdentity.add(alleleIdentity(chr,pos,ref,alt)))throw new IllegalArgumentException("duplicate allele identity");
                if(chr!=item.chromosome||pos!=item.position||!(ref.equals(item.ref)&&alt.equals(item.alt)||ref.equals(item.alt)&&alt.equals(item.ref)))status="coordinate_or_allele_mismatch";
                else if(excluded(p,chr,pos))status="prespecified_region_exclusion";
                else{
                    n=number(row[nColumn],"GWAS N");double value=number(row[statColumn],"GWAS statistic");
                    if(n<=0)throw new IllegalArgumentException("nonpositive N");
                    if(statistic.equals("beta_se")){double se=number(row[seColumn],"GWAS SE");if(se<=0||StudyResultShape.pOnly(st,row))throw new IllegalArgumentException("not a calibrated Wald estimate");value/=se;}
                    chi=statistic.equals("chi_square")?value:value*value;if(chi<0||!Double.isFinite(chi))throw new IllegalArgumentException("invalid chi-square");
                    maxN=Math.max(maxN,n);matched.add(new Matched(item,chi,n));
                }
            }catch(IllegalArgumentException invalid){status="invalid_summary_row";}
            if(!status.equals("eligible")){disposition(dispositions,status);audit.add(new String[]{id,status});}
        }
        int eligibleReference=0;for(Ld item:ld.values())if(!excluded(p,item.chromosome,item.position))eligibleReference++;
        int matchedBeforeChi=matched.size();double coverage=eligibleReference==0?0:(double)matchedBeforeChi/eligibleReference;
        double chiMax=p.containsKey("chi_square_max")?((Number)p.get("chi_square_max")).doubleValue():Math.max(80,.001*maxN);
        var retained=new ArrayList<Matched>();for(var row:matched)if(row.chi>=chiMax){disposition(dispositions,"chi_square_at_or_above_prespecified_max");audit.add(new String[]{row.ld.id,"chi_square_at_or_above_prespecified_max"});}else{retained.add(row);disposition(dispositions,"eligible");}
        for(Ld item:ld.values())if(!sourceIds.contains(item.id)){disposition(dispositions,"missing_gwas_summary");audit.add(new String[]{item.id,"missing_gwas_summary"});}
        FollowupSupport.table(c.outputDirectory().resolve("snp-qc-exclusions.tsv"),List.of("variant_id","disposition"),audit);
        summary.put("source_snps",st.rows().size());summary.put("reference_regression_snps",ld.size());summary.put("reference_eligible_before_chi_square",eligibleReference);summary.put("source_reference_id_matches",candidate);summary.put("matched_valid_before_chi_square",matchedBeforeChi);summary.put("reference_coverage",coverage);summary.put("chi_square_max_exclusive",chiMax);summary.put("snp_dispositions",dispositions);summary.put("regression_snps",retained.size());
        if(coverage<((Number)p.get("minimum_coverage")).doubleValue())return finish(c,p,summary,false,false,"Insufficient full regression-reference coverage; inspect exclusions. No enrichment FDR was generated");
        retained.sort(Comparator.comparingInt((Matched row)->row.ld.chromosome).thenComparingInt(row->row.ld.position).thenComparing(row->row.ld.id));
        int n=retained.size(),b=integer(p,"blocks");long chromosomes=retained.stream().map(row->row.ld.chromosome).distinct().count();summary.put("regression_chromosomes",chromosomes);
        if(n<integer(p,"minimum_snps")||n<=b||chromosomes<integer(p,"minimum_chromosomes"))return finish(c,p,summary,false,false,"Insufficient SNP/chromosome/block coverage for the registered genome-wide analysis");
        double[] chi=new double[n],ns=new double[n],weight=new double[n];double[][] scores=new double[n][];int[] blocks=new int[n];var regressionRows=new ArrayList<String[]>();
        // LDSC uses floor(linspace(0,n,B+1)) separators, including unequal-by-one blocks.
        int block=0;for(int i=0;i<n;i++){while(block+1<b&&i>=(int)((long)(block+1)*n/b))block++;var row=retained.get(i);chi[i]=row.chi;ns[i]=row.n;weight[i]=row.ld.weight;scores[i]=row.ld.scores;blocks[i]=block;regressionRows.add(new String[]{row.ld.id,String.valueOf(row.ld.chromosome),String.valueOf(row.ld.position),String.valueOf(block),String.valueOf(row.chi),String.valueOf(row.n)});}
        FollowupSupport.table(c.outputDirectory().resolve("regression-snps.tsv"),List.of("variant_id","chromosome","position","block","chi_square","n"),regressionRows);
        summary.put("sample_size_min",java.util.Arrays.stream(ns).min().orElseThrow());summary.put("sample_size_max",java.util.Arrays.stream(ns).max().orElseThrow());summary.put("sample_size_mean",java.util.Arrays.stream(ns).average().orElseThrow());
        var result=mixed?StratifiedLdScoreRegression.fitMixed(chi,scores,weight,ns,blocks,m,overlap,binary,"free".equals(p.get("intercept")),((Number)p.get("maximum_work")).longValue()):StratifiedLdScoreRegression.fit(chi,scores,weight,ns,blocks,m,overlap,"free".equals(p.get("intercept")),((Number)p.get("maximum_work")).longValue());
        var standardized=StratifiedLdScoreRegression.standardizedEffects(result,m,overlap);double[] tauStar=standardized.effects(),tauStarSe=standardized.standardErrors(),annotationSd=standardized.annotationStandardDeviations();
        summary.put("total_heritability",result.totalHeritability());summary.put("total_heritability_se",result.totalStandardError());summary.put("intercept",result.intercept());summary.put("intercept_se",result.interceptStandardError());summary.put("mean_chi_square",result.meanChiSquare());summary.put("aggregate_weight_heritability",result.aggregateWeightHeritability());
        double hz=result.totalStandardError()>0?result.totalHeritability()/result.totalStandardError():Double.NaN;summary.put("total_heritability_z",value(hz));
        boolean complete=result.totalHeritability()>0&&Double.isFinite(hz)&&hz>=((Number)p.get("minimum_h2_z")).doubleValue()&&java.util.Arrays.stream(result.deleteTotals()).allMatch(v->v>0);
        var logProbabilities=new LinkedHashMap<String,Double>();double[] probability=result.probabilities(),enrichment=result.enrichments(),ese=result.enrichmentStandardErrors();
        double[] co=result.coefficients(),cov=result.coefficientCovariance(),contrast=result.contrasts(),cse=result.contrastStandardErrors(),logP=new double[count];java.util.Arrays.fill(logP,Double.NaN);
        for(int j=0;j<count;j++)if(annotations.get(j).role.equals("target")){
            logP[j]=binary[j]&&cse[j]>0?Math.log(2)+jdistlib.T.cumulative(-Math.abs(contrast[j]/cse[j]),b,true,true):!binary[j]&&cov[j*count+j]>0?SummaryMath.logP(co[j]/Math.sqrt(cov[j*count+j])):Double.NaN;
            if(!Double.isFinite(probability[j])||!Double.isFinite(logP[j])||(binary[j]?(!Double.isFinite(enrichment[j])||!Double.isFinite(ese[j])):(!Double.isFinite(tauStar[j])||!Double.isFinite(tauStarSe[j]))))complete=false;else logProbabilities.put(annotations.get(j).id,logP[j]);
        }
        var adjusted=new LinkedHashMap<String,Double>();var logAdjusted=new LinkedHashMap<String,Double>();
        if(complete){double[] adjustedLogs=org.jlinalg.enrichment.EnrichmentAnalysis.adjustLog(logProbabilities.values().stream().mapToDouble(Double::doubleValue).toArray(),org.jlinalg.enrichment.EnrichmentAnalysis.Fdr.valueOf(text(p.get("adjustment"))));int j=0;for(String id:logProbabilities.keySet()){logAdjusted.put(id,adjustedLogs[j]);adjusted.put(id,Math.exp(adjustedLogs[j++]));}}
        summary.put("official_family_complete",complete);summary.put("multiplicity_precision","Complete registered target family adjusted in natural-log space; linear P/adjusted-P may underflow, retained log fields remain authoritative for tail reporting");
        summary.put("testing_family",Map.of("id","gwas_enrichment:"+c.operation().get("hypothesis"),"coverage",complete?"complete":"unresolved","tested_count",annotations.stream().filter(a->a.role.equals("target")).count(),"adjustment",p.get("adjustment"),"test",mixed?"binary_category_vs_complement_t_B_or_continuous_conditional_coefficient_normal_as_declared_by_annotation_type":"two_sided_per_snp_category_vs_complement_t_with_B_degrees_of_freedom","all_targets_fitted_jointly",true));
        if(n<200000)summary.put("required_manuscript_caveat",summary.get("required_manuscript_caveat")+" Fewer than 200,000 regression SNPs remain; precision and genome-wide representativeness need explicit review. Small synthetic verification inputs establish numerical execution only.");
        summary.put("standardized_effect_definition","tau_star = coefficient * sqrt(Q_jj/M - (S_j/M)^2) * M / total_h2; population annotation SD over the complete reference universe, not the regression subset");summary.put("standardized_effect_se","Delete-block ratio jackknife including coefficient and total-h2 uncertainty; reference moments and initial weights held fixed. Primary continuous P tests raw coefficient/its jackknife SE using a two-sided normal tail.");
        var targets=new ArrayList<String[]>();var coefficients=new ArrayList<String[]>();var covariance=new ArrayList<String[]>();var deletes=new ArrayList<String[]>();double[] cat=result.categoryHeritability(),prop=result.proportions(),pse=result.proportionStandardErrors();
        for(int j=0;j<count;j++){
            var a=annotations.get(j);double se=Math.sqrt(cov[j*count+j]);String coefficientP=se>0?value(SummaryMath.p(co[j]/se)):"NA";coefficients.add(new String[]{a.id,a.name,a.role,a.binary?value(a.count):"NA",value(co[j]),value(se),coefficientP,value(cat[j]),value(prop[j]),value(pse[j]),value(enrichment[j]),value(ese[j]),a.binary?"binary":"continuous",value(a.count),value(annotationSd[j]),value(tauStar[j]),value(tauStarSe[j])});
            for(int k=0;k<count;k++)covariance.add(new String[]{a.id,annotations.get(k).id,value(cov[j*count+k])});
            if(a.role.equals("target"))targets.add(new String[]{a.id,a.name,complete?"ok":"family_unresolved",a.binary?"S-LDSC_category_vs_complement_t_B":"S-LDSC_conditional_coefficient_normal",a.binary?value(a.count/m[0]):"NA",value(cat[j]),value(prop[j]),value(pse[j]),value(enrichment[j]),value(ese[j]),value(contrast[j]),value(cse[j]),value(probability[j]),value(adjusted.getOrDefault(a.id,Double.NaN)),text(p.get("adjustment")),a.binary?"binary":"continuous",a.binary?"category_vs_complement":"conditional_coefficient",value(a.count),value(annotationSd[j]),value(co[j]),value(se),coefficientP,value(tauStar[j]),value(tauStarSe[j]),text(summary.get("required_manuscript_caveat")),value(logP[j]),value(logAdjusted.getOrDefault(a.id,Double.NaN)),se>0?value(SummaryMath.logP(co[j]/se)):"NA"});
        }
        double[][] dc=result.deleteCoefficients();double[] dt=result.deleteTotals(),di=result.deleteIntercepts();for(int k=0;k<b;k++)for(int j=0;j<count;j++)deletes.add(new String[]{String.valueOf(k),annotations.get(j).id,value(dc[k][j]),value(dt[k]),value(di[k])});
        FollowupSupport.table(c.outputDirectory().resolve("gwas-enrichment.tsv"),List.of("term_id","term_name","status","method","reference_snp_proportion","category_heritability","heritability_proportion","heritability_proportion_se","enrichment","enrichment_se","per_snp_contrast","per_snp_contrast_se","p_value","adjusted_p","adjustment","annotation_type","inference_target","annotation_sum","annotation_sd","coefficient","coefficient_se","coefficient_p","standardized_coefficient","standardized_coefficient_se","required_manuscript_caveat","log_p_value","log_adjusted_p","coefficient_log_p"),targets);
        FollowupSupport.table(c.outputDirectory().resolve("sldsc-coefficients.tsv"),List.of("term_id","term_name","role","reference_count","coefficient","coefficient_se","coefficient_p","category_heritability","heritability_proportion","heritability_proportion_se","enrichment","enrichment_se","annotation_type","annotation_sum","annotation_sd","standardized_coefficient","standardized_coefficient_se"),coefficients);
        FollowupSupport.table(c.outputDirectory().resolve("sldsc-coefficient-covariance.tsv"),List.of("left","right","covariance"),covariance);
        FollowupSupport.table(c.outputDirectory().resolve("sldsc-block-deletes.tsv"),List.of("block","term_id","coefficient","total_heritability","intercept"),deletes);
        double[][] standardizedDeletes=standardized.deleteEffects();var standardDeletes=new ArrayList<String[]>();for(int k=0;k<b;k++)for(int j=0;j<count;j++)standardDeletes.add(new String[]{String.valueOf(k),annotations.get(j).id,value(standardizedDeletes[k][j])});FollowupSupport.table(c.outputDirectory().resolve("sldsc-standardized-block-deletes.tsv"),List.of("block","term_id","standardized_coefficient"),standardDeletes);
        return finish(c,p,summary,complete,true,complete?"Fitted prespecified S-LDSC target and nuisance annotations; adjusted the complete registered annotation-test family":"Retained S-LDSC estimates and diagnostics; weak/nonpositive total heritability, invalid delete ratios or unestimable target tests leave the entire annotation correction family unresolved");
    }
    private static void disposition(Map<String,Integer> counts,String status){counts.merge(status,1,Integer::sum);}
    private static boolean excluded(Map<String,Object> p,int chr,int pos){for(Object raw:list(p.get("excluded_regions"))){var r=map(raw);if(((Number)r.get("chromosome")).intValue()==chr&&pos>=((Number)r.get("start")).intValue()&&pos<=((Number)r.get("end")).intValue())return true;}return false;}
    private static String allele(String value){String a=value.toUpperCase(java.util.Locale.ROOT);if(!Set.of("A","C","G","T").contains(a))throw new IllegalArgumentException("S-LDSC reference and matched summary rows must be biallelic SNPs");return a;}
    private static String alleleIdentity(int chromosome,int position,String ref,String alt){return chromosome+":"+position+":"+(ref.compareTo(alt)<0?ref+":"+alt:alt+":"+ref);}
    private static int chromosome(String value){String c=value.replaceFirst("(?i)^chr","");int chr=positiveInteger(c,"chromosome");if(chr>22)throw new IllegalArgumentException("This S-LDSC model supports autosomes 1..22");return chr;}
    private static int positiveInteger(String value,String label){double d=number(value,label);if(d!=Math.rint(d)||d<=0||d>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid "+label);return (int)d;}
    private static int integer(Map<String,Object> p,String key){return ((Number)p.get(key)).intValue();}
    private static double number(String value,String label){double d;try{d=Double.parseDouble(value);}catch(NumberFormatException invalid){throw new IllegalArgumentException("Invalid "+label);}if(!Double.isFinite(d))throw new IllegalArgumentException("Nonfinite "+label);return d;}
    private static String value(double d){return Double.isFinite(d)?String.valueOf(d):"NA";}
    private static Path snapshot(Context c,Path source,String name)throws IOException{String hash=FollowupSupport.hash(source);if(source.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".csv"))name=name.replaceFirst("\\.tsv$",".csv");Path copy=c.outputDirectory().resolve(name);Files.copy(source,copy);if(!hash.equals(FollowupSupport.hash(copy))||!hash.equals(FollowupSupport.hash(source)))throw new IOException("GWAS enrichment input changed during snapshot: "+source);return copy;}
    private static Result finish(Context c,Map<String,Object> p,Map<String,Object> summary,boolean complete,boolean performed,String message)throws IOException{
        summary.put("method_report",Map.of("performed",performed,"operation","gwas_enrichment","method","mixed".equals(p.get("annotation_mode"))?"Observed-scale mixed-annotation S-LDSC; fixed baseline initial weights; genome-ordered delete-block jackknife; binary category t(B) or continuous conditional-coefficient normal tests; reference-SD standardized effects with ratio jackknife":"Observed-scale overlapping binary-annotation stratified LD score regression; fixed aggregate initial weights; equal-count genome-ordered delete-block jackknife; two-sided category-versus-complement t(B) test","parameters",p,"software","JLinAlg "+JLinAlgCli.version(),"references",List.of("https://doi.org/10.1038/ng.3404","https://doi.org/10.1038/ng.3954","https://github.com/bulik/ldsc/tree/2fdeeb3b44379408794154993dbd6101b8946b7e"),"interpretation",message));
        summary.put("interpretation",message);if(!complete)summary.put("needs",List.of(StudyNeed.review("gwas-enrichment:"+c.operation().get("hypothesis"),"What is needed for the registered GWAS heritability enrichment?",message,true).document()));
        write(c.outputDirectory().resolve("gwas-enrichment-analysis.yaml"),summary);var outputs=new ArrayList<String>();try(var paths=Files.walk(c.outputDirectory())){for(Path path:paths.filter(Files::isRegularFile).sorted().toList())outputs.add(c.outputDirectory().relativize(path).toString().replace('\\','/'));}
        return new Result(complete?State.COMPLETE:State.WAITING_FOR_INPUT,message,outputs,summary);
    }
}
