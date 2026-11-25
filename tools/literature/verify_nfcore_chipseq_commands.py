# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read the packaged two-edition ChIP command evidence without rerunning science."""
from pathlib import Path
import argparse,hashlib,json,sys

def require(value,message):
    if not value:raise ValueError(message)
def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb')as f:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def closure(root,manifest):
    require(bool(manifest),'Empty source/evidence closure')
    for name,identity in manifest.items():
        p=Path(name);require(not p.is_absolute()and '..'not in p.parts and '\\'not in name and ':'not in name,'Unsafe saved closure member')
        file=root/p
        if not file.exists():raise FileNotFoundError(file)
        require(file.is_file()and file.resolve()==file.absolute()and pin(file)==identity,'Changed/invalid saved member: '+name)
def verify(root,producer=None,private=None,cache=None):
    root=Path(root).absolute();require(root.resolve()==root and Path(__file__).resolve()==root/'tools/literature/verify_nfcore_chipseq_commands.py','Run the exact reader from its physical checkpoint source context');folder=root/'docs/literature/nfcore-chipseq-command-checkpoint';state=read(folder/'STATUS.json')
    require(state['status']=='PASS_PACKAGED_CHIPSEQ121_AND_CHIPSEQ200_ROUTES'and state['new_family_credit']==0 and not state['nfcore_catalog_admitted']and not state['whole_goal_complete'],'Expanded integration/family claim')
    require(pin(folder/'FILES.json')==state['public_manifest'],'Changed public manifest');files=read(folder/'FILES.json');closure(root,files);closure(root,state['canonical_unchanged'])
    proof=read(folder/'VALIDATION.json');require(proof['Java_tests']==dict(windows=19,linux=19),'Missing new-route/existing-route Java checks')
    require(proof['whole_jar_compatibility']['unchanged_entries']==24593 and len(proof['whole_jar_compatibility']['changed_entries'])==3 and proof['unchanged_nfcore_scientific_classes']==129,'Scientific implementation or packaged payload unexpectedly changed')
    for platform in('windows','linux'):
        cli=proof['cli'][platform];require(cli['status']=='PASS_PACKAGED_CHIPSEQ_COMMAND_ROUTES'and len(cli['calls'])==20 and all(r['exit_code']==r['expected']for r in cli['calls']),'Incomplete actual packaged calls/refusals')
        require([(r['edition'],r['complete_files_checked'])for r in cli['legacy_results']]==[('1.2.1',1212),('2.0.0',2408)]and all(r['original_capture_unchanged']and not r['scientific_models_refit']for r in cli['legacy_results']),'Incomplete historical portable result verification')
    for edition,cached,components in(('121',95,5),('200',213,4)):
        row=proof['new_original_workflows'][edition]
        require(row['resume_trace']=={'CACHED':cached,'COMPLETED':2},'Incomplete warm-resume source-task population')
        if edition=='200':
            require(row['cold_trace']=={'COMPLETED':215},'Incomplete 2.0.0 cold source-task population')
        else:
            require(row['cold_trace']=={'COMPLETED':82,'FAILED':1}and row['cold_exit_code']==2 and row['cold_failure']=='PLOTPROFILE exceeded configured 2h task limit','Missing retained 1.2.1 timeout')
            require(row['recovery_trace']=={'CACHED':81,'COMPLETED':16}and row['recovery_exit_code']==0 and row['unchanged_request_inputs_and_limits'],'Incomplete unchanged-cache recovery; a failure must not be relabelled as cold success')
        require(row['source_components']==components and row['normal_packaged_entrypoint_used']and row['fresh_result_verified_windows']and row['fresh_result_verified_linux'],'Missing original source-output or portable result qualification')
        require(not row['whole_paper_reproduced'],'Expanded paper reproduction claim')
    require(proof['new_original_workflows']['121']['explicit_synthetic_PCR_fixture'],'Missing explicit positive-fixture provenance')
    negative=proof['shallow_fixture_refusal'];require(negative['adapter_exit_code']==2 and negative['original_engine_exit_code']==0 and negative['trace_statuses']=={'COMPLETED':95,'FAILED':2}and negative['no_adapter_result_admitted']and negative['original_case_preserved']and not negative['biological_negative_finding'],'A shallow source failure was hidden or converted into success')
    if producer is not None:require(pin(producer)==state['producer'],'Restore exact complete producer; never refresh old hashes')
    checked=0
    if private is not None:
        require(producer is not None,'--private-root requires --producer');private=Path(private).absolute();manifest=private/'PRIVATE-CLOSURE.json';require(pin(manifest)==state['private_manifest'],'Changed private evidence closure');members=read(manifest);closure(private,members);checked=len(members)
    fresh=0
    if cache is not None:
        sys.path.insert(0,str(root/'tools/literature'));from nfcore_workflows import primary
        require(primary.ROOT.resolve()==root.resolve(),'Use the physical checkpoint source context, without imported current-tree fallbacks')
        result=primary.audit((root/'docs/literature/omics-2023-2026/papers.json').read_bytes(),cache,root);require(result==proof['primary_audit'],'Changed 250-paper primary coverage');fresh=250
    return dict(status='PASS_PACKAGED_CHIPSEQ_COMMAND_CHECKPOINT',public_files=len(files),private_files_checked=checked,primary_XML_checked_now=fresh,new_family_credit=0,nfcore_catalog_admitted=False,whole_goal_complete=False)
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2]);p.add_argument('--producer',type=Path);p.add_argument('--private-root',type=Path);p.add_argument('--cache',type=Path);a=p.parse_args()
    try:print(json.dumps(verify(a.root,a.producer,a.private_root,a.cache),indent=2));return 0
    except(FileNotFoundError,PermissionError)as e:print('SKIP3: dependent checkpoint verification skipped. '+str(e)+'. Restore exact permitted prerequisites or retain the skip.',file=sys.stderr);return 3
    except Exception as e:print('FAIL2: no complete packaged-command checkpoint. '+repr(e)+'. Preserve evidence and correct the mismatch; never refresh old hashes.',file=sys.stderr);return 2
if __name__=='__main__':sys.exit(main())
