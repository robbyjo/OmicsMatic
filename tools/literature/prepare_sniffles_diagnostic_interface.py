# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add causal error reporting to the isolated corrected option interface.

Leaves working sources and all running artifacts unchanged. The direct failure
and guarded-cancellation receipts must remain distinct from successful calling.
"""
import argparse,json,subprocess,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args()
    base=ROOT/'build/literature-review/sniffles-runtime';out=a.out.resolve();assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    parent=base/'option-interface-preview-1';proof=json.loads((parent/'COMPLETED.json').read_bytes())
    assert proof['status']=='PASS_ISOLATED_SNIFFLES_OPTION_INTERFACE_PREVIEW_BUILD_ONLY'
    assert all(pin(ROOT/name)==value for name,value in proof['current_workspace_sources_unchanged'].items())
    folder='src/main/java/org/jlinalg/cli/';sources=[]
    for name in ['SnifflesWorkflowRequest.java','SnifflesWorkflowCli.java']:
        original=parent/'source-candidate'/folder/name;raw=original.read_bytes()
        assert pin(original)==proof['candidate_sources']['source-candidate/'+folder+name]
        text=raw.decode('utf8')
        if name=='SnifflesWorkflowCli.java':
            needle='state.put("analysis_admitted", false); state.put("error", failure.toString());'
            assert text.count(needle)==1;text=text.replace(needle,'state.put("analysis_admitted", false); state.put("error", failureText(failure));')
            needle='    private static void warnings(Path out, Map<String, Object> state, List<String> warnings) throws IOException {'
            assert text.count(needle)==1
            helper='''    private static String failureText(Throwable failure) {
        var pending = new java.util.ArrayDeque<Throwable>();
        var labels = new java.util.ArrayDeque<String>();
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        var text = new StringBuilder(); pending.add(failure); labels.add(""); int remaining = 32;
        while (!pending.isEmpty() && remaining-- > 0) {
            Throwable item = pending.remove(); String label = labels.remove();
            if (!seen.add(item)) continue;
            if (!text.isEmpty()) text.append('\\n');
            text.append(label).append(item);
            if (item.getCause() != null) { pending.add(item.getCause()); labels.add("CAUSE: "); }
            for (Throwable suppressed : item.getSuppressed()) {
                if (pending.size() >= 32) break;
                pending.add(suppressed); labels.add("SUPPRESSED: ");
            }
        }
        if (!pending.isEmpty()) text.append("\\nAdditional diagnostic causes omitted from this bounded summary; preserve complete process logs.");
        return text.toString();
    }
'''
            text=text.replace(needle,helper+needle)
        target=out/'source-candidate'/folder/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(text,encoding='utf8',newline='\n');sources.append(target)
    artifacts={}
    for compiler,javac in [('javac21','C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe'),('javac25','C:/Program Files/Common Files/Oracle/Java/javapath/javac.exe')]:
        before=ROOT/proof['artifacts'][compiler]['path'];assert pin(before)=={key:proof['artifacts'][compiler][key] for key in ['bytes','sha256']}
        home=out/compiler;home.mkdir();classes=home/'classes';classes.mkdir()
        command=[javac,'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(before),'-d',str(classes),str(sources[1])]
        run=subprocess.run(command,capture_output=True,timeout=120);save(home/'compile.json',dict(command=command,exit_code=run.returncode))
        (home/'compile.stdout').write_bytes(run.stdout);(home/'compile.stderr').write_bytes(run.stderr);assert run.returncode==0,run.stderr.decode(errors='replace')
        with zipfile.ZipFile(before) as archive:payload={name:archive.read(name) for name in archive.namelist()}
        changed=[]
        for path in classes.rglob('*.class'):
            name=path.relative_to(classes).as_posix();assert name.startswith('org/jlinalg/cli/SnifflesWorkflowCli')
            raw=path.read_bytes()
            if payload[name]!=raw:changed.append(name)
            payload[name]=raw
        artifact=home/'sniffles-corrected-interface-preview.jar'
        with zipfile.ZipFile(artifact,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
            for name,raw in sorted(payload.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;archive.writestr(info,raw)
        artifacts[compiler]=dict(path=artifact.relative_to(ROOT).as_posix(),**pin(artifact),base_artifact=pin(before),changed_workflow_class_members=changed,all_other_class_and_resource_bytes_unchanged=True)
    assert all(pin(ROOT/name)==value for name,value in proof['current_workspace_sources_unchanged'].items())
    save(out/'COMPLETED.json',dict(status='PASS_ISOLATED_SNIFFLES_DIAGNOSTIC_INTERFACE_BUILD_ONLY',implementation_credit=False,whole_goal_complete=False,
        artifacts=artifacts,candidate_sources={path.relative_to(out).as_posix():pin(path) for path in sources},current_workspace_sources_unchanged=proof['current_workspace_sources_unchanged'],
        previous_option_candidate=pin(parent/'COMPLETED.json'),diagnostic_change='Preserve bounded cause and suppressed-exception summaries, including wrapped Cancellation requested; no execution/admission decision or scientific argument/value changes.',
        source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))}))
    print('Built isolated causal-diagnostic candidates with both compilers; working sources unchanged; calling/cancellation validation required')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable isolated diagnostic source/artifact comparisons')
    main()
