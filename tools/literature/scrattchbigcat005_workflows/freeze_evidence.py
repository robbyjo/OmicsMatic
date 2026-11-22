#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete scoped BIGCAT records; this does not change the paper catalog."""
from pathlib import Path
import json,sys,traceback
sys.dont_write_bytecode=True
from Runtime import ROOT,HERE,load,save,pin,sources,inventory
from Claims import check_report
from Evidence import Writer

B=ROOT/'build/literature-review/monocle-runtime'
DATA=ROOT/'docs/literature/scrattchbigcat005-workflow-validation'
EXCLUDED=('font-cache','xdg-cache')

def freeze():
    if DATA.exists():raise FileExistsError('Retain existing evidence; use verification, never refresh old hashes')
    batch=B/'scrattchbigcat005-workflow-comparison-3';inputs=B/'scrattchbigcat005-workflow-controls-5/inputs'
    runs=load(batch/'EXECUTED.json');assert len(runs['cases'])==24 and runs['module_sources_unchanged']
    comparisons=load(B/'scrattchbigcat005-stronger-comparisons-2/COMPLETED.json')
    assert comparisons['status']=='STRONGER_COMPLETE_OBJECT_COMPARISONS_RECORDED_NOT_YET_ADMISSION'
    current=sources();specifications={};claims={}
    definitions=load(inputs/'CONTROLS.json')['cases']
    for run in runs['cases']:
        name=run['case'];definition=definitions[name]
        assert load(batch/name/'adapter/INPUTS.json')['adapter_sources']==current
        report=comparisons['cases'][name];assert report['exit_code']==0
        claims[name]=check_report(report['result'],definition['operation'],run['adapter_COMPLETED'])
        if not run['adapter_COMPLETED']:
            assert run['adapter_exit_code']==2 and not (batch/name/'adapter/COMPLETED.json').exists()
        specifications[name]=dict(profile=definition['profile'],operation=definition['operation'],
          case='controls/'+name,inputs='inputs/main',request='inputs/main/'+definition['request'],
          returned=run['adapter_COMPLETED'],comparison_keys=sorted(k for k,v in report['result']['checks'].items() if 'all_equal' in v))
    bins=B/'scrattchbigcat005-nonzero-bin-comparison-1';bin_name='mapping-nonzero-four-partitions'
    bin_run=load(bins/'EXECUTED.json')['cases'];assert len(bin_run)==1 and bin_run[0]['adapter_COMPLETED']
    report=load(bins/'COMPARED.json')['cases'][bin_name];assert report['exit_code']==0
    claims[bin_name]=check_report(report['result'],'map_cells_knn_big',True)
    specifications[bin_name]=dict(profile='prepublication-2023',operation='map_cells_knn_big',
      case='controls/'+bin_name,inputs='inputs/nonzero-bins',request='inputs/nonzero-bins/requests/'+bin_name+'.rds',
      returned=True,comparison_keys=sorted(k for k,v in report['result']['checks'].items() if 'all_equal' in v))
    inspection=load(B/'scrattchbigcat005-nonzero-bin-inspection-2/INSPECTION.json')
    assert inspection['status']=='PASS_FOUR_NONZERO_ORIGINAL_PARTITIONS_AND_INDEPENDENT_MAPPING' and inspection['nonzero_entries']==30
    guards=load(B/'scrattchbigcat005-workflow-guards-2/CHECKED.json')
    assert guards['status']=='PASS_ALL_TEN_PREFLIGHT_AND_POST_RETURN_RECORDING_GUARDS' and guards['module_sources_unchanged']
    mutations=B/'scrattchbigcat005-saved-result-mutations-1';mutation=load(mutations/'COMPLETED.json')
    assert (mutation['accepted_unchanged'],mutation['rejected_changed'],mutation['originals_unchanged'])==(2,11,True)
    skip=load(B/'scrattchbigcat005-unavailable-profile-skip-1/original/R-status.json')
    assert skip['exit_code']==3 and not skip['analysis_started']
    assert load(B/'scrattchbigcat005-windows-readiness-1/FAILURE.json')['status']=='SKIP3_BIGCAT_PREREQUISITE_UNAVAILABLE'
    pdf=load(B/'scrattchbigcat005-plot-pdf-comparison-1/PDF-COMPARISONS.json')
    assert pdf['pdf_pairs']==15 and pdf['pages_per_side']==18 and all(r['all_pages_identical'] for r in pdf['comparisons'])
    restoration=load(B/'scrattchbigcat005-cache-restoration-1/COMPLETED.json')
    assert restoration['entries_moved']==88 and not restoration['original_provider_hashes_refreshed']
    installs=B/'scrattchbigcat005-installation-record-capture-1'
    capture=load(installs/'CAPTURE.json')
    for name,identity in capture['files'].items():
        assert pin(installs/name)=={k:identity[k] for k in ['bytes','sha256']},name
    for mode in ['online','offline']:
        assert load(installs/mode/'COMPLETED.json')['status']=='PASS_INSTALLED_AND_AUDITED_ORIGINAL_BIGCAT_PROFILE_NOT_ANALYSIS'
    smokes=['scrattchbigcat005-public-online-clustering-smoke-1','scrattchbigcat005-public-offline-harmonization-smoke-1']
    for name in smokes:assert load(B/name/'COMPLETED.json')['status']=='PASS_ORIGINAL_BIGCAT_COMPLETE_RETURN_AND_ASSETS'
    native=load(ROOT/'docs/literature/scrattchbigcat005-native-development/VALIDATION.json')
    for name,identity in native['source_files'].items():assert pin(ROOT/name)==identity,name
    module={p.name:pin(p) for p in HERE.iterdir() if p.is_file() and p.name!='CATALOG_STATUS.md'}
    proof=dict(status='PASS_ORIGINAL_BIGCAT_REPORTED_WORKFLOWS_WITH_DISCLOSED_R_OBJECT_LIMITS',
      adapter_sources=current,module_sources=module,native_source_files=native['source_files'],
      original_controls=25,complete_returns=19,original_failures_without_result=6,
      reported_function_roles=8,compound_workflows=2,profiles=['prepublication-2023','pre-regression-2022'],
      original_namespace_functions={'prepublication-2023':380,'pre-regression-2022':373},original_Arrow_functions=15,
      cases=specifications,scientific_comparisons=claims,compared_fields=sum(r['compared_fields'] for r in claims.values()),
      independent_centroid_maximum_absolute_difference=1.7763568394002505e-15,
      over_200_training_cells_per_class=240,nonzero_Parquet_partitions=4,
      runtime_policy_and_recording_guards=12,unchanged_semantic_controls=2,rejected_resealed_semantic_controls=11,
      pdf_pairs=15,rendered_pages_per_side=18,complete_rendered_PDF_pixels_identical=True,
      original_conditions_and_RNG_retained=True,complete_RNG_comparisons_for_returned_controls=19,
      original_condition_fields_deleted_or_coerced=False,opaque_R_identity_differences_retained=True,
      live_R_object_or_pointer_identity_claimed=False,source_or_dependency_algorithms_patched=False,
      author_source_commits_established=False,P058_edition_established=False,whole_tool_qualified=False,
      whole_paper_reproduced=False,qualification_is_catalog_admission=False,new_family_credit=0,whole_goal_complete=False,
      storage='Complete logical evidence trees; identical file payloads stored once by SHA256',
      excluded_ephemeral_resources='Font caches remain in original verification output/quarantine; full inventories and configurations are retained. Installed binary prefixes are represented by complete inventories and locked source/install records.')
    DATA.mkdir(parents=True,exist_ok=False)
    (DATA/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    bundle=DATA/'original-workflow-and-installation-evidence.zip';writer=Writer(bundle)
    writer.tree('module',HERE,exclude=('CATALOG_STATUS.md',))
    writer.add('dependencies/arrow-source-profile.json',ROOT/'tools/literature/arrow1201/source-profile.json')
    writer.tree('inputs/main',inputs);writer.tree('inputs/nonzero-bins',B/'scrattchbigcat005-nonzero-bin-control-1/inputs')
    for name in definitions:writer.tree('controls/'+name,batch/name)
    writer.tree('controls/'+bin_name,bins/bin_name)
    writer.tree('stronger-replay',B/'scrattchbigcat005-stronger-comparisons-2',exclude=EXCLUDED)
    writer.tree('installation',installs)
    for year in ['2022','2023']:writer.tree('reference-providers/'+year,B/('scrattchbigcat005-workflow-provider-'+year+'-dev-5'))
    writer.tree('policy-guards',B/'scrattchbigcat005-workflow-guards-2')
    writer.tree('windows-skip',B/'scrattchbigcat005-windows-readiness-1')
    writer.tree('unavailable-operation-skip',B/'scrattchbigcat005-unavailable-profile-skip-1')
    writer.tree('PDF-render-comparison',B/'scrattchbigcat005-plot-pdf-comparison-1')
    writer.tree('bin-inspection',B/'scrattchbigcat005-nonzero-bin-inspection-2')
    writer.tree('FBM-inspection',B/'scrattchbigcat005-FBM-class-inspection-2')
    for name in smokes:writer.tree('installed-runtime-smokes/'+name,B/name)
    writer.tree('test-programs',B/'scrattchbigcat005-workflow-bootstrap-1')
    for name in ['scrattchbigcat005-workflow-candidate-2-source','scrattchbigcat005-workflow-candidate-3-source',
      'scrattchbigcat005-workflow-candidate-4-source','scrattchbigcat005-runtime-drift-inspection-1',
      'scrattchbigcat005-cache-restoration-1','scrattchbigcat005-stronger-comparisons-1']:
        writer.tree('development-history/'+name,B/name,exclude=EXCLUDED)
    for path in mutations.iterdir():
        if path.is_file():writer.add('semantic-guards/'+path.name,path)
    deltas={}
    for label,entry in mutation['cases'].items():
        target=mutations/label;baseline=batch/entry['original_case'];changed=[]
        for path in target.rglob('*'):
            if not path.is_file() or path.is_symlink():continue
            relative=path.relative_to(target)
            if relative.parts[0]=='case':
                original=baseline.joinpath(*relative.parts[1:])
                if original.is_file() and pin(original)==pin(path):continue
                changed.append(relative.as_posix())
            writer.add('semantic-guards/'+label+'/'+relative.as_posix(),path)
        deltas[label]=dict(baseline='controls/'+entry['original_case'],changed_case_files=changed)
    # Keep generated metadata inside this NEW evidence directory, so a retained
    # earlier packaging attempt cannot collide with a shared scratch filename.
    delta=DATA/'semantic-delta-index.json';save(delta,deltas)
    writer.add('semantic-guards/DELTA-INDEX.json',delta)
    proof['evidence_layout']=writer.finish(proof);proof['evidence_bundle']=pin(bundle)
    proof['evidence_payloads']={p.name:pin(p) for p in writer.part_paths}
    if any(p['bytes']>=95_000_000 for p in [proof['evidence_bundle'],*proof['evidence_payloads'].values()]):
        raise ValueError('An evidence volume exceeds the selected repository file budget; retain candidate and qualify NEW storage')
    for name,identity in module.items():assert pin(HERE/name)==identity,name
    save(DATA/'QUALIFICATION.json',proof)
    print('FROZEN_SCOPED_BIGCAT_EVIDENCE',proof['evidence_bundle']['bytes'],proof['evidence_layout'],flush=True)

if __name__=='__main__':
    try:freeze()
    except BaseException as error:
        print('FAIL2_BIGCAT_EVIDENCE_NOT_ADMITTED',repr(error),flush=True);raise
