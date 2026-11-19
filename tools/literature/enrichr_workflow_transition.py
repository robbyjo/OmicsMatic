"""Narrow dispatcher/serial-annotation review and retained component readback.

This is a new compatibility reader. The unchanged historical reader still
requires its original dispatcher; no old fingerprint or scientific lookup is
rewritten, overlaid or monkeypatched.
"""
from pathlib import Path
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[2]
DISPATCH = 'src/main/java/org/jlinalg/cli/JLinAlgCli.java'
BASE = '383c677d81924d09c2f2f8140dfc44eab99393d5'
BEFORE_SHA = '60cf5ee2b0a52120c3be4597f0cf52230347c85101b24087db6f4899fe9cd5b1'
SERIAL = {
    'src/main/java/org/jlinalg/cli/ColocAbfFiles.java': (
        dict(bytes=6409, sha256='481b18ac2372559154dba97e329e44da02217824de92660f5e1db5aad99bb90d'),
        [b'    static final class Unavailable extends IOException {']),
    'src/main/java/org/jlinalg/enrichr/EnrichrClient.java': (
        dict(bytes=20836, sha256='b816264d663a85bd5db146a618ec51f78f2996ba17612c0f9f253f538385eb34'),
        [b'    public static final class Failure extends IOException {',
         b'    private static final class InterruptedTransfer extends InterruptedException {']),
}


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def annotation_before(relative):
    """Only the exact inline SOURCE-retained serial warning annotations are reversed."""
    raw = (ROOT / relative).read_bytes()
    wanted, declarations = SERIAL[relative]
    before = raw
    for declaration in declarations:
        annotated = b'    @SuppressWarnings("serial") ' + declaration[4:]
        require(before.count(annotated) == 1, 'Missing/ambiguous exact serial annotation: ' + relative)
        before = before.replace(annotated, declaration, 1)
    require(pin(before) == wanted and before.count(b'\n') == raw.count(b'\n'),
            'Change exceeds the source-only annotation scope: ' + relative)
    return before


def annotations():
    return dict(status='PASS_EXACT_INLINE_SOURCE_ONLY_SERIAL_ANNOTATIONS',
                paths={name: dict(before=pin(annotation_before(name)), after=pin((ROOT / name).read_bytes()))
                       for name in SERIAL},
                expected_compilation_class_identity='25 exact class payloads verified separately',
                old_fingerprints_refreshed=False, scientific_source_overlay=False)


def dispatcher(before, after):
    require(pin(before) == dict(bytes=54187, sha256=BEFORE_SHA), 'Wrong original dispatcher')
    anchor = b'            if (arguments.length > 0 && arguments[0].equals("gseapy-native"))\n'
    help_anchor = b'              java -jar omicsmatic-<version>.jar enrichment-tools --help\n'
    route = (b'            if (arguments.length > 0 && arguments[0].equals("enrichr-tools"))\n'
             b'                return EnrichrWorkflowCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n')
    advertised = b'              java -jar omicsmatic-<version>.jar enrichr-tools --help\n'
    require(before.count(anchor) == before.count(help_anchor) == 1, 'Ambiguous old insertion anchors')
    expected = before.replace(anchor, route + anchor).replace(help_anchor, advertised + help_anchor)
    require(after == expected, 'Dispatcher change exceeds the exact three additive lines')
    require(after.replace(route, b'', 1).replace(advertised, b'', 1) == before,
            'Exact dispatcher inverse differs')
    return dict(status='PASS_EXACT_ADDITIVE_DISPATCH_INVERSE', path=DISPATCH,
                base_commit=BASE, before=pin(before), after=pin(after), added_lines=3,
                removed_old_lines=0, changed_existing_member_scopes=['run(String[],PrintStream,PrintStream)', 'help()'],
                scientific_source_overlay=False, old_fingerprints_refreshed=False,
                whole_program_semantic_equivalence_claimed=False)


