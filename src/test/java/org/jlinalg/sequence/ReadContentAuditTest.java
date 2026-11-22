/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadContentAuditTest {
    @TempDir Path directory;
    @Test void spillsAndChecksUnorderedDuplicateObservationsWithoutLosingMultiplicity()throws Exception{
        Path tmp=directory.resolve("audit");
        try(var audit=new ReadContentAudit(tmp,2)){
            for(int i=0;i<12;i++){audit.expected("read"+(i%3),i%2+1,"ACGT","IJKL");}
            for(int i=11;i>=0;i--){audit.observed("read"+(i%3),i%2+1,"ACGT","IJKL");}
            audit.verify();
        }
        assertFalse(Files.exists(tmp));
    }
    @Test void detectsQualityMateNameSequenceAndMultiplicityChanges()throws Exception{
        for(String problem:new String[]{"quality","mate","name","sequence","duplicate","omitted"}){
            try(var audit=new ReadContentAudit(directory.resolve(problem),1)){
                audit.expected("r",1,"ACGT","IJKL");
                if(!problem.equals("omitted"))audit.observed(problem.equals("name")?"s":"r",problem.equals("mate")?2:1,problem.equals("sequence")?"ACGA":"ACGT",problem.equals("quality")?"IJKM":"IJKL");
                if(problem.equals("duplicate"))audit.observed("r",1,"ACGT","IJKL");
                assertThrows(java.io.IOException.class,audit::verify);
            }
        }
    }
}
