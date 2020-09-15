main:
	docker build \
    -t idcov:latest \
    -t idcov:0.0.1 \
    .

release:
	docker save idcov:latest | gzip > ../releases/idcov_docker_image.tar.gz
