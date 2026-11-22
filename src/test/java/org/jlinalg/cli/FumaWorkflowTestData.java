package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FumaWorkflowTestData {
    private FumaWorkflowTestData() { }
    static Map<String,Object> recipe(Path dir,boolean loci)throws Exception{
        Files.createDirectories(dir);Path root=Path.of("").toAbsolutePath();
        Files.writeString(dir.resolve("genes.tsv"),"ensembl_gene_id\texternal_gene_name\tchromosome_name\tstart_position\tend_position\tstrand\tgene_biotype\tentrezID\nG1\tONE\t1\t1\t200\t+\tprotein_coding\t1\nG2\tTWO\t1\t900\t3100\t-\tprotein_coding\t2\nG3\tTHREE\t2\t100\t200\t+\tlncRNA\t3\nG4\tFOUR\t2\t900\t1000\t+\tprotein_coding\t4\n");
        Files.writeString(dir.resolve("scores.tsv"),"ensg\tsymbol\tchr\tpos\tpLI\tmis_z\nG1\tONE\t1\t1\t0.123456789012345678901\t-1\nG2\tTWO\t1\t900\t.2\t2\nG3\tTHREE\t2\t100\tNA\tNA\nG4\tFOUR\t2\t900\t.4\t4\n");
        Files.writeString(dir.resolve("sets.gmt"),"PAIR\tlink\t1\t2\n");Files.writeString(dir.resolve("collections.tsv"),"path\nsets.gmt\n");
        var map=new LinkedHashMap<String,String>();map.put("genes","genes.tsv");map.put("annotation-id","synthetic-GRCh37-v1");map.put("biotypes","all");map.put("mhc","keep");map.put("mhc-extension","none");map.put("positional","window");map.put("window-bp","0");map.put("eqtl","none");map.put("pos-cadd","0");map.put("pos-rdb","none");map.put("pos-states","none");map.put("pos-binary","none");map.put("gene-scores","scores.tsv");map.put("gene-score-id","synthetic-scores-v1");map.put("score-duplicates","error");map.put("score-missing","error");map.put("empty","retain");
        var recipe=new LinkedHashMap<String,Object>();recipe.put("schema_version",1);recipe.put("build","GRCh37");recipe.put("mapping",map);recipe.put("gene_set_query_policy","actual-links");var geneSet=new LinkedHashMap<String,String>();geneSet.put("annotations","genes.tsv");geneSet.put("biotypes","all");geneSet.put("exclude-mhc","false");geneSet.put("gmt-manifest","collections.tsv");geneSet.put("annotation-id","synthetic-GRCh37-v1");geneSet.put("correction","fdr_bh");geneSet.put("adjusted-cutoff","1");geneSet.put("min-overlap","1");geneSet.put("duplicates","error");recipe.put("gene_set",geneSet);
        if(loci){
            var options=AdmixtureInputs.decode(Files.readAllBytes(root.resolve("examples/fuma/loci/options.json")));var stage=new LinkedHashMap<String,String>();for(var e:options.entrySet())if(!List.of("method","out").contains(e.getKey().toString())){String k=e.getKey().toString(),v=e.getValue().toString();stage.put(k,List.of("gwas","reference","ld").contains(k)?root.resolve(v).toString():v);}recipe.put("loci",stage);
            var rows=Files.readAllLines(root.resolve("examples/fuma/loci/ref.tsv"));String[] header=rows.get(0).split("\t");int chr=List.of(header).indexOf("chr"),pos=List.of(header).indexOf("pos"),a1=List.of(header).indexOf("A1"),a2=List.of(header).indexOf("A2");var annotations=new StringBuilder("uid\tchr\tpos\tCADD\tRDB\n");for(String line:rows.subList(1,rows.size())){String[] f=line.split("\t");String lo=f[a1].compareTo(f[a2])<0?f[a1]:f[a2],hi=f[a1].compareTo(f[a2])<0?f[a2]:f[a1];annotations.append(f[chr]).append(':').append(f[pos]).append(':').append(lo).append(':').append(hi).append('\t').append(f[chr]).append('\t').append(f[pos]).append("\t10\t2\n");}Files.writeString(dir.resolve("variant-annotations.tsv"),annotations);recipe.put("variant_resources",new LinkedHashMap<>(Map.of("annotations","variant-annotations.tsv","candidate_score_source","annotations","extra_rows","exclude-with-audit")));
        }else{
            Files.writeString(dir.resolve("candidates.tsv"),"uid\trsID\tchr\tpos\tgwasP\tCADD\tRDB\tlocus\tindependent_rsIDs\nu1\trs1\t1\t100\t1e-10\t10\t2\t1\trs1\nu2\trs2\t1\t1000\t.01\t10\t2\t1\trs2\n");Files.writeString(dir.resolve("variant-annotations.tsv"),"uid\tCADD\tRDB\nu1\t10\t2\nu2\t10\t2\n");map.put("candidates","candidates.tsv");map.put("annotations","variant-annotations.tsv");
        }
        return recipe;
    }
    @SuppressWarnings("unchecked") static Map<String,String> stage(Map<String,Object> recipe,String name){return (Map<String,String>)recipe.get(name);}
}
