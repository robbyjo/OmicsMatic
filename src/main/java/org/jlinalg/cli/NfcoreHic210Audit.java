/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.jlinalg.sequence.FastqIO;
import org.jlinalg.omics.contact.HicProContactAudit;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete paired-input, source pairing/projection and independent raw contact-matrix checks. */
final class NfcoreHic210Audit {
    private NfcoreHic210Audit(){ }
    static Reader decoded(Path p)throws IOException{
        var input=new PushbackInputStream(Files.newInputStream(p),2);try{int a=input.read(),b=input.read();if(b>=0)input.unread(b);if(a>=0)input.unread(a);InputStream stream=a==31&&b==139?new GZIPInputStream(input,65536):input;return new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)),65536);}catch(IOException|RuntimeException failure){input.close();throw failure;}
    }
    static final class Lines implements AutoCloseable {
        final Reader reader;final NfcoreHic210Request.Request request;long characters;
        Lines(Path p,NfcoreHic210Request.Request r)throws IOException{reader=decoded(p);request=r;}
        String next()throws IOException{
            var s=new StringBuilder();int c;while((c=reader.read())!=-1){if(++characters>request.auditLimits().maximumDecodedCharacters())throw new IOException("Whole decoded Hi-C input capacity exceeded");if(c=='\n'){if(s.length()>0&&s.charAt(s.length()-1)=='\r')s.setLength(s.length()-1);return s.toString();}if(s.length()>=request.auditLimits().maximumLineCharacters())throw new IOException("Whole Hi-C input line capacity exceeded");s.append((char)c);}return s.length()==0?null:s.toString();
        }
        public void close()throws IOException{reader.close();}
    }
    static Map<String,Long> reference(NfcoreHic210Files files,NfcoreHic210Request.Request request)throws IOException{
        var result=new LinkedHashMap<String,Long>();String current=null;long length=0;
        try(var lines=new Lines(files.inputFile("data/reference/"+NfcoreHic210Prepare.basename(request.fasta())),request)){
            String line;while((line=lines.next())!=null){if(line.startsWith(">")){if(current!=null){if(length==0)throw new IOException("Empty original reference sequence");result.put(current,length);}String header=line.substring(1);if(header.isEmpty()||Character.isWhitespace(header.charAt(0)))throw new IOException("Invalid original reference identifier");current=header.split("[ \\t]",2)[0];if(result.containsKey(current))throw new IOException("Duplicate literal original reference sequence");length=0;if(result.size()>=request.auditLimits().maximumBins())throw new IOException("Whole reference-sequence capacity exceeded");}
                else{if(current==null)throw new IOException("Reference sequence precedes its original header");for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c==' '||c=='\t')continue;if(c<33||c>126)throw new IOException("Unsupported original non-ASCII reference sequence symbol");length=Math.addExact(length,1);}}}
        }
        if(current==null||length==0)throw new IOException("Empty/incomplete original FASTA reference");if(result.put(current,length)!=null)throw new IOException("Duplicate literal reference identifier");return result;
    }
    record Reads(long pairs,long bases1,long bases2,Set<String> ids){ }
    static Reads reads(NfcoreHic210Files files,NfcoreHic210Request.Request r,String sample)throws IOException{
        long count=0,bases1=0,bases2=0;var ids=new HashSet<String>();
        for(var library:NfcoreHic210Prepare.reads(r))if(library.sample().equals(sample))try(var first=new Lines(files.inputFile(library.firstRelative()),r);var second=new Lines(files.inputFile(library.secondRelative()),r)){
            String header;while((header=first.next())!=null){if(++count>r.maximumReads())throw new IOException("Whole original FASTQ pair/ID capacity exceeded; no cohort subset");var a=read(header,first);var b=read(second.next(),second);if(!a.pairId().equals(b.pairId()))throw new IOException("Original FASTQ mate identity/order differs at input pair "+count);ids.add(a.pairId());bases1=Math.addExact(bases1,a.sequence().length());bases2=Math.addExact(bases2,b.sequence().length());if((count&1023)==0&&Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Hi-C input inspection interrupted");}if(second.next()!=null)throw new IOException("Original second FASTQ mate has extra records");
        }
        return new Reads(count,bases1,bases2,ids);
    }
    private static FastqIO.Read read(String header,Lines lines)throws IOException{
        String sequence=lines.next(),plus=lines.next(),quality=lines.next();if(header==null||!header.startsWith("@")||header.length()<2||Character.isWhitespace(header.charAt(1))||sequence==null||plus==null||!plus.startsWith("+")||quality==null||sequence.length()!=quality.length())throw new IOException("Original FASTQ requires complete unwrapped four-line records and equal sequence/quality lengths");
        for(int i=0;i<sequence.length();i++)if("ACGTRYSWKMBDHVNUacgtryswkmbdhvnu".indexOf(sequence.charAt(i))<0||quality.charAt(i)<33||quality.charAt(i)>126)throw new IOException("Invalid original FASTQ nucleotide/quality character");if(plus.length()>1&&!plus.substring(1).split("\\s+",2)[0].equals(header.substring(1).split("\\s+",2)[0]))throw new IOException("Original repeated FASTQ + header differs");return new FastqIO.Read(header,sequence,quality);
    }
    static Map<String,Long> stats(Path p,NfcoreHic210Request.Request r)throws IOException{
        var result=new LinkedHashMap<String,Long>();try(var rows=new Lines(p,r)){String line;while((line=rows.next())!=null){if(line.startsWith("#"))continue;String[] f=line.split("\t",-1);if(f.length<2||!f[1].matches("0|[1-9][0-9]*"))throw new IOException("Original Hi-C count-statistic schema differs");long value;try{value=Long.parseLong(f[1]);}catch(NumberFormatException e){throw new IOException("Original count-statistic overflow",e);}if(result.put(f[0],value)!=null)throw new IOException("Repeated original count-statistic key");}}return result;
    }
    private static long count(Map<String,Long> values,String key)throws IOException{Long v=values.get(key);if(v==null)throw new IOException("Missing original count statistic: "+key);return v;}
    static Map<String,Object> run(NfcoreHic210Files files,NfcoreHic210Request.Request r,Path export)throws IOException{
        var metadata=object(json(export.resolve("COMPLETED.json"),67108864));if(!"COMPLETE_ORIGINAL_COOLER_ARRAY_EXPORT_NO_SUBSET_OR_NUMERICAL_REPAIR".equals(metadata.get("status")))throw new IOException("Incomplete original Cooler export");var maps=list(metadata.get("maps"));if(maps.size()!=Math.multiplyExact(r.samples().size(),NfcoreHic210Request.mapResolutions(r).size()))throw new IOException("Complete cohort/resolution export population differs");var references=reference(files,r);var reports=new ArrayList<Map<String,Object>>();var used=new HashSet<String>();
        for(var sample:r.samples()){
            Reads input=reads(files,r,sample.id());String base="hicpro/stats/"+sample.id()+"/"+sample.id();var first=stats(files.output(base+".R1.mmapstat"),r);var second=stats(files.output(base+".R2.mmapstat"),r);var paired=stats(files.output(base+".mpairstat"),r);var classified=stats(files.output(base+".mRSstat"),r);var merged=stats(files.output(base+"_allValidPairs.mergestat"),r);
            if(count(first,"total_R1")!=input.pairs()||count(second,"total_R2")!=input.pairs()||count(paired,"Total_pairs_processed")!=input.pairs()||count(first,"mapped_R1")!=Math.addExact(count(first,"global_R1"),count(first,"local_R1"))||count(second,"mapped_R2")!=Math.addExact(count(second,"global_R2"),count(second,"local_R2")))throw new IOException("Complete original FASTQ/mapping count census differs");
            long pairPartition=0;for(String key:List.of("Unmapped_pairs","Low_qual_pairs","Unique_paired_alignments","Multiple_pairs_alignments","Pairs_with_singleton"))pairPartition=Math.addExact(pairPartition,count(paired,key));if(pairPartition!=input.pairs())throw new IOException("Original mapped-pair classification does not cover every input pair");
            long classifiedSum=0;for(String key:List.of("Valid_interaction_pairs","Dangling_end_pairs","Religation_pairs","Self_Cycle_pairs","Single-end_pairs","Filtered_pairs","Dumped_pairs"))classifiedSum=Math.addExact(classifiedSum,count(classified,key));if(classifiedSum!=count(paired,"Reported_pairs"))throw new IOException("Original Hi-C interaction classes do not account for all reported pairs");
            for(var library:NfcoreHic210Prepare.reads(r))if(library.sample().equals(sample.id()))files.output("hicpro/mapping/"+sample.id()+"_"+(library.library()-1)+"_bwt2pairs.bam");files.output("hicpro/valid_pairs/pairix/"+sample.id()+"_contacts.pairs.gz.px2");HicProContactAudit.Result result;
            try(var original=decoded(files.output("hicpro/valid_pairs/"+sample.id()+".allValidPairs"));var projected=decoded(files.output("hicpro/valid_pairs/pairix/"+sample.id()+"_contacts.pairs.gz"))){result=HicProContactAudit.inspect(original,projected,references,NfcoreHic210Request.mapResolutions(r),input.ids(),r.auditLimits());}
            if(result.pairs()!=count(merged,"valid_interaction_rmdup")||result.cisPairs()!=count(merged,"cis_interaction")||result.transPairs()!=count(merged,"trans_interaction")||count(merged,"valid_interaction")!=count(classified,"Valid_interaction_pairs"))throw new IOException("Original valid-pair read IDs, projection, cis/trans or duplicate-retention counts differ");
            var matrixReports=new ArrayList<Map<String,Object>>();for(var matrix:result.matrices()){
                Map<?,?> selected=null;for(Object item:maps){var row=object(item);if(sample.id().equals(row.get("sample"))&&integer(row.get("bin_size"),1,Integer.MAX_VALUE)==matrix.binSize()){if(selected!=null)throw new IOException("Repeated source sample/resolution export");selected=row;}}
                if(selected==null)throw new IOException("Missing source sample/resolution export");String directory=member(text(selected.get("directory")));if(!used.add(directory)||directory.contains("/"))throw new IOException("Repeated/invalid original array-export directory");var sourceChroms=list(selected.get("chromosomes"));if(sourceChroms.size()!=references.size())throw new IOException("Complete original reference-contig census differs");int ci=0;for(var chr:references.entrySet()){var actual=object(sourceChroms.get(ci++));if(!chr.getKey().equals(actual.get("name"))||chr.getValue()!=integer(actual.get("length"),1,Long.MAX_VALUE))throw new IOException("Exact original reference contig/order/length differs");}
                HicProContactAudit.Verified verified;try(var bins=decoded(export.resolve(directory+"/bins.tsv"));var pixels=decoded(export.resolve(directory+"/pixels.tsv"))){verified=HicProContactAudit.verify(matrix,bins,pixels,r.auditLimits());}if(verified.bins()!=integer(selected.get("bins"),1,Integer.MAX_VALUE)||verified.pixels()!=integer(selected.get("pixels"),0,Integer.MAX_VALUE)||verified.countSum()!=integer(selected.get("count_sum"),0,Long.MAX_VALUE)||verified.missingWeightBins()!=integer(selected.get("missing_weight_bins"),0,Integer.MAX_VALUE))throw new IOException("Original HDF5 array population/export metadata differs");matrixReports.add(Map.of("bin_size",matrix.binSize(),"bins",verified.bins(),"source_missing_weight_bins",verified.missingWeightBins(),"raw_pixels",verified.pixels(),"raw_count_sum",verified.countSum()));
            }
            reports.add(Map.of("sample",sample.id(),"input_read_pairs",input.pairs(),"unique_input_read_ids",input.ids().size(),"bases_read1",input.bases1(),"bases_read2",input.bases2(),"source_valid_pairs",result.pairs(),"source_cis_pairs",result.cisPairs(),"source_trans_pairs",result.transPairs(),"matrices",matrixReports));
        }
        return Map.of("samples",reports,"reference_contigs",references.size(),"reference_bases",references.values().stream().reduce(0L,Math::addExact),"all_source_pairs_and_selected_raw_cooler_pixels_recomputed",true,"source_missing_weight_markers_preserved",true,"source_fit_outputs_retained",true,"independent_compartment_TAD_or_mcool_numerical_parity_certified",false,"scientific_identifiers_repaired",false);
    }
}
