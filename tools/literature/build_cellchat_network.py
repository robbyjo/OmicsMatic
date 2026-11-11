# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build a standalone Java17 original CellChat1.1.3 network postprocessing controller without changing the canonical JAR."""
import argparse,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--compiler',choices=['javac21','javac25'],required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cellchat-runtime');out.mkdir(parents=True)
    state=dict(status='FAIL2_CELLCHAT_NETWORK_BUILD',implementation_credit=False,whole_goal_complete=False)
    try:
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
        names=['NextflowParameters','NextflowFiles','NextflowJson','SimpleJson','CellChatLegacyRequest','CellChatLegacyTables','CellChatLegacyAudit','CellChatLegacyCli','CellChatNetworkRequest','CellChatNetworkAudit','CellChatNetworkCli']
        sources=[ROOT/'src/main/java/org/jlinalg/cli'/(n+'.java') for n in names]+[ROOT/'src/main/java/org/jlinalg/omics/CellChatProjection.java',ROOT/'src/main/java/org/jlinalg/omics/CellChatNetworkSimilarity.java']
        resources={'org/jlinalg/omics/cellchat-legacy/worker.R':ROOT/'tools/literature/cellchat_legacy_workflow.R','org/jlinalg/omics/cellchat-legacy/SOURCE-LOCK.json':ROOT/'tools/literature/cellchat-legacy-source-lock.json'}
        resources.update({'org/jlinalg/omics/cellchat-network/worker.R':ROOT/'tools/literature/cellchat_network_workflow.R','org/jlinalg/omics/cellchat-network/SOURCE-LOCK.json':ROOT/'tools/literature/cellchat-legacy-source-lock.json'})
        files=[Path(__file__).resolve(),*sources,*resources.values()];pins={p.relative_to(ROOT).as_posix():pin(p) for p in files}
        for file in files:
            target=out/'source-snapshot'/file.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(file.read_bytes())
        compiler='C:/Program Files/'+('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe' if a.compiler=='javac21' else 'Common Files/Oracle/Java/javapath/javac.exe')
        classes=out/'classes';classes.mkdir();command=[compiler,'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-d',str(classes),*map(str,sources)]
        run=subprocess.run(command,capture_output=True,timeout=120);save(out/'compile.json',dict(command=command,exit_code=run.returncode));(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);assert run.returncode==0,run.stderr.decode(errors='replace')
        jar=out/'cellchat-network-development.jar'
        with zipfile.ZipFile(jar,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            entries={p.relative_to(classes).as_posix():p.read_bytes() for p in sorted(classes.rglob('*.class'))};entries.update({name:p.read_bytes() for name,p in resources.items()});entries['META-INF/LICENSE']=(ROOT/'LICENSE').read_bytes();entries['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nMain-Class: org.jlinalg.cli.CellChatNetworkCli\r\n\r\n'
            for name,raw in sorted(entries.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
        assert pin(canonical)==before and all(pin(ROOT/n)==v for n,v in pins.items())
        state.update(status='PASS_ISOLATED_CELLCHAT_NETWORK_WORKFLOW_BUILD_ONLY',compiler=a.compiler,artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),source_pins=pins,canonical_executable_unchanged=before)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/canonical preservation checks')
    raise SystemExit(main())
