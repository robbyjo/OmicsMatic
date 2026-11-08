# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile additive CellPhoneDB prepared numerical components against the validated LeafCutter workflow checkpoint; no named-tool credit."""
import argparse,json,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_CELLPHONEDB_NATIVE_BUILD_INCOMPLETE',implementation_credit=False,whole_goal_complete=False)
    try:
        proof=json.loads((ROOT/'docs/literature/leafcutter-workflow-development/VALIDATION.json').read_bytes());parent=ROOT/proof['artifact']['path'];assert pin(parent)=={k:proof['artifact'][k] for k in ['bytes','sha256']};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
        intake=ROOT/'build/literature-review/cellphonedb-runtime/source-intake-2';original=json.loads((intake/'COMPLETED.json').read_bytes())['versions']['5.0.1'];assert original['original_license_body_present'];license_names=[n for n in original['members'] if n.endswith('/LICENSE') or n.endswith('/LICENSE.txt')];assert len(license_names)==1;notice=intake/'5.0.1/source'/license_names[0];assert pin(notice)==original['members'][license_names[0]]
        resources=ROOT/'src/main/resources/org/jlinalg/omics/cellphonedb-statistics';resources.mkdir(parents=True,exist_ok=True);profile=dict(version='5.0.1',distribution=original['distribution'],source_members=original['members'],scope='Prepared numeric multidata axes only; explicit supplied permutation labels. No database/gene mapping, normalization, DEG fitting, RNG port, full workflow or biological claim.',implementation_credit=False,whole_goal_complete=False)
        members={'SOURCE-PROFILE.json':(json.dumps(profile,indent=2)+'\n').encode(),'MIT-LICENSE.txt':notice.read_bytes(),'NOTICE.txt':b'Independent Java prepared numerical kernels follow unmodified published CellPhoneDB5.0.1, MIT, Copyright (c) 2023 Wellcome Sanger Institute. Original MIT notice is retained. Java code is GPL-3.0-or-later as part of OmicsMatic. No CellPhoneDB database is included or licensed by this notice. Prepared components do not establish full named-tool implementation, RNG equivalence, paper reproduction or biological communication.\n'}
        for name,raw in members.items():
            file=resources/name
            if file.exists():assert file.read_bytes()==raw,'Do not overwrite previous source/profile resources'
            else:file.write_bytes(raw)
        sources=[ROOT/'src/main/java/org/jlinalg/singlecell/CellPhoneDbStatistics.java',ROOT/'tools/literature/java/org/jlinalg/cli/CellPhoneDbStatisticsMatrix.java'];pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),*sources,*sorted(resources.iterdir())]}
        for name in pins:target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)];result=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(result.stdout);(out/'compile.stderr').write_bytes(result.stderr);save(out/'compile.json',dict(command=command,exit_code=result.returncode));assert result.returncode==0,result.stderr.decode('utf8',errors='replace');added={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')};added.update({f.relative_to(ROOT/'src/main/resources').as_posix():f.read_bytes() for f in resources.iterdir()});jar=out/'omicsmatic-0.3.7-cellphonedb-statistics-development.jar'
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
            assert set(added).isdisjoint(before.namelist())
            for info in before.infolist():after.writestr(info,before.read(info.filename))
            for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and all(before.read(n)==after.read(n) for n in before.namelist());unchanged=len(before.namelist())
        assert all(pin(ROOT/n)==want for n,want in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state.update(status='PASS_COMPILED_CELLPHONEDB_COMPONENTS_ONLY_NOT_SCIENTIFIC_VALIDATION',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),source_pins=pins,parent_artifact=proof['artifact'],unchanged_parent_entries=unchanged,added_entries=sorted(added),canonical_executable_unchanged=canonical)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable additive source/build checks')
    raise SystemExit(main())
