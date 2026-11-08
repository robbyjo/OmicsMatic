# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify observation does not change the original subsampling kernel under its own legitimate constructor seed; do not change the full method's entropy behavior."""
import json,sys
from pathlib import Path
sys.path.insert(0,str(Path(sys.argv[2]).resolve()))
from codec import clean,save
from worker import ReturnObserver
import numpy as np
from cellphonedb.src.core.utils.subsampler import Subsampler
from cellphonedb.src.core.methods import cpdb_statistical_analysis_helper as helper
from cellphonedb.utils import db_utils,file_utils


def main():
    root=Path(sys.argv[1]);home=root/'seeded-sampling-kernel';home.mkdir();request=json.loads((root/'request.json').read_bytes());case=next(c for c in request['analyses'] if c['id']=='statistical-subsampling-original-geometry');unused,genes,unused,unused,synonyms,unused=db_utils.get_interactions_genes_complex(case['database']['path']);counts,meta,unused,unused,unused=file_utils.get_user_files(counts=case['counts']['path'],meta_fp=case['metadata']['path'],gene_synonym2gene_name=synonyms,counts_data=case['counts_data']);mapped,unused=helper.add_multidata_and_means_to_counts(counts,genes,case['counts_data']);sampling=case['statistical']['subsampling'];first=Subsampler(log=sampling['log'],num_pc=sampling['pcs'],num_cells=sampling['cells'],verbose=False,debug_seed=7331);plain=first.subsample(mapped.copy(deep=True));first_rng=clean(np.random.get_state());observer=ReturnObserver(home)
    try:
        observer.enter();second=Subsampler(log=sampling['log'],num_pc=sampling['pcs'],num_cells=sampling['cells'],verbose=False,debug_seed=7331);observed=second.subsample(mapped.copy(deep=True));second_rng=clean(np.random.get_state())
    finally:observer.close()
    assert plain.equals(observed) and first_rng==second_rng and len(plain.columns)==sampling['cells'];save(home/'uninstrumented.json',clean(plain));save(home/'observed.json',clean(observed));save(home/'COMPLETED.json',dict(status='PASS_UNCHANGED_SEEDED_ORIGINAL_SUBSAMPLER_KERNEL_OBSERVATION_ONLY',constructor_seed=7331,complete_cell_axes_and_values_exact=True,complete_rng_state_exact=True,full_method_entropy_seed_replaced=False,namespace_patched=False,pickle_deserialized=False));print('PASS_UNCHANGED_SEEDED_ORIGINAL_SUBSAMPLER_KERNEL_OBSERVATION_ONLY')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original seeded-kernel controls')
    main()
