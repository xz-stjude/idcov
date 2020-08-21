#!/usr/bin/env bash

mkdir output-files
conda -V >> output-files/info.txt
tree >> output-files/info.txt
cat /dev/urandom | head -c 1024 | xxd > output-files/info.txt

cat > output-files/hello.csv <<- EOM
Hello,World
A,1
B,2
C,3
EOM
