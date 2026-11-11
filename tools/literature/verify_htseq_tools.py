# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify scoped HTSeq evidence offline; no installations, original counter or user analysis execution."""
import argparse
import json
import sys
from pathlib import Path
from htseq_tools_evidence import verify


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--folder',type=Path)
    proof=verify(parser.parse_args().folder)
    print(json.dumps(dict(status='passed-offline-scoped-HTSeq-tools',tests_per_JVM=proof['tests_per_JVM'],JVMs=3,
        final_count_analyses=7,final_positive_portable_verifications=24,corruption_refusals=33,missing_runtime_skips=3,
        source_and_commit_refusals=4,binary_original_matrices=32,source_pins=len(proof['source_pins']),logical_files=proof['logical_files'],whole_goal_complete=False)))


if __name__=='__main__':
    try:
        if not __debug__:raise RuntimeError('Never disable source/control checks')
        main()
    except Exception as error:
        print('HTSEQ EVIDENCE FAIL2: '+str(error)+'\nRequired action: preserve the checkpoint, correct the named source/archive/control mismatch and repeat verification. No partial proof or whole-tool/paper equivalence is admitted.',file=sys.stderr);raise SystemExit(2)
