"""Export explicit mathematical calibration data using an installed, unmodified FlowKit 1.3.2.

No FlowKit/cytolib biex generator is copied into OmicsMatic. This optional helper
calls upstream software in the user's environment; Java consumes the resulting
fully pinned tables. No vendor software is downloaded, installed, or executed.
"""
from pathlib import Path
import argparse
import hashlib
import importlib.metadata
import json
import sys

T = "http://www.isac-net.org/std/Gating-ML/v2.0/transformations"
D = "http://www.isac-net.org/std/Gating-ML/v2.0/datatypes"


def pin(path):
    return {"bytes": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", required=True, type=Path)
    parser.add_argument("--sample-id", required=True)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    owned = None
    try:
        try:
            import numpy as np
            from lxml import etree
            import flowkit
        except ImportError as error:
            print("SKIP3: calibration export skipped: " + str(error) +
                  ". Install permitted FlowKit 1.3.2 in an isolated environment, or supply an independently exported exact table. "
                  "Do not substitute logicle. No analysis has run.", file=sys.stderr)
            return 3
        if importlib.metadata.version("flowkit") != "1.3.2":
            print("SKIP3: exact FlowKit 1.3.2 unavailable. Install that version in a permitted isolated environment or supply a matching exported calibration. No analysis ran.", file=sys.stderr)
            return 3
        pins = json.loads(Path(__file__).with_name("flowkit-1.3.2-source-pins.json").read_text(encoding="utf-8"))
        package = Path(flowkit.__file__).parent
        for name, identity in pins["files"].items():
            if pin(package / name) != identity:
                raise ValueError("Installed FlowKit source differs from pinned official 1.3.2 file: " + name)
        source = args.workspace.resolve(strict=True)
        before = pin(source)
        if before["bytes"] > 16777216:
            raise ValueError("Complete WSP exceeds 16 MiB capacity")
        with source.open("rb") as stream:
            raw = stream.read(16777217)
        if len(raw) > 16777216 or hashlib.sha256(raw).hexdigest() != before["sha256"]:
            raise ValueError("Workspace changed or exceeded capacity")
        root = etree.fromstring(raw, etree.XMLParser(resolve_entities=False, load_dtd=False, no_network=True, huge_tree=False))
        if root.getroottree().docinfo.doctype:
            raise ValueError("DOCTYPE is not permitted")
        if root.tag != "Workspace" or root.get("version") != "20.0" or not root.get("flowJoVersion", "").startswith("10."):
            raise ValueError("Require FlowJo 10.x WSP format 20.0")
        samples = [s for s in root.findall("SampleList/Sample") if s.find("SampleNode") is not None and s.find("SampleNode").get("sampleID") == args.sample_id]
        if len(samples) != 1:
            raise ValueError("Require exactly one sample with the explicit sampleID; no sample-name/URI guess")
        target = args.out.absolute()
        if target.exists() or source.is_relative_to(target):
            raise ValueError("Use NEW output outside the original input")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.mkdir()
        owned = target
        tables = []
        for element in samples[0].find("Transformations"):
            if element.tag != "{" + T + "}biex":
                continue
            if element.get("{" + T + "}length") != "256":
                raise ValueError("Biex length other than literal 256 is unsupported")
            parameter = element.find("{" + D + "}parameter").attrib["{" + D + "}name"]
            get = lambda name: float(element.attrib["{" + T + "}" + name])
            transform = flowkit.transforms.WSPBiexTransform(negative=get("neg"), width=get("width"), positive=get("pos"), max_value=get("maxRange"))
            y = np.arange(4097, dtype=np.float64)
            x = transform.inverse(y)
            if not np.isfinite(x).all() or not (np.diff(x) > 0).all():
                raise ValueError("Upstream calibration is nonfinite/nonmonotone; no repair")
            table = target / ("calibration-%d.tsv" % len(tables))
            with table.open("x", encoding="utf-8", newline="\n") as stream:
                np.savetxt(stream, np.column_stack([x, y]), delimiter="\t", header="input\toutput", comments="", fmt="%.17g")
            tables.append({"parameter": parameter, "attributes": dict(element.attrib), "table": str(table), "pin": pin(table),
                           "provenance": "Unmodified installed FlowKit 1.3.2 WSPBiexTransform.inverse on 0..4096; explicit WSP parameters; not a vendor runtime result"})
        if pin(source) != before:
            raise ValueError("Original workspace changed during calibration export")
        result = {"status": "PASS_CALIBRATION_DATA_ONLY", "sample_id": args.sample_id, "workspace": before,
                  "flowkit": "1.3.2", "numpy": importlib.metadata.version("numpy"), "calibrations": tables,
                  "upstream_package_source_checked": pins["commit"], "source_files_checked": len(pins["files"]),
                  "environment": {name: importlib.metadata.version(name) for name in ("flowkit", "flowio", "flowutils", "numpy", "scipy", "lxml", "pandas")},
                  "gating_analysis_executed": False, "vendor_runtime_executed": False}
        with (target / "CALIBRATIONS.json").open("x", encoding="utf-8") as stream:
            json.dump(result, stream, indent=2, allow_nan=False)
            stream.write("\n")
        print("PASS calibration data only: copy CALIBRATIONS.json's calibrations array into the explicit Java request. No gating analysis ran.")
        return 0
    except Exception as error:
        message = "FAIL2: no calibration export admitted: " + repr(error) + ". Preserve original inputs and partial output, correct the named problem and retry NEW output."
        if owned is not None:
            with (owned / "FAILURE.txt").open("x", encoding="utf-8") as stream:
                stream.write(message + "\n")
        print(message, file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
