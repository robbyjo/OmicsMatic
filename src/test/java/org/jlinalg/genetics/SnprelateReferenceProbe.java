/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Complete independent original-R reference check; not a production analysis admission command. */
public final class SnprelateReferenceProbe {
    private SnprelateReferenceProbe() { }
    private static void need(boolean condition,String cause) throws IOException {if(!condition)throw new IOException(cause);}
    private static List<Map<String,String>> table(Path path) throws IOException {
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            String line=reader.readLine();need(line!=null,"Missing reference header: "+path);String[] header=line.split("\t",-1);
            need(Set.of(header).size()==header.length,"Duplicate reference header");var rows=new ArrayList<Map<String,String>>();
            for(;(line=reader.readLine())!=null;) {
                String[] f=line.split("\t",-1);need(f.length==header.length,"Invalid complete TSV shape: "+path);
                var row=new HashMap<String,String>();for(int i=0;i<f.length;i++)row.put(header[i],f[i]);rows.add(row);
            }
            return rows;
        }
    }
    private static double number(String value) throws IOException {
        return switch(value) {
            case "NaN" -> Double.NaN;
            case "Inf" -> Double.POSITIVE_INFINITY;
            case "-Inf" -> Double.NEGATIVE_INFINITY;
            case "NA" -> throw new IOException("Original R NA is distinct from NaN; no silent substitution");
            default -> {try{yield Double.parseDouble(value);}catch(NumberFormatException failure){throw new IOException("Invalid source numeric field: "+value,failure);}}
        };
    }
    private static void same(double expected,String raw,String role) throws IOException {
        double actual=number(raw);
        need(Double.doubleToLongBits(expected)==Double.doubleToLongBits(actual),role+" differs: "+Double.toHexString(expected)+" versus original "+raw);
    }
    /** Require BED prefix, reference original-results directory and a NEW complete summary output TSV. */
    public static void main(String[] args) throws Exception {
        need(args.length==3,"Require complete BED prefix, original-R results and NEW summary TSV");
        Path prefix=Path.of(args[0]),root=Path.of(args[1]),summary=Path.of(args[2]);need(!Files.exists(summary),"Preserve existing output");
        var fam=Files.readAllLines(Path.of(prefix+".fam"),StandardCharsets.UTF_8);
        var bim=Files.readAllLines(Path.of(prefix+".bim"),StandardCharsets.UTF_8);
        int n=fam.size(),m=bim.size();need(n>=2&&n<=500&&m>=1&&m<=20000&&(long)n*m<=2_000_000,"Reference probe capacity exceeded");
        var fid=new ArrayList<String>();var iid=new ArrayList<String>();var chr=new ArrayList<Integer>();
        for(String line:fam){String[] f=line.strip().split("\\s+");need(f.length==6,"Invalid FAM");fid.add(f[0]);iid.add(f[1]);}
        for(String line:bim){String[] f=line.strip().split("\\s+");need(f.length==6,"Invalid BIM");chr.add(Integer.parseInt(f[0]));}
        byte[] bed=Files.readAllBytes(Path.of(prefix+".bed"));int width=(n+3)/4;
        need(bed.length==3+(long)width*m&&bed[0]==0x6c&&bed[1]==0x1b&&bed[2]==1,"Invalid complete BED transport");
        byte[][] calls=new byte[m][n];var variants=new ArrayList<SnprelateVariantQc.Counts>();
        for(int v=0;v<m;v++) {
            for(int i=0;i<n;i++) {
                int code=((bed[3+v*width+i/4]&255) >>> (2*(i%4)))&3;
                calls[v][i]=switch(code){case 0->2;case 2->1;case 3->0;default->-1;};
            }
            variants.add(SnprelateVariantQc.count(calls[v]));
        }
        var pairs=new ArrayList<KingRobustKinship.Pair>();
        for(int hi=1;hi<n;hi++)for(int lo=0;lo<hi;lo++)pairs.add(new KingRobustKinship.Pair(hi,lo));
        var containers=new ArrayList<Path>();try(var children=Files.list(root)) {
            children.filter(p->Files.isDirectory(p)&&Files.exists(p.resolve("original-case-dispositions.tsv"))).sorted().forEach(containers::add);
        }
        need(!containers.isEmpty(),"No complete original source container");
        long totalCells=0,totalVariants=0,sourceErrors=0,sourceEmptyReturns=0,nonfinite=0,identityDifferences=0,sampleIdentityDifferences=0;
        try(var writer=Files.newBufferedWriter(summary,StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW)) {
            writer.write("container\tcase\tselected_variants\tmatrix_cells\tnative_pairs\tnonfinite_source_kinships\tstatus\n");
            for(Path container:containers) {
                Map<String,String> transport=table(container.resolve("transport-specification.tsv")).get(0);
                var layout=transport.get("snp_first_dimension").equals("TRUE")?SnprelateVariantQc.Layout.SNP_BY_SAMPLE:SnprelateVariantQc.Layout.SAMPLE_BY_SNP;
                var samples=table(container.resolve("original-sample-map.tsv"));need(samples.size()==n,"Complete source sample map differs");
                var sampleKeys=new java.util.HashSet<String>();
                for(int i=0;i<n;i++) {
                    var sample=samples.get(i);String key=sample.get("gds_sample_id");
                    need(Integer.parseInt(sample.get("input_row"))==i+1&&fid.get(i).equals(sample.get("FID"))&&iid.get(i).equals(sample.get("IID")),"Original FAM identity/order changed");
                    need(sample.get("gds_sample_id_na").equals("FALSE")&&!key.isEmpty()&&sampleKeys.add(key),"Missing/duplicate original GDS sample key");
                    if(transport.get("transport").startsWith("row-index"))need(key.equals(Integer.toString(i+1))&&sample.get("storage").equals("integer"),"Explicit row-index sample transport changed");
                    else if(!key.equals(iid.get(i)))sampleIdentityDifferences++;
                }
                var maps=table(container.resolve("original-variant-map.tsv"));need(maps.size()==m,"Complete source variant map differs");
                for(int v=0;v<m;v++) {
                    var row=maps.get(v);String[] original=bim.get(v).strip().split("\\s+");
                    need(Integer.parseInt(row.get("input_row"))==v+1&&Integer.parseInt(row.get("chr"))==chr.get(v),"Original variant row/chromosome differs");
                    need(Double.parseDouble(row.get("position"))==Long.parseLong(original[3])&&row.get("allele").equals(original[4]+"/"+original[5]),"Original variant position/alleles changed");
                    if(row.get("rs_id_node_present").equals("TRUE"))need(row.get("rs_id").equals(original[1])&&row.get("rs_id_na").equals("FALSE"),"Explicit original rs ID lost or made missing");
                    else if(!row.get("gds_snp_id").equals(original[1]))identityDifferences++;
                }
                var genotypeRows=table(container.resolve("original-genotypes.tsv"));need(genotypeRows.size()==m,"Complete original genotype population differs");
                for(int v=0;v<m;v++) {
                    var r=genotypeRows.get(v);need(Integer.parseInt(r.get("input_variant_row"))==v+1,"Genotype variant order differs");
                    for(int i=0;i<n;i++)need(r.get(Integer.toString(i+1)).equals(calls[v][i]<0?"NA":Byte.toString(calls[v][i])),"Original GDS genotype transport differs from complete BED calls");
                }
                var rates=table(container.resolve("original-variant-statistics.tsv"));need(rates.size()==m,"Source statistics population differs");
                for(int v=0;v<m;v++) {
                    var s=SnprelateVariantQc.statistics(variants.get(v),layout);var r=rates.get(v);
                    same(s.alleleFrequency(),r.get("AlleleFreq_hex"),"Allele frequency");
                    same(s.minorAlleleFrequency(),r.get("MinorFreq_hex"),"Minor allele frequency");
                    same(s.missingRate(),r.get("MissingRate_hex"),"Layout-specific missing rate");totalVariants++;
                }
                for(var disposition:table(container.resolve("original-case-dispositions.tsv"))) {
                    String name=disposition.get("case");Path folder=container.resolve(name);
                    var options=table(folder.resolve("original-case-options.tsv")).get(0);
                    var filters=new SnprelateVariantQc.Filters(options.get("remove_monomorphic").equals("TRUE"),number(options.get("maf_hex")),number(options.get("missing_rate_hex")));
                    var expected=new ArrayList<Integer>();
                    for(int v=0;v<m;v++)if((!options.get("autosome_only").equals("TRUE")||chr.get(v)>=1&&chr.get(v)<=22)
                            &&SnprelateVariantQc.selected(SnprelateVariantQc.decide(SnprelateVariantQc.statistics(variants.get(v),layout),filters)))expected.add(v);
                    if(disposition.get("status").equals("ORIGINAL_ERROR")) {
                        need(expected.isEmpty(),"Original R error despite nonempty native selected population: "+folder);
                        need(Files.exists(folder.resolve("original-error.txt"))&&!Files.exists(folder.resolve("original-complete-matrices.tsv")),"Source failed-disposition artifacts differ");
                        need(Files.readString(folder.resolve("original-error.txt"),StandardCharsets.UTF_8).contains("Invalid SnpStart and SnpCount"),"Unexpected original error; an empty native selection alone is insufficient");
                        writer.write(container.getFileName()+"\t"+name+"\t0\t0\t0\t0\tORIGINAL_EMPTY_SELECTION_REJECTED\n");sourceErrors++;continue;
                    }
                    need(disposition.get("status").equals("ORIGINAL_SUCCESS"),"Unknown source case disposition");
                    if(expected.isEmpty())sourceEmptyReturns++;
                    var selected=table(folder.resolve("original-selected-variants.tsv"));need(selected.size()==expected.size(),"Source selected SNP population size differs");
                    for(int j=0;j<selected.size();j++)need(Integer.parseInt(selected.get(j).get("input_row"))==expected.get(j)+1,"Source selected SNP order/identity differs");
                    var sampleSelection=table(folder.resolve("original-selected-samples.tsv"));need(sampleSelection.size()==n,"Source participant population differs");
                    for(int i=0;i<n;i++)need(Integer.parseInt(sampleSelection.get(i).get("input_row"))==i+1&&sampleSelection.get(i).get("original_sample_id").equals(samples.get(i).get("gds_sample_id")),"Source result sample identity/order differs");
                    var accumulator=new KingRobustKinship.Accumulator(n,pairs,Math.max(1,expected.size()),200_000_000);
                    for(int v:expected)accumulator.accept(calls[v]);accumulator.finish();
                    var cells=table(folder.resolve("original-complete-matrices.tsv"));need(cells.size()==n*n,"Incomplete original source matrices");
                    boolean[] seen=new boolean[n*n];long caseNonfinite=0;
                    for(var cell:cells) {
                        int i=Integer.parseInt(cell.get("row_index"))-1,j=Integer.parseInt(cell.get("col_index"))-1;
                        need(i>=0&&j>=0&&i<n&&j<n&&!seen[i*n+j],"Invalid/duplicate original matrix cell");seen[i*n+j]=true;
                        need(cell.get("row_sample_id").equals(samples.get(i).get("gds_sample_id"))&&cell.get("col_sample_id").equals(samples.get(j).get("gds_sample_id")),"Source matrix identity mapping differs");
                        double k,z;
                        if(i==j){k=SnprelateKingRobust.DIAGONAL_KINSHIP;z=SnprelateKingRobust.DIAGONAL_IBS0;}
                        else {
                            int hi=Math.max(i,j),lo=Math.min(i,j),pair=hi*(hi-1)/2+lo;
                            var context=options.get("family_mode").equals("original-FID")&&fid.get(i).equals(fid.get(j))?SnprelateKingRobust.Context.WITHIN_FAMILY:SnprelateKingRobust.Context.BETWEEN_OR_SINGLETONS;
                            var estimate=SnprelateKingRobust.estimate(accumulator.estimate(pair).counts(),context);k=estimate.kinship();z=estimate.ibs0();
                        }
                        same(k,cell.get("kinship_hex"),"Complete original KING coefficient");same(z,cell.get("IBS0_hex"),"Complete original IBS0 proportion");
                        need(cell.get("kinship_na").equals(Double.isNaN(k)?"TRUE":"FALSE")&&cell.get("kinship_nan").equals(Double.isNaN(k)?"TRUE":"FALSE"),"Original R NA/NaN class differs");
                        need(cell.get("IBS0_na").equals(Double.isNaN(z)?"TRUE":"FALSE")&&cell.get("IBS0_nan").equals(Double.isNaN(z)?"TRUE":"FALSE"),"Original R IBS0 NA/NaN class differs");
                        if(!Double.isFinite(k)){caseNonfinite++;nonfinite++;}totalCells++;
                    }
                    writer.write(container.getFileName()+"\t"+name+"\t"+expected.size()+"\t"+cells.size()+"\t"+pairs.size()+"\t"+caseNonfinite+"\t"+(expected.isEmpty()?"ORIGINAL_EMPTY_SELECTION_RETURNED_NAN_MATRIX":"COMPLETE_BIT_IDENTICAL_SOURCE_FIELDS")+"\n");
                }
            }
        }
        System.out.println("PASS complete source matrices/stats: cells="+totalCells+" statistics="+totalVariants+" nonfinite="+nonfinite+" original_empty_errors="+sourceErrors+" original_empty_matrix_returns="+sourceEmptyReturns+" upstream_variant_id_representations_changed="+identityDifferences+" upstream_sample_id_representations_changed="+sampleIdentityDifferences);
    }
}
