/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.hash;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Independent Java validation around read-only HDF5 and original-parser bridges. */
final class PrsCsAudits {
    private PrsCsAudits() { }
    record Result(long blocks,long sourceSnps,long sourceCells,long selectedCells,long numericChecks,
            long estimatedWorkBytes,Map<String,String> bridgeHashes) { }

    static void originalParser(PrsCsRuntime runtime,PrsCsInputs.Prepared input,Path stage,Path work,int timeout,int threads)throws Exception {
        Path script=resource("prscs_parse_audit.py",work);
        var request=new LinkedHashMap<String,Object>();request.put("source",runtime.sourceDirectory().toString());request.put("reference_info",input.sources().get("reference_info").toString());
        request.put("target_prefix",stage.resolve("target").toString());request.put("summary",stage.resolve("summary.txt").toString());request.put("sample_size",input.request().sampleSize());request.put("chromosomes",input.request().chromosomes());
        Path settings=work.resolve("original-parser-request.json"),output=work.resolve("original-marginals.tsv");Files.writeString(settings,RegenieRuntime.json(request)+"\n");
        var command=runtime.flags(work,"original-parser");command.addAll(List.of(script.toString(),settings.toString(),output.toString()));runtime.execute(command,work,"original-parser",timeout,threads);
        validateParser(input,output);PrsCsInputs.verify(input,stage);
    }
    static void validateParser(PrsCsInputs.Prepared input,Path output)throws Exception {
        if(Files.size(output)>input.limits().reportBytes())throw new IOException("Original parser audit exceeds report-byte ceiling");
        try(var stream=new BufferedInputStream(Files.newInputStream(output))) {
            if(!"CHR\tSNP\tBP\tA1\tA2\tMAF\tBETA\tFLP".equals(line(stream,input)))throw new IOException("Original parser audit header differs");
            for(var variants:input.model().values())for(var v:variants) {
                String[] row=fields(line(stream,input),8,"original parser");
                if(!row[0].equals(Integer.toString(v.chromosome()))||!row[1].equals(v.id())||!row[2].equals(Integer.toString(v.position()))
                        ||!row[3].equals(Character.toString(v.effectAllele()))||!row[4].equals(Character.toString(v.otherAllele()))||!row[7].equals(Integer.toString(v.referenceSign())))
                    throw new IOException("Original parser model identity/order/allele/LD orientation differs at "+v.id());
                close(number(row[5]),v.frequency(),"reference effect frequency "+v.id());close(number(row[6]),v.marginal(),"standardized marginal "+v.id());
            }
            if(line(stream,input)!=null)throw new IOException("Original parser returned extra model rows");
        }
    }

