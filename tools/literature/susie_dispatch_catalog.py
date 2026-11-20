"""Bounded command-discovery metadata update after the exact additive SuSiE route.

Earlier catalog owners/readers stay unchanged. Their retained metadata and
archived control source are checked explicitly; the new control producer has
separate qualification. Only the dispatcher uses the declared byte inverse.
"""
from pathlib import Path
import copy
import hashlib
import html
import json
import re
import zipfile
from susie_catalog import model as old
from susie_catalog.catalog import PROOF as OLD_PROOF,OWN as OLD_OWN
from susie_dispatch_transition import DISPATCH,BEFORE,dispatcher_before,pin,require
from susie_replay_control import WORKFLOW,ARCHIVE,check as replay_control_change

ROOT=Path(__file__).resolve().parents[2]
BASE='05a3b132447c60b93fd0c51401c4e17a79405f30'
META,DATA=old.META,old.DATA
GUIDE='docs/vignettes/susie-tools.md'
FOLDER='docs/literature/susie-dispatch-catalog'
PROOF=DATA+'/susie-dispatch-validation.json'
COMPAT=DATA+'/susie-dispatch-shared-source-compatibility.json'
SCOPE=('Java file workflow through the global susie-tools command and the retained standalone '
       'org.jlinalg.cli.SusieWorkflowCli entry point, calling original susieR 0.11.92, 0.12.35 and 0.14.2 '
       'with exact private R 4.6.1 profiles. Individual design/phenotype, version-specific RSS and '
       'sufficient-statistic interfaces, raw/original-RDS capture, RUN, R-free structural VERIFY and '
       'original-runtime REPLAY. Native global susie stays separate; there is no automatic fallback.')
NOTE=(old.NOTE+' The root-route change adds no model interface, author-function attribution, family credit '
      'or source-review credit. A separately qualified replay-output guard rejects destinations inside the parent, '
      'including existing-path aliases, before output creation. The model-fitting code is unchanged. '
      'The frozen earlier source/scope guides describe their producing checkpoints; '
      'the current global-command guide gives the new invocation. The published 0.4.0 binary predates this route.')
ACTION=old.ACTION
NOTICE=('<aside id="susie-global-command-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);'
    'margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
    '<h2>SuSiE: global command with retained scientific scope</h2><p>'+html.escape(old.CURRENT)+'</p><p>'+
    html.escape(SCOPE+' '+NOTE)+'</p><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+
    '</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Global command, exact runtime and recovery</a>.</p></aside>')
CHANGED_FIELDS={'coverage_note','implementation_scope','global_CLI_dispatch_available','qualified_component_entrypoints',
    'current_documentation','source_role_documentation','global_command_qualification','validation_note'}


def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()


def retained_catalog(before):
    """Check actual preceding proof against explicit prior metadata, never a replaced lookup."""
    proof=json.loads((ROOT/OLD_PROOF).read_bytes())
    require(proof['status']=='PASS_SUSIE_CUMULATIVE_ADMISSION_20_ROLES_THREE_ORIGINAL_EDITIONS'
            and proof['after']=={n:pin(raw) for n,raw in before.items()},'Different preceding SuSiE catalog')
    require(proof['source_code']=={n:pin((ROOT/n).read_bytes()) for n in OLD_OWN},'Original SuSiE owner changed')
    control=replay_control_change((ROOT/ARCHIVE).read_bytes(),(ROOT/WORKFLOW).read_bytes())
    for group in ['evidence','foundation_sources']:
        for n,wanted in proof[group].items():
            raw=(ROOT/n).read_bytes()
            if n==DISPATCH:raw=dispatcher_before(raw)
            if n==WORKFLOW:raw=(ROOT/ARCHIVE).read_bytes()
            require(pin(raw)==wanted,'Changed preceding source/evidence outside explicit root inverse: '+n)
    for n,wanted in proof['component_qualification']['source_files'].items():
        raw=(ROOT/ARCHIVE).read_bytes() if n==WORKFLOW else (ROOT/n).read_bytes()
        require(pin(raw)==wanted,'Changed retained original scientific component: '+n)
    with zipfile.ZipFile(ROOT/'docs/literature/susie-catalog-admission/catalog-before.zip') as z:
        require(len(z.namelist())==8 and set(z.namelist())==set(META),'Different original admission before archive')
        earlier={n:z.read(n) for n in META}
    require(proof['before']=={n:pin(raw) for n,raw in earlier.items()},'Old admission before pins differ')
    census=json.loads((ROOT/'docs/literature/susie-workflow-checks/CENSUS.json').read_bytes())
    result=old.check(earlier,before,census,proof['component_qualification'])
    require(result['new_scoped_integrations']==138 and result['reviewed_uses']==1503,'Different preceding counts')
    return dict(status='PASS_RETAINED_SUSIE_CATALOG_WITH_EXPLICIT_ROOT_SOURCE_INVERSE',result=result,
        source_lookup_overlay=False,original_owner_modified=False,original_owner_executed_here=False,
        scientific_models_run=False,old_fingerprints_refreshed=False,replay_control_change=control,
        previous_control_source_archived=True,current_control_requires_separate_qualification=True)


