# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile native source-bound BioMart query planning additively; compilation does not admit the annotation client."""
import argparse,json,subprocess,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/biomart-runtime') and not out.exists();out.mkdir(parents=True);proof=json.loads((ROOT/'docs/literature/loftee-workflow-development/VALIDATION.json').read_bytes());parent=ROOT/proof['artifact']['path'];assert pin(parent)=={k:proof['artifact'][k] for k in ['bytes','sha256']};source=ROOT/'src/main/java/org/jlinalg/genetics/BioMartQueries.java';resources=ROOT/'src/main/resources/org/jlinalg/omics/biomart-queries';assert resources.is_dir() and {f.name for f in resources.iterdir()}=={'ARTISTIC-2.0.txt','NOTICE.txt','SOURCE-PROFILE.json'},'Require the checked-in original source/license resources; builds never rewrite scientific resources';pins={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),source,*sorted(resources.iterdir())]};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-d',str(classes),str(source)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace');added={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};added.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in resources.iterdir()});jar=out/'omicsmatic-0.3.7-biomart-queries-development.jar'
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
        assert set(added).isdisjoint(before.namelist())
        for info in before.infolist():after.writestr(info,before.read(info.filename))
        for name,value in sorted(added.items()):after.writestr(name,value,compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and all(before.read(n)==after.read(n) for n in before.namelist());parent_entries=len(before.namelist())
    assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;save(out/'COMPLETED.json',dict(status='compiled-native-BioMart-query-planning-not-client-validation',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),parent_artifact=proof['artifact'],source_pins=pins,unchanged_parent_entries=parent_entries,added_entries=sorted(added),canonical_executable_unchanged=canonical,annotation_client_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Compiled',len(added),'native query-planning entries; parent and canonical executable preserved')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable additive build/source checks')
    main()
