# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve exact executed source for an existing SMR workflow attempt before implementation evolves; original records remain unchanged."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--attempt',required=True,type=Path);a=p.parse_args();home=a.attempt.resolve();assert home.is_relative_to(ROOT/'build/literature-review/smr-runtime');record=json.loads((home/'COMPLETED.json').read_bytes());target=home/'executed-source-snapshot';assert not target.exists();build=ROOT/record['artifact']['path'];mapping={}
    for name,expected in record['source_pins'].items():
        choices=[ROOT/name,build.parent/'source-snapshot'/name,home/'probe-at-attempt.py'];matches=[f for f in choices if f.is_file() and pin(f)==expected];assert matches,'Exact executed source unavailable: '+name;destination=target/name;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(matches[0].read_bytes());assert pin(destination)==expected;mapping[name]=expected
    save(home/'EXECUTED-SOURCE-SNAPSHOT.json',dict(status='post-attempt-exact-executed-source-preservation',original_record=pin(home/'COMPLETED.json'),original_record_modified=False,sources=mapping,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))}));print('Preserved',len(mapping),'exact executed sources for',home.name)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact executed-source identity checks')
    main()
