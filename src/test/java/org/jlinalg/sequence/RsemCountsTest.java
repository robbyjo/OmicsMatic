package org.jlinalg.sequence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RsemCountsTest {
    private static final String ORIGINAL="2 32 0 34\n32 0 10\n42 3\n0\t2\n1\t22\n2\t10\nInf\t0\n";
    private static RsemCounts.Counts read(Path dir,String text)throws IOException{
        Path path=dir.resolve("sample.cnt");Files.writeString(path,text);return RsemCounts.read(path,1000,100000,1000,100000,10000);
    }
    @Test void originalGeneAndIsoformAccountingRemainsInReadTypeUnits(@TempDir Path dir)throws Exception{
        var x=read(dir,ORIGINAL);assertEquals(32,x.alignableGroups());assertEquals(42,x.alignmentHits());assertEquals(3,x.readType());assertEquals(10,x.multipleIsoformGroups());assertEquals(2,x.histogram().size());
        assertThrows(UnsupportedOperationException.class,()->x.histogram().clear());
    }
    @Test void allFourOriginalReadTypesArePreservedWithoutDoubling(@TempDir Path dir)throws Exception{
        for(int type=0;type<4;type++){var x=read(dir,ORIGINAL.replace("42 3","42 "+type));assertEquals(type,x.readType());assertEquals(34,x.totalGroups());}
    }
    @Test void headerHistogramAndHitTotalsMustAgree(@TempDir Path dir)throws Exception{
        for(String x:new String[]{ORIGINAL.replace("0 34","0 35"),ORIGINAL.replace("32 0 10","31 0 10"),ORIGINAL.replace("42 3","43 3"),ORIGINAL.replace("1\t22","1\t21"),ORIGINAL.replace("0\t2","0\t1"),ORIGINAL.replace("Inf\t0","Inf\t1")})
            assertThrows(IOException.class,()->read(dir,x));
    }
    @Test void multipleAlignmentCountMustMatchTheCompleteHistogram(@TempDir Path dir)throws Exception{
        for(String value:new String[]{"32 0 33","32 0 9","32 0 11"})
            assertThrows(IOException.class,()->read(dir,ORIGINAL.replace("32 0 10",value)));
    }
    @Test void originalCrossGeneGroupsAlsoContributeToIsoMulti(@TempDir Path dir)throws Exception{
        var counts=read(dir,"0 1 0 1\n0 1 1\n2 1\n0\t0\n2\t1\nInf\t0\n");
        assertEquals(0,counts.uniqueGeneGroups());assertEquals(1,counts.multipleGeneGroups());assertEquals(1,counts.multipleIsoformGroups());
        assertThrows(IOException.class,()->read(dir,"0 1 0 1\n0 1 0\n2 1\n0\t0\n2\t1\nInf\t0\n"));
    }
    @Test void binsCannotDuplicateReorderDisappearOrHideTrailingData(@TempDir Path dir)throws Exception{
        for(String x:new String[]{ORIGINAL.replace("2\t10","1\t10"),ORIGINAL.replace("1\t22\n2\t10","2\t10\n1\t22"),ORIGINAL.replace("Inf\t0\n",""),ORIGINAL+"1 1\n",ORIGINAL.replace("1\t22","1\t0")})
            assertThrows(IOException.class,()->read(dir,x));
    }
    @Test void completePopulationCapacitiesAreEnforced(@TempDir Path dir)throws Exception{
        Path p=dir.resolve("x.cnt");Files.writeString(p,ORIGINAL);
        assertThrows(IOException.class,()->RsemCounts.read(p,33,100,100,10000,1000));
        assertThrows(IOException.class,()->RsemCounts.read(p,100,41,100,10000,1000));
        assertThrows(IOException.class,()->RsemCounts.read(p,100,100,1,10000,1000));
        assertThrows(IOException.class,()->RsemCounts.read(p,100,100,100,4,1000));
    }
    @Test void exactArithmeticOverflowIsRejected(@TempDir Path dir)throws Exception{
        Path p=dir.resolve("x.cnt");Files.writeString(p,"0 2 0 2\n2 0 0\n1 3\n0 0\n9223372036854775807 2\nInf 0\n");
        assertThrows(IOException.class,()->RsemCounts.read(p,Long.MAX_VALUE,Long.MAX_VALUE,100,10000,1000));
    }
}
