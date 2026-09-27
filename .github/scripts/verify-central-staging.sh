#!/usr/bin/env bash

set -euo pipefail
exec python3 -B "$(dirname "$0")/central_release_inventory.py" staging "$@"
