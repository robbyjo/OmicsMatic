#!/usr/bin/env python3
"""Read saved GCTA admission bytes and receipts; no Git,Java,XML parsing or model execution."""
from pathlib import Path,PurePosixPath
from collections import Counter
import argparse,hashlib,json,re,zipfile

def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--restore',action='store_true');args=parser.parse_args();out=args.out.resolve();out.mkdir(parents=True,exist_ok=False);root=Path(__file__).resolve().parents[2];q=root/'docs/literature/gcta-catalog-checkpoint'
    try:
        packet=json.loads((q/'CHECKS.json').read_bytes())
        for name,w in {**packet['source_inputs'],**packet['canonical_after']}.items():
            p=PurePosixPath(name);assert not p.is_absolute() and '..' not in p.parts;assert pin(root/name)==w,'Exact owner/source/canonical bytes differ: '+name
        assert pin(q/'checks.zip')==packet['archive'] and pin(q/'INVENTORY.json')==packet['inventory'];inventory=json.loads((q/'INVENTORY.json').read_bytes());unique={v['sha256'] for v in inventory.values()}
        with zipfile.ZipFile(q/'checks.zip') as archive:
            names=archive.namelist();assert len(names)==len(set(names))==len(unique) and set(names)=={'blobs/'+sha for sha in unique}
            for sha in unique:assert re.fullmatch('[0-9a-f]{64}',sha) and hashlib.sha256(archive.read('blobs/'+sha)).hexdigest()==sha
            for name,w in inventory.items():
                p=PurePosixPath(name);assert not p.is_absolute() and '..' not in p.parts;raw=archive.read('blobs/'+w['sha256']);assert len(raw)==w['bytes']
                if args.restore:
                    target=out/'evidence'/name;target.parent.mkdir(parents=True,exist_ok=True)
                    with target.open('xb') as stream:stream.write(raw)
            def read(name):return json.loads(archive.read('blobs/'+inventory[name]['sha256']))
            verified=read('current/verify-fresh-1/COMPLETED.json');assert verified['status']=='PASS_GCTA_CUMULATIVE_ADMISSION_ONE_FAMILY_ONE_ALIAS_27_SOURCE_ROLES' and verified['fresh_source_audit_performed'] and verified['current_manifest']['entries']==829
            result=verified['result'];assert (result['integrations'],result['native_integrations'],result['upstream_integrations'],result['reviewed_uses'],result['pending_uses'])==(141,26,115,1575,1140);assert result['alias_extra_integration_credit']==0 and result['existing_reviews_retained']==1548
            for folder in ['preview-ui-1','installed-ui-1']:
                ui=read('current/'+folder+'/browser/COMPLETED.json');assert ui['status']=='PASS_LOCAL_GCTA_CATALOG_ROLES_ALIAS_COUNTS_DESKTOP_MOBILE_WARNINGS' and ui['reviewed']==1575 and ui['validated_family_aliases']==1 and not ui['page_errors'] and not ui['website_published']
            cli=read('current/cli-controls-1/COMPLETED.json');assert len(cli['results'])==5 and all(r['exit_code']==r['expected_exit'] for r in cli['results'])
            independent=read('current/independent-readback-1/RESULT.json');assert independent['registry_definitions']==485 and independent['unrelated_registry_definitions_exact']==483 and independent['old_reviews_exact']==1548 and independent['dispatcher_and_review_multi_step_inverses_passed']
        data=root/'docs/literature/omics-2023-2026';papers=json.loads((data/'papers.json').read_bytes());tools=json.loads((data/'tool-inventory.json').read_bytes());summary=json.loads((data/'summary.json').read_bytes());reviews=json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];counts=Counter(t['implementation_status'] for t in tools)
        assert len(papers)==250 and len(tools)==446 and sum(len(p['tools']) for p in papers)==2715 and len(reviews)==1575
        assert counts['upstream-adapter-validated']==115 and counts['native-subset-validated']==26 and counts['validated-family-alias']==1 and counts['method-overlap']==33 and counts['missing']==259 and dict(counts)==summary['implementation_status_counts']
        assert next(t for t in tools if t['id']=='cojo')['family_alias_of']=='gcta' and next(t for t in tools if t['id']=='cojo')['new_scoped_integration_credit']==0
        output=dict(status='PASS_SAVED_GCTA_CATALOG_CHECKPOINT_BYTES_ONLY',logical_files=len(inventory),unique_blobs=len(unique),integration_families=141,validated_aliases_excluded_from_extra_credit=1,observations=2715,reviewed=1575,pending=1140,manifest_entries=829,source_control_tests_per_host=23,control_hosts=2,actual_CLI_refusals=5,preview_and_installed_UI_pass=True,Git_Java_primary_XML_or_models_executed=False,whole_goal_complete=False)
        (out/'RESULT.json').write_text(json.dumps(output,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(output))
    except BaseException as error:
        (out/'FAILURE.txt').write_text(repr(error)+'\n',encoding='utf-8',newline='\n');raise
if __name__=='__main__':main()
