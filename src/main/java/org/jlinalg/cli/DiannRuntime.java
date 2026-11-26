/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Read-only exact separately installed Linux originals; no download or licence acceptance. */
final class DiannRuntime {
    static final class Unavailable extends IOException {private static final long serialVersionUID=1L;Unavailable(String message){super(message);}}
    private final Path home;private final String edition,program;private final Map<?,?> profile;
    private final Map<String,Pin> helperPins=new TreeMap<>();private final Map<String,String> attributes=new TreeMap<>();
    DiannRuntime(Path supplied,String edition)throws IOException {
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Unavailable("Original DIA-NN execution requires Linux x86_64 Java. On Windows use Linux Java inside WSL, or skip this dependent analysis; Windows Java supports saved readback");
        profile=profile(edition);this.edition=edition;program=text(profile.get("program"));
        if(supplied==null)throw new Unavailable("Supply --home for the separately permitted exact original DIA-NN release. This analysis does not install software or accept licence terms");
        home=supplied.toAbsolutePath().normalize();if(!Files.isDirectory(home,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Original private DIA-NN runtime directory is unavailable: "+home+". Install separately where permitted, or retain SKIP3");directory(home);
        for(String name:List.of("/usr/bin/setsid","/usr/bin/prlimit")){Path path=Path.of(name);if(!Files.isRegularFile(path)||!Files.isExecutable(path))throw new Unavailable("Missing permitted Linux process helper: "+name+"; install separately where allowed or skip");helperPins.put(name,pin(path,16777216,()->{}));}
        if(!Files.isReadable(Path.of("/proc/self/stat"))||!Files.isReadable(Path.of("/proc/self/status")))throw new Unavailable("Readable Linux /proc process metadata is required; dependent analysis skipped");
        for(Object raw:object(profile.get("files")).keySet()){String name=text(raw);if(!Files.isRegularFile(home.resolve(name),LinkOption.NOFOLLOW_LINKS)||!Files.isReadable(home.resolve(name)))throw new Unavailable("Exact original runtime member is missing/unreadable: "+name+". Supply the complete selected private release; skip dependent analysis meanwhile");}
        verify();for(Object raw:object(profile.get("files")).keySet()){String name=text(raw);attributes.put(name,attribute(home.resolve(name)));}
        if(!Files.isExecutable(program()))throw new Unavailable("Original program lacks executable permission; install the unmodified executable with permitted execute access, or retain the dependent-analysis skip");
    }
    static Map<?,?> profile(String edition)throws IOException {
        try(var input=DiannRuntime.class.getResourceAsStream("/org/jlinalg/cli/diann-tools/runtime-profiles.json")){
            if(input==null)throw new IOException("Missing packaged original runtime profiles");byte[] raw=input.readNBytes(1048577);require(raw.length<=1048576,"Runtime profile capacity exceeded");var document=object(SimpleJson.parse(utf8(raw)));keys(document,"schema_version","editions");integer(document.get("schema_version"),1,1);var editions=object(document.get("editions"));if(!editions.containsKey(edition))throw new Unavailable("Unqualified exact DIA-NN edition: "+edition+". Qualified original releases are1.8,1.8.1,2.0.2; no nearest version is inferred");return object(editions.get(edition));
        }
    }
    Path home(){return home;}Path program(){return home.resolve(program);}
    void verify()throws IOException {
        var expected=object(profile.get("files"));var found=new HashSet<String>();
        try(var paths=Files.list(home)){for(Path path:paths.limit(33).toList()){String name=path.getFileName().toString();require(found.add(name)&&expected.containsKey(name)&&!Files.isSymbolicLink(path),"Extra/linked original runtime member");require(pin(path,536870912,()->{}).equals(fingerprint(expected.get(name),536870912)),"Changed or wrong-edition original runtime file: "+name);}}
        require(found.equals(expected.keySet()),"Incomplete original runtime namespace");for(var e:helperPins.entrySet())require(e.getValue().equals(pin(Path.of(e.getKey()),16777216,()->{})),"Linux execution helper changed");
    }
    void quickVerify()throws IOException {for(var e:attributes.entrySet())require(e.getValue().equals(attribute(home.resolve(e.getKey()))),"Original runtime changed while executing");}
    private static String attribute(Path path)throws IOException {var a=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);require(a.isRegularFile()&&!a.isSymbolicLink(),"Runtime member changed type");return a.size()+":"+a.lastModifiedTime()+":"+a.fileKey();}
    Map<String,Object> provenance(){return Map.of("edition",edition,"original_program",program,"original_files",profile.get("files"),"linux_helpers",helperPins,"original_binaries_modified",false);}
    Map<String,String> environment(int threads){return Map.of("PATH","/usr/bin:/bin","LANG","C","LC_ALL","C","LD_LIBRARY_PATH",home.toString(),"OMP_NUM_THREADS",Integer.toString(threads),"OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS",Integer.toString(threads));}
    private static void require(boolean condition,String message)throws IOException{if(!condition)throw new IOException(message);}
}
