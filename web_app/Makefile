uberjar:
	clojure -A:depstar -m hf.depstar.uberjar ../app.jar -C -m app.server-main

cloud:
	npx shadow-cljs release :main && clj -m app.server-main

azure-dev:
	env BASEPATH="/data/1000/var/idcov/" npx shadow-cljs watch :main

azure:
	env BASEPATH="/data/1000/var/idcov/" npx shadow-cljs release :main \
       && env BASEPATH="/data/1000/var/idcov/" clj -m app.server-main

dev:
	cd /home/xzhu/Documents/stjude-covid19-study/web_app/fulcro-app/; tmux new-session -s IDCOV -n shadow 'npx shadow-cljs server' \; new-window -n clj 'em src/main/app/server_main.clj' \; new-window -n cljs 'fish'

server:
	env PATH="/hpcf/lsf/lsf_prod/10.1/linux3.10-glibc2.17-x86_64/etc:/hpcf/lsf/lsf_prod/10.1/linux3.10-glibc2.17-x86_64/bin:/home/xzhu1/.local/bin:/home/linuxbrew/.linuxbrew/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/home/xzhu1/.rvm/bin" \
		npx shadow-cljs server

test:
	npm install
	npx shadow-cljs compile ci-tests
	npx karma start --single-run
	clj -A:dev:clj-tests

.PHONY: test
