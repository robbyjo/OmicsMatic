/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jlinalg.sequence.MemeMotifInputs;
import org.jlinalg.sequence.TomtomResults;

/** Whole unchanged motif capture, source preprocessing and protected complete namespaces. */
final class TomtomInputs {
    private TomtomInputs() { }
    record Original(Path path,NextflowFiles.Identity identity) { }
    record MotifFile(String role,int ordinal,String producerPath,String member,NextflowFiles.Pin pin,MemeMotifInputs.Document document) { }
    record Context(Path root,TomtomRecipe.Request request,List<MotifFile> files,String backgroundMember,
                   TomtomResults.Backgrounds backgrounds,Map<String,Object> protectedTree,List<Original> originals,List<String> warnings) {
        Context {files=List.copyOf(files);protectedTree=Map.copyOf(protectedTree);originals=List.copyOf(originals);warnings=List.copyOf(warnings);}
        void verify(boolean verifyOriginals,Runnable check)throws IOException {
            require(protectedTree.equals(tree(root,request,check)),"Complete Tomtom captured namespace, bytes or modes changed");
            if(verifyOriginals){request.verify(check);for(var original:originals)require(original.identity().equals(NextflowFiles.identity(original.path(),request.capacity("input_bytes"),check)),"Original Tomtom input changed during capture/execution: "+original.path());}
        }
        Path query(){return root.resolve(files.get(0).member());}
        List<String> targets(){return files.stream().filter(f->f.role().equals("target")).map(f->root.resolve(f.member()).toString()).toList();}
        String backgroundFile(){return backgroundMember==null?null:root.resolve(backgroundMember).toString();}
        TomtomResults.Expected expected(List<String> argv)throws IOException {
            TomtomRecipe.nativePath(root.toString());require(!argv.isEmpty(),"Missing typed original Tomtom command");
            int outputIndex=argv.indexOf("-o");String output=outputIndex>=0&&outputIndex+1<argv.size()?argv.get(outputIndex+1):null;
            require(argv.equals(request.arguments(argv.get(0),query().toString(),targets(),backgroundFile(),output)),"Original invocation differs from the complete typed requested Tomtom settings/input carriers");
            var queries=new ArrayList<TomtomResults.Database>();var targets=new ArrayList<TomtomResults.Database>();
            for(var file:files){String source=root.resolve(file.member()).toString();String name=source.substring(source.lastIndexOf('/')+1);if(name.endsWith(".meme"))name=name.substring(0,name.length()-5);
                var database=new TomtomResults.Database(source,name,file.document().entries().stream().map(MemeMotifInputs.Entry::model).toList(),file.role().equals("query")?backgrounds.firstQuery():backgrounds.followingDatabases());
                (file.role().equals("query")?queries:targets).add(database);
            }
            var display=new ArrayList<>(argv);require(!display.isEmpty()&&display.get(0).endsWith("/tomtom"),"Expected a typed original Tomtom command");display.set(0,"tomtom");
            String background=request.background()==TomtomRecipe.Background.QUERY?"--query--":request.background()==TomtomRecipe.Background.UNIFORM?"--uniform--":backgroundFile();
            return new TomtomResults.Expected(String.join(" ",display),request.distance(),request.reverseComplement(),request.thresholdKind()==TomtomRecipe.Threshold.QVALUE?"qvalue":"evalue",request.threshold().doubleValue(),request.pseudocount().doubleValue(),request.minimumOverlap(),request.internal(),background,backgrounds.firstQuery(),queries,targets);
        }
    }

