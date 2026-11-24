/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Capture only the declared files and exact reference-prefix populations. */
final class GenomicsemPrepBundle {
    private GenomicsemPrepBundle() { }
    private static Path address(Path base,String name)throws IOException {GenomicsemPrepContracts.relative(name);Path p=base.resolve(name).normalize();require(p.startsWith(base),"Input path leaves declared bundle");return p;}
    private static void physical(Path p)throws IOException {
        if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))throw new GenomicsemRuntime.Unavailable("Missing exact preparation input: "+p);
        require(!Files.isSymbolicLink(p)&&p.toRealPath().equals(p.toAbsolutePath().normalize()),"Input alias/reparse point is not accepted: "+p);
    }
    private static String name(Path base,Path file)throws IOException {physical(file);require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),"Require a regular source input: "+file);String n=base.relativize(file).toString().replace('\\','/');relative(n);return n;}
    static List<Integer> chromosomes(Map<String,Object>request)throws IOException {
        var o=object(request.get("options"));int count=Math.toIntExact(number(o,"chromosomes"));Object selection=o.get("select");var out=new ArrayList<Integer>();
        if(selection instanceof List<?>)for(Object v:list(selection))out.add(Math.toIntExact(integer(v,1,count)));
        else for(int i=1;i<=count;i++)if(Boolean.FALSE.equals(selection)||selection.equals("ODD")&&i%2==1||selection.equals("EVEN")&&i%2==0)out.add(i);
        require(!out.isEmpty(),"No chromosomes selected by the original request");return out;
    }
    private static void file(Path base,Set<String>names,String value)throws IOException {names.add(name(base,address(base,value)));}
    private static void files(Path base,Set<String>names,Object values)throws IOException {for(Object v:list(values))file(base,names,text(v,"source file"));}
    private static Set<Integer> prefix(Path base,Set<String>names,String prefix,List<String>suffixes)throws IOException {
        Path full=address(base,prefix),parent=prefix.endsWith("/")?full:full.getParent();physical(parent);require(Files.isDirectory(parent),"Reference prefix parent is not a directory");String stem=prefix.endsWith("/")?"":full.getFileName().toString();Set<Integer> chromosomes=new TreeSet<>();Map<String,Set<Integer>> populations=new LinkedHashMap<>();for(String suffix:suffixes)populations.put(suffix,new TreeSet<>());
        try(var stream=Files.list(parent)){
            for(Path p:stream.toList()){
                String leaf=p.getFileName().toString();if(!leaf.startsWith(stem))continue;
                boolean matched=false;
                for(String suffix:suffixes)if(leaf.endsWith(suffix)||suffix.equals(".l2.ldscore.gz")&&leaf.endsWith(".l2.ldscore")){
                    int ending=leaf.endsWith(suffix)?suffix.length():".l2.ldscore".length();String middle=leaf.substring(stem.length(),leaf.length()-ending);if(middle.startsWith("."))middle=middle.substring(1);require(middle.matches("[1-9][0-9]?"),"Reference prefix matched a nonstandard chromosome member: "+leaf);int chromosome=Integer.parseInt(middle);require(chromosome<=22&&populations.get(suffix).add(chromosome),"Duplicated/out-of-range reference chromosome or both compressed/plain LD scores");names.add(name(base,p));chromosomes.add(chromosome);matched=true;break;
                }
                if(!matched)for(String suffix:suffixes)require(!(suffix.equals(".l2.ldscore.gz")&&leaf.contains("l2.ldscor")||suffix.equals(".l2.M_5_50")&&leaf.endsWith("M_5_50")||suffix.equals(".annot.gz")&&leaf.endsWith("annot.gz")||suffix.equals(".frq")&&leaf.endsWith(".frq")),"Original prefix glob would include an unsupported member; no silently reduced source population: "+leaf);
            }
        }
        require(!chromosomes.isEmpty(),"No chromosome files match declared reference prefix: "+prefix);
        for(var population:populations.entrySet())require(population.getValue().equals(chromosomes),"Incomplete chromosome population for "+prefix+"*"+population.getKey());return chromosomes;
    }
    static Set<String> selected(Path bundle,Map<String,Object>request)throws IOException {
        Path base=bundle.toAbsolutePath().normalize();physical(base);require(Files.isDirectory(base),"Bundle must be a physical input directory");var paths=object(request.get("paths"));var options=object(request.get("options"));String op=text(request.get("operation"),"operation");Set<String> names=new TreeSet<>();
        if(paths.containsKey("files"))files(base,names,paths.get("files"));if(paths.containsKey("traits"))files(base,names,paths.get("traits"));
        for(String k:List.of("hm3","reference","model"))if(paths.containsKey(k))file(base,names,text(paths.get(k),k));
        if(op.equals("LDSC"))for(int chr:chromosomes(request)){
            Path ld=address(base,text(paths.get("ld"),"ld"));physical(ld);file(base,names,base.relativize(ld.resolve(chr+".l2.ldscore.gz")).toString().replace('\\','/'));file(base,names,base.relativize(ld.resolve(chr+".l2.M_5_50")).toString().replace('\\','/'));
            if(GenomicsemPrepContracts.bool(options,"separate_weights")){Path w=address(base,text(paths.get("wld"),"wld"));physical(w);file(base,names,base.relativize(w.resolve(chr+".l2.ldscore.gz")).toString().replace('\\','/'));}
        }
        if(op.equals("S_LDSC")){
            Set<Integer> expected=null;
            for(Object value:list(paths.get("ld_prefixes"))){var found=prefix(base,names,text(value,"LD prefix"),List.of(".l2.ldscore.gz",".l2.M_5_50",".annot.gz"));if(expected==null)expected=found;else require(expected.equals(found),"LD/annotation prefixes cover different chromosomes");}
            require(Objects.equals(expected,prefix(base,names,text(paths.get("weights_prefix"),"weights prefix"),List.of(".l2.ldscore.gz"))),"Weight/reference chromosome populations differ");require(Objects.equals(expected,prefix(base,names,text(paths.get("frequency_prefix"),"frequency prefix"),List.of(".frq"))),"Frequency/reference chromosome populations differ");
        }
        if(op.equals("ENRICH")){
            Path structured=address(base,text(paths.get("stratified"),"stratified"));physical(structured);require(Files.isDirectory(structured),"Stratified input is the complete typed product directory,not an arbitrary RDS");
            try(var stream=Files.walk(structured)){var cursor=stream.iterator();while(cursor.hasNext()){
                Path p=cursor.next();physical(p);if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))continue;String n=structured.relativize(p).toString().replace('\\','/');require(n.equals("tree.json")||n.matches("tables/table-[0-9]{6}\\.tsv"),"Unexpected member in typed stratified product: "+n);names.add(name(base,p));require(names.size()<=GenomicsemContracts.cap(request,"maximum_files"),"Input file capacity exceeded");
            }}require(Files.isRegularFile(structured.resolve("tree.json")),"Missing typed stratified tree");
        }
        require(!names.isEmpty()&&names.size()<=GenomicsemContracts.cap(request,"maximum_files"),"Empty/excessive preparation input population");return names;
    }
    static Map<String,Object> capture(Path root,Path bundle,Map<String,Object>request)throws IOException {
        Path base=bundle.toAbsolutePath().normalize();var files=new LinkedHashMap<String,Object>();Set<String> names=selected(base,request);
        for(String n:names){Path destination=root.resolve("inputs/bundle/"+n);ScenicWorkflowFiles.capture(base.resolve(n),destination,request,files,"inputs/bundle/"+n);}
        require(names.equals(selected(base,request)),"Source input namespace changed during capture");return files;
    }
    static void verify(Path root,Map<String,Object>request,Map<String,Object>expected)throws IOException {
        var names=selected(root.resolve("inputs/bundle"),request);require(names.stream().map(n->"inputs/bundle/"+n).collect(java.util.stream.Collectors.toSet()).equals(expected.keySet()),"Changed declared input role/prefix population");
        Set<String> actual=new TreeSet<>();try(var stream=Files.walk(root.resolve("inputs/bundle"))){for(Path p:stream.toList()){physical(p);if(Files.isDirectory(p))continue;String n=root.relativize(p).toString().replace('\\','/');actual.add(n);require(same(pin(p),expected.get(n)),"Changed captured input bytes: "+n);}}
        require(actual.equals(expected.keySet()),"Unindexed/omitted preparation inputs");
    }
    static Map<String,BasicFileAttributes> attributes(Path root,Map<String,Object>files)throws IOException {Map<String,BasicFileAttributes> values=new TreeMap<>();for(String n:files.keySet())values.put(n,Files.readAttributes(root.resolve(n),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS));return values;}
    static void unchanged(Path root,Map<String,BasicFileAttributes>expected)throws IOException {for(var e:expected.entrySet()){var a=Files.readAttributes(root.resolve(e.getKey()),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);var b=e.getValue();require(a.isRegularFile()&&!a.isSymbolicLink()&&a.size()==b.size()&&a.lastModifiedTime().equals(b.lastModifiedTime())&&Objects.equals(a.fileKey(),b.fileKey()),"Captured input changed during source execution");}}
}
