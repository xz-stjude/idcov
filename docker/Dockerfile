FROM nextflow/nextflow:20.07.1


# # Update packages and install dependency packages for services
# RUN apt-get update \
# && apt-get dist-upgrade -y \
# && apt-get clean \
# && echo 'Finished installing dependencies'

WORKDIR "/app"

COPY workflow /app/
ENV WORKFLOW_PATH /app/workflow

RUN mkdir /idcov
ENV BASEPATH /idcov

COPY idcov_latest.jar /app/idcov.jar

ENV REFS_PATH /refs

ENV PORT 8080

EXPOSE 8080

CMD ["java", "-jar", "idcov.jar"]
