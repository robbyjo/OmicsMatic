/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
import java.util.zip.GZIPInputStream;
import org.jlinalg.genetics.DiploidScores;
import org.jlinalg.genetics.PlinkBedDataset;
import org.jlinalg.study.StudyOperationExecutor.Context;

/** Bounded complete-model genotype application, followed by the common held-out evaluator. */
final class StudyGenotypeScores {
    static final String CAVEAT="Frozen additive human diploid autosomal score sums, with exact declared-build coordinate/allele matching. Build and forward-strand declarations are not reference-FASTA validation. No strand inference, liftover, target-frequency estimation, MAC filtering, LD fitting or target calibration was performed. VCF/BCF prefer FORMAT/DS then called GT; finite dosage without observed ploidy requires a source diploidy declaration. Split multiallelic or observed non-diploid model genotypes are held. External source independence, ancestry transportability and clinical validity require separate evidence.";
    record Applied(double[] score,double[] prediction,double[] baseline,Map<String,Object> summary) { }
    record Key(String chromosome,long position,String a,String b) { }
    record Weight(String label,Key key,String ea,String oa,double beta,double frequency) { }
    private record Identity(String fid,String iid) { }
    private StudyGenotypeScores() { }

    static Applied apply(Context c,Map<String,Object> p,DelimitedData table,String[] ids,String[] roles,String[] units,boolean relatedOnly)throws IOException {
        var g=map(p.get("genotypes"));if(!p.containsKey("weights"))throw new IllegalArgumentException("Register frozen weights for genotype scoring");
        Map<String,String> before=StudyRiskScoreResources.inputHashes(c.project(),c.study(),c.operation());
        Path weights=c.project().resolve(text(p.get("weights")));if(!Files.isRegularFile(weights))throw new IllegalArgumentException("Registered genetic weights are missing");
        long weightBytes=limit(g,"maximum_weights_mib")*1048576L;if(Files.size(weights)>weightBytes)throw new IllegalArgumentException("Genetic weights exceed maximum_weights_mib");
        Path copy=c.outputDirectory().resolve(weights.toString().endsWith(".gz")?"genetic-weights.raw.gz":"genetic-weights.raw");Files.copy(weights,copy);
        if(!FollowupSupport.hash(weights).equals(FollowupSupport.hash(copy)))throw new IOException("Genetic weights changed during snapshot");
        var model=weights(copy,g,weights.getFileName().toString(),weightBytes);
        double[] score=new double[ids.length],prediction=new double[ids.length],baseline=new double[ids.length];Arrays.fill(score,Double.NaN);Arrays.fill(prediction,Double.NaN);Arrays.fill(baseline,Double.NaN);
        String link=text(g.get("prediction_link"));boolean binary="binary".equals(p.get("family")),probability="probability".equals(p.get("prediction_scale"));
        if(!relatedOnly&&((!binary&&!link.equals("identity"))||(binary&&probability&&!link.equals("logit"))||(binary&&!probability&&!link.equals("none"))))throw new IllegalArgumentException("Register identity prediction for quantitative evaluation, logit for binary probabilities, or none for binary raw-score discrimination");
        if(g.containsKey("prediction_linear_column")){
            String column=text(g.get("prediction_linear_column"));var forbidden=new HashSet<String>(List.of(text(p.get("outcome")),text(p.get("id_column")),text(p.get("partition_column"))));for(Object raw:list(p.get("related_phenotypes")))forbidden.add(text(map(raw).get("column")));forbidden.addAll(org.jlinalg.study.StudyRiskScorePolicy.list(p,"group_columns"));for(String key:List.of("source_column","fid_column","iid_column"))if(g.containsKey(key))forbidden.add(text(g.get(key)));if(forbidden.contains(column))throw new IllegalArgumentException("Frozen prediction linear component cannot be an outcome or identity/partition/group column");table.column(column);
        }
        var sources=list(g.get("sources"));var registered=new HashSet<String>();for(Object raw:sources)registered.add(text(map(raw).get("id")));
        String single=sources.size()==1?text(map(sources.get(0)).get("id")):"";String[] sourceOf=new String[ids.length];
        for(int i=0;i<ids.length;i++)if(!roles[i].equals("excluded")){sourceOf[i]=g.containsKey("source_column")?table.rows().get(i)[table.column(text(g.get("source_column")))]:single;if(!registered.contains(sourceOf[i]))throw new IllegalArgumentException("Participant references an unregistered genotype source");}
        long cells=0,totalVariants=0;var sourceReports=new ArrayList<Object>();var participantRows=new ArrayList<String[]>();
        Path alignment=c.outputDirectory().resolve("genetic-variant-alignment.tsv");
        try(var audit=Files.newBufferedWriter(alignment,StandardCharsets.UTF_8);var coverage=Files.newBufferedWriter(c.outputDirectory().resolve("genetic-model-coverage.tsv"),StandardCharsets.UTF_8)){
            audit.write("source\tweight_variant\tchromosome\tposition\tea\toa\tsource_variant\tsource_a1\tsource_a2\torientation\tbeta\tea_frequency\tsource_format\tcounted_allele\tploidy_status\n");
            coverage.write("source\tweight_variant\tchromosome\tposition\tea\toa\tstatus\n");
            for(Object raw:sources){
                var source=map(raw);String sourceId=text(source.get("id"));
                int[] requested=java.util.stream.IntStream.range(0,ids.length).filter(i->sourceId.equals(sourceOf[i])).toArray();if(requested.length==0)throw new IllegalArgumentException("A registered genotype source has no active participant metadata");
                if(Arrays.stream(requested).noneMatch(i->roles[i].equals("evaluation")))throw new IllegalArgumentException("Each registered genotype source must contribute declared evaluation records");
                if(source.containsKey("file")){
                    var streamed=StudyVariantGenotypeScores.read(c,g,source,model,table,roles,units,requested,limit(g,"maximum_source_variants")-totalVariants,limit(g,"maximum_cells")-cells,audit,coverage);
                    totalVariants+=Math.max(streamed.inventory().records(),streamed.inventory().variants());cells+=streamed.inventory().variants()*streamed.inventory().samples();
                    var application=applyRecords(p,g,table,ids,roles,units,relatedOnly,requested,streamed.participantToScore(),streamed.accumulator(),model.size(),score,prediction,baseline,participantRows,sourceId);
                    var report=new LinkedHashMap<String,Object>(streamed.summary());report.put("source",sourceId);report.put("active_records",requested.length);report.put("complete_evaluation_records",application.accepted());report.put("imputed_record_variant_cells",application.imputed());sourceReports.add(report);continue;
                }
                Path prefix=c.project().resolve(text(source.get("bfile")));for(String ext:List.of("bed","bim","fam"))if(Files.isRegularFile(Path.of(prefix+"."+ext))&&Files.size(Path.of(prefix+"."+ext))>limit(g,"maximum_source_mib")*1048576L)throw new IllegalArgumentException("BED source exceeds maximum_source_mib");
                var selected=new LinkedHashMap<String,Weight>();var seenKeys=new HashSet<Key>();long variants=0;
                Path bim=Path.of(prefix+".bim"),fam=Path.of(prefix+".fam");long metadataLimit=limit(g,"maximum_metadata_mib")*1048576L;
                if(!Files.isRegularFile(bim)||!Files.isRegularFile(fam)||Files.size(bim)+Files.size(fam)>metadataLimit)throw new IllegalArgumentException("BED metadata are missing or exceed maximum_metadata_mib");
                try(var lines=new Lines(bim,limit(g,"maximum_line_bytes"),metadataLimit,false)){
                    for(String line;(line=lines.next())!=null;){if(line.isBlank())continue;if(++variants+totalVariants>limit(g,"maximum_source_variants"))throw new IllegalArgumentException("Complete source scan exceeds maximum_source_variants");String[] fields=line.strip().split("[ \\t]+");if(fields.length!=6)throw new IllegalArgumentException("BIM must have six fields");
                        Key key=keyOrNull(fields[0],fields[3],fields[4],fields[5]);Weight w=model.get(key);if(w==null)continue;
                        if(!seenKeys.add(key)||selected.putIfAbsent(fields[1],w)!=null)throw new IllegalArgumentException("Ambiguous duplicate model coordinate/alleles or source variant ID");
                    }
                }
                totalVariants+=variants;for(var w:model.values())coverage.write(String.join("\t",sourceId,w.label,w.key.chromosome,String.valueOf(w.key.position),w.ea,w.oa,seenKeys.contains(w.key)?"unique_coordinate_allele_match":"no_coordinate_allele_match")+"\n");coverage.flush();
                if(selected.size()!=model.size())throw new IllegalArgumentException("Complete frozen model is absent or allele-incompatible in a registered source; partial scores are withheld; review genetic-model-coverage.tsv");
                var limits=new PlinkBedDataset.Limits((int)limit(g,"maximum_samples"),limit(g,"maximum_source_variants"),model.size(),metadataLimit,limit(g,"maximum_retained_mib")*1048576L,(int)limit(g,"maximum_line_bytes"));
                try(var bed=PlinkBedDataset.open(prefix,selected.keySet(),limits)){
                    long sourceCells=Math.multiplyExact((long)model.size(),bed.samples().size());cells=Math.addExact(cells,sourceCells);if(cells>limit(g,"maximum_cells"))throw new IllegalArgumentException("Complete genotype scoring exceeds shared maximum_cells");
                    var positions=new HashMap<Identity,Integer>();for(int i=0;i<bed.samples().size();i++){var sample=bed.samples().get(i);positions.put(new Identity(sample.familyId(),sample.individualId()),i);}
                    var order=new LinkedHashMap<Identity,Integer>();int[] participantToScore=new int[requested.length];var priorRoles=new HashMap<Identity,String>();var priorUnits=new HashMap<Identity,String>();var sourceOrder=new ArrayList<Integer>();
                    for(int j=0;j<requested.length;j++){int i=requested[j];Identity identity=new Identity(table.rows().get(i)[table.column(text(g.get("fid_column")))],table.rows().get(i)[table.column(text(g.get("iid_column")))]);Integer at=positions.get(identity);if(at==null)throw new IllegalArgumentException("Active participant FID/IID is absent from its genotype source");
                        if(order.containsKey(identity)){if(Boolean.TRUE.equals(p.get("independent_rows"))||!priorRoles.get(identity).equals(roles[i])||!priorUnits.get(identity).equals(units[i]))throw new IllegalArgumentException("Repeated genotype identity requires the same partition and declared resampling unit");}
                        else{order.put(identity,order.size());sourceOrder.add(at);priorRoles.put(identity,roles[i]);priorUnits.put(identity,units[i]);}participantToScore[j]=order.get(identity);
                    }
                    var accumulator=DiploidScores.accumulator(order.size(),g.get("missing").equals("reference_mean")?DiploidScores.MissingPolicy.MEAN_IMPUTE:DiploidScores.MissingPolicy.ERROR,new DiploidScores.Limits((int)limit(g,"maximum_samples"),model.size(),limit(g,"maximum_cells")));
                    for(var v:bed.selectedVariants()){
                        Weight w=selected.get(v.id());if(!w.key.equals(keyOrNull(v.chromosome(),String.valueOf(v.position()),v.allele1(),v.allele2())))throw new IllegalArgumentException("Selected BIM metadata changed between scan and scoring");
                        double[] all=bed.readAllele1Dosages(v),dosage=new double[order.size()];boolean flip=!w.ea.equals(v.allele1());for(int j=0;j<dosage.length;j++)dosage[j]=flip?DiploidScores.otherAlleleDosage(all[sourceOrder.get(j)]):all[sourceOrder.get(j)];accumulator.add(dosage,w.beta,w.frequency);
                        audit.write(String.join("\t",sourceId,w.label,w.key.chromosome,String.valueOf(w.key.position),w.ea,w.oa,v.id(),v.allele1(),v.allele2(),flip?"2-minus-A1":"A1",String.valueOf(w.beta),String.valueOf(w.frequency),"BED",v.allele1(),"format_diploid_hard_call")+"\n");
                    }
                    var application=applyRecords(p,g,table,ids,roles,units,relatedOnly,requested,participantToScore,accumulator,model.size(),score,prediction,baseline,participantRows,sourceId);
                    sourceReports.add(Map.of("source",sourceId,"format","BED","source_variants",variants,"source_samples",bed.samples().size(),"model_variants_matched",model.size(),"active_records",requested.length,"unique_genotyped_participants",order.size(),"complete_evaluation_records",application.accepted(),"imputed_record_variant_cells",application.imputed()));
                }
            }
        }
        if(!before.equals(StudyRiskScoreResources.inputHashes(c.project(),c.study(),c.operation())))throw new IOException("Frozen score inputs changed during application");
        FollowupSupport.table(c.outputDirectory().resolve("genetic-participant-disposition.tsv"),List.of("sample_id","source","model_variants","observed_variants","imputed_variants","observed_fraction","dosage_status","eligibility"),participantRows);
        var report=new LinkedHashMap<String,Object>();report.put("model_variants",model.size());report.put("genome_build",g.get("weights_genome_build"));report.put("coefficient_scale",g.get("coefficient_scale"));report.put("missing",g.get("missing"));report.put("allele_alignment",g.get("allele_alignment"));report.put("reference_frequency_status",g.get("missing").equals("reference_mean")?"user_supplied_external_frequencies_not_independently_certified":"not_used");report.put("prediction_link",link);report.put("score_center",g.get("score_center"));report.put("score_scale",g.get("score_scale"));report.put("minimum_observed_fraction",g.get("minimum_observed_fraction"));report.put("sources",sourceReports);report.put("source_formats",sourceReports.stream().map(StudyPublicEvidence::map).map(r->r.get("format")).distinct().sorted(java.util.Comparator.comparing(String::valueOf)).toList());report.put("assumed_diploid_dosage_cells",sourceReports.stream().map(StudyPublicEvidence::map).mapToLong(r->((Number)r.getOrDefault("assumed_diploid_dosage_cells",0)).longValue()).sum());report.put("decoded_genotype_cells",cells);report.put("source_variants_scanned",totalVariants);report.put("model_coverage","complete_in_every_registered_source");report.put("input_sha256",before);report.put("required_manuscript_caveat",CAVEAT);
        write(c.outputDirectory().resolve("genetic-score-application.yaml"),report);return new Applied(score,prediction,baseline,report);
    }


