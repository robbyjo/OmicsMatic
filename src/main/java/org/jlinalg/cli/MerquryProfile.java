/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;

/** Explicit original editions and separately selected native source profile; no fallback. */
final class MerquryProfile {
    static final String NATIVE="native-boundary-palette-v1",ORIGINAL="original";
    static final String RESOURCE="/org/jlinalg/omics/merqury/";
    private MerquryProfile() { }
    static byte[] resource(String name)throws IOException {
        try(var input=MerquryProfile.class.getResourceAsStream(RESOURCE+member(name))){if(input==null)throw new IOException("Missing packaged Merqury resource: "+name);byte[] value=input.readNBytes(16777217);require(value.length<=16777216,"Packaged Merqury resource exceeds capacity");return value;}
    }
    static Map<?,?> profiles()throws IOException {return object(SimpleJson.parse(utf8(resource("profiles.json"))));}
    static Map<?,?> edition(String edition)throws IOException {var result=object(profiles().get(edition));require(Set.of("1.1","1.3","1.4.1").contains(edition),"Unsupported explicit Merqury edition");return result;}
    static void selection(String edition,String mode)throws IOException {edition(edition);require(mode.equals(ORIGINAL)||edition.equals("1.4.1")&&mode.equals(NATIVE),"Native source modification must be explicitly selected for1.4.1; no automatic substitution");}
    static void source(Path root,String edition,Runnable check)throws IOException {
        var expected=object(edition(edition).get("files"));var actual=tree(root,10000,268435456,16777216,check);require(actual.keySet().equals(expected.keySet()),"Complete original Merqury source member population changed");
        for(var row:actual.entrySet())require(row.getValue().kind().equals("file")&&row.getValue().pin().equals(fingerprint(expected.get(row.getKey()),16777216)),"Changed original Merqury source member: "+row.getKey());
    }
    static void copySource(Path original,Path target,String edition,String mode,Runnable check)throws IOException {
        selection(edition,mode);source(original,edition,check);Files.createDirectory(target);
        for(Object key:object(edition(edition).get("files")).keySet()) {
            check.run();String name=member(text(key));Path output=target.resolve(name);Files.createDirectories(output.getParent());Files.copy(original.resolve(name),output);
            if(name.endsWith(".sh"))require(output.toFile().setExecutable(true,false),"Cannot permit captured original Merqury script execution");
        }
        if(mode.equals(NATIVE)) {
            Path filt=target.resolve("build/filt.sh");String text=Files.readString(filt,StandardCharsets.UTF_8),old="java -jar -Xmx1g $MERQURY/eval/kmerHistToPloidyDepth.jar $db.hist";
            require(text.split(java.util.regex.Pattern.quote(old),-1).length==3&&text.split(java.util.regex.Pattern.quote(".hist.ploidy"),-1).length==7,"Explicit native profile source anchors differ");
            text=text.replace(old,"java -Xmx1g -cp \"$OMICS_MERQURY_CLASSPATH\" org.jlinalg.cli.MerquryBoundaryCli \"$db.hist\"").replace(".hist.ploidy",".hist.boundary.tsv");Files.writeString(filt,text,StandardCharsets.UTF_8);
            Files.write(target.resolve("plot/plot_spectra_cn.R"),resource("plot_spectra_cn_native.R"));
        }
        source(original,edition,check);
    }
    static void capturedSource(Path root,String edition,String mode,Runnable check)throws IOException {
        selection(edition,mode);var expected=object(edition(edition).get("files"));var actual=tree(root,10000,268435456,16777216,check);require(actual.keySet().equals(expected.keySet()),"Complete captured Merqury source population changed");
        for(var row:actual.entrySet()) {
            require(row.getValue().kind().equals("file"),"Captured source links refused");String name=row.getKey();
            if(mode.equals(NATIVE)&&name.equals("plot/plot_spectra_cn.R")){byte[] value=resource("plot_spectra_cn_native.R");require(row.getValue().pin().equals(new Pin(value.length,sha(value))),"Native plot source differs");}
            else if(mode.equals(NATIVE)&&name.equals("build/filt.sh")) {
                String current=Files.readString(root.resolve(name),StandardCharsets.UTF_8),original=current.replace("java -Xmx1g -cp \"$OMICS_MERQURY_CLASSPATH\" org.jlinalg.cli.MerquryBoundaryCli \"$db.hist\"","java -jar -Xmx1g $MERQURY/eval/kmerHistToPloidyDepth.jar $db.hist").replace(".hist.boundary.tsv",".hist.ploidy");byte[] value=original.getBytes(StandardCharsets.UTF_8);
                require(new Pin(value.length,sha(value)).equals(fingerprint(expected.get(name),16777216)),"Native threshold source changes exceed the explicit profile");
            }else require(row.getValue().pin().equals(fingerprint(expected.get(name),16777216)),"Changed captured original source member: "+name);
        }
    }
}
