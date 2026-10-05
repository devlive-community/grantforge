#!/bin/sh
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Stops the server if it runs, then starts it.

BIN=$(cd "$(dirname "$0")" && pwd)
sh "$BIN/shutdown.sh"
sh "$BIN/startup.sh"
