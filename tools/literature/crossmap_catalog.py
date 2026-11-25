"""Plan, atomically admit and verify four CrossMap source roles and one upstream family.

An explicit --artifact-root can supply unchanged historical catalog objects for
an archived source context. Every object is checked against that context's old
manifest; current metadata never substitutes for the archived version.
"""
from pathlib import Path
import argparse
import hashlib
import io
import json
import os
import subprocess
import sys
import traceback
import uuid
import zipfile

import crossmap_catalog_model as model
from crossmap_primary import encoded, pin, require
import verify_crossmap_workflows as workflow

ROOT = Path(__file__).resolve().parents[2]
BASE = "26715d5839db279949e86fd1b8db045d0fdba8ff"
FOLDER = "docs/literature/crossmap-catalog-admission"
STATUS = "PASS_CROSSMAP_ONE_UPSTREAM_FAMILY_FOUR_ROLES_THREE_NEW_REVIEWS"
SUMS = model.DATA + "/SHA256SUMS"


def file_pin(path):
    return workflow.file_pin(path)


def archive(files):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zipped:
        for name, raw in sorted(files.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            zipped.writestr(info, raw)
    return output.getvalue()


def read_archive(path):
    with zipfile.ZipFile(path) as zipped:
        require(len(zipped.namelist()) == len(set(zipped.namelist())) and set(zipped.namelist()) == set(model.META),
                "Changed archived predecessor metadata population")
        require(sum(i.file_size for i in zipped.infolist()) < 64 * 1024 * 1024, "Oversized predecessor archive")
        return {name: zipped.read(name) for name in model.META}


def sources():
    names = set(json.loads((ROOT / workflow.FOLDER / "FILES.json").read_bytes()))
    names.update(p.relative_to(ROOT).as_posix() for p in (ROOT / workflow.FOLDER).iterdir() if p.is_file())
    names.update(("tools/literature/crossmap_catalog.py", "tools/literature/crossmap_catalog_model.py",
                  "tools/literature/crossmap_catalog_test.py",
                  "docs/literature/prsice-catalog-admission/REGISTRY.json"))
    return {n: file_pin(ROOT / workflow.member(n)) for n in sorted(names)}


def manifest(artifact_root, updates=None):
    updates = updates or {}
    lines = (ROOT / SUMS).read_text().splitlines()
    wanted = {}
    for line in lines:
        digest, name = line.split("  ", 1)
        require(name not in wanted and len(digest) == 64 and all(c in "0123456789abcdef" for c in digest),
                "Invalid/duplicate catalog manifest member")
        target = (ROOT / model.DATA / name).resolve()
        require(target.is_relative_to(ROOT / "docs/literature"), "Catalog manifest escapes literature directory")
        wanted[name] = digest
    data = ROOT / model.DATA
    if artifact_root == ROOT:
        require({p.name for p in data.iterdir() if p.is_file() and p.name != "SHA256SUMS"}
                == {n for n in wanted if "/" not in n}, "Current catalog has unlisted or missing data files")
    for name, digest in wanted.items():
        target = ROOT / model.DATA / name
        if not target.is_file():
            # Explicit immutable shared-object source, never a metadata fallback.
            require(target.relative_to(ROOT).as_posix() not in model.META, "Missing archived versioned metadata")
            target = artifact_root / model.DATA / name
        require(file_pin(target)["sha256"] == digest, "Changed retained catalog object: " + name)
    result = dict(wanted)
    for name, raw in updates.items():
        if Path(name).parent.as_posix() == model.DATA and Path(name).name != "SHA256SUMS":
            result[Path(name).name] = pin(raw)["sha256"]
    order = list(wanted) + sorted(set(result) - set(wanted))
    return "".join(result[n] + "  " + n + "\n" for n in order).encode(), len(result)


def qualified(args):
    # Complete captured-result readback, not original source reexecution.
    actual = workflow.verify(argparse.Namespace(root=ROOT, private_root=args.private_root, producer=args.producer,
        predecessor_producer=args.predecessor_producer, java=args.java, cache=args.cache))
    qualification = json.loads((ROOT / workflow.FOLDER / "QUALIFICATION.json").read_bytes())
    require(actual["saved_captures_checked"] == 26 and actual["private_files_checked"] == 2476
            and actual["primary_XML_checked"] == 250, "Incomplete retained workflow readback")
    roles = json.loads((ROOT / "docs/literature/crossmap-source-audit/ROLES.json").read_bytes())
    return roles, qualification, actual


def plan(args):
    before = {n: (ROOT / n).read_bytes() for n in model.META}
    for name, raw in before.items():
        exact = subprocess.check_output(["git", "--no-replace-objects", "show", BASE + ":" + name], cwd=ROOT,
                                        stdin=subprocess.DEVNULL)
        require(raw == exact, "Changed exact predecessor metadata: " + name)
    _, count = manifest(args.artifact_root)
    require(count == 875, "Changed preceding complete manifest population")
    roles, qualification, actual = qualified(args)
    after = model.transition(before, roles, qualification)
    result = model.check(before, after, roles, qualification)
    (args.out / "metadata-before.zip").write_bytes(archive(before))
    (args.out / "metadata-after.zip").write_bytes(archive(after))
    (args.out / "PREVIOUS-SHA256SUMS").write_bytes((ROOT / SUMS).read_bytes())
    record = dict(status="PLANNED_" + STATUS, base_commit=BASE, source_code=sources(),
                  before={n: pin(raw) for n, raw in before.items()}, after={n: pin(raw) for n, raw in after.items()},
                  result=result, qualification=qualification, actual_workflow_readback=actual,
                  archives={n: file_pin(args.out / n) for n in ("metadata-before.zip", "metadata-after.zip", "PREVIOUS-SHA256SUMS")})
    (args.out / "PLANNED.json").write_bytes(encoded(record))
    return record


def inspect(args):
    record = json.loads((args.plan_dir / "PLANNED.json").read_bytes())
    require(record["status"] == "PLANNED_" + STATUS and record["base_commit"] == BASE
            and record["source_code"] == sources(), "Changed saved plan/source; preserve it and create a new plan")
    for name, wanted in record["archives"].items():
        require(file_pin(args.plan_dir / workflow.member(name)) == wanted, "Changed saved plan archive")
    before, after = read_archive(args.plan_dir / "metadata-before.zip"), read_archive(args.plan_dir / "metadata-after.zip")
    require(before == {n: (ROOT / n).read_bytes() for n in model.META}, "Canonical metadata changed after planning")
    require((ROOT / SUMS).read_bytes() == (args.plan_dir / "PREVIOUS-SHA256SUMS").read_bytes(), "Manifest changed after planning")
    roles, qualification, actual = qualified(args)
    require(qualification == record["qualification"] and model.check(before, after, roles, qualification) == record["result"],
            "Changed planned interpretation or qualification")
    return record, before, after, actual


def install(args, expected, updates, post_write):
    require(set(expected) == set(updates), "Every owned metadata write needs an exact predecessor")
    for name in updates:
        target = ROOT / workflow.member(name)
        require(target.resolve().is_relative_to(ROOT), "Metadata target escapes checkout")
        require((target.read_bytes() if target.exists() else None) == expected[name], "Concurrent metadata change: " + name)
    written = {}
    try:
        for name, raw in updates.items():
            target = ROOT / name
            require((target.read_bytes() if target.exists() else None) == expected[name], "Concurrent metadata change: " + name)
            target.parent.mkdir(parents=True, exist_ok=True)
            temporary = args.out / (uuid.uuid4().hex + ".owned-temporary")
            with temporary.open("xb") as stream:
                stream.write(raw)
            require((target.read_bytes() if target.exists() else None) == expected[name], "Metadata changed before replace: " + name)
            os.replace(temporary, target)
            written[name] = raw
        return post_write()
    except Exception:
        restored, retained, concurrent = [], [], []
        for name, raw in reversed(list(written.items())):
            target = ROOT / name
            if not target.exists() or target.read_bytes() != raw:
                concurrent.append(name)
            elif expected[name] is not None:
                target.write_bytes(expected[name])
                restored.append(name)
            else:
                recovery = args.out / "retained-failed-metadata" / name
                recovery.parent.mkdir(parents=True, exist_ok=True)
                target.rename(recovery)
                retained.append(name)
        (args.out / "ROLLBACK.json").write_bytes(encoded(dict(restored=restored, retained=retained, concurrent_changes_preserved=concurrent)))
        raise


def apply(args):
    record, before, after, actual = inspect(args)
    coverage = dict(status=STATUS, qualification=record["qualification"], result=record["result"],
                    roles=json.loads((ROOT / "docs/literature/crossmap-source-audit/ROLES.json").read_bytes())["roles"],
                    whole_goal_complete=False)
    assets = {FOLDER + "/" + n: (args.plan_dir / n).read_bytes() for n in ("metadata-before.zip", "PREVIOUS-SHA256SUMS")}
    assets[FOLDER + "/REGISTRY.json"] = encoded(model.registry())
    updates = dict(after)
    updates.update(assets)
    updates[model.COVERAGE] = encoded(coverage)
    proof = dict(schema_version=1, status=STATUS, base_commit=BASE, source_code=record["source_code"],
                 before=record["before"], after=record["after"], qualification=record["qualification"],
                 result=record["result"], evidence={n: pin(raw) for n, raw in assets.items()},
                 coverage=pin(updates[model.COVERAGE]), whole_goal_complete=False)
    updates[model.PROOF] = encoded(proof)
    updates[FOLDER + "/STATUS.json"] = encoded(proof)
    updates[SUMS], count = manifest(args.artifact_root, updates)
    require(count == 877, "Wrong prospective complete manifest population")
    expected = {n: None for n in updates}
    expected.update(before)
    expected[SUMS] = (args.plan_dir / "PREVIOUS-SHA256SUMS").read_bytes()
    def post_write():
        require(sources() == record["source_code"], "Admission source changed during installation")
        require(model.check(before, {n: (ROOT / n).read_bytes() for n in model.META},
                            json.loads((ROOT / "docs/literature/crossmap-source-audit/ROLES.json").read_bytes()),
                            record["qualification"]) == record["result"], "Installed interpretation differs")
        require(manifest(args.artifact_root)[1] == 877, "Installed complete manifest differs")
        return record["result"]
    result = install(args, expected, updates, post_write)
    return dict(status=STATUS, result=result, actual_workflow_readback=actual, canonical_modified=True)


def verify(args):
    proof = json.loads((ROOT / model.PROOF).read_bytes())
    require(proof["status"] == STATUS and proof["base_commit"] == BASE and proof["source_code"] == sources(),
            "Changed catalog source/scope; restore the immutable context")
    require((ROOT / FOLDER / "STATUS.json").read_bytes() == (ROOT / model.PROOF).read_bytes(), "Changed admission status")
    for name, wanted in proof["evidence"].items():
        require(file_pin(ROOT / workflow.member(name)) == wanted, "Changed admission evidence: " + name)
    require(file_pin(ROOT / model.COVERAGE) == proof["coverage"], "Changed source-role coverage")
    before = read_archive(ROOT / FOLDER / "metadata-before.zip")
    after = {n: (ROOT / n).read_bytes() for n in model.META}
    require({n: pin(raw) for n, raw in before.items()} == proof["before"]
            and {n: pin(raw) for n, raw in after.items()} == proof["after"], "Changed predecessor/current metadata")
    roles, qualification, actual = qualified(args)
    require(qualification == proof["qualification"] and model.check(before, after, roles, qualification) == proof["result"],
            "Changed admitted source-role interpretation")
    require((ROOT / FOLDER / "REGISTRY.json").read_bytes() == encoded(model.registry()), "Changed provider registry")
    require(manifest(args.artifact_root)[1] == 877, "Changed complete catalog manifest")
    return dict(status=STATUS, result=proof["result"], actual_workflow_readback=actual, canonical_modified=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    for mode in ("plan", "inspect", "apply", "verify"):
        group.add_argument("--" + mode, action="store_true")
    for name in ("out", "private-root", "producer", "predecessor-producer", "java", "cache"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--artifact-root", type=Path, default=ROOT)
    parser.add_argument("--plan-dir", type=Path)
    args = parser.parse_args()
    owned = False
    try:
        args.artifact_root = args.artifact_root.resolve()
        args.private_root = args.private_root.resolve()
        args.out = args.out.absolute()
        require(args.out.is_relative_to(args.private_root) and args.out != args.private_root,
                "Use a NEW output under the explicitly retained private evidence root")
        args.out.mkdir()
        owned = True
        if args.apply or args.inspect:
            require(args.plan_dir is not None, "Supply the concrete saved --plan-dir")
        result = plan(args) if args.plan else inspect(args)[0] if args.inspect else apply(args) if args.apply else verify(args)
        (args.out / "COMPLETED.json").write_bytes(encoded(result))
        print(json.dumps(dict(status="PASS_CROSSMAP_CATALOG_" + ("plan" if args.plan else "inspect" if args.inspect else "apply" if args.apply else "verify"))))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        if owned:
            (args.out / "FAILURE.json").write_bytes(encoded(dict(status="SKIP3" if code == 3 else "FAIL2", error=repr(error), traceback=traceback.format_exc(), canonical_result_admitted=False)))
        print(("SKIP3: " if code == 3 else "FAIL2: ") + str(error) + ". " + model.ACTION, file=sys.stderr)
        return code


if __name__ == "__main__":
    raise SystemExit(main())
