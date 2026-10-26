/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ordered original QC decisions; reference subsets never become implicit final-cohort exclusions. */
final class PlinkQcPipeline {
    record Settings(String engine,String order,double mind,double geno,double maf,long mac,String frequencyPopulation,
            String hweMode,double hweP,double hweK,boolean keepFewHet,String hwePopulation,String hweFounders,
            String hweUnavailable,String finalCheck,int threads,int memory,int timeout,long reportBytes) { }
    private final PlinkQcInputs.Prepared input;
    private final PlinkRuntime runtime;
    private final Path directory;
    private final Settings settings;
    private final Map<String,Object> session;
    private final List<String> warnings;
    private Set<PlinkQcInputs.Id> samples;
    private Set<String> variants;
    private final Map<PlinkQcInputs.Id,String> sampleReasons=new LinkedHashMap<>();
    private final Map<String,String> variantReasons=new LinkedHashMap<>();
    private final List<Map<String,Object>> stages=new ArrayList<>();
    private Map<PlinkQcInputs.Id,PlinkQcReports.Missing> sampleAtDecision=Map.of(),finalSampleMissing=Map.of();
    private Map<String,PlinkQcReports.Missing> variantAtDecision=Map.of(),finalVariantMissing=Map.of();
    private Map<String,PlinkQcReports.Frequency> frequencyAtDecision=Map.of(),finalFrequency=Map.of();
    private Map<String,PlinkQcReports.Hwe> hweAtDecision=Map.of(),finalHwe=Map.of();
    private Set<PlinkQcInputs.Id> finalSampleFailures=Set.of();
    private final Map<String,String> finalVariantFailures=new LinkedHashMap<>();

