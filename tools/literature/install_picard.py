"""Fetch the hash-pinned official Picard JAR privately, only when permitted."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
VERSION = "3.5.0"
URL = "https://github.com/broadinstitute/picard/releases/download/3.5.0/picard.jar"
SHA256 = "b7d97861c3a54ba5a421f5a317f38382f955803862d30ef4aca2bcdc54943631"
MARKER = ".jlinalg-picard.json"


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def available(environment):
    try:
        marker = json.loads((environment / MARKER).read_text(encoding="utf-8"))
        return marker["version"] == VERSION and marker["jar_sha256"] == SHA256 and digest(environment / "picard.jar") == SHA256
    except (OSError, ValueError, KeyError):
        return False


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--allow-download", action="store_true")
    parser.add_argument("--environment", type=Path, default=ROOT / "build/omics-picard")
    parser.add_argument("--archive", type=Path, default=ROOT / "build/omics-picard-download/picard-3.5.0.jar")
    parser.add_argument("--report", type=Path, default=ROOT / "build/literature-review/picard-dependency")
    args = parser.parse_args()
    environment, archive, report = args.environment.resolve(), args.archive.resolve(), args.report.resolve()
    report.mkdir(parents=True, exist_ok=True)
    record = {"method": "picard", "version": VERSION, "source": URL, "jar_sha256": SHA256,
              "environment": str(environment), "download_authorized": args.allow_download,
              "recorded_utc": dt.datetime.now(dt.timezone.utc).isoformat()}
    if available(environment):
        record.update(status="available-not-yet-analysis-validated", action="Use --picard " + str(environment / "picard.jar") + " with Java 17+; actual analysis validation is separate.")
    elif not args.allow_download:
        record.update(status="skipped-install-not-permitted", action="Where permitted run python tools/literature/install_picard.py --allow-download, then supply --picard build/omics-picard/picard.jar. If forbidden, skip affected analyses until an administrator supplies the validated JAR and Java 17+.")
    elif environment.exists():
        record.update(status="skipped-existing-environment", action="Choose a new --environment. The existing unmanaged or integrity-failed directory is preserved.")
    else:
        try:
            if not archive.exists():
                archive.parent.mkdir(parents=True, exist_ok=True)
                with tempfile.NamedTemporaryFile(prefix="picard-", suffix=".part", dir=report, delete=False) as stream:
                    record["download_staging_file"] = stream.name
                    with urllib.request.urlopen(URL, timeout=60) as response:
                        total = 0
                        while block := response.read(1024 * 1024):
                            total += len(block)
                            if total > 100 * 1024 * 1024:
                                raise ValueError("Release exceeds 100 MiB size bound")
                            stream.write(block)
                temporary = Path(record["download_staging_file"])
                if digest(temporary) != SHA256:
                    raise ValueError("Downloaded JAR failed publisher SHA-256 verification")
                temporary.replace(archive)
            if digest(archive) != SHA256:
                raise ValueError("Cached JAR failed publisher SHA-256 verification; retained for inspection")
            environment.parent.mkdir(parents=True, exist_ok=True)
            staging = Path(tempfile.mkdtemp(prefix=".jlinalg-picard-", dir=environment.parent)).resolve()
            record["installation_staging_directory"] = str(staging)
            (staging / "picard.jar").write_bytes(archive.read_bytes())
            (staging / MARKER).write_text(json.dumps({"version": VERSION, "source": URL, "jar_sha256": SHA256}, indent=2) + "\n", encoding="utf-8")
            staging.replace(environment)
            record.update(status="installed-not-yet-analysis-validated", action="Use --picard " + str(environment / "picard.jar") + " with Java 17+; actual analysis validation is separate.")
        except Exception as failure:
            record.update(status="skipped-dependency-unavailable", error=str(failure), action="Inspect retained staging/cache files and the exact error. Obtain the official pinned JAR through an allowed network/cache path, verify SHA-256, choose a new writable environment and rerun. Skip analysis until the runtime is available.")
    (report / "dependency-status.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    skipped = record["status"].startswith("skipped")
    message = ("# PICARD UNAVAILABLE - ANALYSIS SKIPPED\n\n" + record.get("error", record["status"]) + "\n\nRequired action: " + record["action"] + "\n"
               if skipped else "# Picard dependency available\n\nInstallation/integrity checks do not establish successful analysis.\n")
    (report / "WARNINGS.md").write_text(message, encoding="utf-8")
    print(json.dumps(record))
    return 3 if skipped else 0


if __name__ == "__main__":
    raise SystemExit(main())
