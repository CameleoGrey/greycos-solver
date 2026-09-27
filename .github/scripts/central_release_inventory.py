#!/usr/bin/env python3
"""Validate the fixed GreyCOS Maven Central release inventory and signed bundle."""

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path, PurePosixPath


NAMESPACE = PurePosixPath("io/github/cameleogrey")
ARTIFACT_IDS = frozenset(
    {
        "greycos-solver-parent",
        "greycos-solver-bom",
        "greycos-solver-ide-config",
        "greycos-solver-build-parent",
        "greycos-solver-core",
        "greycos-solver-persistence-parent",
        "greycos-solver-jaxb",
        "greycos-solver-jackson",
        "greycos-solver-jpa",
        "greycos-solver-spring-integration",
        "greycos-solver-spring-boot-autoconfigure",
        "greycos-solver-spring-boot-starter",
        "greycos-solver-quarkus-integration",
        "greycos-solver-quarkus-parent",
        "greycos-solver-quarkus",
        "greycos-solver-quarkus-deployment",
        "greycos-solver-quarkus-jackson-parent",
        "greycos-solver-quarkus-jackson",
        "greycos-solver-quarkus-jackson-deployment",
        "greycos-solver-quarkus-benchmark-parent",
        "greycos-solver-quarkus-benchmark",
        "greycos-solver-quarkus-benchmark-deployment",
        "greycos-solver-tools-parent",
        "greycos-solver-webui",
        "greycos-solver-benchmark",
        "greycos-solver-benchmark-aggregator",
        "greycos-solver-migration",
        "greycos-solver-docs",
    }
)
POM_ONLY_IDS = frozenset(
    {
        "greycos-solver-parent",
        "greycos-solver-bom",
        "greycos-solver-build-parent",
        "greycos-solver-persistence-parent",
        "greycos-solver-spring-integration",
        "greycos-solver-quarkus-integration",
        "greycos-solver-quarkus-parent",
        "greycos-solver-quarkus-jackson-parent",
        "greycos-solver-quarkus-benchmark-parent",
        "greycos-solver-tools-parent",
        "greycos-solver-docs",
    }
)
RESOURCE_ONLY_IDS = frozenset({"greycos-solver-ide-config", "greycos-solver-webui"})
MAIN_JAR_IDS = ARTIFACT_IDS - POM_ONLY_IDS
JAVADOC_IDS = MAIN_JAR_IDS - RESOURCE_ONLY_IDS
CHECKSUMS = ("md5", "sha1", "sha256", "sha512")
COMPANIONS = ("asc", *CHECKSUMS)
MAX_FILE_COUNT = 800  # A release must stay below this headroom target.
MAX_UNCOMPRESSED_BYTES = 80_000_000
VERSION_PATTERN = re.compile(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\Z")


class InventoryError(Exception):
    """A staged release or bundle violates its publishing contract."""


def expected_primaries(version):
    """Return relative repository paths of all files that JReleaser signs."""
    if not VERSION_PATTERN.fullmatch(version):
        raise InventoryError(f"Invalid release version: {version}")
    if (len(ARTIFACT_IDS), len(MAIN_JAR_IDS), len(JAVADOC_IDS)) != (28, 17, 15):
        raise InventoryError("The release artifact inventory is internally inconsistent")

    paths = set()
    for artifact_id in ARTIFACT_IDS:
        directory = NAMESPACE / artifact_id / version
        base = f"{artifact_id}-{version}"
        paths.add(str(directory / f"{base}.pom"))
        paths.add(str(directory / f"{base}-cyclonedx.json"))
        if artifact_id in MAIN_JAR_IDS:
            paths.add(str(directory / f"{base}.jar"))
            paths.add(str(directory / f"{base}-sources.jar"))
        if artifact_id in JAVADOC_IDS:
            paths.add(str(directory / f"{base}-javadoc.jar"))
    core = "greycos-solver-core"
    paths.add(str(NAMESPACE / core / version / f"{core}-{version}-tests.jar"))
    if len(paths) != 106:
        raise InventoryError("Expected 106 primary release files")
    return paths


def is_excluded_from_bundle(relative):
    """Mirror JReleaser 1.25.0's hidden-file and maven-metadata exclusions."""
    path = PurePosixPath(relative)
    return any(part.startswith(".") for part in path.parts) or "maven-metadata.xml" in path.name


def staging_files(staging):
    if not staging.is_dir():
        raise InventoryError(f"Missing staged repository: {staging}")
    if (staging / "greycos/solver").exists():
        raise InventoryError("The obsolete greycos.solver Maven group is present in staging")
    files = {}
    for path in staging.rglob("*"):
        if path.is_symlink():
            raise InventoryError(f"Symlink in staging: {path}")
        if path.is_file():
            relative = path.relative_to(staging).as_posix()
            if "SNAPSHOT" in relative:
                raise InventoryError(f"A snapshot artifact was staged: {relative}")
            files[relative] = path
    return files


def verify_staging(version, staging):
    expected = expected_primaries(version)
    files = staging_files(staging)
    visible = {name for name in files if not is_excluded_from_bundle(name)}
    allowed = expected | {f"{name}.{extension}" for name in expected for extension in COMPANIONS}
    missing = sorted(expected - visible)
    unexpected = sorted(visible - allowed)
    if missing or unexpected:
        raise InventoryError(
            "Staging inventory mismatch: "
            f"missing={missing[:8]} ({len(missing)} total); "
            f"unexpected={unexpected[:8]} ({len(unexpected)} total)"
        )
    obsolete_markers = (b"<groupId>greycos.solver</groupId>", b"greycos.solver:greycos-solver")
    for name in expected:
        if name.endswith(".pom"):
            contents = files[name].read_bytes()
            if any(marker in contents for marker in obsolete_markers):
                raise InventoryError(f"Obsolete Maven coordinate in {name}")
    return files, expected


def expected_bundle_paths(primaries):
    return primaries | {f"{name}.{extension}" for name in primaries for extension in COMPANIONS}


def enforce_budget(file_count, total_bytes):
    if file_count >= MAX_FILE_COUNT:
        raise InventoryError(f"Bundle has {file_count} files; required < {MAX_FILE_COUNT}")
    if total_bytes >= MAX_UNCOMPRESSED_BYTES:
        raise InventoryError(
            f"Bundle has {total_bytes} uncompressed bytes; required < {MAX_UNCOMPRESSED_BYTES}"
        )


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha256_zip_file(archive, info):
    digest = hashlib.sha256()
    with archive.open(info) as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def is_armored_pgp_signature(data):
    """Accept JReleaser's direct or compressed OpenPGP signature armor."""
    lines = data.splitlines()
    if len(lines) < 3:
        return False
    for kind in (b"SIGNATURE", b"MESSAGE"):
        if lines[0] != b"-----BEGIN PGP " + kind + b"-----":
            continue
        if lines[-1] != b"-----END PGP " + kind + b"-----":
            return False
        return any(
            re.fullmatch(rb"[A-Za-z0-9+/]+={0,2}", line) and len(line) >= 8
            for line in lines[1:-1]
        )
    return False


def verify_bundle(version, staging, bundle):
    files, primaries = verify_staging(version, staging)
    if not bundle.is_file():
        raise InventoryError(f"Missing JReleaser dry-run bundle: {bundle}")
    expected = expected_bundle_paths(primaries)
    try:
        with zipfile.ZipFile(bundle) as archive:
            info_by_name = {}
            seen = set()
            for info in archive.infolist():
                name = info.filename
                path = PurePosixPath(name)
                normalized = str(path) + ("/" if info.is_dir() else "")
                if name.startswith("/") or "\\" in name or ".." in path.parts or normalized != name:
                    raise InventoryError(f"Unsafe ZIP entry: {name}")
                if name in seen:
                    raise InventoryError(f"Duplicate ZIP entry: {name}")
                seen.add(name)
                if info.is_dir():
                    continue
                info_by_name[name] = info

            actual = set(info_by_name)
            missing = sorted(expected - actual)
            unexpected = sorted(actual - expected)
            if missing or unexpected:
                raise InventoryError(
                    "Signed bundle inventory mismatch: "
                    f"missing={missing[:8]} ({len(missing)} total); "
                    f"unexpected={unexpected[:8]} ({len(unexpected)} total)"
                )
            total_bytes = sum(info.file_size for info in info_by_name.values())
            enforce_budget(len(actual), total_bytes)

            # The dry-run ZIP must be the staged payload, with JReleaser's
            # signatures and all four checksums already present.
            for name, info in info_by_name.items():
                staged = files.get(name)
                if staged is None or staged.stat().st_size != info.file_size:
                    raise InventoryError(f"ZIP entry differs from staging: {name}")
                if sha256_zip_file(archive, info) != sha256_file(staged):
                    raise InventoryError(f"ZIP entry differs from staging: {name}")

            for name in primaries:
                with archive.open(info_by_name[name]) as source:
                    payload = source.read()
                signature = f"{name}.asc"
                with archive.open(info_by_name[signature]) as source:
                    if not is_armored_pgp_signature(source.read()):
                        raise InventoryError(f"Missing armored PGP signature content: {signature}")
                for algorithm in CHECKSUMS:
                    companion = f"{name}.{algorithm}"
                    with archive.open(info_by_name[companion]) as source:
                        checksum = source.read().strip().decode("ascii")
                    if checksum.lower() != hashlib.new(algorithm, payload).hexdigest():
                        raise InventoryError(f"Incorrect {algorithm} checksum: {companion}")
    except (OSError, zipfile.BadZipFile, UnicodeError) as exc:
        raise InventoryError(f"Cannot validate signed bundle {bundle}: {exc}") from exc

    return {
        "version": version,
        "primaryFiles": len(primaries),
        "fileCount": len(actual),
        "uncompressedBytes": total_bytes,
        "bundleZipBytes": bundle.stat().st_size,
        "fileCountLimitExclusive": MAX_FILE_COUNT,
        "uncompressedByteLimitExclusive": MAX_UNCOMPRESSED_BYTES,
        "bundle": str(bundle),
    }, sorted(actual)


def default_bundle(version):
    return Path(
        f"out/jreleaser/deploy/mavenCentral/greycos-solver/"
        f"io.github.cameleogrey-greycos-solver-{version}-bundle.zip"
    )


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("staging", "bundle"))
    parser.add_argument("version")
    parser.add_argument("--staging", type=Path, default=Path("target/staging-deploy"))
    parser.add_argument("--bundle", type=Path)
    parser.add_argument("--output-dir", type=Path, default=Path("target"))
    args = parser.parse_args(argv)
    try:
        if args.mode == "staging":
            files, primaries = verify_staging(args.version, args.staging)
            args.output_dir.mkdir(parents=True, exist_ok=True)
            (args.output_dir / "central-staging-manifest.txt").write_text(
                "\n".join(sorted(files)) + "\n", encoding="utf-8"
            )
            print(
                f"Verified Maven Central staging: 28 POMs, 28 CycloneDX JSON files, "
                f"17 main JARs, 17 source JARs, 15 Javadoc JARs, "
                f"one core test JAR ({len(primaries)} primary files)."
            )
        else:
            report, manifest = verify_bundle(
                args.version, args.staging, args.bundle or default_bundle(args.version)
            )
            args.output_dir.mkdir(parents=True, exist_ok=True)
            (args.output_dir / "central-bundle-manifest.txt").write_text(
                "\n".join(manifest) + "\n", encoding="utf-8"
            )
            (args.output_dir / "central-bundle-report.json").write_text(
                json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
            )
            print(
                f"Verified signed Maven Central bundle: {report['fileCount']} files, "
                f"{report['uncompressedBytes']} uncompressed bytes."
            )
    except InventoryError as exc:
        print(f"Maven Central release verification failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