    PlinkQcPipeline(PlinkQcInputs.Prepared input,PlinkRuntime runtime,Path directory,Settings settings,Map<String,Object> session,List<String> warnings) {
        this.input=input;this.runtime=runtime;this.directory=directory;this.settings=settings;this.session=session;this.warnings=warnings;
        samples=new LinkedHashSet<>(input.initialSamples());variants=new LinkedHashSet<>(input.initialVariants());
        for(var sample:input.samples().values())sampleReasons.put(sample.staged(),sample.initialInclude()?"RETAINED":"PREEXCLUDED_BY_SAMPLE_TABLE");
        for(var variant:input.variants().values())variantReasons.put(variant.staged(),!variant.autosomal()?"PREEXCLUDED_NONAUTOSOMAL":!variants.contains(variant.staged())?"PREEXCLUDED_UNRESOLVED_ALLELES":"RETAINED");
    }
    long execute(Path output) throws Exception {
        try {
            if(settings.order().equals("samples-then-variants")){sampleFilter();variantFilters();}
            else {variantFilters();sampleFilter();}
            recheck();writeDecisions(output);
            long noCalls=finalVariantMissing.values().stream().filter(m->m.missing()==m.opportunities()).count();if(noCalls==variants.size())throw new IOException("No retained variant has any observed genotype calls");
            if((!finalSampleFailures.isEmpty()||!finalVariantFailures.isEmpty())&&settings.finalCheck().equals("fail"))throw new IOException("One-pass QC retained individuals/variants which fail final-cohort criteria;see final-threshold-violations.tsv. No filtered genotype set was published");
            run("final-genotypes",samples,variants,List.of("--make-bed","--indiv-sort","none"));
            PlinkQcInputs.verify(input,directory);
            long cells=PlinkQcInputs.export(input,directory.resolve("final-genotypes"),samples,variants,output);PlinkQcInputs.verify(input,directory);session.put("verified_retained_genotype_cells",cells);session.put("retained_samples",samples.size());session.put("retained_variants",variants.size());
            long unresolved=variants.stream().filter(id->!input.variants().get(id).resolved()).count();if(unresolved>0)warnings.add(unresolved+" retained variants have unresolved allele labels under the explicit retain-with-warning policy. Required action: resolve their allele metadata before genetic-effect interpretation;raw call preservation does not establish allele identity.");
            long zeroPositions=variants.stream().filter(id->input.variants().get(id).bp().matches("[+]?0+")).count();if(zeroPositions>0)warnings.add(zeroPositions+" retained variants have zero bp coordinates. Required action: establish coordinate/assembly provenance before genomic mapping.");
            if(!finalSampleFailures.isEmpty()||!finalVariantFailures.isEmpty())warnings.add("One-pass QC retained "+finalSampleFailures.size()+" samples and "+finalVariantFailures.size()+" variants failing final-cohort criteria,as permitted by --final-check warn. Required action: review final-threshold-violations.tsv and the declared operation order;use --final-check fail or an explicitly justified additional QC pass before claiming all final thresholds are met. No threshold was relaxed automatically.");
            if(noCalls>0)warnings.add(noCalls+" retained markers have no observed calls. Required action: exclude or replace them before analyses requiring observed genotype information;they are not evidence of HWE or absence of variation.");
            return cells;
        } finally {session.put("qc_stages",stages);writeDecisions(output);}
    }
    private void sampleFilter() throws Exception {
        run("sample-before",samples,variants,List.of("--missing"));sampleAtDecision=sampleMissing("sample-before",samples,true);validateSampleOpportunities(sampleAtDecision,variants.size());
        run("sample-filter",samples,variants,List.of("--mind",Double.toString(settings.mind()),"--missing"));var retained=sampleMissing("sample-filter",samples,false);validateSampleOpportunities(retained,variants.size());
        if(retained.isEmpty())throw new IOException("Original QC removed every sample under --maximum-sample-missing");
        for(var id:samples)if(!retained.containsKey(id))sampleReasons.put(id,"SAMPLE_MISSINGNESS");stage("sample-missingness",samples.size(),retained.size(),variants.size(),variants.size(),samples.size());samples=new LinkedHashSet<>(retained.keySet());
    }
    private void variantFilters() throws Exception {
        run("variant-before",samples,variants,List.of("--missing"));variantAtDecision=variantMissing("variant-before",variants);validateVariantOpportunities(variantAtDecision,samples.size());
        run("variant-filter",samples,variants,List.of("--geno",Double.toString(settings.geno()),"--write-snplist"));filter(snps("variant-filter",variants),"VARIANT_MISSINGNESS",samples.size());
        var reference=PlinkQcInputs.population(input,samples,settings.frequencyPopulation(),false);
        run("frequency-before",reference,variants,List.of("--nonfounders","--freq","counts"));frequencyAtDecision=frequencies("frequency-before",variants);validateAlleleCounts(frequencyAtDecision,reference.size());
        run("frequency-filter",reference,variants,frequencyFlags());filter(snps("frequency-filter",variants),"MAF_OR_MAC",reference.size());
        if(!settings.hweMode().equals("off")) {
            var population=hwePopulation();run("hwe-before",population,variants,hardyFlags());hweAtDecision=hwe("hwe-before",variants);validateHweCounts(hweAtDecision,population.size());
            run("hwe-filter",population,variants,hweFlags());var retained=snps("hwe-filter",variants);applyHweUnavailable(hweAtDecision,retained,settings.hweUnavailable(),false);filter(retained,"HWE_OR_EXPLICIT_UNREPORTABLE_POLICY",population.size());
        }
    }
    private void recheck() throws Exception {
        run("final-missing",samples,variants,List.of("--missing"));finalSampleMissing=sampleMissing("final-missing",samples,true);finalVariantMissing=variantMissing("final-missing",variants);validateSampleOpportunities(finalSampleMissing,variants.size());validateVariantOpportunities(finalVariantMissing,samples.size());
        run("final-sample-check",samples,variants,List.of("--mind",Double.toString(settings.mind()),"--missing"));var checked=sampleMissing("final-sample-check",samples,false);var failures=new LinkedHashSet<>(samples);failures.removeAll(checked.keySet());finalSampleFailures=failures;
        run("final-variant-check",samples,variants,List.of("--geno",Double.toString(settings.geno()),"--write-snplist"));markFinalDifference(snps("final-variant-check",variants),"VARIANT_MISSINGNESS");
        var frequencyPopulation=PlinkQcInputs.population(input,samples,settings.frequencyPopulation(),false);
        run("final-frequency",frequencyPopulation,variants,List.of("--nonfounders","--freq","counts"));finalFrequency=frequencies("final-frequency",variants);validateAlleleCounts(finalFrequency,frequencyPopulation.size());
        run("final-frequency-check",frequencyPopulation,variants,frequencyFlags());markFinalDifference(snps("final-frequency-check",variants),"MAF_OR_MAC");
        if(!settings.hweMode().equals("off")) {
            var population=hwePopulation();run("final-hwe",population,variants,hardyFlags());finalHwe=hwe("final-hwe",variants);validateHweCounts(finalHwe,population.size());run("final-hwe-check",population,variants,hweFlags());var retained=snps("final-hwe-check",variants);applyHweUnavailable(finalHwe,retained,settings.hweUnavailable(),true);markFinalDifference(retained,"HWE_OR_EXPLICIT_UNREPORTABLE_POLICY");
            long zeroP=finalHwe.values().stream().filter(PlinkQcReports.Hwe::unresolvedZero).count();if(zeroP>0)warnings.add(zeroP+" original HWE P-values were printed as zero. Required action: use retained original filter decisions for QC and obtain a report with adequate precision before numerical P-value interpretation;no floor or exact zero was inferred.");
            long degenerate=finalHwe.values().stream().filter(PlinkQcReports.Hwe::degenerate).count();if(degenerate>0)warnings.add(degenerate+" final HWE reference sets are monomorphic or conditional singletons. Required action: review cohort-specific MAC/MAF support;these degenerate conditional tests do not establish population equilibrium.");
        }
        session.put("final_sample_threshold_failures",finalSampleFailures.size());session.put("final_variant_threshold_failures",finalVariantFailures.size());
    }
    private Set<PlinkQcInputs.Id> hwePopulation() throws IOException {return PlinkQcInputs.population(input,samples,settings.hweFounders(),settings.hwePopulation().equals("controls"));}
    private List<String> hardyFlags(){var flags=new ArrayList<>(List.of("--nonfounders","--hardy"));if(settings.hweMode().equals("midp"))flags.add("midp");return flags;}
    private List<String> frequencyFlags(){var flags=new ArrayList<>(List.of("--nonfounders"));if(settings.maf()>0)flags.addAll(List.of("--maf",Double.toString(settings.maf())));flags.addAll(List.of("--mac",Long.toString(settings.mac()),"--write-snplist"));return flags;}
    private List<String> hweFlags(){var flags=new ArrayList<>(List.of("--nonfounders","--hwe",Double.toString(settings.hweP())));if(settings.engine().equals("plink1"))flags.add("include-nonctrl");else{flags.add(Double.toString(settings.hweK()));if(settings.keepFewHet())flags.add("keep-fewhet");}if(settings.hweMode().equals("midp"))flags.add("midp");flags.add("--write-snplist");return flags;}
    private void applyHweUnavailable(Map<String,PlinkQcReports.Hwe> reports,Set<String> retained,String policy,boolean finalPass) throws IOException {
        long count=0;for(String id:reports.keySet())if(!reports.get(id).informative()||!input.variants().get(id).resolved()){count++;if(policy.equals("error"))throw new IOException("HWE has no interpretable calls/P-value or unresolved alleles at "+input.variants().get(id).original()+";original P=1 with zero calls is not evidence of equilibrium");if(policy.equals("exclude"))retained.remove(id);}
        if(count>0&&policy.equals("retain-with-warning"))warnings.add((finalPass?"Final":"Decision-stage")+" HWE has "+count+" unreportable markers retained under the explicit policy. Required action: inspect reference-cohort call counts and allele metadata;do not interpret a zero-call original P=1 as a tested equilibrium result. Original masks are still respected.");
    }
    private void filter(Set<String> retained,String reason,int population) throws IOException {if(retained.isEmpty())throw new IOException("No variant remains after "+reason);for(String id:variants)if(!retained.contains(id))variantReasons.put(id,reason);stage(reason,samples.size(),samples.size(),variants.size(),retained.size(),population);variants=new LinkedHashSet<>(retained);}
    private void stage(String name,int beforeSamples,int afterSamples,int beforeVariants,int afterVariants,int population){stages.add(Map.of("stage",name,"before_samples",beforeSamples,"after_samples",afterSamples,"before_variants",beforeVariants,"after_variants",afterVariants,"reference_population_samples",population));}
    private void markFinalDifference(Set<String> retained,String reason){for(String id:variants)if(!retained.contains(id))finalVariantFailures.merge(id,reason,(a,b)->a+";"+b);}
    private void run(String stage,Set<PlinkQcInputs.Id> people,Set<String> markers,List<String> flags) throws Exception {
        if(people.isEmpty()||markers.isEmpty())throw new IOException("Cannot run an original QC stage on an empty population/family");
        Path keep=directory.resolve(stage+".keep"),extract=directory.resolve(stage+".extract");PlinkQcInputs.keep(keep,people);PlinkQcInputs.snps(extract,markers);String kh=FollowupSupport.hash(keep),vh=FollowupSupport.hash(extract);
        var args=new ArrayList<>(List.of("--bfile","input","--keep",keep.getFileName().toString(),"--extract",extract.getFileName().toString(),"--autosome","--threads",Integer.toString(settings.threads()),"--memory",Integer.toString(settings.memory())));if(settings.engine().equals("plink1"))args.addAll(List.of("--keep-allele-order","--allow-no-sex"));else args.add("--no-psam-pheno");args.addAll(flags);args.addAll(List.of("--out",stage));runtime.execute(args,directory,stage,settings.timeout());if(!kh.equals(FollowupSupport.hash(keep))||!vh.equals(FollowupSupport.hash(extract)))throw new IOException("QC reference mask changed during an original stage");
    }
    private Map<PlinkQcInputs.Id,PlinkQcReports.Missing> sampleMissing(String stage,Set<PlinkQcInputs.Id> expected,boolean complete) throws IOException{return PlinkQcReports.samples(directory.resolve(stage+(settings.engine().equals("plink1")?".imiss":".smiss")),expected,complete,input.bounds().samples(),settings.reportBytes(),input.bounds().line());}
    private Map<String,PlinkQcReports.Missing> variantMissing(String stage,Set<String> expected) throws IOException{return PlinkQcReports.variants(directory.resolve(stage+(settings.engine().equals("plink1")?".lmiss":".vmiss")),expected,input.bounds().variants(),settings.reportBytes(),input.bounds().line());}
    private Map<String,PlinkQcReports.Frequency> frequencies(String stage,Set<String> expected) throws IOException{return PlinkQcReports.frequencies(directory.resolve(stage+(settings.engine().equals("plink1")?".frq.counts":".acount")),expected,input.bounds().variants(),settings.reportBytes(),input.bounds().line());}
    private Map<String,PlinkQcReports.Hwe> hwe(String stage,Set<String> expected) throws IOException{return PlinkQcReports.hwe(directory.resolve(stage+(settings.engine().equals("plink1")?".hwe":".hardy")),expected,input.bounds().variants(),settings.reportBytes(),input.bounds().line(),settings.hweMode());}
    private Set<String> snps(String stage,Set<String> expected) throws IOException{return PlinkQcReports.snps(directory.resolve(stage+".snplist"),expected,settings.reportBytes(),input.bounds().line());}
    private static void validateSampleOpportunities(Map<PlinkQcInputs.Id,PlinkQcReports.Missing> rows,int expected) throws IOException {for(var value:rows.values())if(value.opportunities()!=expected)throw new IOException("Original sample missingness denominator differs from its exact marker mask");}
    private static void validateVariantOpportunities(Map<String,PlinkQcReports.Missing> rows,int expected) throws IOException {for(var value:rows.values())if(value.opportunities()!=expected)throw new IOException("Original variant missingness denominator differs from its exact sample mask");}
    private static void validateAlleleCounts(Map<String,PlinkQcReports.Frequency> rows,int population) throws IOException {for(var value:rows.values())if(value.observations()>2L*population)throw new IOException("Allele count exceeds explicit frequency reference population");}
    private static void validateHweCounts(Map<String,PlinkQcReports.Hwe> rows,int population) throws IOException {for(var value:rows.values())if(value.called()>population)throw new IOException("HWE genotype count exceeds explicit reference population");}
    private static String number(double value){return Double.isNaN(value)?"":Double.toString(value);}
    private static String miss(PlinkQcReports.Missing value){return value==null?"\t\t":"\t"+value.missing()+"\t"+value.opportunities();}
    private void writeDecisions(Path output) throws IOException {
        try(var writer=Files.newBufferedWriter(output.resolve("sample-decisions.tsv"));var kept=Files.newBufferedWriter(output.resolve("qc-samples-retained.tsv"))){writer.write("source_ordinal\tfid\tiid\tupstream_fid\tupstream_iid\tgroup\tFAM_founder\tinitial_include\tretained\tdecision\tdecision_missing\tdecision_opportunities\tfinal_missing\tfinal_opportunities\tfinal_threshold_failure\n");kept.write("fid\tiid\tinclude\tgroup\n");for(var sample:input.samples().values()){writer.write((sample.ordinal()+1)+"\t"+sample.original().fid()+"\t"+sample.original().iid()+"\t"+sample.staged().fid()+"\t"+sample.staged().iid()+"\t"+sample.group()+"\t"+sample.founder()+"\t"+sample.initialInclude()+"\t"+samples.contains(sample.staged())+"\t"+sampleReasons.get(sample.staged())+miss(sampleAtDecision.get(sample.staged()))+miss(finalSampleMissing.get(sample.staged()))+"\t"+finalSampleFailures.contains(sample.staged())+"\n");if(samples.contains(sample.staged()))kept.write(sample.original().fid()+"\t"+sample.original().iid()+"\tyes\t"+sample.group()+"\n");}}
        try(var writer=Files.newBufferedWriter(output.resolve("variant-decisions.tsv"))){writer.write("source_ordinal\tvariant\tupstream_variant\tchromosome\tposition\toriginal_a1\toriginal_a2\tresolved_alleles\tretained\tdecision\tdecision_missing\tdecision_opportunities\tfinal_missing\tfinal_opportunities\tfrequency_ref_allele_observations\tfrequency_ref_MAC\tfrequency_ref_MAF\thwe_ref_called\thwe_ref_a1\thwe_ref_other\thwe_raw_p\thwe_reportability\tfinal_threshold_failures\n");for(var variant:input.variants().values()){String id=variant.staged();var frequency=finalFrequency.getOrDefault(id,frequencyAtDecision.get(id));var hwe=finalHwe.getOrDefault(id,hweAtDecision.get(id));String hweStatus=hwe==null?"NOT_EVALUATED":!variant.resolved()?"UNRESOLVED_ALLELES":hwe.called()==0?"NO_CALLS":!hwe.validP()?"MISSING_P":hwe.unresolvedZero()?"REPORTED_ZERO_NO_NUMERIC_RESOLUTION":hwe.degenerate()?"REPORTED_DEGENERATE":"REPORTED";
                writer.write((variant.ordinal()+1)+"\t"+variant.original()+"\t"+id+"\t"+variant.chromosome()+"\t"+variant.bp()+"\t"+variant.a1()+"\t"+variant.a2()+"\t"+variant.resolved()+"\t"+variants.contains(id)+"\t"+variantReasons.get(id)+miss(variantAtDecision.get(id))+miss(finalVariantMissing.get(id))+"\t"+(frequency==null?"\t\t":frequency.observations()+"\t"+frequency.minorCount()+"\t"+number(frequency.minorFrequency()))+"\t"+(hwe==null?"\t\t\t\t":hwe.called()+"\t"+hwe.first()+"\t"+hwe.second()+"\t"+hwe.rawP()+"\t")+hweStatus+"\t"+finalVariantFailures.getOrDefault(id,"")+"\n");}}
        try(var writer=Files.newBufferedWriter(output.resolve("final-threshold-violations.tsv"))){writer.write("kind\tid1\tid2\tcriteria\n");for(var id:finalSampleFailures){var original=input.samples().get(id).original();writer.write("sample\t"+original.fid()+"\t"+original.iid()+"\tSAMPLE_MISSINGNESS\n");}for(var entry:finalVariantFailures.entrySet())writer.write("variant\t"+input.variants().get(entry.getKey()).original()+"\t\t"+entry.getValue()+"\n");}
    }
}
