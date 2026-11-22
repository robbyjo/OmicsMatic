package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HomerGeneratedSortTest {
    @TempDir Path root;
    private HomerGeneratedSort sorter(long records)throws Exception{return new HomerGeneratedSort(root.resolve("sort"),new HomerCoreData.Budget(200000000),1048576,20000,records,()->{});}
    @Test void emptyAndUnsortedJoinsPreserveAllRows()throws Exception{Path input=root.resolve("input");Files.writeString(input,"c\t3\na\t1\nb\t2\n");var s=sorter(10);var out=s.sort(input,"values");assertEquals(3,out.records());assertEquals(List.of("a\t1","b\t2","c\t3"),Files.readAllLines(out.path()));Files.writeString(input,"");assertEquals(0,s.sort(input,"empty").records());}
    @Test void duplicateIdsAreRejectedAcrossIndependentChunks()throws Exception{Path input=root.resolve("input");Files.writeString(input,"a\t1\na\t2\n");assertThrows(IOException.class,()->sorter(10).sort(input,"duplicate"));}
    @Test void multipassFanInKeepsCompleteSortedMembership()throws Exception{Path input=root.resolve("input");try(var out=Files.newBufferedWriter(input)){for(int i=1399;i>=0;i--)out.write(String.format(java.util.Locale.ROOT,"ID%04d\t",i)+"A".repeat(16000)+"\n");}var result=sorter(1400).sort(input,"multi");assertEquals(1400,result.records());try(var in=Files.newBufferedReader(result.path())){for(int i=0;i<1400;i++)assertTrue(in.readLine().startsWith(String.format(java.util.Locale.ROOT,"ID%04d\t",i)));assertNull(in.readLine());}}
    @Test void capacitiesAndMalformedRowsFailWithRetainedInputs()throws Exception{Path input=root.resolve("input");Files.writeString(input,"a\t1\nb\t2\n");assertThrows(IOException.class,()->sorter(1).sort(input,"limited"));assertTrue(Files.exists(input));}
    @Test void deadlineChecksCanAbortBeforeSorting()throws Exception{Path input=root.resolve("input");Files.writeString(input,"a\t1\n");var s=new HomerGeneratedSort(root.resolve("sort"),new HomerCoreData.Budget(10000000),1048576,1000,10,()->{throw new IOException("deadline");});assertThrows(IOException.class,()->s.sort(input,"deadline"));}
}
