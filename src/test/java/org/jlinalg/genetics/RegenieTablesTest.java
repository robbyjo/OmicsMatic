/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.jlinalg.genetics.RegenieTables.Kind.*;

class RegenieTablesTest {
    @TempDir Path directory;
    private Path input(String text)throws Exception{Path p=directory.resolve("input");Files.writeString(p,text);return p;}
    @Test void quantitativeModeDoesNotInferBinaryStatusAndSelectedCellsKeepOriginalOrderAndPrecision()throws Exception{
        var table=RegenieTables.read(input("FID IID other q\n2 20 text 1.0000000000000001\n1 10 ignored 0\n3 30 ignored NA\n"),List.of("q"),Set.of(),QUANTITATIVE);
        assertEquals(List.of(new RegenieTables.Sample("2","20"),new RegenieTables.Sample("1","10"),new RegenieTables.Sample("3","30")),table.samples());assertEquals(1,table.missing().get("q"));
        Path output=directory.resolve("canonical");table.write(output);assertEquals("FID\tIID\tq\n2\t20\t1.0000000000000001\n1\t10\t0\n3\t30\tNA\n",Files.readString(output));
    }
    @Test void pairAndUnderscoreCompositeCollisionsAreRejectedBeforeUpstreamMatching()throws Exception{
        var duplicate=input("FID IID y\n1 2 0\n1 2 1\n");assertThrows(java.io.IOException.class,()->RegenieTables.read(duplicate,List.of("y"),Set.of(),QUANTITATIVE));
        var collision=input("FID IID y\na_b c 0\na b_c 1\n");var failure=assertThrows(java.io.IOException.class,()->RegenieTables.read(collision,List.of("y"),Set.of(),QUANTITATIVE));assertTrue(failure.getMessage().contains("colliding"));
    }
    @Test void binaryCodingIsExplicitAndCovariateCategoriesAreNotNumericCoercions()throws Exception{
        var binary=input("FID IID y\n1 1 1\n2 2 2\n");assertEquals(2,RegenieTables.read(binary,List.of("y"),Set.of(),BINARY_ONE_TWO).rows().size());assertThrows(java.io.IOException.class,()->RegenieTables.read(binary,List.of("y"),Set.of(),BINARY_ZERO_ONE));
        var covariates=input("FID IID batch age\n1 1 array-A 4.5e1\n2 2 array-B NA\n");var result=RegenieTables.read(covariates,List.of("age","batch"),Set.of("batch"),COVARIATES);assertEquals(List.of("4.5e1","array-A"),result.rows().get(0).cells());assertEquals(1,result.missing().get("age"));
    }
    @Test void partialNumericTokensReservedSentinelsAndAmbiguousMissingnessFail()throws Exception{
        for(String value:List.of("12suffix","NaN","Infinity","-999","-999.0")){
            var file=input("FID IID y\n1 1 "+value+"\n");assertThrows(java.io.IOException.class,()->RegenieTables.read(file,List.of("y"),Set.of(),QUANTITATIVE),value);
        }
        var missing=input("FID IID y\n1 1 NA\n");assertThrows(java.io.IOException.class,()->RegenieTables.read(missing,List.of("y"),Set.of(),QUANTITATIVE));
        var absent=input("FID IID y\n1 1\n");assertThrows(java.io.IOException.class,()->RegenieTables.read(absent,List.of("y"),Set.of(),QUANTITATIVE));
    }
}
