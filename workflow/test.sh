#!/usr/bin/env bash

echo "Start!"

echo "Start! (error)" >&2

echo "mkdir output_files"
sleep 5
mkdir output_files

echo "Start! (error)" >&2
echo "conda -V >> output_files/info.txt"
sleep 1
conda -V >> output_files/info.txt

echo "Start! (error)" >&2
echo "tree >> output_files/info.txt"
sleep 1
tree >> output_files/info.txt

echo "Start! (error)" >&2
echo "cat /dev/urandom | head -c 1024 | xxd >> output_files/info.txt"
sleep 1
cat /dev/urandom | head -c 1024 | xxd > output_files/info.txt

echo "Start! (error)" >&2
echo "cat >> output_files/hello.csv <<- EOM ..."
sleep 1
cat > output_files/hello.csv <<- EOM
Hello,World
A,1
B,2
C,3
EOM

echo "All done!"
