"""Build the scoped workflow against its exact parent, preserving every old payload."""
from pathlib import Path
import argparse
import json
import shutil
import subprocess
import sys
import zipfile
from verify_plink_relatedness_workflows import pin, check_package


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, required=True)
    parser.add_argument("--parent", type=Path, required=True)
    parser.add_argument("--javac", required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    project, parent, output = (p.resolve() for p in (args.project, args.parent, args.out))
    packet = project / "docs/literature/plink-relatedness-development"
    proof = json.loads((packet / "QUALIFICATION.json").read_bytes())
    if not parent.is_file():
        print("SKIP3: exact parent JAR missing. Restore the parent bytes named in the "
              "checkpoint; scoped build skipped. No dependency was installed.", file=sys.stderr)
        return 3
    if pin(parent) != proof["parent"]:
        raise ValueError("Exact parent bytes/hash differ")
    if pin(packet / "PACKAGE.json") != proof["package_index"]:
        raise ValueError("Package index differs")
    index = json.loads((packet / "PACKAGE.json").read_bytes())
    sources = []
    resources = {}
    for name, wanted in proof["build_source_pins"].items():
        if pin(project / name) != wanted:
            raise ValueError("Build source differs: " + name)
    output.mkdir(parents=True, exist_ok=False)
    for name in proof["build_source_pins"]:
        target = output / "source" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(project / name, target)
        if name.endswith(".java"):
            sources.append(target)
        elif name.startswith("src/main/resources/"):
            resources[name.removeprefix("src/main/resources/")] = target
    classes = output / "classes"
    classes.mkdir()
    command = [args.javac, "--release", "17", "-g", "-Xlint:all", "-Werror",
               "-cp", str(parent), "-d", str(classes), *map(str, sources)]
    (output / "COMMAND.json").write_text(json.dumps(command, indent=2) + "\n", encoding="utf-8")
    with (output / "javac.stdout").open("xb") as stdout, (output / "javac.stderr").open("xb") as stderr:
        process = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=600)
    if process.returncode:
        raise ValueError("javac exited " + str(process.returncode) + "; read retained javac.stderr")
    added = {p.relative_to(classes).as_posix(): p for p in classes.rglob("*.class")}
    added.update(resources)
    if set(added) != set(index["added_payloads"]):
        raise ValueError("Built payload population differs")
    for name, source in added.items():
        if pin(source) != index["added_payloads"][name]:
            raise ValueError("Compiled/resource payload differs: " + name +
                             "; use the tested compiler or qualify a new compilation separately")
    candidate = output / "omicsmatic-0.4.0.jar"
    shutil.copyfile(parent, candidate)
    with zipfile.ZipFile(candidate, "a", zipfile.ZIP_DEFLATED, compresslevel=9) as package:
        for name, source in sorted(added.items()):
            package.write(source, name)
    check_package(candidate, index)
    report = dict(status="PASS_BIT_IDENTICAL_RELATEDNESS_PAYLOAD_BUILD", candidate=pin(candidate),
                  parent=pin(parent), old_payloads_unchanged=len(index["parent_payloads"]),
                  added_payloads=len(added), original_program_executed=False, catalog_credit=0)
    (output / "COMPLETED.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(report["status"], flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print("FAIL2: no qualified build admitted; preserve the attempt, correct the named "
              "cause and retry NEW paths: " + repr(error), file=sys.stderr)
        raise SystemExit(2)
