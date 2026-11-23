from pathlib import Path
import json
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');D=T/'tools/literature/mrpresso_catalog'
(D/'__init__.py').write_text('"""Source-bound native MR-PRESSO catalog admission."""\n',encoding='utf-8')
source=(T/'tools/literature/squidpy_catalog/catalog.py').read_text()
source=source.replace('Squidpy','MR-PRESSO').replace('squidpy','mrpresso')
source=source.replace("BASE='483805ba165efd89e632a32394f23d25ae822cac'","BASE='6dda7a239967c68266c786de5678a156e3196658'")
source=source.replace("STATUS='PASS_ONE_SQUIDPY_UPSTREAM_FAMILY_SIX_SOURCE_ROLES_WITH_UNREPORTED_CALLERS'","STATUS='PASS_ONE_MRPRESSO_NATIVE_FAMILY_SIX_SOURCE_ROLES_WITH_UNREPORTED_SETTINGS'")
source=source.replace("'__init__.py','primary.py','model.py','catalog.py','test_model.py','test_transaction.py','check_ui.cjs','README.md','ADMISSION.md'", "'__init__.py','primary.py','model.py','catalog.py','compatibility.py','test_model.py','test_transaction.py','check_ui.cjs','README.md','ADMISSION.md'")
source=source.replace("'docs/literature/mrpresso-catalog-sources/ROLES.json','docs/literature/mrpresso-catalog-sources/STATUS.json']", "'docs/literature/mrpresso-catalog-sources/ROLES.json','docs/literature/mrpresso-catalog-sources/STATUS.json',m.GUIDE]")
source=source.replace("'tools/literature/fusion_catalog/model.py'","'tools/literature/squidpy_catalog/model.py'")
source=source.replace("'docs/literature/mrpresso-workflow-development/SOURCE-AUDIT.json'","'docs/literature/mrpresso-development-handover-20261123/EVIDENCE.json'")
source=source.replace("current_manifest_entries']==855", "current_manifest_entries']==857")
source=source.replace("['entries']==855", "['entries']==857").replace("['entries']==857", "['entries']==859")
# The previous line advances all occurrences; restore plan/inspection predecessor checks.
source=source.replace("clean_manifest()['entries']==859,'Changed complete predecessor manifest'", "clean_manifest()['entries']==857,'Changed complete predecessor manifest'")
source=source.replace("clean_manifest()['entries']==859,'Changed complete predecessor manifest'", "clean_manifest()['entries']==857,'Changed complete predecessor manifest'")
source=source.replace("current['entries']==859,'Incorrect complete after manifest'", "current['entries']==859,'Incorrect complete after manifest'")
source=source.replace("Changed complete857-entry manifest", "Changed complete859-entry manifest")
source=source.replace("return q,dict(workflow=measured,fresh_primary_XML_checked=250 if fresh else 0,original_analysis_reexecuted=False)",
 "from mrpresso_catalog.compatibility import verify as compatibility_verify\n    bridge=compatibility_verify()\n    return q,dict(workflow=measured,predecessor_dispatch_bridge=bridge,fresh_primary_XML_checked=250 if fresh else 0,original_analysis_reexecuted=False)")
source=source.replace('PASS_SQUIDPY_CATALOG_','PASS_MRPRESSO_CATALOG_')
assert "['entries']==857,'Changed complete predecessor manifest'" in source
assert "['entries']==859,'Incorrect complete after manifest'" in source
assert "PASS_ONE_MRPRESSO_NATIVE_FAMILY" in source and "mrpresso-workflow-development" not in source
(D/'catalog.py').write_text(source,encoding='utf-8')
(T/'tools/literature/close_mrpresso_catalog.py').write_text('"""Guarded MR-PRESSO native-family/source-role admission."""\nfrom mrpresso_catalog.catalog import main\n\nif __name__=="__main__":raise SystemExit(main())\n',encoding='utf-8')
old=T/'docs/literature/MRPRESSO_NATIVE_WORKFLOWS.md';text=old.read_text();first=text.index('## Warnings and required actions')
header='''# MR-PRESSO workflows and six source-bound paper roles

This is the current guide for the guarded MR-PRESSO native-family catalog checkpoint.
The installed `omics-2023-2026/mrpresso-catalog-validation.json` records admission;
the separate frozen native-workflow qualification grants no catalog credit by itself.
The complete250-paper implementation and semantic review goal remains unfinished.

Six curated source roles distinguish outlier correction, sensitivity, multivariable
height/BMI fracture analysis, conditional method reporting, nonsignificant global
tests and comparison to primary MR-RAPS analyses. Exact paper editions, seeds and
simulation settings remain unreported. P177 recommends the method in discussion;
actual execution is not established. P150's study-level decision tree/Bonferroni
policy remains separate from this native tool. See
[source roles](mrpresso-catalog-sources/ROLES.json) and the installed role-coverage file.

The [frozen pre-admission guide](MRPRESSO_NATIVE_WORKFLOWS.md) retains the exact
documentation/source context checked by the native qualification reader. It is
historical evidence, not a current catalog-status report. The scientific commands,
examples, limits and required actions below use that same qualified native engine.
Released JARs may predate the command; use the exact retained producer in the packet.

'''
(T/'docs/literature/MRPRESSO_WORKFLOWS.md').write_text(header+text[first:],encoding='utf-8')
roles=T/'docs/literature/mrpresso-catalog-sources/ROLES.json'
save(roles.with_name('STATUS.json'),dict(status='PASS_FULL250_MRPRESSO_SOURCE_ROLES_WITH_EXPLICIT_UNREPORTED_SETTINGS',
 roles_identity=pin(roles),papers_checked=250,positive_roles=6,discussion_only=['P177'],source_primary_text_redistributed=False,
 native_workflow_qualification='mrpresso-workflow-qualification/STATUS.json',new_reviews=0,new_family_credit=0,whole_goal_complete=False))
print('Prepared guarded native catalog controller and current guide; no canonical metadata changed.')
