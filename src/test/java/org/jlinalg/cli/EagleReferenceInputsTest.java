/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jlinalg.genetics.EagleGeneticMap;
import org.jlinalg.genetics.EagleGenotypes;
import org.jlinalg.genetics.EagleReferenceJoin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class EagleReferenceInputsTest {
    @TempDir Path root;
    private static final String META="##fileformat=VCFv4.2\n##contig=<ID=22>\n##contig=<ID=X>\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"GT\">\n";
    private static final String FIXED="#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO";
    private static String header(boolean ref){return META+FIXED+"\tFORMAT\t"+(ref?"R1\tR2":"A\tβ")+"\n";}
    private static String row(String chr,String pos,String id,String ref,String alt,String a,String b){return chr+"\t"+pos+"\t"+id+"\t"+ref+"\t"+alt+"\t1.00\tPASS\tAN=4\tGT\t"+a+"\t"+b+"\n";}
    private static String row(int pos,String a,String b){return row("22",Integer.toString(pos),"v"+pos,"A","C",a,b);}
    private static String normal(){return row(100,"0|1","1|0")+row(200,"0|1","1|1");}
    private Path text(String name,String value)throws IOException{Path p=root.resolve(name);Files.writeString(p,value);return p;}
    private Path map()throws IOException{return text("map.txt",EagleGeneticMap.HEADER+"\n22 0 1 0\n22 1000 1 1\n23 0 1 0\n23 1000 1 1\n");}
    private static final class Config {
        boolean swap,x;String contig="22";long retained=4000000,ledger=1000000,workspace=20000000;int records=100;
        EagleReferenceInputs.Unmatched unmatched=EagleReferenceInputs.Unmatched.DROP_WITH_WARNING;
        EagleReferenceInputs.Expansion expansion=EagleReferenceInputs.Expansion.REFUSE;
        EagleReferenceInputs.ReferenceFallback fallback=EagleReferenceInputs.ReferenceFallback.REFUSE;
        EagleReferenceInputs.SampleOverlap overlap=EagleReferenceInputs.SampleOverlap.REFUSE;
        EagleCohortInputs.PartialMissing partial=EagleCohortInputs.PartialMissing.REFUSE;
        EagleGenotypes.Missing missing=EagleGenotypes.Missing.IMPUTE;
        EagleReferenceInputs.Spec build(){
            var v=new EagleVcf.Limits(new MinimacVcf.Limits(100,10000,1000000,4000000,8000000,8192,4000000,1000),100,10000,10,10,10000);
            return new EagleReferenceInputs.Spec("synthetic-no-biological-reference","reviewed synthetic protocol only",contig,x?23:22,23,1,1000,
                    new EagleGenotypes.Options(EagleGenotypes.Mode.REFERENCE,x,missing,false),swap,unmatched,expansion,fallback,overlap,partial,
                    EagleGeneticMap.Outside.REQUIRE_BRACKET,new EagleGeneticMap.Limits(1000,1000,1024,1000000,100),v,new EagleReferenceJoin.Limits(records,10000,1000000),retained,ledger,workspace);
        }
    }
    private EagleReferenceInputs.Prepared prepare(String targetRows,String refRows,Config config,String name)throws IOException {
        Path target=text(name+"-target.vcf",header(false)+targetRows),reference=text(name+"-ref.vcf",header(true)+refRows);
        return EagleReferenceInputs.prepare(target,reference,null,map(),root.resolve(name),config.build(),()->{});
    }
    private Path output(String name,String rows)throws IOException{return text(name,header(false)+rows);}

    @Test void completeSnapshotsAndOriginalOrderStagesPreservePositionGtAndMetadata()throws Exception {
        String rows=row("22","000100","v100","A","C","00/01","1|0")+row(200,"0/1","1|1")+row("X","100","outside","A","C","1","0|1");
        var p=prepare(rows,normal(),new Config(),"δ work");assertEquals(3,p.inputs().get(EagleReferenceInputs.Role.TARGET).variants());assertEquals(2,p.stages().get(EagleReferenceInputs.Role.TARGET).variants());
        try(var reader=EagleVcf.open(p.target(),p.spec().vcfLimits(),()->{})){var r=reader.next();assertEquals("000100",r.positionText());assertEquals("00/01",r.calls().get(0).raw());assertEquals("1.00",r.quality());assertEquals("AN=4",r.info());}
        assertEquals(header(false)+rows,Files.readString(p.snapshots().stream().filter(s->s.copy().getFileName().toString().equals("target.vcf-source")).findFirst().orElseThrow().copy()));
        var audit=EagleReferenceInputs.audit(p,output("result.vcf",normal()),()->{});assertEquals(4,audit.calls());assertTrue(Files.size(p.target().resolveSibling("target.vcf.gz.csi"))>0);assertTrue(Files.readString(p.work().resolve("INPUT_WARNINGS.md")).contains("Required action"));
    }
    @Test void sourceOrderedPlanAuditsOutputWithoutReorderingStagedTargets()throws Exception {
        Config c=new Config();c.unmatched=EagleReferenceInputs.Unmatched.KEEP_UNPHASED;
        String target=row("22","100","g","A","G","0|1","1|1")+row("22","100","c","A","C","0|1","1|0")+row(200,"0|1","1|1");
        String ref=row("22","100","c","A","C","0|1","1|0")+row(200,"0|1","1|1");var p=prepare(target,ref,c,"work");
        try(var reader=EagleVcf.open(p.target(),p.spec().vcfLimits(),()->{})){assertEquals("g",reader.next().id());assertEquals("c",reader.next().id());}
        var lines=Files.readAllLines(p.outputPlan());assertTrue(lines.get(1).startsWith("2\t2\t22\t100\tPHASED"));assertTrue(lines.get(2).startsWith("1\t1\t22\t100\tUNPHASED"));
        String result=row("22","100","c","A","C","1|0","0|1")+row("22","100","g","A","G","0/1","1/1")+row(200,"0|1","1|1");assertEquals(6,EagleReferenceInputs.audit(p,output("result.vcf",result),()->{}).calls());
    }
    @Test void exactModeDropsUnpairedMonomorphicTargetWithoutExpandingIt()throws Exception {
        String mono=row("22","50","mono","A",".","0|0","0|0");String ref=row("22","50","mono","A","C","0|1","1|0");
        var p=prepare(mono+normal(),ref+normal(),new Config(),"work");assertEquals(1,p.summary().droppedTargetRows());assertEquals(0,p.summary().expandedOutputRows());assertEquals(2,EagleReferenceInputs.audit(p,output("result.vcf",normal()),()->{}).rows());
    }
    @Test void monomorphicExpansionNeedsExplicitPolicyAndRetainsOriginalSourceGt()throws Exception {
        Config c=new Config();c.swap=true;String mono=row("22","50","mono","A",".","0/0","0|0"),ref=row("22","50","mono","A","C","0|1","1|0");
        assertTrue(assertThrows(IOException.class,()->prepare(mono+normal(),ref+normal(),c,"refused")).getMessage().contains("expansion"));c.expansion=EagleReferenceInputs.Expansion.ALLOW_ALT_EXPANSION;
        var p=prepare(mono+normal(),ref+normal(),c,"allowed");assertEquals(1,p.summary().expandedOutputRows());assertEquals(0,p.summary().changedTargetReferenceRows());
        assertEquals(3,EagleReferenceInputs.audit(p,output("result.vcf",row("22","50","mono","A","C","0|0","0|0")+normal()),()->{}).rows());
    }
    @Test void replacingOriginalTargetReferenceRequiresSeparateExplicitPolicy()throws Exception {
        Config c=new Config();c.swap=true;c.expansion=EagleReferenceInputs.Expansion.ALLOW_ALT_EXPANSION;
        String mono=row("22","50","mono","A",".","0/0","0|0"),ref=row("22","50","mono","G","C","0|1","1|0");
        assertTrue(assertThrows(IOException.class,()->prepare(mono+normal(),ref+normal(),c,"refused")).getMessage().contains("replace target REF"));
        c.expansion=EagleReferenceInputs.Expansion.ALLOW_SOURCE_REF_REPLACEMENT;var p=prepare(mono+normal(),ref+normal(),c,"allowed");assertEquals(1,p.summary().changedTargetReferenceRows());
        assertEquals(3,EagleReferenceInputs.audit(p,output("result.vcf",row("22","50","mono","G","C","0|0","0|0")+normal()),()->{}).rows());assertTrue(p.warnings().stream().anyMatch(w->w.contains("whose REF changes")));
    }
    @Test void retainedUnphasedMonomorphicTargetIncludesOriginalPreRejectionExpansion()throws Exception {
        Config c=new Config();c.swap=true;c.expansion=EagleReferenceInputs.Expansion.ALLOW_ALT_EXPANSION;c.unmatched=EagleReferenceInputs.Unmatched.KEEP_UNPHASED;
        String mono=row("22","50","mono","A",".","0|0","0|0"),ref=row("22","50","mono","A","C,G","2|0","1|0");
        var p=prepare(mono+normal(),ref+normal(),c,"work");assertEquals(1,p.summary().unphasedRows());assertEquals(1,p.summary().expandedOutputRows());
        assertEquals(3,EagleReferenceInputs.audit(p,output("result.vcf",row("22","50","mono","A","C,G","0/0","0/0")+normal()),()->{}).rows());
    }
    @Test void sampleFreeExclusionPreservesModeDependentAllelePairing()throws Exception {
        Path target=text("target.vcf",header(false)+row(50,"0|1","1|0")+normal()),ref=text("ref.vcf",header(true)+row(50,"0|1","1|0")+normal());
        Path excluded=text("exclude.vcf","##fileformat=VCFv4.2\n##contig=<ID=22>\n"+FIXED+"\n22\t50\texclude\tA\tG\t.\tPASS\t.\n");Path geneticMap=map();
        Config c=new Config();var exact=EagleReferenceInputs.prepare(target,ref,excluded,geneticMap,root.resolve("exact"),c.build(),()->{});assertEquals(3,exact.summary().phasedRows());assertEquals(0,exact.stages().get(EagleReferenceInputs.Role.EXCLUSION).calls());
        c.swap=true;c.unmatched=EagleReferenceInputs.Unmatched.KEEP_UNPHASED;var swap=EagleReferenceInputs.prepare(target,ref,excluded,geneticMap,root.resolve("swap"),c.build(),()->{});
        assertEquals(2,swap.summary().phasedRows());assertEquals(1,swap.summary().unphasedRows());assertEquals(3,EagleReferenceInputs.audit(swap,output("result.vcf",row(50,"0/1","1/0")+normal()),()->{}).rows());
    }
    @Test void matchedReferenceFallbacksRequireExplicitPolicyAndAreCountedWithoutRewritingGt()throws Exception {
        Config c=new Config();String reference=row(100,"0|.","0/1")+row(200,"0|1","1|1");
        assertTrue(assertThrows(IOException.class,()->prepare(normal(),reference,c,"refused")).getMessage().contains("ALLOW_ORIGINAL"));c.fallback=EagleReferenceInputs.ReferenceFallback.ALLOW_ORIGINAL_WITH_WARNING;
        var p=prepare(normal(),reference,c,"allowed");assertEquals(1,p.summary().referenceMissingCalls());assertEquals(1,p.summary().referenceUnphasedCalls());
        try(var r=EagleVcf.open(p.reference(),p.spec().vcfLimits(),()->{})){assertEquals("0|.",r.next().calls().get(0).raw());}assertTrue(p.warnings().stream().anyMatch(w->w.contains("REFERENCE FALLBACKS")));
    }
    @Test void originalUnphasedReferenceCounterIncludesHomozygotes()throws Exception {
        Config c=new Config();c.fallback=EagleReferenceInputs.ReferenceFallback.ALLOW_ORIGINAL_WITH_WARNING;
        var p=prepare(normal(),row(100,"0/0","1/1")+row(200,"0|1","1|1"),c,"work");assertEquals(2,p.summary().referenceUnphasedCalls());
    }
    @Test void observedHaploidReferenceRequiresDeclaredXAndPreservesItsRawStorage()throws Exception {
        assertTrue(assertThrows(IOException.class,()->prepare(normal(),row(100,"1","0|1")+row(200,"0|1","1|1"),new Config(),"refused")).getMessage().contains("haploid reference"));
        Config c=new Config();c.x=true;c.contig="X";String target=normal().replace("22\t","X\t"),ref=(row(100,"1","0")+row(200,"0","1")).replace("22\t","X\t");
        var p=prepare(target,ref,c,"x-work");assertEquals(2,p.summary().phasedRows());assertEquals(2,EagleReferenceInputs.audit(p,output("result.vcf",target),()->{}).rows());
    }
    @Test void allHaploidXTargetsWarnThatMissingCallsBypassRequestedImputation()throws Exception {
        Config c=new Config();c.x=true;c.contig="X";String target=(row(100,".","1")+row(200,".","0")).replace("22\t","X\t");
        var p=prepare(target,normal().replace("22\t","X\t"),c,"work");assertEquals(2,p.summary().allHaploidTargetRows());assertEquals(2,p.summary().missingTargetCallsBypassed());
        var result=EagleReferenceInputs.audit(p,output("result.vcf",target),()->{});assertEquals(2,result.missingCallsBypassedInAllHaploidReferenceRows());assertTrue(p.warnings().stream().anyMatch(w->w.contains("ALL-HAPLOID")));
    }
    @Test void partialTargetPolicyAndReferenceNoImputationClearingRemainExplicit()throws Exception {
        Config c=new Config();c.missing=EagleGenotypes.Missing.KEEP;String target=row(100,"0/.","1|0")+row(200,"0|1","1|1");
        assertTrue(assertThrows(IOException.class,()->prepare(target,normal(),c,"refused")).getMessage().contains("Partial target"));c.partial=EagleCohortInputs.PartialMissing.ALLOW_ORIGINAL_WHOLE_CALL_MISSING;
        var p=prepare(target,normal(),c,"allowed");assertEquals(1,p.summary().targetObservedAllelesIgnored());var result=EagleReferenceInputs.audit(p,output("result.vcf",row(100,"./.","0|1")+row(200,"0|1","1|1")),()->{});
        assertEquals(1,result.inputObservedAllelesCleared());assertTrue(p.warnings().stream().anyMatch(w->w.contains("cleared to fully missing")));
    }
    @Test void sampleLabelOverlapRequiresExplicitReviewRatherThanAutomaticExclusion()throws Exception {
        Path target=text("target.vcf",header(false)+normal()),ref=text("ref.vcf",header(true).replace("\tR1\tR2","\tA\tR2")+normal());Path geneticMap=map();Config c=new Config();
        assertTrue(assertThrows(IOException.class,()->EagleReferenceInputs.prepare(target,ref,null,geneticMap,root.resolve("refused"),c.build(),()->{})).getMessage().contains("labels overlap"));
        c.overlap=EagleReferenceInputs.SampleOverlap.ALLOW_WITH_WARNING;var p=EagleReferenceInputs.prepare(target,ref,null,geneticMap,root.resolve("allowed"),c.build(),()->{});assertEquals(2,p.summary().phasedRows());assertTrue(p.warnings().stream().anyMatch(w->w.contains("LABEL OVERLAP")));
    }
    @Test void malformedUnselectedTrailerCannotAdmitAValidRegionPrefix()throws Exception {
        assertThrows(IOException.class,()->prepare(normal()+row("X","100","bad","A","C","0/1/1","0|1"),normal(),new Config(),"work"));assertTrue(Files.exists(root.resolve("work/input-snapshots/target.vcf-source")));
    }
    @Test void mutatedOriginalOrAdmittedPlanInvalidatesPreparation()throws Exception {
        var p=prepare(normal(),normal(),new Config(),"first");Files.writeString(p.snapshots().get(0).original(),"changed");assertThrows(IOException.class,()->p.verify(()->{}));
        var q=prepare(normal(),normal(),new Config(),"second");Files.writeString(q.outputPlan(),Files.readString(q.outputPlan())+"CORRUPT\n");assertThrows(IOException.class,()->EagleReferenceInputs.audit(q,output("result.vcf",normal()),()->{}));
    }
    @Test void incompleteExtraWrongIdentityAndChangedDosageOutputsFailCompleteAudit()throws Exception {
        var p=prepare(normal(),normal(),new Config(),"work");List<String> invalid=List.of(row(100,"0|1","1|0"),normal()+row(300,"0|1","1|0"),normal().replace("v200","WRONG"),normal().replace("0|1","0|0"));
        for(int k=0;k<invalid.size();k++){Path result=output("bad-"+k+".vcf",invalid.get(k));assertThrows(IOException.class,()->EagleReferenceInputs.audit(p,result,()->{}));}
    }
    @Test void groupMemoryLedgerAndWorkspaceCapsRefuseRatherThanTruncate()throws Exception {
        Config group=new Config();group.records=1;assertTrue(assertThrows(IOException.class,()->prepare(normal(),normal(),group,"group")).getMessage().contains("row capacity"));
        Config memory=new Config();memory.retained=600000;String huge=row("22","100","v".repeat(6000),"A","C","0|1","1|0")+row(200,"0|1","1|1");assertTrue(assertThrows(IOException.class,()->prepare(normal(),huge,memory,"memory")).getMessage().contains("storage"));
        Config ledger=new Config();ledger.ledger=10;assertThrows(IOException.class,()->prepare(normal(),normal(),ledger,"ledger"));Config workspace=new Config();workspace.workspace=10;assertThrows(IOException.class,()->prepare(normal(),normal(),workspace,"workspace"));
    }
    @Test void zeroGeneticSpanOrNoLiteralMatchesCannotReturnAnAdmittedPreparation()throws Exception {
        Path target=text("target.vcf",header(false)+normal()),ref=text("ref.vcf",header(true)+normal()),geneticMap=map();Files.writeString(geneticMap,Files.readString(geneticMap).replace("1000 1 1","1000 1 0"));
        assertTrue(assertThrows(IOException.class,()->EagleReferenceInputs.prepare(target,ref,null,geneticMap,root.resolve("zero"),new Config().build(),()->{})).getMessage().contains("positive physical and genetic"));
        assertThrows(IOException.class,()->prepare(normal().replace("22\t","chr22\t"),normal(),new Config(),"aliases"));
    }
    @Test void existingOutputsAndCancellationKeepProvisionalEvidenceWithoutAdmission()throws Exception {
        Path work=Files.createDirectory(root.resolve("existing"));Files.writeString(work.resolve("sentinel"),"keep");assertThrows(IOException.class,()->prepare(normal(),normal(),new Config(),"existing"));assertEquals("keep",Files.readString(work.resolve("sentinel")));
        Path target=text("target.vcf",header(false)+normal()),ref=text("ref.vcf",header(true)+normal()),geneticMap=map();var n=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->EagleReferenceInputs.prepare(target,ref,null,geneticMap,root.resolve("cancel"),new Config().build(),()->{if(n.incrementAndGet()>4)throw new IllegalStateException("cancel");}));
    }
    @Test void referenceModeXAndPlanCapacityMustMatchOriginalRegionsAndSharedReaderBounds() {
        Config c=new Config();c.x=true;c.contig="23";assertThrows(IllegalArgumentException.class,c::build);
        Config capacity=new Config();capacity.ledger=1000000000001L;assertThrows(IllegalArgumentException.class,capacity::build);
    }
    @Test void repeatedMissingVariantIdsAreResolvedByRowOrdinalAndCompleteAlleles()throws Exception {
        String target=row("22","100",".","A","G","0|1","1|1")+row("22","100",".","A","C","0|1","1|0")+row("22","200",".","A","C","0|1","1|1");
        String reference=row("22","100",".","A","C","0|1","1|0")+row("22","100",".","A","G","0|1","1|1")+row("22","200",".","A","C","0|1","1|1");
        var p=prepare(target,reference,new Config(),"work");String result=row("22","100",".","A","C","1|0","0|1")+row("22","100",".","A","G","0|1","1|1")+row("22","200",".","A","C","0|1","1|1");
        assertEquals(3,EagleReferenceInputs.audit(p,output("result.vcf",result),()->{}).rows());
    }
    @Test void exclusionWithNoSelectedSitesIsRetainedButNotIndexedOrSentToEngine()throws Exception {
        Path target=text("target.vcf",header(false)+normal()),reference=text("ref.vcf",header(true)+normal()),exclude=text("exclude.vcf","##fileformat=VCFv4.2\n##contig=<ID=22>\n"+FIXED+"\n22\t2000\toutside\tA\tC\t.\tPASS\t.\n");
        var p=EagleReferenceInputs.prepare(target,reference,exclude,map(),root.resolve("work"),new Config().build(),()->{});
        assertNull(p.exclusion());assertEquals(0,p.stages().get(EagleReferenceInputs.Role.EXCLUSION).variants());assertTrue(Files.exists(p.work().resolve("exclusion.vcf.gz")));assertFalse(Files.exists(p.work().resolve("exclusion.vcf.gz.csi")));
        assertTrue(p.warnings().stream().anyMatch(w->w.contains("NO SELECTED EXCLUSIONS")));assertEquals(2,EagleReferenceInputs.audit(p,output("result.vcf",normal()),()->{}).rows());
    }
}
