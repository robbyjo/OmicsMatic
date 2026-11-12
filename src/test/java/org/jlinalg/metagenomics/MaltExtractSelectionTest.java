/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaltExtractSelectionTest {
    @Test void originalChangingDepthLoopSkipsIntermediateCladesOnLongUnaryPaths()throws Exception {
        var tree=MaltExtractTaxonomy.read(new StringReader("((((((7:1)6:1)5:1)4:1)3:1)2:1)1:1;"),new MaltExtractTaxonomy.Limits(1000,100,100));
        var model=new MaltExtractSelection.Model(tree);Set<Integer> keys=Set.of(1,2,3,4,5,6,7,999);
        assertEquals(Set.of(4,5,6,7),model.sourceDescendants(1,keys));
        assertEquals(Set.of(1,4,5,6,7),model.processedIds(List.of(1),keys));
        assertEquals(Set.of(7),model.sourceDescendants(7,keys));
        assertThrows(IOException.class,()->model.sourceDescendants(999,keys));
    }
    @Test void literalMapUsesLastNameAndLastIdAssignmentWithoutAliasRepair()throws Exception {
        String map="1\troot\n+2\tduplicate\n02\tfinal-display\n3\tduplicate\n";
        var names=MaltExtractSelection.names(new StringReader(map),List.of(" duplicate ","Duplicate","duplicate_alias"),Set.of(2,3),new MaltExtractSelection.MapLimits(1000,100,100,10,10));
        assertEquals(3,names.targets().get(0).taxon());assertEquals(" duplicate ",names.targets().get(0).supplied());assertEquals("duplicate",names.targets().get(0).lookup());assertEquals(2,names.targets().get(0).matchingRows());
        assertEquals("final-display",names.displayNames().get(2));assertEquals(List.of("Duplicate","duplicate_alias"),names.missingNames());assertEquals(4,names.rows());
    }
    @Test void readerFailureAndFullMapCapacityCannotAdmitAPrefix() {
        Reader broken=new Reader(){int at;public int read(char[] b,int off,int n)throws IOException{if(at++>0)throw new IOException("retained source I/O failure");String row="1\troot\n";row.getChars(0,row.length(),b,off);return row.length();}public void close(){}};
        var caps=new MaltExtractSelection.MapLimits(100,10,100,10,10);
        assertThrows(IOException.class,()->MaltExtractSelection.names(broken,List.of("root"),Set.of(1),caps));
        assertThrows(IOException.class,()->MaltExtractSelection.names(new StringReader("1\troot\n2\tother\n"),List.of("root"),Set.of(1),new MaltExtractSelection.MapLimits(100,1,100,10,10)));
    }
    @Test void sourceNamingNamespacesRemainDistinctAndCollisionsRefuseOutput()throws Exception {
        String raw="Human ' # $ / \\ colon:trait?";
        assertEquals("Human_______/_\\_colon:trait?",MaltExtractOutputAudit.tableName(raw));
        assertEquals("Human_'___$_____colon:trait?",MaltExtractOutputAudit.fileName(raw));
        assertEquals("Human_'_#_$_/_\\_colon:trait?",MaltExtractOutputAudit.summaryName(raw));
        assertThrows(IOException.class,()->MaltExtractOutputAudit.names(Map.of(1,"A B",2,"A_B"),Set.of(1,2)));
        assertThrows(IOException.class,()->MaltExtractOutputAudit.names(Map.of(1,"one"),Set.of(1,2)));
    }
}
