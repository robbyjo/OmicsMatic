"""Read this saved handover; never execute GSEA, saved helpers or installations."""
from pathlib import Path, PurePosixPath
import argparse
import gzip
import hashlib
import json
import re
import sys

PACKET = Path(__file__).resolve().parent
BASE = '0de612c6182b87d42a1d574af5f06a99630a0924'
COMMITS = {'4.4.0': '2cfcbd9856cd8e47b39994063ff717708631cfcf',
           '4.3.2': 'e1ae61cd83ddb14878ece6388b9c1da92594a4c1'}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def safe(name):
    require(isinstance(name, str) and name and '\\' not in name and ':' not in name,
            'Unsafe saved path')
    path = PurePosixPath(name)
    require(not path.is_absolute() and str(path) == name
            and all(part not in ['', '.', '..'] for part in path.parts), 'Unsafe saved path')
    return name


def pin(path):
    with Path(path).open('rb') as stream:
        return dict(bytes=Path(path).stat().st_size,
                    sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def read(name):
    return json.loads((PACKET / safe(name)).read_bytes())


def verify(primary_cache=None, local_intake=None):
    require(__debug__ and sys.version_info >= (3, 11), 'Use Python 3.11+ without -O')
    manifest = {}
    for line in (PACKET / 'SHA256SUMS').read_text(encoding='utf-8').splitlines():
        require(bool(re.fullmatch(r'[0-9a-f]{64}  .+', line)), 'Malformed packet manifest')
        digest, name = line.split('  ', 1)
        safe(name)
        require(name not in manifest and name != 'SHA256SUMS', 'Duplicate/self manifest path')
        manifest[name] = digest
    actual = {p.relative_to(PACKET).as_posix() for p in PACKET.rglob('*') if p.is_file()}
    require(actual == set(manifest) | {'SHA256SUMS'}, 'Different packet file population')
    for name, wanted in manifest.items():
        require(pin(PACKET / name)['sha256'] == wanted, 'Changed packet file: ' + name)
    candidates = read('CANDIDATE-ANCHORS.json')
    require(candidates['base_commit'] == BASE and candidates['papers_scanned'] == 250,
            'Different frozen candidate intake')
    identities = candidates['source_identities']
    require(len(identities) == len({p['paper_id'] for p in identities}) == 250,
            'Different source identity population')
    require(candidates['candidate_papers'] == len(candidates['candidates']) == 50,
            'Different lexical candidate population')
    existing = {p['paper_id'] for p in candidates['candidates'] if p['existing_observation_present']}
    require(existing == set(candidates['original_observations']) and len(existing) == 19,
            'Different original observation population')
    require(candidates['canonical_metadata_modified'] is False
            and candidates['new_reviews_appended'] == candidates['new_family_credit'] == 0
            and candidates['private_full_text_included'] is False, 'Candidate intake changes scope')
    for row in candidates['candidates']:
        for anchor in row['anchors']:
            require('text' not in anchor and bool(re.fullmatch(r'[0-9a-f]{64}', anchor['paragraph_sha256'])),
                    'Primary paragraphs must stay outside the packet')
    baseline = read('CANONICAL-BASELINE.json')
    require(baseline['base'] == BASE and len(baseline['files']) == 15
            and baseline['catalog_mutated'] is False, 'Different saved canonical baseline')
    tags = read('OFFICIAL-TAGS.json')['tags']
    require(tags['v4.3.3'] == tags['v4.3.2'] == COMMITS['4.3.2'], 'Different historical source aliases')
    local_files = 0
    for version, count in [('4.4.0', 698), ('4.3.2', 711)]:
        source = read('SOURCE-' + version + '.json')
        require(source['commit'] == tags['v' + version] == COMMITS[version]
                and source['requested_tag'] == 'v' + version
                and source['source_files'] == len(source['files']) == count
                and source['source_modified'] is False, 'Different saved source intake')
        for name in source['files']:
            safe(name)
        if local_intake is not None:
            folder = local_intake / 'source-intake-1' / version
            require(pin(folder / 'original-source.zip') == source['archive'], 'Changed source archive: ' + version)
            local_files += 1
            for name, wanted in source['files'].items():
                require(pin(folder / 'source' / name) == wanted, 'Changed original source: ' + version + '/' + name)
                local_files += 1
    build = read('BUILD44-COMPLETED.json')
    producer = read('BUILD44-PRODUCER.json')
    profile = read('RUNTIME44-PROFILE.json')
    require(build['producer'] == producer['jar_pin'] and build['source_files_unchanged'] == 698
            and build['production_vendor_binary'] is False
            and read('BUILD44-PROCESS-EXIT.json')['exit_code'] == 0,
            'Different saved original-source build')
    require(profile['source_commit'] == build['source_commit'] == COMMITS['4.4.0']
            and profile['qualified_statistical_execution'] is False
            and profile['vendor_binary'] is False, 'Different unqualified source-runtime profile')
    if local_intake is not None:
        jar = local_intake / 'original-build-4.4.0-1/project-build/libs/gsea-minimal-4.4.0.jar'
        require(pin(jar) == producer['jar_pin'], 'Changed locally built source JAR')
        local_files += 1
        for name, wanted in profile['files'].items():
            require(pin(local_intake / 'runtime-source-4.4.0-1' / safe(name)) == wanted,
                    'Changed private source runtime: ' + name)
            local_files += 1
    for operation in ['GSEA', 'GSEAPreranked']:
        process = read(operation + '-PROCESS.json')
        require(process['exit_code'] == 1 and process['command'][-1] == '-help', 'Different help probe')
        output = (PACKET / (operation + '-stdout')).read_bytes() + (PACKET / (operation + '-stderr')).read_bytes()
        require(b'Unrecognized option: -help' in output, 'Different unsupported-help disposition')
    readback = read('READBACK.json')
    require(readback['base'] == BASE and readback['new_family_credit'] == readback['new_GSEA_reviews'] == 0
            and readback['GSEA_statistical_execution_qualified'] is False
            and readback['fresh_scientific_fit'] is False and readback['whole_goal_complete'] is False
            and all(r['exit_code'] == 0 for r in readback['readbacks'].values()),
            'Different saved handover readback scope')
    catalog = read('EDGER-CATALOG-READBACK.json')
    require(catalog['result']['integrations'] == 139 and catalog['result']['reviewed_uses'] == 1520
            and catalog['result']['pending_uses'] == 1185 and catalog['current_manifest']['entries'] == 823,
            'Different admitted catalog checkpoint')
    xmls = 0
    if primary_cache is not None:
        for identity in identities:
            require(bool(re.fullmatch(r'PMC[0-9]+', identity['pmcid'])), 'Unsafe primary identity')
            with gzip.open(primary_cache / (identity['pmcid'] + '.xml.gz'), 'rb') as stream:
                raw = stream.read(64 * 1024 * 1024 + 1)
            require(len(raw) <= 64 * 1024 * 1024
                    and hashlib.sha256(raw).hexdigest() == identity['xml_sha256'],
                    'Changed exact primary XML: ' + identity['paper_id'])
            xmls += 1
    return dict(status='PASS_SAVED_GSEA_RESEARCH_BUILD_HANDOVER_INTEGRITY', packet_files=len(manifest),
                candidate_papers=50, existing_GSEA_observations=19, optional_XMLs_rehashed=xmls,
                optional_local_files_rehashed=local_files, new_catalog_credit=0,
                GSEA_statistical_execution_qualified=False, scientific_processes_executed=0)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--primary-cache', type=Path)
    parser.add_argument('--local-intake', type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.primary_cache, args.local_intake), indent=2))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(('SKIP3: requested exact prerequisite missing/inaccessible. ' if code == 3
               else 'FAIL2: saved handover integrity/structure differs. ') + str(error)
              + '\nRestore the named exact permitted prerequisite or retain the skip. Preserve affected '
                'files/logs and investigate integrity failures; do not silently replace bytes or refresh '
                'hashes. No GSEA analysis result is qualified by this reader.', file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
