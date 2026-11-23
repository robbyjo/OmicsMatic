from pathlib import Path
import json,re,subprocess,sys
from handover_inventory_20261122_111 import save,snapshot
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/mrpresso-catalog-178';O=R/'build/git-publish/20261123-mrpresso-catalog-178'
for host in ('windows','linux'):
 test=json.loads((B/f'checks-2/{host}-model-transaction.RESULT.json').read_bytes());assert test['exit_code']==0
 stderr=(B/f'checks-2/{host}-model-transaction.stderr').read_text();assert re.search(r'Ran 19 tests in [0-9.]+s',stderr) and stderr.rstrip().endswith('OK')
 assert json.loads((B/f'inspect-{host}-2/RESULT.json').read_bytes())['exit_code']==0
controls=json.loads((B/'controls-2/RESULT.json').read_bytes());assert len(controls['cases'])==10 and controls['canonical_bytes_unchanged']
assert all(x['exit_code']==x['expected'] and not x['complete_result'] for x in controls['cases'])
ui=json.loads((B/'ui-preview-2/COMPLETED.json').read_bytes());assert ui['unique_profile_processes_after']==0 and ui['no_OS_process_termination']
assert ui['page_result']['paper_roles_checked']==['P105','P125','P135','P150','P189','P224'] and ui['page_result']['page_errors']==[]
website=json.loads((B/'website-2/VALIDATION.json').read_bytes());assert not website['full_checkWebsite_passed'] and len(website['preexisting_missing_citation_panels'])==33 and not website['new_missing_citation_panels']
save(O/'WORKTREES-PREAPPLY.json',snapshot());save(O/'PREAPPLY-GATES.json',dict(status='PASS_REQUIRED_PREAPPLY_GATES',model_transaction_tests_per_host=19,
 actual_CLI_refusals=10,styled_preview_passed=True,full_website_check_passed=False,unchanged_existing_citation_failures=33,
 new_citation_failures=0,canonical_modified_before_this_apply=False,native_science_reexecuted=False))
raise SystemExit(subprocess.call([sys.executable,'-X','utf8','-B',str(R/'build/run_mrpresso_catalog_phase_20261123_181.py'),'apply','windows','1'],cwd=T))
