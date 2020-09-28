jar:
	npx shadow-cljs release :main \
    && clojure -Spom \
    && clojure -A:depstar ../docker/idcov_latest.jar -C -m app.server-main

azure:
	env \
    BASEPATH=/data/1000/var/idcov/ \
    LOG_PATH=/data/1000/var/idcov/log \
    PORT=3000 \
    WORKFLOW_PATH=`realpath ../workflow/` \
    REFS_PATH=`realpath ../refs/` \
    EXAMPLE_PROJECT_PATH=/home/xzhu1/tmp/covid_samples/selected \
    npx shadow-cljs watch :main

desktop:
	env \
    BASEPATH=/var/lib/idcov/ \
    LOG_PATH=/var/lib/idcov/log \
    PORT=3000 \
    WORKFLOW_PATH=`realpath ../workflow/` \
    REFS_PATH=`realpath ../refs/` \
    EXAMPLE_PROJECT_PATH=/home/xzhu1/tmp/covid_samples/selected \
    npx shadow-cljs watch :main

hpc-server:
	env PATH="/hpcf/lsf/lsf_prod/10.1/linux3.10-glibc2.17-x86_64/etc:/hpcf/lsf/lsf_prod/10.1/linux3.10-glibc2.17-x86_64/bin:/home/xzhu1/.local/bin:/home/linuxbrew/.linuxbrew/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/home/xzhu1/.rvm/bin" \
		npx shadow-cljs server

test:
	npm install
	npx shadow-cljs compile ci-tests
	npx karma start --single-run
	clj -A:dev:clj-tests

.PHONY: test
