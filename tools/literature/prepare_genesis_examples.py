# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Copy complete synthetic GENESIS inputs and literal requests into a NEW workspace; run no analysis."""
import argparse
import json
import stat
import traceback
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import byte_pin

EXAMPLES = ROOT / 'examples/genesis-tools'
ACTION = 'This helper only prepares synthetic inputs and literal recipes; it admits no analysis or runtime. Require the matching genesis-tools command, exit0, result_admitted=true and complete --verify before using any result. SKIP3: missing or forbidden dependencies mean SKIP dependent original analysis; supply the exact permitted private Windows GENESIS2.42/R4.6.1 profile or leave analysis skipped. FAIL2: preserve the attempt, resolve the stated integrity/path cause and retry NEW paths. The two known-error recipes must admit no result.'


class Unavailable(RuntimeError):
    pass


def preflight_failure(out, cause, code):
    status = 'SKIP3_GENESIS_EXAMPLE_PROFILE_UNAVAILABLE' if code == 3 else 'FAIL2_GENESIS_EXAMPLE_PROFILE'
    state = dict(status=status, cause=cause, original_analysis_executed=False, result_admitted=False, required_action=ACTION)
    try:
        out.mkdir(parents=True)
        (out / 'WARNINGS.md').write_text('# ' + status + '\n\n' + cause + '\n\nREQUIRED ACTION: ' + ACTION + '\n', encoding='utf8', newline='\n')
        save(out / 'COMPLETED.json', state)
    except OSError as error:
        print('SKIP3: cannot write the requested new diagnostic output: ' + repr(error) + '. No result admitted. ' + ACTION)
        return 3
    print(status + ': ' + cause + '. ' + ACTION)
    return code


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--profile', type=Path, required=True); p.add_argument('--out', type=Path, required=True)
    a = p.parse_args(); out = a.out.resolve()
    if out.exists() or not out.is_relative_to(ROOT / 'build/literature-review/genesis-runtime'):
        print('FAIL2: choose NEW output beneath build/literature-review/genesis-runtime. ' + ACTION); return 2
    if not a.profile.is_file():
        return preflight_failure(out, 'The selected exact runtime profile is missing', 3)
    # Resolve named runtime roots before creating anything: do not let recipe
    # preparation add files to a library whose complete closure is already pinned.
    try:
        raw = a.profile.read_bytes()
        if len(raw) > 16_777_216:
            return preflight_failure(out, 'Runtime profile exceeds the complete profile capacity', 2)
        profile = json.loads(raw)
        for key in ['r_home', 'private_library']:
            root = Path(profile[key]).resolve()
            if out.is_relative_to(root) or root.is_relative_to(out):
                # Do not create diagnostics inside the protected runtime either.
                print('FAIL2: example workspace overlaps a named runtime root. No files written. ' + ACTION); return 2
    except PermissionError as error:
        return preflight_failure(out, 'Reading the profile is forbidden: ' + repr(error), 3)
    except (ValueError, KeyError, TypeError, OSError) as error:
        return preflight_failure(out, 'Invalid or unreadable runtime profile: ' + repr(error), 2)
    out.mkdir(parents=True); state = dict(status='FAIL2_GENESIS_EXAMPLE_PREPARATION', original_analysis_executed=False, result_admitted=False, downloads_or_installation=False, required_action=ACTION); code = 2
    try:
        assert profile['schema_version'] == 1 and profile['tool'] == 'GENESIS' and profile['version'] == '2.42.0' and profile['r_version'] == '4.6.1' and profile['platform'] == 'windows'
        before = pin(a.profile); assert before == byte_pin(raw)
        manifest = json.loads((EXAMPLES / 'manifest.json').read_bytes())
        assert manifest['status'] == 'synthetic-GENESIS-examples-not-results' and manifest['participant_data'] is False and len(manifest['cases']) == 13
        archive = EXAMPLES / 'inputs.zip'; assert pin(archive) == manifest['inputs']['archive']
        inputs = out / 'inputs'; inputs.mkdir(); requests = out / 'requests'; requests.mkdir(); (out / 'results').mkdir()
        with zipfile.ZipFile(archive) as z:
            names = z.namelist(); expected = manifest['inputs']['files']; assert len(names) == len(set(names)) == 11 and set(names) == set(expected)
            for item in z.infolist():
                name = item.filename
                assert Path(name).name == name and '/' not in name and '\\' not in name and ':' not in name and not item.is_dir()
                assert stat.S_IFMT(item.external_attr >> 16) in [0, stat.S_IFREG] and item.file_size <= 1_000_000
                content = z.read(item); assert byte_pin(content) == expected[name]; (inputs / name).write_bytes(content)
        cases = []
        for case in manifest['cases']:
            name = case['request']; assert Path(name).name == name and name == case['case'] + '.json'
            content = (EXAMPLES / name).read_bytes(); request = json.loads(content)
            assert request['out'] == '../results/' + case['case'] and request['runtime_profile'] == '../runtime-profile.json'
            (requests / name).write_bytes(content); cases.append(dict(case=case['case'], request='requests/' + name, expected_exit=case['expected_exit'], request_pin=byte_pin(content)))
        (out / 'runtime-profile.json').write_bytes(raw); assert pin(a.profile) == before
        state.update(status='PASS_PREPARED13_LITERAL_GENESIS_RECIPES_AND11_SYNTHETIC_INPUTS_ONLY', source_manifest=pin(EXAMPLES / 'manifest.json'), source_profile=before, cases=cases,
                     input_files=expected, source_recipe_options_unchanged=True, runtime_admission_performed=False, producer=pin(Path(__file__)))
        code = 0
    except (Unavailable, PermissionError) as error:
        state.update(status='SKIP3_GENESIS_EXAMPLE_PREREQUISITE_UNAVAILABLE', cause=repr(error)); code = 3
    except Exception as error:
        state['cause'] = repr(error); (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' + state.get('cause', 'Synthetic inputs and literal recipes prepared; no runtime or scientific result admitted.') + '\n\nREQUIRED ACTION: ' + ACTION + '\n', encoding='utf8', newline='\n')
    save(out / 'COMPLETED.json', state); print(state['status'], state.get('cause', '')[:6000], flush=True)
    return code


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete input and literal-recipe integrity checks')
    raise SystemExit(main())