    record SampleApplication(int accepted,long imputed) { }
    static SampleApplication applyRecords(Map<String,Object> p,Map<String,Object> g,DelimitedData table,String[] ids,String[] roles,String[] units,boolean relatedOnly,int[] requested,int[] participantToScore,org.jlinalg.genetics.DiploidScores.Accumulator accumulator,int modelVariants,double[] score,double[] prediction,double[] baseline,java.util.List<String[]> participantRows,String sourceId) {
        String link=text(g.get("prediction_link"));boolean binary="binary".equals(p.get("family")),probability="probability".equals(p.get("prediction_scale"));
        int accepted=0;long imputed=0;for(int j=0;j<requested.length;j++){
            int i=requested[j];var value=accumulator.score(participantToScore[j]);double fraction=value.observedVariants()/(double)modelVariants;boolean eligible=fraction>=number(g,"minimum_observed_fraction");
            participantRows.add(new String[]{ids[i],sourceId,String.valueOf(value.inputVariants()),String.valueOf(value.observedVariants()),String.valueOf(value.imputedVariants()),String.valueOf(fraction),value.status().name(),eligible?"eligible":"below_minimum_observed_fraction"});
            imputed+=value.imputedVariants();if(!eligible)continue;score[i]=(value.sum()-number(g,"score_center"))/number(g,"score_scale");if(!Double.isFinite(score[i]))throw new IllegalArgumentException("Nonfinite frozen genetic score normalization");
            if(!link.equals("none")){double linear=score[i]+number(g,"intercept")+(g.containsKey("prediction_linear_column")?StudyRiskScores.number(table,table.rows().get(i),text(g.get("prediction_linear_column"))):0);if(Double.isInfinite(linear))throw new IllegalArgumentException("Nonfinite frozen prediction linear predictor");prediction[i]=link.equals("identity")?linear:linear>=0?1/(1+Math.exp(-linear)):Math.exp(linear)/(1+Math.exp(linear));}
            baseline[i]=p.containsKey("baseline_column")?StudyRiskScores.number(table,table.rows().get(i),text(p.get("baseline_column"))):Double.NaN;
            boolean completeOutcome=relatedOnly||Double.isFinite(StudyRiskScores.number(table,table.rows().get(i),text(p.get("outcome"))));
            boolean completePrediction=relatedOnly||binary&&!probability||Double.isFinite(prediction[i])&&Double.isFinite(baseline[i]);
            if(roles[i].equals("evaluation")&&completeOutcome&&completePrediction)accepted++;
        }
        if(accepted==0)throw new IllegalArgumentException("A registered source has no eligible complete evaluation records; do not silently drop a cohort");

        return new SampleApplication(accepted,imputed);
    }

