/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.pipeline;
import java.io.IOException;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Streams one human contrast, with explicit dosage conversion and a caller-owned audit sink. */
public final class HumanPloidyVariantSource implements VariantSource {
    public record Audit(String variant,String chromosome,long position,String region,String disposition,String sourcePloidyStatus,int called,int missing,int invalid,double alternateCopies,double calledAlleles) { }
    public record InvalidCall(String variant,String sampleId,int observedPloidy,double sourceDosage) { }
    private final VariantSource source;private final HumanPloidyProfile profile;private final boolean hardCalls;private final Consumer<Audit> audit;
    private final Consumer<InvalidCall> invalidAudit;
    public HumanPloidyVariantSource(VariantSource source,HumanPloidyProfile profile,boolean hardCalls,Consumer<Audit> audit){this(source,profile,hardCalls,audit,ignored->{});}
    public HumanPloidyVariantSource(VariantSource source,HumanPloidyProfile profile,boolean hardCalls,Consumer<Audit> audit,Consumer<InvalidCall> invalidAudit){this.source=java.util.Objects.requireNonNull(source);this.profile=java.util.Objects.requireNonNull(profile);this.hardCalls=hardCalls;this.audit=java.util.Objects.requireNonNull(audit);this.invalidAudit=java.util.Objects.requireNonNull(invalidAudit);}
    @Override public VariantSourceMetadata metadata(){return new VariantSourceMetadata(source.metadata().path(),-1,source.metadata().sampleIds(),source.metadata().format());}
    @Override public VariantBlockReader open(int[] order)throws IOException {
        var reader=source.open(order);
        return new VariantBlockReader(){
            public VariantBlock read(int maximum)throws IOException {
                for(VariantBlock block;(block=reader.read(maximum))!=null;) {
                    var kept=new ArrayList<VariantRecord>();
                    for(var raw:block.variants()) {
                        if(raw.id().chars().anyMatch(Character::isISOControl)||raw.chromosome().chars().anyMatch(Character::isISOControl))throw new IOException("Control character in human-ploidy variant metadata");
                        var region=HumanGenomeRegions.classify(profile.genomeBuild(),raw.chromosome(),raw.position());
                        if(!profile.includes(region)){audit.accept(new Audit(raw.id(),raw.chromosome(),raw.position(),region.name(),"outside_registered_region",raw.genotypeMetadata().ploidyStatus(),0,0,0,Double.NaN,0));continue;}
                        var values=raw.dosages();var old=raw.genotypeMetadata();byte[] ploidies=new byte[values.length];java.util.Arrays.fill(ploidies,(byte)(profile.haploid()?1:2));int called=0,missing=0,invalid=0;double ac=0;
                        var observed=old.observedPloidies();
                        for(int i=0;i<values.length;i++) {
                            if(Double.isNaN(values[i])){missing++;continue;}
                            int p=observed==null?0:observed[i];boolean bad=!Double.isFinite(values[i]);double scale=1;
                            if(profile.haploid()) {
                                if(profile.haploidSourceScale().equals("observed"))bad|=p!=1;
                                else if(profile.haploidSourceScale().equals("haploid"))bad|=p!=0&&p!=1;
                                else {bad|=p!=0&&p!=2;scale=2;}
                            }else bad|=p!=0&&p!=2;
                            double value=values[i]/scale;bad|=value< -1e-10||value>(profile.haploid()?1:2)+1e-10;
                            if(hardCalls||old.origin()==VariantGenotypeMetadata.Origin.HARD_CALL)bad|=Math.abs(value-Math.rint(value))>1e-8;
                            if(bad){invalidAudit.accept(new InvalidCall(raw.id(),source.metadata().sampleIds().get(order==null?i:order[i]),p,values[i]));invalid++;missing++;values[i]=Double.NaN;}else{value=Math.max(0,Math.min(profile.haploid()?1:2,value));if(hardCalls||old.origin()==VariantGenotypeMetadata.Origin.HARD_CALL)value=Math.rint(value);values[i]=value;ac+=value;called++;}
                        }
                        audit.accept(new Audit(raw.id(),raw.chromosome(),raw.position(),region.name(),invalid>0?profile.invalidCalls().equals("error")?"invalid_ploidy_error":"invalid_calls_set_missing":"normalized",old.ploidyStatus(),called,missing,invalid,ac,(profile.haploid()?1.:2.)*called));
                        if(invalid>0&&profile.invalidCalls().equals("error"))throw new IOException("Invalid source ploidy/dosage in registered human contrast at "+raw.id()+" ("+invalid+" calls); inspect the local ploidy audit");
                        String chromosome=raw.chromosome().equalsIgnoreCase("PAR1")||raw.chromosome().equalsIgnoreCase("PAR2")?"X":raw.chromosome();
                        kept.add(new VariantRecord(raw.id(),chromosome,raw.position(),raw.referenceAllele(),raw.alternateAllele(),values,raw.imputationQuality(),new VariantGenotypeMetadata(old.origin(),old.splitMultiallelic(),ploidies,true)));
                    }
                    if(!kept.isEmpty())return new VariantBlock(block.firstSourceIndex(),kept);
                }return null;
            }
            public void close()throws IOException{reader.close();}
        };
    }
}
