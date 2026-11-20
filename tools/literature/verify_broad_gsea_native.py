"""Read saved native Broad GSEA qualification; no Git/JVM/R/GSEA execution."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import io
import json
import math
import struct
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PACKET = ROOT / 'docs/literature/gsea-native-development'
FIXTURE = 'src/test/resources/org/jlinalg/enrichment/broad-gsea-originals.zip'
STATUS = 'PASS_NORMAL_JAR_NATIVE_BROAD_GSEA_ARITHMETIC_THREE_JVMS_AND_ORIGINAL_SOURCE_COUNTERPARTS'
OWN = ['src/main/java/org/jlinalg/enrichment/BroadGseaStatistics.java',
       'src/main/resources/META-INF/licenses/GSEA-DESKTOP-LICENSE.txt',
       'src/test/java/org/jlinalg/enrichment/BroadGseaStatisticsTest.java',
       'tools/literature/verify_broad_gsea_native.py',
       'tools/literature/gsea_original/README.md',
       'docs/vignettes/gsea-native-statistics.md',
       'docs/literature/gsea-native-development/README.md']
OWN += ['tools/literature/gsea_original/' + name for name in [
    'OriginalGseaProbe.java', 'OriginalPrimitiveProbe.java', 'CheckNativeBroadGsea.java',
    'CheckNativeBroadPrimitives.java', 'PackagedNativeChecks.java']]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def safe(name):
    require(isinstance(name, str) and name and '\\' not in name and ':' not in name, 'Unsafe evidence path')
    path = PurePosixPath(name)
    require(not path.is_absolute() and str(path) == name and all(p not in ['', '.', '..'] for p in path.parts), 'Unsafe evidence path')
    return name


def unpack(raw):
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        infos = archive.infolist()
        require(len(infos) <= 1000 and len(infos) == len({i.filename for i in infos}), 'Duplicate/excess archive members')
        require(sum(i.file_size for i in infos) <= 50_000_000, 'Saved archive exceeds decoded capacity')
        files = {}
        for item in infos:
            safe(item.filename)
            require(not item.is_dir() and item.external_attr >> 28 != 10, 'Directory/link in saved archive')
            files[item.filename] = archive.read(item)
        return files


def floats(text):
    return [struct.unpack('!f', struct.pack('!f', float(x)))[0] for x in text.split(',')]


def case_counts(files):
    result = dict(walks=0, families=0, walk_refusals=0, family_refusals=0, workflow_forms=0,
                  primitive_fields=0, workflow_fields=0)
    for name, raw in files.items():
        if name.endswith('/primitives.tsv'):
            rows = [line.split('\t') for line in raw.decode().splitlines()[1:]]
            require(sum(r[0] == 'WALK' for r in rows) == 1280 and sum(r[0] == 'FAMILY' for r in rows) == 240,
                    'Different original primitive case population')
            for row in rows:
                if row[0] == 'WALK':
                    mass = sum(floats(row[11]))
                    if not math.isfinite(mass) or abs(mass - 1) > 5e-6:
                        result['walk_refusals'] += 1
                    else:
                        result['walks'] += 1
                        result['primitive_fields'] += 3 + 2 * len(row[4].split(',')) + len(row[3].split(','))
                elif row[0] == 'FAMILY':
                    if row[4] != 'FINITE':
                        require(row[4] in ['ORIGINAL_ERROR', 'NONFINITE'], 'Different original failed family status')
                        result['family_refusals'] += 1
                    else:
                        m = len(row[2].split(','))
                        require(all(math.isfinite(x) for field in row[5:9] for x in floats(field)), 'Finite original family contains nonfinite fields')
                        result['families'] += 1
                        result['primitive_fields'] += 4 * m + sum(len(r.split(',')) for r in row[9].split(';'))
        elif any(name.endswith('/' + mode + '.tsv') for mode in ['preranked', 'expression-phenotype', 'expression-geneset']):
            rows = [line.split('\t') for line in raw.decode().splitlines()]
            n = sum(r[0] == 'RANK' for r in rows)
            sets = [r for r in rows if r[0] == 'MEMBERS']
            require(n == 300 and len(sets) == 5 and sum(r[0] == 'SCORE' for r in rows) == 5, 'Different original workflow family')
            require(all(len(r) - 2 == 10000 for r in rows if r[0] == 'NULL'), 'Different original permutation count')
            result['workflow_forms'] += 1
            result['workflow_fields'] += sum(7 + n + 2 * (len(r) - 2) for r in sets)
    return result


def verify(folder=PACKET, jar=None):
    require(__debug__ and sys.version_info >= (3, 11), 'Use Python3.11+ without -O')
    q = json.loads((folder / 'QUALIFICATION.json').read_bytes())
    require(q['schema_version'] == 1 and q['status'] == STATUS and set(q['source_code']) == set(OWN), 'Different native qualification/source population')
    for name, wanted in q['source_code'].items():
        require(pin((ROOT / safe(name)).read_bytes()) == wanted, 'Changed qualified source: ' + name)
    fixture_raw = (ROOT / FIXTURE).read_bytes()
    require(pin(fixture_raw) == q['fixture'], 'Changed original reference fixture')
    original = unpack(fixture_raw)
    manifest = {}
    for line in original['SHA256SUMS'].decode().splitlines():
        digest, name = line.split('  ', 1)
        require(name not in manifest, 'Duplicate original manifest member')
        manifest[safe(name)] = digest
    require(set(original) == set(manifest) | {'SHA256SUMS'}, 'Different original fixture population')
    for name, digest in manifest.items():
        require(pin(original[name])['sha256'] == digest, 'Changed original fixture member: ' + name)
    for name in ['OriginalGseaProbe.java', 'OriginalPrimitiveProbe.java']:
        require(original['reference-source/' + name] == (ROOT / 'tools/literature/gsea_original' / name).read_bytes(), 'Different original reference program')
    counts = case_counts(original)
    require(counts == dict(walks=2415, families=444, walk_refusals=1425, family_refusals=276,
            workflow_forms=9, primitive_fields=338961, workflow_fields=17325), 'Original case/field counts differ')
    raw = (folder / 'checks.zip').read_bytes()
    require(pin(raw) == q['checks']['archive'], 'Changed saved native qualification archive')
    evidence = unpack(raw)
    require({n: pin(v) for n, v in evidence.items()} == q['checks']['files'], 'Saved native evidence inventory differs')
    producer = json.loads(evidence['producer/PRODUCER.json'])
    completed = json.loads(evidence['producer/COMPLETED.json'])
    require(completed['status'] == STATUS and completed['producer'] == producer['producer'] == q['producer'], 'Different normal producer identity')
    require(completed['unchanged_predecessor_members'] == producer['unchanged_predecessor_members'] == 23485,
            'Different unchanged predecessor population')
    require(completed['project_RUN_VERIFY_REPLAY_qualified'] is False and completed['new_catalog_credit'] == 0,
            'Changed qualification scope')
    for host in ['windows-java21', 'windows-java25', 'linux-java21']:
        process = json.loads(evidence['runs/' + host + '/PROCESS.json'])
        require(process['exit_code'] == 0 and process['native_production_class_from_exact_normal_JAR'] is True,
                'Failed or different packaged native execution: ' + host)
        output = evidence['runs/' + host + '/stdout'].decode()
        require('PASS_NATIVE_BROAD_GSEA_ORIGINAL_OBJECT_COMPARISONS\t17325' in output
                and 'comparisons=338961\twalks=2415\tfamilies=444\twalkRefusals=1425\tfamilyRefusals=276' in output
                and 'PASS_EXACT_NORMAL_JAR_NATIVE_ONLY_SOURCE_ORIGIN' in output, 'Incomplete native comparison output: ' + host)
    require(json.loads(evidence['gradle/PROCESS-EXIT.json'])['exit_code'] == 0, 'Normal source build/tests failed')
    require(b'tests="2" skipped="0" failures="0" errors="0"' in evidence['gradle/TEST.xml'], 'Focused source-counterpart JUnit tests failed/differ')
    if jar is not None:
        require(pin(Path(jar).read_bytes()) == q['producer'], 'Different optional exact normal producer')
        with zipfile.ZipFile(jar) as archive:
            require(len(archive.namelist()) == len(producer['members']) and set(archive.namelist()) == set(producer['members']), 'Different producer member population')
            for name, wanted in producer['members'].items():
                require(pin(archive.read(name)) == wanted, 'Different optional producer member: ' + name)
    return dict(status='PASS_SAVED_NATIVE_BROAD_GSEA_SOURCE_COUNTERPART_QUALIFICATION',
                original_case_counts=counts, exact_field_comparisons_per_JVM=356286,
                source_bound_refusals_per_JVM=1701, JVMs=3, optional_exact_jar_checked=jar is not None,
                unchanged_predecessor_members=23485, new_catalog_credit=0,
                Java_or_original_statistics_rerun=False, project_file_workflow_qualified=False), evidence


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--packet', type=Path, default=PACKET)
    parser.add_argument('--jar', type=Path)
    args = parser.parse_args()
    try:
        require(not args.out.exists() and not args.out.resolve().is_relative_to(args.packet.resolve()), 'Choose NEW output outside the packet')
        result, _ = verify(args.packet, args.jar)
        args.out.mkdir(parents=True, exist_ok=False)
        (args.out / 'READBACK.json').write_bytes((json.dumps(result, indent=2) + '\n').encode())
        print(json.dumps(result, indent=2))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(('SKIP3: named exact prerequisite missing/inaccessible. ' if code == 3 else 'FAIL2: no valid saved native qualification. ')
              + str(error) + '\nRestore the named exact permitted source/proof/producer or retain the skip. Preserve affected evidence, '
              'correct integrity/schema/capacity/output causes and use NEW output; do not refresh old hashes.', file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
