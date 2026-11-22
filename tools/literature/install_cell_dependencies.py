"""Install pinned optional tool profiles into a private, explicitly allowed venv.

Each package is attempted separately. Unavailable wheels or forbidden installs
are reported as skipped dependencies with remediation; never as passed analyses.
"""
import argparse
import datetime as dt
import json
from pathlib import Path
import subprocess
import sys
import venv

ROOT = Path(__file__).resolve().parents[2]
PINS = {"umap": "umap-learn==0.5.12", "leiden": "leidenalg==0.12.0",
        "louvain": "igraph==1.0.0", "harmony": "harmonypy==2.1.0"}
SEQUENCE_PINS = {"cutadapt": "cutadapt==5.2", "multiqc": "multiqc==1.35"}


def already_available(python, requirement):
    """Read installed distribution metadata without installing or importing a tool."""
    if not python.is_file():
        return False
    name, expected = requirement.split("==", 1)
    command = [str(python), "-I", "-c", "import importlib.metadata; print(importlib.metadata.version(" + repr(name) + "))"]
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=30)
        return result.returncode == 0 and result.stdout.strip() == expected
    except (OSError, subprocess.TimeoutExpired):
        return False


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--profile", choices=["cell", "sequence"], default="cell")
    parser.add_argument("--allow-install", action="store_true")
    parser.add_argument("--allow-source-build", action="store_true")
    parser.add_argument("--environment", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    pins = PINS if args.profile == "cell" else SEQUENCE_PINS
    environment = (args.environment or ROOT / ("build/omics-" + args.profile + "-tools-env")).resolve()
    report = (args.report or ROOT / "build/literature-review" / ("dependencies" if args.profile == "cell" else "sequence-dependencies")).resolve()
    report.mkdir(parents=True, exist_ok=True)
    marker = environment / (".jlinalg-" + args.profile + "-tools.json")
    python = environment / ("Scripts/python.exe" if sys.platform == "win32" else "bin/python")
    available = {method for method, package in pins.items() if already_available(python, package)}
    records = [{"method": method, "dependency": package, "status": "available-not-yet-analysis-validated",
                "action": "No installation is needed; use this Python for the adapter. Scientific runtime validation remains separate."}
               for method, package in pins.items() if method in available]
    pending = {method: package for method, package in pins.items() if method not in available}
    if not args.allow_install:
        records += [{"method": method, "dependency": package, "status": "skipped-install-not-permitted",
                    "action": "Install in an approved private environment, or rerun this installer with --allow-install where permitted."}
                   for method, package in pending.items()]
    elif environment.exists() and not marker.is_file():
        records += [{"method": method, "dependency": package, "status": "skipped-unmanaged-environment",
                    "action": "Choose a new --environment directory. Existing unmanaged environments are not modified."}
                   for method, package in pending.items()]
    elif pending:
        try:
            if not environment.exists():
                venv.EnvBuilder(with_pip=True).create(environment)
                marker.write_text(json.dumps({"owner": "JLinAlg literature " + args.profile + " adapters", "created_utc": dt.datetime.now(dt.timezone.utc).isoformat()}) + "\n")
            for method, package in pending.items():
                command = [str(python), "-m", "pip", "install", "--only-binary=:all:", "--disable-pip-version-check",
                           "--report", str(report / (method + "-pip-report.json")), package]
                log = report / (method + "-install.log")
                with log.open("w", encoding="utf-8") as stream:
                    result = subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, timeout=900)
                attempts = [{"command": command, "exit_code": result.returncode, "log": str(log)}]
                if result.returncode and args.allow_source_build:
                    command = [str(python), "-m", "pip", "install", "--disable-pip-version-check",
                               "--report", str(report / (method + "-source-pip-report.json")), package]
                    log = report / (method + "-source-install.log")
                    with log.open("w", encoding="utf-8") as stream:
                        result = subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, timeout=900)
                    attempts.append({"command": command, "exit_code": result.returncode, "log": str(log)})
                record = {"method": method, "dependency": package, "command": command, "exit_code": result.returncode,
                          "log": str(log), "installation_attempts": attempts,
                          "status": "installed-not-yet-analysis-validated" if result.returncode == 0 else "skipped-dependency-unavailable"}
                if result.returncode:
                    record["action"] = ("Inspect the installation log. Install " + package + " in a supported Python/OS environment or an approved source build, then point the adapter at that Python. "
                                        + ("Harmony 2.x publishes Linux/macOS wheels. " if method == "harmony" else "")
                                        + "The dependent analysis is skipped until this succeeds.")
                records.append(record)
                print(json.dumps(record), flush=True)
        except Exception as error:
            attempted = {record.get("method") for record in records}
            records += [{"method": method, "dependency": package, "status": "skipped-installation-failed", "error": str(error),
                         "action": "Use a writable approved environment with Python venv/pip and network access; inspect the recorded error and rerun."}
                        for method, package in pending.items() if method not in attempted]
    records.sort(key=lambda record: list(pins).index(record["method"]))
    result = {"environment": str(environment), "install_authorized": args.allow_install,
              "profile": args.profile,
              "source_build_authorized": args.allow_source_build,
              "platform": sys.platform, "python": sys.version, "records": records}
    (report / "dependency-status.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    warnings = [r for r in records if r["status"].startswith("skipped")]
    lines = ["# Dependency warnings and skipped analyses", "",
             "Installation is separate from scientific workflow validation. A successful installation is not a passed analysis.", ""]
    if not warnings:
        lines += ["No dependency installation failures. Analysis validation remains separate.", ""]
    for warning in warnings:
        lines += ["## SKIPPED: " + warning.get("method", "environment setup"), "",
                  "- Status: " + warning["status"], "- Required dependency: " + warning.get("dependency", "Python environment"),
                  "- What to do: " + warning["action"], "- Error/log: " + warning.get("error", warning.get("log", "installation not attempted")), ""]
    (report / "WARNINGS.md").write_text("\n".join(lines), encoding="utf-8")
    return 3 if warnings else 0


if __name__ == "__main__":
    raise SystemExit(main())
