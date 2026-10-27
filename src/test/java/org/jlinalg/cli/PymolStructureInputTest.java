/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PymolStructureInputTest {
    private static String line(int serial,String name,String alt,int residue,String insertion,double x,String element) {
        char[] text=new char[80];Arrays.fill(text,' ');put(text,0,"ATOM  ");put(text,6,String.format(java.util.Locale.ROOT,"%5d",serial));put(text,12,String.format(java.util.Locale.ROOT,"%-4s",name));put(text,16,alt);put(text,17,"ALA");put(text,21,"A");put(text,22,String.format(java.util.Locale.ROOT,"%4d",residue));put(text,26,insertion);put(text,30,String.format(java.util.Locale.ROOT,"%8.3f%8.3f%8.3f%6.2f%6.2f",x,2.,3.,.5,10.));put(text,76,String.format(java.util.Locale.ROOT,"%2s",element));return new String(text)+"\n";
    }
    private static void put(char[] text,int start,String value){value.getChars(0,value.length(),text,start);}
    private static final String CIF="""
        data_example
        _entry.id 'loop_'
        loop_
        _atom_site.group_PDB
        _atom_site.id
        _atom_site.type_symbol
        _atom_site.label_atom_id
        _atom_site.label_alt_id
        _atom_site.label_comp_id
        _atom_site.label_asym_id
        _atom_site.label_seq_id
        _atom_site.pdbx_PDB_ins_code
        _atom_site.Cartn_x
        _atom_site.Cartn_y
        _atom_site.Cartn_z
        _atom_site.occupancy
        _atom_site.B_iso_or_equiv
        _atom_site.auth_atom_id
        _atom_site.auth_comp_id
        _atom_site.auth_asym_id
        _atom_site.auth_seq_id
        _atom_site.pdbx_PDB_model_num
        ATOM 11 C CA A GLY X1 7 B 1 2 3 .5 23.4 CB ALA 'author A' 42 9
        ATOM 12 N N . GLY X1 8 ? 11 12 13 1 0 N ALA 'author A' 43 5
        #
        """;
    @Test void pdbModelsUseInputOrdinalAndRetainSerialInsertionAndAlt()throws Exception {
        var parsed=PymolStructureInput.parse("MODEL        5\n"+line(9,"CA","A",-2,"B",1,"C")+"ENDMDL\nMODEL        9\n"+line(17,"CA","",42,"",11,"C")+"ENDMDL\n","pdb",2,"author",10);
        assertEquals(List.of("5","9"),parsed.modelLabels());assertEquals(17,parsed.atoms().get(0).id());assertEquals("42",parsed.atoms().get(0).resi());assertEquals(11,parsed.atoms().get(0).point().x());
        var first=PymolStructureInput.parse("MODEL        5\n"+line(9,"CA","A",-2,"B",1,"C")+"ENDMDL\n","pdb",1,"author",10).atoms().get(0);assertEquals("-2B",first.resi());assertEquals("A",first.alt());assertEquals(.5,first.q());
    }
    @Test void alternateConformersAreNeverCollapsed()throws Exception {
        var parsed=PymolStructureInput.parse(line(1,"CA","A",1,"",1,"C")+line(2,"CA","B",1,"",2,"C"),"pdb",1,"author",10);
        assertEquals(2,parsed.atoms().size());assertEquals(List.of("A","B"),parsed.atoms().stream().map(PymolStructureInput.Atom::alt).toList());
    }
    @Test void duplicateChemicalIdentitiesAndMissingElementsFail() {
        String atom=line(1,"CA","",1,"",1,"C");
        assertThrows(IOException.class,()->PymolStructureInput.parse(atom+atom,"pdb",1,"author",10));
        assertThrows(IOException.class,()->PymolStructureInput.parse(line(1,"CA","",1,"",1,""),"pdb",1,"author",10));
    }
    @Test void pdbMixedNestedMissingAndUnterminatedModelsFail() {
        String atom=line(1,"CA","",1,"",1,"C");
        for(String bad:List.of(atom+"MODEL        2\n"+atom+"ENDMDL\n","MODEL        1\nMODEL        2\n"+atom+"ENDMDL\n","MODEL        1\n"+atom,"ENDMDL\n"+atom))assertThrows(IOException.class,()->PymolStructureInput.parse(bad,"pdb",1,"author",10));
        assertThrows(IOException.class,()->PymolStructureInput.parse(atom,"pdb",2,"author",10));
    }
    @Test void cifNamespacesAndModelLabelsRemainDistinct()throws Exception {
        var author=PymolStructureInput.parse(CIF,"cif",1,"author",10);var a=author.atoms().get(0);assertEquals(List.of("9","5"),author.modelLabels());assertEquals("CB",a.name());assertEquals("ALA",a.resn());assertEquals("42B",a.resi());assertEquals("author A",a.chain());assertEquals("X1",a.segi());assertEquals("A",a.alt());
        var label=PymolStructureInput.parse(CIF,"cif",1,"label",10).atoms().get(0);assertEquals("CA",label.name());assertEquals("GLY",label.resn());assertEquals("7B",label.resi());assertEquals("X1",label.chain());
        assertEquals(11,PymolStructureInput.parse(CIF,"cif",2,"author",10).atoms().get(0).point().x());
    }
    @Test void cifAbsentAuthorColumnsUseLabelButMissingAuthorCellsFail()throws Exception {
        String without=CIF.replace("_atom_site.auth_atom_id\n","").replace("CB ALA 'author A' 42 9","ALA 'author A' 42 9").replace("N ALA 'author A' 43 5","ALA 'author A' 43 5");
        assertEquals("CA",PymolStructureInput.parse(without,"cif",1,"author",10).atoms().get(0).name());
        assertThrows(IOException.class,()->PymolStructureInput.parse(CIF.replace("CB ALA", "? ALA"),"cif",1,"author",10));
    }
    @Test void cifMalformedLoopsAndNonfiniteCoordinatesFail() {
        for(String bad:List.of(CIF.replace("_atom_site.id\n","_atom_site.id\n_atom_site.id\n"),CIF.replace(" 43 5"," 43"),CIF.replace(" 1 2 3 .5"," NaN 2 3 .5"),CIF+"loop_\n_atom_site.id\n1\n",CIF.replace("'author A'", "'unterminated")))assertThrows(IOException.class,()->PymolStructureInput.parse(bad,"cif",1,"author",10));
    }
    @Test void atomBoundsAndUndefinedSelectedStatesFail() {
        assertThrows(IOException.class,()->PymolStructureInput.parse(line(1,"CA","",1,"",1,"C")+line(2,"N","",1,"",1,"N"),"pdb",1,"author",1));
        assertThrows(IOException.class,()->PymolStructureInput.parse(CIF,"cif",3,"author",10));
        assertThrows(IOException.class,()->PymolStructureInput.parse("data_empty\n","cif",1,"author",10));
    }
    @Test void boundsIncludeUnselectedStatesBeforeOriginalLoading() {
        String atom=line(1,"CA","",1,"",1,"C");String text="MODEL        5\n"+atom+"ENDMDL\nMODEL        9\n"+atom+"ENDMDL\n";
        assertThrows(IOException.class,()->PymolStructureInput.parse(text,"pdb",1,"author",1));assertThrows(IOException.class,()->PymolStructureInput.parse(CIF,"cif",1,"author",1));
    }
}
