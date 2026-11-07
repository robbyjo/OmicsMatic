/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed report-import probe; requested text analyses never require this capability. */
final class MemePerl {
    private MemePerl() { }
    private static final String PROBE="""
        use strict;
        use warnings;
        use Cwd qw(abs_path);
        use Fcntl qw(O_RDONLY SEEK_SET);
        use File::Basename qw(fileparse);
        use File::Spec::Functions qw(tmpdir);
        use File::Temp qw(tempfile);
        use Getopt::Long;
        use Pod::Usage;
        use XML::Parser::Expat;
        BEGIN { require lib; lib->import($ARGV[0]); }
        unshift(@INC, $ARGV[1]);
        require HtmlMonolithWr;
        require TomtomSAX;
        require DynaLoader;
        print "VERSION\\t$^V\\n";
        for my $path (@INC) { die "Non-path module hook" if ref($path); print "INC\\t$path\\n"; }
        for my $name (sort keys %INC) { print "MODULE\\t$name\\t$INC{$name}\\n"; }
        for my $path (@DynaLoader::dl_shared_objects) { print "DYNAMIC\\t$path\\n"; }
        """;
    record Receipt(String version,List<String> roots,Map<String,String> modules,List<String> dynamicImages) {
        Receipt {roots=List.copyOf(roots);modules=Map.copyOf(modules);dynamicImages=List.copyOf(dynamicImages);}
        Map<String,Object> document(){return Map.of("perl_version",version,"ordered_module_roots",roots,"loaded_modules",modules,"loaded_native_images",dynamicImages,"scope","Fixed original report imports and complete configured module roots; full report health must also run fresh. No scientific fit or text-mode dependency is implied.");}
    }
    static List<String> arguments(Path home){return List.of("/usr/bin/perl","-e",PROBE,home.resolve("lib/meme-5.5.9/perl").toString(),home.resolve("libexec/meme-5.5.9").toString());}
    static boolean unavailableImport(IOException failure,byte[] stderr)throws IOException {
        // Only a clean original import exit plus a named missing module/loader
        // dependency is a SKIP. Integrity, cleanup and capacity failures remain
        // failures even if the process happened to print an import message.
        if(failure.getSuppressed().length!=0||!("Original RSEM process failed, exit 2".equals(failure.getMessage())||"Original RSEM process failed, exit 255".equals(failure.getMessage())))return false;
        String errors=NextflowFiles.utf8(stderr);if(errors.contains("Out of memory")||errors.contains("Cannot allocate memory")||errors.contains("CPU time limit")||errors.contains("File size limit"))return false;
        return java.util.regex.Pattern.compile("Can't locate [A-Za-z0-9_/-]+\\.pm in @INC").matcher(errors).find()
            ||errors.contains("for module XML::Parser::Expat:")&&(errors.contains("cannot open shared object file")||errors.contains("Permission denied"));
    }
    static Receipt parse(byte[] stdout,byte[] stderr)throws IOException {
        require(stderr.length==0,"Original Perl report-import probe emitted errors/warnings; inspect the retained output and supply permitted report dependencies");String text=NextflowFiles.utf8(stdout);require(text.endsWith("\n")&&text.indexOf('\r')<0,"Incomplete fixed Perl module report");
        String version=null;var roots=new ArrayList<String>();var modules=new java.util.LinkedHashMap<String,String>();var images=new ArrayList<String>();Set<String> imageNames=new HashSet<>();
        for(String line:text.split("\n")){
            String[] fields=line.split("\t",-1);
            if(fields.length==2&&fields[0].equals("VERSION")){require(version==null&&fields[1].matches("v5\\.[0-9]+\\.[0-9]+"),"Unsupported/duplicate original Perl version report");version=fields[1];}
            else if(fields.length==2&&fields[0].equals("INC")){path(fields[1]);roots.add(fields[1]);}
            else if(fields.length==3&&fields[0].equals("MODULE")){require(!fields[1].isEmpty()&&fields[1].chars().noneMatch(c->c<33||c==127),"Invalid fixed Perl module identifier");path(fields[2]);require(modules.putIfAbsent(fields[1],fields[2])==null,"Duplicate original Perl module report");}
            else if(fields.length==2&&fields[0].equals("DYNAMIC")){path(fields[1]);require(imageNames.add(fields[1]),"Duplicate Perl native-image report");images.add(fields[1]);}
            else throw new IOException("Unknown fixed Perl report-import record");
        }
        require(version!=null&&!roots.isEmpty()&&roots.size()<=64&&modules.size()<=2000&&images.size()<=128,"Incomplete/excessive original Perl import population");
        require(modules.keySet().containsAll(Set.of("XML/Parser/Expat.pm","HtmlMonolithWr.pm","TomtomSAX.pm","JsonWr.pm","CheckingSAX.pm","File/Temp.pm"))&&!images.isEmpty(),"Original report imports omitted required modules/native XML parser");
        return new Receipt(version,roots,modules,images);
    }
    private static void path(String text)throws IOException {
        require(text.startsWith("/")&&text.length()<=32768&&text.chars().noneMatch(c->c<32||c==127),"Unreviewed relative/control-character Perl lookup path");
        // Receipt paths are producer Linux text even during a Windows audit.
        if(!text.equals("/"))for(String part:text.substring(1).split("/",-1))require(!part.isEmpty()&&!part.equals(".")&&!part.equals(".."),"Non-normalized original Perl lookup path");
    }
    private static void require(boolean condition,String message)throws IOException {MemeRuntimeFiles.require(condition,message);}
}
