"""Verify saved Genomic SEM foundation evidence; no statistical refit or catalog admission."""
from pathlib import Path
import argparse,json,sys
from genomicsem.primary import audit
from genomicsem.runtime import HERE,pin,profile,require,source_inventory
from qualify_genomicsem_runtime import inspect

FOLDER='docs/literature/genomicsem-foundation-checkpoint'

def verify(root,cache=None,runtime=None,qualification=None):
    root=Path(root).absolute();d=root/FOLDER
    require(HERE.resolve()==(root/'tools/literature/genomicsem').resolve(),'Run the reader from the selected physical source context')
    files=json.loads((d/'FILES.json').read_bytes())
    require(files and all(not Path(n).is_absolute() and '..' not in Path(n).parts and '\\' not in n and ':' not in n for n in files),'Invalid checkpoint file manifest')
    for n,expected in files.items():require(pin(root/n)==expected,'Changed public foundation file: '+n)
    status=json.loads((d/'STATUS.json').read_bytes());public=json.loads((d/'QUALIFICATION.json').read_bytes());refusals=json.loads((d/'REFUSALS.json').read_bytes());tests=json.loads((d/'TESTS.json').read_bytes())
    require(status['status']=='UPSTREAM_FOUNDATION_QUALIFIED_JAVA_ADAPTER_NOT_IMPLEMENTED' and not status['catalog_admitted'] and not status['java_adapter_implemented'],'Changed scope/admission statement')
    require(status['scoped_families']==159 and status['native']==30 and status['upstream']==129 and status['reviewed_uses']==1760 and status['pending_uses']==964,'Changed unchanged catalog totals')
    require(public['cases']==13 and public['numeric_cells_checked']==162 and public['original_namespace_functions']==44 and len(public['case_statuses'])==13 and all(v=='PASS' for v in public['case_statuses'].values()),'Incomplete declared runtime qualification')
    require(len(refusals['cases'])==10 and all(c['passed'] and c['actual_exit']==c['expected_exit'] for c in refusals['cases']),'Incomplete CLI refusal evidence')
    require(tests==dict(Windows=dict(tests=8,passed=True),Linux=dict(tests=8,passed=True)),'Changed boundary test evidence')
    profile();primary=None;runtime_files=None;qualification_files=None
    if cache is not None:primary=audit(root,cache)
    if runtime is not None:
        _,_,r=inspect(runtime);runtime_files=len(r['library_files'])
    if qualification is not None:
        q=Path(qualification);v=json.loads((q/'QUALIFIED.json').read_bytes());results=json.loads((q/'original/RESULT.json').read_bytes())
        require(v['status']=='PASS_SCOPED_ORIGINAL_RUNTIME_FOUNDATION' and not v['java_adapter_implemented'] and not v['catalog_admitted'],'Unqualified/private expanded scope')
        require(source_inventory(q/'original')==v['outputs'] and pin(q/'qualify.R')==v['worker']==pin(root/'tools/literature/genomicsem/qualify.R'),'Changed retained qualification source/output closure')
        require(pin(q/'runtime-binding.json')==v['runtime_binding'],'Changed original runtime binding')
        require(results['status']=='PASS_DECLARED_SYNTHETIC_UPSTREAM_CASES' and {k:c['status'] for k,c in results['cases'].items()}==public['case_statuses'] and results['numeric_cells_checked']==public['numeric_cells_checked'] and results['original_namespace_functions']==44,'Private/public qualification differs')
        qualification_files=len(v['outputs'])
    return dict(status='PASS_SAVED_GENOMICSEM_FOUNDATION',public_files=len(files),primary_source_audit=primary,runtime_library_files=runtime_files,retained_qualification_files=qualification_files,no_statistical_refit_performed=True,java_adapter_implemented=False,catalog_admitted=False,new_family_credit=0,new_review_credit=0,whole_goal_complete=False)

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2]);p.add_argument('--cache',type=Path);p.add_argument('--runtime',type=Path);p.add_argument('--qualification',type=Path)
    a=p.parse_args(argv)
    try:print(json.dumps(verify(a.root,a.cache,a.runtime,a.qualification),indent=2));return 0
    except (OSError,ValueError,KeyError) as e:print('FAIL2: saved Genomic SEM foundation did not verify: '+str(e)+'. Restore original evidence; this reader never upgrades an incomplete adapter or admits a paper analysis.',file=sys.stderr);return 2

if __name__=='__main__':raise SystemExit(main())
