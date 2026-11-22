"""Install the verified FastQC release privately, only where downloads are allowed."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import stat
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
VERSION = "0.13.0"
URL = "https://github.com/s-andrews/FastQC/releases/download/v0.13.0/fastqc_v0.13.0.zip"
SHA256 = "c9504d47752e79ecfe61691e09bf367fc3f15167ac3fe83d9e33534fffbcc301"
MARKER = ".jlinalg-fastqc.json"


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def available(environment):
    marker = environment / MARKER
    if not marker.is_file():
        return False
    try:
        data = json.loads(marker.read_text(encoding="utf-8"))
        if data["archive_sha256"] != SHA256 or data["version"] != VERSION or not data["files"]:
            return False
        for name, expected in data["files"].items():
            path = (environment / name).resolve()
            if not path.is_relative_to(environment.resolve()) or not path.is_file() or digest(path) != expected:
                return False
        return True
    except (OSError, ValueError, KeyError):
        return False


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--allow-download", action="store_true")
    parser.add_argument("--environment", type=Path, default=ROOT / "build/omics-fastqc")
    parser.add_argument("--archive", type=Path, default=ROOT / "build/omics-fastqc-download/fastqc_v0.13.0.zip")
    parser.add_argument("--report", type=Path, default=ROOT / "build/literature-review/fastqc-dependency")
    args = parser.parse_args()
    environment, archive, report = args.environment.resolve(), args.archive.resolve(), args.report.resolve()
    report.mkdir(parents=True, exist_ok=True)
    record = {"method": "fastqc", "version": VERSION, "source": URL, "archive_sha256": SHA256,
              "environment": str(environment), "download_authorized": args.allow_download,
              "recorded_utc": dt.datetime.now(dt.timezone.utc).isoformat()}
    if available(environment):
        record.update(status="available-not-yet-analysis-validated", action="Use --fastqc-home " + str(environment / "FastQC") + "; analysis validation remains separate.")
    elif not args.allow_download:
        record.update(status="skipped-install-not-permitted", action="In an approved environment run python tools/literature/install_fastqc.py --allow-download, then supply --fastqc-home build/omics-fastqc/FastQC. The affected analysis must be skipped until available.")
    elif environment.exists():
        record.update(status="skipped-existing-environment", action="Choose a new --environment directory. The existing unmanaged or integrity-failed installation is preserved.")
    else:
        try:
            if not archive.exists():
                archive.parent.mkdir(parents=True, exist_ok=True)
                with tempfile.NamedTemporaryFile(prefix="fastqc-download-", suffix=".part", dir=report, delete=False) as stream:
                    record["download_staging_file"] = stream.name
                    with urllib.request.urlopen(URL, timeout=60) as response:
                        total = 0
                        while block := response.read(1024 * 1024):
                            total += len(block)
                            if total > 20 * 1024 * 1024:
                                raise ValueError("Release exceeds the pinned archive size bound")
                            stream.write(block)
                temporary = Path(record["download_staging_file"])
                if digest(temporary) != SHA256:
                    raise ValueError("Downloaded FastQC archive failed publisher SHA-256 verification")
                temporary.replace(archive)
            if digest(archive) != SHA256:
                raise ValueError("Cached FastQC archive failed publisher SHA-256 verification; preserved for inspection")
            environment.parent.mkdir(parents=True, exist_ok=True)
            staging = Path(tempfile.mkdtemp(prefix=".jlinalg-fastqc-", dir=environment.parent)).resolve()
            record["extraction_staging_directory"] = str(staging)
            with zipfile.ZipFile(archive) as source:
                total = 0
                for entry in source.infolist():
                    target = (staging / entry.filename).resolve()
                    total += entry.file_size
                    if not target.is_relative_to(staging) or stat.S_ISLNK(entry.external_attr >> 16) or total > 200 * 1024 * 1024:
                        raise ValueError("Unsafe or excessive release archive member")
                source.extractall(staging)
            files = {p.relative_to(staging).as_posix(): digest(p) for p in sorted(staging.rglob("*")) if p.is_file()}
            (staging / MARKER).write_text(json.dumps({"version": VERSION, "source": URL, "archive_sha256": SHA256,
                "files": files}, indent=2) + "\n", encoding="utf-8")
            staging.replace(environment)
            record.update(status="installed-not-yet-analysis-validated", action="Use --fastqc-home " + str(environment / "FastQC") + "; Java runtime and actual analysis validation remain separate.")
        except Exception as error:
            record.update(status="skipped-dependency-unavailable", error=str(error), action="Inspect the exact error and retained staging/cache files. Use a new writable --environment and an approved network/cache path; obtain the pinned official archive and verify its SHA-256. Rerun installation, then rerun the affected analysis to a new output directory.")
    (report / "dependency-status.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    skipped = record["status"].startswith("skipped")
    message = "# FASTQC DEPENDENCY UNAVAILABLE - ANALYSIS SKIPPED\n\n" + record.get("error", record["status"]) + "\n\nRequired action: " + record["action"] + "\n" if skipped else "# FastQC dependency available\n\nInstallation/integrity checks do not establish successful analysis.\n"
    (report / "WARNINGS.md").write_text(message, encoding="utf-8")
    print(json.dumps(record))
    return 3 if skipped else 0


if __name__ == "__main__":
    raise SystemExit(main())
