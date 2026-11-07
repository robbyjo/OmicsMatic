# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile unmodified extracted StringTie finalization expressions; distinguish arithmetic harness from whole-tool runs."""
import argparse
import hashlib
import json
from pathlib import Path
import platform
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from collect_stringtie_controls import invoke, pin, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    base = ROOT / 'build/literature-review/stringtie-runtime'
    assert platform.system() == 'Linux' and out.is_relative_to(base.resolve()) and not out.exists()
    out.mkdir(parents=True)
    intake = base / 'source-intake-1'; original = json.loads((intake / 'COMPLETED.json').read_bytes())
    coverages = ['0.000000', '-0.000000', '0.000001', '-0.000001', '0.000003', '0.000009', '0.001001', '0.100001',
                 '1.000000', '1.000001', '1.000003', '16.777217', '21.337809', '30.394629', '46.666667', '53.119999',
                 '999.999999', '12345.678901', '16777217.000000', '-12345.678901', '0.000000499999999999',
                 '0.000000500000000000', '0.000001500000000000', '340282346638528859811704183484516925440.000000']
    denominators = [(0.0, 0.0), (0.001, 0.00001), (0.0010000000000000002, 0.000010000000000000003),
                    (28000.0, 98.399105), (14000.0, 46.666666666666664), (1e-300, 1e300)]
    cases = [(str(i + 1), coverage, frag.hex(), cov.hex()) for i, (coverage, (frag, cov)) in
             enumerate((c, d) for c in coverages for d in denominators)]
    inputs = ''.join('\t'.join(c) + '\n' for c in cases)
    (out / 'cases.tsv').write_text('id\tcoverage\tFrag_Len_hex\tCov_Sum_hex\n' + inputs, encoding='ascii', newline='\n')
    records = []
    for edition in original['editions']:
        version = edition['version']; folder = out / version; folder.mkdir()
        relative = 'stringtie-' + version + '/stringtie.cpp'
        source = intake / version / 'source' / relative
        assert pin(source) == {k: edition['artifacts']['source']['files'][relative][k] for k in ['bytes', 'sha256']}
        text = source.read_text()
        match = re.search(r'(?m)^\s*sscanf\(linebuf,"%d %d %d %d %(?:g|lf|lg)"[^\n]*', text)
        assert match
        end = text.index('if(istr)', match.end())
        excerpt = text[match.start():end]
        declarations = {name: re.findall(r'\b(float|double)\s+' + name + r'\s*;', text) for name in ['tcov', 'calc_fpkm', 'calc_tpm']}
        assert all(len(v) == 1 for v in declarations.values()) and len({v[0] for v in declarations.values()}) == 1
        scalar = declarations['tcov'][0]
        (folder / 'original-expressions.cpp.txt').write_text(excerpt, encoding='utf-8', newline='\n')
        driver = '''#include <cstdio>
#include <cstdlib>
#include <cfenv>
#include <iostream>
#include <string>
int main(){if(std::fesetround(FE_TONEAREST)!=0)return 5;
std::string id,coverage,frag,covsum;
while(std::cin>>id>>coverage>>frag>>covsum){
double Frag_Len=std::strtod(frag.c_str(),nullptr),Cov_Sum=std::strtod(covsum.c_str(),nullptr);
int istr=0,nl=0,tlen=0,t_id=0;
SCALAR tcov=0,calc_fpkm=0,calc_tpm=0;
std::string row="1 1 1 1 "+coverage;const char* linebuf=row.c_str();
EXCERPT
std::printf("%s\\t%a\\t%a\\t%a\\t%.6f\\t%.6f\\t%.6f\\n",id.c_str(),double(tcov),double(calc_fpkm),double(calc_tpm),double(tcov),double(calc_fpkm),double(calc_tpm));
}return std::cin.eof()?0:6;}
'''.replace('SCALAR', scalar).replace('EXCERPT', excerpt)
        driver_path = folder / 'driver.cpp'; driver_path.write_text(driver, encoding='utf-8', newline='\n')
        build = invoke(['/usr/bin/g++', '-std=c++11', '-O2', '-fno-fast-math', '-ffp-contract=off', '-o', 'arithmetic-control', str(driver_path)], folder, 'compile', 60)
        assert build['exit_code'] == 0 and not build['timed_out']
        command = [str(folder / 'arithmetic-control')]
        completed = subprocess.run(command, input=inputs.encode(), stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30, check=False)
        (folder / 'original-values.tsv').write_bytes(completed.stdout); (folder / 'run.stderr').write_bytes(completed.stderr)
        assert completed.returncode == 0 and not completed.stderr
        rows = completed.stdout.decode('ascii').splitlines()
        assert len(rows) == len(cases) and all(len(r.split('\t')) == 7 and r.split('\t')[0] == cases[i][0] for i, r in enumerate(rows))
        records.append(dict(version=version, scalar=scalar, source=pin(source), source_relative=source.relative_to(ROOT).as_posix(),
                            expression=pin(folder / 'original-expressions.cpp.txt'), driver=pin(driver_path), build=build,
                            process=dict(command=command, exit_code=completed.returncode), binary=pin(folder / 'arithmetic-control'),
                            outputs=pin(folder / 'original-values.tsv'), cases=len(rows),
                            license_relative=(intake / version / 'source' / ('stringtie-' + version) / 'LICENSE').relative_to(ROOT).as_posix()))
        save(out / 'PROGRESS.json', records)
    assert len(records) == 7 and len(cases) == 144
    report = dict(schema_version=1, status='captured-1008-original-source-StringTie-normalization-arithmetic-cases',
                  collector=pin(Path(__file__)), cases=pin(out / 'cases.tsv'), editions=records, total_cases=1008,
                  original_intake=pin(intake / 'COMPLETED.json'), whole_StringTie_runs=False, implementation_credit=False,
                  warning='Unmodified extracted finalization expressions in an explicit C++/FE_TONEAREST harness, not original whole-tool fits. Nonfinite classes and raw source text remain preserved; NaN payload/sign identity is not a portable contract. Complete captured original-tool families remain separate integration evidence.')
    save(out / 'CAPTURED.json', report)
    print(json.dumps(dict(status=report['status'], editions=len(records), cases=report['total_cases'])))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original source arithmetic extraction checks')
    main()
