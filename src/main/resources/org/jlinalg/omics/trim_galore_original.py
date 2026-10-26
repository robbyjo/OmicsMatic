"""Run unmodified legacy Trim Galore with fixed, audited upstream entry points.

Only process transport/reporting lives here. Adapter/quality algorithms are the
original Cutadapt package; final read operations are the original Perl script.
Java validates inputs, the sealed dependency profile and every emitted stream.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys

MARKER = '.jlinalg-trim-galore.json'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def cygpath(path):
    value = str(path.resolve()).replace('\\', '/')
    if len(value) > 2 and value[1] == ':':
        return '/cygdrive/' + value[0].lower() + value[2:]
    return value


def configuration(path):
    config = json.loads(path.read_text(encoding='utf-8'))
    marker = Path(config['home']) / MARKER
    if digest(marker) != config['marker_sha256']:
        raise RuntimeError('Dependency profile changed; skip the affected analysis and restore/reseal a permitted runtime')
    profile = json.loads(marker.read_text(encoding='utf-8'))
    if profile['profile'] != 'windows-cygwin-perl-native-python' or config['release'] not in ['0.6.7', '0.6.10']:
        raise RuntimeError('Unsupported original profile or release')
    return config, profile


def process_environment(profile, cwd):
    result = dict(os.environ)
    for key in ['PERL5LIB', 'PERLLIB', 'PERL5OPT', 'PYTHONPATH', 'PYTHONHOME', 'CLASSPATH', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS']:
        result.pop(key, None)
    result.update(LC_ALL='C', PERL_HASH_SEED='0', PERL_PERTURB_KEYS='0', PYTHONNOUSERSITE='1', PYTHONDONTWRITEBYTECODE='1')
    result['PATH'] = os.pathsep.join([str(cwd / 'bin'), str(Path(profile['perl']).parent), str(Path(profile['python']).parent), str(Path(os.environ.get('SystemRoot', 'C:/Windows')) / 'System32')])
    return result


def python_command(profile, cwd):
    return [profile['python'], '-I', '-S', '-B', '-X', 'utf8', '-X', 'pycache_prefix=' + str(cwd / 'ignored-python-cache'), str(Path(__file__).resolve())]


def reserve(cwd, prefix):
    folder = cwd / 'transport-audit'; folder.mkdir(exist_ok=True)
    for index in range(1000):
        path = folder / (prefix + '-%03d.json' % index)
        try:
            descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600); os.close(descriptor)
            return path
        except FileExistsError:
            pass
    raise RuntimeError('Original-process transport invocation limit exceeded')


def cutadapt_child(config, profile, arguments, record):
    """Call the original console-entry API without site startup or unrelated .pth files."""
    handles = []
    try:
        for directory in [Path(profile['python_base']), Path(profile['python_base']) / 'DLLs', Path(profile['python_base']) / 'Library/bin']:
            if directory.is_dir() and hasattr(os, 'add_dll_directory'):
                handles.append(os.add_dll_directory(str(directory)))
        sys.path.insert(0, profile['python_site'])
        sys.stdout.reconfigure(encoding='utf-8', newline='\n')
        sys.argv = ['cutadapt', *arguments]
        from cutadapt.cli import main
        main(arguments)
    finally:
        files = sorted({str(Path(module.__file__).resolve()) for module in sys.modules.values() if getattr(module, '__file__', None) and Path(module.__file__).is_file()})
        allowed = profile['external_files']
        unexpected = [p for p in files if Path(p).as_posix() not in allowed and Path(p).resolve() != Path(__file__).resolve()]
        record.write_text(json.dumps(dict(loaded_module_files=files, unexpected_module_files=unexpected), indent=2) + '\n', encoding='utf-8')
        for handle in handles:
            handle.close()
        if unexpected:
            raise RuntimeError('Original Cutadapt imported unsealed module files: ' + repr(unexpected))


def cutadapt_bridge(config_path, config, profile, arguments):
    cwd = Path.cwd(); path = reserve(cwd, 'cutadapt'); base = path.with_suffix('')
    raw, normalized, stderr, modules = [base.with_suffix(suffix) for suffix in ['.stdout-raw', '.stdout-lf', '.stderr-raw', '.modules.json']]
    command = python_command(profile, cwd) + ['--mode', 'cutadapt-child', '--configuration', str(config_path.resolve()), '--record', str(modules.resolve()), '--', *arguments]
    exit_code, failure = 2, None
    try:
        with raw.open('xb') as unmodified, normalized.open('xb') as lf, stderr.open('xb') as errors:
            child = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=errors, env=process_environment(profile, cwd))
            pending = b''
            try:
                for block in iter(lambda: child.stdout.read(65536), b''):
                    unmodified.write(block); block = pending + block
                    pending = b'\r' if block.endswith(b'\r') else b''
                    if pending:
                        block = block[:-1]
                    data = block.replace(b'\r\n', b'\n'); lf.write(data); sys.stdout.buffer.write(data)
                if pending:
                    lf.write(pending); sys.stdout.buffer.write(pending)
                sys.stdout.buffer.flush()
            except BaseException:
                child.kill(); child.wait(); raise
            finally:
                child.stdout.close()
            exit_code = child.wait()
        with stderr.open('rb') as errors:
            for block in iter(lambda: errors.read(65536), b''):
                sys.stderr.buffer.write(block)
        sys.stderr.buffer.flush()
    except Exception as error:
        failure = repr(error); print('ERROR: Original Cutadapt transport failed: ' + failure, file=sys.stderr)
    finally:
        path.write_text(json.dumps(dict(arguments=arguments, command=command, exit_code=exit_code, error=failure,
            files={p.name: digest(p) for p in [raw, normalized, stderr, modules] if p.is_file()},
            scope='Original Cutadapt5.2 console-entry API;no site startup,algorithm edits or argument substitution. Only stdout CRLF pairs become LF;all original streams and exit code retained.'), indent=2) + '\n', encoding='utf-8')
    return exit_code


def fastqc_bridge(config, profile, arguments):
    qc = profile.get('fastqc'); cwd = Path.cwd(); path = reserve(cwd, 'fastqc')
    if not qc or len(arguments) != 1 or not re.fullmatch(r'reads[12]_(?:trimmed|val_[12])\.fq', arguments[0]):
        raise RuntimeError('FastQC dependency unavailable or unexpected original output argument; affected QC is not run')
    classpath = os.pathsep.join([qc['home'], *[str(Path(qc['home']) / jar) for jar in qc['jars']]])
    command = [qc['java'], '-Xmx' + str(config.get('fastqc_memory_mb', 512)) + 'm', '-XX:ActiveProcessorCount=1', '-XX:+ExitOnOutOfMemoryError',
               '-Djava.awt.headless=true', '-Dfastqc.output_dir=' + str(cwd), '-Dfastqc.threads=1', '-Dfastqc.sequence_format=fastq', '-cp', classpath,
               'uk.ac.babraham.FastQC.FastQCApplication', *arguments]
    result = subprocess.run(command, capture_output=True, env=process_environment(profile, cwd))
    stdout, stderr = path.with_suffix('.stdout-raw'), path.with_suffix('.stderr-raw')
    stdout.write_bytes(result.stdout); stderr.write_bytes(result.stderr)
    sys.stdout.buffer.write(result.stdout); sys.stderr.buffer.write(result.stderr)
    path.write_text(json.dumps(dict(arguments=arguments, command=command, exit_code=result.returncode,
                                   files={p.name: digest(p) for p in [stdout, stderr]}), indent=2) + '\n', encoding='utf-8')
    return result.returncode


def original_arguments(config):
    mode = config['operation']; paired = config['paired']; settings = config['settings']
    if mode not in ['trim', 'hardtrim5', 'hardtrim3'] or type(paired) is not bool:
        raise ValueError('Unsupported original operation or pairing contract')
    command = ['--path_to_cutadapt', './bin/cutadapt', '--dont_gzip']
    if mode.startswith('hardtrim'):
        bases = settings['bases']
        if type(bases) is not int or not 1 <= bases <= 999 or set(settings) != {'bases'} or config.get('fastqc'):
            raise ValueError('Hardtrim requires only bases1..999;no other filtering or FastQC options')
        command.extend(['--' + mode, str(bases)])
    else:
        allowed = {'phred', 'quality', 'nextseq', 'minimum_length', 'maximum_length', 'maximum_n', 'overlap', 'error_rate', 'trim_n', 'adapter_kind',
                   'adapter1', 'adapter2', 'already_trimmed_limit', 'clip_r1', 'clip_r2', 'three_prime_clip_r1', 'three_prime_clip_r2', 'retain_unpaired', 'orphan_min1', 'orphan_min2'}
        if set(settings) - allowed or settings['phred'] not in [33, 64]:
            raise ValueError('Unknown or invalid typed original setting')
        command.append('--phred' + str(settings['phred']))
        mapping = {'quality': '--quality', 'nextseq': '--nextseq', 'minimum_length': '--length', 'maximum_length': '--max_length', 'maximum_n': '--max_n',
                   'overlap': '--stringency', 'error_rate': '--error_rate', 'already_trimmed_limit': '--consider_already_trimmed', 'clip_r1': '--clip_R1',
                   'clip_r2': '--clip_R2', 'three_prime_clip_r1': '--three_prime_clip_R1', 'three_prime_clip_r2': '--three_prime_clip_R2', 'orphan_min1': '--length_1', 'orphan_min2': '--length_2'}
        for key, flag in mapping.items():
            if key in settings:
                value = settings[key]
                if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value < 0:
                    raise ValueError('Non-numeric original option: ' + key)
                command.extend([flag, str(value)])
        kind = settings['adapter_kind']
        presets = {'illumina': '--illumina', 'nextera': '--nextera', 'small-rna': '--small_rna', 'stranded-illumina': '--stranded_illumina'}
        if kind == 'none':
            command.extend(['-a', 'X'])
        elif kind in presets:
            if kind == 'stranded-illumina' and config['release'] == '0.6.7':
                raise ValueError('Original0.6.7 has no stranded-illumina preset')
            command.append(presets[kind])
        elif kind == 'custom':
            if not re.fullmatch(r'[ACGTNacgtn]{1,1000}', settings.get('adapter1', '')):
                raise ValueError('Custom adapter1 must be literal DNA1..1000bases')
            command.extend(['-a', settings['adapter1']])
        elif kind != 'auto':
            raise ValueError('Unsupported adapter kind')
        if 'adapter2' in settings:
            if not paired or not re.fullmatch(r'[ACGTNacgtn]{1,1000}', settings['adapter2']):
                raise ValueError('Custom adapter2 requires paired literal DNA1..1000bases')
            command.extend(['--adapter2', settings['adapter2']])
        if settings.get('trim_n'):
            command.append('--trim-n')
        if paired:
            command.append('--paired')
        if settings.get('retain_unpaired'):
            command.append('--retain_unpaired')
        if config.get('fastqc'):
            command.append('--fastqc')
    command.extend(['reads1.fastq'] + (['reads2.fastq'] if paired else []))
    return command


def run(config_path, config, profile):
    cwd = Path.cwd(); binary = cwd / 'bin'; binary.mkdir()
    if Path(config['work_directory']).resolve() != cwd or not (cwd / 'reads1.fastq').is_file() or config['paired'] and not (cwd / 'reads2.fastq').is_file():
        raise RuntimeError('Require staged canonical input in the declared original-work directory')
    for name, mode in [('cutadapt', 'cutadapt'), ('fastqc', 'fastqc')]:
        command = [cygpath(Path(profile['python'])), '-I', '-S', '-B', '-X', 'utf8', '-X', 'pycache_prefix=' + str(cwd / 'ignored-python-cache'),
                   str(Path(__file__).resolve()), '--mode', mode, '--configuration', str(config_path.resolve()), '--']
        (binary / name).write_text('#!/bin/sh\nexec ' + ' '.join(shlex.quote(str(p)) for p in command) + ' "$@"\n', encoding='utf-8', newline='\n')
    env = process_environment(profile, cwd)
    chmod = subprocess.run([profile['perl'], '-e', 'chmod 0755, @ARGV or die $!;', 'bin/cutadapt', 'bin/fastqc'], env=env, capture_output=True)
    if chmod.returncode:
        raise RuntimeError('Cannot make private POSIX transport launchers executable: ' + repr(chmod.stderr))
    source = Path(config['home']) / config['release'] / 'trim_galore'
    command = [profile['perl'], cygpath(source), *original_arguments(config)]
    with (cwd / 'original.stdout').open('xb') as stdout, (cwd / 'original.stderr').open('xb') as stderr:
        child = subprocess.run(command, env=env, stdout=stdout, stderr=stderr)
    records = []
    for path in sorted((cwd / 'transport-audit').glob('*.json')):
        if path.name.endswith('.modules.json'):
            continue
        item = json.loads(path.read_text(encoding='utf-8')); records.append(dict(file=path.name, exit_code=item['exit_code'], arguments=item['arguments']))
    failure = child.returncode != 0 or any(r['exit_code'] != 0 for r in records)
    result = dict(status='failed' if failure else 'original-processes-completed-not-yet-java-audited', original_command=command, original_exit_code=child.returncode,
                  invocations=records, driver_sha256=digest(Path(__file__)), source_sha256=digest(source), marker_sha256=digest(Path(config['home']) / MARKER),
                  scope='Actual unmodified original Perl script and original Cutadapt5.2;original FastQC if requested. Single core;safe staged relative filenames;no inferred adapter flags,site startup or algorithm modification. Java must independently validate every output before publication.')
    (cwd / 'driver-run.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    return 2 if failure else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mode', choices=['run', 'cutadapt', 'cutadapt-child', 'fastqc'], required=True)
    parser.add_argument('--configuration', type=Path, required=True)
    parser.add_argument('--record', type=Path)
    parser.add_argument('arguments', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    config, profile = configuration(args.configuration)
    arguments = args.arguments[1:] if args.arguments[:1] == ['--'] else args.arguments
    if args.mode == 'run':
        return run(args.configuration, config, profile)
    if args.mode == 'cutadapt-child':
        if args.record is None:
            raise ValueError('Original entry-point module inventory destination required')
        cutadapt_child(config, profile, arguments, args.record); return 0
    if args.mode == 'cutadapt':
        return cutadapt_bridge(args.configuration, config, profile, arguments)
    return fastqc_bridge(config, profile, arguments)


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception as failure:
        print('ERROR: Original Trim Galore process failed: ' + repr(failure) + '. Required action: inspect retained original/transport logs and correct the named dependency or input before retrying.', file=sys.stderr)
        raise SystemExit(2)
