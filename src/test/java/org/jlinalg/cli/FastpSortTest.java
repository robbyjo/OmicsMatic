/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FastpSortTest {
    @TempDir Path temporary;
    @Test void completeMultisetSurvivesMultipleRunMergeIncludingDuplicateKeys()throws Exception {
        var rows=new ArrayList<String>();for(int i=0;i<5000;i++)rows.add("key"+(i%17)+"\t"+String.format(java.util.Locale.ROOT,"%08d",i)+"\t"+"x".repeat(80));Collections.shuffle(rows,new Random(42));Path input=temporary.resolve("input.tsv");Files.writeString(input,String.join("\n",rows)+"\n");var result=FastpSort.sort(input,temporary.resolve("sort"),new FastpSort.Limits(1048576,1000,10000,128),()->{},()->{});rows.sort(String::compareTo);assertEquals(rows,Files.readAllLines(result.path()));assertEquals(5000,result.records());
        try(var files=Files.list(result.path().getParent())){assertEquals(1,files.count());}
    }
    @Test void excessRowsAndLinesFailWithoutTruncatingOrOverwriting()throws Exception {
        Path input=temporary.resolve("input.tsv");Files.writeString(input,"key\tvalue\nkey\tsecond\n");assertThrows(IOException.class,()->FastpSort.sort(input,temporary.resolve("failed"),new FastpSort.Limits(1048576,100,1,32),()->{},()->{}));assertEquals("key\tvalue\nkey\tsecond\n",Files.readString(input));
    }
    @Test void cancellationRetainsOwnedScratchAndCannotReturnSuccess()throws Exception {
        Path input=temporary.resolve("input.tsv");Files.writeString(input,"key\tvalue\n");assertThrows(IllegalStateException.class,()->FastpSort.sort(input,temporary.resolve("cancelled"),new FastpSort.Limits(1048576,100,10,32),()->{},()->{throw new IllegalStateException("cancel");}));
    }
}
