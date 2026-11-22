#!/usr/bin/env python3
"""Run public original-PCA recipes and verify identities, counts, weights and residuals."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def table(path):
    with path.open(encoding='utf-8', newline='') as stream:
        return list(csv.DictReader(stream, delimiter='\t'))


def unit(value):
    return 0 if value == 0 else 10 ** (math.floor(math.log10(abs(value))) - 5)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--home', required=True, type=Path)
    parser.add_argument('--java', required=True, type=Path, action='append')
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    try:
        import numpy as np
    except ImportError:
        (args.out / 'WARNINGS.md').write_text('# VALIDATION SKIPPED\n\nInstall NumPy in a permitted private Python environment and rerun to a new directory. No packaged scientific verification was performed.\n')
        (args.out / 'verification.json').write_text(json.dumps(dict(status='skipped', reason='numpy-unavailable')) + '\n')
        print('VALIDATION SKIPPED: read ' + str(args.out / 'WARNINGS.md'), file=sys.stderr)
        return 3
    source = ROOT / 'examples/plink-tools/pca'
    provenance = json.loads((source / 'provenance.json').read_text())
    for name, digest in provenance['files_sha256'].items():
        assert sha(source / name) == digest, name
    inputs = args.out / 'input'
    shutil.copytree(source, inputs)
    before = {f.name: sha(f) for f in inputs.iterdir() if f.is_file()}
    fam = [line.split() for line in (inputs / 'known.fam').read_text().splitlines()]
    bim = [line.split() for line in (inputs / 'known.bim').read_text().splitlines()]
    packed = (inputs / 'known.bed').read_bytes()
    assert packed[:3] == bytes([108, 27, 1])
    stride = (len(fam) + 3) // 4
    wanted = set((inputs / 'panel.txt').read_text().splitlines())
    variants = [i for i, row in enumerate(bim) if row[1] in wanted]
    metadata = [bim[i] for i in variants]
    calls = np.array([[{0: 2, 1: np.nan, 2: 1, 3: 0}[(packed[3 + v * stride + i // 4] >> (2 * (i % 4))) & 3]
                      for i in range(len(fam))] for v in variants])
    index = {tuple(row[:2]): i for i, row in enumerate(fam)}
    record = dict(status='running', producer_sha256=sha(__file__), jar_sha256=sha(args.jar), numpy=np.__version__,
        source_provenance_sha256=sha(source / 'provenance.json'), input_sha256=before, workflows=[], negative_controls=[],
        scope='Actual public packaged original-PCA recipes on synthetic hard calls. Independent BED decoding, explicit AF/reference masks, complete sample/allele identities, original precision, GRM eigen/weight/residual mathematics. No large production cohort or whole-paper/whole-PLINK coverage claim.')

    def save():
        (args.out / 'verification.json').write_text(json.dumps(record, indent=2) + '\n')

    def run(java, label, options):
        recipe = args.out / (label + '.json')
        recipe.write_text(json.dumps(options, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        command = [str(java), '-jar', str(args.jar.resolve()), 'plink-tools', '--options-file', str(recipe.resolve())]
        result = subprocess.run(command, cwd=ROOT, capture_output=True, timeout=240)
        (args.out / (label + '.stdout')).write_bytes(result.stdout)
        (args.out / (label + '.stderr')).write_bytes(result.stderr)
        return result, command

    def options(case, output, input_folder=inputs):
        values = json.loads((source / (case['label'] + '.json')).read_text())
        values.update(home=str(args.home.resolve()), bfile=str((input_folder / 'known').resolve()),
                      samples=str((input_folder / ('samples-' + case['frequency_population'] + '.tsv')).resolve()),
                      variants=str((input_folder / 'panel.txt').resolve()), out=str(output.resolve()))
        return values

    def verify(out, case, data=calls, names=fam, marker_names=metadata, role_path=None):
        sample_rows = table(role_path or inputs / ('samples-' + case['frequency_population'] + '.tsv'))
        ordinal = {tuple(row[:2]): i for i, row in enumerate(names)}
        roles = {(r['fid'], r['iid']): r for r in sample_rows}
        basis = [i for i, row in enumerate(names) if roles[tuple(row[:2])]['fit'] == 'yes']
        reference = [i for i, row in enumerate(names) if roles[tuple(row[:2])]['frequency_reference'] == 'yes']
        n, m, k = len(basis), len(marker_names), 5
        p = np.nanmean(data[:, reference], axis=1) / 2
        observed = np.isfinite(data[:, basis])
        z = np.where(observed, (data[:, basis] - 2*p[:, None]) / np.sqrt(2*p*(1-p))[:, None], 0)
        matrix = z.T @ z / (m if case['missingness'] == 'mean' else observed.astype(np.int64).T @ observed.astype(np.int64))
        eigenvalues, eigenvectors = np.linalg.eigh(matrix)
        eigenvalues = eigenvalues[::-1]
        eigenvectors = eigenvectors[:, ::-1]
        values = np.array([float(row['eigenvalue']) for row in table(out / 'eigenvalues.tsv')])
        assert values.shape == (k,) and np.all(values > 0)
        coordinate_rows = table(out / 'eigenvectors.tsv')
        assert [(r['fid'], r['iid']) for r in coordinate_rows] == [tuple(names[i][:2]) for i in basis]
        vectors = np.array([[float(r['PC'+str(c+1)]) for c in range(k)] for r in coordinate_rows])
        vector_error = np.sqrt(np.sum(np.array([[unit(v)/2 for v in row] for row in vectors])**2, axis=0))
        for c in range(k):
            if float(vectors[:, c] @ eigenvectors[:, c]) < 0:
                eigenvectors[:, c] *= -1
        if case['algorithm'] == 'exact':
            for c in range(k):
                assert abs(values[c]-eigenvalues[c]) <= min(unit(values[c]), unit(eigenvalues[c]))/2 + 1e-10
                assert np.linalg.norm(vectors[:, c]-eigenvectors[:, c]) <= vector_error[c] + 1e-9
        sample_ledger = table(out / 'sample-reference.tsv')
        assert [(r['fid'], r['iid']) for r in sample_ledger] == [tuple(row[:2]) for row in names]
        for row in sample_ledger:
            pair = (row['fid'], row['iid'])
            assert row['fitted'] == str(roles[pair]['fit'] == 'yes').lower()
            assert row['frequency_reference'] == str(roles[pair]['frequency_reference'] == 'yes').lower()
            assert row['recorded_FAM_founder'] == str(names[ordinal[pair]][2:4] == ['0','0']).lower()
        allele_rows = table(out / 'variant-reference.tsv')
        assert [r['variant'] for r in allele_rows] == [r[1] for r in marker_names]
        for v, row in enumerate(allele_rows):
            ref_calls = data[v, reference]
            count = int(np.isfinite(ref_calls).sum())
            assert int(row['frequency_reference_called']) == count
            assert int(row['counted_alleles']) == int(np.nansum(ref_calls))
            assert float(row['frequency']) == float(p[v])
            assert int(row['fitted_called']) == int(observed[v].sum()) and row['status'] == 'ADMITTED'
            assert [row['counted_allele'], row['other_allele']] == marker_names[v][4:6]
        weights = table(out / 'variant-weights.tsv')
        expected_rows = m if case['engine'] == 'plink1' else 2*m
        assert len(weights) == expected_rows
        by_variant = {}
        for row in weights:
            by_variant.setdefault(row['variant'], []).append(row)
        assert list(by_variant) == [r[1] for r in marker_names]
        weight_fields = 0
        for v, meta in enumerate(marker_names):
            group = by_variant[meta[1]]
            assert len(group) == (1 if case['engine'] == 'plink1' else 2)
            assert {r['counted_allele'] for r in group} == ({meta[4]} if case['engine'] == 'plink1' else set(meta[4:6]))
            for row in group:
                assert row['chromosome'] == meta[0] and {row['first_allele'],row['second_allele']} == set(meta[4:6])
                assert row['allele_order_convention'] == ('PLINK1_A1_A2' if case['engine']=='plink1' else 'PLINK2_REF_ALT')
                for c in range(k):
                    # Use printed vectors with a propagated decimal-error envelope,
                    # including eigenvalue and reported weight quantization.
                    sign = 1 if row['counted_allele'] == meta[4] else -1
                    scale = 1 if case['engine'] == 'plink1' else -.5
                    target = float(z[v] @ vectors[:,c] / math.sqrt(values[c]) * sign * scale)
                    original = float(row['PC'+str(c+1)])
                    error = float(np.sum(abs(z[v]) * np.array([unit(x)/2 for x in vectors[:,c]])) / math.sqrt(values[c]) * abs(scale))
                    error += abs(target) * (unit(values[c])/2) / (2*(values[c]-unit(values[c])/2))
                    assert abs(original-target) <= unit(original)/2 + error + 1e-11, (case['label'],meta[1],c,original,target,error)
                    weight_fields += 1
        residuals = np.linalg.norm(matrix@vectors-vectors*values, axis=0)/(abs(values)*np.linalg.norm(vectors,axis=0))
        if case['missingness'] == 'mean':
            diagnostic = table(out / 'eigen-quality.tsv')
            assert len(diagnostic) == k
            for c, row in enumerate(diagnostic):
                assert abs(float(row['observed_relative_residual'])-float(residuals[c])) < 1e-11
                assert row['accepted'] == 'true' and float(row['requested_maximum_relative_residual']) == .01
        else:
            assert not (out / 'eigen-quality.tsv').exists()
            assert 'indefinite' in (out / 'WARNINGS.md').read_text()
        assert json.loads((out / 'analysis-status.json').read_text())['status'] == 'passed-with-review-warnings'
        return dict(fitted_rows=n, sample_identity_rows=len(names), frequency_rows=m, weight_rows=expected_rows,
                    weight_fields=weight_fields, eigenvalues=k, coordinate_fields=n*k,
                    maximum_relative_eigen_residual=float(residuals.max()),
                    files_sha256={f.name:sha(f) for f in out.iterdir() if f.is_file()})

    save()
    try:
        cases = provenance['cases']
        for jvm, java in enumerate(args.java):
            for case in cases:
                label = str(jvm)+'-'+case['label'];out=args.out/label
                result, command = run(java,label,options(case,out))
                assert result.returncode == 0, (label,result.stderr.decode(errors='replace'))
                assert b'PCA WARNINGS' in result.stderr
                record['workflows'].append(dict(label=label,command=command,**verify(out,case)))
                save()
            # Unicode identifiers and directories travel only through a UTF-8 recipe.
            unicode_input=args.out/('unicode-input-'+str(jvm))/'输入_α';shutil.copytree(inputs,unicode_input)
            fitted_pairs=[(r['fid'],r['iid']) for r in table(inputs/'samples-founders.tsv') if r['fit']=='yes']
            unicode_fam=[row.copy() for row in fam];replacements={fitted_pairs[0]:('家庭_a','b'),fitted_pairs[1]:('家庭','a_b')}
            for row in unicode_fam:
                if tuple(row[:2]) in replacements:row[:2]=list(replacements[tuple(row[:2])])
            (unicode_input/'known.fam').write_text('\n'.join(' '.join(row) for row in unicode_fam)+'\n',encoding='utf-8')
            for population in ['all','founders']:
                roles=table(inputs/('samples-'+population+'.tsv'))
                for row in roles:
                    if (row['fid'],row['iid']) in replacements:row['fid'],row['iid']=replacements[(row['fid'],row['iid'])]
                with (unicode_input/('samples-'+population+'.tsv')).open('w',encoding='utf-8',newline='') as stream:
                    writer=csv.DictWriter(stream,fieldnames=['fid','iid','fit','frequency_reference'],delimiter='\t');writer.writeheader();writer.writerows(roles)
            case=next(c for c in cases if c['label']=='founders-seed17');label=str(jvm)+'-unicode';out=args.out/('unicode-output-'+str(jvm))/'输出_β'
            result,command=run(java,label,options(case,out,unicode_input));assert result.returncode==0,(label,result.stderr.decode(errors='replace'))
            record['workflows'].append(dict(label=label,command=command,**verify(out,case,names=unicode_fam,role_path=unicode_input/'samples-founders.tsv')));save()
            base_case=next(c for c in cases if c['label']=='founders-seed17')
            controls=[('missing-home',dict(home=str(args.out/'absent-home'),bfile='NOT_READ'),3),
                ('approx-pairwise',{'pca-missing':'pairwise'},2),('wrong-task-option',{'model':'linear'},2),
                ('exact-seed',{'algorithm':'exact'},2),('no-seed',{'seed':None},2),
                ('bad-tolerance',{'maximum-relative-eigen-residual':'NaN'},2),
                ('strict-accuracy',{'maximum-relative-eigen-residual':'.00001'},2),
                ('quality-work',{'maximum-quality-products':'1'},2),
                ('few-reference',{'minimum-reference-calls':'124'},2),('missing-prohibited',{'missing-genotypes':'error'},2)]
            for name,changes,expected in controls:
                label=str(jvm)+'-control-'+name;out=args.out/label;o=options(base_case,out)
                for key,value in changes.items():
                    if value is None:o.pop(key,None)
                    else:o[key]=value
                result,command=run(java,label,o);assert result.returncode==expected,(label,result.returncode,result.stderr.decode(errors='replace'))
                assert not any((out/f).exists() for f in ['eigenvalues.tsv','eigenvectors.tsv','variant-weights.tsv'])
                if name in ['missing-home','strict-accuracy','quality-work','few-reference','missing-prohibited']:
                    assert (out/'WARNINGS.md').exists() and b'see ' in result.stderr
                if name=='strict-accuracy':assert any(r['accepted']=='false' for r in table(out/'eigen-quality.tsv'))
                if name=='missing-home':assert not (out/'upstream/input.bed').exists() and '--allow-install' in (out/'WARNINGS.md').read_text()
                if name=='quality-work':assert not (out/'upstream/frequency.stdout').exists()
                record['negative_controls'].append(dict(label=label,exit_code=result.returncode,command=command));save()
            low=args.out/('low-maf-input-'+str(jvm));shutil.copytree(inputs,low);raw=bytearray(packed);v=variants[0]
            fit_roles=table(low/'samples-all.tsv');selected={index[(r['fid'],r['iid'])] for r in fit_roles if r['frequency_reference']=='yes'};heterozygote=min(selected)
            for i in range(len(fam)):
                offset=3+v*stride+i//4;shift=2*(i%4);raw[offset]=(raw[offset]&~(3<<shift))|((2 if i==heterozygote else 3)<<shift)
            (low/'known.bed').write_bytes(raw);case=next(c for c in cases if c['label']=='plink2-pca-all-mean');label=str(jvm)+'-control-low-maf';out=args.out/label
            result,command=run(java,label,options(case,out,low));assert result.returncode==2 and 'under 0.005' in (out/'upstream/pca.stderr').read_text()
            assert not (out/'eigenvectors.tsv').exists();record['negative_controls'].append(dict(label=label,exit_code=result.returncode,command=command));save()
            completed=args.out/(str(jvm)+'-'+cases[0]['label']);prior={f.relative_to(completed).as_posix():sha(f) for f in completed.rglob('*') if f.is_file()}
            result,command=run(java,str(jvm)+'-control-existing-output',options(cases[0],completed));assert result.returncode==2
            assert prior=={f.relative_to(completed).as_posix():sha(f) for f in completed.rglob('*') if f.is_file()};record['negative_controls'].append(dict(label=str(jvm)+'-control-existing-output',exit_code=2,command=command));save()
        assert before=={f.name:sha(f) for f in inputs.iterdir() if f.is_file()}
        record['status']='passed-packaged-original-PCA-recipes-controls-and-independent-math'
        record['totals']={key:sum(r[key] for r in record['workflows']) for key in ['fitted_rows','sample_identity_rows','frequency_rows','weight_rows','weight_fields','eigenvalues','coordinate_fields']}
        save();print(json.dumps(dict(status=record['status'],workflows=len(record['workflows']),negative_controls=len(record['negative_controls']),totals=record['totals'])))
        return 0
    except Exception as failure:
        record.update(status='failed-retained',error=repr(failure));save()
        (args.out/'WARNINGS.md').write_text('# VALIDATION FAILED — DO NOT CREDIT AS PASSED\n\n'+repr(failure)+'\n\nInspect verification.json and retained process logs;correct the named cause and rerun to a new directory.\n')
        raise


if __name__=='__main__':
    raise SystemExit(main())
