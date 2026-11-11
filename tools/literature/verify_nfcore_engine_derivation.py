# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the separate engine is an exact, bounded derivation of frozen existing workflow helpers."""
import argparse, difflib, json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    out = p.parse_args().out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/nfcore-runtime') and not out.exists()
    out.mkdir()
    frozen = json.loads((ROOT / 'docs/literature/nextflow-typed-workflow/VALIDATION.json').read_bytes())
    names = dict(NextflowWorkflow='NfcoreEngineWorkflow', NextflowBundle='NfcoreEngineBundle', NextflowToolsCli='NfcoreEngineToolsCli')
    records = []
    for old, new in names.items():
        source = ROOT / 'src/main/java/org/jlinalg/cli' / (old + '.java')
        target = source.with_name(new + '.java')
        assert pin(source) == frozen['source_pins'][source.relative_to(ROOT).as_posix()]
        before = source.read_bytes().decode('utf8'); expected = before
        for a, b in names.items(): expected = expected.replace(a, b)
        if old == 'NextflowToolsCli': expected = expected.replace('nextflow-tools', 'nfcore-engine-tools')
        if old == 'NextflowWorkflow':
            expected = expected.replace('/** Runs captured user workflows with the original engine and a stable owned resume cache. */',
                '/** Separate primary-config binding for nf-core config methods; original engine/capture/process helpers remain unchanged. */')
            expected = expected.replace('import static org.jlinalg.cli.NextflowFiles.*;',
                'import org.jlinalg.cli.NextflowWorkflow.Tool;\nimport static org.jlinalg.cli.NextflowFiles.*;')
            expected = expected.replace('    record Tool(String name,String path,String realPath,Identity identity) { }\n', '')
            # Only the -C binding changes; every process, capture, parameter and admission helper stays exact.
            needle = '        var result=new ArrayList<>(List.of("/usr/bin/bash",home+"/nextflow","-C",attempt+"/adapter.config",'
            replacement = '''        var configs=new ArrayList<String>();for(String name:strings(workflow.get("configs")))configs.add(bindings.get("workflow")+"/"+member(name));configs.add(attempt+"/adapter.config");
        for(String path:configs)if(path.indexOf(',')>=0)throw new NextflowRuntime.Unavailable("The original Nextflow -C list cannot represent a comma in a config path; use NEW comma-free session/source paths without renaming scientific identifiers");
        var result=new ArrayList<>(List.of("/usr/bin/bash",home+"/nextflow","-C",String.join(",",configs),'''
            assert expected.count(needle) == 1
            expected = expected.replace(needle, replacement)
            start = expected.index('    static String config(Map<?,?> recipe,String workflow)throws IOException {')
            end = expected.index('    private static List<Tool> tools(', start)
            expected = expected[:start] + '''    static String config(Map<?,?> recipe,String workflow)throws IOException {
        return "trace.fields='"+TRACE+"'\\n";
    }
''' + expected[end:]
        assert target.read_bytes() == expected.encode('utf8'), ('Unexpected change outside bounded engine derivation', new)
        (out / (new + '.diff')).write_text('\n'.join(difflib.unified_diff(before.splitlines(), expected.splitlines(), fromfile=old + '.java', tofile=new + '.java')) + '\n', encoding='utf8', newline='\n')
        records.append(dict(original=source.relative_to(ROOT).as_posix(), original_pin=pin(source), derived=target.relative_to(ROOT).as_posix(), derived_pin=pin(target)))
    save(out / 'COMPLETED.json', dict(status='PASS_EXACT_BOUNDED_PRIMARY_CONFIG_ENGINE_DERIVATION', records=records,
        source_pin=pin(Path(__file__)), existing_source_modified=False, scientific_method_bodies_modified=False,
        runtime_capture_process_and_admission_helpers_reused_unchanged=True, new_catalog_credit=False))
    print('PASS exact bounded derivation; existing sources and scientific helpers unchanged', flush=True)


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable exact original-source or bounded-derivation checks')
    main()
