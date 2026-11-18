#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare complete local original ATAC1.2.1 inputs and exact typed execution bindings.

Run with Linux Python. Originals remain unchanged. No installation or analysis.
An optional explicit URI map binds public example-design URLs to pinned local files;
this command never downloads data or infers files from their basenames.
"""
import argparse
import hashlib
import json
import os
import shutil
import sys
import traceback
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
RESOURCE = ROOT/'src/main/resources/org/jlinalg/omics/nfcore-atacseq121'
ACTION = ('Preparation only. Read WARNINGS.md and the path mapping. Run the exact original '
          'source through the typed adapter with a permitted managed Nextflow/private container '
          'runtime and current host report. Require completed execution and portable verification '
          'before downstream use. Missing/forbidden prerequisites skip the dependent analysis. '
          'Preserve originals and failed preparations; resolve the cause and use NEW paths.')


class Number(str):
    pass


def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate JSON key: '+key)
        result[key] = value
    return result


def encode(value):
    if isinstance(value, Number):
        return str(value)
    if isinstance(value, dict):
        return '{'+','.join(json.dumps(k, ensure_ascii=False)+':'+encode(v) for k, v in value.items())+'}'
    if isinstance(value, list):
        return '['+','.join(encode(v) for v in value)+']'
    return json.dumps(value, ensure_ascii=False, allow_nan=False)


def pin(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''):
            h.update(block)
    return dict(bytes=path.stat().st_size, sha256=h.hexdigest())


def save(path, value):
    path.write_text(encode(value)+'\n', encoding='utf-8', newline='\n')


def absolute(path):
    if not path.is_absolute() or path.resolve(strict=True) != path:
        raise ValueError('Declare an existing canonical unlinked absolute path: '+str(path))
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['workflow', 'data-root', 'design', 'fasta', 'gtf', 'container-home', 'images', 'out', 'run-root']:
        parser.add_argument('--'+name, required=True, type=Path)
    parser.add_argument('--macs-genome-size', required=True)
    parser.add_argument('--executor-cpus', type=int, required=True)
    parser.add_argument('--executor-memory-bytes', type=int, required=True)
    parser.add_argument('--max-memory', required=True)
    parser.add_argument('--max-time', required=True)
    for name in ['parameters', 'limits', 'uri-map']:
        parser.add_argument('--'+name, type=Path)
    parser.add_argument('--resource', action='append', default=[], help='Additional captured input ID=ABSOLUTE_PATH')
    parser.add_argument('--dot', type=Path, help='Optional explicitly bound Graphviz dot executable for the workflow SVG diagram')
    args = parser.parse_args()
    if sys.platform != 'linux':
        print('SKIP3 Linux path semantics are required. Run Linux Python within WSL; no preparation/analysis performed. '+ACTION, file=sys.stderr)
        return 3
    out = args.out.absolute()
    if out.exists() or out.is_symlink() or out.parent.resolve(strict=True) != out.parent:
        raise ValueError('Use a NEW canonical preparation directory')
    out.mkdir()
    try:
        if not 1 <= args.executor_cpus <= 1024 or not 16777216 <= args.executor_memory_bytes <= 1099511627776:
            raise ValueError('Invalid declared local executor capacity')
        run = args.run_root
        if not run.is_absolute() or run.resolve() != run or run.exists() or run.is_relative_to(out) or out.is_relative_to(run):
            raise ValueError('Require a disjoint NEW canonical run root')
        if any(not (c.isascii() and (c.isalnum() or c in '/_.-')) for c in str(run)):
            raise ValueError('Original unquoted pipeline profile requires an ASCII run root without shell metacharacters/whitespace')
        workflow, base = absolute(args.workflow), absolute(args.data_root)
        materialized = out/'materialized'
        installed, data = materialized/'workflow', materialized/'data'
        installed.mkdir(parents=True)
        data.mkdir()
        guards = {}
        lock = json.loads((RESOURCE/'source-lock.json').read_bytes(), object_pairs_hook=unique)
        for name, identity in lock['files'].items():
            source = workflow/name
            expected = {k: identity[k] for k in ['bytes', 'sha256']}
            if not source.is_file() or pin(source) != expected:
                raise ValueError('Original source differs: '+name)
            target = installed/name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            target.chmod(identity['mode'])
            if pin(target) != expected or pin(source) != expected:
                raise ValueError('Original source changed during copy: '+name)
            guards[str(source)] = expected
        copied = {}

        def stage(source):
            source = absolute(source)
            if not source.is_relative_to(base) or not source.is_file():
                raise ValueError('Complete regular input must remain under declared data root: '+str(source))
            if source in copied:
                return copied[source]
            identity = pin(source)
            target = data/source.relative_to(base)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            if pin(target) != identity or pin(source) != identity:
                raise ValueError('Complete input changed while staging: '+str(source))
            guards[str(source)] = identity
            copied[source] = target
            return target

        def captured(path):
            return str(run/'engine/workspace/resources/r0/materialized'/path.relative_to(materialized))

        uri_map = {}
        if args.uri_map:
            absolute(args.uri_map)
            guards[str(args.uri_map)] = pin(args.uri_map)
            uri_map = json.loads(args.uri_map.read_bytes(), object_pairs_hook=unique)
            if not isinstance(uri_map, dict) or any(not isinstance(k, str) or not isinstance(v, str) for k, v in uri_map.items()):
                raise ValueError('Require explicit exact URI-to-local-path object')
            shutil.copyfile(args.uri_map, out/'original-uri-map.json')
        design = absolute(args.design)
        original = design.read_bytes()
        guards[str(design)] = pin(design)
        (out/'original-design.csv').write_bytes(original)
        rows = original.decode('utf-8').splitlines()
        if not rows or rows[0] != 'group,replicate,fastq_1,fastq_2':
            raise ValueError('Require original four-column ATAC design schema')
        derived, mapping, used_uris = [rows[0]], [], set()
        for line in rows[1:]:
            cells = line.split(',')
            if len(cells) != 4:
                raise ValueError('This preparation requires four literal CSV fields per row, including empty read2 for single-end')
            changed, sources = list(cells), []
            for column in [2, 3]:
                if not cells[column]:
                    continue
                declared = cells[column].strip()
                if '://' in declared:
                    if declared not in uri_map:
                        raise ValueError('Public/original URI needs an explicit pinned local mapping: '+declared)
                    source = Path(uri_map[declared])
                    used_uris.add(declared)
                else:
                    source = Path(declared)
                    if not source.is_absolute():
                        source = base/source
                target = stage(source)
                changed[column] = captured(target)
                sources.append(source)
            if not sources or len(sources) == 2 and os.path.samefile(*sources):
                raise ValueError('Require complete distinct physical FASTQ mates')
            if cells[:2] != changed[:2]:
                raise ValueError('Non-path design cells changed')
            derived.append(','.join(changed))
            mapping.append(dict(original=cells, derived=changed))
        if used_uris != set(uri_map):
            raise ValueError('URI map contains unused/unmatched declarations')
        design_path = materialized/'design.csv'
        design_path.write_text('\n'.join(derived)+'\n', encoding='utf-8', newline='\n')
        fasta, gtf = stage(args.fasta), stage(args.gtf)
        external = materialized/'external-configs'
        external.mkdir()
        (external/'nfcore_custom.config').write_text('// No institutional overrides selected; original scientific defaults remain in force.\n', encoding='utf-8', newline='\n')
        resources = {key: dict(path=str(path)) for key, path in dict(bundle=materialized, design=design_path, fasta=fasta, gtf=gtf, external_configs=external).items()}
        for item in args.resource:
            key, name = item.split('=', 1)
            if key in resources or key == 'workflow':
                raise ValueError('Duplicate/reserved resource alias: '+key)
            path = absolute(Path(name))
            if path.is_dir():
                if not path.is_relative_to(base):
                    raise ValueError('Complete input directory lies outside data root')
                dest = data/path.relative_to(base)
                dest.mkdir(parents=True, exist_ok=True)
                for entry in path.rglob('*'):
                    if entry.is_symlink():
                        raise ValueError('Explicit resource directories require unlinked members')
                    if entry.is_file():
                        stage(entry)
            else:
                dest = stage(path)
            resources[key] = dict(path=str(dest))
        parameters = {}
        if args.parameters:
            absolute(args.parameters)
            guards[str(args.parameters)] = pin(args.parameters)
            parameters = json.loads(args.parameters.read_bytes(), parse_float=Number, object_pairs_hook=unique)
            shutil.copyfile(args.parameters, out/'original-parameters.json')
        reserved = {'input', 'fasta', 'gtf', 'custom_config_base', 'outdir', 'tracedir', 'macs_gsize', 'max_cpus', 'max_memory', 'max_time'}
        if not isinstance(parameters, dict) or reserved.intersection(parameters):
            raise ValueError('Use protected explicit path/genome/execution options')
        try:
            gsize = json.loads(args.macs_genome_size, parse_float=Number)
        except json.JSONDecodeError:
            gsize = args.macs_genome_size
        parameters.update(input={'$input': 'design'}, fasta={'$input': 'fasta'}, gtf={'$input': 'gtf'}, custom_config_base={'$input': 'external_configs'},
                          outdir={'$output': ''}, tracedir={'$output': 'pipeline_info'}, macs_gsize=gsize, max_cpus=args.executor_cpus,
                          max_memory=args.max_memory, max_time=args.max_time)
        home, images_path = absolute(args.container_home), absolute(args.images)
        guards[str(images_path)] = pin(images_path)
        images = json.loads(images_path.read_bytes(), object_pairs_hook=unique)
        image_profile = json.loads((RESOURCE/'images.json').read_bytes(), object_pairs_hook=unique)
        if set(images) != {'schema_version', 'images'} or images['schema_version'] != 1 or set(images['images']) != set(image_profile['images']):
            raise ValueError('Incomplete/unknown exact original image binding')
        quote = lambda text: "'"+str(text).replace('\\', '\\\\').replace("'", "\\'")+"'"
        overlay = "process.executor = 'local'\nexecutor.cpus = "+str(args.executor_cpus)+"\nexecutor.memory = '"+str(args.executor_memory_bytes)+" B'\nsingularity.cacheDir = "+quote(run/'engine/workspace/execution/container-cache')+"\nsingularity.autoMounts = true\nsingularity.runOptions = '--userns --no-home --pid'\nprocess {\n"
        for uri, row in image_profile['images'].items():
            image = absolute(Path(images['images'][uri]))
            if pin(image) != row['pin']:
                raise ValueError('Exact original image bytes differ')
            guards[str(image)] = row['pin']
            for name in row['process_names']:
                overlay += '  withName: '+quote(name)+' { container = '+quote(image)+' }\n'
        overlay += '}\n'
        (installed/'runtime').mkdir()
        (installed/'runtime/host.config').write_text(overlay, encoding='utf-8', newline='\n')
        limits = dict(wall_seconds=7200, cpu_seconds=7200, address_space_bytes=68719476736, workspace_bytes=274877906944,
                      processes=512, threads=16384, sampled_rss_bytes=34359738368, java_heap_mib=2048, java_cpus=args.executor_cpus,
                      poll_millis=1000, metadata_bytes=67108864)
        if args.limits:
            absolute(args.limits)
            guards[str(args.limits)] = pin(args.limits)
            shutil.copyfile(args.limits, out/'original-limits.json')
            limits.update(json.loads(args.limits.read_bytes(), object_pairs_hook=unique))
        tools = [dict(name='singularity', path=str(home/'bin/singularity'))]
        if args.dot:
            dot = absolute(args.dot)
            if not dot.is_file() or not os.access(dot, os.X_OK):
                raise PermissionError('The explicitly requested Graphviz dot executable is unavailable/forbidden')
            guards[str(dot)] = pin(dot)
            tools.append(dict(name='dot', path=str(dot)))
        recipe = dict(schema_version=1, engine_profile='linux-x86_64-nextflow-21.04.1', workflow=dict(path=str(installed), entrypoint='main.nf', configs=['nextflow.config', 'runtime/host.config'], profiles=['singularity']),
                      resources=resources, parameters=parameters, required_tools=tools,
                      outputs=[dict(path=name, kind='directory', allow_empty=False) for name in ['pipeline_info', 'fastqc', 'trim_galore', 'bwa', 'genome', 'multiqc', 'igv']], require_tasks=True, limits=limits)
        recipe_path = out/'engine-recipe.json'
        save(recipe_path, recipe)
        save(out/'request.json', dict(schema_version=1, pipeline_profile='nfcore-atacseq-1.2.1', engine_recipe=str(recipe_path), container_home=str(home), images=str(images_path), executor_cpus=args.executor_cpus, executor_memory_bytes=args.executor_memory_bytes))
        save(out/'path-bindings.json', mapping)
        if any(pin(Path(name)) != identity for name, identity in guards.items()):
            raise ValueError('Original input/source/image changed during complete preparation')
        state = dict(status='PASS_ATAC_SOURCE_DATA_PREPARATION_ONLY', source_version='1.2.1', author_engine_inferred=False,
                     original_files_unchanged=guards, request=pin(out/'request.json'), recipe=pin(recipe_path), scientific_analysis_executed=False, required_action=ACTION)
        code = 0
    except Exception as error:
        code = 3 if isinstance(error, PermissionError) else 2
        state = dict(status='SKIP3_PREPARATION_UNAVAILABLE' if code == 3 else 'FAIL2_PREPARATION_NO_RESULT', error=repr(error), traceback=traceback.format_exc(), required_action=ACTION)
    save(out/('COMPLETED.json' if code == 0 else 'FAILURE.json'), state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error', 'Complete source/input bytes and explicit path bindings retained.')+'\n\n'+ACTION+'\n', encoding='utf-8', newline='\n')
    print(state['status'], state.get('error', ''), flush=True)
    return code


if __name__ == '__main__':
    raise SystemExit(main())
