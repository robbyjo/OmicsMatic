/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.FollowupSupport.hash;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.PrsCsMath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrsCsAuditsTest {
    @TempDir Path root;
    private PrsCsInputs.Prepared prepare()throws Exception {
        Path data=PrsCsInputsTest.fixture(root),stage=root.resolve("stage");Files.createDirectory(stage);
        return PrsCsInputs.prepare(new PrsCsInputs.Request(data.resolve("beta-se.txt"),data.resolve("target.bim"),data.resolve("ldblk_1kg_eur"),"1kg",List.of(1,22),"beta-se",20000,"exclude-with-warning","allow-upstream-with-warning","allow-complement-with-warning","require-match",PrsCsMath.PValuePolicy.ERROR),new PrsCsInputs.Limits(100,1000,1048576,1048576,4096,10000,100,100,1048576,10000),stage);
    }
    private static Map<?,?> supplied()throws Exception {
        try(var input=PrsCsAuditsTest.class.getResourceAsStream("/org/jlinalg/genetics/prscs-reference/independent-inputs.json")){assertNotNull(input);return (Map<?,?>)SimpleJson.parse(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
    }
    private Path bridge(PrsCsInputs.Prepared input)throws Exception {
        Path work=Files.createTempDirectory(root,"bridge-");StringBuilder blocks=new StringBuilder("chromosome\tblock\tsource_snps\tselected_snps\tsource_dtype\tmatrix_file\tmatrix_sha256\tmatrix_bytes\n"),snps=new StringBuilder("chromosome\tblock\tsource_index\tselected_index\tsnp\n");
        long cells=0,selectedCells=0,sourceSnps=0;
        for(Object value:(List<?>)supplied().get("blocks")) {
            Map<?,?> block=(Map<?,?>)value;int c=((Number)block.get("chromosome")).intValue(),b=((Number)block.get("block")).intValue();List<?> ids=(List<?>)block.get("identifiers"),matrix=(List<?>)block.get("matrix");var indices=new ArrayList<Integer>();
            for(int i=0;i<ids.size();i++)if(input.byId().containsKey(ids.get(i)))indices.add(i);
            int n=indices.size();String filename="chr"+c+"-block"+b+".raw-f64le";ByteBuffer bytes=ByteBuffer.allocate(n*n*8).order(ByteOrder.LITTLE_ENDIAN);
            for(int i:indices)for(int j:indices)bytes.putDouble(((Number)((List<?>)matrix.get(i)).get(j)).doubleValue());Path binary=work.resolve(filename);Files.write(binary,bytes.array());
            blocks.append(c).append('\t').append(b).append('\t').append(ids.size()).append('\t').append(n).append("\t<f8\t").append(filename).append('\t').append(hash(binary)).append('\t').append(bytes.capacity()).append('\n');
            for(int i=0;i<n;i++)snps.append(c).append('\t').append(b).append('\t').append(indices.get(i)).append('\t').append(i).append('\t').append(ids.get(indices.get(i))).append('\n');
            sourceSnps+=ids.size();cells+=(long)ids.size()*ids.size();selectedCells+=(long)n*n;
        }
        Files.writeString(work.resolve("hdf5-blocks.tsv"),blocks);Files.writeString(work.resolve("hdf5-selected-snps.tsv"),snps);
        var entries=new ArrayList<Map<String,Object>>();long sourceBytes=0;
        for(var entry:input.model().entrySet()) {Path source=input.sources().get("reference_chr_"+entry.getKey());sourceBytes+=Files.size(source);entries.add(Map.of("chromosome",entry.getKey(),"selected_snp_entries",entry.getValue().size(),"source_file",source.toString(),"source_sha256",input.sourceHashes().get("reference_chr_"+entry.getKey())));}
        var report=new LinkedHashMap<String,Object>();report.put("status","exported-for-native-validation");report.put("source_bytes",sourceBytes);report.put("source_snp_entries",sourceSnps);report.put("source_ld_cells",cells);report.put("source_blocks",6);report.put("exported_ld_cells",selectedCells);report.put("chromosomes",entries);report.put("block_metadata_file","hdf5-blocks.tsv");report.put("block_metadata_sha256",hash(work.resolve("hdf5-blocks.tsv")));report.put("snp_index_file","hdf5-selected-snps.tsv");report.put("snp_index_sha256",hash(work.resolve("hdf5-selected-snps.tsv")));Files.writeString(work.resolve("hdf5-audit.json"),RegenieRuntime.json(report)+"\n");return work;
    }
    private static void update(Path work,String key,Object value)throws Exception {
        var report=new LinkedHashMap<String,Object>();for(var e:RegenieRuntime.object(work.resolve("hdf5-audit.json")).entrySet())report.put(e.getKey().toString(),e.getValue());report.put(key,value);Files.writeString(work.resolve("hdf5-audit.json"),RegenieRuntime.json(report)+"\n");
    }
    private static void matrix(Path work,double a,double b,boolean symmetric)throws Exception {
        Path file=work.resolve("chr1-block1.raw-f64le");byte[] raw=Files.readAllBytes(file);var bytes=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);int n=(int)Math.sqrt(raw.length/8);bytes.putDouble(0,a);bytes.putDouble(8,b);if(symmetric)bytes.putDouble(8*n,b);Files.write(file,raw);
        Path metadata=work.resolve("hdf5-blocks.tsv");var lines=Files.readAllLines(metadata);String[] row=lines.get(1).split("\t",-1);row[6]=hash(file);lines.set(1,String.join("\t",row));Files.write(metadata,lines);update(work,"block_metadata_sha256",hash(metadata));
    }

    @Test void allSuppliedRawCorrelationCellsAndModelOrderPassWithoutAlleleFlippingOrProjection()throws Exception {
        var input=prepare();Path work=bridge(input);var result=PrsCsAudits.validateHdf5(input,work,1e-6,10000,100000);
        assertEquals(6,result.blocks());assertEquals(28,result.sourceSnps());assertEquals(136,result.sourceCells());assertEquals(74,result.selectedCells());assertEquals(74,result.numericChecks());PrsCsAudits.verifyBridge(result,work);
        Path matrix=work.resolve("chr1-block1.raw-f64le");Files.write(matrix,new byte[Files.readAllBytes(matrix).length]);assertThrows(IOException.class,()->PrsCsAudits.verifyBridge(result,work));
    }

    @Test void forgedMatchingHashesCannotAdmitNonfiniteOutOfRangeNonUnitOrAsymmetricLd()throws Exception {
        var input=prepare();
        for(String mutation:List.of("nonfinite","range","diagonal","asymmetry")) {
            Path work=bridge(input);matrix(work,mutation.equals("diagonal")?.99:1,mutation.equals("nonfinite")?Double.NaN:mutation.equals("range")?1.1:.2,!mutation.equals("asymmetry"));
            assertThrows(IOException.class,()->PrsCsAudits.validateHdf5(input,work,1e-6,10000,100000),mutation);
        }
    }

    @Test void modelPermutationExtraIndicesAndFalseAggregateCountsFailEvenWithUpdatedMetadataHashes()throws Exception {
        var input=prepare();
        for(String mutation:List.of("permutation","extra-index","aggregate","ceiling")) {
            Path work=bridge(input),snps=work.resolve("hdf5-selected-snps.tsv");var lines=Files.readAllLines(snps);
            if(mutation.equals("permutation")){String[] a=lines.get(1).split("\t"),b=lines.get(2).split("\t");String id=a[4];a[4]=b[4];b[4]=id;lines.set(1,String.join("\t",a));lines.set(2,String.join("\t",b));Files.write(snps,lines);update(work,"snp_index_sha256",hash(snps));}
            if(mutation.equals("extra-index")){lines.add(lines.get(1));Files.write(snps,lines);update(work,"snp_index_sha256",hash(snps));}
            if(mutation.equals("aggregate"))update(work,"source_ld_cells",1);
            assertThrows(IOException.class,()->PrsCsAudits.validateHdf5(input,work,1e-6,mutation.equals("ceiling")?10:10000,100000),mutation);
        }
    }

    @Test void marginalAuditRequiresEveryVariantIdentityAlleleFlipFrequencyAndNativeEffect()throws Exception {
        var input=prepare();Path file=root.resolve("original.tsv");var text=new StringBuilder("CHR\tSNP\tBP\tA1\tA2\tMAF\tBETA\tFLP\n");
        for(var list:input.model().values())for(var v:list)text.append(v.chromosome()).append('\t').append(v.id()).append('\t').append(v.position()).append('\t').append(v.effectAllele()).append('\t').append(v.otherAllele()).append('\t').append(v.frequency()).append('\t').append(v.marginal()).append('\t').append(v.referenceSign()).append('\n');
        Files.writeString(file,text);PrsCsAudits.validateParser(input,file);String original=text.toString();
        for(String mutated:List.of(original.replaceFirst("rs1_1","wrong"),original.replaceFirst("0.915","0.5"),original.replaceFirst("\\t-1\\n","\t1\n"),original+original.substring(original.indexOf('\n')+1))) {Files.writeString(file,mutated);assertThrows(IOException.class,()->PrsCsAudits.validateParser(input,file));}
    }

    @Test void numericLdChecksDoNotClaimGeneralPositiveSemidefiniteCertification()throws Exception {
        var input=prepare();Path work=bridge(input);Path file=work.resolve("chr1-block1.raw-f64le");byte[] raw=Files.readAllBytes(file);var bytes=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);int n=(int)Math.sqrt(raw.length/8);
        for(int i=0;i<3;i++)for(int j=0;j<3;j++)bytes.putDouble(8*(i*n+j),i==j?1:i+j==3?-.9:.9);Files.write(file,raw);
        Path metadata=work.resolve("hdf5-blocks.tsv");var lines=Files.readAllLines(metadata);String[] row=lines.get(1).split("\t",-1);row[6]=hash(file);lines.set(1,String.join("\t",row));Files.write(metadata,lines);update(work,"block_metadata_sha256",hash(metadata));
        assertEquals(74,PrsCsAudits.validateHdf5(input,work,1e-6,10000,100000).numericChecks());
    }
}
