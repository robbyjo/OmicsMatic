# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the native source-bound LeafCutter phenotype component additively; compilation grants no full-tool credit."""
import argparse,json,subprocess,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--intake',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);parent_proof=json.loads((ROOT/'docs/literature/leafcutter-clusters-development/VALIDATION.json').read_bytes());parent=ROOT/parent_proof['artifact']['path'];assert pin(parent)=={k:parent_proof['artifact'][k] for k in ['bytes','sha256']};intake=json.loads((a.intake/'COMPLETED.json').read_bytes());assert intake['status']=='PASS_ORIGINAL_LEAFCUTTER_PHENOTYPE_AND_NUMERIC_SUPPORT_SOURCES_ONLY';resources=ROOT/'src/main/resources/org/jlinalg/omics/leafcutter-phenotypes';profile=dict(schema_version=1,component='native-source-count-phenotype-preprocessing-without-PCA',original_script=intake['original_script'],numeric_support_sources=intake['records'],full_LeafCutter_workflow_admitted=False,whole_goal_complete=False);expected={'Apache-2.0.txt':(ROOT/'src/main/resources/org/jlinalg/omics/leafcutter-clusters/Apache-2.0.txt').read_bytes(),'NumPy-BSD.txt':(a.intake/'numpy/LICENSE.txt').read_bytes(),'scikit-learn-BSD.txt':(a.intake/'sklearn/COPYING').read_bytes(),'SOURCE-PROFILE.json':(json.dumps(profile,indent=2)+'\n').encode(),'NOTICE.txt':b'Modified Java phenotype preprocessing follows original LeafCutter prepare_phenotype_table.py, contributors David Knowles, Yang Li and others, repository Apache-2.0, commit2c9907ef66adf0bfb3092f0ceb6886ee5c046fbb. The contiguous pairwise summation implementation follows NumPy1.26.4 loops_utils.h.src (NumPy Developers, BSD-3-Clause); population standardization follows scikit-learn1.3.2 preprocessing.scale (scikit-learn contributors, BSD-3-Clause). All original source identities and complete notices are packaged. Inverse normal quantiles use existing JDistLib. PCA, compression/indexing, QTL fitting, R/Stan models, historical paper identity and full LeafCutter admission remain separate. Original data and source algorithms are not patched.\n'};resources.mkdir(parents=True,exist_ok=True)
    for name,raw in expected.items():
        path=resources/name
        if path.exists():assert path.read_bytes()==raw,'Never overwrite source/license resources'
        else:path.write_bytes(raw)
    sources=[ROOT/'src/main/java/org/jlinalg/sequence/LeafCutterPhenotypes.java',ROOT/'tools/literature/java/org/jlinalg/cli/LeafCutterPhenotypesMatrix.java'];pins={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),*sources,*sorted(resources.iterdir())]};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    for name in pins:path=out/'source-snapshot'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes((ROOT/name).read_bytes())
    classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace');added={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')};added.update({f.relative_to(ROOT/'src/main/resources').as_posix():f.read_bytes() for f in resources.iterdir()});jar=out/'omicsmatic-0.3.7-leafcutter-phenotypes-development.jar'
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
        assert set(added).isdisjoint(before.namelist())
        for info in before.infolist():after.writestr(info,before.read(info.filename))
        for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and all(before.read(n)==after.read(n) for n in before.namelist());unchanged=len(before.namelist())
    assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;save(out/'COMPLETED.json',dict(status='compiled-native-LeafCutter-phenotypes-not-original-validation',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),source_pins=pins,parent_artifact=parent_proof['artifact'],unchanged_parent_entries=unchanged,added_entries=sorted(added),canonical_executable_unchanged=canonical,implementation_credit=False,whole_goal_complete=False));print('Compiled',len(added),'new phenotype entries; no full LeafCutter credit')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source/additive build checks')
    main()
