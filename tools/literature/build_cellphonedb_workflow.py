# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add the CellPhoneDB workflow development classes without changing any parent JAR member. Compilation grants no tool credit."""
import argparse,json,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_CELLPHONEDB_WORKFLOW_BUILD',implementation_credit=False,whole_goal_complete=False)
    try:
        proof=json.loads((ROOT/'docs/literature/cellphonedb-preparation-development/VALIDATION.json').read_bytes());parent=ROOT/proof['artifact']['path'];assert pin(parent)=={k:proof['artifact'][k] for k in ['bytes','sha256']};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');sources=sorted((ROOT/'src/main/java/org/jlinalg/cli').glob('CellPhoneDbWorkflow*.java'));sources+=sorted((ROOT/'src/main/java/org/jlinalg/cli').glob('CellPhoneDbSetupCli.java'));sources+=[ROOT/'tools/literature/java/org/jlinalg/cli/CellPhoneDbWorkflowAuditMatrix.java'];resources=ROOT/'src/main/resources/org/jlinalg/omics/cellphonedb-workflow';files=[Path(__file__).resolve(),*sources,*sorted(p for p in resources.rglob('*') if p.is_file())];pins={p.relative_to(ROOT).as_posix():pin(p) for p in files}
        for path in files:target=out/'source-snapshot'/path.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(path.read_bytes())
        classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)];run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode('utf8',errors='replace');added={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};added.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in resources.rglob('*') if p.is_file()});jar=out/'omicsmatic-0.3.7-cellphonedb-workflow-development.jar'
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
            assert set(added).isdisjoint(before.namelist())
            for info in before.infolist():after.writestr(info,before.read(info.filename))
            for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and all(before.read(n)==after.read(n) for n in before.namelist());unchanged=len(before.namelist())
        assert all(pin(ROOT/n)==want for n,want in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state.update(status='PASS_COMPILED_CELLPHONEDB_WORKFLOW_DEVELOPMENT_ONLY',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),source_pins=pins,parent_artifact=proof['artifact'],unchanged_parent_entries=unchanged,added_entries=sorted(added),canonical_executable_unchanged=canonical)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':raise SystemExit(main())
