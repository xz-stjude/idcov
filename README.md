idCOV
=====

idCOV is a phylogenetic pipeline for quickly identifying the clades of
SARS-CoV-2 virus isolates from raw sequencing data based on a selected
clade-defining marker list. Web and equivalent command-line interfaces are
available. It can be deployed on any Linux environment, including personal
computer, HPC and the cloud.

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

Confirm success by typing `docker images` in your shell. You should see a new image
tagged "idcov" appearing in the list.

### 5. Start the server

```
docker run -p 8071:8080 \
  -v `pwd`/refs:/refs \
  -v `pwd`/idcov:/idcov \
  -it \
  idcov:latest
```

You should see a welcome message that says the server is now listening to the port 8071.

Note: The `` `pwd\`/idcov`` part specifies the folder where idcov is going to
store its persistent state which includes the user account information and
projects. Deleting this folder in the host file system effectively "factory resets" idCOV.
You may want to specify this folder at a different location such as
`/var/lib/idcov` to suit your needs. **Be sure that both refs and idcov paths need to be
absolute (hence the `` `pwd` ``).**

### 6. Visit the website

Open your browser and enter `http://<IP to the server machine>:8071` and you should see the website showing up.

If you deployed the server on the same machine where your browser is, the `<IP to the server machine>` is simply `localhost`.


Usage
-----

### 1. Register an account

Click "Sign up" on the left panel to register for an account.

### 2. Log in

After successfully registered the account, log in with your credentials using the left panel.

### 3. Create a project

Click the "New project ..." button on the toolbar to create a new project.

In the popped up dialog, enter a name for your project and upload the FastQ files.

Note that currently idCOV supports pair-end sequences only and the FastQ files
should be in pairs, and named as "\*R1.fastq.gz" and "\*R2.fastq.gz".

After clicking the "Create Project" button, the upload process will begin and the progress will be shown on the toolbar.

When all files are uploaded, you will see a new project with your specified name appearing in the list of projects.

### 4. Start a run

Click "run" to the right of the newly created project. A new "run" will be created in the "Runs" tab.

### 5. Monitor the run progress

Make sure the "Auto-refresh" button on the top of the screen is activated.

You should see the new run is reporting its progress in the "Messages" tab.

The run is successul when you see the icon of the run turns green.

### 6. Check the report and files

Go to the "Report" tab to check the report and the "Files" tab to download the output files.
