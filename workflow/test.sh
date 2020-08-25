#!/usr/bin/env bash

echo "Start!"

echo "mkdir output-files"
sleep 20
mkdir output-files

echo "conda -V >> output-files/info.txt"
sleep 1
conda -V >> output-files/info.txt

echo "tree >> output-files/info.txt"
sleep 1
tree >> output-files/info.txt

echo "cat /dev/urandom | head -c 1024 | xxd > output-files/info.txt"
sleep 1
cat /dev/urandom | head -c 1024 | xxd > output-files/info.txt

echo "cat > output-files/hello.csv <<- EOM ..."
sleep 1
cat > output-files/hello.csv <<- EOM
Hello,World
A,1
B,2
C,3
EOM

echo "All done!"
