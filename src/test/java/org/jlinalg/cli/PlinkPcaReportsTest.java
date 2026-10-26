/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlinkPcaReportsTest {
    @TempDir Path directory;
    private static final PlinkPcaReports.Limits LIMITS=new PlinkPcaReports.Limits(200,100,10,16L*1048576,16L*1048576,16384);
    private static final String CASE="plink2-pca-all-mean";
    private static String resource(String name)throws IOException {
        try(var input=PlinkPcaReportsTest.class.getResourceAsStream("/org/jlinalg/cli/plink-pca-reports/"+name)) {
            if(input==null)throw new IOException("Missing synthetic PCA report fixture "+name);
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private Path file(String name,String content)throws IOException {Path p=directory.resolve(name);Files.writeString(p,content,StandardCharsets.UTF_8);return p;}
    private Path copy(String name)throws IOException {return file(name,resource(name));}
    private static Set<PlinkQcInputs.Id> samples()throws IOException {
        var result=new LinkedHashSet<PlinkQcInputs.Id>();for(String line:resource("samples.keep").lines().toList()){String[] row=line.split("\\s+");result.add(new PlinkQcInputs.Id(row[0],row[1]));}return result;
    }
    private static Map<String,PlinkPcaReports.Variant> variants()throws IOException {
        var result=new LinkedHashMap<String,PlinkPcaReports.Variant>();for(String line:resource("markers.tsv").lines().skip(1).toList()){String[] row=line.split("\t");result.put(row[0],new PlinkPcaReports.Variant(row[0],row[1],row[2],row[3]));}return result;
    }
    private PlinkPcaReports.Basis basis(Path values,Path vectors)throws IOException {return PlinkPcaReports.basis(values,vectors,samples(),5,LIMITS);}

    @Test void tenActualExactAndApproximateReportsPreserveRawValuesAndOrientation()throws Exception {
        int cases=0,totalSamples=0;long totalWeights=0;
        for(String line:resource("cases.tsv").lines().skip(1).toList()) {
            String[] row=line.split("\t",-1);String name=row[0],engine=row[1];
            var basis=basis(copy(name+".eigenval"),copy(name+".eigenvec"));
            assertEquals(125,basis.samples().size());assertEquals(5,basis.eigenvalues().size());
            assertEquals(resource(name+".eigenval").lines().toList(),basis.eigenvalues().stream().map(PlinkPcaReports.Number::literal).toList());
            assertTrue(basis.maximumOrthogonalityError()<2e-6);
            var weights=new ArrayList<PlinkPcaReports.Weight>();String suffix=engine.equals("plink1")?"eigenvec.var":"eigenvec.allele";
            var counts=PlinkPcaReports.weights(copy(name+"."+suffix),engine,variants(),5,LIMITS,weights::add);
            assertEquals(87,counts.variants());assertEquals(engine.equals("plink1")?87:174,counts.rows());assertEquals(counts.rows(),weights.size());
            List<String> originals=resource(name+"."+suffix).lines().skip(1).toList();
            for(int i=0;i<weights.size();i++) {
                var weight=weights.get(i);String[] raw=originals.get(i).strip().split("\\s+");
                assertEquals(raw[1],weight.variant());assertEquals(raw[2],weight.firstAllele());assertEquals(raw[3],weight.secondAllele());
                assertEquals(raw[engine.equals("plink1")?2:4],weight.countedAllele());
                assertEquals(Arrays.asList(raw).subList(engine.equals("plink1")?4:5,raw.length),weight.components().stream().map(PlinkPcaReports.Number::literal).toList());
            }
            cases++;totalSamples+=basis.samples().size();totalWeights+=counts.rows();
        }
        assertEquals(10,cases);assertEquals(1250,totalSamples);assertEquals(1566,totalWeights);
    }

    @Test void boundsRejectOversizedCoordinateAndStreamingRowRequests()throws Exception {
        Path values=copy(CASE+".eigenval"),vectors=copy(CASE+".eigenvec"),weights=copy(CASE+".eigenvec.allele");
        for(var limits:List.of(new PlinkPcaReports.Limits(124,100,10,16000000,16000000,16384),
                new PlinkPcaReports.Limits(200,100,4,16000000,16000000,16384),
                new PlinkPcaReports.Limits(200,100,10,1,16000000,16384),
                new PlinkPcaReports.Limits(200,100,10,16000000,1,16384)))
            assertThrows(IOException.class,()->PlinkPcaReports.basis(values,vectors,samples(),5,limits));
        assertThrows(IOException.class,()->PlinkPcaReports.weights(weights,"plink2",variants(),5,new PlinkPcaReports.Limits(200,86,10,16000000,16000000,16384),w->{ }));
        assertThrows(IOException.class,()->PlinkPcaReports.weights(weights,"plink2",variants(),5,new PlinkPcaReports.Limits(200,100,10,1,16000000,16384),w->{ }));
        assertThrows(IOException.class,()->PlinkPcaReports.weights(weights,"other",variants(),5,LIMITS,w->{ }));
    }

    @Test void rejectsMissingDuplicateUnknownOrMalformedSampleRows()throws Exception {
        Path values=copy(CASE+".eigenval");var original=resource(CASE+".eigenvec").lines().toList();
        for(String content:List.of(String.join("\n",original.subList(0,original.size()-1))+"\n",
                String.join("\n",original)+"\n"+original.get(1)+"\n",
                String.join("\n",original).replaceFirst("FID","ID"),
                String.join("\n",original).replaceFirst("(?m)^"+original.get(1).strip().split("\\s+")[0]+"\\s+","UNDECLARED "),
                String.join("\n",original)+"\n\n"))
            assertThrows(IOException.class,()->basis(values,file("invalid.eigenvec",content)));
    }

    @Test void nonpositiveMissingNonfiniteUnresolvedOrUnorderedEigenvaluesFail()throws Exception {
        Path vectors=copy(CASE+".eigenvec");var original=resource(CASE+".eigenval").lines().toList();
        for(String bad:List.of("0","-1","NaN","Infinity","1e-999","1.234567","999999")) {
            var rows=new ArrayList<>(original);rows.set(2,bad);
            assertThrows(IOException.class,()->basis(file("invalid.eigenval",String.join("\n",rows)+"\n"),vectors));
        }
        assertThrows(IOException.class,()->basis(file("few.eigenval",String.join("\n",original.subList(0,4))+"\n"),vectors));
        assertThrows(IOException.class,()->basis(file("extra.eigenval",String.join("\n",original)+"\n1\n"),vectors));
    }

    @Test void orthogonalityUsesPrintedPrecisionAndRejectsRescaledVectors()throws Exception {
        var rows=new ArrayList<String>();var original=resource(CASE+".eigenvec").lines().toList();rows.add(original.get(0));
        for(String line:original.subList(1,original.size())){String[] fields=line.strip().split("\\s+");fields[2]=String.format(Locale.ROOT,"%.6g",2*Double.parseDouble(fields[2]));rows.add(String.join("\t",fields));}
        var failure=assertThrows(IOException.class,()->basis(copy(CASE+".eigenval"),file("scaled.eigenvec",String.join("\n",rows)+"\n")));
        assertTrue(failure.getMessage().contains("orthonormal"));
    }

    @Test void weightsRejectWrongPanelOrderAllelesHeadersAndIncompleteFamilies()throws Exception {
        String original=resource(CASE+".eigenvec.allele");var lines=original.lines().toList();
        var reversed=new LinkedHashMap<String,PlinkPcaReports.Variant>();var entries=new ArrayList<>(variants().entrySet());java.util.Collections.reverse(entries);entries.forEach(e->reversed.put(e.getKey(),e.getValue()));
        assertThrows(IOException.class,()->PlinkPcaReports.weights(copy(CASE+".eigenvec.allele"),"plink2",reversed,5,LIMITS,w->{ }));
        for(String content:List.of(original.replaceFirst("REF","A2"),String.join("\n",lines.subList(0,lines.size()-1))+"\n",original+lines.get(1)+"\n"))
            assertThrows(IOException.class,()->PlinkPcaReports.weights(file("invalid.weights",content),"plink2",variants(),5,LIMITS,w->{ }));
        for(int column:new int[]{1,2,3,4}) {
            var changed=new ArrayList<>(lines);String[] row=changed.get(1).strip().split("\\s+");row[column]="UNKNOWN";changed.set(1,String.join("\t",row));
            assertThrows(IOException.class,()->PlinkPcaReports.weights(file("wrong.weights",String.join("\n",changed)+"\n"),"plink2",variants(),5,LIMITS,w->{ }));
        }
        var duplicate=new ArrayList<>(lines);duplicate.set(2,duplicate.get(1));
        assertThrows(IOException.class,()->PlinkPcaReports.weights(file("duplicate.weights",String.join("\n",duplicate)+"\n"),"plink2",variants(),5,LIMITS,w->{ }));
    }

    @Test void weightsRejectNonfiniteOrInventedPrecisionAndPropagateConsumerFailure()throws Exception {
        var original=resource(CASE+".eigenvec.allele").lines().toList();
        for(String bad:List.of("NA","NaN","Infinity","1e-999","1.234567")) {
            var changed=new ArrayList<>(original);String[] row=changed.get(1).strip().split("\\s+");row[5]=bad;changed.set(1,String.join("\t",row));
            assertThrows(IOException.class,()->PlinkPcaReports.weights(file("invalid.weights",String.join("\n",changed)+"\n"),"plink2",variants(),5,LIMITS,w->{ }));
        }
        var failure=assertThrows(IOException.class,()->PlinkPcaReports.weights(copy(CASE+".eigenvec.allele"),"plink2",variants(),5,LIMITS,w->{throw new IOException("output publication failed");}));
        assertEquals("output publication failed",failure.getMessage());
    }

    @Test void identitiesUseExactFamilyIndividualPairsAndResultsAreImmutable()throws Exception {
        var model=basis(copy(CASE+".eigenval"),copy(CASE+".eigenvec"));
        assertThrows(UnsupportedOperationException.class,()->model.samples().clear());
        assertThrows(UnsupportedOperationException.class,()->model.eigenvalues().clear());
        assertThrows(UnsupportedOperationException.class,()->model.samples().values().iterator().next().components().clear());
        var changed=new LinkedHashSet<>(samples());var first=changed.iterator().next();changed.remove(first);changed.add(new PlinkQcInputs.Id(first.fid()+"_"+first.iid(),"different"));
        assertThrows(IOException.class,()->PlinkPcaReports.basis(copy(CASE+".eigenval"),copy(CASE+".eigenvec"),changed,5,LIMITS));
    }
}
