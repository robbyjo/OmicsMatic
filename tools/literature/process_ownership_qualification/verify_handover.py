"""Verify the current append-only fixture follow-up; never launch a worker."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[3]
PACKET = ROOT / 'docs/literature/process-ownership-safety-20261122'


def main():
    status = json.loads((PACKET / 'STATUS-v2.json').read_bytes())
    for name, expected in status['file_pins'].items():
        path = ROOT / name
        assert path.is_file() and not path.is_symlink(), name
        raw = path.read_bytes()
        assert {'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()} == expected, name
    for filename, count in [('windows-java21-atomic-fixture.json', 761),
                            ('windows-java25-atomic-fixture.json', 27)]:
        result = json.loads((PACKET / filename).read_bytes())
        assert result['exit_code'] == 0 and result['tests'] == count, filename
        assert result['failures'] == 0 and result['skipped'] == 0, filename
    assert status['production_jar_unchanged'] and status['new_family_credit'] == 0
    assert not status['whole_goal_complete']
    print('PASS: current safety source pins and 761 Java21 / 27 Java25 runtime '
          'checks after atomic fixture publication. No workers executed by this '
          'verifier; no upstream, Linux runtime, release or whole-goal qualification.')


if __name__ == '__main__':
    main()
