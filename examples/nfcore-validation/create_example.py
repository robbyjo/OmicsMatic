# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Export an original nf-validation control recipe; perform no install or analysis."""
import argparse
import json
import sys
from pathlib import Path

WORKFLOW = '''nextflow.enable.dsl=2
include { validateParameters; paramsSummaryMap } from 'plugin/nf-validation'
validateParameters()
def summary = paramsSummaryMap(workflow)
process RECORD {
    publishDir params.outdir, mode:'copy'
    input:
    val specimen
    val threshold
    output:
    path 'validated.tsv'
    script:
    """
    printf '%s\\t%s\\n' '$specimen' '$threshold' > validated.tsv
    """
}
workflow { RECORD(params.specimen, params.threshold) }
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['out', 'engine', 'plugins']:
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    if sys.platform != 'linux':
        raise SystemExit('Export with Linux Python, including WSL, for native Linux recipe paths. No analysis ran.')
    root = args.out.absolute()
    if root.parent.resolve() != root.parent or root.exists():
        raise SystemExit('Use a NEW example directory with a canonical existing parent.')
    resources = {}
    for name in ['engine', 'plugins']:
        path = getattr(args, name).absolute()
        if not path.is_dir() or path.resolve() != path:
            raise SystemExit('Provide the canonical existing separately installed ' + name + ' directory.')
        resources[name] = {'path': str(path)}
    root.mkdir()
    project = root / 'project'
    project.mkdir()
    (project / 'main.nf').write_text(WORKFLOW, encoding='utf-8', newline='\n')
    (project / 'nextflow.config').write_text("plugins { id 'nf-validation@1.1.3' }\nprocess.executor='local'\nprocess.cpus=1\nprocess.memory='64 MB'\n", encoding='utf-8', newline='\n')
    schema = {'$schema': 'http://json-schema.org/draft-07/schema', 'title': 'OmicsMatic original plugin control', 'type': 'object', 'definitions': {'analysis': {'type': 'object', 'title': 'Analysis', 'required': ['specimen', 'threshold', 'outdir'], 'properties': {'specimen': {'type': 'string', 'pattern': '^[A-Za-z][A-Za-z0-9_]*$'}, 'threshold': {'type': 'number', 'minimum': 0, 'maximum': 1}, 'outdir': {'type': 'string'}}}}, 'allOf': [{'$ref': '#/definitions/analysis'}]}
    (project / 'nextflow_schema.json').write_text(json.dumps(schema, indent=2) + '\n', encoding='utf-8', newline='\n')
    recipe = dict(schema_version=1, engine_profile='linux-x86_64-nextflow-24.04.4', workflow=dict(path=str(project), entrypoint='main.nf', configs=['nextflow.config'], profiles=[]), resources=resources, parameters=dict(specimen='sample_A', threshold=0.05, outdir={'$output': ''}), required_tools=[], outputs=[dict(path='validated.tsv', kind='file', allow_empty=False)], require_tasks=True, limits=dict(wall_seconds=300, address_space_bytes=8589934592, cpu_seconds=300, workspace_bytes=4294967296, capture_bytes=4294967296, file_bytes=2147483648, java_heap_mib=512))
    (root / 'recipe.json').write_text(json.dumps(recipe, indent=2) + '\n', encoding='utf-8', newline='\n')
    (root / 'expected.tsv').write_bytes(b'sample_A\t0.05\n')
    print('Exported original plugin control only: ' + str(root / 'recipe.json') + '. No software installed or analysis executed.')


if __name__ == '__main__':
    main()
