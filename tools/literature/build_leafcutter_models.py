# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile the independent native original-posterior/effects audit additively; no optimizer or full named-tool credit."""
import argparse,json,subprocess,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--intake',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True)
    parent_proof=json.loads((ROOT/'docs/literature/leafcutter-phenotypes-development/VALIDATION.json').read_bytes());parent=ROOT/parent_proof['artifact']['path'];assert pin(parent)=={k:parent_proof['artifact'][k] for k in ['bytes','sha256']}
    intake=json.loads((a.intake/'COMPLETED.json').read_bytes());assert intake['status']=='PASS_THREE_COMPLETE_ORIGINAL_LEAFCUTTER_R_PACKAGE_TREES_ONLY';edition=intake['editions']['source-2c9907ef66ad'];names=['leafcutter/inst/stan/dm_glm_multi_conc.stan','leafcutter/inst/stan/dm_glm_robust.stan','leafcutter/R/differential_splicing.R','leafcutter/R/dm_glm_multi_conc.R'];members={name:edition['members'][name] for name in names}
    for name,want in members.items():assert pin(a.intake/'source-2c9907ef66ad/source'/name)=={k:want[k] for k in ['bytes','sha256']}
    profile=dict(schema_version=1,revision=edition['revision'],R_package_version=edition['R_package_version'],source_members=members,scope='Evaluate supplied constrained parameters with original dropped-constant posterior, concentration-weighted effects and nested-model LRT; no fitting or full LeafCutter workflow.',implementation_credit=False,whole_goal_complete=False)
    resources=ROOT/'src/main/resources/org/jlinalg/omics/leafcutter-models';resources.mkdir(parents=True,exist_ok=True);expected={'SOURCE-PROFILE.json':(json.dumps(profile,indent=2)+'\n').encode(),'Apache-2.0.txt':(ROOT/'src/main/resources/org/jlinalg/omics/leafcutter-clusters/Apache-2.0.txt').read_bytes(),'NOTICE.txt':b'Independent Java evaluation of multi-concentration/robust Dirichlet-multinomial posterior and R effect-size/LRT definitions from LeafCutter, David Knowles, Yang Li and contributors, revision2c9907ef66adf0bfb3092f0ceb6886ee5c046fbb. Repository Apache-2.0; R package DESCRIPTION GPL (>=3). This Java implementation is GPL-3.0-or-later as part of OmicsMatic. Log-gamma and chi-square tails use existing JDistLib. No model optimization, paper replication or full named-tool credit is established. Exact original source identities are packaged.\n'}
    for name,raw in expected.items():
        path=resources/name
        if path.exists():assert path.read_bytes()==raw,'Never overwrite prior model source/profile resources'
        else:path.write_bytes(raw)
    sources=[ROOT/'src/main/java/org/jlinalg/sequence/LeafCutterModels.java',ROOT/'tools/literature/java/org/jlinalg/cli/LeafCutterModelsMatrix.java'];pins={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),*sources,*sorted(resources.iterdir())]};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    for name in pins:target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    added={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')};added.update({f.relative_to(ROOT/'src/main/resources').as_posix():f.read_bytes() for f in resources.iterdir()});jar=out/'omicsmatic-0.3.7-leafcutter-models-development.jar'
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
        assert set(added).isdisjoint(before.namelist())
        for info in before.infolist():after.writestr(info,before.read(info.filename))
        for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and all(before.read(n)==after.read(n) for n in before.namelist());unchanged=len(before.namelist())
    assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
    save(out/'COMPLETED.json',dict(status='compiled-native-LeafCutter-model-evaluator-not-original-validation',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),source_pins=pins,parent_artifact=parent_proof['artifact'],unchanged_parent_entries=unchanged,added_entries=sorted(added),canonical_executable_unchanged=canonical,implementation_credit=False,whole_goal_complete=False));print('Compiled',len(added),'new model evaluation entries; no optimizer/full-tool credit')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source/build checks')
    main()
