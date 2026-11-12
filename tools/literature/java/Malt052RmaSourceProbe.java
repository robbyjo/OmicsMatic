/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import megan.rma6.RMA6Connector;
import megan.data.IReadBlock;

/** Direct original0.5.2 API oracle, not a native reader or a new format implementation. */
public final class Malt052RmaSourceProbe {
    private Malt052RmaSourceProbe() { }
    private static String text(String s){return s==null?"NULL":Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
    private static String bits(float f){return Integer.toHexString(Float.floatToRawIntBits(f));}
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IOException("Require complete source RMA and NEW output directory");Path out=Path.of(args[1]);Files.createDirectory(out);var connector=new RMA6Connector(args[0]);long reportedReads=connector.getNumberOfReads(),reportedMatches=connector.getNumberOfMatches();var membership=new HashMap<Long,Integer>();var classes=new TreeMap<Integer,String>();var classification=connector.getClassificationBlock("Taxonomy");
        if(classification==null&&reportedReads!=0)throw new IOException("Missing complete stored classification");
        if(classification!=null)for(int taxon:new TreeSet<>(classification.getKeySet())) {
            int count=classification.getSum(taxon);long found=0;
            try(var reads=connector.getReadsIterator("Taxonomy",taxon,0f,Float.MAX_VALUE,true,true)){while(reads.hasNext()){var read=reads.next();if(membership.putIfAbsent(read.getUId(),taxon)!=null)throw new IOException("Repeated class/read membership");found++;}}
            if(found!=count)throw new IOException("Incomplete source class population");classes.put(taxon,taxon+"\t"+count+"\t"+bits(classification.getWeightedSum(taxon))+"\n");
        }
        var rows=new TreeMap<String,String>();var hits=new TreeMap<String,String>();var uids=new TreeMap<String,String>();long total=0,available=0;var seen=new HashSet<Long>();
        try(var reads=connector.getAllReadsIterator(0f,Float.MAX_VALUE,true,true)) {
            while(reads.hasNext()) {
                IReadBlock r=reads.next();Integer taxon=membership.get(r.getUId());if(taxon==null||!seen.add(r.getUId()))throw new IOException("Missing/repeated full-read membership");String name=text(r.getReadName());int stored=r.getNumberOfMatches(),have=r.getNumberOfAvailableMatchBlocks();total+=stored;available+=have;
                String row=name+"\t"+text(r.getReadHeader())+"\t"+text(r.getReadSequence())+"\t"+r.getReadLength()+"\t"+r.getReadWeight()+"\t"+stored+"\t"+have+"\t"+taxon+"\n";if(rows.putIfAbsent(name,row)!=null)throw new IOException("Nonunique source read name");uids.put(name,r.getUId()+"\t"+name+"\t"+taxon+"\n");
                var matches=new StringBuilder();for(int i=0;i<have;i++){var m=r.getMatchBlock(i);matches.append(name).append('\t').append(i).append('\t').append(m.getTaxonId()).append('\t').append(bits(m.getBitScore())).append('\t').append(bits(m.getExpected())).append('\t').append(bits(m.getPercentIdentity())).append('\t').append(m.isIgnore()).append('\t').append(text(m.getRefSeqId())).append('\t').append(text(m.getText())).append('\t').append(m.getAlignedQueryStart()).append('\t').append(m.getAlignedQueryEnd()).append('\t').append(m.getRefLength()).append('\n');}hits.put(name,matches.toString());
            }
        }
        if(rows.size()!=reportedReads||total!=reportedMatches||available!=total||!seen.equals(membership.keySet()))throw new IOException("Incomplete source header/read/match/classification populations");
        Files.writeString(out.resolve("reads.tsv"),"name_base64\theader_base64\tsequence_base64\tlength\tweight\tstored_matches\tavailable_matches\tstored_taxon\n"+String.join("",rows.values()));Files.writeString(out.resolve("matches.tsv"),"name_base64\tindex\ttaxon\tbit_score_bits\texpected_bits\tidentity_bits\tignored\treference_base64\ttext_base64\taligned_start\taligned_end\treference_length\n"+String.join("",hits.values()));Files.writeString(out.resolve("classes.tsv"),"taxon\treads\tweighted_reads_bits\n"+String.join("",classes.values()));Files.writeString(out.resolve("identities.tsv"),"uid\tname_base64\tstored_taxon\n"+String.join("",uids.values()));Files.writeString(out.resolve("counts.tsv"),"reads\t"+reportedReads+"\nstored_matches\t"+reportedMatches+"\navailable_matches\t"+available+"\n");System.out.println("PASS_COMPLETE_ORIGINAL_MALT052_RMA_FIELDS_ONLY reads="+reportedReads+" matches="+reportedMatches);
    }
}
