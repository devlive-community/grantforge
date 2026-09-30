#!/bin/sh
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

GRANTFORGE_HOME=$(pwd)

sh "$GRANTFORGE_HOME"/bin/shutdown.sh
sh "$GRANTFORGE_HOME"/bin/startup.sh
