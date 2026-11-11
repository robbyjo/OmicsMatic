# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build source-bound native Hail sample-QC development code additively. Compilation alone grants no implementation credit."""
import argparse,json,subprocess,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--intake',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert not out.exists();out.mkdir(parents=True);parent_proof=json.loads((ROOT/'docs/literature/biomart-workflow-development/VALIDATION.json').read_bytes());parent=ROOT/parent_proof['artifact']['path'];assert pin(parent)=={k:parent_proof['artifact'][k] for k in ['bytes','sha256']};intake=json.loads((a.intake/'COMPLETED.json').read_bytes());original=intake['versions']['0.2.139'];assert original['status']=='PASS_EXACT_ORIGINAL_HAIL_SOURCE_AND_LICENSE_CAPTURE_ONLY';resources=ROOT/'src/main/resources/org/jlinalg/omics/hail-sample-qc';expected={'MIT.txt':(a.intake/'0.2.139/upstream-LICENSE').read_bytes(),'NOTICE.txt':b'Native sample-QC metrics follow original Hail0.2.139 (Hail Team, MIT). Original source URLs, revision, wheel and critical member identities are retained in SOURCE-PROFILE.json and the source-intake evidence. Genotype calls, allele strings, missing versus filtered entries and source count/ratio semantics are retained. This component is not a Hail MatrixTable/VDS reader, association model, Batch backend, paper mask or complete Hail integration.\n','SOURCE-PROFILE.json':(json.dumps(dict(schema_version=1,component='original-Hail0.2.139-sample-QC-metrics-only',revision=original['revision'],wheel=original['wheel'],license=original['license'],original_source_members={n:original['members'][n] for n in ['hail/methods/qc.py','hail/expr/functions.py','hail/genetics/allele_type.py','hail/genetics/call.py','hail/expr/aggregators/aggregators.py']},full_Hail_workflow_admitted=False,whole_goal_complete=False),indent=2)+'\n').encode('utf8')}
    resources.mkdir(parents=True,exist_ok=True)
    for name,raw in expected.items():
        path=resources/name
        if path.exists():assert path.read_bytes()==raw,'Never overwrite source/license resources'
        else:path.write_bytes(raw)
    source=ROOT/'src/main/java/org/jlinalg/genetics/HailSampleQc.java';driver=ROOT/'tools/literature/java/org/jlinalg/cli/HailSampleQcMatrix.java';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),source,driver,*sorted(resources.iterdir())]};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    for name in pins:
        snapshot=out/'source-snapshot'/name;snapshot.parent.mkdir(parents=True,exist_ok=True);snapshot.write_bytes((ROOT/name).read_bytes())
    classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),str(source),str(driver)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace');added={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};added.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in resources.iterdir()});jar=out/'omicsmatic-0.3.7-hail-sample-qc-development.jar'
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
        assert set(added).isdisjoint(before.namelist())
        for info in before.infolist():after.writestr(info,before.read(info.filename))
        for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and all(before.read(n)==after.read(n) for n in before.namelist());unchanged=len(before.namelist())
    assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;save(out/'COMPLETED.json',dict(status='compiled-native-Hail-sample-QC-not-original-runtime-validation',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),parent_artifact=parent_proof['artifact'],source_pins=pins,unchanged_parent_entries=unchanged,added_entries=sorted(added),canonical_executable_unchanged=canonical,implementation_credit=False,full_Hail_workflow_admitted=False,whole_goal_complete=False));print('Compiled',len(added),'new native sample-QC entries; no Hail integration credit')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/additive build checks')
    main()
