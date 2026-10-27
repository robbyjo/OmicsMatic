/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipInputStream;
import org.jlinalg.protein.PqrStructure;
import org.junit.jupiter.api.Test;

class Pdb2pqrInputTest {
    static String atom(int serial,String name,char alt,String residue,String chain,int number,char insertion,double x,String segment,String element) {
        return String.format(Locale.ROOT,"%-6s%5d %-4s%c%3s %1s%4d%c   %8.3f%8.3f%8.3f%6.2f%6.2f      %4s%2s%n","ATOM",serial,name,alt,residue,chain,number,insertion,x,0.,0.,1.,10.,segment,element);
    }
    private static String n(double x){return atom(1,"N",' ',"ALA","A",1,' ',x,"","N");}
    private static Pdb2pqrInput.Selected source(String pdb)throws Exception{return Pdb2pqrInput.select(pdb,1,"error",null,100);}
    private static PqrStructure pqr(String rows)throws Exception{return PqrStructure.read(new StringReader(rows),100);}
    private static Pdb2pqrInput.Audit audit(String input,String charged,String prepared,String mode,String water,String repair,String missing,double shift)throws Exception{return Pdb2pqrInput.audit(source(input),pqr(charged),prepared,mode,water,repair,missing,shift,100);}
    @Test void modelStateIsOrdinalAndChosenTerRecordsSurvive()throws Exception {
        String pdb="HEADER source\nMODEL        9\n"+n(1)+"TER       2      ALA A   1\nENDMDL\nMODEL      100\n"+n(4)+"TER       2      ALA A   1\nENDMDL\nEND\n";var selected=Pdb2pqrInput.select(pdb,2,"error",null,100);assertEquals(List.of("9","100"),selected.sourceModels());assertEquals(2,selected.sourceAtoms());assertEquals(4,selected.atoms().get(0).point().x());assertFalse(selected.pdb().contains("MODEL"));assertTrue(selected.pdb().contains("TER"));assertEquals(4,PymolStructureInput.parse(selected.pdb(),"pdb",1,"author",100).atoms().get(0).point().x());
    }
    @Test void explicitAlternateSelectionKeepsBlankAtomsAndClearsOnlyChosenLabels()throws Exception {
        String pdb=atom(1,"N",'A',"ALA","A",1,' ',1,"","N")+atom(2,"N",'B',"ALA","A",1,' ',9,"","N")+atom(3,"CA",' ',"ALA","A",1,' ',2,"","C");var selected=Pdb2pqrInput.select(pdb,1,"label","A",100);assertEquals(2,selected.atoms().size());assertEquals(1,selected.alternateAtomsExcluded());assertEquals(1,selected.alternateLabelsCleared());assertEquals("A",selected.atoms().get(0).alt());assertEquals("",PymolStructureInput.parse(selected.pdb(),"pdb",1,"author",100).atoms().get(0).alt());assertThrows(java.io.IOException.class,()->Pdb2pqrInput.select(pdb,1,"error",null,100));assertThrows(java.io.IOException.class,()->Pdb2pqrInput.select(pdb,1,"label","Z",100));
    }
    @Test void segmentCollisionsAndDuplicateSerialsCannotCollapseIntoPqr() {
        for(String pdb:List.of(n(0)+atom(2,"N",' ',"ALA","A",1,' ',1,"SEG2","N"),n(0)+atom(1,"CA",' ',"ALA","A",1,' ',1,"","C")))assertThrows(java.io.IOException.class,()->source(pdb));
    }
    @Test void missingModelAndMalformedCoordinatesFailBeforePreparation() {
        assertThrows(java.io.IOException.class,()->Pdb2pqrInput.select(n(0),2,"error",null,100));assertThrows(java.io.IOException.class,()->source("MODEL        1\n"+n(0)));assertThrows(java.io.IOException.class,()->source("ATOM  bad\n"));
    }
    @Test void insertedResidueIdentityAndNegativeNumbersArePreserved()throws Exception {
        String pdb=atom(1,"N",' ',"ALA","A",-2,'A',0,"","N");var result=audit(pdb,"ATOM 1 N ALA A -2A 0 0 0 .3 2\n",pdb,"prepare","keep","error","error",0);assertEquals(1,result.summary().get("retained_source_atoms"));assertEquals("-2A",result.ledger().get(0).get("residue_id"));
    }
    @Test void chargedZeroRadiusOutputHydrogenRemainsInLedger()throws Exception {
        String input=n(0),prepared=input+atom(2,"H",' ',"ALA","A",1,' ',1,"","H");var result=audit(input,"ATOM 1 N ALA A 1 0 0 0 -.3 2\nATOM 2 H ALA A 1 1 0 0 .3 0\n",prepared,"prepare","keep","error","error",0);assertEquals(1,result.summary().get("output_only_hydrogens"));assertEquals(1L,result.summary().get("zero_radius_atoms"));assertEquals(.3,result.ledger().get(1).get("charge_e"));assertEquals(0.,result.ledger().get(1).get("radius_angstrom"));
    }
    @Test void outputOnlyHeavyAtomsNeedAllowAndNeverReceiveInventedSourceLineage()throws Exception {
        String input=n(0),prepared=input+atom(2,"O",' ',"ALA","A",1,' ',1,"","O"),charged="ATOM 1 N ALA A 1 0 0 0 .3 2\nATOM 2 O ALA A 1 1 0 0 -.3 1.4\n";assertThrows(java.io.IOException.class,()->audit(input,charged,prepared,"prepare","keep","error","error",0));var result=audit(input,charged,prepared,"prepare","keep","allow","error",0);assertEquals(1,result.summary().get("output_only_heavy_atoms"));assertTrue(result.ledger().get(1).get("lineage").toString().contains("repair-or-rename"));assertFalse(result.ledger().get(1).containsKey("input_serial"));
    }
    @Test void explicitOmissionProducesAnIncompleteModelWarningAndLedger()throws Exception {
        String input=n(0)+atom(2,"FE",' ',"HEM","B",2,' ',1,"","FE"),charged="ATOM 1 N ALA A 1 0 0 0 0 2\n";assertThrows(java.io.IOException.class,()->audit(input,charged,input,"prepare","keep","error","error",0));var result=audit(input,charged,input,"prepare","keep","error","omit",0);assertEquals(1,result.summary().get("omitted_or_renamed_source_atoms"));assertTrue(result.warnings().get(0).contains("INCOMPLETE MODEL"));assertEquals(2,result.ledger().size());
    }
    @Test void declaredWaterDropIsDistinctFromMissingParametersEvenInAssignOnly()throws Exception {
        String input=n(0)+atom(2,"O",' ',"HOH","W",9,' ',1,"","O"),charged="ATOM 1 N ALA A 1 0 0 0 0 2\n";var result=audit(input,charged,n(0),"assign-only","drop","error","error",0);assertEquals(1,result.summary().get("explicitly_dropped_water_atoms"));assertEquals(0,result.summary().get("omitted_or_renamed_source_atoms"));assertThrows(java.io.IOException.class,()->audit(input,charged,n(0),"prepare","keep","error","error",0));
    }
    @Test void assignOnlyCannotAddOrRemoveHydrogens() {
        String input=n(0),prepared=input+atom(2,"H",' ',"ALA","A",1,' ',1,"","H"),charged="ATOM 1 N ALA A 1 0 0 0 -.3 2\nATOM 2 H ALA A 1 1 0 0 .3 0\n";assertThrows(java.io.IOException.class,()->audit(input,charged,prepared,"assign-only","keep","error","error",0));assertThrows(java.io.IOException.class,()->audit(prepared,"ATOM 1 N ALA A 1 0 0 0 0 2\n",input,"assign-only","keep","error","error",0));
    }
    @Test void heavyShiftAndIndependentPreparedPdbCoordinatesAreBothAudited() {
        assertThrows(java.io.IOException.class,()->audit(n(0),"ATOM 1 N ALA A 1 .1 0 0 0 2\n",n(.1),"prepare","keep","error","error",.01));assertThrows(java.io.IOException.class,()->audit(n(0),"ATOM 1 N ALA A 1 0 0 0 0 2\n",n(.1),"prepare","keep","error","error",1));
    }
    @Test void protonationRenamingIsReportedButUnrelatedResidueMutationFails()throws Exception {
        String input=atom(1,"N",' ',"HIS","A",1,' ',0,"","N"),prepared=atom(1,"N",' ',"HIP","A",1,' ',0,"","N");var result=audit(input,"ATOM 1 N HIP A 1 0 0 0 1 2\n",prepared,"prepare","keep","error","error",0);assertEquals(1,result.summary().get("retained_atoms_with_changed_residue_name"));assertThrows(java.io.IOException.class,()->audit(n(0),"ATOM 1 N LYS A 1 0 0 0 1 2\n",atom(1,"N",' ',"LYS","A",1,' ',0,"","N"),"prepare","keep","error","error",0));
    }
    @Test void everyChargedAtomNeedsIndependentPreparedPdbIdentityAndElement() {
        assertThrows(java.io.IOException.class,()->audit(n(0),"ATOM 1 N ALA A 1 0 0 0 0 2\nATOM 2 X ALA A 1 1 0 0 0 1\n",n(0),"prepare","keep","allow","error",0));assertThrows(java.io.IOException.class,()->audit(n(0),"ATOM 1 N ALA A 1 0 0 0 0 2\n",atom(1,"N",' ',"ALA","A",1,' ',0,"","C"),"prepare","keep","error","error",0));
    }
    @Test void all8988OriginalAtomRowsMatchIndependentCoordinatesChargesRadiiAndIdentities()throws Exception {
        var properties=new Properties();byte[] archive;try(var in=getClass().getResourceAsStream("/org/jlinalg/protein/pdb2pqr/reference.properties")){assertNotNull(in);properties.load(in);}try(var in=getClass().getResourceAsStream("/org/jlinalg/protein/pdb2pqr/original-preparations.zip")){assertNotNull(in);archive=in.readAllBytes();}assertEquals(properties.getProperty("archive.sha256"),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive)));Map<String,String> files=new HashMap<>();try(var zip=new ZipInputStream(new ByteArrayInputStream(archive))){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())files.put(entry.getName(),new String(zip.readAllBytes(),StandardCharsets.US_ASCII));}
        int cases=0,total=0;for(String name:files.keySet().stream().filter(n->n.endsWith("/prepared.pqr")).sorted().toList()) {
            String id=name.substring(0,name.indexOf('/'));var model=PqrStructure.read(new StringReader(files.get(name)),1000);var numerical=MessageDigest.getInstance("SHA-256");var identities=MessageDigest.getInstance("SHA-256");var bytes=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            for(var atom:model.atoms()){bytes.clear();bytes.putInt(atom.number()).putDouble(atom.coordinate().x()).putDouble(atom.coordinate().y()).putDouble(atom.coordinate().z()).putDouble(atom.charge()).putDouble(atom.radius());numerical.update(bytes.array());String line=String.join("|",atom.hetero()?"HETATM":"ATOM",atom.chain(),Integer.toString(atom.residueNumber())+atom.insertionCode(),atom.residue(),atom.name())+"\n";identities.update(line.getBytes(StandardCharsets.UTF_8));}
            assertEquals(properties.getProperty(id+".numerical_sha256"),HexFormat.of().formatHex(numerical.digest()),id);assertEquals(properties.getProperty(id+".identity_sha256"),HexFormat.of().formatHex(identities.digest()),id);assertEquals(Integer.parseInt(properties.getProperty(id+".atoms")),model.atoms().size());assertEquals(Double.parseDouble(properties.getProperty(id+".charge")),model.totalCharge(),1e-11);assertEquals(Long.parseLong(properties.getProperty(id+".zero_radius")),model.zeroRadiusAtoms());
            var selected=Pdb2pqrInput.select(files.get(id+"/source.pdb"),1,"error",null,1000);var audit=Pdb2pqrInput.audit(selected,model,files.get(id+"/prepared.pdb"),id.contains("assign-only")?"assign-only":"prepare","keep",id.contains("repair-one")?"allow":"error","error",10,1000);assertEquals(model.atoms().size(),audit.summary().get("charged_atoms"));total+=model.atoms().size();cases++;
        }assertEquals(14,cases);assertEquals(8988,total);
    }
}
