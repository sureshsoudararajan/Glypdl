#!/usr/bin/env python3
"""Main test runner for Glypdl test suite."""

import sys
import os
from pathlib import Path
import unittest

# Ensure src is in sys.path
sys_path_root = Path(__file__).resolve().parent.parent / "src"
if str(sys_path_root) not in sys.path:
    sys.path.insert(0, str(sys_path_root))

if __name__ == "__main__":
    loader = unittest.TestLoader()
    suite = loader.discover(start_dir=str(Path(__file__).parent))
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    
    # os._exit cleanly exits without triggering GTK4/EGL shutdown teardown crash in headless CI
    os._exit(0 if result.wasSuccessful() else 1)
