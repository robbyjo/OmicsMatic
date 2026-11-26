"""Read-only source/evidence verification for the scoped native ALLCools region methods."""
import argparse,hashlib,json,subprocess,sys
from pathlib import Path

def pin(p):
    h=hashlib.sha256()
    with p.open('rb')as f:
        for block in iter(lambda:f.read(1048576),b''):h.update(block)
    return dict(bytes=p.stat().st_size,sha256=h.hexdigest())
def require(ok,message):
    if not ok:raise RuntimeError(message)
def safe(s):return isinstance(s,str)and s and not s.startswith('/')and':'not in s and'\\'not in s and all(x not in ('','.','..')for x in s.split('/'))
def read(p):return json.loads(p.read_bytes())
def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2]);ap.add_argument('--private',type=Path);args=ap.parse_args();r=args.root.resolve();d=r/'docs/literature/allcools-region-validation';q=read(d/'QUALIFICATION.json')
    require(q['status']=='PASS_SCOPED_NATIVE_ALLCOOLS_REGION_QUANTIFICATION'and q['new_family_credit']==q['new_source_reviews']==0 and not q['dataset_adapter_qualified']and not q['whole_goal_complete'],'Scope or credit differs')
    require(q['original_cases']==1000 and q['original_numeric_values']==186272 and q['tests_per_host']==31 and q['complete_Zarr_values_per_host']==192,'Qualification population differs')
    sources=read(d/'SOURCES.json');require(len(sources)>=14,'Incomplete source context')
    for name,expected in sources.items():require(safe(name)and pin(r/name)==expected,'Changed source: '+name)
    require(pin(d/'QUALIFICATION.json')==read(d/'STATUS.json')['qualification'],'Changed qualification')
    if args.private:
        b=args.private.resolve();index=read(d/'PRIVATE-INDEX.json');require(pin(d/'PRIVATE-INDEX.json')==read(d/'STATUS.json')['private_index'],'Changed private evidence index')
        for name,expected in index.items():require(safe(name)and pin(b/name)==expected,'Changed/missing retained private evidence: '+name)
        build=read(b/'project-build-Windows-2/COMPLETED.json');require(build['producer']==q['producer']and build['totals']==dict(tests=31,failures=0,errors=0,skipped=0),'Different full producer build')
        require(read(b/'region-tests-Linux-1/COMPLETED.json')['tests']==31,'Missing Linux tests')
        for host in ('Windows','Linux'):
            workflow=read(b/('region-workflow-'+host+'-1/COMPLETED.json'));require(workflow['producer']==q['producer']and workflow['full_original_Zarr_values_compared']==192 and workflow['corruption_refusals']==8 and workflow['resource_refusals']==workflow['overflow_refusals']==workflow['missing_prerequisite_skips']==1 and workflow['saturated_count_export_to_native_posterior_passed'],'Incomplete packaged controls')
            portable=read(b/('region-portable-'+host+'-1/COMPLETED.json'));require(len(portable['replays'])==2 and all(v['derived_output_bytes_identical_between_hosts']and v['foreign_original_unavailable']for v in portable['replays']),'Incomplete foreign replay')
        require(read(b/'runtime-readback-1/COMPLETED.json')['runtime_unchanged'],'Original runtime changed')
    print(json.dumps(dict(status='PASS_SCOPED_NATIVE_ALLCOOLS_REGION_READBACK',sources=len(sources),private_checked=bool(args.private),new_family_credit=0,whole_goal_complete=False)))

if __name__=='__main__':main()
