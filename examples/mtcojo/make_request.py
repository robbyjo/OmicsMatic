#!/usr/bin/env python3
"""Create a NEW explicit GCTA1.94.1 mtCOJO request from prepared data; no analysis or download."""
from pathlib import Path
import argparse
import json
import math


def assignment(value):
    name, separator, data = value.partition('=')
    if not separator or not name or not data or any(c.isspace() or c == ',' for c in name):
        raise argparse.ArgumentTypeError('Use literal NAME=VALUE; names cannot contain whitespace or comma')
    return name, data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', type=assignment, required=True, metavar='NAME=SUMMARY_PATH')
    parser.add_argument('--covariate', type=assignment, action='append', required=True, metavar='NAME=SUMMARY_PATH')
    parser.add_argument('--genotype-prefix', type=Path, required=True, help='Prepared PLINK prefix before .bed/.bim/.fam')
    parser.add_argument('--reference-ld-prefix', required=True, help='Prefix before CHR.l2.ldscore and CHR.l2.M_5_50')
    parser.add_argument('--weight-ld-prefix', required=True, help='Prefix before CHR.l2.ldscore')
    parser.add_argument('--trait-compression', choices=['none', 'gzip'], default='none')
    parser.add_argument('--ld-compression', choices=['none', 'gzip'], default='none')
    parser.add_argument('--causal-effect-mode', choices=['GSMR', 'supplied'], required=True)
    parser.add_argument('--causal-effect', type=assignment, action='append', default=[], metavar='NAME=NUMBER')
    parser.add_argument('--prevalence', type=assignment, action='append', default=[], metavar='NAME=SAMPLE,POPULATION',
                        help='Retained metadata only: this source profile performs no liability conversion')
    parser.add_argument('--effect-scale', choices=['as_supplied'], required=True)
    parser.add_argument('--runtime', type=Path, required=True, help='Exact qualified GCTA executable; Windows needs adjacent libomp.dll')
    parser.add_argument('--preparation-note', required=True, help='Describe input preparation, reference population and effect scale')
    parser.add_argument('--analysis-out', type=Path, required=True, help='NEW analysis directory')
    parser.add_argument('--request-out', type=Path, required=True, help='NEW UTF-8 JSON file')
    parser.add_argument('--gwas-p', type=float)
    parser.add_argument('--diff-frequency', type=float)
    parser.add_argument('--minimum-instruments', type=int)
    parser.add_argument('--clump-kb', type=int)
    parser.add_argument('--clump-r2', type=float)
    parser.add_argument('--heidi-p', type=float)
    parser.add_argument('--ld-fdr', type=float, help='Source default 0.05 (flag omitted), or explicit integer 0/1')
    parser.add_argument('--timeout-seconds', type=int)
    args = parser.parse_args()
    if not 1 <= len(args.covariate) <= 16:
        parser.error('Require 1..16 covariate traits')
    traits = [args.target, *args.covariate]
    names = [name for name, _ in traits]
    if len(set(names)) != len(names):
        parser.error('Trait names must be unique; the first trait is the target')
    if args.analysis_out.exists():
        parser.error('Preserve existing output; choose a NEW --analysis-out')
    effects = {}
    for name, value in args.causal_effect:
        try:
            number = float(value)
        except ValueError:
            parser.error('Causal effects must be finite numbers')
        if name in effects or not math.isfinite(number) or abs(number) >= 100:
            parser.error('Require unique finite causal effects with abs(bxy)<100; 100 is an original GSMR sentinel')
        effects[name] = number
    gsmr_fields = ['minimum_instruments', 'clump_kb', 'clump_r2', 'heidi_p', 'ld_fdr']
    if args.causal_effect_mode == 'supplied':
        if set(effects) != set(names[1:]):
            parser.error('Supplied mode needs exactly one --causal-effect for every covariate')
        if any(getattr(args, key) is not None for key in gsmr_fields):
            parser.error('GSMR-only settings are unused in supplied mode')
    elif effects:
        parser.error('GSMR mode cannot also supply causal effects')
    if args.ld_fdr is not None and args.ld_fdr not in [.05, 0, 1]:
        parser.error('This original source cannot honor other fractional LD-FDR settings')
    prevalence = {}
    for name, value in args.prevalence:
        try:
            sample, population = [float(v) for v in value.split(',')]
        except ValueError:
            parser.error('Use --prevalence NAME=SAMPLE,POPULATION')
        if name not in names or name in prevalence or not (0 < sample < 1 and 0 < population < 1):
            parser.error('Require unique known traits and both prevalences strictly between zero and one')
        prevalence[name] = dict(sample_prevalence=sample, population_prevalence=population)
    request = dict(schema_version=1, mode='RUN', profile='GCTA-1.94.1', engine='original', model='MTCOJO',
                   out=str(args.analysis_out.resolve()), runtime=str(args.runtime.resolve()),
                   preparation_note=args.preparation_note, effect_scale=args.effect_scale,
                   causal_effect_mode=args.causal_effect_mode,
                   genotypes={suffix: str(Path(str(args.genotype_prefix) + '.' + suffix).resolve()) for suffix in ['bed', 'bim', 'fam']},
                   traits=[dict(name=name, summary=str(Path(path).resolve()), compression=args.trait_compression,
                                **prevalence.get(name, {})) for name, path in traits])
    suffix = '.gz' if args.ld_compression == 'gzip' else ''
    request['reference_ld'] = [dict(chromosome=c, scores=str(Path(args.reference_ld_prefix + str(c) + '.l2.ldscore' + suffix).resolve()),
                                    markers=str(Path(args.reference_ld_prefix + str(c) + '.l2.M_5_50').resolve()), compression=args.ld_compression) for c in range(1, 23)]
    request['weight_ld'] = [dict(chromosome=c, scores=str(Path(args.weight_ld_prefix + str(c) + '.l2.ldscore' + suffix).resolve()),
                                 compression=args.ld_compression) for c in range(1, 23)]
    if args.causal_effect_mode == 'supplied':
        request['causal_effects'] = effects
    for key in ['gwas_p', 'diff_frequency', *gsmr_fields, 'timeout_seconds']:
        if getattr(args, key) is not None:
            request[key] = getattr(args, key)
    try:
        encoded = json.dumps(request, indent=2, allow_nan=False) + '\n'
    except ValueError:
        parser.error('Require finite numeric settings; NaN and infinity cannot be encoded as scientific parameters')
    args.request_out.parent.mkdir(parents=True, exist_ok=True)
    try:
        with args.request_out.open('x', encoding='utf-8', newline='\n') as stream:
            stream.write(encoded)
    except FileExistsError:
        parser.error('Preserve the existing request file; choose a NEW --request-out')
    print('Wrote request; no analysis has run: ' + str(args.request_out.resolve()))
    print('Inspect the JSON, then run GctaWorkflowCli --request with this file.')
    if prevalence:
        print('WARNING: prevalences are retained metadata only; no liability conversion is claimed.')


if __name__ == '__main__':
    main()
