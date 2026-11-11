# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact988-role catalog extension; unchanged scientific sources and archived980-role validators remain strict."""
import contextlib,gzip,json
from cellphonedb_effective_catalog import *
import cellphonedb_effective_catalog as prior
from cellphonedb_catalog import PriorReviewRoot
from setup_deseq2_reference import pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source

_frozen_980_verifier=prior.prior.verify_review_extension

def _verify_prior(root,data,raw,reviews):
 saved=prior.prior.historical_readme_document
 try:
  prior.prior.historical_readme_document=prior.historical_readme_document
  _frozen_980_verifier(PriorReviewRoot(root,raw),data,reviews)
 finally:prior.prior.historical_readme_document=saved

def load_catalog_registry(root):
 old=prior.load_catalog_registry(root);extra=json.loads((root/'tools/literature/palantir-named-software-registry.json').read_bytes());assert len(old)==433 and len(extra)==5 and not({r['id'] for r in old}&{r['id'] for r in extra});result=old+extra;assert len(result)==len({r['id'] for r in result})==438;return result

def verify_review_extension(root,data,reviews):
 roles=json.loads((data/'palantir-role-coverage.json').read_bytes());assert roles['status']=='passed-eight-trajectory-spatial-primary-role-reviews' and roles['applied'] and (roles['prior_reviews_preserved'],roles['new_reviews'],roles['total_reviews'],roles['supporting_pointers'],roles['new_named_observations'])==(980,8,988,340,8);old_raw=gzip.decompress((data/'palantir-prior-reviews.json.gz').read_bytes());old=json.loads(old_raw);assert len(old['reviews'])==980 and pin(data/'palantir-prior-reviews.json.gz')==roles['prior_review_archive'] and pin(data/'palantir-prior-papers.json.gz')==roles['prior_papers_archive'] and byte_pin(old_raw)['sha256']==roles['before_reviews_sha256'];proof=json.loads((data/'cellphonedb-effective-catalog-validation.json').read_bytes());assert pin(data/'cellphonedb-effective-catalog-support.zip')==proof['archive'];files=unpack(data/'cellphonedb-effective-catalog-support.zip',proof)
 # The previous metadata proof binds unchanged papers/reviews in its predecessor snapshot.
 old_proof=json.loads((data/'cellphonedb-catalog-validation.json').read_bytes());old_files=unpack(data/'cellphonedb-catalog-support.zip',old_proof);assert old_files['catalog-snapshot/reviewed-uses.json']==old_raw and gzip.decompress((data/'palantir-prior-papers.json.gz').read_bytes())==old_files['catalog-snapshot/papers.json']
 for name,want in proof['source_pins'].items():
  assert byte_pin(files['current-source/'+name])==want
  verify_source(root,data,name,want['sha256'])
 raw=(root/'tools/literature/reviewed-uses.json').read_bytes()
 if raw==old_raw:
  assert reviews==old['reviews'];_verify_prior(root,data,old_raw,reviews);return
 current=json.loads(raw);draft_path=root/'tools/literature/trajectory-spatial-use-reviews.json';draft=json.loads(draft_path.read_bytes());assert pin(draft_path)==roles['draft_pin'] and pin(root/'tools/literature/curate_palantir_uses.py')['sha256']==roles['curator_sha256'] and pin(root/'tools/literature/palantir-named-software-registry.json')==roles['registry_extension'] and byte_pin(raw)['sha256']==roles['reviews_sha256'];assert {k:v for k,v in current.items() if k!='reviews'}=={k:v for k,v in old.items() if k!='reviews'} and current['reviews'][:980]==old['reviews'] and current['reviews'][980:]==[d['review'] for d in draft['reviews']] and reviews==current['reviews'] and len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==988 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==340;_verify_prior(root,data,old_raw,old['reviews']);verify_source(root,data,'tools/literature/reviewed-uses.json',byte_pin(old_raw)['sha256']);load_catalog_registry(root)

