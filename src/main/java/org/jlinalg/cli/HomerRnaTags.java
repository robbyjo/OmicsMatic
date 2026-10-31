/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jlinalg.sequence.HomerCoverage;

/** Complete original tag-directory populations, independent of row coalescing/comparator aliases. */
final class HomerRnaTags {
    private HomerRnaTags() { }
    private static final Set<String> QC=Set.of("tagInfo.txt","tagAutocorrelation.txt","tagCountDistribution.txt","tagLengthDistribution.txt");
    record Role(String name,Path directory,Set<String> contigs,long serializedRows,long halfUnits,
                Map<String,String> metadata,Map<Path,String> hashes) {
        Role {contigs=Set.copyOf(contigs);metadata=Map.copyOf(metadata);hashes=Map.copyOf(hashes);}
        void verify()throws IOException {
            var found=new HashSet<Path>();try(var paths=Files.list(directory)){for(var path:paths.toList()){if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path))throw new IOException("Changed/linked original tag-directory family");found.add(path);}}
            if(!found.equals(hashes.keySet()))throw new IOException("Original tag-directory file inventory changed");for(var e:hashes.entrySet())if(!FollowupSupport.hash(e.getKey()).equals(e.getValue()))throw new IOException("Original tag-directory member changed: "+e.getKey());
        }
    }
    record Audited(Map<String,Role> roles,HomerTagLedger.Summary actual,Path ledger) {
        Audited {roles=Map.copyOf(roles);}
        void verify()throws IOException{for(var role:roles.values())role.verify();if(!FollowupSupport.hash(ledger).equals(actual.sha256()))throw new IOException("Native actual tag ledger changed after its complete population audit");}
    }
    private record Population(long rows,long halfUnits) { }
    static Audited audit(HomerRnaInputs.Data data,Map<String,Path> directories)throws Exception {
        var expectedRoles=new HashSet<String>();for(var replicate:data.replicates())expectedRoles.add(replicate.alias());if(!expectedRoles.equals(directories.keySet()))throw new IOException("Every individual replicate tag role is required");
        var ledger=new HomerTagLedger(data.work().resolve("native-actual-chunks"),data.limits().chunkKeys(),256,data.limits().records(),data.limits().workspace(),data::check);var roles=new LinkedHashMap<String,Role>();
        for(String name:new java.util.TreeSet<>(directories.keySet())) {
            Set<String> contigs=data.replicates().stream().filter(r->r.alias().equals(name)).findFirst().orElseThrow().contigs();long half=data.expected().roleHalfUnits().get(name);var role=readRole(data,name,directories.get(name),contigs,half,ledger);roles.put(name,role);
        }
        var actual=ledger.finish(data.work().resolve("native-actual-tags.tsv"));if(!actual.roleHalfUnits().equals(data.expected().roleHalfUnits())||actual.fullKeys()!=data.expected().fullKeys()||Files.mismatch(data.work().resolve("native-expected-tags.tsv"),data.work().resolve("native-actual-tags.tsv"))!=-1)throw new IOException("Complete original tag population differs from every eligible SAM full-key weighted population; inspect both native tag ledgers. Float32 coalescing losses are not silently tolerated");
        var result=new Audited(roles,actual,data.work().resolve("native-actual-tags.tsv"));result.verify();data.verify();return result;
    }
    private static Role readRole(HomerRnaInputs.Data data,String role,Path supplied,Set<String> expectedContigs,long expectedHalf,HomerTagLedger ledger)throws Exception {
        Path directory=supplied.toAbsolutePath().normalize();if(!directory.startsWith(data.work())||!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS)||!directory.toRealPath().equals(directory))throw new IOException("Require a new owned real original tag directory");var expectedFiles=new HashSet<>(QC);for(String name:expectedContigs)expectedFiles.add(name+".tags.tsv");var hashes=new LinkedHashMap<Path,String>();
        try(var paths=Files.list(directory)){for(var path:paths.toList()){if(!expectedFiles.remove(path.getFileName().toString()))throw new IOException("Unexpected original tag-directory file: "+path);HomerFasta.regular(path,data.limits().workspace());hashes.put(path,FollowupSupport.hash(path));}}if(!expectedFiles.isEmpty())throw new IOException("Original tag directory missing required tag/metadata/QC files: "+expectedFiles);
        Path info=directory.resolve("tagInfo.txt");var populations=new TreeMap<String,Population>();var metadata=new TreeMap<String,String>();Population genome=null;
        try(var in=HomerCoreAudit.reader(info)){
            if(!"name\tUnique Positions\tTotal Tags".equals(HomerCoreAudit.next(in,data.limits().line())))throw new IOException("Original tagInfo header differs");
            for(String line;(line=HomerCoreAudit.next(in,data.limits().line()))!=null;){String[] f=line.split("\t",-1);if(f.length!=3)throw new IOException("Original tagInfo row width differs");int split=f[0].indexOf('=');if(split>=0){if(!f[1].isEmpty()||!f[2].isEmpty()||metadata.putIfAbsent(f[0].substring(0,split),f[0].substring(split+1))!=null)throw new IOException("Original tagInfo metadata duplicated/malformed");continue;}
                var value=new Population(whole(f[1],1,data.limits().records()),half(f[2]));if(f[0].equals("genome")){if(genome!=null)throw new IOException("Duplicate original genome population");genome=value;}else if(!expectedContigs.contains(f[0])||populations.putIfAbsent(f[0],value)!=null)throw new IOException("Original tagInfo chromosome population differs");
            }
        }
        if(genome==null||genome.halfUnits()!=expectedHalf||!populations.keySet().equals(expectedContigs)||!"150".equals(metadata.get("fragmentLengthEstimate"))||!metadata.keySet().equals(Set.of("fragmentLengthEstimate","peakSizeEstimate","tagsPerBP","averageTagsPerPosition","medianTagsPerPosition","averageTagLength","gsizeEstimate","averageFragmentGCcontent","cmd")))throw new IOException("Original fixed150bp diagnostic-estimate tagInfo mode, fields or complete population differs");long rows=0,mass=0,genomeEstimate=0;
        for(String contig:populations.keySet()) {
            Path path=directory.resolve(contig+".tags.tsv");long count=0,units=0;int previous=0;
            try(var in=HomerCoreAudit.reader(path)){for(String line;(line=HomerCoreAudit.next(in,data.limits().line()))!=null;){if(++count>data.limits().records())throw new IOException("Original tag record capacity exceeded");if((count&4095)==0)data.check();var tag=tag(line,contig,data.reference().get(contig));if(tag.position1()<previous)throw new IOException("Original serialized tags are not position ordered");previous=tag.position1();long value=(long)(2.0*tag.weight());units=Math.addExact(units,value);ledger.add(new HomerTagLedger.Key(role,contig,tag.position1(),tag.strand(),tag.storedLength()),value);}}
            var declared=populations.get(contig);if(count!=declared.rows()||units!=declared.halfUnits())throw new IOException("Original chromosome tagInfo serialized-row/mass population mismatch: "+contig);rows=Math.addExact(rows,count);mass=Math.addExact(mass,units);genomeEstimate=Math.addExact(genomeEstimate,previous);
        }
        if(rows!=genome.rows()||mass!=genome.halfUnits()||!Long.toString(genomeEstimate).equals(metadata.get("gsizeEstimate")))throw new IOException("Original whole-directory serialized-row/mass/tag-anchor-genome-estimate mismatch");var result=new Role(role,directory,expectedContigs,rows,mass,metadata,hashes);result.verify();return result;
    }
    static List<HomerCoverage.Tag> chromosome(HomerRnaInputs.Data data,Role role,String contig)throws IOException {
        if(!role.contigs().contains(contig))throw new IOException("Unknown audited tag chromosome");Path path=role.directory().resolve(contig+".tags.tsv");var rows=new ArrayList<HomerCoverage.Tag>();
        if(!FollowupSupport.hash(path).equals(role.hashes().get(path)))throw new IOException("Audited original tag file changed before native replay");try(var in=HomerCoreAudit.reader(path)){for(String line;(line=HomerCoreAudit.next(in,data.limits().line()))!=null;){if(rows.size()>=data.limits().chromosomeTags())throw new IOException("Original per-chromosome serialized-tag memory capacity exceeded; raise maximum-chromosome-tags with sufficient Java heap");if((rows.size()&4095)==0)data.check();rows.add(tag(line,contig,data.reference().get(contig)));}}if(!FollowupSupport.hash(path).equals(role.hashes().get(path)))throw new IOException("Audited original tag file changed during native replay");return List.copyOf(rows);
    }
    private static HomerCoverage.Tag tag(String text,String contig,long reference)throws IOException {
        String[] f=text.split("\t",-1);if(f.length!=6||!f[0].isEmpty()||!f[1].equals(contig)||!Set.of("0","1").contains(f[3]))throw new IOException("Malformed original six-column SAM tag row");int position=(int)whole(f[2],1,reference),length=(int)whole(f[5],1,1000000000);long units=half(f[4]);float weight=Float.parseFloat(f[4]);if(2.0*weight!=units)throw new IOException("Serialized original tag weight loses exact half-unit identity");return new HomerCoverage.Tag(position,f[3].equals("0")?'+':'-',length,weight);
    }
    private static long half(String text)throws IOException{try{if(!text.matches("[0-9]+\\.[05]"))throw new NumberFormatException();var value=new java.math.BigDecimal(text).multiply(java.math.BigDecimal.valueOf(2));long units=value.longValueExact();if(units<1||units>200000000)throw new NumberFormatException();return units;}catch(ArithmeticException|NumberFormatException failure){throw new IOException("Original tag mass must be canonical positive half units within the complete SAM capacity",failure);}}
    private static long whole(String text,long minimum,long maximum)throws IOException{try{if(!text.matches("[0-9]+"))throw new NumberFormatException();long value=Long.parseLong(text);if(value<minimum||value>maximum)throw new NumberFormatException();return value;}catch(NumberFormatException failure){throw new IOException("Original tag population integer outside explicit bounds",failure);}}
}
