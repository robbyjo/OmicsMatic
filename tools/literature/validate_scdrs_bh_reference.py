# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare the native explicit-family BH arithmetic with unchanged Statsmodels0.13.5."""
import argparse
import csv
import json
import struct
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE, linux

REFERENCE = r'''import json,sys
import numpy as np
import statsmodels
from statsmodels.stats.multitest import multipletests
assert statsmodels.__version__=='0.13.5'
cases={
'one-at-cutoff':[.05], 'one-below-cutoff':[np.nextafter(.05,0)], 'one-above-cutoff':[np.nextafter(.05,1)],
'equal-threshold-family':[.025,.05], 'ties-and-input-order':[.03,.005,.03,.9,1.,0.,.0001],
'all-one':np.ones(33), 'all-zero':np.zeros(33), 'signed-zero':[0.,-0.,0.,-0.,1.],
'subnormal-and-small':[np.nextafter(0.,1.),1e-300,1e-100,.001,.05,1.],
}
rng=np.random.RandomState(20261110)
for n in [3,17,129,1000,10000]:
 cases['continuous-'+str(n)]=rng.uniform(size=n)
 cases['source-mc-float32-'+str(n)]=np.asarray((rng.randint(0,1001,size=n)+1)/1001,dtype=np.float32)
inputs=[];expected={}
for name,values in cases.items():
 p=np.asarray(values)
 q=multipletests(p,method='fdr_bh')[1]
 inputs.append(dict(id=name,p_hex=[float(x).hex() for x in p]))
 expected[name]=dict(q_hex=[float(x).hex() for x in q],significant=[bool(x<.05) for x in q])
json.dump(dict(schema_version=1,cases=inputs),open(sys.argv[1],'w'),indent=2)
json.dump(dict(statsmodels=statsmodels.__version__,cases=expected),open(sys.argv[2],'w'),indent=2)
'''


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--build', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    (out / 'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    state = dict(status='FAIL2_SCDRS_NATIVE_BH_REFERENCE', new_catalog_credit=False)

    def run(label, args):
        result = subprocess.run(args, cwd=ROOT, stdin=subprocess.DEVNULL, capture_output=True, timeout=180)
        (out / (label + '.stdout')).write_bytes(result.stdout)
        (out / (label + '.stderr')).write_bytes(result.stderr)
        save(out / (label + '.process.json'), dict(command=args, exit_code=result.returncode))
        assert result.returncode == 0, (label, result.stderr[-2000:])

    try:
        build = json.loads((a.build / 'COMPLETED.json').read_bytes())
        assert build['status'] == 'PASS_STANDALONE_SCDRS_WORKFLOW_DEVELOPMENT_BUILD_ONLY'
        artifact = ROOT / build['artifact']['path']
        assert pin(artifact) == {k: build['artifact'][k] for k in ['bytes', 'sha256']}
        reference = out / 'original-reference.py'
        reference.write_text(REFERENCE, encoding='utf-8', newline='\n')
        run('statsmodels-reference', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--',
            '/var/tmp/omicsmatic-scdrs-reference-2/environment/bin/python', '-s', '-B', linux(reference),
            linux(out / 'inputs.json'), linux(out / 'expected.json')])
        probe = ROOT / 'src/test/java/org/jlinalg/cli/ScdrsWorkflowReferenceProbe.java'
        (out / 'probe-at-attempt.java').write_bytes(probe.read_bytes())
        classes = out / 'classes'
        classes.mkdir()
        jdk = Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')
        run('compile-consumer', [str(jdk / 'javac.exe'), '--release', '17', '-sourcepath', '', '-Xlint:all', '-Werror',
            '-encoding', 'UTF-8', '-cp', str(artifact), '-d', str(classes), str(probe)])
        expected = json.loads((out / 'expected.json').read_bytes())
        summaries = []
        for platform, java, sep, convert in [
            ('windows21', [str(jdk / 'java.exe')], ';', str),
            ('windows25', ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'], ';', str),
            ('linux21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/java'], ':', linux)]:
            result_path = out / (platform + '.tsv')
            run(platform, java + ['-cp', sep.join(convert(p) for p in [classes, artifact]),
                'org.jlinalg.cli.ScdrsWorkflowReferenceProbe', convert(out / 'inputs.json'), convert(result_path)])
            count = 0
            with result_path.open(newline='') as stream:
                seen = set()
                for row in csv.DictReader(stream, delimiter='\t'):
                    name, index = row['case'], int(row['row'])
                    assert (name, index) not in seen
                    seen.add((name, index))
                    q, target = float.fromhex(row['q_hex']), float.fromhex(expected['cases'][name]['q_hex'][index])
                    assert struct.pack('>d', q) == struct.pack('>d', target), (platform, name, index, q.hex(), target.hex())
                    assert row['significant_005'] == str(expected['cases'][name]['significant'][index]).lower()
                    count += 1
            assert count == sum(len(v['q_hex']) for v in expected['cases'].values())
            summaries.append(dict(platform=platform, exact_double_q_values=count, strict_cutoff_decisions=count))
        state.update(status='PASS_NATIVE_SCDRS_BH_EXACT_STATSMODELS_AND_STRICT_CUTOFF_THREE_JVMS',
            summaries=summaries, cases=len(expected['cases']), source_version='0.13.5', probe=pin(probe),
            workflow_artifact=pin(artifact), source_repaired=False)
    except Exception as error:
        state.update(cause=repr(error), traceback=traceback.format_exc())
    save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('cause', ''))
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable independent original BH comparisons')
    raise SystemExit(main())
