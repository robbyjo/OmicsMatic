/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import jdistlib.ChiSquare;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;
import static org.jlinalg.cli.GenomicsemPrepTables.*;

/** Source-preserving munged intermediates; inverse-chi-square checks are independent. */
final class GenomicsemPrepMunge {
    private GenomicsemPrepMunge() { }
    private record Reference(String id,String allele1,String allele2) { }
    private record Raw(double p,double effect,double n,String allele1,String allele2) { }
    private static final Map<String,List<String>> ALIASES=Map.ofEntries(
        Map.entry("SNP",List.of("SNP","SNPID","RSID","RS_NUMBER","RS_NUMBERS","MARKERNAME","ID","PREDICTOR","SNP_ID","VARIANTID","VARIANT_ID","RSIDS","RS_ID")),
        Map.entry("A1",List.of("A1","ALLELE1","EFFECT_ALLELE","INC_ALLELE","REFERENCE_ALLELE","EA","REF")),Map.entry("A2",List.of("A2","ALLELE2","ALLELE0","OTHER_ALLELE","NON_EFFECT_ALLELE","DEC_ALLELE","OA","NEA","ALT","A0")),
        Map.entry("effect",List.of("OR","B","BETA","LOG_ODDS","EFFECTS","EFFECT","SIGNED_SUMSTAT","EST","BETA1","LOGOR","Z","ZSCORE","Z-SCORE","ZSTATISTIC","ZSTAT","Z-STATISTIC")),
        Map.entry("P",List.of("P","PVALUE","PVAL","P_VALUE","P-VALUE","P.VALUE","P_VAL","GC_PVALUE","WALD_P")),Map.entry("N",List.of("N","WEIGHT","NCOMPLETESAMPLES","TOTALSAMPLESIZE","TOTALN","TOTAL_N","N_COMPLETE_SAMPLES","SAMPLESIZE","NEFF","N_EFF","N_EFFECTIVE","SUMNEFF")));
    private static int column(List<String>header,String concept,Map<String,Object>provided)throws IOException {
        var upper=header.stream().map(s->s.toUpperCase(Locale.ROOT)).toList();if(provided.containsKey(concept))return upper.indexOf(text(provided.get(concept),"source column").toUpperCase(Locale.ROOT));
        if(!concept.equals("effect")&&upper.contains(concept))return upper.indexOf(concept);for(int i=0;i<upper.size();i++)if(ALIASES.get(concept).contains(upper.get(i)))return i;return -1;
    }
    private static double rawNumber(String value)throws IOException {if(missing(value)||value.equals("NaN"))return Double.NaN;if(value.equals("Inf"))return Double.POSITIVE_INFINITY;if(value.equals("-Inf"))return Double.NEGATIVE_INFINITY;return GenomicsemData.numeric(value);}
    private static boolean sameNumber(double actual,double expected){return Double.doubleToLongBits(actual)==Double.doubleToLongBits(expected)||Double.isFinite(actual)&&Double.isFinite(expected)&&Math.abs(actual-expected)<=1e-12+1e-9*Math.abs(expected);}
    static Map<String,Object> audit(Path root,Map<String,Object>request,boolean inspect)throws IOException {
        var contents=map();for(String name:GenomicsemPrepAudit.traits(request)){
            java.security.MessageDigest digest;try{digest=java.security.MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}long size=0;
            try(var input=new java.util.zip.GZIPInputStream(Files.newInputStream(root.resolve("products/munged/"+name+".sumstats.gz")))){byte[] buffer=new byte[65536];for(int n;(n=input.read(buffer))>=0;){size=Math.addExact(size,n);require(size<=GenomicsemContracts.cap(request,"maximum_uncompressed_bytes"),"Munged decompressed content capacity exceeded");digest.update(buffer,0,n);}}
            contents.put(name,map("bytes",size,"sha256",HexFormat.of().formatHex(digest.digest())));
        }
        if(inspect)require(same(contents,read(root.resolve("worker/munged-content.json"))),"Changed decompressed original munged content");else write(root.resolve("worker/munged-content.json"),contents);
        var options=object(request.get("options"));var paths=object(request.get("paths"));Path bundle=root.resolve("inputs/bundle");Map<String,Reference> reference=new HashMap<>();
        try(var table=new Text(bundle.resolve(text(paths.get("hm3"),"hm3")),request,true)){
            int snp=table.column("SNP"),a1=table.column("A1"),a2=table.column("A2");List<String> row;while((row=table.next())!=null){String id=row.get(snp);require(reference.put(id,new Reference(id,row.get(a1),row.get(a2)))==null,"Duplicated HapMap reference SNP;no reference repair or deduplication is performed");}
        }
        var names=GenomicsemPrepAudit.traits(request);var inputs=list(paths.get("files"));var requestedN=list(options.get("N"));var columns=object(options.get("column_names"));long available=0,unavailable=0,sourceRows=0,tailChecks=0,censoredZ=0,zeroP=0,duplicates=0;
        try(var report=lines(root.resolve("products/munged-status.tsv"),inspect);var rowStatus=lines(root.resolve("products/munged-row-status.tsv"),inspect)){
            rowStatus.line("trait\tsource_output_row\tSNP\tfinite_usable_intermediate\tduplicate_source_output_ID\tnonfinite_Z\toriginal_fields_changed");
            report.line("trait\tsource_output_rows\tfinite_usable_rows\tunavailable_rows\tnonfinite_Z_rows\toriginal_input_rows\tinverse_chisquare_rows_checked\toriginal_values_changed");
            for(int t=0;t<names.size();t++){
                Map<String,List<Raw>> raw=new HashMap<>();long rawRows=0;boolean independent=true;boolean oddsRatio=false;
                try(var table=new Text(bundle.resolve(text(inputs.get(t),"source file")),request)){
                    int id=column(table.header,"SNP",columns),p=column(table.header,"P",columns),effect=column(table.header,"effect",columns),a1=column(table.header,"A1",columns),a2=column(table.header,"A2",columns),n=column(table.header,"N",columns);independent=id>=0&&p>=0&&effect>=0&&a1>=0&&a2>=0;
                    // NEFF_HALF/DIV2 replacement occurs before original aliasing.
                    // Such profiles retain full source provenance; their N value
                    // is checked as a positive original output,not guessed here.
                    boolean half=table.header.stream().map(s->s.toUpperCase(Locale.ROOT)).anyMatch(s->s.equals("NEFF_HALF")||s.equals("NEFFDIV2"));
                    List<String> row;while((row=table.next())!=null){rawRows++;if(!independent)continue;Reference ref=reference.get(row.get(id));if(ref==null)continue;double pv=rawNumber(row.get(p)),e=rawNumber(row.get(effect));if(Double.isNaN(pv)||Double.isNaN(e))continue;double size=requestedN.get(t)==null?(n<0||half?Double.NaN:rawNumber(row.get(n))):GenomicsemContracts.finite(requestedN.get(t));raw.computeIfAbsent(ref.id,k->new ArrayList<>()).add(new Raw(pv,e,size,row.get(a1).toUpperCase(Locale.ROOT),row.get(a2).toUpperCase(Locale.ROOT)));}
                }
                var effects=new ArrayList<Double>();for(var entries:raw.values())for(Raw r:entries)effects.add(r.effect);effects.sort(Double::compare);
                if(!effects.isEmpty()){int middle=effects.size()/2;double median=effects.size()%2==1?effects.get(middle):(effects.get(middle-1)/2+effects.get(middle)/2);oddsRatio=Math.rint(median)==1;}
                // Original odds-ratio detection uses all nonmissing P/effect rows
                // after the reference merge and before allele/INFO/MAF filtering.
                Map<String,Integer> counts=new HashMap<>();try(var table=new Text(root.resolve("products/munged/"+names.get(t)+".sumstats.gz"),request,true)){List<String> row;while((row=table.next())!=null)counts.merge(row.get(0),1,Integer::sum);}
                sourceRows+=rawRows;long good=0,bad=0,nonfinite=0,checks=0,output=0;Set<String> seen=new HashSet<>();
                try(var table=new Text(root.resolve("products/munged/"+names.get(t)+".sumstats.gz"),request,true)){
                    require(table.header.equals(List.of("SNP","N","Z","A1","A2")),"Changed original munged schema");List<String> row;
                    while((row=table.next())!=null){output++;String id=row.get(0);double n=rawNumber(row.get(1)),z=rawNumber(row.get(2));boolean valid=!missing(id)&&Double.isFinite(n)&&n>0&&Double.isFinite(z)&&!missing(row.get(3))&&!missing(row.get(4))&&!row.get(3).equals(row.get(4));Reference ref=reference.get(id);
                        if(ref!=null)require(row.get(3).equals(ref.allele1)&&row.get(4).equals(ref.allele2),"Original munged alleles differ from declared reference");else require(!valid,"Unknown usable source munged SNP");if(!seen.add(id))duplicates++;if(counts.get(id)>1)valid=false;
                        if(!Double.isFinite(z)){nonfinite++;censoredZ++;}if(valid)good++;else bad++;rowStatus.line(quote(names.get(t))+"\t"+output+"\t"+quote(id)+"\t"+valid+"\t"+(counts.get(id)>1)+"\t"+!Double.isFinite(z)+"\tfalse");
                        List<Raw> candidates=raw.get(id);if(independent&&ref!=null&&!missing(id)){require(candidates!=null,"Original munged identity has no captured nonmissing source candidate");boolean matched=false;for(Raw r:candidates){if(!(ref.allele1.equals(r.allele1)||ref.allele2.equals(r.allele1))||!(ref.allele1.equals(r.allele2)||ref.allele2.equals(r.allele2)))continue;double sign=Math.signum(oddsRatio?Math.log(r.effect):r.effect);if(!ref.allele1.equals(r.allele1)&&ref.allele1.equals(r.allele2))sign= -sign;double expected=sign*Math.sqrt(ChiSquare.quantile(r.p,1,false,false));if(sameNumber(z,expected)&&(Double.isNaN(r.n)||sameNumber(n,r.n))){matched=true;if(r.p==0)zeroP++;break;}}require(matched,"Original munged Z/N/allele sign relationship differs from the captured source row");checks++;}
                    }
                }
                available+=good;unavailable+=bad;tailChecks+=checks;report.line(String.join("\t",quote(names.get(t)),Long.toString(output),Long.toString(good),Long.toString(bad),Long.toString(nonfinite),Long.toString(rawRows),Long.toString(checks),"false"));require(good>0,"No finite usable munged intermediate remains for a requested trait;raw source products retained");
            }
        }
        return map("available_units",available,"unavailable_units",unavailable,"structurally_complete",true,"unit_definition","trait and source munged row","source_input_rows",sourceRows,"independent_inverse_chisquare_rows_checked",tailChecks,"source_nonfinite_Z_rows_retained",censoredZ,"checked_source_zero_P_endpoints",zeroP,"duplicate_output_rows",duplicates,"original_intermediates_not_finite_association_results",true,"original_source_fields_repaired",false);
    }
}
