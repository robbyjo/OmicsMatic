# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify frozen cisTarget populations and stored numerical relationships without execution."""
import argparse,csv,io,json,math,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

HOME=ROOT/'docs/literature/cistarget-development'
BASE='build/literature-review/cistarget-runtime/'
STATUS='PASS_NATIVE_CISTARGET_RANKED_FEATURE_DEVELOPMENT_WITH_CATALOG_INTEGRATION_PENDING'


def verify(archive_only=False,home=HOME):
    home=Path(home);proof=json.loads((home/'VALIDATION.json').read_bytes());assert proof['status']==STATUS
    assert proof['standalone_native_tsv_workflow_available'] and not any(proof[k] for k in ['catalog_credit','root_command_available','whole_paper_reproduced','whole_goal_complete'])
    assert (proof['complete_kernel_comparisons'],proof['complete_tsv_workflows'],proof['original_Feather_native_flows'],proof['literal_example_runs'],proof['actual_cli_refusals'])==(672,96,6,6,60)
    assert proof['actual_missing_runtime_skips']==proof['actual_converter_capacity_refusals']==1
    assert pin(home/'controls.zip')==proof['archive'] and all(pin(home/n)==v for n,v in proof['artifacts'].items())
    if not archive_only:
        assert all(pin(ROOT/n)==v for n,v in proof['source_pins'].items())
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    files=unpack(home/'controls.zip',proof)
    for n,v in proof['source_pins'].items():assert byte_pin(files['current-source/'+n])==v
    def document(name):return json.loads(files[BASE+name])
    def rows(name):return list(csv.DictReader(io.StringIO(files[name].decode('utf8')),delimiter='\t'))
    def close(x,y):
        x=float(x);y=float(y)
        if math.isnan(x) or math.isnan(y):assert math.isnan(x) and math.isnan(y),(x,y);return
        assert math.isfinite(x) and math.isfinite(y) and abs(x-y)<=2e-12*max(1,abs(y)),(x,y)
    def successful(name):assert document(name+'/process.json')['exit_code']==0
    pairs=set()
    def matrix(left,right,labels=False):
        pair=(byte_pin(files[left])['sha256'],byte_pin(files[right])['sha256'],labels)
        if pair in pairs:return
        first=files[left].decode().splitlines();second=files[right].decode().splitlines()
        if labels:first=first[1:]
        assert len(first)==len(second)
        for a,b in zip(first,second,strict=True):
            x=a.split('\t')[1:] if labels else a.split('\t');y=b.split('\t');assert len(x)==len(y)
            for u,v in zip(x,y,strict=True):close(u,v)
        pairs.add(pair)
    def background(left,right):
        pair=(byte_pin(files[left])['sha256'],byte_pin(files[right])['sha256'],'background')
        if pair in pairs:return
        x=rows(left);y=rows(right);assert len(x)==len(y)
        for a,b in zip(x,y,strict=True):
            assert float(a['rank'])==float(b['rank'])
            for field in ['mean','sd','mean_plus_two_sd']:close(a[field],b[field])
        pairs.add(pair)
    def metrics(left,right,named=False):
        actual=rows(left);expected=rows(right);assert len(actual)==len(expected)
        for a,b in zip(actual,expected,strict=True):
            assert a['motif']==('motif_'+str(int(float(b['motif']))) if named else b['motif'])
            assert float(a['leading_rank'])==float(b['leading_rank'])
            assert a['status']==('UNDEFINED_NES' if math.isnan(float(b['nes'])) else 'DEFINED_NES')
            for field in ['auc','nes','leading_recovery','leading_excess']:close(a[field],b[field])
            if not named:close(a['direct_auc'],b.get('direct_auc','nan'))
        return actual
    def target_members(left,right,named=False):
        actual=rows(left);expected=rows(right)
        a=[(int(r['motif'].split('_')[-1]) if named else int(r['motif']),int(r['feature'].split('_')[-1]) if named else int(r['feature']),float(r['raw_rank' if named else 'rank']),float(r['weight'])) for r in actual]
        b=sorted((int(r['motif']),int(r['feature']),float(r['rank']),float(r['weight'])) for r in expected)
        assert a==b;return actual
    reference=document('reference-controls-1/COMPLETED.json');assert reference['status']=='PASS_64_ORIGINAL_CISTARGET_PREPARED_RECOVERY_AND_LEADING_EDGE_FAMILIES_ONLY'
    assert not reference['full_R_package_installed'] and not reference['full_database_file_workflow_executed']
    assert len(reference['original_source_pins_unchanged'])==96
    for language in ['python','R']:successful('reference-controls-1/'+language)
    cases=document('controls-1/cases.json');assert len(cases)==64
    controls=document('controls-1/COMPLETED.json');assert all(byte_pin(files[BASE+'controls-1/'+n])==v for n,v in controls['files'].items())
    kernel=document('recovery-validation-3/COMPLETED.json');assert kernel['complete_recovery_comparisons']==384 and kernel['contract_suites']==6 and kernel['status'].startswith('PASS_')
    platforms={(c,p) for c in ['javac21','javac25'] for p in ['windows21','windows25','linux21']}
    assert {(r['compiler'],r['platform']) for r in kernel['runs']}==platforms
    for compiler,number in [('javac21','21'),('javac25','25')]:
        built=document('recovery-build'+number+'-3/COMPLETED.json');workflow=document('workflow-build'+number+'-2/COMPLETED.json');licensed=document('recovery-license'+number+'-1/COMPLETED.json')
        assert built['status']=='PASS_ISOLATED_JAVA17_CISTARGET_RECOVERY_COMPONENT_BUILD_ONLY' and workflow['status']=='PASS_ISOLATED_CISTARGET_TSV_WORKFLOW_BUILD_ONLY'
        for record in [built,workflow,licensed]:
            for name,value in record['source_pins'].items():assert byte_pin(files['current-source/'+name])==value
        kernel_jar=home/('cistarget-recovery-'+compiler+'.jar');workflow_jar=home/('cistarget-workflow-'+compiler+'.jar')
        assert byte_pin(files[licensed['artifact']['path']])==pin(kernel_jar) and byte_pin(files[workflow['artifact']['path']])==pin(workflow_jar)
        assert licensed['status']=='PASS_LICENSED_CISTARGET_RECOVERY_ARTIFACT_WITH_UNCHANGED_SCIENTIFIC_BYTES' and licensed['parent_artifact']==built['artifact']
        with zipfile.ZipFile(io.BytesIO(files[built['artifact']['path']])) as original,zipfile.ZipFile(kernel_jar) as published:
            assert set(published.namelist())==set(original.namelist())|{'META-INF/NUMPY-LICENSE.txt'}
            assert all(published.read(n)==original.read(n) for n in original.namelist())
            assert published.read('META-INF/NUMPY-LICENSE.txt')==files['current-source/docs/literature/CISTARGET_NUMPY_LICENSE.txt']
        with zipfile.ZipFile(kernel_jar) as old,zipfile.ZipFile(workflow_jar) as new:
            assert all(old.read(n)==new.read(n) for n in old.namelist())
            for name in new.namelist():
                assert name in ['META-INF/LICENSE','META-INF/NUMPY-LICENSE.txt'] or name.endswith('.class') and name.startswith(('org/jlinalg/enrichment/CisTargetRecovery','org/jlinalg/cli/CisTargetWorkflowCli'))
                if name.endswith('.class'):assert int.from_bytes(new.read(name)[6:8],'big')==61
    for run in kernel['runs']:
        prefix='recovery-validation-3/'+run['compiler']+'-'+run['platform'];successful(prefix+'/contracts');successful(prefix+'/values');assert len(run['cases'])==64
        for case in cases:
            native=BASE+prefix+'/native/'+case['id'];source=BASE+'reference-controls-1/'+('ctxcore' if case['profile']=='CTXCORE_0_2_0' else 'rcistarget')+'/'+case['id']
            metrics(native+'/metrics.tsv',source+'/metrics.tsv');target_members(native+'/targets.tsv',source+'/targets.tsv')
            matrix(native+'/curves.tsv',source+'/curves.tsv');background(native+'/background.tsv',source+'/background.tsv')
            left=rows(native+'/parameters.tsv');right=rows(source+'/parameters.tsv');assert len(left)==len(right)==1
            for field in right[0]:close(left[0][field],right[0][field])
    pairwise=document('pairwise-validation-1/COMPLETED.json');assert pairwise['complete_recovery_comparisons']==288 and pairwise['contract_suites']==6
    assert {(r['compiler'],r['platform']) for r in pairwise['runs']}==platforms
    extra=document('pairwise-controls-1/cases.json');assert len(extra)==48
    assert document('pairwise-reference-controls-1/COMPLETED.json')['status']=='PASS48_ORIGINAL_CTXCORE_NUMPY_REDUCTION_BOUNDARY_FAMILIES_ONLY';successful('pairwise-reference-controls-1/python')
    for run in pairwise['runs']:
        prefix='pairwise-validation-1/'+run['compiler']+'-'+run['platform'];successful(prefix+'/contracts');successful(prefix+'/values');assert len(run['cases'])==48
        for case in extra:
            native=BASE+prefix+'/native/'+case['id'];source=BASE+'pairwise-reference-controls-1/ctxcore/'+case['id']
            metrics(native+'/metrics.tsv',source+'/metrics.tsv');target_members(native+'/targets.tsv',source+'/targets.tsv');matrix(native+'/curves.tsv',source+'/curves.tsv');background(native+'/background.tsv',source+'/background.tsv')
            for field,value in rows(source+'/parameters.tsv')[0].items():close(rows(native+'/parameters.tsv')[0][field],value)
    workflow=document('workflow-validation-4/COMPLETED.json');assert workflow['status']=='PASS96_NATIVE_CISTARGET_TSV_WORKFLOWS_AND60_ACTUAL_CLI_REFUSALS_ONLY'
    assert (workflow['complete_file_workflow_comparisons'],workflow['actual_cli_refusals'])==(96,60)
    assert {(r['compiler'],r['platform']) for r in workflow['runs']}==platforms
    file_cases=document('workflow-controls-1/cases.json');assert len(file_cases)==16
    for run in workflow['runs']:
        prefix='workflow-validation-4/'+run['compiler']+'-'+run['platform'];assert run['actual_cli_refusals']==10
        for case in file_cases:
            successful(prefix+'/'+case['id']+'-process');native=BASE+prefix+'/'+case['id'];source=case['original_reference']
            receipt=json.loads(files[native+'/run.json']);assert receipt['status']=='PASS_NATIVE_CISTARGET_WITH_WARNINGS'
            for name,value in receipt['result_sha256'].items():assert byte_pin(files[native+'/'+name])['sha256']==value
            actual=metrics(native+'/all-motifs.tsv',source+'/metrics.tsv',True);targets=target_members(native+'/leading-targets.tsv',source+'/targets.tsv',True)
            matrix(native+'/recovery-curves.tsv',source+'/curves.tsv',True);background(native+'/recovery-background.tsv',source+'/background.tsv')
            settings=document('workflow-controls-1/'+case['id']+'/settings.json');selected=[]
            for motif in actual:
                auc=float(motif['auc']);nes=float(motif['nes']);passed=math.isfinite(nes) and (auc>settings['minimum_auc'] if settings['strict_auc'] else auc>=settings['minimum_auc']) and (nes>settings['minimum_nes'] if settings['strict_nes'] else nes>=settings['minimum_nes'])
                assert motif['passes_thresholds']==str(passed).lower()
                if passed:selected.append(motif)
            assert rows(native+'/enriched-motifs.tsv')==selected and receipt['selected_motifs']==len(selected)
            passing={r['motif'] for r in selected};annotations=rows(BASE+'workflow-controls-1/'+case['id']+'/annotations.tsv')
            expected=[dict(tf=a['tf'],motif=t['motif'],feature=t['feature'],evidence=a['evidence']) for t in targets if t['motif'] in passing for a in annotations if a['motif']==t['motif']]
            assert rows(native+'/tf-targets.tsv')==expected
            dispositions=rows(native+'/signature-dispositions.tsv');assert [r['disposition'] for r in dispositions]==['PRESENT']*case['present']+['MISSING_FROM_DATABASE']
        for name in ['coverage','unknown-key','duplicate-columns','duplicate-motif','ragged','invalid-cell','duplicate-signature','nan-weight','duplicate-annotation','existing']:
            assert document(prefix+'/refused-'+name+'-process/process.json')['exit_code']==2
            assert BASE+prefix+'/refused-'+name+'/enriched-motifs.tsv' not in files
    feather=document('feather-validation-3/COMPLETED.json');assert feather['status']=='PASS_ORIGINAL_PUBLIC_FEATHER_REFERENCE_TWO_LOSSLESS_EXPORTS_SIX_NATIVE_FLOWS_AND_ACTUAL_SKIP_REFUSAL_ONLY'
    assert {(r['compiler'],r['platform']) for r in feather['runs']}==platforms
    original=document('feather-validation-3/original-public-file-reference.json');assert original['full_database_features']==22284 and len(original['motifs'])==5
    assert all(math.isfinite(m['nes']) for m in original['motifs']) and any(m['auc']>0 for m in original['motifs']) and original['targets_in_original_rank_order']
    for run in feather['runs']:
        prefix='feather-validation-3/'+run['compiler']+'-'+run['platform'];successful(prefix+'-process');native=BASE+prefix
        actual={r['motif']:r for r in rows(native+'/all-motifs.tsv')};assert set(actual)==set(original['all_background_motifs'])
        for expected in original['motifs']:
            motif=actual[expected['motif']];assert int(motif['leading_rank'])==expected['leading_rank']
            for field in ['auc','nes','leading_recovery','leading_excess']:close(motif[field],expected[field])
        assert sorted((r['motif'],r['feature'],float(r['weight'])) for r in rows(native+'/leading-targets.tsv'))==sorted((r['motif'],r['feature'],r['weight']) for r in original['targets_in_original_rank_order'])
    assert document('feather-validation-3/missing-runtime/COMPLETED.json')['status']=='SKIP3_FEATHER_CONVERSION_RUNTIME_UNAVAILABLE'
    assert document('feather-validation-3/capacity-refused/COMPLETED.json')['status']=='FAIL2_FEATHER_CONVERSION_NOT_ADMITTED'
    assert document('feather-validation-3/missing-runtime-process/process.json')['exit_code']==3 and document('feather-validation-3/capacity-refused-process/process.json')['exit_code']==2
    assert not any(BASE+'feather-validation-3/'+n+'/rankings.tsv' in files for n in ['missing-runtime','capacity-refused'])
    example=document('literal-example-2/COMPLETED.json');assert example['status']=='PASS_SIX_LITERAL_CISTARGET_EXAMPLE_RUNS_WITH_ANALYTIC_AUC_AND_IDENTICAL_RESULTS' and len(example['runs'])==6
    for run in example['runs']:
        prefix='literal-example-2/'+run['compiler']+'-'+run['platform'];successful(prefix);native=BASE+prefix+'/native'
        for n,v in run['result_pins'].items():assert byte_pin(files[native+'/'+n])==v
        close(rows(native+'/all-motifs.tsv')[0]['auc'],7/15);assert len(rows(native+'/enriched-motifs.tsv'))==1
    audit=document('primary-roles-3/COMPLETED.json');assert (audit['papers_scanned'],audit['source_roles'],audit['observed_source_roles'],audit['pending_new_observations'])==(250,7,6,1)
    assert audit['catalog_unchanged'] and audit['availability_unchanged'] and not audit['implementation_credit']
    drafts=document('primary-roles-3/role-drafts.json')['reviews'];assert {r['paper_id'] for r in drafts}=={'P047','P058','P061','P065','P094','P100'}
    assert sum(r['prior_review'] is not None for r in drafts)==1 and not document('primary-roles-3/pending-observation.json')['applied']
    assert document('recovery-validation-1/COMPLETED.json')['status']=='FAIL2_CISTARGET_NATIVE_RECOVERY_CONTROLS'
    assert all(document('reference-setup-'+n+'/COMPLETED.json')['status']=='SKIP3_CISTARGET_REFERENCE_SETUP_UNAVAILABLE' for n in ['1','2'])
    assert not any('/venv/' in n or '/source-intake-1/ctxcore-original/' in n or '/source-intake-1/rcistarget-original/' in n or n.endswith('.feather') for n in files)
    return proof,files


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable frozen evidence checks')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--archive-only',action='store_true');args=parser.parse_args();proof,_=verify(args.archive_only)
    print(proof['status']+';672 complete kernel comparisons;108 native file/example flows; catalog integration pending')
