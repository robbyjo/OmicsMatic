"""Read-only native FUSION component evidence check; never execute R or admit catalog credit."""
from pathlib import Path
import argparse
import csv
import hashlib
import io
import json
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def verify(jar=None):
    folder = ROOT / 'docs/literature/fusion-native-development'
    status = json.loads((folder / 'STATUS.json').read_bytes())
    require(status['status'] == 'PASS_FUSION_NATIVE_PREPARED_COMPONENT_ONLY', 'Wrong measured component scope')
    require(status['new_family_credit'] == 0 and not status['workflow_adapter_complete'] and
            not status['whole_tool_qualified'] and not status['whole_goal_complete'], 'False completion/credit claim')
    for name, identity in status['sources'].items():
        require(pin((ROOT / name).read_bytes()) == identity, 'Component source/fixture differs: ' + name)
    require(pin((folder / 'evidence.zip').read_bytes()) == status['zip'], 'Evidence ZIP differs')
    with zipfile.ZipFile(folder / 'evidence.zip') as archive:
        require(len(archive.namelist()) == len(set(archive.namelist())) and set(archive.namelist()) == set(status['files']),
                'Component evidence inventory differs')
        files = {n: archive.read(n) for n in archive.namelist()}
    require(all(pin(raw) == status['files'][n] for n, raw in files.items()), 'Component receipt bytes differ')
    original = json.loads(files['upstream/INTAKE.json'])
    require(original['commit'] == '9346c1222bffbb34499fa7a8e23c1b701b55cb05', 'Original source profile differs')
    fixture = ROOT / 'src/test/resources/org/jlinalg/fusion/fusion-9346c122-original.zip'
    with zipfile.ZipFile(fixture) as archive:
        require(len(archive.namelist()) == len(set(archive.namelist())), 'Duplicate reference fixture member')
        data = {n: archive.read(n) for n in archive.namelist()}
    reference = json.loads(data['STATUS.json'])
    require(set(data) == set(reference['files']) | {'STATUS.json'} and
            all(pin(data[n]) == identity for n, identity in reference['files'].items()), 'Reference fixture inventory differs')
    require(reference['original_source'] == {k:original['files']['FUSION.assoc_test.R'][k] for k in ['bytes','sha256']},
            'Selected original source differs')
    require(pin(data['original/FUSION.assoc_test.R']) == reference['original_source'] and
            data['generator.R'] == (ROOT / 'tools/literature/fusion_native_reference/generate.R').read_bytes(),
            'Original AST reference inputs differ')
    allele_rows = list(csv.DictReader(io.StringIO(data['alleles.tsv'].decode()), delimiter='\t'))
    rows = list(csv.DictReader(io.StringIO(data['associations.tsv'].decode()), delimiter='\t'))
    require(len(allele_rows) == 6561 and len(rows) == 136 and
            sum(r['source_status'] == 'COMPLETE' for r in rows) == 118 and
            sum(r['source_status'] == 'NO_RESULT' for r in rows) == 18, 'Original case/result population differs')
    tests = [ET.fromstring(raw) for n, raw in files.items() if n.startswith('normal/junit/')]
    require(sum(int(t.attrib['tests']) for t in tests) == 45 and
            all(int(t.attrib[k]) == 0 for t in tests for k in ['failures','errors','skipped']), 'Focused normal tests did not pass')
    normal = json.loads(files['normal/RESULT.json'])
    require(normal['exit_code'] == 0 and normal['source_inputs_unchanged'] and
            normal['development_jar'] == status['producer'], 'Normal producer receipt differs')
    packaged = json.loads(files['packaged/RESULT.json'])
    require(packaged['producer'] == status['producer'] and len(packaged['platforms']) == 3 and
            {p['platform'] for p in packaged['platforms']} == {'windows21','windows25','linux21'} and
            all(p['exit_code'] == 0 for p in packaged['platforms']) and
            packaged['previous_payloads_identical'] == 22832 and packaged['changed_previous_payloads'] == [],
            'Actual packaged execution/preservation scope differs')
    payloads = json.loads(files['packaged/PAYLOADS.json'])
    require(len(payloads['previous']) == 22832 and
            all(payloads['current'].get(n) == v for n,v in payloads['previous'].items()) and
            sorted(set(payloads['current']) - set(payloads['previous'])) == packaged['new_payloads'],
            'Complete previous/current payload maps differ')
    direct = json.loads(files['original-runtime/RESULT.json'])
    require(len(direct['cases']) == 5 and all(c['exit_code'] == 0 and c['original_output_present'] for c in direct['cases']) and
            not direct['adapter_implemented'] and direct['synthetic_inputs_only'], 'Original runtime intake scope differs')
    for case in ['coloc','beta-se']:
        lines = files['original-runtime/' + case + '.out.tsv'].decode().splitlines()
        header = lines[0].split('\t'); index = header.index('COLOC.PP4')
        require(len(lines) == 4 and all(0 <= float(line.split('\t')[index]) <= 1 for line in lines[1:]),
                'Direct original COLOC outputs incomplete')
    if jar is not None:
        require(pin(jar.read_bytes()) == status['producer'], 'Actual component producer differs')
        with zipfile.ZipFile(jar) as archive:
            actual = {n:hashlib.sha256(archive.read(n)).hexdigest() for n in archive.namelist() if not n.endswith('/')}
        require(actual == payloads['current'], 'Actual complete producer payload inventory differs')
    return dict(status=status['status'], normal_tests=45, original_allele_cases=6561,
                original_prepared_associations=136, original_complete=118, original_no_result=18,
                packaged_JVMs=['Windows21','Windows25','Linux21'], previous_payloads_identical=22832,
                original_R_association_intake_cases=5, actual_producer_checked=jar is not None,
                network_executed=False, R_executed=False, workflow_adapter_complete=False,
                whole_tool_qualified=False, new_family_credit=0, whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.jar), indent=2))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(('SKIP3: restore the exact named permitted prerequisite: ' if code == 3 else 'FAIL2: ') + str(error), file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
