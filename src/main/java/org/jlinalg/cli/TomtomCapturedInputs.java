/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jlinalg.sequence.MemeMotifInputs;
import org.jlinalg.sequence.TomtomResults;

/** Portable complete captured input/model audit; producer paths are never opened. */
final class TomtomCapturedInputs {
    private TomtomCapturedInputs() { }
    record Context(Path root,String producerRoot,TomtomRecipe.Request request,List<TomtomInputs.MotifFile> files,
                   String backgroundMember,TomtomResults.Backgrounds backgrounds,List<String> warnings) {
        Context {files=List.copyOf(files);warnings=List.copyOf(warnings);}
        String query(){return producerRoot+"/"+files.get(0).member();}
        List<String> targets(){return files.stream().filter(f->f.role().equals("target")).map(f->producerRoot+"/"+f.member()).toList();}
        String backgroundFile(){return backgroundMember==null?null:producerRoot+"/"+backgroundMember;}
        TomtomResults.Expected expected(List<String> command)throws IOException {
            require(!command.isEmpty(),"Missing captured typed Tomtom invocation");int index=command.indexOf("-o");String output=index>=0&&index+1<command.size()?command.get(index+1):null;
            require(command.equals(request.arguments(command.get(0),query(),targets(),backgroundFile(),output)),"Captured original Tomtom command differs from the complete typed settings/input carriers");
            var queries=new ArrayList<TomtomResults.Database>();var targets=new ArrayList<TomtomResults.Database>();for(var file:files){String source=producerRoot+"/"+file.member();String name=source.substring(source.lastIndexOf('/')+1);if(name.endsWith(".meme"))name=name.substring(0,name.length()-5);
                var db=new TomtomResults.Database(source,name,file.document().entries().stream().map(MemeMotifInputs.Entry::model).toList(),file.role().equals("query")?backgrounds.firstQuery():backgrounds.followingDatabases());(file.role().equals("query")?queries:targets).add(db);}
            var display=new ArrayList<>(command);require(display.get(0).endsWith("/tomtom"),"Captured executable is not typed original Tomtom");display.set(0,"tomtom");String bg=request.background()==TomtomRecipe.Background.QUERY?"--query--":request.background()==TomtomRecipe.Background.UNIFORM?"--uniform--":backgroundFile();
            return new TomtomResults.Expected(String.join(" ",display),request.distance(),request.reverseComplement(),request.thresholdKind()==TomtomRecipe.Threshold.QVALUE?"qvalue":"evalue",request.threshold().doubleValue(),request.pseudocount().doubleValue(),request.minimumOverlap(),request.internal(),bg,backgrounds.firstQuery(),queries,targets);
        }
    }
    static Context read(Path root,String producerRoot,Runnable check)throws IOException {
        NextflowFiles.directory(root);TomtomRecipe.nativePath(producerRoot);var request=TomtomRecipe.readCaptured(root.resolve("recipe.json"),check);var ledger=NextflowFiles.object(NextflowFiles.json(root.resolve("input-ledger.json"),16777216));
        NextflowFiles.keys(ledger,"status","producer_recipe_sha256","combined_original_bytes","backgrounds","background_member","warnings","files");require("CAPTURED_INPUTS_ONLY_NO_RUNTIME_OR_FIT_ADMISSION".equals(ledger.get("status"))&&request.sourcePin().sha256().equals(ledger.get("producer_recipe_sha256")),"Captured Tomtom input/recipe status or identity differs");
        var sources=new ArrayList<String>();sources.add(request.query());sources.addAll(request.targets());var records=NextflowFiles.list(ledger.get("files"));require(records.size()==sources.size(),"Complete captured Tomtom database population differs");var files=new ArrayList<TomtomInputs.MotifFile>();long bytes=request.sourcePin().bytes();
        for(int i=0;i<sources.size();i++){
            check.run();String role=i==0?"query":"target",member="inputs/"+role+"-"+String.format(java.util.Locale.ROOT,"%03d",i)+TomtomInputs.extension(sources.get(i));var recorded=NextflowFiles.object(records.get(i));NextflowFiles.keys(recorded,"role","ordinal","producer_path","member","bytes","sha256","format","motifs","background_rule");
            require(role.equals(recorded.get("role"))&&NextflowFiles.integer(recorded.get("ordinal"),i,i)==i&&sources.get(i).equals(recorded.get("producer_path"))&&member.equals(recorded.get("member")),"Captured original Tomtom role/path mapping differs");
            var pin=NextflowFiles.pin(root.resolve(member),request.capacity("input_bytes"),check);require(pin.equals(NextflowFiles.fingerprint(recorded,request.capacity("input_bytes"))),"Captured Tomtom raw input identity differs");bytes=Math.addExact(bytes,pin.bytes());
            var doc=MemeMotifInputs.read(root.resolve(member),request.inputLimits(),check);require(doc.sha256().equals(pin.sha256())&&doc.format().equals(recorded.get("format"))&&NextflowFiles.integer(recorded.get("motifs"),doc.entries().size(),doc.entries().size())==doc.entries().size()&&doc.backgroundRule().equals(recorded.get("background_rule")),"Captured Tomtom complete parsed model metadata differs");files.add(new TomtomInputs.MotifFile(role,i,sources.get(i),member,pin,doc));
        }
        String background=request.background()==TomtomRecipe.Background.FILE?"inputs/background.txt":null;require(java.util.Objects.equals(background,ledger.get("background_member")),"Captured original background role differs");double[] raw;
        if(background!=null){var pin=NextflowFiles.pin(root.resolve(background),request.capacity("input_bytes"),check);bytes=Math.addExact(bytes,pin.bytes());raw=TomtomInputs.background(root.resolve(background),(int)request.capacity("input_bytes"));}
        else raw=request.background()==TomtomRecipe.Background.UNIFORM?new double[]{.25,.25,.25,.25}:files.get(0).document().background();
        require(bytes<=request.capacity("input_bytes")&&NextflowFiles.integer(ledger.get("combined_original_bytes"),bytes,bytes)==bytes,"Combined captured Tomtom original byte population differs/exceeds capacity");
        var bg=TomtomResults.backgrounds(raw,request.background()==TomtomRecipe.Background.QUERY);var expectedBg=Map.of("original",numbers(bg.original()),"first_query",numbers(bg.firstQuery()),"following_databases",numbers(bg.followingDatabases()),"normalization_passes",bg.normalizationPasses());require(NextflowJson.json(ledger.get("backgrounds")).equals(NextflowJson.json(expectedBg)),"Captured Tomtom source background preprocessing differs");
        var warnings=TomtomInputs.preflight(files,request,check);require(NextflowFiles.strings(ledger.get("warnings")).equals(warnings),"Complete captured original input warnings/dispositions differ");
        var expectedLines=new ArrayList<String>();for(var file:files)for(var entry:file.document().entries())expectedLines.add(RsemJson.json(Map.of("file",file.member(),"ordinal",entry.ordinal(),"id",entry.model().id(),"alternate_id",entry.model().alternateId(),"sites",entry.model().sites(),"pseudocount_sites",entry.pseudocountSites(),"width",entry.probabilityTokens().size(),"source_metadata",entry.metadata(),"normalized_positions",entry.normalizedPositions())));
        String motifLedger=NextflowFiles.utf8(NextflowFiles.bytes(root.resolve("motif-ledger.jsonl"),268435456));require(motifLedger.equals(String.join("\n",expectedLines)+"\n"),"Complete captured Tomtom motif/default/normalization ledger differs");
        request.verify(check);return new Context(root,producerRoot,request,files,background,bg,warnings);
    }
    private static List<Double> numbers(double[] values){return java.util.Arrays.stream(values).boxed().toList();}
    private static void require(boolean value,String message)throws IOException {MemeRuntimeFiles.require(value,message);}
}
