# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build source-bound native LeafCutter cluster component additively; no full-tool implementation credit."""
import argparse,json,subprocess,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--intake',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);parent_proof=json.loads((ROOT/'docs/literature/hail-workflow-development/VALIDATION.json').read_bytes());parent=ROOT/parent_proof['artifact']['path'];assert pin(parent)=={k:parent_proof['artifact'][k] for k in ['bytes','sha256']};intake=json.loads((a.intake/'COMPLETED.json').read_bytes());original=intake['editions']['source-2c9907ef66ad'];assert original['revision']=='2c9907ef66adf0bfb3092f0ceb6886ee5c046fbb';resources=ROOT/'src/main/resources/org/jlinalg/omics/leafcutter-clusters';source_name='clustering/leafcutter_cluster_regtools.py';profile=dict(schema_version=1,component='native-RegTools-BED12-clustering-and-complete-counts-only',revision=original['revision'],R_package_version_reported_in_source=original['R_package_version'],source=original['members'][source_name],repository_license=original['members']['LICENSE'],historical_paper_version_equivalence=False,full_LeafCutter_workflow_admitted=False,whole_goal_complete=False);expected={'Apache-2.0.txt':(a.intake/'source-2c9907ef66ad/source/LICENSE').read_bytes(),'NOTICE.txt':b'Native LeafCutter clustering/counts are a modified Java implementation following the original clustering/leafcutter_cluster_regtools.py, David Knowles, Yang Li and LeafCutter contributors, repository Apache-2.0, exact commit2c9907ef66adf0bfb3092f0ceb6886ee5c046fbb. Original source and complete control identities are retained. Python source is not patched. This component is not R/Stan differential splicing, phenotype normalization/PCA, junction extraction, historical paper binary parity or a full LeafCutter integration. R package code has a separate GPL>=3 declaration; it is not compiled by this component.\n','SOURCE-PROFILE.json':(json.dumps(profile,indent=2)+'\n').encode()};resources.mkdir(parents=True,exist_ok=True)
    for name,raw in expected.items():
        path=resources/name
        if path.exists():assert path.read_bytes()==raw,'Never overwrite frozen source/license resources'
        else:path.write_bytes(raw)
    sources=[ROOT/'src/main/java/org/jlinalg/sequence/LeafCutterClusters.java',ROOT/'tools/literature/java/org/jlinalg/cli/LeafCutterClustersMatrix.java'];pins={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),*sources,*sorted(resources.iterdir())]};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    for name in pins:path=out/'source-snapshot'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes((ROOT/name).read_bytes())
    classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace');added={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')};added.update({f.relative_to(ROOT/'src/main/resources').as_posix():f.read_bytes() for f in resources.iterdir()});jar=out/'omicsmatic-0.3.7-leafcutter-clusters-development.jar'
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
        assert set(added).isdisjoint(before.namelist())
        for info in before.infolist():after.writestr(info,before.read(info.filename))
        for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and all(before.read(n)==after.read(n) for n in before.namelist());unchanged=len(before.namelist())
    assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;save(out/'COMPLETED.json',dict(status='compiled-native-LeafCutter-clusters-not-original-validation',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),source_pins=pins,parent_artifact=parent_proof['artifact'],unchanged_parent_entries=unchanged,added_entries=sorted(added),canonical_executable_unchanged=canonical,implementation_credit=False,whole_goal_complete=False));print('Compiled',len(added),'new cluster component entries; full LeafCutter not admitted')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source/additive build checks')
    main()
