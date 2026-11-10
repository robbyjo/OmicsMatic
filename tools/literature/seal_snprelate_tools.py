# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze project-owned SNPRelate workflow evidence without distributing upstream R runtimes or papers."""
import argparse
import json
import shutil
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin
from verify_snprelate_tools_evidence import HOME,BASE,STATUS,verify


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    base=ROOT/BASE;out=a.out.resolve();assert not out.exists() and out.is_relative_to(base) and not HOME.exists();out.mkdir(parents=True)
    builds={co:json.loads((base/name/'COMPLETED.json').read_bytes()) for co,name in [(21,'adapter-build21-6'),(25,'adapter-build25-3')]}
    sources={n:ROOT/n for n in builds[21]['source_pins']}
    sources.update({p.relative_to(ROOT).as_posix():p for p in (ROOT/'tools/literature').glob('*snprelate*') if p.is_file() and p.suffix=='.py' and 'catalog' not in p.name})
    sources.update({p.relative_to(ROOT).as_posix():p for p in (ROOT/'src/main/resources/org/jlinalg/omics/snprelate').glob('*') if p.is_file()})
    for name in ['src/main/java/org/jlinalg/cli/JLinAlgCli.java','src/test/java/org/jlinalg/cli/SnprelateRequestTest.java','docs/literature/SNPRELATE_INTEGRATION.md']:
        sources[name]=ROOT/name
    pins={n:pin(p) for n,p in sources.items()}
    assert all(pin(ROOT/n)==v for build in builds.values() for n,v in build['source_pins'].items())
    files={};objects={}
    def add(name,path):
        assert name not in files and not path.is_symlink();raw=path.read_bytes();files[name]=objects.setdefault(byte_pin(raw)['sha256'],raw)
    for n,p in sources.items():add('current-source/'+n,p)
    directories=['original-king-controls-6','own-edge-controls-3/mixed-row-index','own-edge-controls-3/zero-heterozygosity-row-index','own-edge-controls-3/all-missing-row-index',
        'original-workflow-mixed-1','original-workflow-mixed-2','original-workflow-mixed-3','original-workflow-zerohet-2','original-workflow-allmissing-2','original-workflow-wide-1',
        'own-wide-LD-control-1','own-PCA-rank-control-1','adapter-pilot-1','adapter-pilot-2','adapter-pilot-3',
        'adapter-build21-4','adapter-build21-6','adapter-build25-3','adapter-unit-contracts-3','adapter-semantics-1','dispatch-bridge-1','dispatch-build21-1','dispatch-build25-1','dispatch-validation-1']
    directories += ['original-PCA-'+n+'-1' for n in ['mixed','zerohet','wide','rank']]
    directories += [kind+str(part) for part in range(4) for kind in ['adapter-census-1-part','adapter-PCA-census-1-part','current-prior-replay-1-part']]
    for name in directories:
        folder=base/name;assert folder.is_dir(),name
        for f in sorted(folder.rglob('*')):
            if f.is_file() and '__pycache__' not in f.parts:
                assert f.suffix.lower() not in ['.exe','.dll','.so','.tar','.gz','.zip','.xz'],('Private upstream program/archive outside frozen evidence scope',str(f))
                add(f.relative_to(ROOT).as_posix(),f)
    for name in ['adapter-census-1-SUMMARY.json','make_wide_ld_control_1.py','make_PCA_rank_control_1.py','package_user_examples_1.py']:
        add((base/name).relative_to(ROOT).as_posix(),base/name)
    for f in (ROOT/'examples/snprelate-workflows').rglob('*'):
        if f.is_file():add('examples/snprelate-workflows/'+f.relative_to(ROOT/'examples/snprelate-workflows').as_posix(),f)
    cases=[];pca_cells=0
    for part in range(4):
        folder=base/f'adapter-census-1-part{part}';done=json.loads((folder/'COMPLETED.json').read_bytes())
        assert done['status']=='PASS_ORIGINAL_SNPRELATE_COMPLETE_TYPED_WORKFLOW_REFERENCE_CENSUS' and len(done['records'])==60
        for row in done['records']:cases.append(dict(bundle=(folder/row['case']).relative_to(ROOT).as_posix(),mode=row['mode'],expected=row['actual'],reference=row['original_reference']))
        folder=base/f'adapter-PCA-census-1-part{part}';done=json.loads((folder/'COMPLETED.json').read_bytes())
        assert done['status']=='PASS_ORIGINAL_SNPRELATE_COMPLETE_PCA_REFERENCE_PARTITION' and len(done['records'])==20
        for row in done['records']:
            cases.append(dict(bundle=(folder/row['case']).relative_to(ROOT).as_posix(),mode='pca',expected=0,reference=row['reference']));pca_cells+=row['original_eigenvector_cells']
    HOME.mkdir();artifacts={}
    for co in [21,25]:
        for name,path in [(f'snprelate-javac{co}.jar',ROOT/builds[co]['artifact']['path']),
            (f'snprelate-root-javac{co}.jar',base/f'dispatch-build{co}-1/snprelate-root-supplement.jar')]:
            shutil.copyfile(path,HOME/name);artifacts[name]=pin(HOME/name)
    packing=pack(out/'controls.zip',files);assert pin(out/'controls.zip')['bytes']<95000000;shutil.copyfile(out/'controls.zip',HOME/'controls.zip')
    previous=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-10-snprelate-native-1/selection.json').read_bytes())
    assert all(pin(ROOT/n)==v for n,v in previous['preserved'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==previous['canonical']
    proof=dict(schema_version=1,status=STATUS,new_catalog_credit=False,whole_goal_complete=False,whole_package_implemented=False,whole_paper_reproduced=False,
        source_pins=pins,artifacts=artifacts,archive=pin(HOME/'controls.zip'),cases=cases,case_dispositions=320,successful_own_original_analyses=266,
        empty_QC_preflight_refusals=54,complete_KING_GRM_matrix_cells=11520,complete_PCA_eigenvector_cells=pca_cells,current_prior_saved_checks=480,
        PCA_saved_checks=160,JUnit_test_executions=60,resealed_scientific_mutations_rejected=40,helper_skip_preservation_contracts=4,root_commands=33,
        root_original_analyses=4,canonical_executable_unchanged=previous['canonical'],unrelated_files_preserved=18,
        analysis_installs_software=False,original_runtime_programs_distributed=False,primary_article_prose_distributed=False,
        scope='Typed SNPRelate1.46.0 original KING-robust, LD pruning, six GRM estimators and exact/randomized PCA on complete caller-prepared hard calls. Native complete genotype/QC/identity checks and KING recomputation; source LD/GRM/PCA are not independently refitted. Exact eigen residual, orthogonality and covariance checks are invariants, not independent optimization. Source nonfinite values, seeds, signs and uncomputed placeholders retained. No historical paper settings, biological validity, producer authentication, Windows system-DLL closure or production-scale claim.',**packing)
    save(HOME/'VALIDATION.json',proof);verify(HOME)
    save(out/'COMPLETED.json',dict(status=STATUS,proof=pin(HOME/'VALIDATION.json'),archive=proof['archive'],whole_goal_complete=False));print(STATUS,proof['archive']['bytes'],flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source, full-population or canonical preservation checks')
    main()
