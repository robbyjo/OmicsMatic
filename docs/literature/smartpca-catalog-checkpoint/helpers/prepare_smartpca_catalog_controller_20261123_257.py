from pathlib import Path
import json,sys
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';P=T/'tools/literature/smartpca_catalog';D=T/'docs/literature/smartpca-catalog-sources'
def put(path,text):path.write_text(text,encoding='utf-8',newline='\n')
code=(T/'tools/literature/mrpresso_catalog/catalog.py').read_text(encoding='utf-8')
code=code.replace('MR-PRESSO','smartpca').replace('mrpresso','smartpca').replace('MRPRESSO','SMARTPCA')
code=code.replace('6dda7a239967c68266c786de5678a156e3196658','efe2e19dd1f4e5add6a6dd09b949e900368dea74').replace('WITH_UNREPORTED_SETTINGS','WITH_CONTEXT_VERSIONS')
code=code.replace('verify_smartpca_workflow_checkpoint import','verify_smartpca_fst_checkpoint import')
code=code.replace("'catalog.py','compatibility.py','test_model.py'","'catalog.py','test_model.py'")
code=code.replace('audit_smartpca_sources.py','audit_smartpca_catalog_sources.py')
start=code.index('FOUNDATION=');end=code.index('\n\n\ndef git',start)
code=code[:start]+'''FOUNDATION=['tools/literature/seurat_catalog/catalog.py','tools/literature/qtltools_catalog/catalog.py',
    'tools/literature/mrpresso_catalog/model.py','tools/literature/collect_omics_review.py',
    'tools/literature/audit_smartpca_sources.py','docs/literature/smartpca-native-development/ROLES.json',
    'tools/literature/verify_smartpca_fst_checkpoint.py','tools/literature/verify_smartpca_workflow_checkpoint.py',
    'tools/literature/smartpca_dispatch_compatibility.py']'''+code[end:]
code=code.replace('docs/literature/smartpca-workflow-qualification/STATUS.json','docs/literature/smartpca-fst-qualification/STATUS.json')
old="""    from smartpca_catalog.compatibility import verify as compatibility_verify
    bridge=compatibility_verify()
    return q,dict(workflow=measured,predecessor_dispatch_bridge=bridge,fresh_primary_XML_checked=250 if fresh else 0,original_analysis_reexecuted=False)"""
new="""    from verify_smartpca_workflow_checkpoint import verify as prepared_verify
    prepared=prepared_verify(ROOT)
    return q,dict(workflow=measured,prepared_PCA=prepared,predecessor_dispatch_bridge_checked_by_fst_reader=True,
        historical_readers_reexecuted_on_changed_dispatcher=False,fresh_primary_XML_checked=250 if fresh else 0,original_analysis_reexecuted=False)"""
assert old in code;code=code.replace(old,new)
code=code.replace("roles=m.frozen_primary()['roles']","roles=[m.applied_role(r) for r in m.frozen_primary()['roles']]")
code=code.replace("['entries']==857","['entries']==859").replace("['entries']==859,'Incorrect complete after manifest'","['entries']==861,'Incorrect complete after manifest'").replace("['entries']==859,'Changed complete859-entry manifest'","['entries']==861,'Changed complete861-entry manifest'")
put(P/'catalog.py',code)
transaction=(T/'tools/literature/mrpresso_catalog/test_transaction.py').read_text(encoding='utf-8').replace('mrpresso_catalog','smartpca_catalog').replace('mrpresso-catalog-178','smartpca-catalog-255')
put(P/'test_transaction.py',transaction)
sys.path.insert(0,str(T/'tools/literature'));from smartpca_catalog.primary import pin
state=dict(status='PASS_FULL250_SOURCE_CENSUS_SIX_CONTEXT_ROLES_NO_CATALOG_CREDIT',primary=pin((D/'ROLES.json').read_bytes()),primary_code=pin((P/'primary.py').read_bytes()),draft_source=pin((T/'docs/literature/smartpca-native-development/ROLES.json').read_bytes()),new_reviews=0,new_family_credit=0,whole_goal_complete=False)
put(D/'STATUS.json',json.dumps(state,indent=2)+'\n')
print('Prepared guarded controller, source pins and shared-transaction regression suite.')
