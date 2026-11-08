# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build a separate original scFates coordinate adapter over the exact native arithmetic parent."""
import argparse,json,subprocess,sys,traceback,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_SCFATES_COORDINATE_WORKFLOW_BUILD',implementation_credit=False,whole_goal_complete=False)
    try:
        proof=json.loads((ROOT/'docs/literature/simpleppt-core-development/VALIDATION.json').read_bytes());assert proof['status']=='passed-native-SimplePPT-arithmetic-and-original-scfates-controls-only';parent=ROOT/proof['artifact']['path'];assert pin(parent)=={k:proof['artifact'][k] for k in ['bytes','sha256']};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');resources=ROOT/'src/main/resources/org/jlinalg/omics/scfates-workflow';profile=json.loads((resources/'profiles.json').read_bytes());worker=json.loads((resources/'worker-profile.json').read_bytes())
        for name,want in {**profile['files'],**worker['files']}.items():assert pin(resources/name)==want,'Kit source differs: '+name
        names=['ScfatesWorkflowRequest','ScfatesWorkflowInputs','ScfatesWorkflowData','ScfatesWorkflowRuntime','ScfatesWorkflowAudit','ScfatesWorkflowCli','ScfatesSetupCli'];sources=[ROOT/'src/main/java/org/jlinalg/cli'/Path(n+'.java') for n in names];drivers=[ROOT/'tools/literature/java/org/jlinalg/cli'/n for n in ['ScfatesWorkflowAuditMatrix.java','ScfatesRootScanMatrix.java']]
        for driver in drivers:
            if driver.exists():sources.append(driver)
        files=[Path(__file__).resolve(),*sources,*sorted(p for p in resources.rglob('*') if p.is_file())];pins={p.relative_to(ROOT).as_posix():pin(p) for p in files}
        for path in files:copy=out/'source-snapshot'/path.relative_to(ROOT);copy.parent.mkdir(parents=True,exist_ok=True);copy.write_bytes(path.read_bytes())
        classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)];run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode('utf8',errors='replace');added={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};added.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in resources.rglob('*') if p.is_file()});jar=out/'omicsmatic-0.3.7-scfates-workflow-development.jar'
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
            assert set(added).isdisjoint(before.namelist())
            for info in before.infolist():after.writestr(info,before.read(info.filename))
            for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and all(before.read(n)==after.read(n) for n in before.namelist());unchanged=len(before.namelist())
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state.update(status='PASS_COMPILED_SCFATES_COORDINATE_WORKFLOW_ONLY',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),source_pins=pins,parent_artifact=proof['artifact'],unchanged_parent_entries=unchanged,added_entries=sorted(added),canonical_executable_unchanged=canonical)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
