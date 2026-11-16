#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prove replay uses captured inputs after the caller's original paths disappear."""
from pathlib import Path
import argparse,importlib.util,shutil,traceback

spec=importlib.util.spec_from_file_location('original_adapter_qualification',Path(__file__).with_name('qualify_adapter.py'))
q=importlib.util.module_from_spec(spec);spec.loader.exec_module(q)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--reference',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();s=None
    try:
        a.suite='captured-input-replay';s=q.Suite(a);own_sha=q.sha(__file__);(s.out/'as-run-qualify_capture.py').write_bytes(Path(__file__).read_bytes());reference=a.reference.resolve()/'v02-lasso';external=s.out/'caller-inputs';external.mkdir()
        for path in reference.iterdir():
            if path.name in ['annotation.tsv','scores.tsv'] or path.name.startswith('features.'):shutil.copyfile(path,external/path.name)
        before={p.name:q.sha(p) for p in external.iterdir()};q.save(s.out/'CALLER-INPUTS-BEFORE.json',before)
        bundle=s.request('captured',dict(edition='0.2',task='fit',gene_annotation=str(external/'annotation.tsv'),feature_prefix=str(external/'features'),feature_chunks='5',target_type='custom',scores=str(external/'scores.tsv'),method='lasso',p_cutoff='1.01'))
        retained=s.out/'caller-inputs-retained-at-different-path'
        assert external.resolve().parent==s.out and retained.resolve().parent==s.out and not retained.exists(),'Owned directory move must stay within this new qualification output'
        assert before=={p.name:q.sha(p) for p in external.iterdir()};external.rename(retained)
        assert not external.exists() and before=={p.name:q.sha(p) for p in retained.iterdir()}
        q.save(s.out/'OWNED-INPUT-PATH-CHANGE.json',dict(original_path=str(external),retained_path=str(retained),original_paths_unavailable=True,input_bytes_preserved=True,scope='Only the newly created synthetic caller-input directory inside this qualification root was renamed. Shared references and installed software were never moved.'))
        s.replay('replay-with-original-paths-unavailable',bundle)
        assert q.sha(__file__)==own_sha and before=={p.name:q.sha(p) for p in retained.iterdir()}
        s.complete(dict(target_genes=600,prediction_genes=603,original_input_paths_unavailable_during_replay=True,all_original_input_bytes_preserved=True,genuine_source_recomputation=True,scientific_results_byte_identical=True));return 0
    except Exception as error:
        if s is not None:q.save(s.out/'FAILURE.json',dict(status='FAIL_CAPTURED_INPUT_REPLAY_QUALIFICATION',reason=repr(error),traceback=traceback.format_exc(),processes=s.records))
        traceback.print_exc();return 2

if __name__=='__main__':raise SystemExit(main())
