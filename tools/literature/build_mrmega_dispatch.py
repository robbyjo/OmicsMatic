# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build a root supplement; preserve every selected parent and MR-MEGA science byte."""
import argparse,json,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--compiler',choices=['javac21','javac25'],required=True);args=parser.parse_args()
    out=args.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/mrmega-runtime');out.mkdir(parents=True)
    state=dict(status='FAIL2_MRMEGA_ROOT_DISPATCH_BUILD',implementation_credit=False,whole_goal_complete=False)
    try:
        parent_proof=ROOT/'docs/literature/monocle-tools-development/VALIDATION.json';proof=json.loads(parent_proof.read_bytes());parent=ROOT/proof['artifact']['path'];assert pin(parent)=={k:proof['artifact'][k] for k in ['bytes','sha256']}
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';canonical_pin=pin(canonical)
        wf_home=ROOT/'docs/literature/mrmega-workflow-development';workflow=wf_home/('mrmega-workflow-'+args.compiler+'.jar');wf_proof=json.loads((wf_home/'VALIDATION.json').read_bytes());assert pin(workflow)==wf_proof['artifacts'][workflow.name]
        javac='C:/Program Files/'+('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe' if args.compiler=='javac21' else 'Common Files/Oracle/Java/javapath/javac.exe')
        source=ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java';paths=[Path(__file__).resolve(),source];pins={p.relative_to(ROOT).as_posix():pin(p) for p in paths}
        for p in paths:target=out/'source-snapshot'/p.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(p.read_bytes())
        classes=out/'classes';classes.mkdir();command=[javac,'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(workflow)+';'+str(parent),'-d',str(classes),str(source)]
        run=subprocess.run(command,capture_output=True,timeout=120);save(out/'compile.json',dict(command=command,exit_code=run.returncode));(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);assert run.returncode==0,run.stderr.decode(errors='replace')
        target='org/jlinalg/cli/JLinAlgCli.class';assert [p.relative_to(classes).as_posix() for p in classes.rglob('*.class')]==[target]
        payload={};unchanged=0
        with zipfile.ZipFile(canonical) as original,zipfile.ZipFile(parent) as previous:
            assert set(original.namelist())<=set(previous.namelist())
            old_names=set(original.namelist());parent_entries={n:previous.read(n) for n in previous.namelist()}
            for n,raw in parent_entries.items():
                if n not in old_names or original.read(n)!=raw:payload[n]=raw
                else:unchanged+=1
        parent_payload=dict(payload)
        with zipfile.ZipFile(workflow) as opened:
            workflow_entries={n:opened.read(n) for n in opened.namelist() if n.endswith('.class')}
        assert not set(workflow_entries)&set(parent_entries);payload.update(workflow_entries);payload[target]=(classes/target).read_bytes()
        payload['META-INF/mrmega/LICENSE']=(ROOT/'LICENSE').read_bytes()
        dependencies={p.relative_to(ROOT).as_posix():pin(p) for p in [canonical,parent,parent_proof,workflow,wf_home/'VALIDATION.json']}
        payload['META-INF/mrmega/DEPENDENCIES.json']=(json.dumps(dependencies,indent=2)+'\n').encode()
        artifact=out/'mrmega-root-supplement.jar'
        with zipfile.ZipFile(artifact,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as opened:
            for n,raw in sorted(payload.items()):
                info=zipfile.ZipInfo(n,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;opened.writestr(info,raw)
        with zipfile.ZipFile(artifact) as opened:
            assert all(opened.read(n)==raw for n,raw in parent_payload.items() if n!=target)
            assert all(opened.read(n)==raw for n,raw in workflow_entries.items())
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(canonical)==canonical_pin
        public=ROOT/'docs/literature/mrmega-catalog-development';public.mkdir(exist_ok=True);published=public/('mrmega-root-'+args.compiler+'.jar');assert not published.exists();published.write_bytes(artifact.read_bytes())
        state.update(status='PASS_MRMEGA_ROOT_SUPPLEMENT_WITH_UNCHANGED_PARENT_SCIENTIFIC_BYTES',compiler=args.compiler,
            artifact=dict(path=artifact.relative_to(ROOT).as_posix(),**pin(artifact)),published_artifact=dict(path=published.relative_to(ROOT).as_posix(),**pin(published)),
            source_pins=pins,dependencies=dependencies,parent_entries_unchanged_except_root=True,unchanged_canonical_entries=unchanged,
            parent_supplement_entries=len(parent_payload),unchanged_native_workflow_classes=len(workflow_entries),canonical_executable_unchanged=canonical_pin)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable science-byte preservation checks')
    raise SystemExit(main())
