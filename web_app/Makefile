dev:
	cd /home/xzhu/Documents/stjude-covid19-study/web_app/fulcro-app/; tmux new-session -s IDCOV -n shadow 'npx shadow-cljs server' \; new-window -n clj 'em src/main/app/server_main.clj' \; new-window -n cljs 'fish'

test:
	npm install
	npx shadow-cljs compile ci-tests
	npx karma start --single-run
	clj -A:dev:clj-tests

.PHONY: test
