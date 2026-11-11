# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline native HTSeq foundation audit; no Python package installation or analysis execution."""
import gzip
import json
import sys
import xml.etree.ElementTree as ET
import zipfile
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

PREFIX='build/literature-review/htseq-runtime/'


def main():
    folder=ROOT/'docs/literature/htseq-interval-development'
    proof=json.loads((folder/'VALIDATION.json').read_bytes())
    assert proof['schema_version']==1 and proof['status']=='passed-native-HTSeq-interval-assignment-foundation'
    assert pin(folder/'controls.zip')==proof['archive'] and pin(folder/'WARNINGS.md')==proof['warnings_document']
    files=unpack(folder/'controls.zip',proof)
    for name,wanted in proof['source_pins'].items():
        assert pin(ROOT/name)==wanted and byte_pin(files['current-source/'+name])==wanted,name
    def record(name):return json.loads(files[PREFIX+name])
    reference=record('interval-original-3/RESULT.json')
    assert reference['HTSeq']=='2.1.2' and len(reference['cases'])==43
    packed=files['current-source/src/test/resources/org/jlinalg/sequence/htseq-intervals/original-controls.json.gz']
    assert gzip.decompress(packed)==files[PREFIX+'interval-original-3/RESULT.json']
    steps=successes=query_failures=insertion_failures=unexecuted=modes=0
    for population in reference['cases']:
        if 'insertion_error' in population:
            assert population['insertion_error']['error_type']=='IndexError'
            insertion_failures+=1;unexecuted+=len(population['queries']);continue
        for query in population['queries']:
            if 'query_error' in query:
                assert query['query_error']['error_type']=='IndexError'
                query_failures+=1;continue
            successes+=1
            assert len(query['blocks'])==len(query['original_steps'])
            memberships=[]
            for block,original in zip(query['blocks'],query['original_steps']):
                end=int(block['start'])
                for step in original:
                    interval=step['interval']
                    assert interval['chromosome']==block['chromosome'] and int(interval['start'])==end
                    assert interval['strand']==(block['strand'] if population['stranded'] else '.')
                    end=int(interval['end']);assert end>int(interval['start'])
                    assert step['features']==sorted(set(step['features']))
                    memberships.append(set(step['features']));steps+=1
                assert end==int(block['end'])
            for mode in ['UNION','INTERSECTION_STRICT','INTERSECTION_NONEMPTY']:
                selected=memberships if mode!='INTERSECTION_NONEMPTY' else [s for s in memberships if s]
                expected=set()
                if selected:
                    expected=set.union(*selected) if mode=='UNION' else set.intersection(*selected)
                assert sorted(expected)==query['documented_mode_assignments_from_original_steps'][mode]
                modes+=1
    assert (steps,successes,modes,query_failures,insertion_failures,unexecuted)==(1214,538,1614,44,15,337)
    native=record('interval-native-matrix-1/COMPLETED.json')
    assert native['tests_per_JVM']==2224 and len(native['JVMs'])==3
    for row in native['JVMs']:
        assert row['tests']==row['passed']==2224 and row['skipped']==0
        assert record('interval-native-matrix-1/'+row['JVM']+'.process.json')['exit_code']==0
        text=files[PREFIX+'interval-native-matrix-1/'+row['JVM']+'.stdout'].decode('utf8')
        assert '2224 tests successful' in text and '0 tests failed' in text and '0 tests skipped' in text
    tests=ET.fromstring(files['normal-Gradle-tests.xml'])
    assert tests.attrib['tests']=='2224' and all(tests.attrib[k]=='0' for k in ['failures','errors','skipped'])
    entries=record('interval-native-matrix-1/parent-entries.json')
    assert not entries['changed'] and entries['parent']==proof['parent_artifact'] and entries['unchanged_existing_entries']==proof['unchanged_existing_entries']
    assert all(name.startswith(('org/jlinalg/sequence/HtseqFeatureAssignment','org/jlinalg/omics/htseq-intervals/')) for name in entries['added'])
    artifact=ROOT/proof['artifact']['path']
    if artifact.exists():
        assert pin(artifact)=={k:proof['artifact'][k] for k in ['bytes','sha256']}
        with zipfile.ZipFile(artifact) as candidate:
            for name in entries['added']:
                if name.endswith('/'):assert candidate.getinfo(name).is_dir() and candidate.read(name)==b''
                else:assert candidate.read(name)==files['candidate-owned-entries/'+name]
    parent=ROOT/'docs/literature/signac-tools-development'
    assert pin(parent/'VALIDATION.json')==proof['parent_public_Signac_validation']
    assert pin(parent/'controls.zip')==proof['parent_public_Signac_archive']
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar'
    if canonical.exists():assert pin(canonical)==proof['canonical_executable_preserved']
    example=record('interval-example-1/COMPLETED.json')
    assert example['JVMs']==3 and example['assignment_outcomes_per_JVM']==6 and example['refusal_controls_per_JVM']==2
    for platform in ['windows-java21','windows-java25','linux-java21']:
        assert record('interval-example-1/'+platform+'.process.json')['exit_code']==0
    census=json.loads(files['primary-source-census.json'])
    assert (census['papers_scanned'],census['source_papers'],census['catalogued_papers'])==(250,8,7)
    assert {r['paper_id'] for r in census['records']}=={'P005','P020','P044','P099','P113','P121','P148','P195'}
    for name in ['reference-setup-1/attempted-setup.py','reference-setup-1/FAILURE.json',
        'reference-inventory-recovery-1/attempted-inventory-source.py','interval-original-1/attempted-collector.py',
        'interval-original-2/collector.py','interval-original-2/collector.stderr']:
        assert PREFIX+name in files,name
    recovered=record('reference-inventory-recovery-2/COMPLETED.json')
    assert recovered['reused_already_installed_runtime'] and recovered['old_failure_preserved']
    assert not recovered['dependency_installation_repeated'] and not recovered['shared_or_system_python_modified']
    assert (recovered['runtime_files'],recovered['runtime_symlinks'])==(1915,4)
    assert proof['catalog_counts']==dict(new_scoped_integrations=71,including_existing=74,missing=257)
    for key in ['original_htseq_count_SAM_GTF_run','HTSeq_root_dispatch_available','full_mutable_HTSeq_API',
        'historical_whole_packages','execution_authentication','biological_validity_certified','whole_paper_reproduced',
        'full_project_test_suite_run','catalog_implementation_credit','released','pushed','deployed','whole_goal_complete']:
        assert proof[key] is False,key
    assert proof['Cell_Ranger_excluded'] and proof['all_parent_entries_preserved']
    print(json.dumps(dict(status='passed-offline-native-HTSeq-interval-development',tests_per_JVM=2224,JVMs=3,
        original_spans=steps,successful_queries=successes,mode_resolutions=modes,source_query_refusals=query_failures,
        source_insertion_refusals=insertion_failures,unexecuted_candidates=unexecuted,source_pins=len(proof['source_pins']),
        logical_files=proof['logical_files'],catalog_implementation_credit=False)))


if __name__=='__main__':
    try:
        if not __debug__:raise RuntimeError('Never disable evidence/source checks')
        main()
    except Exception as failure:
        print('HTSEQ INTERVAL EVIDENCE FAIL2: '+str(failure)+'\nRequired action: preserve the checkpoint, correct the named source/archive/capture mismatch and repeat verification. No partial proof or whole-tool credit is admitted.',file=sys.stderr)
        raise SystemExit(2)
