"""Readonly closing reader: pin the exact catalog, source and checksum manifest first."""
from pathlib import Path
import argparse
import json
import sys

import crossmap_catalog as catalog
import crossmap_catalog_model as model
from crossmap_primary import encoded, require
from verify_crossmap_workflows import file_pin, member

ROOT = Path(__file__).resolve().parents[2]
FOLDER = "docs/literature/crossmap-catalog-checkpoint"
STATUS = "PASS_CROSSMAP_CATALOG_SOURCE_CLOSURE_AND_COMPLETE_SAVED_CONTRACTS"


def verify(args):
    state = json.loads((ROOT / FOLDER / "STATUS.json").read_bytes())
    require(state["status"] == STATUS and state["whole_goal_complete"] is False, "Changed closing scope")
    require(file_pin(ROOT / FOLDER / "FILES.json") == state["files"], "Changed closing source inventory")
    files = json.loads((ROOT / FOLDER / "FILES.json").read_bytes())
    for name, wanted in files.items():
        require(file_pin(ROOT / member(name)) == wanted, "Changed exact source/catalog version: " + name)
    # Reconstruct the complete after-manifest from the retained predecessor.
    # Later shared-object digests cannot be substituted by refreshing SHA256SUMS.
    old = (ROOT / catalog.FOLDER / "PREVIOUS-SHA256SUMS").read_text().splitlines()
    wanted = {}
    for line in old:
        digest, name = line.split("  ", 1)
        require(name not in wanted, "Duplicate predecessor catalog object")
        wanted[name] = digest
    require(len(wanted) == 875, "Changed predecessor checksum population")
    for name in [n for n in model.META if Path(n).parent.as_posix() == model.DATA] + [model.COVERAGE, model.PROOF]:
        wanted[Path(name).name] = file_pin(ROOT / name)["sha256"]
    order = [line.split("  ", 1)[1] for line in old]
    order += sorted(set(wanted) - set(order))
    expected = "".join(wanted[n] + "  " + n + "\n" for n in order).encode()
    require((ROOT / catalog.SUMS).read_bytes() == expected and len(wanted) == 877,
            "Historical catalog checksum entries/order changed; restore the exact context")
    for name, wanted in state["private_evidence"].items():
        require(file_pin(args.private_root / member(name)) == wanted, "Changed retained catalog validation evidence: " + name)
    result = catalog.verify(args)
    require(result["result"] == state["result"], "Closing catalog counts/interpretation differ")
    return dict(status=STATUS, result=result["result"], catalog_manifest_entries=877,
                public_source_and_metadata_files=len(files), private_catalog_files_checked=len(state["private_evidence"]),
                workflow_readback=result["actual_workflow_readback"], original_source_conversion_reexecuted=False,
                website_reports_published=False, whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("out", "private-root", "producer", "predecessor-producer", "java", "cache"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--artifact-root", type=Path, default=ROOT)
    args = parser.parse_args()
    owned = False
    try:
        args.private_root = args.private_root.resolve()
        args.artifact_root = args.artifact_root.resolve()
        args.out = args.out.absolute()
        require(args.out.is_relative_to(args.private_root) and args.out != args.private_root,
                "Use a NEW receipt directory under retained private evidence")
        args.out.mkdir()
        owned = True
        result = verify(args)
        (args.out / "COMPLETED.json").write_bytes(encoded(result))
        print(json.dumps(result, sort_keys=True))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        if owned:
            (args.out / "FAILURE.json").write_bytes(encoded(dict(status="SKIP3" if code == 3 else "FAIL2", error=repr(error), complete_result=False)))
        print(("SKIP3 DEPENDENT READBACK SKIPPED: " if code == 3 else "FAIL2 NO READBACK ADMITTED: ")
              + str(error) + ". " + model.ACTION, file=sys.stderr)
        return code


if __name__ == "__main__":
    raise SystemExit(main())
