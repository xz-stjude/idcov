FROM clojure:openjdk-8-tools-deps-1.10.1.561-buster

# # Update packages and install dependency packages for services
# RUN apt-get update \
# && apt-get dist-upgrade -y \
# && apt-get clean \
# && echo 'Finished installing dependencies'

# ENV PORT 3000

RUN mkdir /var/idcov

EXPOSE 3000

COPY . /app
# Change working directory
WORKDIR "/app/web-app"

CMD ["clj", "-m", "app.server-main"]
