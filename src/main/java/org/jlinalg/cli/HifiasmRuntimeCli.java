/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Public setup-only binding of an unchanged original compiled hifiasm runtime. */
public final class HifiasmRuntimeCli {
    private HifiasmRuntimeCli(){ }
    public static void main(String[] args){System.exit(run(args));}
    public static int run(String[] args){Path out=null;int exit=2;var state=new LinkedHashMap<String,Object>();state.put("status","FAIL2_HIFIASM_RUNTIME_BINDING");state.put("analysis_admitted",false);state.put("implementation_credit",false);state.put("started_at",Instant.now().toString());
        try{require(args.length==2&&args[0].equals("--request"),"Use --request runtime-binding-request.json");Path request=HifiasmRuntime.regular(Path.of(args[1]),1048576);var spec=GseapyExecutionRuntime.read(request,1048576);GseapyWorkflowRequest.allowed(spec,"schema_version","mode","build_receipt","out");integer(spec.get("schema_version"),1,1);require(text(spec.get("mode")).equals("BIND_REFERENCE_ONLY"),"Runtime binding is setup only");Path target=Path.of(text(spec.get("out"))).toAbsolutePath().normalize();require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS)&&target.getParent().equals(target.getParent().toRealPath()),"Require NEW output under an existing canonical parent");out=Files.createDirectory(target);Files.write(out.resolve("request.original.json"),NextflowFiles.bytes(request,1048576),StandardOpenOption.CREATE_NEW);HifiasmHost.platform();var build=HifiasmRuntime.build(Path.of(text(spec.get("build_receipt"))));var host=HifiasmHost.capture(build.binary(),out.resolve("host"),build::verifyRuntime);var version=HifiasmVersionProbe.probe(build.binary(),build.edition(),HifiasmHost.environment(),out.resolve("version"),()->{build.verify();HifiasmHost.verifyFiles(host,build.binary());});build.verify();HifiasmHost.verifyFiles(host,build.binary());state.put("status","PASS_BOUND_ORIGINAL_HIFIASM_RUNTIME_ONLY");state.put("edition",build.edition());state.put("commit",build.commit());state.put("binary",build.binary().toString());state.put("build_receipt",build.receipt().toString());state.put("build_receipt_pin",GseapyExecutionRuntime.pinObject(build.receiptPin()));state.put("host_binding",host);state.put("source_version_probe",version);state.put("source_namespace_patched",false);state.put("analysis_installed_software",false);exit=0;
        }catch(RsemProcess.UnavailableDependency unavailable){state.put("status","SKIP3_HIFIASM_RUNTIME_UNAVAILABLE");state.put("error",unavailable.toString());exit=3;System.err.println(state.get("status")+": "+unavailable+"\n"+HifiasmRuntime.ACTION);}
        catch(Exception failed){state.put("error",failed.toString());System.err.println(state.get("status")+": "+failed+"\n"+HifiasmRuntime.ACTION);failed.printStackTrace(System.err);}
        finally{state.put("finished_at",Instant.now().toString());if(out!=null)try{Files.writeString(out.resolve("WARNINGS.md"),"# "+state.get("status")+"\n\n"+HifiasmRuntime.ACTION+"\n\nSetup only: no assembly, tool integration credit or paper reproduction is admitted.\n");NextflowFiles.writeJson(out.resolve("COMPLETED.json"),state);}catch(Exception failed){System.err.println("FAIL2: runtime receipt unavailable: "+failed);exit=2;}}
        return exit;
    }
}
