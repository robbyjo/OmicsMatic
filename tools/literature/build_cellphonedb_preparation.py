# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add native CellPhoneDB typed preparation without changing any validated statistics or parent entries; compilation is not scientific admission."""
import argparse,json,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_CELLPHONEDB_PREPARATION_BUILD_INCOMPLETE',implementation_credit=False,whole_goal_complete=False)
    try:
        parent_proof=json.loads((ROOT/'docs/literature/cellphonedb-statistics-development/VALIDATION.json').read_bytes());parent=ROOT/parent_proof['artifact']['path'];assert pin(parent)=={k:parent_proof['artifact'][k] for k in ['bytes','sha256']};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');intake=ROOT/'build/literature-review/cellphonedb-runtime/preparation-source-intake-1';original=json.loads((intake/'COMPLETED.json').read_bytes());assert original['status']=='PASS_EXACT_PANDAS_223_GROUP_MEAN_SOURCE_ONLY'
        for name,want in original['source_members'].items():assert pin(intake/'source'/name)=={k:want[k] for k in ['bytes','sha256']}
        resources=ROOT/'src/main/resources/org/jlinalg/omics/cellphonedb-preparation';resources.mkdir(parents=True,exist_ok=True);profile=dict(schema_version=1,CellPhoneDB_version='5.0.1',pandas_version='2.2.3',pandas_revision=original['revision'],pandas_source_members=original['source_members'],CellPhoneDB_source_profile=pin(ROOT/'src/main/resources/org/jlinalg/omics/cellphonedb-statistics/SOURCE-PROFILE.json'),scope='Typed finite nonnegative original count/gene/metadata/composition/interaction rows. Explicit source float32 casting and pandas compensated mapping means; full database loading, file parsing, workflow execution and biological admission remain separate.',curated_database_included=False,implementation_credit=False,whole_goal_complete=False)
        members={'SOURCE-PROFILE.json':(json.dumps(profile,indent=2)+'\n').encode(),'PANDAS-BSD-3.txt':(intake/'source/LICENSE').read_bytes(),'NOTICE.txt':b'Native typed preparation follows published CellPhoneDB5.0.1 (MIT, Wellcome Sanger Institute) and pandas2.2.3 group_mean (BSD3, AQR Capital Management/Lambda Foundry/PyData Development Team and open-source contributors). Both original notices are retained in this additive artifact. The Java component is GPL-3.0-or-later as part of OmicsMatic. No curated database is included or relicensed. Explicit float32 casting, compensated gene aggregation, source row identities and filtering decisions remain visible; no full named-tool or biological claim is made.\n'}
        for name,raw in members.items():
            file=resources/name
            if file.exists():assert file.read_bytes()==raw,'Do not overwrite previous source/profile resources'
            else:file.write_bytes(raw)
        sources=[ROOT/'src/main/java/org/jlinalg/singlecell/CellPhoneDbPreparation.java',ROOT/'tools/literature/java/org/jlinalg/cli/CellPhoneDbPreparationMatrix.java'];pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),*sources,*sorted(resources.iterdir())]}
        for name in pins:target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)];run=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode('utf8',errors='replace');added={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')};added.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in resources.iterdir()});jar=out/'omicsmatic-0.3.7-cellphonedb-preparation-development.jar'
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
            assert set(added).isdisjoint(before.namelist())
            for info in before.infolist():after.writestr(info,before.read(info.filename))
            for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and all(before.read(n)==after.read(n) for n in before.namelist());unchanged=len(before.namelist())
        assert all(pin(ROOT/n)==want for n,want in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state.update(status='PASS_COMPILED_CELLPHONEDB_PREPARATION_ONLY_NOT_SCIENTIFIC_VALIDATION',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),source_pins=pins,parent_artifact=parent_proof['artifact'],unchanged_parent_entries=unchanged,added_entries=sorted(added),canonical_executable_unchanged=canonical)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable additive source/build checks')
    raise SystemExit(main())
