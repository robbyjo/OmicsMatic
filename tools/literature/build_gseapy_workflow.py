# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile the current original-workflow transport/export foundation, preserving the validated native parent."""
import argparse,json,subprocess,sys,traceback,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_GSEAPY_WORKFLOW_FOUNDATION_BUILD',implementation_credit=False,full_adapter_admitted=False,whole_goal_complete=False)
    try:
        parentproof=json.loads((ROOT/'docs/literature/gseapy-statistics-development/VALIDATION.json').read_bytes());parent=ROOT/parentproof['artifact']['path'];assert pin(parent)=={k:parentproof['artifact'][k] for k in ['bytes','sha256']};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');resources=ROOT/'src/main/resources/org/jlinalg/omics/gseapy-workflow';runtime=json.loads((resources/'profiles.json').read_bytes());worker=json.loads((resources/'worker-profile.json').read_bytes());assert all(pin(resources/n)==v for profile in [runtime,worker] for n,v in profile['files'].items());sources=[ROOT/'src/main/java/org/jlinalg/cli'/name for name in ['GseapyWorkflowRequest.java','GseapyWorkflowRuntime.java','GseapySetupCli.java']];files=[Path(__file__).resolve(),*sources,*[p for p in resources.rglob('*') if p.is_file()]];pins={p.relative_to(ROOT).as_posix():pin(p) for p in files}
        additional=[ROOT/'src/main/java/org/jlinalg/enrichment/GseapyRankedStatistics.java',ROOT/'src/main/java/org/jlinalg/cli/GseapyWorkflowInputs.java',ROOT/'tools/literature/java/org/jlinalg/cli/GseapyRankedMatrix.java',ROOT/'tools/literature/java/org/jlinalg/cli/GseapyRankedContractMatrix.java'];sources.extend(additional);files.extend(additional);pins.update({p.relative_to(ROOT).as_posix():pin(p) for p in additional})
        for path in files:target=out/'source-snapshot'/path.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(path.read_bytes())
        classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)];run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode(errors='replace');added={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};added.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in resources.rglob('*') if p.is_file()});jar=out/'omicsmatic-0.3.7-gseapy-workflow-development.jar'
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
            assert set(added).isdisjoint(before.namelist())
            for info in before.infolist():after.writestr(info,before.read(info.filename))
            for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and all(before.read(n)==after.read(n) for n in before.namelist());unchanged=len(before.namelist())
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state.update(status='passed-compiled-GSEApy-runtime-transport-and-export-foundation-only',source_pins=pins,artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),parent_artifact=parentproof['artifact'],unchanged_parent_entries=unchanged,added_entries=sorted(added),canonical_executable_unchanged=canonical)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':raise SystemExit(main())
