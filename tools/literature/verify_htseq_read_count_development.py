# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline audit of real original counts, native replay, retained failures and eight primary roles."""
import gzip
import json
import sys
import xml.etree.ElementTree as ET
import zipfile
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

PREFIX='build/literature-review/htseq-runtime/'


def main():
    folder=ROOT/'docs/literature/htseq-read-count-development'
    proof=json.loads((folder/'VALIDATION.json').read_bytes())
    assert proof['schema_version']==1 and proof['status']=='passed-native-HTSeq-prepared-read-counting-and-five-original-references'
    assert pin(folder/'controls.zip')==proof['archive'] and pin(folder/'WARNINGS.md')==proof['warnings_document']
    files=unpack(folder/'controls.zip',proof)
    for name,wanted in proof['source_pins'].items():
        assert pin(ROOT/name)==wanted and byte_pin(files['current-source/'+name])==wanted,name
    def record(name):return json.loads(files[PREFIX+name])
    def rows(name):return [line.split('\t') for line in files[PREFIX+name].decode('utf8').splitlines()]
    calls=successes=failures=0;by_version={}
    for name in ['count-original-1','count-fraction-original-1','historical-py27-counts-2','historical-py39-counts-1']:
        capture=record(name+'/COMPLETED.json')
        for case in capture['cases']:
            version=case.get('version','2.1.2')
            sub=version+'-'+case['id'] if 'version' in case else case.get('source_folder',case['id'])
            base=name+'/'+sub+'/'
            proc=record(base+('process.json' if 'version' in case else 'original-count.process.json'))
            code=case.get('exit_code',0)
            assert proc['exit_code']==code
            totals=by_version.setdefault(version,[0,0,0]);totals[0]+=1;calls+=1
            if code==0:
                source=rows(base+'original-count.stdout');expected=case['counts']
                assert source==expected['genes']+expected['summary'] and len(expected['summary'])==5
                assert record(base+'source-count-table.json')==expected
                assert all(len(row)==2 for row in source)
                assert all(float(row[1])>=0 for row in source)
                totals[1]+=1;successes+=1
            else:
                assert case['counts'] is None and files[PREFIX+base+'original-count.stderr']
                totals[2]+=1;failures+=1
    assert (calls,successes,failures)==(185,171,14)
    assert by_version=={'2.1.2':[44,42,2],'0.5.3':[28,24,4],'0.6.0':[31,27,4],'0.11.1':[39,37,2],'0.13.5':[43,41,2]}
    invalid=[name for name in files if name.startswith(PREFIX+'historical-py27-counts-1/') and name.endswith('/process.json')]
    assert len(invalid)==59 and all(json.loads(files[name])['exit_code']!=0 for name in invalid)
    assert PREFIX+'historical-py27-counts-1/attempted-collector.py' in files
    assert record('historical-py39-setup-1/PARTIAL.json')
    assert PREFIX+'count-reference-binding-1/attempted-binder.py' in files
    binding=record('count-reference-binding-2/COMPLETED.json');module_count=0
    assert len(binding['profiles'])==5
    for profile in binding['profiles']:
        version=profile['version'];inventory=record('count-reference-binding-2/'+version+'/RUNTIME-FILES.json')
        assert byte_pin(files[PREFIX+'count-reference-binding-2/'+version+'/RUNTIME-FILES.json'])==profile['runtime_inventory']
        assert inventory['bytecode_included'] and len(inventory['files'])==profile['runtime_files']
        assert profile['inventory_reused_from_prior_capture']==(version!='2.1.2')
        assert profile['source_archive_modules_not_installed']==(['HTSeq/scripts/count_old.py'] if version=='2.1.2' else [])
        for row in profile['scientific_source_bindings']:
            body=files['original-scientific-sources/'+version+'/'+row['installed_path']]
            wanted={k:row[k] for k in ['bytes','sha256']};assert byte_pin(body)==wanted
            assert any(value==wanted for name,value in inventory['files'].items() if name.endswith('/site-packages/'+row['installed_path']))
            module_count+=1
    assert module_count==37
    fixture=json.loads(gzip.decompress(files['current-source/src/test/resources/org/jlinalg/sequence/htseq-read-count/original-counts.json.gz']))
    assert fixture['HTSeq']=='2.1.2' and len(fixture['cases'])==40
    assert sum(len(c['counts']['genes']) for c in fixture['cases'])==198
    assert sum(len(c['counts']['summary']) for c in fixture['cases'])==200
    assert sum(len(c['prepared']['units']) for c in fixture['cases'])==627
    for case in fixture['cases']:
        source='count-fraction-original-1' if case['id'].startswith('three-feature-') else 'count-original-1'
        capture=record(source+'/COMPLETED.json');original=next(row for row in capture['cases'] if row['id']==case['id'])
        sub=original.get('source_folder',case['id']);base=source+'/'+sub+'/'
        assert original['counts']==case['counts'] and record(base+'prepared-units.json')==case['prepared']
        assert byte_pin(files[PREFIX+base+'original-count.stdout'])==case['source_count_stdout']
    native=record('read-count-native-matrix-1/COMPLETED.json');assert native['artifact']=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert native['tests_per_JVM']==2315 and len(native['JVMs'])==3
    for row in native['JVMs']:
        assert row['tests']==row['passed']==2315 and row['skipped']==0
        assert record('read-count-native-matrix-1/'+row['JVM']+'.process.json')['exit_code']==0
        stdout=files[PREFIX+'read-count-native-matrix-1/'+row['JVM']+'.stdout'].decode('utf8')
        assert '2315 tests successful' in stdout and '0 tests failed' in stdout and '0 tests skipped' in stdout
    for name,count in [('HtseqReadCounterTest',91),('HtseqFeatureAssignmentTest',2224)]:
        tests=ET.fromstring(files['normal-Gradle-'+name+'.xml'])
        assert int(tests.attrib['tests'])==count and all(tests.attrib[k]=='0' for k in ['failures','errors','skipped'])
    entries=record('read-count-native-matrix-1/parent-entries.json');assert not entries['changed']
    assert entries['parent']==proof['parent_artifact'] and entries['unchanged_existing_entries']==proof['unchanged_existing_entries']
    assert all(name.startswith(('org/jlinalg/sequence/HtseqReadCounter','org/jlinalg/omics/htseq-read-count/')) for name in entries['added'])
    artifact=ROOT/proof['artifact']['path']
    if artifact.exists():
        assert pin(artifact)==native['artifact']
        with zipfile.ZipFile(artifact) as archive:
            for name in entries['added']:
                if name.endswith('/'):assert archive.getinfo(name).is_dir() and archive.read(name)==b''
                else:assert archive.read(name)==files['candidate-owned-entries/'+name]
    parent=ROOT/'docs/literature/htseq-interval-development'
    assert pin(parent/'VALIDATION.json')==proof['parent_interval_validation'] and pin(parent/'controls.zip')==proof['parent_interval_archive']
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar'
    if canonical.exists():assert pin(canonical)==proof['canonical_executable_preserved']
    example=record('read-count-example-1/COMPLETED.json');assert example['artifact']==native['artifact'] and example['JVMs']==3
    for platform in ['windows-java21','windows-java25','linux-java21']:
        assert record('read-count-example-1/'+platform+'.process.json')['exit_code']==0
    old=json.loads(gzip.decompress(files[PREFIX+'role-curation-applied-1/before-papers.json.gz']))
    current=json.loads(files['catalog-snapshot/papers.json']);assert len(old)==len(current)==250
    changed=[]
    for a,b in zip(old,current):
        assert {k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'}
        assert [t for t in a['tools'] if t['tool_id']!='htseq']==[t for t in b['tools'] if t['tool_id']!='htseq']
        if a!=b:changed.append(b['paper_id'])
    assert changed==['P005','P020','P044','P099','P113','P121','P148','P195']
    old_reviews=json.loads(gzip.decompress(files[PREFIX+'role-curation-applied-1/before-reviewed-uses.json.gz']))['reviews']
    reviews=json.loads(files['catalog-snapshot/reviewed-uses.json'])['reviews']
    assert len(old_reviews)==890 and len(reviews)==898 and reviews[:890]==old_reviews and all(r['tool_id']=='htseq' for r in reviews[890:])
    summary=json.loads(files['catalog-snapshot/summary.json']);assert summary['paper_tool_observations']==2446 and summary['implementation_status_counts']['missing']==257
    general=record('role-ui-2/general/ui-validation.json');role_ui=record('role-ui-2/roles/htseq-ui-verification.json')
    assert general['status']==role_ui['status']=='passed' and len(general['checks'])==proof['general_browser_checks']==299
    assert role_ui['primary_roles']==8 and len(role_ui['checks'])==proof['role_browser_checks']==4 and role_ui['mobile_layout']==dict(viewport=390,document=390)
    assert role_ui['horizontal_table_scroll']['scrolled']>0
    for key in ['independent_native_SAM_GTF_parser','independent_native_pairing','native_random_assignment','HTSeq_root_dispatch_available',
        'historical_whole_packages','execution_authentication','biological_validity_certified','whole_paper_reproduced','full_project_test_suite_run',
        'catalog_implementation_credit','released','pushed','deployed','whole_goal_complete']:
        assert proof[key] is False,key
    assert proof['Cell_Ranger_excluded'] and proof['all_parent_entries_preserved'] and proof['original_SAM_GTF_count_executed']
    print(json.dumps(dict(status='passed-offline-native-HTSeq-read-count-development',tests_per_JVM=2315,JVMs=3,
        original_calls=calls,original_successes=successes,retained_source_failures=failures,excluded_transport_calls=59,
        unchanged_scientific_modules=module_count,reviewed_roles=8,source_pins=len(proof['source_pins']),logical_files=proof['logical_files'],catalog_implementation_credit=False)))


if __name__=='__main__':
    try:
        if not __debug__:raise RuntimeError('Never disable source/evidence checks')
        main()
    except Exception as failure:
        print('HTSEQ READ COUNT EVIDENCE FAIL2: '+str(failure)+'\nRequired action: preserve the checkpoint, correct the named source/archive/capture mismatch and repeat verification. No partial proof or whole-tool credit is admitted.',file=sys.stderr)
        raise SystemExit(2)
