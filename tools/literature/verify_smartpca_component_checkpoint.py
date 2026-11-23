# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify saved smartpca prepared-component evidence; does not run scientific analysis."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,io,json,re,sys,tarfile,xml.etree.ElementTree as ET,zipfile

ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/smartpca-native-development'
COMMIT='4f7e9f0bf7c85f45ad27d895e1dd7d28a86ec3d1'
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(condition,message):
    if not condition:raise ValueError(message)
def verify(root,jar=None,packet=None):
    require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O')
    root=root.resolve();folder=root/FOLDER if packet is None else packet.resolve()
    state=json.loads((folder/'STATUS.json').read_bytes())
    require(state['status']=='PASS_SMARTPCA_PREPARED_COMPONENT_NOT_WORKFLOW','Changed checkpoint status')
    require((state['component_methods'],state['original_program_calls'],state['finite_original_cases'],state['unique_normal_tests'],state['configured_followups'])==(7,12,9,1871,5),'Changed checkpoint counts')
    require(not any(state[k] for k in ('workflow_qualified','catalog_admitted','whole_tool_qualified','whole_paper_reproduced','whole_goal_complete')),'Unsupported admission claim')
    require(state['new_family_credit']==state['new_reviews']==0,'Component incorrectly granted catalog credit')
    for name,wanted in state['source_code'].items():
        path=PurePosixPath(name);require(not path.is_absolute() and '..' not in path.parts,'Unsafe source path')
        require(pin((root/name).read_bytes())==wanted,'Changed checkpoint source: '+name)
    index_raw=(folder/'EVIDENCE.json').read_bytes();archive_raw=(folder/'evidence.zip').read_bytes()
    require(pin(index_raw)==state['evidence_index'] and pin(archive_raw)==state['evidence_archive'],'Changed evidence archive/index')
    index=json.loads(index_raw)['files']
    with zipfile.ZipFile(io.BytesIO(archive_raw)) as archive:
        require(set(archive.namelist())==set(index) and len(archive.namelist())==len(index),'Changed archive member set')
        for name,wanted in index.items():require(pin(archive.read(name))==wanted,'Changed evidence member: '+name)
        raw=archive.read
        def obj(name):return json.loads(raw(name))
        first=obj('normal-build-1/INITIAL-TESTS-WITH-SKIPS.json')
        require(first['actual']==dict(tests=1871,failures=0,errors=0,skipped=5),'Changed initial normal test receipt')
        totals=dict(tests=0,failures=0,errors=0,skipped=0)
        for name in index:
            if name.startswith('normal-build-1/test-results/') and name.endswith('.xml'):
                xml=ET.fromstring(raw(name))
                for key in totals:totals[key]+=int(xml.get(key,0))
        require(totals==first['actual'],'Saved normal XML does not support test counts')
        follow=dict(tests=0,failures=0,errors=0,skipped=0)
        for name in index:
            if name.startswith('normal-build-1/configured-followup-1/test-results/') and name.endswith('.xml'):
                xml=ET.fromstring(raw(name))
                for key in follow:follow[key]+=int(xml.get(key,0))
        require(follow==dict(tests=5,failures=0,errors=0,skipped=0),'Configured follow-up incomplete')
        normal=obj('normal-build-1/RESULT.json');require(normal['unique_tests_covered']==1871 and normal['initial_skips_resolved']==5,'Changed normal aggregate')
        catalog=obj('CATALOG-PRESERVATION.json')
        require(catalog['before']==catalog['after'] and len(catalog['before'])==9 and catalog['base']=='1709bf5be17e1c6b6a5250b1086f8b01c65341fc','Component stage changed the canonical catalog')
        payloads=obj('normal-build-1/PAYLOADS.json');require(payloads['previous_payloads']==22929 and payloads['modified']==[] and len(payloads['added'])==11,'Previous producer payload changed')
        require(all(n.startswith(('org/jlinalg/genetics/SmartpcaPrepared','org/jlinalg/omics/smartpca-4f7e9f0/')) for n in payloads['added']),'Unrelated new producer payload')
        require(normal['producer']==state['producer'],'Changed exact normal producer')
        checks=obj('component-check-1/RESULT.json');require(len(checks['hosts'])==3 and all(row['compiler_exit']==row['test_exit']==0 for row in checks['hosts']),'Component compiler/test failure')
        final=obj('final-producer-check-3/COMPLETED.json');require(final['producer']==state['producer'] and len(final['profiles'])==3,'Changed final producer check')
        for row in final['profiles']:
            host=row['host'];require(row['component_methods']==7 and row['original_program_comparisons']==9 and row['loaded_native_component_from_exact_final_JAR'] and row['all_commands_exit_zero'],'Incomplete final producer profile')
            require(b'PASS: seven smartpca prepared-component methods; nine original whole-program comparisons.' in raw('final-producer-check-3/'+host+'/component-final-producer.stdout'),'Missing actual native comparisons')
            loaded=[line for line in raw('final-producer-check-3/'+host+'/classload.log').decode().splitlines() if 'org.jlinalg.genetics.SmartpcaPrepared source:' in line]
            require(len(loaded)==1 and 'normal-build-1/project-build/cli/omicsmatic-0.4.0.jar' in loaded[0].replace('\\','/'),'Wrong native class producer')
            for command in ('component-final-producer','example-javac','example-java'):require(obj('final-producer-check-3/'+host+'/'+command+'.json')['exit_code']==0,'Final producer/example failed')
        skipped=obj('setup-controls-1/COMPLETED.json');require(len(skipped['cases'])==3 and all(row['exit_code']==3 and not row['analysis_executed'] and row['required_action'] for row in skipped['cases']),'Missing actual prerequisite skip/remedy')
        setup=obj('reference-setup-records-1/COMPLETED.json');require(setup['scientific_source_files_unchanged']==159 and setup['dependency_files']==339 and setup['active_processes_in_exact_prefixes']==[],'Incomplete original setup/file lock')
        reference=obj('original-program-reference-1/COMPLETED.json');require((reference['cases'],reference['finite_native_comparisons'],reference['original_only_shrinkage_and_fst_cases'],reference['original_error_cases'])==(12,9,2,1),'Changed original witness counts')
        fixture=root/'src/test/resources/org/jlinalg/genetics/smartpca-original-reference.zip'
        require(pin(fixture.read_bytes())==reference['archive'],'Original fixtures differ')
        with zipfile.ZipFile(fixture) as fixtures:
            cases=[json.loads(fixtures.read(name)) for name in fixtures.namelist() if name.endswith('/reference.json')]
            require(len(cases)==12 and sum(c['native_comparison_required'] for c in cases)==9,'Changed original comparison population')
            require(all(c['source_commit']==COMMIT and c['binary_identity']==reference['binary'] and not c['original_source_modified'] for c in cases),'Wrong original reference producer')
            require(sum(c['exit_code']!=0 for c in cases)==1,'Original error witness lost')
    roles=json.loads((root/FOLDER/'ROLES.json').read_bytes());require(roles['papers_checked']==250 and len(roles['roles'])==6 and roles['new_reviews']==roles['new_family_credit']==0,'Changed primary roles')
    for name in ('source-roles-3/ROLES.json','source-roles-linux-2/ROLES.json'):
        require(index[name]==pin((root/FOLDER/'ROLES.json').read_bytes()),'Two-host primary audit differs')
    source_folder=root/'src/main/resources/org/jlinalg/omics/smartpca-4f7e9f0';source=json.loads((source_folder/'SOURCE.json').read_bytes())
    require(source['commit']==COMMIT and len(source['members'])==159,'Changed original source identity')
    source_raw=(source_folder/'original-source.tar.gz').read_bytes();require(pin(source_raw)==source['archive'],'Changed source archive')
    expected={m['path']:{k:m[k] for k in ('bytes','sha256')} for m in source['members']};actual={}
    with tarfile.open(fileobj=io.BytesIO(source_raw),mode='r:gz') as archive:
        for item in archive.getmembers():
            if item.isfile():actual['/'.join(PurePosixPath(item.name).parts[1:])]=pin(archive.extractfile(item).read())
    require(actual==expected,'Original archive source member differs')
    if jar is not None:require(pin(jar.read_bytes())=={k:state['producer'][k] for k in ('bytes','sha256')},'Exact component producer differs')
    return dict(status=state['status'],component_methods=7,original_program_calls=12,finite_original_cases=9,unique_normal_tests=1871,
        configured_followups=5,previous_payloads_unchanged=22929,fresh_saved_evidence_checked=True,original_analysis_reexecuted=False,
        workflow_qualified=False,catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False)
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jar',type=Path);parser.add_argument('--packet',type=Path,help='Check a saved evidence packet against this exact source checkout')
    args=parser.parse_args()
    try:print(json.dumps(verify(ROOT,args.jar,args.packet),indent=2))
    except Exception as error:raise SystemExit('FAIL2: saved smartpca component checkpoint failed: '+str(error)) from error
