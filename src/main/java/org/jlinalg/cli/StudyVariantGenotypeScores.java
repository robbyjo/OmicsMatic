/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.DiploidScores;
import org.jlinalg.pipeline.VariantSources;
import org.jlinalg.study.StudyGeneticScorePolicy;
import org.jlinalg.study.StudyOperationExecutor.Context;

/** Complete immutable-model scoring through the existing VCF/BCF/BGEN readers. */
final class StudyVariantGenotypeScores {
    record Read(StudyVariantScorePreflight.Inventory inventory,DiploidScores.Accumulator accumulator,int[] participantToScore,Map<String,Object> summary) { }
    private StudyVariantGenotypeScores() { }
    static Read read(Context c,Map<String,Object> g,Map<String,Object> source,Map<StudyGenotypeScores.Key,StudyGenotypeScores.Weight> model,DelimitedData table,String[] roles,String[] units,int[] requested,long remainingVariants,long remainingCells,BufferedWriter audit,BufferedWriter coverage)throws IOException {
        String sourceId=text(source.get("id"));Path path=c.project().resolve(text(source.get("file")));var format=StudyGeneticScorePolicy.format(source);
        var inventory=StudyVariantScorePreflight.scan(path,format,g,remainingVariants,remainingCells);List<String> external=null;
        if(source.containsKey("samples")){
            Path samples=c.project().resolve(text(source.get("samples")));if(!Files.isRegularFile(samples)||Files.size(samples)>limit(g,"maximum_metadata_mib")*1048576L)throw new IllegalArgumentException("BGEN sample sidecar missing or above maximum_metadata_mib");long count=0,retained=0;
            try(var lines=new StudyGenotypeScores.Lines(samples,limit(g,"maximum_line_bytes"),limit(g,"maximum_metadata_mib")*1048576L,false)){for(String line;(line=lines.next())!=null;)if(!line.isBlank()){if(++count>limit(g,"maximum_samples")+2)throw new IllegalArgumentException("BGEN sample sidecar exceeds maximum_samples");retained+=256L+4L*line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;if(retained>limit(g,"maximum_retained_mib")*1048576L)throw new IllegalArgumentException("Estimated retained BGEN sidecar exceeds maximum_retained_mib");}}
            external=SampleFiles.read(samples);if(external.size()!=inventory.samples())throw new IllegalArgumentException("BGEN sidecar sample count differs from source");
        }
        org.jlinalg.pipeline.VariantSource variants;
        try{variants=VariantSources.open(path,format,external);}catch(RuntimeException invalid){throw decoderFailure(invalid);}
        var metadata=variants.metadata();if(metadata.sampleIds().size()!=inventory.samples())throw new IOException("Variant header changed after admission scan");
        if(external!=null&&!external.equals(metadata.sampleIds()))throw new IllegalArgumentException("BGEN supplied sample identities conflict with embedded identities");
        var positions=new HashMap<String,Integer>();for(int i=0;i<metadata.sampleIds().size();i++)positions.put(metadata.sampleIds().get(i),i);
        var order=new LinkedHashMap<String,Integer>();var sourceOrder=new ArrayList<Integer>();int[] participantToScore=new int[requested.length];var priorRoles=new HashMap<String,String>();var priorUnits=new HashMap<String,String>();
        for(int j=0;j<requested.length;j++){
            int i=requested[j];String sample=table.rows().get(i)[table.column(text(g.get("iid_column")))];Integer at=positions.get(sample);if(at==null)throw new IllegalArgumentException("Active participant IID is absent from its variant-file source");
            if(order.containsKey(sample)){if(Boolean.TRUE.equals(map(map(StudyBioinformatics.hypothesis(c).get("analysis")).get("risk_score")).get("independent_rows"))||!priorRoles.get(sample).equals(roles[i])||!priorUnits.get(sample).equals(units[i]))throw new IllegalArgumentException("Repeated genotype identity requires the same partition and declared resampling unit");}
            else{order.put(sample,order.size());sourceOrder.add(at);priorRoles.put(sample,roles[i]);priorUnits.put(sample,units[i]);}participantToScore[j]=order.get(sample);
        }
        var accumulator=DiploidScores.accumulator(order.size(),g.get("missing").equals("reference_mean")?DiploidScores.MissingPolicy.MEAN_IMPUTE:DiploidScores.MissingPolicy.ERROR,new DiploidScores.Limits((int)limit(g,"maximum_samples"),model.size(),limit(g,"maximum_cells")));
        var matched=new java.util.HashSet<StudyGenotypeScores.Key>();var origins=new LinkedHashMap<String,Long>();long rows=0,assumed=0;
        org.jlinalg.pipeline.VariantBlockReader opened;try{opened=variants.open();}catch(RuntimeException invalid){throw decoderFailure(invalid);}
        try(var reader=opened){
            for(org.jlinalg.pipeline.VariantBlock block;(block=next(reader))!=null;)for(var v:block.variants()){
                if(++rows>inventory.variants())throw new IOException("Variant rows changed after admission scan");var key=StudyGenotypeScores.keyOrNull(v.chromosome(),String.valueOf(v.position()),v.referenceAllele(),v.alternateAllele());var w=model.get(key);if(w==null)continue;
                if(!matched.add(key))throw new IllegalArgumentException("Ambiguous duplicate model coordinate/alleles in variant source; complete-stream scanning found a repeated locus");
                var genotype=v.genotypeMetadata();if(genotype.splitMultiallelic())throw new IllegalArgumentException("A model member is a split multiallelic record; biallelic complementary dosage is not established");
                double[] all=v.dosages(),dosage=new double[order.size()];byte[] ploidies=genotype.observedPloidies();boolean flip=!w.ea().equals(v.alternateAllele());long rowAssumed=0;
                for(int j=0;j<dosage.length;j++){
                    int at=sourceOrder.get(j),ploidy=ploidies==null?0:ploidies[at];if(ploidy>0&&ploidy!=2)throw new IllegalArgumentException("Observed non-diploid model genotype requires an appropriate supplied-score workflow");
                    if(ploidy==0&&Double.isFinite(all[at])){if(!source.containsKey("diploid_dosage_declaration"))throw new IllegalArgumentException("Finite dosage with unknown ploidy requires source diploid_dosage_declaration");rowAssumed++;}
                    dosage[j]=flip?DiploidScores.otherAlleleDosage(all[at]):all[at];
                }
                assumed+=rowAssumed;accumulator.add(dosage,w.beta(),w.frequency());origins.merge(genotype.origin().name(),1L,Long::sum);
                audit.write(String.join("\t",sourceId,w.label(),w.key().chromosome(),String.valueOf(w.key().position()),w.ea(),w.oa(),v.id(),v.referenceAllele(),v.alternateAllele(),flip?"2-minus-ALT":"ALT",String.valueOf(w.beta()),String.valueOf(w.frequency()),format.name(),v.alternateAllele(),rowAssumed>0?"source_declared_diploid_dosage":"observed_diploid_or_missing")+"\n");
            }
        }
        if(rows!=inventory.variants())throw new IOException("Variant row count changed after admission scan");for(var w:model.values())coverage.write(String.join("\t",sourceId,w.label(),w.key().chromosome(),String.valueOf(w.key().position()),w.ea(),w.oa(),matched.contains(w.key())?"unique_coordinate_allele_match":"no_coordinate_allele_match")+"\n");coverage.flush();
        if(matched.size()!=model.size())throw new IllegalArgumentException("Complete frozen model is absent or allele-incompatible in a registered variant source; review genetic-model-coverage.tsv");
        var summary=new LinkedHashMap<String,Object>();summary.put("format",format.name());summary.put("source_variants",rows);summary.put("physical_records",inventory.records());summary.put("source_samples",inventory.samples());summary.put("model_variants_matched",model.size());summary.put("unique_genotyped_participants",order.size());summary.put("assumed_diploid_dosage_cells",assumed);summary.put("scored_variant_origins",origins);summary.put("preflight_decompressed_bytes",inventory.decompressedBytes());summary.put("maximum_record_bytes",inventory.maximumRecordBytes());summary.put("complete_source_exhausted",true);
        return new Read(inventory,accumulator,participantToScore,summary);
    }
    private static org.jlinalg.pipeline.VariantBlock next(org.jlinalg.pipeline.VariantBlockReader reader)throws IOException {try{return reader.read(1);}catch(RuntimeException invalid){throw decoderFailure(invalid);}}
    private static IOException decoderFailure(RuntimeException invalid){return new IOException("Variant decoder could not complete the registered source ("+invalid.getClass().getSimpleName()+"): "+invalid.getMessage(),invalid);}
    private static long limit(Map<String,Object> g,String key){return ((Number)g.get(key)).longValue();}
}
