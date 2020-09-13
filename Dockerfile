FROM node:10

# Change working directory
WORKDIR "/app"

# Update packages and install dependency packages for services
RUN apt-get update \
&& apt-get dist-upgrade -y \
&& apt-get clean \
&& echo 'Finished installing dependencies'


ENV NODE_ENV production
ENV PORT 3000

EXPOSE 3000

USER cheetah

COPY . /app

CMD ["npm", "start"]
