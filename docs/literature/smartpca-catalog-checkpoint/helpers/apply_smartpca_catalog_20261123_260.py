from pathlib import Path
import json,re,subprocess,sys
from handover_inventory_20261122_111 import save,snapshot,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-catalog-255';O=R/'build/git-publish/20261123-smartpca-catalog-255'
for host in ('windows','linux'):
 test=json.loads((B/f'checks-1/{host}-model-transaction.RESULT.json').read_bytes());assert test['exit_code']==0
 stderr=(B/f'checks-1/{host}-model-transaction.stderr').read_text();assert re.search(r'Ran 21 tests in [0-9.]+s',stderr) and stderr.rstrip().endswith('OK')
 assert json.loads((B/f'inspect-{host}-1/RESULT.json').read_bytes())['exit_code']==0
controls=json.loads((B/'controls-1/RESULT.json').read_bytes());assert len(controls['cases'])==10 and controls['canonical_bytes_unchanged']
assert all(x['exit_code']==x['expected'] and not x['complete_result'] for x in controls['cases'])
ui=json.loads((B/'ui-preview-2/COMPLETED.json').read_bytes());assert ui['unique_profile_processes_after']==0 and ui['no_OS_process_termination']
assert ui['page_result']['paper_roles_checked']==['P019','P041','P045','P071','P180','P229'] and ui['page_result']['page_errors']==[]
website=json.loads((B/'website-2/VALIDATION.json').read_bytes());assert not website['full_checkWebsite_passed'] and len(website['preexisting_missing_citation_panels'])==33 and not website['new_missing_citation_panels']
now=snapshot();save(O/'WORKTREES-PREAPPLY.json',now)
old={x['worktree']:x for x in json.loads((O/'WORKTREES-BEFORE.json').read_bytes())};current={x['worktree']:x for x in now};others=[x for x in old if Path(x)!=T];indexes=json.loads((O/'INDEXES-BEFORE.json').read_bytes())
assert len(others)==18 and set(old)==set(current) and all(old[x]==current[x] for x in others)
assert all(pin(Path(indexes[x]['path']))==indexes[x]['identity'] for x in others)
save(O/'PREAPPLY-GATES.json',dict(status='PASS_REQUIRED_SMARTPCA_PREAPPLY_GATES',model_transaction_tests_per_host=21,actual_CLI_refusals=10,
 styled_preview_passed=True,full_website_check_passed=False,unchanged_existing_citation_failures=33,new_citation_failures=0,
 canonical_modified_before_this_apply=False,native_science_reexecuted=False,other_owners_preserved=18,old_browser_guard_failure_retained_and_recovered=True))
raise SystemExit(subprocess.call([sys.executable,'-X','utf8','-B',str(R/'build/run_smartpca_catalog_phase_20261123_258.py'),'apply','windows','1'],cwd=T))