@contextlib.contextmanager
def current_catalog_review_context():
 saved=prior.prior.verify_review_extension
 try:
  prior.prior.verify_review_extension=verify_review_extension
  with prior.current_catalog_review_context():yield
 finally:prior.prior.verify_review_extension=saved

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
def cellphonedb_metadata(root,data):return _metadata('cellphonedb_metadata',root,data)

def palantir_metadata(root,data):
 from palantir_workflow_evidence import verify
 p=verify(emit=False);assert p['coordinate_workflow_adapter_admitted'];verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
 return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/PalantirWorkflowCli.java',implementation_scope='Original Palantir1.4.4/1.4.6 diffusion maps, trajectory and combined coordinate workflows with explicit float64 PCA/multiscale inputs, DataFrame versus coordinate-only AnnData interface, roots, terminal/waypoint policies, backend, scientific parameters and seeds. Native Java audits observed kernel equations, transition/eigenpair residuals, exact native multiscale coordinates, scaling, version-specific weights, Markov/absorption equations, projection, preclip entropy, original below0.01 clipping and complete TSV exports. Neighbors, ARPACK, RNG, graph/waypoint/root/terminal selection and trajectory fitting remain original upstream, not an independent refit. No expression normalization/HVG/PCA fitting, gene trends, unreported paper edition/options or whole-paper reproduction. P024 selects a maximum correlation over200 roots; P094 uses diffusion space upstream of scFates, not a demonstrated Palantir fate workflow. Read PALANTIR_WORKFLOW_DEVELOPMENT.md.',validation_note='Offline exact private setup uses48 pinned dependency archives and both original wheels over the unchanged project Python3.11.17 base. Original namespaces, sources and full before/after inventories are bound. Twenty-one original workflows cover both backends, alpha normalization, automatic eigengap/terminals, explicit waypoints, annotated AnnData and1.4.6 multiple roots. Complete native observations pass on WindowsJava21/25 and LinuxJava21. Eleven output corruptions and18 input/export/permission/timeout controls are rejected; a late source ARPACK error leaves the earlier case provisional. Whole-output, input/source/runtime rechecks precede atomic admission. Pure native multiscale API is separately validated; original neighbor search/trajectory fitting and biological calibration are not independently replicated. Original clipping can reduce row mass while entropy retains its preclip meaning.',source_roles_reviewed=2,related_source_roles_reviewed=6,source_role_coverage='palantir-role-coverage.json',project_adapter_available=True,canonical_adapter_available=False,development_candidate=p['artifact']['path'],development_candidate_sha256=p['artifact']['sha256'],development_cli_class='org.jlinalg.cli.PalantirWorkflowCli',runtime_setup_cli_class='org.jlinalg.cli.PalantirSetupCli',native_helpers=['src/main/java/org/jlinalg/embedding/PalantirMultiscale.java'],analysis_installs_software=False,independent_trajectory_refit=False,whole_paper_reproduced=False,whole_goal_complete=False)

