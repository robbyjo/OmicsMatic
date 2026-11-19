"""Exact historical-owner execution and current Enrichr source/qualification binding."""
import gzip
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid
from coloc_catalog import catalog as previous
from coloc_catalog import evidence as old
from enrichr_catalog.model import META, DATA, NEW_NAMES
from enrichr_catalog.coverage import encoded, require
from enrichr_source_roles import MissingSource
import enrichr_source_roles

ROOT = Path(__file__).resolve().parents[3]
BASE = 'eaccb1ba6026f6534106941acb82b672350e2606'
COLOC_STAGE = '29486e3f7bf83f534324f21c2d2bd917ad35b134'
pin, rawpin, git = old.pin, old.rawpin, old.git
FOUNDATION = sorted(set(old.FOUNDATION + previous.OWN + [
    'tools/literature/enrichr_source_roles.py', 'tools/literature/enrichr_workflow_transition.py',
    'tools/literature/verify_enrichr_component.py', 'tools/literature/verify_enrichr_workflow.py']))


def baseline():
    before = {n: git('show', BASE + ':' + n) for n in META}
    proof = json.loads(git('show', BASE + ':' + previous.PROOF))
    require(proof['status'] == previous.STATUS and proof['after'] == {n: rawpin(b) for n,b in before.items()},
            'Actual Git baseline differs from completed COLOC admission')
    return before


def predecessor(cache, pdf, author, before, out, checkout):
    if checkout is None or not Path(checkout).is_dir():
        raise MissingSource('Restore a separate exact clean historical COLOC checkout at ' + COLOC_STAGE)
    checkout = Path(checkout).resolve(); out = Path(out); out.mkdir(parents=True, exist_ok=False)
    path = ROOT / previous.PROOF
    require(path.read_bytes() == git('show', BASE + ':' + previous.PROOF), 'Original COLOC proof changed')
    proof = json.loads(path.read_bytes())
    require(proof['after'] == {n: rawpin(b) for n,b in before.items()}, 'COLOC after-stage differs')
    require(proof['source_code'] == {n: pin(ROOT/n) for n in previous.OWN}
            and proof['foundation_sources'] == previous.foundation(), 'COLOC source foundation differs')
    for n,w in proof['evidence'].items(): require(pin(ROOT/n) == w, 'Changed retained COLOC evidence: ' + n)
    def historical(*args):
        return subprocess.check_output(['git', '--no-replace-objects', *args], cwd=checkout)
    require(historical('rev-parse', 'HEAD').decode().strip() == COLOC_STAGE, 'Wrong historical checkout commit')
    require(not historical('status', '--porcelain=v1', '-z', '--untracked-files=all'), 'Historical checkout must be clean')
    for n,b in before.items(): require((checkout/n).read_bytes() == b, 'Historical metadata differs: ' + n)
    for n in previous.OWN:
        require((checkout/n).read_bytes() == git('show', COLOC_STAGE + ':' + n), 'Changed historical owner: ' + n)
    target = checkout / 'build' / ('enrichr-original-owner-' + uuid.uuid4().hex)
    args = [sys.executable, '-B', '-m', 'coloc_catalog.catalog', '--verify', '--primary-cache', str(Path(cache).resolve()),
            '--supplement-pdf', str(Path(pdf).resolve()), '--author-archive', str(Path(author).resolve()), '--out', str(target)]
    env = dict(os.environ, PYTHONPATH=str(checkout/'tools/literature'))
    # The original process resolves its own actual files and Git context. No lookup is replaced.
    process = subprocess.run(args, cwd=checkout, env=env, capture_output=True)
    (out/'original.stdout').write_bytes(process.stdout); (out/'original.stderr').write_bytes(process.stderr)
    (out/'PROCESS.json').write_bytes(encoded(dict(exit_code=process.returncode, historical_commit=COLOC_STAGE,
                                                original_module='coloc_catalog.catalog', source_overlay=False)))
    if process.returncode == 3:
        raise MissingSource('Historical original owner skipped a prerequisite: ' + process.stderr.decode(errors='replace')[-3000:])
    require(process.returncode == 0, 'Historical original owner failed; inspect retained original.stderr')
    result = json.loads((target/'COMPLETED.json').read_bytes())
    require(result['status'] == previous.STATUS and result['new_scoped_integrations'] == 135
            and result['reviewed_uses'] == 1439 and result['current_manifest']['entries'] == 809,
            'Wrong historical owner disposition')
    require(not historical('status', '--porcelain=v1', '-z', '--untracked-files=all'), 'Historical source changed during verification')
    (out/'ORIGINAL-COMPLETED.json').write_bytes(encoded(result))
    return dict(status='PASS_ORIGINAL_COLOC_OWNER_IN_EXACT_CLEAN_HISTORICAL_CHECKOUT',
                historical_commit=COLOC_STAGE, proof=pin(path), original_verifier_exit=0, original_result=result,
                historical_full_marker_audit=result['historical_full_marker_audit'],
                source_XML_identities_checked=250, current_metadata_before_stage_matched=True,
                original_scientific_programs_reexecuted=False, scientific_source_overlay_used=False,
                previous_fingerprints_refreshed=False)


def source_census(cache):
    path = ROOT/'docs/literature/enrichr-component-checks/CENSUS.json'
    require(path.read_bytes() == git('show', BASE + ':docs/literature/enrichr-component-checks/CENSUS.json'), 'Frozen census changed')
    census = enrichr_source_roles.audit(cache)
    require(census == json.loads(path.read_bytes()), 'Fresh 250-identity/25-role census differs')
    # These observations were absent from the old extraction. Preserve the selected literal spelling.
    from collect_omics_review import paragraphs
    import xml.etree.ElementTree as ET
    for r in census['roles']:
        if r['paper_id'] not in NEW_NAMES: continue
        with gzip.open(Path(cache)/(r['pmcid']+'.xml.gz'), 'rb') as stream: root = ET.fromstring(stream.read())
        blocks = {p['paragraph_index']: p for p in paragraphs(root)}
        text = blocks[r['anchors'][0]['paragraph_index']]['text']
        require(re.search(re.escape(NEW_NAMES[r['paper_id']]), text) is not None, 'Different literal new observation name')
    return census


def qualification():
    import verify_enrichr_current
    result = verify_enrichr_current.verify()
    folder = ROOT/'docs/literature/enrichr-current-checks'
    return dict(status='PASS_ENRICHR_NORMAL_SOURCE_BUILD_WORKFLOW_AND_EXAMPLES',
                normal_tests_per_JVM=42, live_source_rows_compared=600, historical_client_parity=False,
                new_family_credit=0, readback=result,
                artifacts={p.relative_to(ROOT).as_posix(): pin(p) for p in sorted(folder.iterdir()) if p.is_file()},
                fresh_JVM_execution=False, fresh_service_execution=False, whole_tool_qualified=False,
                whole_paper_reproduced=False)
