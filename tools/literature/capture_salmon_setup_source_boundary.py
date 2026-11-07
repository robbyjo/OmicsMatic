# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain exact original source for Salmon 1.0.0's unavailable mapping SHA-512 metadata links."""
import argparse
import hashlib
import json
from pathlib import Path
import tarfile

ROOT = Path(__file__).resolve().parents[2]


def pin(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def main():
    if not __debug__:
        raise RuntimeError('Never disable original source checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    base = (ROOT / 'build/literature-review/salmon-runtime').resolve()
    assert out.is_relative_to(base) and not out.exists()
    out.mkdir()
    lock_path = ROOT / 'tools/literature/salmon-runtime-lock.json'
    lock = json.loads(lock_path.read_bytes())
    cases = []
    for version in ['1.0.0', '1.1.0']:
        source = base / 'upstream-capture-1' / version / 'source.tar.gz'
        upstream = lock['profiles']['linux-x86_64-salmon-' + version]['source_archive']
        assert pin(source) == {k: upstream[k] for k in ['bytes', 'sha256']}
        rows = {}
        with tarfile.open(source) as archive:
            for name in ['include/SalmonIndex.hpp', 'include/ReadExperiment.hpp', 'src/GZipWriter.cpp']:
                member = archive.getmember('salmon-' + version + '/' + name)
                assert member.isfile()
                raw = archive.extractfile(member).read()
                path = out / version / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(raw)
                rows[name] = dict(pin=pin(path), archive_member=member.name)
        index = (out / version / 'include/SalmonIndex.hpp').read_text()
        loaded = index[index.index('cereal::make_nvp("SeqHash",'):index.index('cereal::make_nvp("index_version",')]
        has512 = 'cereal::make_nvp("SeqHash512",' in loaded and 'cereal::make_nvp("NameHash512",' in loaded
        assert has512 == (version == '1.1.0')
        assert 'seqHash512_(""), nameHash512_("")' in index
        reader = (out / version / 'include/ReadExperiment.hpp').read_text()
        writer = (out / version / 'src/GZipWriter.cpp').read_text()
        assert 'return salmonIndex_->seqHash512()' in reader and 'return salmonIndex_->nameHash512()' in reader
        assert 'cereal::make_nvp("index_seq_hash512", experiment.getIndexSeqHash512())' in writer
        assert 'cereal::make_nvp("index_name_hash512", experiment.getIndexNameHash512())' in writer
        cases.append(dict(version=version, original_source_archive=upstream, retained_source_members=rows,
                          mapping_index_load_populates_SHA512_members=has512,
                          interpretation='1.0.0 initializes both SHA-512 members empty and omits their mapping-index JSON load; '
                                         'ReadExperiment returns those members and GZipWriter emits them. '
                                         '1.1.0 explicitly loads both fields. Exact original setup outputs corroborate the difference.'))
    result = dict(status='passed-original-source-characterization-of-Salmon-100-empty-mapping-SHA512-links', cases=cases,
                  source_pins={Path(__file__).relative_to(ROOT).as_posix(): pin(Path(__file__)), lock_path.relative_to(ROOT).as_posix(): pin(lock_path)},
                  required_action='Keep original1.0.0 quantification SHA-512 fields empty and mark those links unavailable. '
                                  'Do not fabricate them from index metadata or infer a complete index/quantification hash binding. '
                                  'This source characterization does not admit a current host or user analysis.',
                  scientific_runtime_admitted=False, user_analysis_admitted=False)
    (out / 'COMPLETED.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(result['status'])


if __name__ == '__main__':
    main()
