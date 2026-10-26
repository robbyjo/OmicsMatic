"""Private hash-verified Windows SAMtools/BCFtools runtime; no package scripts."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path, PurePosixPath
import platform
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
LOCK = Path(__file__).with_name("msys2-hts-lock.json")
MARKER = ".jlinalg-hts.json"


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def available(environment, lock_hash):
    try:
        marker = json.loads((environment / MARKER).read_text(encoding="utf-8"))
        if marker["lock_sha256"] != lock_hash or not marker["files"]:
            return False
        for name, expected in marker["files"].items():
            path = (environment / name).resolve()
            if not path.is_relative_to(environment) or not path.is_file() or digest(path) != expected:
                return False
        return all((environment / "ucrt64/bin" / (tool + ".exe")).is_file() for tool in ("samtools", "bcftools"))
    except (OSError, ValueError, KeyError):
        return False


def extract_package(archive, staging, package, zstandard):
    total = 0
    with archive.open("rb") as compressed, zstandard.ZstdDecompressor().stream_reader(compressed) as stream:
        with tarfile.open(fileobj=stream, mode="r|") as tar:
            for item in tar:
                relative = PurePosixPath(item.name)
                if relative.is_absolute() or ".." in relative.parts or "\\" in item.name or ":" in item.name:
                    raise ValueError("Unsafe archive path: " + item.name)
                if item.isdir():
                    continue
                if not item.isfile():
                    # Development-library symlinks are not needed for this binary-only runtime.
                    if item.issym() or item.islnk():
                        continue
                    raise ValueError("Unsupported archive member: " + item.name)
                total += item.size
                if item.size > 128 * 1024 * 1024 or total > 512 * 1024 * 1024:
                    raise ValueError("Excessive package extraction size")
                if relative.parts[0].startswith("."):
                    relative = PurePosixPath("package-metadata", package["name"], *relative.parts)
                elif relative.parts[0] != "ucrt64":
                    raise ValueError("Unexpected package root: " + item.name)
                target = (staging / str(relative)).resolve()
                if not target.is_relative_to(staging):
                    raise ValueError("Archive path escapes private staging")
                target.parent.mkdir(parents=True, exist_ok=True)
                if target.exists():
                    raise ValueError("Conflicting package path: " + str(relative))
                with tar.extractfile(item) as source, target.open("xb") as output:
                    shutil.copyfileobj(source, output)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--allow-download", action="store_true")
    parser.add_argument("--environment", type=Path, default=ROOT / "build/omics-hts")
    parser.add_argument("--cache", type=Path, default=ROOT / "build/omics-hts-download")
    parser.add_argument("--report", type=Path, default=ROOT / "build/literature-review/hts-dependency")
    args = parser.parse_args()
    environment, cache, report = args.environment.resolve(), args.cache.resolve(), args.report.resolve()
    report.mkdir(parents=True, exist_ok=True)
    lock_hash = digest(LOCK)
    record = {"recorded_utc": dt.datetime.now(dt.timezone.utc).isoformat(), "environment": str(environment),
        "platform": platform.platform(), "lock_sha256": lock_hash, "download_authorized": args.allow_download,
        "source": "MSYS2 UCRT64 binary packages; not an upstream Windows binary release"}
    if available(environment, lock_hash):
        record.update(status="available-not-yet-analysis-validated", action="Supply executables under " + str(environment / "ucrt64/bin") + "; analysis validation is separate.")
    elif not args.allow_download:
        record.update(status="skipped-install-not-permitted", action="Where permitted run python tools/literature/install_hts_dependencies.py --allow-download. If forbidden, skip affected analyses until an administrator supplies SAMtools/BCFtools 1.24 and HTSlib 1.24.")
    elif environment.exists():
        record.update(status="skipped-existing-environment", action="Choose a new --environment; existing unmanaged or integrity-failed files are preserved.")
    elif sys.platform != "win32" or platform.machine().lower() not in {"amd64", "x86_64"}:
        record.update(status="skipped-platform-unavailable", action="This private installer supplies Windows x86_64 UCRT binaries. On other hosts install pinned SAMtools/BCFtools 1.24 using the allowed native package/build process, then provide explicit executable paths.")
    else:
        try:
            import zstandard
            packages = json.loads(LOCK.read_text(encoding="utf-8"))["packages"]
            cache.mkdir(parents=True, exist_ok=True)
            environment.parent.mkdir(parents=True, exist_ok=True)
            staging = Path(tempfile.mkdtemp(prefix=".jlinalg-hts-", dir=environment.parent)).resolve()
            record["installation_staging_directory"] = str(staging)
            record["packages"] = packages
            for package in packages:
                archive = cache / package["filename"]
                if not archive.exists():
                    with tempfile.NamedTemporaryFile(prefix="hts-", suffix=".part", dir=report, delete=False) as stream:
                        temporary = Path(stream.name)
                        record["last_download_staging_file"] = str(temporary)
                        with urllib.request.urlopen(package["url"], timeout=60) as response:
                            total = 0
                            while block := response.read(1024 * 1024):
                                total += len(block)
                                if total > package["size"]:
                                    raise ValueError("Download exceeds pinned package size")
                                stream.write(block)
                    if digest(temporary) != package["sha256"]:
                        raise ValueError("Publisher package checksum mismatch: " + package["name"])
                    temporary.replace(archive)
                if archive.stat().st_size != package["size"] or digest(archive) != package["sha256"]:
                    raise ValueError("Cached package integrity mismatch; preserved: " + str(archive))
                extract_package(archive, staging, package, zstandard)
            files = {p.relative_to(staging).as_posix(): digest(p) for p in sorted(staging.rglob("*")) if p.is_file()}
            (staging / MARKER).write_text(json.dumps({"lock_sha256": lock_hash, "files": files}, indent=2) + "\n", encoding="utf-8")
            record["version_probes"] = {}
            for tool in ("samtools", "bcftools"):
                result = subprocess.run([str(staging / "ucrt64/bin" / (tool + ".exe")), "--version"], capture_output=True, text=True, timeout=30)
                record["version_probes"][tool] = {"exit_code": result.returncode, "stdout": result.stdout, "stderr": result.stderr}
                if result.returncode != 0 or not result.stdout.startswith(tool + " 1.24\n"):
                    raise ValueError(tool + " executable or its DLL dependencies failed the pinned version probe")
            staging.replace(environment)
            record.update(status="installed-not-yet-analysis-validated", action="Use executables under " + str(environment / "ucrt64/bin") + "; no global PATH or OS package installation was performed. Real analysis validation is separate.")
        except Exception as failure:
            record.update(status="skipped-dependency-unavailable", error=str(failure), action="Inspect retained package cache, staging files and version probes. Install Python zstandard if missing in an allowed environment, resolve the exact network/checksum/DLL error, choose a new environment and rerun. Skip affected analyses until the required runtime is available.")
    (report / "dependency-status.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    skipped = record["status"].startswith("skipped")
    (report / "WARNINGS.md").write_text(("# HTS DEPENDENCY UNAVAILABLE - ANALYSIS SKIPPED\n\n" + record.get("error", record["status"]) + "\n\nRequired action: " + record["action"] + "\n")
        if skipped else "# HTS runtime available\n\nInstallation/integrity checks do not establish successful analysis.\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in record.items() if k != "packages"}))
    return 3 if skipped else 0


if __name__ == "__main__":
    raise SystemExit(main())
