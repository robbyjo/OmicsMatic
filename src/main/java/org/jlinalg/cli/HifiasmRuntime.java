/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Exact original-source compilation provenance and immutable native runtime binding. */
final class HifiasmRuntime {
    private HifiasmRuntime(){ }
    static final String ACTION="SKIP3 skips every dependent assembly when the permitted Linux x86_64 runtime or required helper is unavailable. Read WARNINGS.md, permit/provide the exact dependencies on a compatible host, build and bind NEW paths, or retain the skip. FAIL2 admits no analysis: inspect the original stderr and process receipt, retain partial outputs and resolve the named cause before retrying NEW paths. Never update an old binding's scientific hashes. A graph/FASTA export does not certify haplotype phasing, genome completeness or biological correctness.";
    static Map<String,Object> profile()throws IOException{try(var input=HifiasmRuntime.class.getResourceAsStream("/org/jlinalg/omics/hifiasm-source/profiles.json")){require(input!=null,"Missing original source profile");byte[] raw=input.readNBytes(16777217);require(raw.length<=16777216,"Original source profile capacity exceeded");return object(NextflowParameters.parse(NextflowFiles.utf8(raw),16777216,1000000));}}
    static Path regular(Path supplied,long maximum)throws IOException{Path path=supplied.toAbsolutePath().normalize();require(path.equals(path.toRealPath())&&Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&Files.size(path)<=maximum,"Require a canonical regular file within its declared capacity: "+path);return path;}
    record Build(Path receipt,NextflowFiles.Pin receiptPin,Path source,Path binary,NextflowFiles.Pin binaryPin,String edition,String commit,Map<String,Object> profile){
        void verifyRuntime()throws IOException{
            require(receiptPin.equals(NextflowFiles.pin(receipt,16777216,()->{}))&&binaryPin.equals(NextflowFiles.pin(binary,1073741824,()->{}))&&Files.isExecutable(binary),"Bound original build receipt/binary changed or lost execute permission");
        }
        void verify()throws IOException{
            verifyRuntime();var members=object(object(object(profile.get("editions")).get(edition)).get("members"));for(var member:members.entrySet()){Path file=source.resolve(NextflowFiles.member(member.getKey()));require(NextflowFiles.fingerprint(member.getValue(),33554432).equals(NextflowFiles.pin(file,33554432,()->{})),"Original build source changed: "+member.getKey());}
        }
    }
    static Build build(Path receipt)throws IOException{
        receipt=regular(receipt,16777216);var pin=NextflowFiles.pin(receipt,16777216,()->{});var raw=GseapyExecutionRuntime.read(receipt,16777216);require(text(raw.get("status")).equals("PASS_ORIGINAL_HIFIASM_SOURCE_COMPILATION_ONLY")&&Boolean.TRUE.equals(raw.get("original_source_members_unchanged"))&&Boolean.FALSE.equals(raw.get("source_namespace_patched")),"Require a successful unchanged original hifiasm compilation receipt");var profile=profile();require(profile.equals(object(raw.get("source_profile"))),"Exact packaged hifiasm source profile differs from compilation provenance");String edition=text(raw.get("edition"));var selected=object(object(profile.get("editions")).get(edition));require(selected.get("members").equals(raw.get("source_members"))&&selected.get("commit").equals(raw.get("commit")),"Exact original commit/member provenance differs");var binaryRecord=object(raw.get("binary"));Path binary=regular(Path.of(text(binaryRecord.get("path"))),1073741824);Path source=binary.getParent();var command=list(raw.get("compiler_command"));require(command.size()==4&&text(command.get(0)).equals("/usr/bin/make")&&text(command.get(1)).matches("-j[1-4]")&&text(command.get(2)).equals("-C")&&text(command.get(3)).equals(source.toString())&&binary.getFileName().toString().equals("hifiasm"),"Unexpected original source compilation command/path");var result=new Build(receipt,pin,source,binary,NextflowFiles.fingerprint(binaryRecord,1073741824),edition,text(selected.get("commit")),profile);result.verify();return result;
    }
    record Prepared(Path manifest,NextflowFiles.Pin manifestPin,Map<String,Object> document,Build build,Map<String,Object> host){
        void verify()throws IOException{require(manifestPin.equals(NextflowFiles.pin(manifest,33554432,()->{})),"Original runtime manifest changed");build.verify();HifiasmHost.verifyFiles(host,build.binary());}
    }
    static Prepared prepare(Path manifest,String edition,Path probes)throws IOException{
        HifiasmHost.platform();if(!Files.isRegularFile(manifest,LinkOption.NOFOLLOW_LINKS))throw new RsemProcess.UnavailableDependency("Bound original hifiasm runtime manifest is missing; build and bind permitted NEW runtime paths");manifest=regular(manifest,33554432);var pin=NextflowFiles.pin(manifest,33554432,()->{});var document=GseapyExecutionRuntime.read(manifest,33554432);if(!text(document.get("status")).equals("PASS_BOUND_ORIGINAL_HIFIASM_RUNTIME_ONLY"))throw new RsemProcess.UnavailableDependency("Original hifiasm runtime binding did not pass; read setup warnings");require(text(document.get("edition")).equals(edition)&&Boolean.FALSE.equals(document.get("source_namespace_patched")),"Exact requested hifiasm source edition differs from bound original runtime");Build build=build(Path.of(text(document.get("build_receipt"))));require(NextflowFiles.fingerprint(document.get("build_receipt_pin"),16777216).equals(build.receiptPin())&&text(document.get("commit")).equals(build.commit())&&text(document.get("binary")).equals(build.binary().toString()),"Runtime compilation provenance changed");Map<String,Object> host=HifiasmHost.capture(build.binary(),probes,build::verifyRuntime);build.verify();require(host.equals(object(document.get("host_binding"))),"Native runtime host/library/search binding changed; prepare a NEW compatible binding, never refresh this receipt");var prepared=new Prepared(manifest,pin,document,build,host);prepared.verify();return prepared;
    }
}
