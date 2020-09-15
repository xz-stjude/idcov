idCOV
=====

Installation
------------

  1. Download the JAR file.
  2. Download the workflow folder.
  2. Download the reference files.
  2. Make sure anaconda is installed.
  2. Make sure java 8 is installed.
  3. Execute `env WORKFLOW_PATH=./workflow REFS_PATH=./refs java PORT=3000 -jar idcov.jar`
  
  
  1. cat idcov_docker_image.tar.gz | gunzip | docker load
  1. docker run -p 8080:8080 -v /data/1000/home/stjude-covid19-study/refs:/refs -v /data/1000/var/idcov:/idcov -it idcov:latest
