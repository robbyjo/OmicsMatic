# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add source-bound native Spyrrow communication/CPython semantics without changing any parent JAR entry. No whole-tool credit."""
import argparse,json,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/spyrrow-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_SPYRROW_COMMUNICATION_BUILD',implementation_credit=False,whole_goal_complete=False)
    try:
        proof=json.loads((ROOT/'docs/literature/cellphonedb-workflow-development/VALIDATION.json').read_bytes());parent=ROOT/proof['artifact']['path'];assert pin(parent)=={k:proof['artifact'][k] for k in ['bytes','sha256']};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');source=ROOT/'build/literature-review/spyrrow-runtime/source-discovery-1';spyrrow=json.loads((source/'SOURCE.json').read_bytes());cpp=ROOT/'build/literature-review/spyrrow-runtime/cpython-source-intake-1';python=json.loads((cpp/'SOURCE.json').read_bytes());immutable=json.loads((cpp/'IMMUTABLE-COMMIT.json').read_bytes());assert immutable['all_captured_files_verified_against_commit'];resources=ROOT/'src/main/resources/org/jlinalg/omics/spyrrow-communication';resources.mkdir(parents=True,exist_ok=True);members={'.gitattributes':b'* -text whitespace=-blank-at-eol,-blank-at-eof,-space-before-tab,cr-at-eol\n'}
        for name,want in spyrrow['source_members'].items():assert pin(source/'source'/name)=={k:want[k] for k in ['bytes','sha256']};members['original-spyrrow/'+name]=(source/'source'/name).read_bytes()
        for name,want in python['members'].items():assert pin(cpp/'source'/name)==want;members['original-cpython/'+name]=(cpp/'source'/name).read_bytes()
        profile=dict(schema_version=1,spyrrow_version='0.1',spyrrow_revision=spyrrow['revision'],spyrrow_source_members=spyrrow['source_members'],cpython_version='3.11.17',cpython_revision=immutable['commit'],cpython_source_members=python['members'],native_scope='Entire basic.cal_cell_communication over explicit float64-equivalent DataFrame/coordinates/interaction lists; seed0 SipHash13/set iteration and literal two-dimensional math.dist. No pickle database, full package/cn.call, plots or paper reproduction.',source_database_included=False,implementation_credit=False,whole_goal_complete=False);members['SOURCE-PROFILE.json']=(json.dumps(profile,indent=2)+'\n').encode();members['NOTICE.txt']=b'Native Java communication behavior follows Spyrrow0.1 source18cf78611c22bfb5ea7a6373a9e1c0bb5bec9b77 (Apache License2.0; original license and complete captured source retained). CPython3.11.17 distance, SipHash13/native Unicode and no-deletion string-set behavior follow sourcee6392eb68a39cef49c9fe6431253050caa17a426 (PSF license and source notices retained). New Java ports are GPL-3.0-or-later. Changes: typed explicit inputs, bounded complete output, preserved source semantics and diagnostics. No database/data pickle is included or loaded. This is a component development artifact, not full Spyrrow/package/cn.call/plot or paper admission.\n'
        for name,raw in members.items():
            p=resources/name;p.parent.mkdir(parents=True,exist_ok=True)
            if p.exists():assert p.read_bytes()==raw,'Protect existing source resources'
            else:p.write_bytes(raw)
        sources=[ROOT/'src/main/java/org/jlinalg/singlecell/SpyrrowSourceSemantics.java',ROOT/'src/main/java/org/jlinalg/singlecell/SpyrrowCommunication.java',ROOT/'tools/literature/java/org/jlinalg/cli/SpyrrowCommunicationMatrix.java'];files=[Path(__file__).resolve(),*sources,*sorted(p for p in resources.rglob('*') if p.is_file())];pins={p.relative_to(ROOT).as_posix():pin(p) for p in files}
        for p in files:target=out/'source-snapshot'/p.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(p.read_bytes())
        classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)];run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode('utf8',errors='replace');added={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};added.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in resources.rglob('*') if p.is_file()});jar=out/'omicsmatic-0.3.7-spyrrow-communication-development.jar'
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
            assert set(added).isdisjoint(before.namelist())
            for info in before.infolist():after.writestr(info,before.read(info.filename))
            for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and all(before.read(n)==after.read(n) for n in before.namelist());unchanged=len(before.namelist())
        assert all(pin(ROOT/n)==want for n,want in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state.update(status='PASS_COMPILED_SPYRROW_COMMUNICATION_COMPONENT_ONLY',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),source_pins=pins,parent_artifact=proof['artifact'],unchanged_parent_entries=unchanged,added_entries=sorted(added),canonical_executable_unchanged=canonical)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':raise SystemExit(main())
