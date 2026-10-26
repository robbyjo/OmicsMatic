"""Stage only this task's code/test changes on a clean managed v0.3.7 checkout.

The shared checkout is not modified. This does not copy its unrelated dirty
study-pipeline work, and therefore validates the toolbox against the committed
baseline rather than claiming the changing shared checkout is green.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
BASE = "57ec05d0f748c9a2015d6623b9268544ef099b59"
FILES = [
    "src/main/java/org/jlinalg/enrichment/SignatureScores.java",
    "src/main/java/org/jlinalg/genetics/GenomicIntervals.java",
    "src/main/java/org/jlinalg/cli/SignatureScoreCli.java",
    "src/main/java/org/jlinalg/cli/GenomicIntervalCli.java",
    "src/main/java/org/jlinalg/cli/CellEmbeddingCli.java",
    "src/main/resources/org/jlinalg/omics/cell_embedding.py",
    "src/test/java/org/jlinalg/enrichment/SignatureScoresTest.java",
    "src/test/java/org/jlinalg/genetics/GenomicIntervalsTest.java",
    "src/test/java/org/jlinalg/cli/SignatureScoreCliTest.java",
    "src/test/java/org/jlinalg/cli/GenomicIntervalCliTest.java",
    "src/test/java/org/jlinalg/cli/CellEmbeddingCliTest.java",
    "tools/literature/install_cell_dependencies.py",
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--destination", type=Path, required=True)
    args = parser.parse_args()
    destination = args.destination.resolve()
    top = subprocess.check_output(["git", "-C", str(destination), "rev-parse", "--show-toplevel"], text=True).strip()
    assert Path(top).resolve() == destination and destination != ROOT
    assert subprocess.check_output(["git", "-C", str(destination), "rev-parse", "HEAD"], text=True).strip() == BASE
    assert not subprocess.check_output(["git", "-C", str(destination), "status", "--porcelain"], text=True).strip(), "Target checkout is not clean"
    edits = {}

    def replace(relative, anchor, replacement):
        content = edits.get(relative, (destination / relative).read_text(encoding="utf-8"))
        assert content.count(anchor) == 1, relative + ": ambiguous/missing baseline anchor"
        edits[relative] = content.replace(anchor, replacement, 1)

    dispatch = "    private static int dispatch(String[] arguments, PrintStream output,\n            PrintStream errorOutput) {\n"
    calls = "".join("        if (arguments.length > 0 && arguments[0].equals(\"" + command + "\"))\n"
                    + "            return " + class_ + ".run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n"
                    for command, class_ in [("signature-score", "SignatureScoreCli"), ("intervals", "GenomicIntervalCli"), ("cell-embed", "CellEmbeddingCli")])
    replace("src/main/java/org/jlinalg/cli/JLinAlgCli.java", dispatch, dispatch + calls)
    help_ = "              java -jar jlinalg-<version>.jar study --help\n"
    replace("src/main/java/org/jlinalg/cli/JLinAlgCli.java", help_, help_ + "".join(
        "              java -jar jlinalg-<version>.jar " + command + " --help\n" for command in ["signature-score", "intervals", "cell-embed"]))
    replace("build.gradle", "test {\n    useJUnitPlatform()\n", "test {\n    useJUnitPlatform()\n"
            + "    // Optional real-tool tests must not reuse a cache entry that skipped them.\n"
            + "    inputs.property('cellAdapterPython',\n"
            + "        providers.environmentVariable('JLINALG_CELL_PYTHON').orElse(''))\n")
    replace(".gitignore", "build/\n", "build/\n__pycache__/\n*.py[cod]\n")
    files = list(FILES)
    files.extend(str(p.relative_to(ROOT)).replace("\\", "/") for p in (ROOT / "src/test/resources/signature-scores").iterdir() if p.is_file())
    for relative in files:
        source, target = ROOT / relative, destination / relative
        assert source.is_file() and not target.exists(), relative + ": target already exists"
    # Validate all targets before the first mutation; copy only explicitly owned files.
    for relative in files:
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / relative, target)
    for relative, content in edits.items():
        (destination / relative).write_text(content, encoding="utf-8")
    dependency = ROOT / "build/dependencies/jdistlib-all-0.10.2.jar"
    if dependency.is_file():
        target = destination / "build/dependencies" / dependency.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(dependency, target)
    report = {"committed_baseline": BASE, "shared_source_checkout": str(ROOT), "isolated_checkout": str(destination),
              "scope": "Only this task's new code/tests plus three dispatcher/help entries and the optional-runtime test cache key. Unrelated dirty study-pipeline changes and literature-site edits are not copied.",
              "files": {p: hashlib.sha256((destination / p).read_bytes()).hexdigest() for p in files + list(edits)}}
    target = destination / "build/literature-review/validation-overlay-manifest.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"destination": str(destination), "new_files": len(files), "modified_baseline_files": list(edits)}))


if __name__ == "__main__":
    main()
