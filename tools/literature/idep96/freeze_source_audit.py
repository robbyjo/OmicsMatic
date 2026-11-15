#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve static-audit findings without distributing the original legacy source."""
from pathlib import Path
import argparse,hashlib,json,zipfile

ROOT=Path(__file__).resolve().parents[3]
BASE=ROOT.parent/'literature-review/monocle-runtime'
EVIDENCE=ROOT/'docs/literature/idep96-source-assessment'
GUIDE='docs/literature/IDEP96_SOURCE_ASSESSMENT.md'
if not __debug__:raise RuntimeError('Do not disable evidence checks')

def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return rawpin(path.read_bytes())
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')

def freeze():
    current=BASE/'idep96-static-source-audit-3'
    facts=json.loads((current/'SOURCE-FACTS.json').read_bytes())
    assert (facts['bindings'],facts['selected_calls'],facts['UI_inputs'],len(facts['server_reactive_bindings']))==(306,226,119,65)
    assert facts['repository_UI_default_expressions']['limmaPval']['value_expression']=='0.1'
    assert 'maxP_limma = 0.05' in facts['server_DESeq2_function_formals']
    for name in ['detectGroups','DEG.DESeq2']:assert not facts['server_and_standalone_helpers'][name]['normalized_R_AST_equal']
    controls=BASE/'idep96-static-parser-controls-2'
    assert json.loads((controls/'TESTS.json').read_bytes())==dict(tests=8,failures=0,errors=0,skipped=0)
    EVIDENCE.mkdir(exist_ok=False)
    (EVIDENCE/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    private={};public={}
    def add_private(folder):
        for path in folder.rglob('*'):
            if path.is_file():private[path.relative_to(BASE).as_posix()]=pin(path)
    for number in [1,2,3]:
        folder=BASE/('idep96-static-source-audit-'+str(number));add_private(folder)
        for name in ['source_audit.py','Audit.R','SOURCE-PROFILE.json','SOURCE-FACTS.json','COMMAND.json','PROCESS.json','LIVE.json','MANIFEST.json','COMPLETED.json','WARNINGS.json','process.stdout','process.stderr','ast/RETURN.txt','ast/SESSION.txt']:
            public['static-audit-'+str(number)+'/'+name]=folder/name
    for number in [1,2]:
        folder=BASE/('idep96-static-parser-controls-'+str(number));add_private(folder)
        # These are complete project-owned synthetic controls; no upstream source appears in them.
        for path in folder.rglob('*'):
            if path.is_file():public['synthetic-controls-'+str(number)+'/'+path.relative_to(folder).as_posix()]=path
    source=BASE/'idep-source-assessment-20261115-1';add_private(source)
    for name in ['DISCOVERY.json','SOURCE-READBACK.json','ASSESSMENT.json','software-paper-source.json']:
        public['intake/'+name]=source/name
    for name in ['idep96-static-source-audit-1.log','idep96-static-source-audit-2.log','idep96-static-source-audit-3.log','idep96-static-parser-controls-1.log','idep96-static-parser-controls-2.log']:
        public['logs/'+name]=ROOT/'build'/name
    index={}
    with zipfile.ZipFile(EVIDENCE/'complete-public-assessment-records.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,path in sorted(public.items()):
            raw=path.read_bytes();archive.writestr(name,raw);index[name]=rawpin(raw)
    write(EVIDENCE/'PUBLIC-RECORDS.json',index);write(EVIDENCE/'PRIVATE-RECORDS.json',private)
    names=[GUIDE]+['tools/literature/idep96/'+name for name in ['Audit.R','source_audit.py','test_source_audit.py','freeze_source_audit.py']]
    proof=dict(status='PASS_IDEP96_STATIC_SOURCE_DIVERGENCE_AUDIT_AND_NONEXECUTION_CONTROLS_ONLY',
        source_files={name:pin(ROOT/name) for name in names},
        evidence_files={path.name:pin(path) for path in EVIDENCE.iterdir() if path.is_file()},
        selected_commit=facts['source_commit'],selected_bindings=306,selected_calls=226,UI_input_definitions=119,server_reactive_assignments=65,
        actual_static_parser_tests=8,Windows_R_version='4.6.1',server_UI_literal='iDEP.96',server_code_literal='iDEP 0.96',
        UI_FDR_expression='0.1',function_FDR_default_expression='0.05',server_standalone_grouping_and_DESeq2_functions_differ=True,
        original_source_or_scientific_expressions_evaluated=False,original_source_distributed=False,
        package_installation_or_network_by_parser=False,private_files=len(private),runtime_analysis_qualified=False,
        legacy_source_license_established=False,author_snapshot_established=False,catalog_credit_added=0,
        native_integration_credit_added=0,whole_paper_reproduced=False,whole_goal_complete=False)
    write(EVIDENCE/'QUALIFICATION.json',proof)

def verify(private_base=None):
    proof=json.loads((EVIDENCE/'QUALIFICATION.json').read_bytes())
    assert proof['status']=='PASS_IDEP96_STATIC_SOURCE_DIVERGENCE_AUDIT_AND_NONEXECUTION_CONTROLS_ONLY'
    for kind,base in [('source_files',ROOT),('evidence_files',EVIDENCE)]:
        for name,identity in proof[kind].items():assert pin(base/name)==identity,(kind,name)
    with zipfile.ZipFile(EVIDENCE/'complete-public-assessment-records.zip') as archive:
        index=json.loads((EVIDENCE/'PUBLIC-RECORDS.json').read_bytes())
        assert len(archive.namelist())==len(set(archive.namelist()))==len(index) and set(archive.namelist())==set(index)
        for name,identity in index.items():assert rawpin(archive.read(name))==identity,name
        facts=json.loads(archive.read('static-audit-3/SOURCE-FACTS.json'))
        assert facts['source_commit']==proof['selected_commit'] and not facts['original_app_or_scientific_expressions_evaluated']
        assert facts['repository_UI_default_expressions']['limmaPval']['value_expression']=='0.1'
        assert 'maxP_limma = 0.05' in facts['server_DESeq2_function_formals']
        assert len(facts['server_reactive_bindings'])==65 and facts['implementation_credit_added']==0
        assert json.loads(archive.read('synthetic-controls-2/TESTS.json'))==dict(tests=8,failures=0,errors=0,skipped=0)
        for number in [1,2,3]:assert json.loads(archive.read('static-audit-'+str(number)+'/PROCESS.json'))['exit_code']==0
    checked=0
    if private_base:
        index=json.loads((EVIDENCE/'PRIVATE-RECORDS.json').read_bytes())
        for name,identity in index.items():assert pin(private_base/name)==identity,name
        checked=len(index);assert checked==proof['private_files']
    return dict(status=proof['status'],actual_static_parser_tests=8,original_source_or_scientific_expressions_evaluated=False,
        complete_private_files_checked=checked,public_only=private_base is None,catalog_credit_added=0,whole_goal_complete=False)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--freeze',action='store_true');parser.add_argument('--private-base',type=Path);args=parser.parse_args()
    if args.freeze:freeze()
    print(json.dumps(verify(args.private_base),indent=2))
