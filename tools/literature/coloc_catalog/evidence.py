"""Exact predecessor/source/component bindings for cumulative COLOC admission."""
import json
from pathlib import Path
import zipfile
from coloc_catalog.model import META,DATA
from coloc_catalog.coverage import encoded,require
from plink_catalog import catalog as previous
from plink_catalog import evidence as old
import coloc_source_roles

ROOT=Path(__file__).resolve().parents[3]
BASE='6f155a6a773f528fe8961246aa057ee9e49abf86'
pin,rawpin,git=old.pin,old.rawpin,old.git
PACKETS=['docs/literature/coloc-abf-handover','docs/literature/coloc-abf-workflow-checks','docs/literature/coloc-susie-audit-checks']
FOUNDATION=sorted(set(old.FOUNDATION+previous.OWN+['tools/literature/coloc_source_roles.py',
    'tools/literature/verify_coloc_abf_handover.py','tools/literature/verify_coloc_abf_workflow.py',
    'tools/literature/verify_coloc_susie_audit.py']))

def baseline():
    before={n:git('show',BASE+':'+n) for n in META}
    proof=json.loads(git('show',BASE+':'+previous.PROOF))
    require(proof['status']==previous.STATUS and proof['after']=={n:rawpin(raw) for n,raw in before.items()},
            'Actual Git baseline differs from completed PLINK admission')
    return before

def predecessor(cache,pdf,author,before,out):
    path=ROOT/previous.PROOF
    require(path.read_bytes()==git('show',BASE+':'+previous.PROOF),'PLINK predecessor proof changed')
    proof=json.loads(path.read_bytes())
    require(proof['status']==previous.STATUS and proof['base_commit']==old.BASE,'Different PLINK predecessor')
    require(proof['source_code']=={n:pin(ROOT/n) for n in previous.OWN}
            and proof['foundation_sources']==previous.foundation(),'PLINK predecessor code/foundation changed')
    for n,wanted in proof['evidence'].items():require(pin(ROOT/n)==wanted,'PLINK evidence changed: '+n)
    with zipfile.ZipFile(ROOT/previous.FOLDER/'catalog-before.zip') as archive:
        require(len(archive.namelist())==8 and set(archive.namelist())==set(META),'Incomplete PLINK before archive')
        earlier={n:archive.read(n) for n in META}
    require(earlier==old.baseline(),'PLINK earlier stage differs from Git')
    require((ROOT/previous.FOLDER/'PREVIOUS-SHA256SUMS').read_bytes()==git('show',old.BASE+':'+DATA+'/SHA256SUMS'),'Old manifest differs')
    prior=old.predecessor(cache,pdf,earlier,Path(out)/'seurat')
    require(prior==proof['predecessor_audit'],'Seurat/METAL/LDAK predecessor chain differs')
    census=old.source_census(cache,author);qualification=old.qualification()
    require(census==json.loads((ROOT/previous.COVERAGE).read_bytes())
            and qualification==proof['component_qualification'],'Retained PLINK source/qualification differs')
    result=previous.check(earlier,before,census,qualification['component_audit'])
    require(proof['before']=={n:rawpin(raw) for n,raw in earlier.items()}
            and proof['after']=={n:rawpin(raw) for n,raw in before.items()},'PLINK stage bindings differ')
    require(all(proof.get(k)==v for k,v in result.items()),'PLINK recorded result differs')
    from catalog_source_integrity import reverse_delta,verify_source
    compat=json.loads((ROOT/previous.COMPAT).read_bytes());name=META[5]
    require(compat['schema_version']==1 and len(compat['paths'])==1 and compat['paths'][0]['path']==name
            and reverse_delta(before[name],compat['paths'][0])==earlier[name],'PLINK review inverse differs')
    verify_source(ROOT,ROOT/DATA,name,rawpin(earlier[name])['sha256'])
    return dict(status='PASS_PLINK_SEURAT_METAL_LDAK_ON_ACTUAL_STAGE_BYTES',proof=pin(path),result=result,
                historical_full_marker_audit=prior['historical_full_marker_audit'],
                source_XML_identities_checked=250,PLINK_roles_checked=96,original_programs_reexecuted=False,
                scientific_source_overlay_used=False,previous_fingerprints_refreshed=False)

def source_census(cache):
    name=PACKETS[0]+'/source-roles.json';raw=(ROOT/name).read_bytes()
    require(raw==git('show',BASE+':'+name),'Frozen COLOC source census changed')
    result=coloc_source_roles.audit(cache)
    require(result==json.loads(raw),'Fresh 250-identity/27-role source census differs')
    return result

def qualification():
    import verify_coloc_abf_handover as api
    import verify_coloc_abf_workflow as workflow
    import verify_coloc_susie_audit as bf
    artifacts={}
    for folder in PACKETS:
        for path in sorted((ROOT/folder).iterdir()):
            if path.is_file():
                name=path.relative_to(ROOT).as_posix()
                require(path.read_bytes()==git('show',BASE+':'+name),'Frozen COLOC qualification changed: '+name)
                artifacts[name]=pin(path)
    reports=dict(ABF_API=api.verify(),ABF_workflow=workflow.verify(None,False),native_BF=bf.verify())
    wf=json.loads((ROOT/PACKETS[1]/'QUALIFICATION.json').read_bytes())
    bq=json.loads((ROOT/PACKETS[2]/'QUALIFICATION.json').read_bytes())
    require(wf['status']=='PASS_SCOPED_NATIVE_ABF_WORKFLOW_QUALIFICATION'
            and bq['status']=='PASS_SCOPED_NATIVE_SUSIE_SOURCE_AUDIT_AND_FIXES','Different qualified components')
    require(any(row['case']=='old-abf-bundle-scientific-verify' and row['exit_code']==0
                for row in bq['qualification']['processes']),'Current BF-fix candidate lacks explicit ABF compatibility')
    return dict(status='PASS_COLOC_CURRENT_ABF_AND_EXPLICIT_BF_VARIANT_QUALIFICATIONS',
                ABF_workflow_tests_per_JVM=62,native_BF_tests_per_JVM=50,native_BF_literal_source_parity=False,
                reports=reports,artifacts=artifacts,current_candidate=bq['build']['candidate'],
                native_BF_source_profile_classification=bq['qualification']['source_profiles'],
                original_programs_reexecuted=False,previous_fingerprints_refreshed=False,
                whole_tool_qualified=False,whole_paper_reproduced=False,new_family_credit=0)
