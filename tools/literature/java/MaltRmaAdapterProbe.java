/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.metagenomics.MaltRmaReader;
import org.jlinalg.metagenomics.MaltTaxonomy;

/** Full typed-adapter comparison and refusal controls; no expected data are used as inputs. */
public final class MaltRmaAdapterProbe {
 private MaltRmaAdapterProbe(){ }
 private static final MaltRmaReader.Limits LIMITS=new MaltRmaReader.Limits(1000000000,1000000,10000000,100000,10000000,1000000);
 private static String b64(String value){return value==null?"NULL":Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
 private static String bits(float value){return Integer.toHexString(Float.floatToRawIntBits(value));}
 public static void main(String[] args)throws Exception {
  if(args.length!=4)throw new IOException("Require provider, complete RMA export directory, original taxonomy paths and NEW output directory");Path provider=Path.of(args[0]),data=Path.of(args[1]),out=Path.of(args[3]);Files.createDirectory(out);
  var tree=MaltRmaReader.defaultTaxonomy(provider,new MaltTaxonomy.Limits(100000000,3000000,1024));if(tree.size()!=1703609 || tree.root()!=1)throw new IOException("Wrong complete default taxonomy");
  try(var reader=Files.newBufferedReader(Path.of(args[2]))) {
   if(!reader.readLine().equals("taxon_id\tparent_id\tname_base64\trank\taddress_char_codes"))throw new IOException("Wrong taxonomy witness schema");String line;
   while((line=reader.readLine())!=null){String[] f=line.split("\t",-1);int id=Integer.parseInt(f[0]);if(tree.parent(id)!=Integer.parseInt(f[1]))throw new IOException("Default tree parent differs");String address=tree.address(id);var codes=new StringBuilder();for(int i=0;i<address.length();i++){if(i>0)codes.append(',');codes.append((int)address.charAt(i));}if(!codes.toString().equals(f[4]))throw new IOException("Default source address differs");}
  }
  long reads=0,matches=0;var inputs=new ArrayList<Path>();
  for(String library:List.of("JK2782","JK2802")) {
   Path input=data.resolve("data/"+library+"/"+library+".unmapped.rma6");inputs.add(input);String label=input.getFileName().toString();var members=new TreeMap<Integer,TreeSet<Long>>();MaltRmaReader.Report report;
   try(var readWriter=Files.newBufferedWriter(out.resolve(label+".reads.tsv"));var matchWriter=Files.newBufferedWriter(out.resolve(label+".matches.tsv"))) {
    readWriter.write("uid\tread_name_base64\theader_base64\tsequence_base64\tread_length\tread_weight\tnumber_of_matches\tavailable_matches\n");matchWriter.write("uid\tmatch_index\ttaxon_id\tbit_score_bits\texpected_bits\tpercent_identity_bits\tignored\treference_base64\ttext_base64\taligned_query_start\taligned_query_end\treference_length\n");
    report=MaltRmaReader.read(provider,input,LIMITS,read->{try {
     members.computeIfAbsent(read.storedTaxon(),ignored->new TreeSet<>()).add(read.uid());readWriter.write(read.uid()+"\t"+b64(read.name())+"\t"+b64(read.header())+"\t"+b64(read.sequence())+"\t"+read.length()+"\t"+read.weight()+"\t"+read.totalMatches()+"\t"+read.matches().size()+"\n");
     for(var match:read.matches())matchWriter.write(read.uid()+"\t"+match.index()+"\t"+match.taxon()+"\t"+bits(match.bitScore())+"\t"+bits(match.expected())+"\t"+bits(match.percentIdentity())+"\t"+match.ignored()+"\t"+b64(match.reference())+"\t"+b64(match.text())+"\t"+match.alignedStart()+"\t"+match.alignedEnd()+"\t"+match.referenceLength()+"\n");
    }catch(IOException error){throw new UncheckedIOException(error);}});
   }
   if(!report.warnings().isEmpty() || report.storedMatches()!=report.availableMatches())throw new IOException("Unexpected control incompleteness/warning");reads+=report.reads();matches+=report.availableMatches();
   try(var writer=Files.newBufferedWriter(out.resolve(label+".classification.tsv"))) {
    writer.write("taxon_id\tclass_sum\tweighted_sum_bits\titerated_reads\tuids\n");for(var row:report.classifications().values()){var uids=members.getOrDefault(row.taxon(),new TreeSet<>());writer.write(row.taxon()+"\t"+row.reads()+"\t"+bits(row.weightedReads())+"\t"+uids.size()+"\t"+uids+"\n");}
   }
   Files.writeString(out.resolve(label+".counts.tsv"),"reads\t"+report.reads()+"\nmatches\t"+report.availableMatches()+"\n");
  }
  if(reads!=172 || matches!=175)throw new IOException("Incomplete control population");int refusals=0;Path input=inputs.get(0);
  try{MaltRmaReader.read(out.resolve("missing-provider"),input,LIMITS,read->{});throw new AssertionError("Missing runtime accepted");}catch(MaltRmaReader.MissingRuntimeException expected){if(!expected.getMessage().contains("SKIP3") || !expected.getMessage().contains("Required action:"))throw expected;refusals++;}
  for(var limits:List.of(new MaltRmaReader.Limits(1,1000000,10000000,100000,10000000,1000000),new MaltRmaReader.Limits(1000000000,1,10000000,100000,10000000,1000000),new MaltRmaReader.Limits(1000000000,1000000,1,100000,10000000,1000000),new MaltRmaReader.Limits(1000000000,1000000,10000000,1,10000000,1000000),new MaltRmaReader.Limits(1000000000,1000000,10000000,100000,1,1000000),new MaltRmaReader.Limits(1000000000,1000000,10000000,100000,10000000,1))) {
   try{MaltRmaReader.read(provider,input,limits,read->{});throw new AssertionError("Incomplete capacity accepted");}catch(IOException expected){if(!expected.getMessage().contains("Required action:"))throw expected;refusals++;}
  }
  for(int bytes:new int[]{0,16,256}) {
   Path corrupt=out.resolve("truncated-"+bytes+".rma6");try(InputStream stream=Files.newInputStream(input)){Files.write(corrupt,stream.readNBytes(bytes));}
   try{MaltRmaReader.read(provider,corrupt,LIMITS,read->{});throw new AssertionError("Truncated RMA accepted");}catch(IOException expected){refusals++;}
  }
  try{MaltRmaReader.defaultTaxonomy(provider,new MaltTaxonomy.Limits(1,3000000,1024));throw new AssertionError("Truncated taxonomy accepted");}catch(IOException expected){refusals++;}
  try{MaltRmaReader.read(provider,input,LIMITS,read->{throw new IllegalStateException("deliberate-callback-stop");});throw new AssertionError("Callback stop ignored");}catch(IllegalStateException expected){if(!expected.getMessage().equals("deliberate-callback-stop"))throw expected;refusals++;}
  if(refusals!=12)throw new IOException("Missing refusal control");Files.writeString(out.resolve("probe-counts.tsv"),"reads\t"+reads+"\nmatches\t"+matches+"\nrefusals\t"+refusals+"\n");System.out.println("PASS_TYPED_RMA_ADAPTER_COMPLETE_FIELDS_AND_REFUSALS_ONLY");
 }
}
