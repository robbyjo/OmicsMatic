package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepeatHealthTest {
    @TempDir Path root;
    private int serial;
    private RepeatHealth.Prepared fixtures()throws Exception{return RepeatHealth.prepare(root.resolve("fixtures-"+serial++),()->{});}
    private RepeatHealth.Fixture fixture(RepeatHealth.Prepared p,String label)throws Exception{
        return p.cases(label.startsWith("current")?RepeatRecipe.CURRENT:RepeatRecipe.HISTORICAL).stream().filter(c->c.label().equals(label)).findFirst().orElseThrow();
    }
    private Path stage(RepeatHealth.Prepared p,RepeatHealth.Fixture f)throws Exception{
        Path stage=Files.createDirectory(root.resolve("stage-"+serial++)),input=Files.createDirectory(stage.resolve("post-input"));
        Files.copy(p.path(f.expectedAnnotations()),input.resolve("query.fa.out"));
        if(f.expectedMask()!=null)Files.copy(p.path(f.expectedMask()),input.resolve("query.fa.masked"));
        return stage;
    }
    @Test void completePinnedFixturePopulationHasFourCurrentAndNineHistoricalRoles()throws Exception{
        var p=fixtures();assertEquals(4,p.cases(RepeatRecipe.CURRENT).size());assertEquals(9,p.cases(RepeatRecipe.HISTORICAL).size());
        assertThrows(RepeatRecipe.UnavailableCapability.class,()->p.cases("unreported-historical-edition"));
        assertEquals(71,RepeatBundle.inventory(p.work(),new RepeatBundle.Limits(1048576,128),true,()->{}).members());
        p.verify(()->{});assertEquals(p,RepeatHealth.open(p.work(),()->{}));
    }
    @Test void allThirteenIndependentOriginalReferencesPassCompleteFieldAndMaskAudits()throws Exception{
        var p=fixtures();int rows=0,negative=0;
        for(String profile:List.of(RepeatRecipe.CURRENT,RepeatRecipe.HISTORICAL))for(var f:p.cases(profile)){
            var actual=RepeatHealth.validate(p,f,stage(p,f),()->{});rows+=actual.annotationRows();
            if(f.expectedMask()==null){negative++;assertTrue(actual.originalNoHitMaskAbsent());assertNull(actual.maskedSequenceContractSha256());}
            else{assertFalse(actual.originalNoHitMaskAbsent());assertTrue(actual.maskedSequenceContractSha256().matches("[0-9a-f]{64}"));}
            assertTrue(actual.queryBases()>0);assertTrue(actual.queryRecords()>0);
        }
        assertEquals(26,rows);assertEquals(2,negative);
    }
    @Test void fixedRecipesKeepTheExactEditionTaxonMasksAndScientificModes()throws Exception{
        var p=fixtures();var modes=new java.util.HashSet<RepeatRecipe.Mode>();var masks=new java.util.HashSet<org.jlinalg.genetics.RepeatAnnotations.Mask>();
        for(String profile:List.of(RepeatRecipe.CURRENT,RepeatRecipe.HISTORICAL))for(var f:p.cases(profile)){
            var s=RepeatRecipe.read(p.path(f.recipe()));assertEquals(profile,s.profile());assertEquals(RepeatRecipe.Operation.SEARCH,s.operation());
            assertEquals(RepeatRecipe.Staging.EXACT_SOURCE,s.staging());modes.add(s.mode());masks.add(s.mask());
            if(s.mode()!=RepeatRecipe.Mode.CUSTOM_LIBRARY)assertEquals("Mus musculus",s.species());
        }
        assertEquals(java.util.Set.of(RepeatRecipe.Mode.CUSTOM_LIBRARY,RepeatRecipe.Mode.SPECIES,RepeatRecipe.Mode.SIMPLE_ONLY),modes);
        assertEquals(java.util.Set.of(org.jlinalg.genetics.RepeatAnnotations.Mask.N,org.jlinalg.genetics.RepeatAnnotations.Mask.X,org.jlinalg.genetics.RepeatAnnotations.Mask.SOFT),masks);
    }
    private static int firstRow(List<String> lines){
        for(int i=0;i<lines.size();i++)if(lines.get(i).stripLeading().matches("[0-9].*"))return i;
        throw new AssertionError("Expected positive original row");
    }
    @Test void aChangedScoreFailsEvenWhenTheIntervalsAndMaskStillAgree()throws Exception{
        var p=fixtures();var f=fixture(p,"current-custom-N");Path stage=stage(p,f),out=stage.resolve("post-input/query.fa.out");
        var lines=new ArrayList<>(Files.readAllLines(out));int index=firstRow(lines);String line=lines.get(index),score=line.stripLeading().split("\\s+",2)[0];
        lines.set(index,line.replaceFirst(score,Long.toString(Long.parseLong(score)+1)));Files.write(out,lines);
        assertThrows(IOException.class,()->RepeatHealth.validate(p,f,stage,()->{}));
    }
    @Test void missingDuplicatedOrReorderedOriginalRowsCannotPassHealth()throws Exception{
        var p=fixtures();var f=fixture(p,"current-custom-N");
        for(String mutation:List.of("missing","duplicate","reordered")){
            Path stage=stage(p,f),out=stage.resolve("post-input/query.fa.out");var lines=new ArrayList<>(Files.readAllLines(out));int index=firstRow(lines);
            switch(mutation){case "missing"->lines.remove(index);case "duplicate"->lines.add(lines.get(index));case "reordered"->java.util.Collections.swap(lines,index,index+1);default->throw new AssertionError();}
            Files.write(out,lines);assertThrows(IOException.class,()->RepeatHealth.validate(p,f,stage,()->{}),mutation);
        }
    }
    @Test void aChangedMaskedBaseFailsAcrossTheCompletePopulation()throws Exception{
        var p=fixtures();var f=fixture(p,"current-custom-N");Path stage=stage(p,f),mask=stage.resolve("post-input/query.fa.masked");
        var lines=new ArrayList<>(Files.readAllLines(mask));
        for(int i=0;i<lines.size();i++)if(!lines.get(i).startsWith(">")&&!lines.get(i).isEmpty()){
            String line=lines.get(i);lines.set(i,(line.charAt(0)=='A'?"C":"A")+line.substring(1));break;
        }
        Files.write(mask,lines);assertThrows(IOException.class,()->RepeatHealth.validate(p,f,stage,()->{}));
    }
    @Test void aChangedMaskedHeaderIsNotHiddenByMatchingBaseStrings()throws Exception{
        var p=fixtures();var f=fixture(p,"current-custom-N");Path stage=stage(p,f),mask=stage.resolve("post-input/query.fa.masked");
        var lines=new ArrayList<>(Files.readAllLines(mask));lines.set(0,lines.get(0)+" changed description");Files.write(mask,lines);
        assertThrows(IOException.class,()->RepeatHealth.validate(p,f,stage,()->{}));
    }
    @Test void noHitMaskAbsenceCannotBeReplacedWithASynthesizedFile()throws Exception{
        var p=fixtures();var f=fixture(p,"current-negative-only");Path stage=stage(p,f);
        Files.writeString(stage.resolve("post-input/query.fa.masked"),">negative\nNNNN\n");
        assertThrows(IOException.class,()->RepeatHealth.validate(p,f,stage,()->{}));
    }
    @Test void noHitSummaryAbsenceIsAlsoPartOfTheOriginalContract()throws Exception{
        var p=fixtures();var f=fixture(p,"historical-negative-only");Path stage=stage(p,f);
        Files.writeString(stage.resolve("post-input/query.fa.tbl"),"invented zero summary\n");
        assertThrows(IOException.class,()->RepeatHealth.validate(p,f,stage,()->{}));
    }
    @Test void mutatedFixtureBytesInvalidatePreviouslyPreparedObjects()throws Exception{
        var p=fixtures();var f=fixture(p,"current-custom-N");Path stage=stage(p,f);
        Files.writeString(p.path(f.expectedAnnotations()),"changed reference\n");
        assertThrows(IOException.class,()->p.verify(()->{}));assertThrows(IOException.class,()->RepeatHealth.validate(p,f,stage,()->{}));
    }
    @Test void extraFilesAndEmptyDirectoriesAreRejectedByTheCompleteFixtureCensus()throws Exception{
        for(boolean directory:List.of(false,true)){
            var p=fixtures();Path extra=p.work().resolve("unmanaged");if(directory)Files.createDirectory(extra);else Files.writeString(extra,"extra");
            assertThrows(IOException.class,()->RepeatHealth.open(p.work(),()->{}));
        }
    }
    @Test void harmlessAnnotationWhitespaceAndFastaWrappingDoNotChangeTheScientificContract()throws Exception{
        var p=fixtures();var f=fixture(p,"current-custom-N");Path stage=stage(p,f),out=stage.resolve("post-input/query.fa.out"),mask=stage.resolve("post-input/query.fa.masked");
        var lines=Files.readAllLines(out).stream().map(line->line.stripLeading().matches("[0-9].*")?line.strip().replaceAll(" +","\t"):line).toList();Files.write(out,lines);
        var bases=new ArrayList<String>();for(String line:Files.readAllLines(mask)){
            if(line.startsWith(">"))bases.add(line);else for(int i=0;i<line.length();i+=17)bases.add(line.substring(i,Math.min(line.length(),i+17)));
        }
        Files.write(mask,bases);assertEquals(3,RepeatHealth.validate(p,f,stage,()->{}).annotationRows());
    }
    @Test void fixturePreparationNeverOverwritesAnExistingDirectory()throws Exception{
        Path existing=Files.createDirectory(root.resolve("keep"));Files.writeString(existing.resolve("KEEP"),"preserve");
        assertThrows(IOException.class,()->RepeatHealth.prepare(existing,()->{}));assertEquals("preserve",Files.readString(existing.resolve("KEEP")));
    }
    @Test void cancelledFixturePreparationRetainsDiagnosticsAndCannotOpenAsComplete()throws Exception{
        Path path=root.resolve("cancelled");assertThrows(CancellationException.class,()->RepeatHealth.prepare(path,()->{throw new CancellationException("controlled cancellation");}));
        assertTrue(Files.isRegularFile(path.resolve("HEALTH_FIXTURES_FAILED.txt")));assertThrows(IOException.class,()->RepeatHealth.open(path,()->{}));
    }
    @Test void missingTruncatedAndUnparseableAnnotationsCannotPassFromAnOldSuccessFlag()throws Exception{
        var p=fixtures();var f=fixture(p,"current-custom-N");
        for(String mutation:List.of("missing","empty","garbage")){
            Path stage=stage(p,f),out=stage.resolve("post-input/query.fa.out");Files.writeString(stage.resolve("health.json"),"{\"status\":\"PASSED\"}\n");
            if(mutation.equals("missing"))Files.delete(out);else Files.writeString(out,mutation.equals("empty")?"":"not valid original annotation\n");
            assertThrows(IOException.class,()->RepeatHealth.validate(p,f,stage,()->{}),mutation);
        }
    }
}
