# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal complete scoped MTAG typed/root controls, preserving earlier failures and leaving catalog/live-root credit pending."""
import argparse
import json
import shutil
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE
from signac_scan_evidence import pack
from verify_mtag_workflow_evidence import HOME,STATUS,SOURCE_FILES,PREFIX,inspect


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True);assert not HOME.exists();HOME.mkdir(parents=True)
    (HOME/'.gitattributes').write_bytes(b'* -text\n')
    required={
        'workflow-options-2':'PASS_TYPED_ORIGINAL_MTAG_OPTION_MATRIX_AND_NATIVE_REFUSALS',
        'workflow-safety-2':'PASS_MTAG_TOOLS_SAFETY_AND_PORTABLE_SAVED_AUDIT',
        'dispatch-validation-2':'PASS_MTAG_STAGED_ROOT_WORKFLOWS_AND60_PORTABLE_OPTION_REPLAYS',
        'runtime-refusal-controls-3':'PASS5_MTAG_RUNTIME_REFUSAL_AND_PRESERVATION_CONTROLS',
        'scipy-tail-validation-1':'PASS_SOURCE_BOUND_SCIPY121_MTAG_TAIL_COMPONENT_ONLY'}
    for group,status in required.items():assert json.loads((BASE/group/'COMPLETED.json').read_bytes())['status']==status
    current=['workflow-options-2','workflow-safety-2','dispatch-validation-2','runtime-refusal-controls-3','scipy-tail-validation-1',
             'workflow-build21-9','workflow-build25-4','dispatch-build21-3','dispatch-build25-3',
             'workflow-runtime-setup-1','workflow-runtime-profile-2','workflow-lock-intake-1','cephes-source-intake-1']
    prior=['workflow-options-1','workflow-safety-1','dispatch-validation-1','runtime-refusal-controls-1','runtime-refusal-controls-2','cdf-boundary-discovery-1',
           'workflow-baseline-run-1','workflow-sidecar-baseline-1','workflow-contracts-1','workflow-runtime-profile-1',
           *['workflow-build21-'+str(n) for n in range(1,9)],*['workflow-build25-'+str(n) for n in range(1,4)],
           *['dispatch-build'+c+'-'+str(n) for c in ['21','25'] for n in [1,2]]]
    omitted=[]
    def collect(groups):
        files={}
        for group in groups:
            home=BASE/group;assert home.is_dir(),group
            for path in sorted(home.rglob('*')):
                if not path.is_file():continue
                relative=path.relative_to(home);parts=relative.parts
                setup=group.startswith('workflow-runtime-')
                skip=(setup and (parts[0] in ('archives','mtag-source','.conda','.mamba','mamba-root','micromamba')))
                skip|=group.startswith('runtime-refusal-controls-') and 'mtag-source' in parts
                if skip:
                    if len(parts)==1 or 'mtag-source' not in parts:omitted.append(dict(path=path.relative_to(ROOT).as_posix(),reason='Pinned upstream bootstrap/archive/cache or source extraction; original archive lock and complete source inventory retained'))
                    continue
                assert not path.is_symlink();files[PREFIX+group+'/'+relative.as_posix()]=path.read_bytes()
        return files
    current_files=collect(current);prior_files=collect(prior)
    retained=BASE/'workflow-public-before-exit-fix-1'
    assert retained.is_dir()
    prior_files['retained-public-before-exit-fix-1/file-pins.json']=(json.dumps({p.name:pin(p) for p in sorted(retained.iterdir()) if p.is_file()},indent=2)+'\n').encode()
    for platform in ['windows21','windows25','linux21']:
        history=BASE/('workflow-public-replay-'+platform+'-1')
        for path in history.iterdir():
            if path.is_file():prior_files['retained-replay-before-exit-fix-1/'+platform+'/'+path.name]=path.read_bytes()
    for name in SOURCE_FILES:current_files['current-source/'+name]=(ROOT/name).read_bytes()
    for name in ['LICENSE','README.md','mtag.py','mtag_munge.py','environment.yml']:
        current_files['upstream/source/'+name]=(BASE/'source-intake-1/source'/name).read_bytes()
    current_files['upstream/COMPLETED.json']=(BASE/'source-intake-1/COMPLETED.json').read_bytes()
    archives={}
    for name,files in [('controls.zip',current_files),('prior-attempts.zip',prior_files)]:
        record=pack(HOME/name,files);assert (HOME/name).stat().st_size<95_000_000,('GitHub-safe physical archive limit exceeded; retain before repartition',name)
        archives[name]=dict(archive=pin(HOME/name),**record)
    artifacts={}
    for compiler,build in [('21','workflow-build21-9'),('25','workflow-build25-4')]:
        for source,name in [(BASE/build/'mtag-workflow-development.jar','mtag-workflow-javac'+compiler+'.jar'),
                            (BASE/('dispatch-build'+compiler+'-3')/'mtag-root-supplement.jar','mtag-root-javac'+compiler+'.jar')]:
            shutil.copyfile(source,HOME/name);assert pin(source)==pin(HOME/name);artifacts[name]=pin(HOME/name)
    predecessor={}
    for folder in ['mtag-native-development','genesis-tools-development']:
        for path in sorted((ROOT/'docs/literature'/folder).iterdir()):
            if path.is_file():predecessor[path.relative_to(ROOT).as_posix()]=pin(path)
    rows=json.loads((BASE/'workflow-options-2/COMPLETED.json').read_bytes())['results']
    bundles=['workflow-options-2/'+r['case']+'/analysis' for r in rows if r['expected']==0]
    bundles+=['dispatch-validation-2/'+name+'/analysis' for name in ['root-full-ldsc-maxfdr','root-explicit-maf-qc','root-original-p-zero']]
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar'
    proof=dict(schema_version=1,status=STATUS,new_catalog_credit=False,workflow_ready_for_catalog=True,staged_root_command_available=True,
        live_root_modified=False,mtag_catalog_promotion_pending=True,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False,
        source_commit='9e17f3cf1fbcf57b6bc466daefdc51fd0de3c5dc',supported_source_versions=['1.0.8'],
        option_cases=25,admitted_option_workflows=20,expected_scientific_refusals=4,expected_capability_skips=1,option_trait_rows_checked=172625,
        root_original_attempts=4,root_admitted_analyses=3,root_expected_source_errors=1,root_expected_skips=3,root_option_saved_replays=60,root_analysis_saved_replays=9,
        portable_safety_protocols=24,setup_refusal_cases=5,original_source_tail_finite_comparisons=386088,source_tail_zeros_preserved=35853,
        original_ldsc_and_maxfdr_executed=True,independent_ldsc_or_maxfdr_refit=False,original_effective_values_repaired=False,
        participant_data_or_article_prose_distributed=False,upstream_runtime_binaries_or_package_archives_distributed=False,
        source_pins={name:pin(ROOT/name) for name in SOURCE_FILES},artifacts=artifacts,archives=archives,predecessor_evidence_files=predecessor,
        canonical_executable_unchanged=pin(canonical),saved_bundle_paths=bundles,retained_current_groups=current,retained_prior_groups=prior,
        omitted_upstream_runtime_extractions_and_caches=omitted,
        omitted_source_extractions_scope='Only setup scratch mtagsource/archives/bootstrap/package caches are omitted. The exact source archive,1096file inventory,33package archives and bootstrap remain cryptographically pinned for permitted reproduction. All selected synthetic workflow bundles are retained complete.',
        required_action='Read MTAG_WORKFLOW_DEVELOPMENT.md. Require exit0, result_admitted=true, complete completion manifest and successful saved verification before interpretation. SKIP dependent analysis when exact software/capabilities are unavailable or forbidden; supply permitted prerequisites or leave it skipped. FAIL2 admits no partial, repaired or unassessable result. Preserve every attempt and retry NEW paths after resolving the named cause. Live root dispatch, five paper-role applications and catalog/browser admission are still pending.')
    save(HOME/'VALIDATION.json',proof)
    (HOME/'SHA256SUMS').write_text(''.join(pin(path)['sha256']+'  '+path.name+'\n' for path in sorted(HOME.iterdir()) if path.is_file() and path.name!='SHA256SUMS'),encoding='utf8',newline='\n')
    verified,files,summary=inspect();assert verified==proof
    save(out/'COMPLETED.json',dict(status='PASS_COMPLETE_MTAG_WORKFLOW_EVIDENCE_SEALED_WITHOUT_CATALOG_CREDIT',summary=summary,validation=pin(HOME/'VALIDATION.json'),artifacts=artifacts,canonical_executable_unchanged=pin(canonical)))
    print(json.dumps(summary));return 0


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete typed/source/root/scientific evidence or predecessor preservation checks')
    raise SystemExit(main())
