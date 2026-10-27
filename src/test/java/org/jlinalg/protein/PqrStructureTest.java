/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.protein;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class PqrStructureTest {
    @Test void optionalChainFormsPreserveOrderIdentitiesAndSignedCharges()throws Exception {
        var model=PqrStructure.read(new StringReader("ATOM 9 N THR A 1 1 2 3 -.32 2\nHETATM 4 CA ION -2 4 5 6 2 1.5\n"),10);assertEquals(2,model.atoms().size());assertEquals(9,model.atoms().get(0).number());assertEquals("A",model.atoms().get(0).chain());assertEquals("",model.atoms().get(1).chain());assertEquals(-2,model.atoms().get(1).residueNumber());assertTrue(model.atoms().get(1).hetero());assertEquals(1.68,model.totalCharge(),1e-15);assertEquals(1,model.bounds().minimum().x());assertEquals(4,model.bounds().maximum().x());
    }
    @Test void chargedZeroRadiusAtomsRemainPresent()throws Exception {var model=PqrStructure.read(new StringReader("ATOM 1 H THR A 1 0 0 0 .33 0\n"),10);assertEquals(1,model.zeroRadiusAtoms());assertEquals(.33,model.totalCharge());assertEquals(1,model.atoms().size());}
    @Test void duplicatesAndMultipleModelsAreNotSilentlyMerged() {
        String atom="ATOM 1 CA ALA A 1 0 0 0 .1 2\n";for(String source:new String[]{atom+atom,atom+"ATOM 2 CA ALA A 1 1 1 1 .2 2\n","MODEL 1\n"+atom+"ENDMDL\n"})assertThrows(IOException.class,()->PqrStructure.read(new StringReader(source),10));
    }
    @Test void malformedNonfiniteAndNegativeRadiusRecordsFail() {
        for(String source:new String[]{"ATOM 1 H THR A 1 0 0 0 .33 -1\n","ATOM 1 H THR A 1 NaN 0 0 .33 0\n","ATOM 1 H THR A 1 0 0 0 Infinity 0\n","ATOM 1 H THR A 1 0 0 0 .33\n","ATOM 1 H THR A 1AB 0 0 0 .33 0\n"})assertThrows(IOException.class,()->PqrStructure.read(new StringReader(source),10));
    }
    @Test void signedResidueInsertionCodesRemainDistinctAndCompatibilityConstructorWorks()throws Exception {
        var model=PqrStructure.read(new StringReader("ATOM 1 CA ALA A -2A 0 0 0 .1 2\nATOM 2 CA ALA A -2B 1 0 0 .2 2\n"),10);assertEquals(-2,model.atoms().get(0).residueNumber());assertEquals("A",model.atoms().get(0).insertionCode());assertNotEquals(model.atoms().get(0).key(),model.atoms().get(1).key());
        var old=new PqrStructure.Atom(false,3,"C","ALA","A",1,new org.jlinalg.protein.MolecularGeometry.Point(0,0,0),0,2);assertEquals("",old.insertionCode());assertEquals("A\u001f1\u001fALA\u001fC",old.key());
    }
    @Test void emptyAndBoundedInputsFailClearly() {assertThrows(IOException.class,()->PqrStructure.read(new StringReader("REMARK no atoms\nEND\n"),10));assertThrows(IOException.class,()->PqrStructure.read(new StringReader("ATOM 1 N ALA A 1 0 0 0 0 2\nATOM 2 C ALA A 1 1 0 0 0 2\n"),1));assertThrows(IllegalArgumentException.class,()->PqrStructure.read(new StringReader(""),0));}
    @Test void commentsAreRetainedWithoutInferringParameters()throws Exception {var model=PqrStructure.read(new StringReader("REMARK SOURCE custom\n# no force field inferred\nATOM 1 N ALA A 1 0 0 0 0 2\nTER\nEND\n"),10);assertEquals(2,model.remarks().size());assertThrows(UnsupportedOperationException.class,()->model.atoms().clear());assertThrows(UnsupportedOperationException.class,()->model.remarks().clear());}
    @Test void originalPdb2PqrParseChargesAndZeroRadiusHydrogensMatchDecimalTruth()throws Exception {
        var properties=new Properties();try(var input=getClass().getResourceAsStream("/org/jlinalg/protein/apbs-grid/reference.properties")){assertNotNull(input);properties.load(input);}byte[] raw=OpenDxGridTest.original("1CRN-PARSE.pqr.gz",properties,"pqr.compressed_sha256");var model=PqrStructure.read(new InputStreamReader(new ByteArrayInputStream(raw),StandardCharsets.US_ASCII),1000);assertEquals(Integer.parseInt(properties.getProperty("pqr.atoms")),model.atoms().size());assertEquals(Double.parseDouble(properties.getProperty("pqr.total_charge_e")),model.totalCharge(),1e-12);assertEquals(Long.parseLong(properties.getProperty("pqr.zero_radius_atoms")),model.zeroRadiusAtoms());assertEquals("N",model.atoms().get(0).name());assertEquals(-.32,model.atoms().get(0).charge());assertEquals(17.047,model.atoms().get(0).coordinate().x());
    }
}
