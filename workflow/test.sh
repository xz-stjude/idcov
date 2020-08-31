#!/usr/bin/env bash

echo "Start!"

echo "Start! (error)" >&2

echo "mkdir output_files"
sleep 1
mkdir output_files

echo "Start! (error)" >&2
echo "conda -V >> output_files/info.txt"
conda -V >> output_files/info.txt

echo "Start! (error)" >&2
echo "tree >> output_files/info.txt"
tree >> output_files/info.txt

echo "Start! (error)" >&2
echo "cat /dev/urandom | head -c 1024 | xxd >> output_files/info.txt"
cat /dev/urandom | head -c 1024 | xxd > output_files/info.txt

echo "Start! (error)" >&2
echo "cat >> output_files/hello.csv <<- EOM ..."
cat > output_files/hello.csv <<- EOM
Hello,World
A,1
B,2
C,3
EOM

echo "curl 'https://www.google.com/images/branding/googlelogo/1x/googlelogo_color_272x92dp.png' > output_files/google.png"
curl 'https://www.google.com/images/branding/googlelogo/1x/googlelogo_color_272x92dp.png' > output_files/google.png

conda activate r

R -e "rmarkdown::render(input='bin/test_rmarkdown.Rmd', output_format='html_document', output_file='index.html', output_dir='output_files')"


cat > output_files/test.html <<- EOM
<!DOCTYPE html>
<html>
    <head>
        <!-- head definitions go here -->
    </head>
    <body>
        Hello, World!
        <img src="google.png">
    </body>
</html>
EOM

echo "All done!"
