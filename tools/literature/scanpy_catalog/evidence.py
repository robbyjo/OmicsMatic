"""Execute unchanged predecessor owners in exact checkouts and read immutable Scanpy qualifications."""
import json
import os
from pathlib import Path
import subprocess
import sys
import uuid
from enrichr_catalog import catalog as previous
from enrichr_catalog import evidence as old
from scanpy_catalog.model import META, DATA
from scanpy_catalog.coverage import encoded, require
from scanpy_source_roles import MissingSource
import scanpy_source_roles

ROOT = Path(__file__).resolve().parents[3]
BASE = '4677a851630130f2ff96bac6691e0b3a1b461574'
ENRICHR_STAGE = 'bd26330459c35c5ec6e5be904d349280bb353f6e'
pin, rawpin, git = old.pin, old.rawpin, old.git
READERS = ['verify_scanpy_preprocessing', 'verify_scanpy_workflow', 'verify_scanpy_pearson',
           'verify_scanpy_dispatch', 'verify_scanpy_hvg']
FOUNDATION = sorted(set(old.FOUNDATION + previous.OWN + [
    'tools/literature/scanpy_source_roles.py', 'tools/literature/scanpy_coverage.py',
    'tools/literature/scanpy_dispatch_transition.py', 'tools/literature/verify_enrichr_current.py'
] + ['tools/literature/' + name + '.py' for name in READERS]))


def baseline():
    before = {n: git('show', BASE + ':' + n) for n in META}
    proof = json.loads(git('show', BASE + ':' + previous.PROOF))
    require(proof['status'] == previous.STATUS and proof['after'] == {n: rawpin(b) for n,b in before.items()},
            'Actual Git baseline differs from completed Enrichr admission')
    return before


def predecessor(cache, pdf, author, before, out, checkout, coloc_checkout):
    if checkout is None or not Path(checkout).is_dir():
        raise MissingSource('Restore a separate exact clean Enrichr checkout at ' + ENRICHR_STAGE)
    if coloc_checkout is None or not Path(coloc_checkout).is_dir():
        raise MissingSource('Restore the separate exact clean COLOC predecessor checkout at ' + old.COLOC_STAGE)
    checkout = Path(checkout).resolve(); out = Path(out); out.mkdir(parents=True, exist_ok=False)
    path = ROOT / previous.PROOF
    require(path.read_bytes() == git('show', BASE + ':' + previous.PROOF), 'Original Enrichr proof changed')
    proof = json.loads(path.read_bytes())
    require(proof['after'] == {n: rawpin(b) for n,b in before.items()}, 'Enrichr after-stage differs')
    require(proof['source_code'] == {n: pin(ROOT/n) for n in previous.OWN}
            and proof['foundation_sources'] == previous.foundation(), 'Enrichr source foundation differs')
    for n,w in proof['evidence'].items(): require(pin(ROOT/n) == w, 'Changed retained Enrichr evidence: ' + n)
    def historical(*args):
        return subprocess.check_output(['git', '--no-replace-objects', *args], cwd=checkout)
    require(historical('rev-parse', 'HEAD').decode().strip() == ENRICHR_STAGE, 'Wrong historical Enrichr commit')
    require(not historical('status', '--porcelain=v1', '-z', '--untracked-files=all'), 'Historical Enrichr checkout must be clean')
    for n,b in before.items(): require((checkout/n).read_bytes() == b, 'Historical metadata differs: ' + n)
    for n in previous.OWN:
        require((checkout/n).read_bytes() == git('show', ENRICHR_STAGE + ':' + n), 'Changed historical Enrichr owner: ' + n)
    target = checkout/'build'/('scanpy-original-enrichr-owner-' + uuid.uuid4().hex)
    args = [sys.executable, '-B', '-m', 'enrichr_catalog.catalog', '--verify', '--primary-cache', str(Path(cache).resolve()),
            '--supplement-pdf', str(Path(pdf).resolve()), '--author-archive', str(Path(author).resolve()),
            '--historical-checkout', str(Path(coloc_checkout).resolve()), '--out', str(target)]
    process = subprocess.run(args, cwd=checkout, env=dict(os.environ, PYTHONPATH=str(checkout/'tools/literature')), capture_output=True)
    (out/'original.stdout').write_bytes(process.stdout); (out/'original.stderr').write_bytes(process.stderr)
    (out/'PROCESS.json').write_bytes(encoded(dict(exit_code=process.returncode, historical_commit=ENRICHR_STAGE,
        original_module='enrichr_catalog.catalog', source_overlay=False)))
    if process.returncode == 3:
        raise MissingSource('Historical original owner skipped a prerequisite: ' + process.stderr.decode(errors='replace')[-3000:])
    require(process.returncode == 0, 'Original Enrichr owner failed; inspect retained original.stderr')
    result = json.loads((target/'COMPLETED.json').read_bytes())
    require(result['status'] == previous.STATUS and result['new_scoped_integrations'] == 136
            and result['reviewed_uses'] == 1462 and result['current_manifest']['entries'] == 812,
            'Wrong original Enrichr disposition')
    require(not historical('status', '--porcelain=v1', '-z', '--untracked-files=all'), 'Historical Enrichr source changed during verification')
    (out/'ORIGINAL-COMPLETED.json').write_bytes(encoded(result))
    return dict(status='PASS_ORIGINAL_ENRICHR_OWNER_IN_EXACT_CLEAN_HISTORICAL_CHECKOUT',
        historical_commit=ENRICHR_STAGE, proof=pin(path), original_verifier_exit=0, original_result=result,
        historical_full_marker_audit=result['historical_full_marker_audit'],source_XML_identities_checked=250,
        current_metadata_before_stage_matched=True, original_scientific_programs_reexecuted=False,
        scientific_source_overlay_used=False, previous_fingerprints_refreshed=False)


