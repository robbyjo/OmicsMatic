# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit every synthetic original MTAG row against the native estimator; no project adapter or paper reproduction credit."""
import argparse
import csv
import json
import math
import os
import re
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_mtag_reference import BASE, linux
from build_mtag_native import DEPENDENCY, DEPENDENCY_SHA


def table(path):
    with path.open(encoding='utf8', newline='') as stream:
        return list(csv.DictReader(stream, delimiter='\t'))


def matrix(path):
    return [[float(v) for v in row.split()] for row in path.read_text().splitlines()]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--probe', type=Path, required=True)
    p.add_argument('--build21', type=Path, required=True)
    p.add_argument('--build25', type=Path, required=True)
    p.add_argument('--parser-controls', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    (out / 'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    state = dict(status='FAIL2_MTAG_SYNTHETIC_COMPLETE_ROW_AUDIT', new_catalog_credit=False, project_adapter_validated=False,
                 whole_paper_reproduced=False, independently_fitted_ldsc=False, independently_computed_maxfdr=False,
                 scaled_tolerance=5e-12, nonzero_p_relative_tolerance=5e-10)
    try:
        probe = a.probe.resolve()
        proof = json.loads((probe / 'COMPLETED.json').read_bytes())
        assert proof['status'] == 'PASS_ORIGINAL_MTAG_WORKFLOW_SYNTHETIC_PROBE_ONLY' and proof['input_rows'] == 20000
        protected = {str(ROOT / 'build/cli/omicsmatic-0.3.7.jar'): pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')}
        for name, wanted in proof['original_files'].items():
            path = probe / 'original' / name
            assert pin(path) == wanted
            protected[str(path)] = wanted
        manifest = json.loads((probe / 'input-manifest.json').read_bytes())
        parser_receipt=json.loads((a.parser_controls/'COMPLETED.json').read_bytes())
        assert parser_receipt['status']=='PASS_ORIGINAL_MTAG_PARSER_CONTROL_ONLY'
        assert pin(a.parser_controls/'original-parsed-hex.json')==parser_receipt['parsed_hex']
        parsed=json.loads((a.parser_controls/'original-parsed-hex.json').read_bytes())
        assert parsed['status']=='ORIGINAL_PARSING_QC_ONLY_NO_COVARIANCE_OR_FIT' and len(parsed['variants'])==20000
        parsing_changes=[]
        original_inputs = []
        original_outputs = []
        expected = ['SNP','CHR','BP','A1','A2','Z','N','FRQ','mtag_beta','mtag_se','mtag_z','mtag_pval']
        for t in range(3):
            path = probe / 'inputs' / ('trait%d.tsv' % (t + 1))
            assert pin(path) == manifest['files'][path.name]
            protected[str(path)] = pin(path)
            rows, results = table(path), table(probe / 'original' / ('mtag_trait_%d.txt' % (t + 1)))
            assert len(rows) == len(results) == 20000 and len({r['snpid'] for r in rows}) == 20000
            assert list(results[0]) == expected
            for i,(original, result) in enumerate(zip(rows, results, strict=True)):
                for before, after in [('snpid','SNP'),('chr','CHR'),('bpos','BP'),('a1','A1'),('a2','A2')]:
                    assert original[before] == result[after], (t, original['snpid'], before)
                for before, after in [('z','Z'),('n','N'),('freq','FRQ')]:
                    raw_value,source_value,output_value=float(original[before]),float.fromhex(parsed[before][i][t]),float(result[after])
                    assert parsed['variants'][i]==original['snpid'] and math.isfinite(source_value)
                    # Compare with the actual original C-parser result exactly;
                    # input decimal parsing differences are retained separately.
                    assert source_value==output_value,(t,original['snpid'],before,source_value,output_value)
                    if raw_value!=source_value:
                        parsing_changes.append(dict(trait=t+1,input_row=i+1,snp=original['snpid'],field=before,raw_decimal=original[before],
                            java_python3_value_hex=raw_value.hex(),original_pandas_value_hex=source_value.hex(),
                            absolute_difference=abs(raw_value-source_value),ulp_distance=abs(raw_value-source_value)/math.ulp(raw_value)))
                assert all(math.isfinite(float(result[k])) for k in expected[5:])
            original_inputs.append(rows)
            original_outputs.append(results)
        assert all([r['snpid'] for r in rows] == [r['snpid'] for r in original_inputs[0]] for rows in original_inputs)
        save(out/'original-parser-differences.json',dict(status='RETAINED_COMPLETE_DECIMAL_TO_ORIGINAL_PANDAS_BINARY64_DIFFERENCES',
            compared_numeric_inputs=180000,changed_cells=len(parsing_changes),changes=parsing_changes,values_repaired=False,
            maximum_ulp_distance=max((r['ulp_distance'] for r in parsing_changes),default=0)))
        omega, sigma = matrix(probe / 'original/mtag_omega_hat.txt'), matrix(probe / 'original/mtag_sigma_hat.txt')
        assert all(len(row) == 3 for mat in [omega,sigma] for row in mat) and len(omega) == len(sigma) == 3
        batches = out / 'inputs'
        batches.mkdir()
        for batch in range(4):
            first, last = batch * 5000, (batch + 1) * 5000
            lines = ['MTAG108\t5000\t3', 'trait1\ttrait2\ttrait3', '\t'.join(r['snpid'] for r in original_inputs[0][first:last])]
            for name in ['z','n']:
                lines += ['\t'.join(parsed[name][i][t] for t in range(3)) for i in range(first,last)]
            lines += ['\t'.join(v.hex() for v in row) for mat in [omega,sigma] for row in mat]
            (batches / ('batch%d.tsv' % batch)).write_text('\n'.join(lines) + '\n', encoding='utf8', newline='\n')
        fdr = matrix(probe / 'original/mtag_fdr_mat.txt')
        grid = matrix(probe / 'original/mtag_prob_grid.txt')
        assert len(fdr) == len(grid) > 0 and all(len(row) == 3 for row in fdr) and all(len(row) == 8 for row in grid)
        assert all(math.isfinite(v) and 0 <= v <= 1 for mat in [grid,fdr] for row in mat for v in row)
        assert all(abs(sum(row)-1) <= 1e-14 for row in grid)
        maxima = [max(row[t] for row in fdr) for t in range(3)]
        log = (probe / 'original/mtag.log').read_text()
        reported = re.findall(r'Max FDR of Trait (\d+): ([0-9.eE+-]+) at probs', log)
        assert [int(t) for t, _ in reported] == [1,2,3]
        assert all(abs(float(value)-maxima[int(t)-1]) <= 5e-12 for t,value in reported)
        summaries = []
        for platform in ['windows21','windows25','linux21']:
            build = a.build25.resolve() if platform == 'windows25' else a.build21.resolve()
            receipt = json.loads((build / 'COMPLETED.json').read_bytes())
            assert receipt['status'] == 'PASS_NATIVE_MTAG_API_AND_EXTERNAL_CONSUMER_BUILD_ONLY'
            for artifact in receipt['artifacts'].values():
                assert pin(ROOT / artifact['path']) == {k:artifact[k] for k in ['bytes','sha256']}
            assert pin(DEPENDENCY)['sha256'] == DEPENDENCY_SHA
            cp_paths = [build/'mtag-api.jar',build/'mtag-consumer.jar',DEPENDENCY]
            java = ['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java'] if platform == 'linux21' else [str(Path('C:/Program Files') / ('Common Files/Oracle/Java/javapath' if platform == 'windows25' else 'Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin') / 'java.exe')]
            cp = ':'.join(map(linux,cp_paths)) if platform == 'linux21' else ';'.join(map(str,cp_paths))
            directory = out/platform
            directory.mkdir()
            count, max_scaled, max_p = 0, 0., 0.
            gmm_parts = []
            for batch in range(4):
                input_path, output_path = batches/('batch%d.tsv'%batch), directory/('batch%d.tsv'%batch)
                command = java + ['-cp',cp,'org.jlinalg.genetics.MtagReferenceProbe',linux(input_path) if platform=='linux21' else str(input_path),linux(output_path) if platform=='linux21' else str(output_path)]
                process = subprocess.run(command,cwd=ROOT,env=dict(os.environ),stdin=subprocess.DEVNULL,capture_output=True,timeout=180)
                label = platform+'-batch'+str(batch)
                (out/(label+'.stdout')).write_bytes(process.stdout)
                (out/(label+'.stderr')).write_bytes(process.stderr)
                save(out/(label+'.process.json'),dict(command=command,exit_code=process.returncode))
                assert process.returncode==0,(label,process.stderr[-3000:])
                rows=table(output_path)
                assert len(rows)==75009
                seen=set();gmm=[[0.]*3 for _ in range(3)]
                for row in rows:
                    kind,i,t=row['kind'],int(row['row']),int(row['column'])
                    assert (kind,i,t) not in seen
                    seen.add((kind,i,t));value=float.fromhex(row['value_hex'])
                    assert math.isfinite(value)
                    if kind=='gmm':
                        assert 0<=i<3 and 0<=t<3
                        gmm[i][t]=value
                        continue
                    assert kind in ['beta','se','factor','z','p'] and 0<=i<5000 and 0<=t<3
                    if kind=='factor':
                        continue
                    r=original_outputs[t][batch*5000+i]
                    key={'beta':'mtag_beta','se':'mtag_se','z':'mtag_z','p':'mtag_pval'}[kind]
                    expected_value=float(r[key])
                    if kind in ['beta','se']:
                        freq=float(r['FRQ']);expected_value*=math.sqrt(2*freq*(1-freq))
                    scaled=abs(value-expected_value)/max(1,abs(expected_value))
                    assert scaled<=state['scaled_tolerance'],(platform,batch,kind,i,t,value,expected_value)
                    max_scaled=max(max_scaled,scaled)
                    if kind=='p' and expected_value:
                        relative=abs(value-expected_value)/expected_value
                        assert relative<=state['nonzero_p_relative_tolerance']
                        max_p=max(max_p,relative)
                    elif kind=='p':
                        assert value==0
                    count+=1
                assert seen=={(kind,i,t) for kind in ['beta','se','factor','z','p'] for i in range(5000) for t in range(3)} | {('gmm',i,t) for i in range(3) for t in range(3)}
                gmm_parts.append(gmm)
            # This synthetic fixture's original GMM covariance required no source
            # positive-definiteness adjustment. Check all nine entries explicitly.
            for i in range(3):
                for j in range(3):
                    actual=sum(mat[i][j] for mat in gmm_parts)/4
                    assert abs(actual-omega[i][j])<=1e-10*max(abs(omega[i][j]),1e-300),(i,j,actual,omega[i][j])
            summaries.append(dict(platform=platform,matched_trait_rows=60000,compared_numeric_cells=count,global_gmm_entries=9,max_scaled_error=max_scaled,max_nonzero_p_relative_error=max_p))
        for path,wanted in protected.items():
            assert pin(Path(path))==wanted,path
        state.update(status='PASS_COMPLETE_SYNTHETIC_MTAG_ESTIMATOR_OUTPUT_AUDIT_ONLY',input_variants=20000,traits=3,platforms=summaries,
                     original_input_output_identities_preserved=True,original_source_parsed_values_preserved_exactly=True,raw_decimal_bitwise_equality_claimed=False,
                     source_parser_differences=pin(out/'original-parser-differences.json'),source_parser_changed_cells=len(parsing_changes),source_positive_definiteness_adjustment_needed=False,
                     original_maxfdr=maxima,maxfdr_grid_rows=len(grid),maxfdr_grid_and_logged_maxima_checked=True,
                     protected_files=protected,canonical_executable_unchanged=True)
        print(json.dumps({k:state[k] for k in ['status','input_variants','traits','platforms','original_maxfdr','maxfdr_grid_rows']}))
    except Exception as error:
        state['error']=repr(error)
        (out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
        print(state['status']+': '+repr(error))
    save(out/'COMPLETED.json',state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Complete synthetic numerical audit only; no project adapter, historical paper or biological validation.')
        +'\n\nREQUIRED ACTION: retain original inputs and failed attempts. Correct the named missing prerequisite or numerical cause where permitted and retry NEW paths; no output repair. Typed workflow, original source/QC variants, runtime profile and saved project verification remain open.\n',encoding='utf8',newline='\n')
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete row, identity or numeric checks')
    raise SystemExit(main())
