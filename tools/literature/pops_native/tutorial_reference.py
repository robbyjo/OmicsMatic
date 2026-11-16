#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the exact original PoPS tutorial with its complete supplied gene/feature data."""
from pathlib import Path
import argparse, hashlib, json, os, resource, subprocess, sys, time
import numpy as np
import pandas as pd
import scipy
import sklearn


def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024*1024), b''):
            h.update(block)
    return h.hexdigest()


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--upstream', type=Path, required=True)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    upstream, out = args.upstream.resolve(), args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    manifest = json.loads(args.manifest.read_text())
    assert manifest['commit'] == '76eb86cba10254490003c8f4dc7ff5ce492d3667'
    assert sys.version_info[:2] == (3, 8)
    versions = dict(numpy=np.__version__, scipy=scipy.__version__, pandas=pd.__version__, sklearn=sklearn.__version__)
    assert versions == dict(numpy='1.19.5', scipy='1.5.2', pandas='1.0.5', sklearn='0.24.2')
    for name, expected in manifest['files'].items():
        assert sha(upstream / name) == expected['sha256'], name
    save(out / 'INPUT-IDENTITY.json', dict(manifest=manifest, versions=versions, source_modified=False,
                                         dataset='Complete official public schizophrenia tutorial; no claim of biological replication'))
    command = [sys.executable, str(upstream / 'pops.py'), '--gene_annot_path', str(upstream / 'example/data/utils/gene_annot_jun10.txt'),
               '--feature_mat_prefix', str(upstream / 'example/data/features_munged/pops_features'), '--num_feature_chunks', '2',
               '--magma_prefix', str(upstream / 'example/data/magma_scores/PASS_Schizophrenia'),
               '--control_features_path', str(upstream / 'example/data/utils/features_jul17_control.txt'),
               '--out_prefix', str(out / 'original'), '--save_matrix_files', '--verbose']
    # Original defaults and all data are retained. Additional flags only save
    # internal matrices and diagnostic messages. The address-space bound is 16 GiB.
    resource.setrlimit(resource.RLIMIT_AS, (16 * 2**30, 16 * 2**30))
    env = os.environ.copy()
    env.update(OPENBLAS_NUM_THREADS='1', OMP_NUM_THREADS='1', PYTHONHASHSEED='0', PYTHONDONTWRITEBYTECODE='1')
    started = time.monotonic()
    with (out / 'original.stdout').open('xb') as so, (out / 'original.stderr').open('xb') as se:
        process = subprocess.run(command, stdout=so, stderr=se, env=env, timeout=1800)
    record = dict(command=command, exit_code=process.returncode, seconds=round(time.monotonic()-started, 3), address_space_limit_bytes=16*2**30)
    save(out / 'PROCESS.json', record)
    if process.returncode:
        save(out / 'FAILURE.json', dict(status='FAIL_ORIGINAL_POPS_TUTORIAL', process=record,
                                       action='Retain raw logs and inputs; resolve the recorded runtime/resource failure and retry a new path. Do not trim genes or features to obtain a pass.'))
        return 2
    for name, expected in manifest['files'].items():
        assert sha(upstream / name) == expected['sha256'], name
    pred = pd.read_csv(out / 'original.preds', sep='\t')
    train = pd.read_csv(out / 'original.traindata', sep='\t')
    marg = pd.read_csv(out / 'original.marginals', sep='\t')
    coefs = pd.read_csv(out / 'original.coefs', sep='\t', index_col=0)
    assert pred.shape[0] == 18383 and marg.shape[0] == 508
    status = dict(status='PASS_COMPLETE_UNMODIFIED_ORIGINAL_POPS_TUTORIAL', prediction_genes=len(pred),
                  magma_target_genes=int(pred.Y.notnull().sum()), training_genes=len(train), input_features=len(marg),
                  selected_features=int(marg.selected.sum()), alpha=float(coefs.loc['SELECTED_CV_ALPHA', 'beta']),
                  best_cv_score=float(coefs.loc['BEST_CV_SCORE', 'beta']), process=record,
                  default_scientific_settings=True, no_gene_or_feature_subsampling=True,
                  additional_observation_flags=['--save_matrix_files', '--verbose'], new_catalog_credit=0,
                  native_comparison_performed=False, whole_goal_complete=False)
    save(out / 'STATUS.json', status)
    print(json.dumps(status))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