def source_census(cache):
    name='docs/literature/scanpy-preprocessing-checks/CENSUS.json'
    path=ROOT/name
    require(path.read_bytes() == git('show', BASE + ':' + name), 'Frozen Scanpy census changed')
    census=scanpy_source_roles.audit(cache)
    require(census == json.loads(path.read_bytes()), 'Fresh 250-identity/22-role census differs')
    return census


def qualification():
    from verify_scanpy_preprocessing import verify as preprocessing
    from verify_scanpy_dispatch import verify as dispatch
    from verify_scanpy_hvg import verify as hvg
    reports={}
    for key,reader in [('preprocessing',preprocessing),('dispatch',dispatch),('hvg',hvg)]:
        reports[key]=reader()[0]
    # These two original readers expose a CLI only. Execute their unchanged entrypoints
    # against actual current files, retaining NEW outputs; do not replace lookups/functions.
    scratch=ROOT/'build'/('scanpy-catalog-qualification-'+uuid.uuid4().hex);scratch.mkdir()
    for key in ['workflow','pearson']:
        out=scratch/key
        process=subprocess.run([sys.executable,'-B',str(ROOT/'tools/literature'/('verify_scanpy_'+key+'.py')),
                                '--out',str(out)],cwd=ROOT,capture_output=True)
        (scratch/(key+'.stdout')).write_bytes(process.stdout);(scratch/(key+'.stderr')).write_bytes(process.stderr)
        if process.returncode==3:raise MissingSource('Restore the exact named Scanpy '+key+' evidence dependency: '+process.stderr.decode(errors='replace')[-1500:])
        require(process.returncode==0,'Scanpy '+key+' saved proof failed; preserve NEW qualification diagnostics')
        reports[key]=json.loads((out/'COMPLETED.json').read_bytes())
    require(reports['preprocessing']['original_reference_cases']==418 and reports['workflow']['original_array_annotation_comparisons']==58
            and reports['pearson']['original_cases']==720 and reports['dispatch']['tests_per_JVM']==62
            and reports['hvg']['original_cases']==2560,'Different qualified original populations')
    folders=['scanpy-'+key+'-checks' for key in ['preprocessing','workflow','pearson','dispatch','hvg']]+['scanpy-source-coverage']
    return dict(status='PASS_SCANPY_CURRENT_WORKFLOW_AND_EXPLICIT_NATIVE_COMPONENTS',
        normal_global_tests_per_JVM=62,native_preprocessing_tests_per_JVM=426,native_pearson_tests_per_JVM=735,
        native_hvg_tests_per_JVM_per_producer=2574,qualified_upstream_operations=15,development_allowlisted_operations=29,
        workflow_original_array_annotation_comparisons=58,global_original_array_comparisons=52,
        historical_author_package_parity=False,author_runtime_executed=False,new_family_credit=0,
        readbacks=reports,artifacts={p.relative_to(ROOT).as_posix():pin(p) for folder in folders
                                   for p in sorted((ROOT/'docs/literature'/folder).iterdir()) if p.is_file()},
        fresh_JVM_execution=False,fresh_upstream_execution=False,whole_tool_qualified=False,whole_paper_reproduced=False)
