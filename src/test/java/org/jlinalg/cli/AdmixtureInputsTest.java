package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdmixtureInputsTest {
    @TempDir Path temp;
    @Test void explicitChoicesResolvePathsAndKeepIntegerVersusDeltaStops()throws Exception {
        var f=new AdmixtureTestData(temp);var spec=AdmixtureInputs.read(f.write(f.recipe()));assertEquals(temp.resolve("cohort"),spec.prefix());assertEquals("0.000001",spec.major().argument());assertEquals("3",spec.minor().argument());assertEquals(64,spec.recipeHash().length());
    }
    @Test void unknownMissingAndFractionalScientificFieldsFail()throws Exception {
        var f=new AdmixtureTestData(temp);var r=f.recipe();r.put("script","arbitrary");assertThrows(IOException.class,()->AdmixtureInputs.read(f.write(r)));r.remove("script");r.remove("seed");assertThrows(IOException.class,()->AdmixtureInputs.read(f.write(r)));r.put("seed",1.5);assertThrows(IOException.class,()->AdmixtureInputs.read(f.write(r)));
    }
    @Test void unknownOriginalVersionIsPreservedForDependencySkipNotUpgraded()throws Exception {
        var f=new AdmixtureTestData(temp);var r=f.recipe();r.put("version","1.23");assertEquals("1.23",AdmixtureInputs.read(f.write(r)).version());r.put("version","1.3");assertEquals("1.3",AdmixtureInputs.read(f.write(r)).version());
    }
    @Test void recipeAndSnapshotGenotypesRemainHashBound()throws Exception {
        var f=new AdmixtureTestData(temp);var p=f.prepare(f.recipe(),"work");p.verify();assertEquals(4,p.samples().size());assertEquals(3,p.markers().size());assertArrayEquals(Files.readAllBytes(temp.resolve("cohort.bed")),Files.readAllBytes(Path.of(p.prefix()+".bed")));Files.writeString(temp.resolve("cohort.bim"),"changed");assertThrows(IOException.class,p::verify);
    }
    @Test void changedStagedFilesCannotBeAdmitted()throws Exception {
        var f=new AdmixtureTestData(temp);var p=f.prepare(f.recipe(),"work");Files.writeString(temp.resolve("work/cohort.fam"),"changed");assertThrows(IOException.class,p::verify);
    }
    @Test void unsafeBootstrapDefaultsAndIncompleteCountsFailBeforeLaunch()throws Exception {
        var f=new AdmixtureTestData(temp);var r=f.recipe();r.put("bootstrap_replicates",10);assertThrows(IOException.class,()->AdmixtureInputs.read(f.write(r)));r.put("bootstrap_block_markers",3);assertThrows(IOException.class,()->f.prepare(r,"oversized"));r.put("bootstrap_block_markers",1);var p=f.prepare(r,"valid");assertTrue(AdmixtureInputs.command(p).contains("--blockSize=1"));
    }
    @Test void invalidStoppingAndPlannedModelChoicesFail()throws Exception {
        var f=new AdmixtureTestData(temp);var r=f.recipe();r.put("major_stop",Map.of("kind","delta","value",1));assertThrows(IOException.class,()->AdmixtureInputs.read(f.write(r)));r.put("major_stop",Map.of("kind","iterations","value",2.1));assertThrows(IOException.class,()->AdmixtureInputs.read(f.write(r)));var bad=f.recipe();bad.put("cv_folds",1);assertThrows(IOException.class,()->AdmixtureInputs.read(f.write(bad)));
    }
    @Test void genotypeWorkAndParameterMemoryLimitsApply()throws Exception {
        var f=new AdmixtureTestData(temp);var r=f.recipe();var limits=new LinkedHashMap<String,Object>();((Map<?,?>)r.get("limits")).forEach((k,v)->limits.put((String)k,v));limits.put("genotype_cells",11);r.put("limits",limits);assertThrows(IOException.class,()->f.prepare(r,"cells"));limits.put("genotype_cells",100);limits.put("parameter_cells",13);assertThrows(IOException.class,()->f.prepare(r,"parameters"));
    }
    @Test void numericContigsUniqueMarkersAndResolvedAllelesAreRequired()throws Exception {
        var f=new AdmixtureTestData(temp);Files.writeString(temp.resolve("cohort.bim"),"X v1 0 1 A G\n1 v2 0 2 C T\n2 v3 0 3 G A\n");assertThrows(IOException.class,()->f.prepare(f.recipe(),"chr"));f.bed();Files.writeString(temp.resolve("cohort.bim"),"1 v1 0 1 A G\n1 v1 0 2 C T\n2 v3 0 3 G A\n");assertThrows(IOException.class,()->f.prepare(f.recipe(),"duplicate"));f.bed();Files.writeString(temp.resolve("cohort.bim"),"1 v1 0 1 0 G\n1 v2 0 2 C T\n2 v3 0 3 G A\n");assertThrows(IOException.class,()->f.prepare(f.recipe(),"alleles"));
    }
    @Test void allMissingMarkersAndSamplesFail()throws Exception {
        var f=new AdmixtureTestData(temp);Files.write(temp.resolve("cohort.bed"),new byte[]{0x6c,0x1b,1,0x55,(byte)0xcc,0x3a});assertThrows(IOException.class,()->f.prepare(f.recipe(),"marker"));Files.write(temp.resolve("cohort.bed"),new byte[]{0x6c,0x1b,1,0x49,(byte)0xcd,0x39});assertThrows(IOException.class,()->f.prepare(f.recipe(),"sample"));
    }
    @Test void haploidCodingRejectsHeterozygotesAndProducesExplicitFlag()throws Exception {
        var f=new AdmixtureTestData(temp);var r=f.recipe();r.put("model_ploidy",1);r.put("genotype_coding","homozygous-haploid");assertThrows(IOException.class,()->f.prepare(r,"heterozygous"));Files.write(temp.resolve("cohort.bed"),new byte[]{0x6c,0x1b,1,(byte)0xc3,(byte)0xc3,(byte)0xc3});assertTrue(AdmixtureInputs.command(f.prepare(r,"haploid")).contains("--haploid=*"));
    }
    @Test void supervisionJoinsExactIdsAndUsesSafeReferenceAliases()throws Exception {
        var f=new AdmixtureTestData(temp);var r=f.recipe();r.put("operation","supervised");r.put("populations","labels.tsv");Files.writeString(temp.resolve("labels.tsv"),"fid\tiid\tpopulation\nf\td\t-\nf\tc\t-\nf\tb\tReference β\nf\ta\tReference α\n");var p=f.prepare(r,"supervised");assertEquals(List.of("Reference α","Reference β","-","-"),p.labels());assertEquals("G1\nG2\n-\n-\n",Files.readString(temp.resolve("supervised/cohort.pop")));assertTrue(AdmixtureInputs.command(p).contains("--supervised"));
    }
    @Test void supervisionRejectsMissingExtraAndDuplicateIds()throws Exception {
        var f=new AdmixtureTestData(temp);var r=f.recipe();r.put("operation","supervised");r.put("populations","labels.tsv");Files.writeString(temp.resolve("labels.tsv"),"fid\tiid\tpopulation\nf\ta\tA\nf\ta\tB\n");assertThrows(IOException.class,()->f.prepare(r,"duplicate"));Files.writeString(temp.resolve("labels.tsv"),"fid\tiid\tpopulation\nf\ta\tA\n");assertThrows(IOException.class,()->f.prepare(r,"missing"));
    }
    @Test void metadataUsesExactUtf8BytesAndRejectsDuplicateKeysOrExcessSize()throws Exception {
        var f=new AdmixtureTestData(temp);Path p=temp.resolve("bad.json");Files.write(p,new byte[]{(byte)0xc3,0x28});assertThrows(IOException.class,()->AdmixtureInputs.read(p));Files.writeString(p,"{\"k\":2,\"k\":3}");assertThrows(IOException.class,()->AdmixtureInputs.json(p,100));Files.writeString(p," ".repeat(101));assertThrows(IOException.class,()->AdmixtureInputs.raw(p,100));Path valid=f.write(f.recipe());byte[] raw=Files.readAllBytes(valid);assertEquals(AdmixtureInputs.digest(raw),AdmixtureInputs.read(valid).recipeHash());
    }
    @Test void referenceLabelsCannotContainHiddenControlCharacters()throws Exception {
        var f=new AdmixtureTestData(temp);var r=f.recipe();r.put("operation","supervised");r.put("populations","labels.tsv");Files.writeString(temp.resolve("labels.tsv"),"fid\tiid\tpopulation\nf\ta\tA\u0000hidden\nf\tb\tB\nf\tc\t-\nf\td\t-\n");assertThrows(IOException.class,()->f.prepare(r,"control-label"));
    }
}
