# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Clarify native NORMALIZE has no VERIFY; preserve the complete first main checkpoint."""
from pathlib import Path
import json,shutil
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,unpack

def main():
    folder=ROOT/'docs/literature/deseq2-tools-development';target=folder/'VALIDATION.json';prior=target.read_bytes();proof=json.loads(prior);archive=folder/'controls.zip';assert pin(archive)==proof['archive'];files=unpack(archive,proof)
    name='docs/literature/DESEQ2_TOOLS.md';guide=ROOT/name;old=guide.read_bytes();assert pin(guide)==proof['source_pins'][name]
    original=b'Require the analysis completion marker, matching hashes and a successful VERIFY; inspect retained source warnings and nonfinite values.'
    replacement=b'For deseq2-tools ANALYZE, require its completion marker, matching hashes and a successful VERIFY; inspect retained source warnings and nonfinite values. Native deseq2-normalize NORMALIZE has no VERIFY operation: inspect its completion/status, manifest, size factors and normalized-count warnings directly; it does not perform a differential fit.'
    assert old.count(original)==1;new=old.replace(original,replacement)
    before=ROOT/'build/literature-review/deseq2-runtime/main-recovery-note-refresh-1';assert not before.exists();before.mkdir()
    for p in [target,archive,guide]:shutil.copyfile(p,before/p.name)
    guide.write_bytes(new);proof['source_pins'][name]=pin(guide);files['current-source/'+name]=new
    script=Path(__file__);scriptname=script.relative_to(ROOT).as_posix();proof['source_pins'][scriptname]=pin(script);files['current-source/'+scriptname]=script.read_bytes()
    refresh=dict(status='passed-one-literal-documentation-only-recovery-clarification',old_guide=pin(before/guide.name),new_guide=pin(guide),prior_validation=pin(before/target.name),prior_archive=pin(before/archive.name),only_modified_existing_source=name,all_scientific_sources_and_captured_results_unchanged=True,reason='Native NORMALIZE has no VERIFY operation; distinguish its recovery from original-runtime ANALYZE.')
    save(before/'REFRESH.json',refresh);files['documentation-history/first-VALIDATION.json']=prior;files['documentation-history/first-DESEQ2_TOOLS.md']=old;files['documentation-history/REFRESH.json']=(json.dumps(refresh,indent=2)+'\n').encode('utf8')
    staged=before/'refreshed-controls.zip';layout=pack(staged,files);shutil.copyfile(staged,archive);proof.update(layout);proof['archive']=pin(archive);proof['documentation_refresh']=refresh
    assert all(pin(ROOT/n)==v for n,v in proof['source_pins'].items());save(target,proof);unpack(archive,proof)
    print('Clarified native NORMALIZE recovery; first archive/guide retained, scientific sources/results unchanged')

if __name__=='__main__':main()
