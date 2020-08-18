#!/usr/bin/env bash

mkdir output-files
tree > output-files/tree.txt
cat /dev/urandom | head -c 1024 | xxd > output-files/random-xxd.txt
conda -V > output-files/conda.txt
