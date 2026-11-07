# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the unchanged full source cohort and exact scoped provider derivations."""
import gzip
import hashlib
import json

BASELINE_SHA = 'fb28f6bcb783f66651053c384f2ad75e0eec789db89033bfc026a09e8af8c08e'
BASELINE_RAW_SHA = 'e6b2fd0bda0c461616f0fa3089eaf2c8935b44c41b1c684c0701f684bfcf06cb'
DERIVATIONS = [{'original': 'tools/literature/nextflow_catalog.py', 'derived': 'tools/literature/nextflow_current_catalog.py', 'original_sha256': 'ae0aeadfaa1d94b9eae3bc2527fdcdcc6a7e287aa50608f410c88c3a6a4d9000', 'derived_sha256': 'f34fa9a3c627a841d91bffb1bb068e79a9968b967bd19346138ef9fc90a36860', 'edits': [('from catalog_source_integrity import verify_source\n', 'from catalog_source_integrity import verify_source\nfrom catalog_fixed_cohort import verify_fixed_cohort\n'), ("    assert len(papers) == 250 and sum(len(p['tools']) for p in papers) == 2430\n", '    verify_fixed_cohort(root, data, papers)\n')]}, {'original': 'tools/literature/salmon_catalog.py', 'derived': 'tools/literature/salmon_native_current_catalog.py', 'original_sha256': '185791e089e8a0f42cb29822d9e3cd73ba9878eea2a8e1e6dcb867d6ca043af6', 'derived_sha256': 'aa7e8f4041fa26fdd47954239a739286e08f4e8e03204ad373fdf0b634b7e6c4', 'edits': [('from catalog_source_integrity import verify_source\n', 'from catalog_source_integrity import verify_source\nfrom catalog_fixed_cohort import verify_fixed_cohort\n'), ("papers=json.loads((data/'papers.json').read_bytes());assert len(papers)==250 and sum(len(p['tools']) for p in papers)==2430\n", "papers=json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(root,data,papers)\n")]}, {'original': 'tools/literature/salmon_workflow_catalog.py', 'derived': 'tools/literature/salmon_current_catalog.py', 'original_sha256': '7705d4db1592a2e5061b62bd846e73fa9dbe7fe0c3fcdf7856781c732cefe41b', 'derived_sha256': '9114564fe8e4f4f0d816150f42bd65608e96501f8b3788efef90cd3c48156016', 'edits': [('from salmon_catalog import salmon_metadata as legacy_metadata\n', 'from salmon_native_current_catalog import salmon_metadata as legacy_metadata\n')]}]


def verify_fixed_cohort(root, data, papers):
    packed=(data/'fixed-250-paper-cohort.json.gz').read_bytes()
    assert hashlib.sha256(packed).hexdigest()==BASELINE_SHA
    raw=gzip.decompress(packed)
    assert hashlib.sha256(raw).hexdigest()==BASELINE_RAW_SHA
    baseline=json.loads(raw)
    assert len(papers)==len(baseline)==250
    assert [{k:v for k,v in paper.items() if k!='tools'} for paper in papers]==baseline
    for row in DERIVATIONS:
        before=(root/row['original']).read_bytes()
        assert hashlib.sha256(before).hexdigest()==row['original_sha256']
        expected=before
        for old,new in row['edits']:
            assert expected.count(old.encode())==1
            expected=expected.replace(old.encode(),new.encode())
        actual=(root/row['derived']).read_bytes()
        assert actual==expected and hashlib.sha256(actual).hexdigest()==row['derived_sha256']