    static Context capture(Path root,TomtomRecipe.Request request,Runnable check)throws IOException {
        request.verify(check);require(!Files.exists(root,LinkOption.NOFOLLOW_LINKS)&&root.isAbsolute()&&root.normalize().equals(root)&&root.getParent()!=null&&root.getParent().toRealPath().equals(root.getParent()),"Require a NEW canonical Tomtom input-capture directory");
        var sources=new ArrayList<String>();sources.add(request.query());sources.addAll(request.targets());if(request.backgroundFile()!=null)sources.add(request.backgroundFile());
        require(!request.source().startsWith(root),"Capture output cannot enclose the original recipe");for(String source:sources)require(!Path.of(source).startsWith(root),"Capture output cannot enclose an original input");
        var originals=new ArrayList<Original>();var identities=new ArrayList<NextflowFiles.Identity>();long bytes=request.sourcePin().bytes();
        for(String name:sources){Path path=Path.of(name);var identity=NextflowFiles.identity(path,request.capacity("input_bytes"),check);bytes=Math.addExact(bytes,identity.pin().bytes());require(bytes<=request.capacity("input_bytes"),"Complete combined Tomtom recipe/motif/background byte capacity exceeded");originals.add(new Original(path,identity));identities.add(identity);}
        Files.createDirectory(root);Files.createDirectory(root.resolve("inputs"));copy(request.source(),root.resolve("recipe.json"),request.sourcePin(),check);
        var files=new ArrayList<MotifFile>();
        for(int i=0;i<request.targets().size()+1;i++){
            check.run();String role=i==0?"query":"target",member="inputs/"+role+"-"+String.format(java.util.Locale.ROOT,"%03d",i)+extension(sources.get(i));
            copy(Path.of(sources.get(i)),root.resolve(member),identities.get(i).pin(),check);
            var doc=MemeMotifInputs.read(root.resolve(member),request.inputLimits(),check);
            require(doc.bytes()==identities.get(i).pin().bytes()&&doc.sha256().equals(identities.get(i).pin().sha256()),"Parsed Tomtom capture identity differs");files.add(new MotifFile(role,i,sources.get(i),member,identities.get(i).pin(),doc));
        }
        String backgroundMember=null;double[] rawBackground;
        if(request.background()==TomtomRecipe.Background.FILE){backgroundMember="inputs/background.txt";int last=sources.size()-1;copy(Path.of(sources.get(last)),root.resolve(backgroundMember),identities.get(last).pin(),check);rawBackground=background(root.resolve(backgroundMember),(int)request.capacity("input_bytes"));}
        else rawBackground=request.background()==TomtomRecipe.Background.UNIFORM?new double[]{.25,.25,.25,.25}:files.get(0).document().background();
        var bg=TomtomResults.backgrounds(rawBackground,request.background()==TomtomRecipe.Background.QUERY);var warnings=preflight(files,request,check);
        Map<String,Object> ledger=new LinkedHashMap<>();ledger.put("status","CAPTURED_INPUTS_ONLY_NO_RUNTIME_OR_FIT_ADMISSION");ledger.put("producer_recipe_sha256",request.sourcePin().sha256());ledger.put("combined_original_bytes",bytes);
        ledger.put("backgrounds",Map.of("original",numbers(bg.original()),"first_query",numbers(bg.firstQuery()),"following_databases",numbers(bg.followingDatabases()),"normalization_passes",bg.normalizationPasses()));ledger.put("background_member",backgroundMember);ledger.put("warnings",warnings);
        ledger.put("files",files.stream().map(f->Map.of("role",f.role(),"ordinal",f.ordinal(),"producer_path",f.producerPath(),"member",f.member(),"bytes",f.pin().bytes(),"sha256",f.pin().sha256(),"format",f.document().format(),"motifs",f.document().entries().size(),"background_rule",f.document().backgroundRule())).toList());
        write(root.resolve("input-ledger.json"),ledger);
        try(var out=Files.newBufferedWriter(root.resolve("motif-ledger.jsonl"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
            for(var file:files)for(var entry:file.document().entries()){
                check.run();out.write(RsemJson.json(Map.of("file",file.member(),"ordinal",entry.ordinal(),"id",entry.model().id(),"alternate_id",entry.model().alternateId(),"sites",entry.model().sites(),"pseudocount_sites",entry.pseudocountSites(),"width",entry.probabilityTokens().size(),"source_metadata",entry.metadata(),"normalized_positions",entry.normalizedPositions())));out.newLine();
            }
        }
        Files.writeString(root.resolve("WARNINGS.md"),"# Tomtom inputs only; no runtime or analysis admitted\n\n"+String.join("\n\n",warnings)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        var context=new Context(root,request,files,backgroundMember,bg,tree(root,request,check),originals,warnings);context.verify(true,check);return context;
    }

    static List<String> preflight(List<MotifFile> files,TomtomRecipe.Request request,Runnable check)throws IOException {
        long motifs=0,scalars=0,queries=0,targets=0;var warnings=new ArrayList<String>();var textIds=new HashSet<String>();
        for(var file:files){var ids=new HashSet<String>();warnings.addAll(file.document().warnings());
            for(var entry:file.document().entries()){
                check.run();motifs++;scalars=Math.addExact(scalars,entry.probabilityTokens().size()*4L);
                require(motifs<=request.capacity("motifs")&&scalars<=request.capacity("matrix_values"),"Combined complete Tomtom motif/scalar population exceeds declared capacity");
                if(!ids.add(entry.model().id()))throw new TomtomResults.UnsupportedInput("Original duplicate-ID filtering needs a complete source-exclusion ledger. Preserve all captured records; skip dependent execution rather than remove motifs silently.");
                if(file.role().equals("query")){queries++;if(entry.probabilityTokens().size()>100)throw new TomtomResults.UnsupportedInput("Original Tomtom query width above100 needs a separately tested exclusion ledger. No fit was launched; preserve the complete input and skip dependent execution.");}
                else{targets++;if(request.mode()==TomtomRecipe.Mode.TEXT&&!textIds.add(entry.model().id()))throw new TomtomResults.UnsupportedInput("Text-only original output does not disambiguate duplicate target IDs across databases. Preserve inputs; explicitly request FULL_REPORT or skip this analysis. Output mode was not changed automatically.");}
                if(request.distance().equals("allr")&&request.pseudocount().signum()==0)for(double[] row:entry.model().probabilities())for(double value:row)if(value==0)
                    throw new TomtomResults.UnsupportedInput("Original ALLR zero probabilities can be excluded or crash for a singleton. Preserve the captured inputs and skip until a complete source-exclusion/failure ledger is supported; no pseudocount was added.");
            }
        }
        require(Math.multiplyExact(queries,targets)<=request.capacity("pairs"),"Complete Tomtom query-target Cartesian pair population exceeds declared capacity");
        warnings.add("REQUIRED ACTION: these are captured inputs only. Exact permitted original runtime admission, typed supervised execution and complete requested output audit are separate gates. Missing/forbidden dependencies require a prominent named skip and instructions to supply them on a permitted host; analysis never installs.");
        warnings.add("SOURCE POPULATIONS: every declared record remains in raw files and motif-ledger.jsonl. No source exclusions are silently applied. Do not infer unreported values or recompute q-values from best-match rows.");
        warnings.add("PATH MAPPING: safe staged filenames are command carriers; original paths remain literal producer metadata. Raw files are byte-identical. Display/database paths in newly generated output will refer to the staged carriers.");
        return List.copyOf(new java.util.LinkedHashSet<>(warnings));
    }
    static Map<String,Object> tree(Path root,TomtomRecipe.Request request,Runnable check)throws IOException {
        NextflowFiles.directory(root);var result=new TreeMap<String,Object>();long[] bytes={0};int[] count={0};
        Files.walkFileTree(root,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attributes)throws IOException {
                check.run();require(++count[0]<=request.capacity("files")&&!attributes.isSymbolicLink(),"Tomtom captured directory capacity/link failure");
                String name=path.equals(root)?".":member(root,path);result.put(name,Map.of("kind","directory","mode",NextflowFiles.mode(path)));return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attributes)throws IOException {
                check.run();require(++count[0]<=request.capacity("files")&&attributes.isRegularFile()&&!attributes.isSymbolicLink(),"Tomtom captured namespace contains an unsupported node or exceeds capacity");
                var identity=NextflowFiles.identity(path,request.capacity("file_bytes"),check);bytes[0]=Math.addExact(bytes[0],identity.pin().bytes());require(bytes[0]<=request.capacity("workspace_bytes"),"Complete Tomtom captured byte capacity exceeded");
                result.put(member(root,path),Map.of("kind","file","bytes",identity.pin().bytes(),"sha256",identity.pin().sha256(),"mode",identity.mode()));return FileVisitResult.CONTINUE;
            }
        });return Map.copyOf(result);
    }
    private static String member(Path root,Path path)throws IOException {String name=root.relativize(path).toString().replace(path.getFileSystem().getSeparator(),"/");return NextflowFiles.member(name);}
    static String extension(String path){int dot=path.lastIndexOf('.');String extension=dot<0?"":path.substring(dot).toLowerCase(java.util.Locale.ROOT);return Set.of(".xml",".txt",".html",".meme").contains(extension)?extension:".meme";}
    private static List<Double> numbers(double[] values){return java.util.Arrays.stream(values).boxed().toList();}
    static double[] background(Path path,int maximum)throws IOException {
        var tokens=new ArrayList<String>();for(String line:NextflowFiles.utf8(NextflowFiles.bytes(path,maximum)).split("\n")){int comment=line.indexOf('#');String data=(comment<0?line:line.substring(0,comment)).strip();if(!data.isEmpty())tokens.addAll(List.of(data.split("\\s+")));}
        require(tokens.size()==8,"Require complete original order-zero A/C/G/T background; higher-order terms need separate source handling");double[] result=new double[4];var seen=new HashSet<String>();double sum=0;
        for(int i=0;i<8;i+=2){String base=tokens.get(i);require(Set.of("A","C","G","T").contains(base)&&seen.add(base),"Unknown/duplicate original background letter");
            String token=tokens.get(i+1);require(token.length()<=1024&&token.matches("[+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Invalid bounded original background decimal");
            BigDecimal value;try{value=new BigDecimal(token);}catch(NumberFormatException invalid){throw new IOException("Invalid original background decimal",invalid);}
            require(Math.abs((long)value.scale())<=4096&&value.signum()>=0&&value.compareTo(BigDecimal.ONE)<=0,"Invalid original background probability");double number=value.doubleValue();require(value.signum()==0||number>0,"Original background probability underflows the source parser");result["ACGT".indexOf(base)]=number;sum+=number;
        }
        require(Math.abs(sum-1)<=0.1,"Original background mass exceeds source tolerance");return result;
    }
    private static void copy(Path source,Path destination,NextflowFiles.Pin pin,Runnable check)throws IOException {
        long size=0;try(var in=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);var out=Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW)){
            byte[] buffer=new byte[65536];for(int n=in.read(buffer);n>=0;n=in.read(buffer)){check.run();size=Math.addExact(size,n);require(size<=pin.bytes(),"Original Tomtom input grew during capture");out.write(buffer,0,n);}
        }
        require(size==pin.bytes()&&pin.equals(NextflowFiles.pin(source,pin.bytes(),check))&&pin.equals(NextflowFiles.pin(destination,pin.bytes(),check)),"Original/captured Tomtom input identity changed");
    }
    private static void write(Path path,Object value)throws IOException {Files.writeString(path,RsemJson.json(value)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    private static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}
