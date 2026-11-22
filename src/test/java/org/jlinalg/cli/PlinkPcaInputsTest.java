/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlinkPcaInputsTest {
    @TempDir Path directory;
    private record Fixture(Path prefix,Path samples,Path panel) { }
    private static final PlinkGlmInputs.Bounds BOUNDS=new PlinkGlmInputs.Bounds(200,100,20000,1048576,16L*1048576,16384,1048576,1048576);
    private static String resource(String folder,String name)throws IOException {
        try(var stream=PlinkPcaInputsTest.class.getResourceAsStream("/org/jlinalg/"+folder+"/"+name)) {
            if(stream==null)throw new IOException("Missing PCA synthetic fixture "+name);
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private Fixture fixture(String name)throws Exception {
        Path folder=directory.resolve(name);Files.createDirectory(folder);Path prefix=folder.resolve("known");
        for(String suffix:List.of("bed","bim","fam"))try(var input=PlinkPcaInputsTest.class.getResourceAsStream("/org/jlinalg/genetics/plink-pruning/known."+suffix)) {
            assertNotNull(input);Files.copy(input,Path.of(prefix+"."+suffix));
        }
        var included=new LinkedHashSet<PlinkQcInputs.Id>();
        for(String line:resource("genetics/plink-pruning","samples.tsv").lines().skip(1).toList()) {
            String[] row=line.split("\t");if(row[2].equals("yes"))included.add(new PlinkQcInputs.Id(row[0],row[1]));
        }
        var rows=new ArrayList<String>();rows.add("fid\tiid\tfit\tfrequency_reference");
        for(String line:Files.readAllLines(Path.of(prefix+".fam"))) {
            String[] row=line.split("\\s+");boolean fit=included.contains(new PlinkQcInputs.Id(row[0],row[1]));
            rows.add(row[0]+"\t"+row[1]+"\t"+(fit?"yes":"no")+"\t"+(fit&&row[2].equals("0")&&row[3].equals("0")?"yes":"no"));
        }
        Path samples=folder.resolve("samples.tsv"),panel=folder.resolve("panel.txt");Files.write(samples,rows,StandardCharsets.UTF_8);
        Files.writeString(panel,resource("genetics/plink-pruning","plink2-count-legacy.in"),StandardCharsets.UTF_8);return new Fixture(prefix,samples,panel);
    }
    private PlinkPcaInputs.Prepared prepare(Fixture f,String name,String missing,int minimum)throws Exception {
        Path out=directory.resolve(name);Files.createDirectory(out);return PlinkPcaInputs.prepare(f.prefix(),f.samples(),f.panel(),out,"error",missing,minimum,BOUNDS);
    }

    @Test void independentExplicitMasksAndCountsMatchDecodedReferencePanel()throws Exception {
        var f=fixture("input");var p=prepare(f,"prepared","upstream",2);
        assertEquals(128,p.bed().samples().size());assertEquals(125,p.basis().size());assertEquals(123,p.reference().size());assertEquals(87,p.panel().size());
        assertEquals(2,p.basis().stream().filter(id->!p.bed().samples().get(id).founder()).count());
        List<String> markerRows=resource("genetics/genetic-pca","markers.tsv").lines().skip(1).toList();
        List<String> sampleRows=resource("genetics/genetic-pca","samples.tsv").lines().skip(1).toList();
        List<String> dosageRows=resource("genetics/genetic-pca","dosages.tsv").lines().toList();
        var originalToStaged=new java.util.LinkedHashMap<String,String>();for(String id:p.panel())originalToStaged.put(p.bed().variants().get(id).original(),id);
        for(int v=0;v<markerRows.size();v++) {
            String original=markerRows.get(v).split("\t")[0];String[] calls=dosageRows.get(v).split("\t");int called=0,basisCalled=0;long alleles=0;
            for(int i=0;i<calls.length;i++)if(!calls[i].equals("NA")){basisCalled++;if(sampleRows.get(i).split("\t")[2].equals("1")){called++;alleles+=Long.parseLong(calls[i]);}}
            var counts=p.calls().get(originalToStaged.get(original));assertEquals(called,counts.referenceCalled());assertEquals(alleles,counts.countedAlleles());assertEquals(basisCalled,counts.basisCalled());
            assertEquals(alleles/(2.*called),counts.frequency());
        }
        assertEquals(p.panel(),PlinkPcaInputs.variants(p).keySet());PlinkPcaInputs.verify(p,directory.resolve("prepared"));
    }

    @Test void frequencyReferenceMayIncludeSamplesOutsideFittedBasis()throws Exception {
        var f=fixture("input");var rows=new ArrayList<>(Files.readAllLines(f.samples()));int selected=0;
        for(int i=1;i<rows.size();i++){String[] row=rows.get(i).split("\t");if(row[2].equals("yes")){row[3]="yes";row[2]=selected++<70?"yes":"no";}rows.set(i,String.join("\t",row));}
        Files.write(f.samples(),rows);var p=prepare(f,"prepared","upstream",2);
        assertEquals(70,p.basis().size());assertEquals(125,p.reference().size());assertEquals(55,p.reference().stream().filter(id->!p.basis().contains(id)).count());
    }

    @Test void malformedUnknownDuplicateOrIncompleteSampleTablesFail()throws Exception {
        var f=fixture("input");String original=Files.readString(f.samples());var lines=original.lines().toList();int attempt=0;
        for(String content:List.of(original.replaceFirst("frequency_reference","reference"),original.replaceFirst("yes","true"),
                String.join("\n",lines.subList(0,lines.size()-1))+"\n",original+lines.get(1)+"\n",original+"UNKNOWN\tPAIR\tyes\tyes\n")) {
            Files.writeString(f.samples(),content);String out="invalid-"+attempt++;assertThrows(IOException.class,()->prepare(f,out,"upstream",2));
        }
    }

    @Test void explicitMinimumEmptyReferenceAndMissingPolicyAreEnforced()throws Exception {
        var f=fixture("input");assertThrows(IOException.class,()->prepare(f,"few-reference","upstream",124));
        assertThrows(IOException.class,()->prepare(f,"missing-error","error",2));
        assertTrue(Files.readString(directory.resolve("missing-error/variant-reference.tsv")).contains("MISSING_CALLS_PROHIBITED"));
        var rows=new ArrayList<>(Files.readAllLines(f.samples()));for(int i=1;i<rows.size();i++){String[] row=rows.get(i).split("\t");row[3]="no";rows.set(i,String.join("\t",row));}Files.write(f.samples(),rows);
        assertThrows(IOException.class,()->prepare(f,"empty-reference","upstream",2));
    }

    @Test void panelRejectsUnknownDuplicateBlankAndNonautosomalMarkers()throws Exception {
        var f=fixture("input");String original=Files.readString(f.panel());int attempt=0;
        for(String content:List.of("",original+original.lines().findFirst().orElseThrow()+"\n",original+"UNKNOWN\n",original+"\n")) {
            Files.writeString(f.panel(),content);String out="invalid-panel-"+attempt++;assertThrows(IOException.class,()->prepare(f,out,"upstream",2));
        }
        Files.writeString(f.panel(),original);Path bim=Path.of(f.prefix()+".bim");var rows=new ArrayList<>(Files.readAllLines(bim));String target=original.lines().findFirst().orElseThrow();
        for(int i=0;i<rows.size();i++){String[] row=rows.get(i).split("\\s+");if(row[1].equals(target)){row[0]="X";rows.set(i,String.join("\t",row));}}
        Files.write(bim,rows);assertThrows(IOException.class,()->prepare(f,"nonautosomal","upstream",2));
    }

    @Test void exactSourceAndStagedMaskIntegrityIsRechecked()throws Exception {
        var f=fixture("input");var p=prepare(f,"prepared","upstream",2);Path output=directory.resolve("prepared"),keep=output.resolve("pca-basis.keep");String prior=Files.readString(keep);
        Files.writeString(keep,prior+"F999\tI999\n");assertThrows(IOException.class,()->PlinkPcaInputs.verify(p,output));Files.writeString(keep,prior);PlinkPcaInputs.verify(p,output);
        Files.writeString(f.panel(),Files.readString(f.panel())+"UNKNOWN\n");assertThrows(IOException.class,()->PlinkPcaInputs.verify(p,output));
    }
}