def previous_abf(before):
    folder = ROOT / 'docs/literature/coloc-abf-workflow-checks'
    proof = json.loads((folder / 'QUALIFICATION.json').read_bytes())
    require(proof['status'] == 'PASS_SCOPED_NATIVE_ABF_WORKFLOW_QUALIFICATION', 'Different old ABF qualification')
    for name, wanted in proof['files'].items():
        # Review the dispatcher addition and exact SOURCE-only annotations.
        # Every other source and saved file must still match its original pin.
        raw = before if name == DISPATCH else annotation_before(name) if name in SERIAL else (ROOT / name).read_bytes()
        require(pin(raw) == wanted, 'Changed old scientific/source evidence: ' + name)
    tests = proof['qualification']['tests']
    require({r['host'] for r in tests} == {'windows21', 'windows25', 'linux21'}, 'Missing old ABF JVM scope')
    require(all(r['exit_code'] == 0 and int(r['counts']['tests']) == 62 and
                all(int(r['counts'][k]) == 0 for k in ['skipped', 'failures', 'errors']) for r in tests),
            'Old ABF test disposition differs')
    with zipfile.ZipFile(folder / 'evidence.zip') as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)) and set(names) == set(proof['archive_members']),
                'Old ABF archive population differs')
        require(archive.testzip() is None, 'Old ABF archive CRC failed')
        for name, wanted in proof['archive_members'].items():
            require(pin(archive.read(name)) == wanted, 'Changed old ABF archive payload: ' + name)
    return dict(status='PASS_RETAINED_ABF_WITH_EXPLICIT_DISPATCH_COMPATIBILITY',
                source_files_checked=len(proof['files']), archive_payloads_checked=len(proof['archive_members']),
                original_reader_current_dispatch_accepted=False,
                original_reader_modified=False, original_reader_reexecuted=False,
                all_other_current_files_matched_original_pins=True,
                source_only_annotation_inverse_checked=True,
                old_dispatcher_identity=pin(before), scientific_source_overlay=False,
                old_fingerprints_refreshed=False, fresh_JVM_tests=False, fresh_R_execution=False)


def previous_enrichr_api():
    folder = ROOT / 'docs/literature/enrichr-component-checks'
    proof = json.loads((folder / 'QUALIFICATION.json').read_bytes())
    require(proof['status'] == 'PASS_ENRICHR_SOURCE_CENSUS_AND_API_COMPONENT_ONLY', 'Different old API qualification')
    for name, wanted in proof['sources'].items():
        raw = annotation_before(name) if name in SERIAL else (ROOT / name).read_bytes()
        require(pin(raw) == wanted, 'Old API source differs outside source-only annotation scope: ' + name)
    readback = json.loads((folder / 'READBACK.json').read_bytes())
    for name, wanted in readback['artifacts'].items():
        require('/' not in name and '\\' not in name and name not in ['.', '..'], 'Unsafe old API packet path')
        require(pin((folder / name).read_bytes()) == wanted, 'Changed old API packet: ' + name)
    with zipfile.ZipFile(folder / 'evidence.zip') as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)) and set(names) == set(proof['evidence_payloads']), 'Old API payload population differs')
        require(archive.testzip() is None, 'Old API archive CRC failure')
        for name, wanted in proof['evidence_payloads'].items():
            require(pin(archive.read(name)) == wanted, 'Changed old API evidence: ' + name)
        require(pin(archive.read('component/omicsmatic-enrichr-component-development.jar')) == proof['component_jar'],
                'Old original API JAR differs')
    return dict(status='PASS_RETAINED_ENRICHR_API_WITH_EXPLICIT_SOURCE_ANNOTATION_COMPATIBILITY',
                sources_checked=len(proof['sources']), archive_payloads_checked=len(proof['evidence_payloads']),
                old_original_reader_current_source_accepted=False, old_original_reader_modified=False,
                old_fingerprints_refreshed=False, scientific_source_overlay=False,
                original_component_jar=proof['component_jar'], fresh_JVM_execution=False, fresh_upstream_execution=False)
