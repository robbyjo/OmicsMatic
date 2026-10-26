package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlinkQcInputsTest {
    @TempDir Path root;
    private PlinkGlmInputs.Bounds bounds(){return new PlinkGlmInputs.Bounds(1000,1000,1000000,10485760,10485760,16384,10485760,10485760);}
    private PlinkQcInputs.Prepared prepare(PlinkQcFixture.Input fixture,String unresolved) throws Exception{return PlinkQcInputs.prepare(fixture.prefix(),fixture.samples(),Files.createTempDirectory(root,"stage"),unresolved,bounds());}
    @Test void explicitQcIdentitiesPoliciesGroupsAndRecordedParentsArePreserved() throws Exception {
        var fixture=PlinkQcFixture.create(root.resolve("输入"),false,false,false);var p=prepare(fixture,"exclude");assertEquals(128,p.samples().size());assertEquals(127,p.initialSamples().size());assertEquals(96,p.variants().size());assertEquals(94,p.initialVariants().size());assertFalse(p.initialVariants().contains("V4"));assertFalse(p.initialVariants().contains("V96"));
        assertTrue(p.samples().values().stream().anyMatch(s->s.original().equals(new PlinkQcInputs.Id("A_B","C"))));assertTrue(p.samples().values().stream().anyMatch(s->s.original().equals(new PlinkQcInputs.Id("A","B_C"))));assertTrue(p.samples().values().stream().anyMatch(s->s.original().equals(new PlinkQcInputs.Id("家族","成员"))&&s.sex().equals("0")));
        assertEquals(2,p.samples().values().stream().filter(s->!s.founder()).count());assertEquals(62,PlinkQcInputs.population(p,p.initialSamples(),"founders",true).size());assertEquals(64,PlinkQcInputs.population(p,p.initialSamples(),"all",true).size());
    }
    @Test void unresolvedPolicyDoesNotSilentlyInterpretMissingAlleleLabels() throws Exception {
        var fixture=PlinkQcFixture.create(root.resolve("input"),false,false,false);assertThrows(Exception.class,()->prepare(fixture,"error"));var p=prepare(fixture,"retain-with-warning");assertEquals(95,p.initialVariants().size());assertFalse(p.variants().get("V4").resolved());
    }
    @Test void completeCohortUnknownDuplicateAndMalformedSelectionsFail() throws Exception {
        var fixture=PlinkQcFixture.create(root.resolve("input"),false,false,false);var original=Files.readAllLines(fixture.samples());
        for(int i=0;i<4;i++){var changed=new java.util.ArrayList<>(original);if(i==0)changed.remove(1);if(i==1)changed.set(2,changed.get(1));if(i==2)changed.set(1,"UNKNOWN\tID\tyes\tcontrol");if(i==3)changed.set(1,changed.get(1).replace("yes","true"));Files.write(fixture.samples(),changed);assertThrows(Exception.class,()->prepare(fixture,"exclude"));}
    }
    @Test void zeroInitialPopulationAndEmptyRequestedReferenceFailExplicitly() throws Exception {
        var fixture=PlinkQcFixture.create(root.resolve("input"),false,false,false);String original=Files.readString(fixture.samples());Files.writeString(fixture.samples(),original.replace("\tyes\t","\tno\t"));assertThrows(Exception.class,()->prepare(fixture,"exclude"));
        Files.writeString(fixture.samples(),original.replace("\tcontrol\n","\tcase\n"));var p=prepare(fixture,"exclude");assertThrows(Exception.class,()->PlinkQcInputs.population(p,p.initialSamples(),"all",true));
    }
    @Test void changedSourceAndCorruptPackedDimensionsAreDetected() throws Exception {
        var fixture=PlinkQcFixture.create(root.resolve("input"),false,false,false);Path stage=Files.createDirectory(root.resolve("stage"));var p=PlinkQcInputs.prepare(fixture.prefix(),fixture.samples(),stage,"exclude",bounds());Files.writeString(fixture.samples(),Files.readString(fixture.samples())+"\n");assertThrows(Exception.class,()->PlinkQcInputs.verify(p,stage));
        Files.write(Path.of(fixture.prefix()+".bed"),new byte[]{0x6c,0x1b,1});assertThrows(Exception.class,()->prepare(fixture,"exclude"));
    }
}
