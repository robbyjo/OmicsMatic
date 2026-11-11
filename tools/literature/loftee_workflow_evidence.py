# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Strict portable source/output proof for the scoped LOFTEE adapter; independent model fitting and catalog credit remain separate."""
import copy,json,zipfile
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from compare_loftee_original import compare_tables

def archived_table(raw):
    lines=raw.decode('utf8').splitlines();return dict(CSQ_header=next(s for s in lines if s.startswith('##INFO=<ID=CSQ,')),column_header=next(s for s in lines if s.startswith('#CHROM\t')),records=[s for s in lines if not s.startswith('#')])

def verify():
    folder=ROOT/'docs/literature/loftee-workflow-development';p=json.loads((folder/'VALIDATION.json').read_bytes());assert p['schema_version']==1 and p['status']=='passed-scoped-typed-original-LOFTEE-workflow' and p['adapter_admitted'] and p['implementation_credit'] and p['admitted_named_tools']==['loftee'] and not p['cache_execution_validated'] and not p['catalog_changed'] and not p['whole_paper_reproduced'] and not p['whole_goal_complete'];assert pin(folder/'controls.zip')==p['archive'] and pin(folder/'WARNINGS.md')==p['warnings'];files=unpack(folder/'controls.zip',p)
    for line in (folder/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        sha,name=line.split('  ',1);assert name in ['controls.zip','WARNINGS.md','VALIDATION.json','INSTALL-LOCK.json'] and pin(folder/name)['sha256']==sha
    for name,value in p['source_pins'].items():assert pin(ROOT/name)==value and byte_pin(files['current-source/'+name])==value
    for name,value in p['parent_proofs'].items():assert pin(ROOT/name)==value
    get=lambda name:json.loads(files[name]);reports={k:get(v+'/COMPLETED.json') for k,v in p['final_attempts'].items()};external=get('EXTERNAL-TEST-LIBRARIES.json');assert len(external)==9 and all(n.startswith('build/literature-review/shapeit-runtime/owned-common-native-matrix-4/lib/') and n.endswith('.jar') for n in external)
    for key,report in reports.items():
        assert report['status'].startswith(('passed-','PASS_','compiled-')),key
        for name,value in report.get('source_pins',{}).items():
            if name in external:
                assert value==external[name]
                if (ROOT/name).exists():assert pin(ROOT/name)==value
            else:assert name in p['source_pins'] and value==p['source_pins'][name] and byte_pin(files['current-source/'+name])==value,(key,name)
    build=reports['build'];assert build['artifact']==p['artifact'] and build['parent_artifact']==p['parent_artifact'];assert all(reports[k]['artifact']==p['artifact'] for k in ['native','compressed','forked','late','fast','supervision','example']);native=reports['native'];assert [r['tests'] for r in native['JVMs']]==p['native_checks_by_JVM']==[39,39,45] and all(r['exit_code']==r['skipped']==0 for r in native['JVMs']);assert all(r['tests']==8 and r['exit_code']==r['skipped']==0 for r in reports['comparator']['platforms']) and len(reports['comparator']['platforms'])==2
    default=reports['default'];assert default['artifact']==p['artifact'] and len(default['JVMs'])==2 and all(r['discovered']==45 and r['passed']==39 and r['explicit_Linux_only_skipped']==6 and r['failed']==r['aborted']==r['exit_code']==0 for r in default['JVMs']);previous='build/literature-review/loftee-runtime/workflow-proof-before-platform-guards-1/';evolution=get(previous+'TEST-PLATFORM-EVOLUTION.json');assert byte_pin(files[previous+'TEST-PLATFORM-EVOLUTION.json'])==p['test_platform_evolution_receipt'] and byte_pin(files[previous+'VALIDATION.json'])==p['prior_sealed_proof'];before=files[previous+'source-before/current-source/'+evolution['path']];after=files['current-source/'+evolution['path']];assert byte_pin(before)==evolution['before'] and byte_pin(after)==evolution['after'] and after.replace(evolution['annotation_line'].encode('ascii'),b'')==before and get(previous+'VALIDATION.json')['artifact']==p['artifact'] and not evolution['scientific_code_or_inputs_outputs_changed']
    comparisons=0;variants=0;annotations=0
    for key,count in [('compressed',8),('forked',6),('example',2)]:
        root=p['final_attempts'][key]+'/';analysis=root+('example/analysis/' if key=='example' else 'analysis/');report=reports[key];assert len(report['comparisons'])==count and get(analysis+'analysis-status.json')['result_admitted'];manifest=get(analysis+'manifest.json');assert manifest['result_admitted'] and not manifest['runtime_environment_fallback'] and not manifest['runtime_installs_software']
        for name,value in manifest['outputs'].items():assert byte_pin(files[analysis+name])==value
        for case in report['comparisons']:
            identifier=case['case'];native_prefix=analysis+'worker/cases/'+identifier+'/';original=root+'direct-'+identifier+'/original.vcf';assert byte_pin(files[original])==case['original_vcf'] and byte_pin(files[native_prefix+'original.vcf'])==case['typed_vcf'];q=get(root+('example/request.json' if key=='example' else 'request.json'));selected=next(c for c in q['analyses'] if c['id']==identifier);actual=compare_tables(archived_table(files[native_prefix+'original.vcf']),archived_table(files[original]),bool(selected['settings'].get('run_splice_predictions')));assert actual==case['complete_source_record_comparison'];audit=get(native_prefix+'native-audit.json');assert audit==case['native_audit'] and audit['variants']==15 and audit['annotations']==80
            for name in ['original.vcf','native-variants.tsv','native-annotations.tsv','native-audit.json']:assert files[analysis+'results/'+identifier+'/'+name]==files[native_prefix+name]
            ledger=files[native_prefix+'native-annotations.tsv'].decode('utf8')
            if selected['ancestral_check']:assert 'ANC_ALLELE' in ledger
            if selected['conservation_check']:assert 'PHYLOCSF_UNLIKELY_ORF' in ledger and 'PHYLOCSF_WEAK' in ledger
            comparisons+=1;variants+=audit['variants'];annotations+=audit['annotations']
    assert comparisons==p['complete_original_case_comparisons']==16 and variants==240 and annotations==1280
    protocols=[*reports['fast']['protocols'],*reports['supervision']['protocols']];assert len(protocols)==18 and len(reports['fast']['protocols'])==16 and len(reports['supervision']['protocols'])==2
    for key in ['fast','supervision']:
        for protocol in reports[key]['protocols']:
            assert protocol['expected_exit']==protocol['observed_exit'] in [2,3] and not protocol['result_admitted'] and protocol['warning_actions_present'];root=p['final_attempts'][key]+'/'+protocol['name']+'/';analysis=root+('unsafe parent/analysis/' if protocol['name']=='unsafe-output-parent' else 'analysis/');assert not any(n.startswith(analysis+'results/') for n in files)
            if protocol['name']=='invalid-field':assert not any(n.startswith(analysis) for n in files)
            else:assert not get(analysis+'analysis-status.json')['result_admitted'] and analysis+'WARNINGS.md' in files
    late=p['final_attempts']['late']+'/analysis/';assert reports['late']['typed_exit_code']==2 and reports['late']['earlier_success_retained_but_unadmitted'] and not get(late+'analysis-status.json')['result_admitted'] and late+'worker/cases/early-success/native-audit.json' in files and not any(n.startswith(late+'results/') for n in files);assert p['expected_no_result_analysis_protocols']==19
    for key in ['compressed','forked','late','fast','supervision','example']:
        prefix=p['final_attempts'][key]+'/'
        for name in files:
            if name.startswith(prefix) and name.endswith('/process-receipt.json'):assert all(not row['alive_after_cleanup'] for row in get(name)['owned_process_cleanup'])
    assert len(reports['preparation']['protocols'])==p['expected_no_result_preparation_protocols']==3 and all(r['exit_code']==3 and not r['download_or_analysis_started'] and r['prominent_warning_and_required_action_present'] for r in reports['preparation']['protocols'])
    profile_path='src/main/resources/org/jlinalg/omics/loftee-workflow/SOURCE-PROFILE.json';profile=get('current-source/'+profile_path);refinement=p['final_attempts']['refinement']+'/';before=get(refinement+'SOURCE-PROFILE-before.json');excluded=profile['environment']['explicitly_unreachable_legacy_files']['files'];assert len(excluded)==p['excluded_unreachable_legacy_files']==8 and all(n.startswith('lib/perl5/site_perl/5.22.0/') for n in excluded);expected=copy.deepcopy(before);expected['environment']['files']={n:v for n,v in before['environment']['files'].items() if n not in excluded};expected['environment']['execution_closure_files']=len(expected['environment']['files']);expected['environment']['execution_closure_bytes']=sum(v.get('bytes',0) for v in expected['environment']['files'].values());expected['environment']['explicitly_unreachable_legacy_files']=profile['environment']['explicitly_unreachable_legacy_files'];assert expected==profile and len(profile['environment']['files'])==p['runtime_closure_entries']==4840;assert get(refinement+'include-contract.stdout')['legacy_RNA_import_unavailable'] and get(refinement+'include-contract.stdout')['perl']=='v5.32.1'
    lock=get('current-source/docs/literature/loftee-workflow-development/INSTALL-LOCK.json');old_lock=get(refinement+'INSTALL-LOCK-before.json');assert lock['source_profile']==byte_pin(files['current-source/'+profile_path]) and old_lock['source_profile']==byte_pin(files[refinement+'SOURCE-PROFILE-before.json']);assert {k:v for k,v in lock.items() if k!='source_profile'}=={k:v for k,v in old_lock.items() if k!='source_profile'} and len(lock['packages'])==p['locked_packages']==224
    restore=reports['restoration'];assert restore['status']=='PASS_EXACT_LOCKED_ENVIRONMENT_AND_COMPACT_RUNTIME_PREPARED' and restore['installed_packages']==224 and not restore['analysis_executed'] and restore['existing_locked_environment_used_read_only'];assert get('build/lft-restore-1/COMPLETED.json')['status']=='SKIP3_DEPENDENCY_UNAVAILABLE' and get('build/lft-restore-1/runtime/COMPLETED.json')['status']=='SKIP3_DEPENDENCY_UNAVAILABLE';assert get('build/lft-prefix-probe-1/COMPLETED.json')['status']=='FAIL2_PREFIX_RESTORATION_PROBE';assert get('build/lft-restore-1/environment-install.process.json')['exit_code']==0 and get('build/lft-restore-1/EXPORT-DIAGNOSTICS.json')[0]['name'].endswith('/RNA/RNA.so')
    old=get('artifact-parent-entries.json');new=get('artifact-candidate-entries.json');assert len(old)==p['unchanged_parent_entries'] and all(new[n]==v for n,v in old.items()) and set(new)-set(old)==set(p['added_entries']);assert all(byte_pin(files['candidate-owned-entry/'+name])==new[name] for name in p['added_entries'])
    for key,entries in [('parent_artifact',old),('artifact',new)]:
        path=ROOT/p[key]['path']
        if path.exists():
            assert pin(path)=={k:p[key][k] for k in ['bytes','sha256']}
            with zipfile.ZipFile(path) as archive:assert len(archive.namelist())==len(set(archive.namelist())) and {n:byte_pin(archive.read(n)) for n in archive.namelist()}==entries
    omissions=get('EXPLICIT-OMISSIONS.json');assert omissions['final_synthetic_scientific_inputs_outputs_logs_complete'] and omissions['no_variant_input_or_output_dropped_for_runtime_refinement'] and all(n not in files for n in omissions['files']);counts=get('catalog-baseline/summary.json')['implementation_status_counts'];assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,63,251)
    if (ROOT/'build/cli/omicsmatic-0.3.7.jar').exists():assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==p['canonical_executable_unchanged']
    print('Verified scoped LOFTEE workflow:16 complete original comparisons,39/39/45 native checks,19 no-result analysis and3 preparation protocols; strict runtime refinement and original failures retained. Catalog and whole goal remain separate.');return p

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source/output proof checks')
    verify()
