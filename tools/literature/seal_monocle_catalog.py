# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal twelve catalog reviews, software/version recovery and complete original Cicero readiness."""
import json
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack


def main():
    data=ROOT/'docs/literature/omics-2023-2026';base=ROOT/'build/literature-review/monocle-runtime';archive=data/'monocle-catalog-support.zip';target=data/'monocle-catalog-validation.json';assert not archive.exists() and not target.exists()
    roles=json.loads((data/'monocle-role-coverage.json').read_bytes());assert roles['applied'] and roles['total_reviews']==917
    audit=json.loads((data/'monocle-source-audit.json').read_bytes());assert (audit['source_xml_hashes_verified'],audit['tool_observation_pointers_verified'])==(250,2449)
    scientific=json.loads((base/'cicero-readiness-probe-2/RESULT.json').read_bytes());assert scientific['status']=='COMPLETED_ORIGINAL_CICERO_COACCESSIBILITY_CONTROL' and scientific['connection_rows']==2172 and scientific['unresolved_NA']==4 and scientific['libraries_unchanged']
    ui=base/'catalog-ui-3';check=json.loads((ui/'COMPLETED.json').read_bytes());visual=json.loads((ui/'VISUAL-REVIEW.json').read_bytes());assert check['page']==visual['page']==pin(ROOT/'site/literature-review.html') and visual['desktop_and_mobile_reviewed']
    files={}
    def collect(folder):
        for path in folder.rglob('*'):
            if path.is_file():files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for name in ['version-history-intake-1','version-history-intake-2','cicero-dependency-probe-1','cicero-readiness-probe-1','cicero-readiness-probe-2','catalog-ui-1','catalog-ui-2','catalog-ui-3','catalog-source-intake-1']:collect(base/name)
    for name in ['catalog-review-preparation-2','catalog-curation-dry-1','catalog-curation-1','named-recovery-intake-1']:
        for path in (base/name).iterdir():
            if path.is_file() and (path.name in ['COMPLETED.json'] or path.name.startswith('before-')):files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    # Preserve complete open-source editions; restricted py-monocle code/archive
    # stays private. Its immutable descriptor, commit and license are sufficient
    # to audit catalog naming/licensing without distributing the implementation.
    related=base/'related-source-intake-1'
    for path in related.rglob('*'):
        if not path.is_file():continue
        relative=path.relative_to(related)
        if relative.parts[0]=='py-monocle' and (relative.as_posix()=='py-monocle/source.tar.gz' or (relative.parts[1]=='source' and relative.as_posix()!='py-monocle/source/LICENSE')):continue
        files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    setup=base/'cicero-reference-setup-1'
    for path in setup.rglob('*'):
        if path.is_file() and 'R-lib' not in path.relative_to(setup).parts and path.suffix not in ['.zip','.gz']:files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    # Before/after inventories are regular JSON and remain complete.
    snapshots=['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','monocle-role-coverage.json','monocle-source-audit.json','monocle-prior-reviews.json.gz','monocle-reviews-shared-source-compatibility.json','monocle-render-shared-source-compatibility.json']
    for n in snapshots:files['catalog-snapshot/'+n]=(data/n).read_bytes()
    files['catalog-snapshot/reviewed-uses.json']=(ROOT/'tools/literature/reviewed-uses.json').read_bytes()
    for n in ['literature-review.html','literature-warnings.html']:files['site-snapshot/'+n]=(ROOT/'site'/n).read_bytes()
    names=['recover_monocle_version_sources.py','intake_monocle_related_sources.py','prepare_monocle_catalog_reviews.py','curate_monocle_catalog_uses.py','monocle_catalog_context.py','monocle-catalog-use-reviews.json','monocle-named-software-registry.json','audit_monocle_current_catalog.py','verify_monocle_review_context.py','probe_cicero_dependencies.py','install_cicero_reference.R','setup_cicero_reference.py','probe_cicero_reference.R','probe_cicero_reference.py','check_monocle_review_ui.cjs','validate_monocle_catalog_ui.py','seal_monocle_catalog.py','verify_monocle_catalog.py','render_review.py','check_review_ui.cjs','reviewed-uses.json']
    sources=['tools/literature/'+n for n in names]+['docs/literature/MONOCLE_SOURCE_REVIEW.md','docs/literature/omics-2023-2026/monocle-render-shared-source-compatibility.json','docs/literature/omics-2023-2026/monocle-reviews-shared-source-compatibility.json']
    frozen=['collect_omics_review.py','tool-registry.json','audit_current_omics_catalog.py','meme_discovery_catalog.py','meme_discovery_current_catalog.py','meme_discovery_provider_compatibility.py','verify_meme_discovery_catalog.py','verify_meme_discovery_current_catalog.py','chipseeker_catalog.py','verify_chipseeker_catalog.py','catalog_source_integrity.py','catalog_fixed_cohort.py']
    for n in frozen:
        relative='tools/literature/'+n;assert (ROOT/relative).read_bytes()==subprocess.check_output(['git','show','HEAD:'+relative],cwd=ROOT);sources.append(relative)
    pins={n:pin(ROOT/n) for n in sorted(sources)};files.update({'current-source/'+n:(ROOT/n).read_bytes() for n in pins})
    layout=pack(archive,files);parents={n:pin(ROOT/n) for n in ['docs/literature/monocle2-differential-development/VALIDATION.json','docs/literature/monocle-tools-development/VALIDATION.json']}
    save(target,dict(schema_version=1,status='passed-Monocle-related-catalog-source-and-original-Cicero-readiness',archive=pin(archive),source_pins=pins,frozen_source_names=['tools/literature/'+n for n in frozen],parent_proofs=parents,
        papers=250,named_tools=389,observations=2449,reviewed_uses=917,prior_reviews_preserved=905,supporting_pointers=309,new_named_tools=['cicero','py-monocle'],new_observations=3,source_roles=12,
        native_integrations=16,upstream_integrations=58,new_scoped_integrations=74,missing_integrations=256,original_Cicero_connections=2172,unresolved_Cicero_NA=4,Cicero_library_files_unchanged=33277,
        general_browser_checks=299,scoped_browser_checks=5,ui_prefix=ui.relative_to(ROOT).as_posix()+'/',restricted_python_source_distributed=False,py_monocle_runtime_executed=False,
        scientific_sources_or_frozen_verifiers_changed=False,implementation_promoted=False,canonical_executable_unchanged=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar'),whole_paper_reproduced=False,whole_goal_complete=False,pushed=False,deployed=False,**layout))
    print('Sealed',len(files),'logical source/catalog/readiness files; restricted Python implementation remains private')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source/readiness bindings')
    main()
