# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal the original counting workflow, preserving all earlier source/failure observations and unrelated work."""
import argparse,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out==ROOT/'docs/literature/rsubread-execution-development' and not out.exists();out.mkdir(parents=True)
    base=ROOT/'build/literature-review/rsubread-runtime';files={};sources=set();names=['runtime-build-1','runtime-build-2','runtime-build-3','runtime-build-25-1','workflow-build-1','workflow-build-2','workflow-build-3','workflow-build-4','workflow-build-25-1','runtime-binding-tests-1','runtime-binding-tests-2','runtime-binding-tests-3','runtime-package-exports-1','workflow-public-1','workflow-public-2','workflow-public-3','workflow-public-25-1','workflow-portability-1','workflow-portability-25-1','workflow-failures-1','workflow-failures-2','workflow-failures-25-1','process-controls-1','process-controls-25-1','workflow-documentation-1']
    historical={'runtime-build-1','runtime-build-2','workflow-build-1','workflow-build-2','workflow-build-3','runtime-binding-tests-1','runtime-binding-tests-2','workflow-public-1','workflow-public-2','workflow-failures-1'}
    for name in names:
        home=base/name;proof=json.loads((home/'COMPLETED.json').read_bytes())
        if name not in historical:assert proof['status'].startswith('passed-')
        for path in home.rglob('*'):
            if not path.is_file():continue
            rel=path.relative_to(home);parts=rel.parts
            if any(part.endswith('-fixture') for part in parts):continue
            if any(part in ['R-lib','corresponding-original-source','private-runtime'] for part in parts):continue
            assert not path.is_symlink();files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
        if name not in historical:sources.update(proof.get('source_pins',{}))
    # Every current executable/profile/worker and helper is source-bound; old snapshots remain historical.
    sources.update(q.relative_to(ROOT).as_posix() for q in (ROOT/'src/main/java/org/jlinalg/cli').glob('Rsubread*.java'))
    sources.add('src/main/java/org/jlinalg/cli/SimpleJson.java')
    sources.update(q.relative_to(ROOT).as_posix() for q in (ROOT/'src/main/resources/org/jlinalg/omics/rsubread-workflow-reference').rglob('*') if q.is_file())
    sources.update(q.relative_to(ROOT).as_posix() for q in (ROOT/'src/main/resources/org/jlinalg/omics/rsubread-workflow-reference-v2').rglob('*') if q.is_file())
    sources.update(['src/main/resources/org/jlinalg/omics/rsubread_runtime_guard.R','src/main/resources/org/jlinalg/omics/rsubread_count_worker.R','src/main/resources/org/jlinalg/omics/rsubread_result_wire.R','tools/literature/rsubread_execution_evidence.py','tools/literature/seal_rsubread_execution.py','docs/literature/RSUBREAD_WORKFLOWS.md'])
    for name in sources:files['source/'+name]=(ROOT/name).read_bytes()
    from rsubread_result_evidence import verify as prior_verify
    prior,oldfiles=prior_verify(False);files['prior-result/VALIDATION.json']=(ROOT/'docs/literature/rsubread-result-development/VALIDATION.json').read_bytes();files['prior-result/rsubread-result-values-api.jar']=(ROOT/'docs/literature/rsubread-result-development/rsubread-result-values-api.jar').read_bytes()
    refs=sorted(n for n in oldfiles if n.endswith('/complete-original-result.rds'))
    assert len(refs)==50
    for name in refs:
        files[name]=oldfiles[name];readable=name[:-4]+'.R'
        if readable in oldfiles:files[readable]=oldfiles[readable]
    checkpoint=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-rsubread-results-1/selection.json').read_bytes());assert all(pin(ROOT/n)==v for n,v in checkpoint['preserved'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==checkpoint['canonical']
    catalog={}
    for name in ['docs/literature/omics-2023-2026/papers.json','docs/literature/omics-2023-2026/tool-inventory.json','docs/literature/omics-2023-2026/summary.json','tools/literature/reviewed-uses.json']:
        raw=(ROOT/name).read_bytes();catalog[name]=byte_pin(raw);files['catalog-snapshot/'+name]=raw
    reviews=json.loads(files['catalog-snapshot/tools/literature/reviewed-uses.json'])['reviews'];assert len(reviews)==1000 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==347
    artifacts={};contents={}
    for label,name,filename in [('javac21','workflow-build-4','rsubread-workflow-development.jar'),('javac25','workflow-build-25-1','rsubread-workflow-javac25-development.jar')]:
        build=json.loads((base/name/'COMPLETED.json').read_bytes());raw=(ROOT/build['artifact']['path']).read_bytes();assert byte_pin(raw)=={k:build['artifact'][k] for k in ['bytes','sha256']};(out/filename).write_bytes(raw);artifacts[label]=dict(file=filename,**byte_pin(raw),built_artifact=build['artifact'])
        with zipfile.ZipFile(ROOT/build['artifact']['path']) as z:contents[label]={n:z.read(n) for n in z.namelist()}
    differing=sorted(n for n in contents['javac21'] if contents['javac21'][n]!=contents['javac25'][n]);assert len(differing)==4 and all(n.endswith('.class') for n in differing)
    archive=out/'controls.zip';blobs={byte_pin(raw)['sha256']:raw for raw in files.values()}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for digest,raw in sorted(blobs.items()):info=zipfile.ZipInfo('blobs/'+digest,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    proof=dict(schema_version=1,status='passed-source-bound-original-Rsubread-bulk-counting-workflow',declared_counting_workflow_available=True,implementation_credit=False,source_pins={n:pin(ROOT/n) for n in sorted(sources)},archive=dict(file=archive.name,**pin(archive)),files={n:byte_pin(raw) for n,raw in sorted(files.items())},artifacts=artifacts,artifact=dict(path=(out/artifacts['javac21']['file']).relative_to(ROOT).as_posix(),**{k:artifacts['javac21'][k] for k in ['bytes','sha256']}),compiler_variants_byte_identical=False,compiler_variant_differing_entries=differing,current_artifact_original_comparisons_per_compiler=58,beginner_original_execution=1,original_comparison_scope='50 complete separate-original comparisons under WindowsJVM21 plus eight under WindowsJVM25 for each compiler; complete target differences have explicit ledgers',separate_original_reference_objects=refs,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False,scientific_source_exception_added=False,independent_read_counting=False,independent_junction_counting=False,analysis_installs_software=False,paper_binary_identified=False,source_role_drafts_applied=False,canonical_executable_unchanged_at_checkpoint=checkpoint['canonical'],unrelated_files_preserved_at_checkpoint=18,catalog_snapshot=dict(files=catalog,papers=250,reviewed_roles=1000,supporting_pointers=347,new_scoped_integrations=89,including_existing=92,native=17,upstream=72,missing=252),runtime_export_scope='Exact original GPL package and complete corresponding source are losslessly reproducible from the earlier matrix evidence; R home files are not redistributed or installed. Cloned package mutation fixtures are represented by requests/receipts and current known-population checks, not duplicated runtime trees.',verification_scope='SHA-256/source/artifact and recorded complete-population relationships, not a signature or independent execution authentication. Default verification does not launch Java/R, refit or recount. Public VERIFY performs frozen native structural replay. Catalog snapshot is historical; no new availability credit is assigned here.')
    save(out/'VALIDATION.json',proof);(out/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    (out/'README.md').write_bytes(b'# Original Rsubread bulk counting development\n\nRead ../RSUBREAD_WORKFLOWS.md first. Use rsubread-workflow-development.jar for the documented entry points. The separate javac25 artifact has independently tested behavior; its new CLI bytecode is not byte-identical to javac21. All14 prior native value classes and R resources remain unchanged.\n\nRequire exit0 AND root COMPLETED.json analysis_admitted=true. Failures/skips/cancellation admit no earlier provisional case. Retain original objects, inputs and complete diagnostics. Analysis installs nothing; private software export requires explicit permission. Original Windows profiles are declared explicitly; Linux execution/binding skips.\n\ncontrols.zip is a lossless content-addressed archive. VALIDATION.json maps every logical file to a SHA-256 blob. It retains both compiler variants, complete public original result objects and comparison ledgers, actual skips/failures/cancellation/process evidence, raw source/build snapshots, earlier rejected attempts and beginner examples. Original RDS files retain details absent from the scalar wire. Keep earlier matrix/result evidence for original software/source/runtime history and licensed package export.\n\npython tools/literature/rsubread_execution_evidence.py checks current sources/artifacts and recorded relationships without starting Java/R. --archive-only checks historical integrity without current-source/runtime claims. SHA-256 is integrity, not a signature or execution authentication. Native annotation checks do not independently parse the source GTF, recount reads or prove biological junction annotations. R may coerce numeric/logical-looking GeneIDs and literal NA; preserve raw inputs and the original R objects. Double-storage GeneID interpretation remains unsupported.\n\nCatalog/primary-use updates are a separate milestone: this proof assigns no availability credit and does not complete the250-paper goal. Cell Ranger remains excluded.\n')
    from rsubread_execution_evidence import verify
    verify();assert all(pin(ROOT/n)==v for n,v in checkpoint['preserved'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==checkpoint['canonical']
    print('Sealed original counting execution:',len(files),'logical files,',len(blobs),'unique blobs,',archive.stat().st_size,'archive bytes; catalog/canonical/unrelated18 unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable current-source, historical-failure and unrelated-work preservation checks')
    main()
