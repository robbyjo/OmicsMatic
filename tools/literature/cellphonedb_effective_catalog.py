# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Current CellPhoneDB startup metadata; frozen scientific sources and roles stay strict."""
import contextlib,json
from cellphonedb_catalog import *
import cellphonedb_catalog as prior
from setup_deseq2_reference import pin
from signac_scan_evidence import unpack,byte_pin

HISTORY=['cellphonedb-catalog-support.zip','leafcutter-catalog-support.zip','hail-catalog-support.zip','biomart-catalog-support.zip','loftee-catalog-support.zip','smr-catalog-support.zip','dndscv-catalog-support.zip']

def historical_readme_document(root,data,archived,expected):
    assert byte_pin(archived)==expected
    if pin(root/'README.md')==expected:return
    proof=json.loads((data/'cellphonedb-effective-root-readme-evolution.json').read_bytes());assert proof['status']=='passed-exact-README-documentation-evolution' and proof['schema_version']==1 and proof['path']=='README.md' and proof['after']==pin(root/'README.md') and not proof['scientific_source_exception_added'];rows=[r for r in proof['historical_documents'] if r['document']==expected];assert len(rows)==1;row=rows[0];assert row['archive'] in HISTORY and pin(data/row['archive'])==row['archive_pin'];old=json.loads((data/row['archive'].replace('-support.zip','-validation.json')).read_bytes());assert old['archive']==row['archive_pin'] and unpack(data/row['archive'],old)['current-source/README.md']==archived

@contextlib.contextmanager
def current_catalog_review_context():
    saved=prior.historical_readme_document
    try:
        prior.historical_readme_document=historical_readme_document
        with prior.current_catalog_review_context():yield
    finally:prior.historical_readme_document=saved

def _metadata(name,*args):
    with current_catalog_review_context():return getattr(prior,name)(*args)

def current_meme_metadata(root,data):return _metadata('current_meme_metadata',root,data)
def current_chipseeker_metadata(root,data):return _metadata('current_chipseeker_metadata',root,data)
def monocle_development_metadata(root,data):return _metadata('monocle_development_metadata',root,data)
def normal_fit_metadata(root,data):return _metadata('normal_fit_metadata',root,data)
def cicero_metadata(root,data):return _metadata('cicero_metadata',root,data)
def soupx_metadata(root,data):return _metadata('soupx_metadata',root,data)
def dndscv_metadata(root,data):return _metadata('dndscv_metadata',root,data)
def smr_metadata(root,data,tool):return _metadata('smr_metadata',root,data,tool)
def loftee_metadata(root,data):return _metadata('loftee_metadata',root,data)
def biomart_metadata(root,data):return _metadata('biomart_metadata',root,data)
def hail_metadata(root,data):return _metadata('hail_metadata',root,data)
def leafcutter_metadata(root,data):return _metadata('leafcutter_metadata',root,data)

def cellphonedb_metadata(root,data):
    from cellphonedb_hash_seed_evidence import verify
    proof=verify(emit=False);result=_metadata('cellphonedb_metadata',root,data)
    result.update(implementation_source='src/main/java/org/jlinalg/cli/CellPhoneDbWorkflowCliV2.java',development_candidate=proof['artifact']['path'],development_candidate_sha256=proof['artifact']['sha256'],development_cli_class='org.jlinalg.cli.CellPhoneDbWorkflowCliV2',runtime_setup_cli_class='org.jlinalg.cli.CellPhoneDbSetupCliV2',runner_revision=2,python_hash_seed_effective=True,python_startup_receipt='worker/python-startup.json',first_generation_runs_relabelled=False)
    result['implementation_scope']+=' Current runner revision2 uses sanitized -B -P -s startup, checks effective Python hash flags before scientific imports and verifies worker/python-startup.json before atomic admission. First-generation -I ignored PYTHONHASHSEED; retained runs are not relabelled. Read CELLPHONEDB_HASH_SEED.md and use the explicit V2 classes.'
    result['validation_note']+=' Revision2 repeats all19 original workflows and17 complete serial byte/RNG comparisons. Three actual JVMs audit all19 cases and each reject22 corrupt startup receipts plus a missing receipt at seeds0,123 and4294967295. Independent repeated startup baselines,8 public commands,12 producer protocols,6 export/setup controls,7 invalid seed requests,8 unsafe startup modes and owned cancellation pass. Native seed0 SipHash13/Unicode probes are independent; nonzero hash secrets remain upstream. A bytecode-enabled negative-control cache incident, strict runtime rejection, checked quarantine without receipt refresh and successful reruns are retained. The canonical executable and original scientific profiles are unchanged.'
    return result

CELLPHONEDB_WARNING=prior.CELLPHONEDB_WARNING.replace('CellPhoneDbWorkflowCli','CellPhoneDbWorkflowCliV2').replace('CellPhoneDbSetupCli','CellPhoneDbSetupCliV2').replace('CELLPHONEDB_WORKFLOWS.md','CELLPHONEDB_HASH_SEED.md')
_warning='''**Use runner revision2 for an effective `python_hash_seed`.** First-generation `-I` ignored `PYTHONHASHSEED`. The corrected worker uses sanitized `-B -P -s`, checks effective flags before scientific imports, and records `worker/python-startup.json`. Native Java audits that receipt before atomic admission and independently checks seed-zero hash probes. Missing, changed or invalid startup evidence means FAIL2 for the whole batch. Rerun NEW paths when controlled hash ordering is required; do not relabel old runs or refresh old hashes. Nonzero seeds use bound-interpreter checks and independent baselines; they do not have a native hash-secret port. Hash ordering does not make entropy subsampling or parallel nulls deterministic. The canonical released executable and main dispatcher are unchanged; use the explicit development V2 class.

'''
CELLPHONEDB_WARNING=CELLPHONEDB_WARNING.replace('## CellPhoneDB: warnings and required actions\n\n','## CellPhoneDB: warnings and required actions\n\n'+_warning)
CELLPHONEDB_ACTIONS=prior.CELLPHONEDB_ACTIONS.replace('CellPhoneDbWorkflowCli','CellPhoneDbWorkflowCliV2').replace('CellPhoneDbSetupCli','CellPhoneDbSetupCliV2').replace('CELLPHONEDB_WORKFLOWS.md','CELLPHONEDB_HASH_SEED.md')
_actions='<p><strong>Use runner revision2 for an effective Python hash seed.</strong> First-generation <code>-I</code> ignored <code>PYTHONHASHSEED</code>. Corrected <code>-B -P -s</code> startup checks effective flags before scientific imports. Read <code>worker/python-startup.json</code>; missing, changed or invalid evidence means FAIL2 for the whole batch. Native seed-zero probes and repeated nonzero interpreter baselines are checked. Retry NEW paths; never relabel old runs or refresh hashes. Hash ordering does not make entropy sampling or parallel nulls deterministic. The canonical executable and main dispatcher are unchanged; use the explicit development V2 class and <code>CELLPHONEDB_HASH_SEED.md</code>.</p>'
CELLPHONEDB_ACTIONS=CELLPHONEDB_ACTIONS.replace('</h2>','</h2>'+_actions,1)
