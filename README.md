idCOV
=====

Installation
------------

### 1. Docker

[Install Docker](https://docs.docker.com/get-docker/). Confirm success by
executing `docker images`. It should show you a list of your local docker
images. Note that this list may be empty for a fresh installation of Docker, in
which case you will only see a line of column headers. Consult the [docker
installation guide](https://docs.docker.com/get-docker/) if any error
occurs.

### 2. Download

Download the idCOV docker image and the indexed reference genome (human + SARS-Cov-2).

```
wget http://168.61.40.201/get-idcov/idcov_docker_image.tar.gz
wget http://168.61.40.201/get-idcov/refs.tar.gz
```

### 3. Unzip the reference

Unzip the reference genome files.

```
tar xvf refs.tar.gz
```

### 4. Load the docker file

```
cat idcov_docker_image.tar.gz | gunzip | docker load
```

Confirm by typing `docker images` in your shell. You should see a new image
tagged "idcov" appearing in the list.

### 5. Start the server

```
docker run -p 8080:8080 \
  -v `pwd`/refs:/refs \
  -v `pwd`/idcov:/idcov \
  -it \
  idcov:latest
```

You should see a welcome message that says the server is now listening to the port 8080.

### 6. Visit the website

Open your browser and enter `http://<IP to the server machine>:8080` and you should see the website showing up.

If you deployed the server on the same machine where your browser is, the `<IP to the server machine>` is simply `localhost`.
