# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute unchanged Linux split.cpp with explicit progress/interrupt-only Rcpp stubs."""
import argparse
import json
import os
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    base = ROOT / 'build/literature-review/signac-runtime'
    assert os.uname().sysname == 'Linux' and out.is_relative_to(base) and not out.exists()
    out.mkdir()
    reference = base / 'peaks-preparation-original-2/original/RESULT.json'
    cases = json.loads(reference.read_bytes())['split']
    cpp = base / 'source-intake-1/1.9.0/source/Signac/src/split.cpp'
    compared = {v: pin(base / ('source-intake-1/' + v + '/source/Signac/src/split.cpp'))
                for v in ['1.6.0', '1.7.0', '1.9.0', '1.14.0', '1.17.1']}
    # These support declarations replace no parsing/splitting/storage instruction.
    (out / 'Rcpp.h').write_text('''#include <iostream>
#include <vector>
#include <map>
#include <algorithm>
#include <string>
#include <cstring>
namespace Rcpp { static std::ostream& Rcerr=std::cerr; inline void checkUserInterrupt() {} }
''')
    runner = out / 'runner.cpp'
    runner.write_text('''#include <fstream>
#include "''' + cpp.as_posix() + '''"
int main(int argc,char**argv){
 if(argc!=5)return 2;
 std::vector<std::string> cells,idents,unique;std::ifstream mappings(argv[3]),groups(argv[4]);std::string line;
 while(std::getline(mappings,line)){auto n=line.find('\\t');if(n==std::string::npos)return 2;cells.push_back(line.substr(0,n));idents.push_back(line.substr(n+1));}
 while(std::getline(groups,line))unique.push_back(line);
 return splitFragments(argv[1],cells,idents,unique,std::string(argv[2])+"/","",256,true,false);
}
''')

    def command(label, argv):
        argv = list(map(str, argv))
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            process = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=120)
        save(out / (label + '.process.json'), dict(command=argv, exit_code=process.returncode))
        assert process.returncode == 0, label

    try:
        command('compile', ['g++', '-std=c++17', '-O2', '-I', out, runner, '-lz', '-o', out / 'original-split'])
        results = {}
        for name, case in cases.items():
            folder = out / name
            folder.mkdir()
            destination = folder / 'split'
            destination.mkdir()
            groups = folder / 'groups.txt'
            groups.write_text(''.join(g + '\n' for g in case['source_names']), encoding='utf8')
            selected = {c['cell']: c['group'].replace(' ', '_').replace('/', '_') for c in case['cells']}
            for i, source in enumerate(case['inputs']):
                path = folder / ('input' + str(i) + '.tsv')
                path.write_bytes(source['text'].encode('utf8'))
                mappings = folder / ('map' + str(i) + '.tsv')
                mappings.write_text(''.join(row['barcode'] + '\t' + selected[row['cell']] + '\n'
                                            for row in source['map'] if row['cell'] in selected), encoding='utf8')
                command(name + '-' + str(i), [out / 'original-split', path, destination, mappings, groups])
            expected = {g: (destination / (g + '.bed')).read_bytes().hex() for g in case['source_names']}
            # Windows std::ofstream text translation is retained separately, not normalized away.
            assert all(bytes.fromhex(case['expected_hex'][g]) == bytes.fromhex(expected[g]).replace(b'\n', b'\r\n')
                       for g in case['source_names'])
            results[name] = dict(case, expected_hex=expected, original_Windows_Rcpp_hex=case['expected_hex'])
        save(out / 'COMPLETED.json', dict(status='passed-seven-original-Linux-fragment-split-controls',
                                         cases=results, unchanged_source=pin(cpp), source_editions=compared,
                                         Rcpp_stub_scope='Progress output and interrupt callback only; no record/parse/group/write logic replaced. Not a whole Signac R runtime.',
                                         Windows_text_translation_checked=True,
                                         source_pins={str(p.relative_to(ROOT)): pin(p) for p in [Path(__file__), cpp, reference]}))
        print('Seven unchanged original Linux C++ split controls completed; Windows CRLF translation retained')
    except Exception as error:
        save(out / 'FAILURE.json', dict(status='FAIL2_ORIGINAL_LINUX_SPLIT_CONTROLS', error=str(error)))
        raise


if __name__ == '__main__':
    main()
