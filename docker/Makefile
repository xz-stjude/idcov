docker:
	rsync -av ../workflow/ ./workflow \
	&& rsync -av ../web_app/releases/latest.jar ./idcov_latest.jar \
	&& docker build -t idcov:latest .

run:
	docker run \
    -p 8080:8080 \
    -v /data/1000/home/stjude-covid19-study/refs:/refs:ro \
    -v /data/0/var/idcov:/idcov \
    -v /data/1000/home/tmp/covid_samples/selected:/example:ro \
    -it idcov:latest