    private static LinkedHashMap<Key,Weight> weights(Path path,Map<String,Object> g,String original,long maximumBytes)throws IOException {
        var result=new LinkedHashMap<Key,Weight>();var columns=map(g.get("weight_columns"));long retained=0;char delimiter=original.endsWith(".csv")||original.endsWith(".csv.gz")?',':'\t';
        try(var lines=new Lines(path,limit(g,"maximum_line_bytes"),maximumBytes,original.endsWith(".gz"))){
            String first;do{first=lines.next();}while(first!=null&&(first.startsWith("#")||first.isBlank()));if(first==null)throw new IllegalArgumentException("Frozen genetic weight file is empty");
            List<String> header=DelimitedData.parse(first,delimiter,1,path);if(header.stream().anyMatch(String::isBlank)||new HashSet<>(header).size()!=header.size())throw new IllegalArgumentException("Genetic weight headers must be unique and nonblank");
            var at=new HashMap<String,Integer>();for(String role:List.of("variant","chromosome","position","ea","oa","weight","ea_frequency","genome_build")){String physical=String.valueOf(columns.getOrDefault(role,role));int index=header.indexOf(physical);if(columns.containsKey(role)&&index<0)throw new IllegalArgumentException("A mapped genetic weight column is absent");at.put(role,index);}
            for(String role:List.of("chromosome","position","ea","oa","weight"))if(at.get(role)<0)throw new IllegalArgumentException("Frozen genetic weights require genomic coordinates, both alleles and weight");
            if(at.values().stream().filter(i->i>=0).distinct().count()!=at.values().stream().filter(i->i>=0).count())throw new IllegalArgumentException("Logical genetic weight columns must map to distinct physical columns");
            for(String line;(line=lines.next())!=null;){if(line.isBlank()||line.startsWith("#"))continue;if(result.size()>=limit(g,"maximum_model_variants"))throw new IllegalArgumentException("Frozen score exceeds maximum_model_variants");var f=DelimitedData.parse(line,delimiter,1,path);if(f.size()!=header.size())throw new IllegalArgumentException("Genetic weight row width differs from header");
                for(String flag:List.of("is_haplotype","is_diplotype","is_interaction","is_dominant","is_recessive")){int column=header.indexOf(flag);if(column>=0&&!Set.of("","false","0","na",".").contains(f.get(column).toLowerCase(java.util.Locale.ROOT)))throw new IllegalArgumentException("Nonadditive or unresolved scoring flags require an appropriate supplied score workflow");}
                for(String flag:List.of("dosage_0_weight","dosage_1_weight","dosage_2_weight","inclusion_criteria")){int column=header.indexOf(flag);if(column>=0&&!Set.of("","na",".").contains(f.get(column).toLowerCase(java.util.Locale.ROOT)))throw new IllegalArgumentException("Dosage-specific or conditional scoring requires an appropriate supplied score workflow");}
                String ea=f.get(at.get("ea")),oa=f.get(at.get("oa"));Key key=keyOrNull(f.get(at.get("chromosome")),f.get(at.get("position")),ea,oa);if(key==null)throw new IllegalArgumentException("Genetic weights require positive human autosomal coordinates and resolved distinct forward sequence alleles");
                if(g.get("allele_alignment").equals("reject_palindromic")&&((ea.equals("A")&&oa.equals("T"))||(ea.equals("T")&&oa.equals("A"))||(ea.equals("C")&&oa.equals("G"))||(ea.equals("G")&&oa.equals("C"))))throw new IllegalArgumentException("Palindromic model variant requires a documented forward-strand declaration; no model variants are silently dropped");
                if(at.get("genome_build")>=0&&!f.get(at.get("genome_build")).equals(g.get("weights_genome_build")))throw new IllegalArgumentException("A model row genome build differs from the declared weight build");
                double beta=Double.parseDouble(f.get(at.get("weight")));if(!Double.isFinite(beta))throw new IllegalArgumentException("Nonfinite genetic score weight");if(Set.of("odds_ratio","hazard_ratio").contains(g.get("coefficient_scale"))){if(!(beta>0))throw new IllegalArgumentException("Ratio weights must be positive");beta=Math.log(beta);}
                double frequency=at.get("ea_frequency")<0?Double.NaN:Double.parseDouble(f.get(at.get("ea_frequency")));if((Double.isNaN(frequency)&&g.get("missing").equals("reference_mean"))||!Double.isNaN(frequency)&&(!Double.isFinite(frequency)||frequency<0||frequency>1))throw new IllegalArgumentException("Reference-mean scoring requires external effect-allele frequencies in [0,1]");
                String label=at.get("variant")>=0?f.get(at.get("variant")):key.chromosome+":"+key.position+":"+ea+":"+oa;if(label.isBlank()||label.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid model variant label");retained+=512L+4L*line.length();if(retained>limit(g,"maximum_retained_mib")*1048576L)throw new IllegalArgumentException("Genetic model exceeds estimated retained metadata bound");
                if(result.putIfAbsent(key,new Weight(label,key,ea,oa,beta,frequency))!=null)throw new IllegalArgumentException("Duplicate genomic coordinate/allele key in frozen genetic model");
            }
        }
        if(result.isEmpty())throw new IllegalArgumentException("Frozen genetic model has no variants");return result;
    }
    static Key keyOrNull(String chr,String position,String a,String b){
        if(chr.startsWith("chr"))chr=chr.substring(3);if(!chr.matches("[1-9]|1[0-9]|2[0-2]")||!a.matches("[ACGT]+")||!b.matches("[ACGT]+")||a.equals(b))return null;
        long pos;try{pos=Long.parseLong(position);}catch(NumberFormatException invalid){return null;}if(pos<=0)return null;return a.compareTo(b)<0?new Key(chr,pos,a,b):new Key(chr,pos,b,a);
    }
    private static long limit(Map<String,Object> p,String key){return ((Number)p.get(key)).longValue();}
    private static double number(Map<String,Object> p,String key){return ((Number)p.get(key)).doubleValue();}
    /** Bounds decompressed input and each line before retaining unbounded text. */
    static final class Lines implements AutoCloseable {
        private final BufferedReader reader;private final long maximumBytes,maximumLine;private long bytes;
        Lines(Path path,long maximumLine,long maximumBytes,boolean gzip)throws IOException {var input=Files.newInputStream(path);try{reader=new BufferedReader(new InputStreamReader(gzip?new GZIPInputStream(input):input,StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)));}catch(IOException|RuntimeException invalid){input.close();throw invalid;}this.maximumLine=maximumLine;this.maximumBytes=maximumBytes;}
        String next()throws IOException {var line=new StringBuilder();boolean any=false;for(int ch;(ch=reader.read())!=-1;){any=true;if(++bytes>maximumBytes)throw new IllegalArgumentException("Decompressed metadata exceeds byte bound");if(ch=='\n')break;if(line.length()>=maximumLine)throw new IllegalArgumentException("Genetic metadata line exceeds maximum_line_bytes");line.append((char)ch);}if(!any)return null;String value=line.toString();if(value.endsWith("\r"))value=value.substring(0,value.length()-1);long extra=value.getBytes(StandardCharsets.UTF_8).length-value.length();bytes+=extra;if(bytes>maximumBytes||value.getBytes(StandardCharsets.UTF_8).length>maximumLine)throw new IllegalArgumentException("Genetic metadata byte/line bound exceeded");return value;}
        @Override public void close()throws IOException {reader.close();}
    }
}
