# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Package the tested upstream legacy binary and unchanged original notices.

This developer-only step uses retained reference preparation. The resulting
distribution and installer do not require that ignored preparation directory.
"""
import argparse,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from sniffles_native_evidence import verify,byte_pin


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    out=args.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/sniffles-runtime');out.mkdir(parents=True)
    native,files=verify(False);base='build/literature-review/sniffles-runtime/'
    profile_path=ROOT/'src/main/resources/org/jlinalg/omics/sniffles-execution/profiles.json';profile=json.loads(profile_path.read_bytes())
    binary=ROOT/base/'reference-setup-3/sources/v1.0.7/bin/sniffles-core-1.0.7/sniffles'
    assert pin(binary)==profile['legacy_binary']
    source=json.loads(files[base+'source-intake-3/COMPLETED.json'])
    import io,tarfile
    notices={};row=source['editions']['v1.0.7'];archive=files[row['archive']['path']]
    with tarfile.open(fileobj=io.BytesIO(archive),mode='r:gz') as opened:
        prefix='Sniffles-'+row['commit']+'/'
        names=['LICENSE','lib/bamtools-2.3.0/LICENSE','lib/tclap-1.2.1/COPYING','lib/zlib-1.2.7/zlib.h',
            'lib/bamtools-2.3.0/src/third_party/gtest-1.6.0/COPYING','lib/bamtools-2.3.0/src/third_party/jsoncpp/LICENSE']
        for name in names:
            raw=opened.extractfile(prefix+name).read();assert byte_pin(raw)==row['members'][name];notices['notices/v1.0.7/'+name]=raw
    downloads=json.loads(files[base+'reference-setup-3/downloaded-archives.json'])
    wheels={name:value for name,value in downloads.items() if value['package'] in ['pysam','edlib','psutil']};assert len(wheels)==3
    pip=dict(filename='pip-24.3.1-py3-none-any.whl',version='24.3.1',
        url='https://files.pythonhosted.org/packages/ef/7d/500c9ad20238fcfcb4cb9243eede163594d7020ce87bd9610c9e02771876/pip-24.3.1-py3-none-any.whl',
        bytes=1822182,sha256='3790624780082365f47549d032f3770eeb2b1e8bd1f7b2e02dace1afa361b4ed')
    notice=("# Original Sniffles 1.0.7 native binary\n\nThis is the tested Linux x86_64 binary built from unmodified upstream Sniffles commit "+row['commit']+
        ". It is not an identified paper author's binary. Original Sniffles, BamTools, TCLAP, zlib, Google Test and JsonCpp notices accompany it unchanged in assets.zip. No original scientific source was patched.\n\n"+
        "The retained reference build selected CMAKE_CXX_STANDARD=11 with GCC 13 and CMake Release; older BamTools does not compile with the compiler's default C++17 comparator requirements. A build in another absolute directory may have different binary bytes. This distribution supplies the exact tested binary rather than silently accepting a new binary hash.\n\n"+
        "Original source: https://github.com/fritzsedlazeck/Sniffles/tree/"+row['commit']+"\n\nComplete immutable source archive and preparation/build logs are retained in docs/literature/sniffles-vcf-development/controls.zip. System C/C++ runtime libraries and Python are not redistributed here. This asset preparation grants no SV calling or catalog availability credit.\n").encode()
    payload={'legacy-sniffles':binary.read_bytes(),**notices,'NOTICE.md':notice}
    with zipfile.ZipFile(out/'assets.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as opened:
        for name,raw in sorted(payload.items()):
            item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;opened.writestr(item,raw)
    manifest=dict(schema_version=1,status='PREPARED_EXACT_UPSTREAM_RUNTIME_DISTRIBUTION_ONLY',analysis_admitted=False,implementation_credit=False,
        assets=pin(out/'assets.zip'),asset_members={name:byte_pin(raw) for name,raw in payload.items()},profile=pin(profile_path),
        source_reference_validation=pin(ROOT/'docs/literature/sniffles-vcf-development/VALIDATION.json'),source_archives={tag:record['archive'] for tag,record in source['editions'].items()},
        source_commits={tag:record['commit'] for tag,record in source['editions'].items()},wheels=wheels,pip=pip,
        original_binary=profile['legacy_binary'],source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))},canonical_executable_unchanged=native['canonical_executable_unchanged'])
    save(out/'DISTRIBUTION.json',manifest);(out/'NOTICE.md').write_bytes(notice)
    destination=ROOT/'docs/literature/sniffles-runtime-distribution';assert not destination.exists();destination.mkdir()
    for path in out.iterdir():(destination/path.name).write_bytes(path.read_bytes())
    print('Prepared exact original binary and unchanged notices; installation validation remains required')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source/binary/notice verification')
    main()