def transition(before):
    require(set(before)==set(META),'Exactly eight preceding metadata files required')
    proof=json.loads((ROOT/OLD_PROOF).read_bytes())
    require(proof['after']=={n:pin(raw) for n,raw in before.items()},'Before metadata is not the admitted preceding stage')
    inventory=json.loads(before[META[1]]);row=next(r for r in inventory if r['id']=='susie')
    require(row['global_CLI_dispatch_available'] is False and row['implementation_status']=='upstream-adapter-validated'
        and row['implementation_scope']==old.SCOPE+' '+old.NOTE,'Different/already updated SuSiE command scope')
    row.update(coverage_note=SCOPE+' '+NOTE,implementation_scope=SCOPE+' '+NOTE,global_CLI_dispatch_available=True,
        qualified_component_entrypoints=['susie-tools --request RUN/VERIFY/REPLAY','susie-tools --verify',*row['qualified_component_entrypoints']],
        current_documentation=GUIDE,source_role_documentation=old.GUIDE,
        global_command_qualification='../susie-dispatch-checks/QUALIFICATION.json',
        validation_note='Global normal JAR: 173 checks on each of Windows Java21/25 and Linux Java21, with original workflow/native/Scanpy/Enrichr regressions and replay-parent protection; five actual root-route original-model comparisons have numeric difference zero (three converged RUNs, one refused nonconvergence, one replay). All 23458 payloads outside the main dispatcher and replay-control class are unchanged. '+row['validation_note'])
    after=dict(before);raw=encoded(inventory)
    if before[META[1]].count(b'\n')==before[META[1]].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
    after[META[1]]=raw
    page=before[META[6]].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Embedded before data differs')
    payload=json.dumps(dict(papers=json.loads(before[META[0]]),tools=inventory,summary=json.loads(before[META[2]])),ensure_ascii=False).replace('<','\\u003c')
    page=page[:match.start(2)]+payload+page[match.end(2):]
    for name,text in [(META[6],page),(META[7],before[META[7]].decode())]:
        require(text.count(old.NOTICE)==1 and 'id="susie-global-command-notice"' not in text,'Different/already updated notice')
        text=text.replace(old.NOTICE,'<details><summary>Previous SuSiE catalog checkpoint (historical)</summary>'+old.NOTICE+'</details>',1)
        body=re.search(r'<body[^>]*>',text,re.I);require(body is not None,'HTML body missing')
        after[name]=(text[:body.end()]+NOTICE+text[body.end():]).encode()
    for name,title in [(META[3],'250-paper omics and GWAS toolbox catalog'),(META[4],'Current literature warnings and required actions')]:
        after[name]=('# '+title+'\n\n## SuSiE global command\n\n'+old.CURRENT+'\n\n'+SCOPE+'\n\n'+NOTE+'\n\n'+ACTION+
            '\n\nRead [the global command guide](../../vignettes/susie-tools.md).\n\n<details><summary>Before global SuSiE dispatch (historical)</summary>\n\n'+before[name].decode()+'\n</details>\n').encode()
    return after


def check(before,after):
    require(after==transition(before),'Unexpected command-discovery metadata change')
    for name in [META[0],META[2],META[5]]:require(after[name]==before[name],'Paper identities, summary or reviews changed')
    a,b=json.loads(before[META[1]]),json.loads(after[META[1]])
    require([r for r in a if r['id']!='susie']==[r for r in b if r['id']!='susie'],'Unrelated tool row changed')
    x,y=next(r for r in a if r['id']=='susie'),next(r for r in b if r['id']=='susie')
    require({k:v for k,v in x.items() if k not in CHANGED_FIELDS}=={k:v for k,v in y.items() if k not in CHANGED_FIELDS},'Scientific/source-role scope changed')
    return dict(status='PASS_SUSIE_GLOBAL_COMMAND_METADATA_WITHOUT_NEW_CREDIT',papers=250,observations=2704,
        reviewed_uses=1503,pending_uses=1201,integrations=138,native_integrations=25,upstream_integrations=113,
        global_CLI_dispatch_available=True,new_family_credit=0,new_review_records=0,
        model_fitting_source_changed=False,replay_output_admission_changed=True,
        whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
