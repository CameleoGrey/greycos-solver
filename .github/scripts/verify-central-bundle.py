#!/usr/bin/env python3
"""Check the exact bundle produced by JReleaser deploy --dry-run."""

import sys

from central_release_inventory import main


if __name__ == "__main__":
    raise SystemExit(main(["bundle", *sys.argv[1:]]))