PALANTIR_WARNING='''## Palantir: warnings and required actions

Use development **PalantirWorkflowCli**, separate **PalantirSetupCli** and **PALANTIR_WORKFLOW_DEVELOPMENT.md**. Require exit0 AND `analysis-status.json result_admitted=true`. Preserve the whole directory, original inputs/logs, data-only observations, native audit, manifest and process receipts. The canonical executable is unchanged.

**SKIP3: skip dependent analysis** when the exact permitted Linux x86_64 runtime, edition, base receipt, archives or installation/download permission is unavailable or forbidden. Export the kit and read SETUP.md. Prepare the exact project Python3.11.17 base separately if absent; no CellPhoneDB analysis is required. Install into NEW case-sensitive Palantir storage only with `--allow-install`; uncached pinned archives also need `--allow-download`, otherwise supply an exact cache. Preserve failed prefixes and all setup diagnostics. RUN installs nothing. Versions1.4.4 and1.4.6 are explicit, never inferred to be the unreported paper version.

**FAIL2: no result from the whole batch, including earlier successful cases.** Read WARNINGS.md, analysis-status.json, native-audit.json, original stdout/stderr and original-error.traceback. Correct the named input, identity, numeric, source, runtime, timeout or capacity cause and retry NEW paths. Never repair or partially match roots/terminals/waypoints, omit failures, replace nonfinite values or infer preprocessing/settings.

**Returned fate probabilities may sum below1.** Original probabilities below0.01 are clipped after entropy computation and are not renormalized. Read clipped-state/row-mass warnings; do not silently renormalize or recompute entropy. Explicit empty_fates=fail rejects missing fates; retain-pseudotime-only allows zero fate columns with a prominent warning that zero entropy is not evidence of differentiation. DataFrame annotated output retains cell-ID headers; AnnData uses annotations. Actual waypoint count can differ after source sampling/unions. Source versions use distinct RNG/weight behavior.

Supply scientifically justified float64 PCA/multiscale coordinates and all options. Native audits check observed equations and exports, not an independent neighbor search, graph construction, terminal selection or trajectory refit. P024's maximum-over200 root comparison is selected, not independent held-out accuracy; P094's scFates trees remain a separate missing implementation. SAW, Stereopy and DoRothEA are also separately catalogued without implementation credit. No whole-Palantir, biological or paper reproduction; the250-paper implementation goal remains active. Cell Ranger remains excluded. Supervision tracks owned processes and sampled wall/output limits, not hard OS CPU/memory containment.

'''
PALANTIR_ACTIONS='''<section id="palantir-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>Palantir: warnings and required actions</h2><p>Use development <code>PalantirWorkflowCli</code>, separate <code>PalantirSetupCli</code> and <code>PALANTIR_WORKFLOW_DEVELOPMENT.md</code>. Require exit0 and <code>result_admitted=true</code>. Read <code>WARNINGS.md</code>, <code>analysis-status.json</code>, <code>native-audit.json</code>, original stdout/stderr, <code>original-error.traceback</code>, manifests and process receipts. Preserve all originals and provisionals; the canonical executable is unchanged.</p><p><strong>SKIP3: skip dependent analysis.</strong> Use the exact permitted Linux x86_64 runtime,1.4.4 or1.4.6, project Python3.11.17 base receipt and pinned archives. Export the kit and read SETUP.md. Installation requires explicit permission flags <code>--allow-install</code> and, for uncached downloads, <code>--allow-download</code>; otherwise supply an exact cache or retain the skip. Prepare NEW case-sensitive storage and preserve failed prefixes/logs. No CellPhoneDB analysis is needed for the base. RUN installs nothing. Neither edition is inferred to be an unreported paper version.</p><p><strong>FAIL2: no result from the whole batch, including earlier successes.</strong> Correct the named input, identity, numeric, source, runtime, timeout or capacity cause and retry NEW paths. Never drop failures, partially match cell IDs, repair values or infer preprocessing/settings.</p><p><strong>Clipping and entropy differ:</strong> original probabilities below0.01 are clipped without renormalization after entropy computation, so returned row mass may be below1. Read the clipping warnings. <code>empty_fates=fail</code> rejects missing fates; <code>retain-pseudotime-only</code> permits zero fate columns, and zero entropy is not evidence of differentiation. Original DataFrame output retains terminal cell-ID headers; AnnData uses annotations. Actual waypoint counts reflect source sampling/unions. Both versions retain distinct RNG/weight behavior.</p><p>Supply explicit float64 PCA/multiscale coordinates, roots, terminals, waypoints and scientific options. Native audits do not independently refit trajectories. P024 selected a maximum correlation across200 roots; P094 used Palantir diffusion space upstream of separate scFates trees. scFates, SAW, Stereopy and DoRothEA remain missing. No whole-paper or biological reproduction is claimed; Cell Ranger remains excluded and the larger goal is active. Owned-process/time/output supervision is not hard OS CPU/memory containment.</p></section>'''
