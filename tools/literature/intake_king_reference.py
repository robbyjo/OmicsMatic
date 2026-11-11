# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Privately retain official KING research sources, original archives and a public example; no execution."""
import argparse, datetime, json, shutil, stat, sys, tarfile, urllib.request, zipfile
from pathlib import Path, PurePosixPath
from setup_deseq2_reference import ROOT, pin, save
BASE = ROOT/'build/literature-review/plink-relatedness-runtime'
ACTION = ('Standalone KING analysis is not admitted by this intake. Use only within the permitted research-use terms; '
    'keep its original software/source archives private. If download, installation, license or runtime is unavailable/forbidden, '
    'skip the dependent standalone KING analysis and supply the named permitted original prerequisites in NEW paths. '
    'The independent native Java between-family component does not require a KING runtime.')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', required=True, type=Path)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    records = []
    state = dict(status='SKIP3_STANDALONE_KING_INTAKE_UNAVAILABLE', runtime_admitted=False,
        analysis_performed=False, new_catalog_credit=False, whole_goal_complete=False, required_action=ACTION)
    try:
        out.mkdir(parents=True)

        def download(name, maximum):
            url = 'https://www.kingrelatedness.com/'+name
            req = urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-research-reference'})
            with urllib.request.urlopen(req, timeout=90) as response, (out/name).open('xb') as target:
                total = 0
                for chunk in iter(lambda: response.read(1048576), b''):
                    total += len(chunk)
                    assert total <= maximum, 'Official source capacity exceeded: '+name
                    target.write(chunk)
                record = dict(url=url, final_url=response.geturl(), fetched_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                    response_status=response.status, headers=dict(response.headers), **pin(out/name))
            records.append(dict(path=name, **record))
            save(out/(name+'.receipt.json'), record)
            return out/name

        page = download('Download.shtml', 2000000).read_text(encoding='utf8')
        assert '2.3.2' in page and 'Feel free to use KING for your research' in page
        assert 'redistribute AND make profits' in page
        for name in ['KINGcode.tar.gz', 'Linux-king.tar.gz', 'Windows-king.zip', 'ex.tar.gz']:
            archive = download(name, 100000000)
            folder = out/(name.replace('.tar.gz', '').replace('.zip', '')+'-original')
            folder.mkdir()
            total = 0

            def target(member, size):
                nonlocal total
                relative = PurePosixPath(member)
                assert not relative.is_absolute() and '..' not in relative.parts and not any(c in member for c in '\\:\0')
                path = (folder/Path(*relative.parts)).resolve()
                assert path.is_relative_to(folder.resolve()) and size >= 0
                total += size
                assert total <= 1000000000, 'Expanded original archive capacity exceeded'
                path.parent.mkdir(parents=True, exist_ok=True)
                return path

            if name.endswith('.zip'):
                with zipfile.ZipFile(archive) as z:
                    assert len(z.infolist()) <= 4000
                    for member in z.infolist():
                        if member.is_dir():
                            continue
                        assert not stat.S_ISLNK(member.external_attr >> 16)
                        path = target(member.filename, member.file_size)
                        with z.open(member) as source, path.open('xb') as dest:
                            shutil.copyfileobj(source, dest)
            else:
                with tarfile.open(archive) as tar:
                    assert len(tar.getmembers()) <= 4000
                    for member in tar.getmembers():
                        if member.isdir():
                            continue
                        assert member.isfile(), 'Original archive contains a nonregular member'
                        path = target(member.name, member.size)
                        with tar.extractfile(member) as source, path.open('xb') as dest:
                            shutil.copyfileobj(source, dest)
            save(out/(name+'.members.json'), {path.relative_to(folder).as_posix(): pin(path) for path in sorted(folder.rglob('*')) if path.is_file()})
            print('Privately retained', name, 'expanded bytes', total, flush=True)
        download('manual.shtml', 8000000)
        download('history.shtml', 4000000)
        state.update(status='PASS_PRIVATE_OFFICIAL_KING232_RESEARCH_INTAKE_ONLY', source_records=records,
            intended_use='Educational/research/nonprofit project use previously stated by the user',
            original_redistribution_planned=False, historical_release_reproduced=False)
    except Exception as error:
        state['cause'] = repr(error)
        print('SKIP3 STANDALONE KING ANALYSIS SKIPPED: '+str(error)+'\nREQUIRED ACTION: '+ACTION, file=sys.stderr)
    if out.is_dir():
        save(out/'COMPLETED.json', state)
        (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause', 'Original source intake only; no runtime or analysis is admitted.')+'\n\nREQUIRED ACTION: '+ACTION+'\n', encoding='utf8')
    print(state['status'], flush=True)
    return 0 if state['status'].startswith('PASS_') else 3


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable research-source/path/capacity checks')
    raise SystemExit(main())
