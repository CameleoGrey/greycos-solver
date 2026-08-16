#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <release-version>" >&2
  exit 2
fi

release_version="$1"
staging_root="target/staging-deploy"
namespace_root="$staging_root/io/github/cameleogrey"

if [[ ! "$release_version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "Invalid release version: $release_version" >&2
  exit 1
fi
if [[ ! -d "$namespace_root" ]]; then
  echo "Missing staged namespace: $namespace_root" >&2
  exit 1
fi
if [[ -d "$staging_root/greycos/solver" ]]; then
  echo "The obsolete greycos.solver Maven group is present in staging." >&2
  exit 1
fi

mapfile -d '' -t project_poms < <(
  find "$namespace_root" -type f -name "*-$release_version.pom" -print0 | sort -z
)
mapfile -d '' -t binary_jars < <(
  find "$namespace_root" -type f -name "*-$release_version.jar" -print0 | sort -z
)
mapfile -d '' -t source_jars < <(
  find "$namespace_root" -type f -name "*-$release_version-sources.jar" -print0 | sort -z
)
mapfile -d '' -t javadoc_jars < <(
  find "$namespace_root" -type f -name "*-$release_version-javadoc.jar" -print0 | sort -z
)

all_pom_count="$(find "$staging_root" -type f -name '*.pom' -print | wc -l)"
if (( ${#project_poms[@]} != 34 || all_pom_count != 34 )); then
  echo "Expected 34 staged project POMs; found ${#project_poms[@]} in the namespace and $all_pom_count total." >&2
  exit 1
fi
if (( ${#binary_jars[@]} != 23 )); then
  echo "Expected 23 main JARs; found ${#binary_jars[@]}." >&2
  exit 1
fi
if (( ${#source_jars[@]} != 23 )); then
  echo "Expected 23 source JARs; found ${#source_jars[@]}." >&2
  exit 1
fi
if (( ${#javadoc_jars[@]} != 21 )); then
  echo "Expected 21 Javadoc JARs; found ${#javadoc_jars[@]}." >&2
  exit 1
fi

snapshot_file="$(find "$staging_root" -type f -name '*SNAPSHOT*' -print -quit)"
if [[ -n "$snapshot_file" ]]; then
  echo "A snapshot artifact was staged: $snapshot_file" >&2
  exit 1
fi
if rg -l -F -e '<groupId>greycos.solver</groupId>' -e 'greycos.solver:greycos-solver' "${project_poms[@]}"; then
  echo "An obsolete Maven coordinate remains in a staged POM." >&2
  exit 1
fi

for binary_jar in "${binary_jars[@]}"; do
  artifact_base="${binary_jar%-$release_version.jar}"
  artifact_id="$(basename "$artifact_base")"
  if [[ ! -f "$artifact_base-$release_version-sources.jar" ]]; then
    echo "Missing sources for $artifact_id." >&2
    exit 1
  fi
  case "$artifact_id" in
    greycos-solver-ide-config|greycos-solver-webui)
      if [[ -f "$artifact_base-$release_version-javadoc.jar" ]]; then
        echo "Unexpected Javadoc JAR for resource-only artifact $artifact_id." >&2
        exit 1
      fi
      ;;
    *)
      if [[ ! -f "$artifact_base-$release_version-javadoc.jar" ]]; then
        echo "Missing Javadocs for $artifact_id." >&2
        exit 1
      fi
      ;;
  esac
done

find "$staging_root" -type f -printf '%P\n' | sort > target/central-staging-manifest.txt
echo "Verified Maven Central staging: 34 projects, 23 JARs, 23 source JARs, and 21 Javadoc JARs."