    static Result hdf5(PrsCsRuntime runtime,PrsCsInputs.Prepared input,Path work,double tolerance,int timeout,int threads)throws Exception {
        if(!Double.isFinite(tolerance)||tolerance<0||tolerance>.01)throw new IllegalArgumentException("Require LD audit tolerance in [0,0.01]");
        Path script=resource("prscs_hdf5_audit.py",work);var wanted=new LinkedHashMap<String,Object>();long nameBytes=0;
        for(var e:input.model().entrySet()) {wanted.put(e.getKey().toString(),e.getValue().stream().map(PrsCsInputs.Variant::id).toList());for(var v:e.getValue())nameBytes=Math.addExact(nameBytes,128L+4L*v.id().length());}
        var limits=input.limits();
        // The original parser materializes all full LD blocks on a chromosome,
        // then selected blocks and SVD temporaries. Count the complete requested
        // family conservatively; do not budget just the largest selected block.
        long metadataBound=Math.addExact(nameBytes,Math.addExact(4096L*input.model().size(),256L*limits.blocks()));
        long reportAvailable=limits.reportBytes()-input.ledgerBytes()-metadataBound;
        long workAvailable=limits.retainedBytes()-input.estimatedRetained()-256L*limits.variants();
        long cells=Math.min(limits.ldCells(),Math.min(reportAvailable/8,workAvailable/64));
        if(cells<1)throw new IOException("No LD bridge work/storage allowance remains;review explicit retained/report ceilings or request fewer chromosomes");
        var request=new LinkedHashMap<String,Object>();request.put("reference_dir",input.request().reference().toAbsolutePath().normalize().toString());request.put("kind",input.request().kind());
        request.put("chromosomes",input.request().chromosomes());request.put("wanted",wanted);request.put("output_dir",work.toAbsolutePath().toString());request.put("maximum_source_bytes",limits.inputBytes());request.put("maximum_ld_cells",cells);
        request.put("maximum_block_snps",limits.blockSnps());request.put("maximum_blocks",limits.blocks());request.put("maximum_variants",limits.variants());
        Path settings=work.resolve("hdf5-request.json"),output=work.resolve("hdf5-audit.json");String json=RegenieRuntime.json(request)+"\n";
        if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>128*1048576)throw new IOException("HDF5 candidate request exceeds 128MiB bridge bound");Files.writeString(settings,json);
        var command=runtime.flags(work,"hdf5");command.addAll(List.of(script.toString(),settings.toString(),output.toString()));runtime.execute(command,work,"hdf5",timeout,threads);
        return validateHdf5(input,work,tolerance,cells,metadataBound);
    }

    static Result validateHdf5(PrsCsInputs.Prepared input,Path work,double tolerance,long admittedCells,long metadataBound)throws Exception {
        if(!Double.isFinite(tolerance)||tolerance<0||tolerance>.01)throw new IllegalArgumentException("Require LD audit tolerance in [0,0.01]");
        var limits=input.limits();Path report=work.resolve("hdf5-audit.json");Map<?,?> object=RegenieRuntime.object(report);
        if(!"exported-for-native-validation".equals(object.get("status")))throw new IOException("HDF5 bridge status differs");
        Path blocks=work.resolve("hdf5-blocks.tsv"),snps=work.resolve("hdf5-selected-snps.tsv");
        if(!"hdf5-blocks.tsv".equals(object.get("block_metadata_file"))||!"hdf5-selected-snps.tsv".equals(object.get("snp_index_file"))
                ||!hash(blocks).equals(object.get("block_metadata_sha256"))||!hash(snps).equals(object.get("snp_index_sha256")))throw new IOException("HDF5 metadata identity differs");
        if(Files.size(blocks)+Files.size(snps)+Files.size(report)>metadataBound)throw new IOException("HDF5 metadata exceeds its preflight bound");
        var hashes=new LinkedHashMap<String,String>();hashes.put("hdf5-audit.json",hash(report));hashes.put("hdf5-blocks.tsv",hash(blocks));hashes.put("hdf5-selected-snps.tsv",hash(snps));
        long sourceBytes=0;Object chromosomeValue=object.get("chromosomes");
        if(!(chromosomeValue instanceof List<?> entries)||entries.size()!=input.model().size())throw new IOException("HDF5 chromosome report family differs");
        int ci=0;
        for(var e:input.model().entrySet()) {
            if(!(entries.get(ci++) instanceof Map<?,?> entry)||integer(entry.get("chromosome"))!=e.getKey()
                    ||integer(entry.get("selected_snp_entries"))!=e.getValue().size())throw new IOException("HDF5 chromosome/selection identity differs");
            Path source=input.sources().get("reference_chr_"+e.getKey());
            if(!source.toAbsolutePath().normalize().toString().equals(entry.get("source_file"))||!input.sourceHashes().get("reference_chr_"+e.getKey()).equals(entry.get("source_sha256")))throw new IOException("HDF5 source report differs from native input hash");
            sourceBytes=Math.addExact(sourceBytes,Files.size(source));
        }
        var blockCount=new LinkedHashMap<Integer,Integer>();var cursors=new LinkedHashMap<Integer,Integer>();for(int c:input.model().keySet()){blockCount.put(c,0);cursors.put(c,0);}
        long totalBlocks=0,sourceSnps=0,sourceCells=0,selectedCells=0,numericChecks=0;int chromosomeOrdinal=0;List<Integer> chromosomeOrder=List.copyOf(input.model().keySet());
        Path checked=work.resolve("ld-validation.tsv");
        try(var blockStream=new BufferedInputStream(Files.newInputStream(blocks));var snpStream=new BufferedInputStream(Files.newInputStream(snps));var out=Files.newBufferedWriter(checked)) {
            if(!"chromosome\tblock\tsource_snps\tselected_snps\tsource_dtype\tmatrix_file\tmatrix_sha256\tmatrix_bytes".equals(line(blockStream,input))||!"chromosome\tblock\tsource_index\tselected_index\tsnp".equals(line(snpStream,input)))throw new IOException("HDF5 metadata headers differ");
            out.write("chromosome\tblock\tsource_snps\tselected_snps\tchecked_cells\tmaximum_absolute_value\tmaximum_diagonal_error\tmaximum_asymmetry\ttolerance\n");
            for(String text;(text=line(blockStream,input))!=null;) {
                String[] row=fields(text,8,"HDF5 block");int c=Integer.parseInt(row[0]),b=Integer.parseInt(row[1]),source=Integer.parseInt(row[2]),selected=Integer.parseInt(row[3]);
                if(!blockCount.containsKey(c)||b!=blockCount.get(c)+1||source<0||source>limits.blockSnps()||selected<0||selected>source)throw new IOException("HDF5 block sequence/dimensions differ");
                if(c!=chromosomeOrder.get(chromosomeOrdinal)) {if(chromosomeOrdinal+1>=chromosomeOrder.size()||c!=chromosomeOrder.get(++chromosomeOrdinal))throw new IOException("HDF5 chromosome order differs");}
                blockCount.put(c,b);totalBlocks++;sourceSnps=Math.addExact(sourceSnps,source);sourceCells=Math.addExact(sourceCells,Math.multiplyExact((long)source,source));selectedCells=Math.addExact(selectedCells,Math.multiplyExact((long)selected,selected));
                if(totalBlocks>limits.blocks()||sourceSnps>limits.variants()||sourceCells>admittedCells)throw new IOException("HDF5 native aggregate work ceiling exceeded");
                int previous=-1;
                for(int i=0;i<selected;i++) {
                    String[] r=fields(line(snpStream,input),5,"HDF5 SNP");int offset=Integer.parseInt(r[2]),cursor=cursors.get(c);
                    if(!r[0].equals(row[0])||!r[1].equals(row[1])||offset<=previous||offset>=source||!r[3].equals(Integer.toString(i))||cursor>=input.model().get(c).size()||!r[4].equals(input.model().get(c).get(cursor).id()))throw new IOException("HDF5 SNP selection/order differs from original reference-info model order; do not silently permute LD");
                    previous=offset;cursors.put(c,cursor+1);
                }
                if(selected==0) {
                    if(!row[5].isEmpty()||!row[6].isEmpty()||!row[7].equals("0"))throw new IOException("Unexpected matrix for empty selected HDF5 block");
                    out.write(c+"\t"+b+"\t"+source+"\t0\t0\tNA\tNA\tNA\t"+tolerance+"\n");continue;
                }
                String filename="chr"+c+"-block"+b+".raw-f64le";Path matrix=work.resolve(filename);long expected=Math.multiplyExact(8L,Math.multiplyExact((long)selected,selected));
                if(!filename.equals(row[5])||Long.parseLong(row[7])!=expected||Files.size(matrix)!=expected||!hash(matrix).equals(row[6]))throw new IOException("HDF5 exported matrix file/hash/size differs");
                if(expected>limits.retainedBytes()-input.estimatedRetained()||expected>Integer.MAX_VALUE-8)throw new IOException("Selected HDF5 matrix exceeds bounded native audit storage");
                byte[] bytes=Files.readAllBytes(matrix);var values=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer();double maximum=0,diagonal=0,asymmetry=0;
                for(int i=0;i<selected;i++)for(int j=0;j<selected;j++) {
                    double value=values.get(i*selected+j);if(!Double.isFinite(value))throw new IOException("Nonfinite selected reference LD value");maximum=Math.max(maximum,Math.abs(value));
                    if(i==j)diagonal=Math.max(diagonal,Math.abs(value-1));else asymmetry=Math.max(asymmetry,Math.abs(value-values.get(j*selected+i)));numericChecks++;
                }
                if(maximum>1+tolerance||diagonal>tolerance||asymmetry>tolerance)throw new IOException("Selected reference LD fails finite correlation/diagonal/symmetry checks; input is preserved and no repair is inferred");
                hashes.put(filename,row[6]);out.write(c+"\t"+b+"\t"+source+"\t"+selected+"\t"+((long)selected*selected)+"\t"+maximum+"\t"+diagonal+"\t"+asymmetry+"\t"+tolerance+"\n");
            }
            if(line(snpStream,input)!=null)throw new IOException("Extra HDF5 selected-SNP metadata");
        }
        for(int c:input.model().keySet())if(blockCount.get(c)<1||cursors.get(c)!=input.model().get(c).size())throw new IOException("Missing HDF5 blocks/model SNPs for chromosome "+c);
        if(integer(object.get("source_bytes"))!=sourceBytes||integer(object.get("source_blocks"))!=totalBlocks||integer(object.get("source_snp_entries"))!=sourceSnps||integer(object.get("source_ld_cells"))!=sourceCells||integer(object.get("exported_ld_cells"))!=selectedCells)throw new IOException("HDF5 bridge aggregate counts differ from native validation");
        hashes.put("ld-validation.tsv",hash(checked));long workBytes=Math.addExact(input.estimatedRetained(),Math.addExact(256L*limits.variants(),Math.multiplyExact(64L,sourceCells)));
        return new Result(totalBlocks,sourceSnps,sourceCells,selectedCells,numericChecks,workBytes,Map.copyOf(hashes));
    }
    static void verifyBridge(Result result,Path work)throws IOException {for(var e:result.bridgeHashes().entrySet())if(!hash(work.resolve(e.getKey())).equals(e.getValue()))throw new IOException("PRS-CS validated bridge artifact changed: "+e.getKey());}
    private static Path resource(String name,Path work)throws IOException {
        Path output=work.resolve(name);try(var stream=PrsCsAudits.class.getResourceAsStream("/org/jlinalg/omics/"+name)){if(stream==null)throw new IOException("Missing bundled PRS-CS bridge: "+name);Files.copy(stream,output);}return output;
    }
    private static String line(InputStream input,PrsCsInputs.Prepared prepared)throws IOException{return PlinkGlmInputs.line(input,prepared.limits().lineBytes());}
    private static String[] fields(String line,int width,String label)throws IOException {if(line==null)throw new IOException("Missing "+label+" row");String[] row=line.split("\t",-1);if(row.length!=width)throw new IOException("Wrong "+label+" row width");return row;}
    private static long integer(Object value)throws IOException {if(!(value instanceof Number number)||number.doubleValue()!=number.longValue())throw new IOException("Invalid HDF5 integer report");return number.longValue();}
    private static double number(String value)throws IOException {try{double n=Double.parseDouble(value);if(Double.isFinite(n))return n;}catch(NumberFormatException failure){throw new IOException("Invalid original marginal audit number",failure);}throw new IOException("Nonfinite original marginal audit number");}
    private static void close(double original,double nativeValue,String label)throws IOException {if(Math.abs(original-nativeValue)>Math.max(3e-15,3e-14*Math.abs(nativeValue)))throw new IOException("Native/original PRS-CS arithmetic differs for "+label);}
}
