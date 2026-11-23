from pathlib import Path
import sys,json
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';P=T/'tools/literature/smartpca_catalog';P.mkdir()
def put(path,text):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf-8',newline='\n')
put(P/'__init__.py','"""Source-bound smartpca native-family catalog admission."""\n')
primary=(T/'tools/literature/audit_smartpca_sources.py').read_text(encoding='utf-8')
primary=primary.replace('parents[2]','parents[3]').replace('def audit(cache):','def _source_audit(cache,papers_raw=None):').replace("papers_raw=(ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes();papers=json.loads(papers_raw);assert len(papers)==250","papers_raw=(ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes() if papers_raw is None else papers_raw;papers=json.loads(papers_raw);assert len(papers)==250")
primary=primary[:primary.index('def main():')]+'''POSITIVE=list(ANCHORS)

def require(condition,message):
    if not condition:raise ValueError(message)

def audit(cache,papers_raw=None):
    papers_raw=(ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes() if papers_raw is None else papers_raw
    result=_source_audit(cache,papers_raw)
    draft=json.loads((ROOT/'docs/literature/smartpca-native-development/ROLES.json').read_bytes())
    require(result==draft,'Changed draft census/source identities or original observations')
    papers=json.loads(papers_raw)
    for role in result['roles']:
        use=next(u for paper in papers if paper['paper_id']==role['paper_id'] for u in paper['tools'] if u['tool_id']=='smartpca')
        role['original_observation']=use
        require(pin(json.dumps(use,sort_keys=True,separators=(',',':')).encode())==role['original_observation_identity'],'Changed original observation')
        role['source_table_evidence']=[]
        role['scope_limit']=role['boundary']
        if role['paper_id']=='P180':
            role['reported_settings']={'PCA':dict(lsqproject=True,outlier_removal=False,minimum_SNP_cutoff=100000),'FST':dict(fstonly=True)}
            role['scope_limit']+=' The100,000-SNP cutoff belongs to PCA; its application to FST is not established. No edition/default/uncertainty is inferred for the FST call.'
    result['status']='PASS_FULL250_SMARTPCA_SIX_SOURCE_ROLES_CONTEXT_VERSIONS_NOT_ADMISSION'
    return result
'''
put(P/'primary.py',primary)
put(T/'tools/literature/audit_smartpca_catalog_sources.py','''"""Audit all250 frozen primary records before smartpca catalog admission."""
from pathlib import Path
import argparse,json
from smartpca_catalog.primary import audit,require
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--cache',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args()
    require(__debug__,'Run without -O');require(not a.out.exists(),'Use NEW output');r=audit(a.cache);a.out.parent.mkdir(parents=True,exist_ok=True);a.out.write_text(json.dumps(r,indent=2,ensure_ascii=False)+'\\n',encoding='utf-8',newline='\\n');print('PASS all250 primary identities and six context-specific smartpca roles; zero credit before admission.')
''')
sys.path.insert(0,str(T/'tools/literature'));from smartpca_catalog.primary import audit
roles=audit(R/'build/literature-review/xml');D=T/'docs/literature/smartpca-catalog-sources';D.mkdir()
put(D/'ROLES.json',json.dumps(roles,indent=2,ensure_ascii=False)+'\n')
put(P/'README.md','''# smartpca catalog admission

One qualified native-family transition and six context-specific paper-use reviews.
Run `python -B tools/literature/close_smartpca_catalog.py --help` for guarded
plan/inspect/apply/verify. Mutating modes need the full frozen primary XML cache
and exact qualified JAR. Every output must be new under this checkout's build/.
The model preserves original extraction bytes semantically, prior reviews,
unrelated records, literal editions/program identifiers and unreported settings.
Admission is separate from workflow qualification or whole-paper reproduction.
See ADMISSION.md for exact validation and source limits. No report deployment
or release publication is performed by this controller.
''')
put(P/'ADMISSION.md','''# Admission contract

Baseline efe2e19dd1f4e5add6a6dd09b949e900368dea74:153 scoped families,
1,691 reviewed/1,031 pending uses. The guarded transition admits one native family
and six source reviews while preserving 2,722 observations and all prior records.
Source definitions, all250 primary identities, both qualified smartpca packets,
the explicit three-line dispatcher bridge, deterministic preview, complete
manifest, transaction/rollback checks and installed reconciliation are required.

P019 MVP v6 differs from ROSMAP projection with edition unreported; P180 PCA
7.2.0 differs from its FST paragraph whose edition is not restated; P229 program
13050 is not a package edition or the qualified16000 program. P045 r2thresh does
not establish killr2. The PCA100,000-SNP threshold is not inferred for FST.
The qualified available source is EIG7.2.1 commit4f7e9f0, with bounded PCA/FST
profiles. No historical equivalence, author environment, whole-tool, whole-paper,
scientific authentication or whole-goal completion is claimed.

SKIP3 skips dependent analysis: supply the named permitted input/runtime/exact
producer, or retain the skip. FAIL2 admits no complete result: preserve failed/
partial/undefined evidence, fix the stated cause and retry a new directory.
Require completion and passing VERIFY. Never replace IDs, literal NA/NaN/Inf,
unreported versions or negative FST. Explicit diagnostic retention does not make
undefined inference eligible. Missing original/runtime licenses are not bypassed.
''')
put(T/'tools/literature/close_smartpca_catalog.py','''"""Guarded smartpca native-family/source-role admission."""
from smartpca_catalog.catalog import main
if __name__=='__main__':raise SystemExit(main())
''')
print(json.dumps(dict(status='DRAFT_PRIMARY_READY',source_papers=roles['papers_checked'],roles=len(roles['roles']),new_credit=0),indent=2))
