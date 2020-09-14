FROM nextflow/nextflow:20.07.1


# # Update packages and install dependency packages for services
# RUN apt-get update \
# && apt-get dist-upgrade -y \
# && apt-get clean \
# && echo 'Finished installing dependencies'

# ENV PORT 3000

RUN mkdir /var/idcov

EXPOSE 3000

COPY idcov.jar /app/
COPY workflow /app/
COPY refs /app/
# Change working directory
WORKDIR "/app

CMD ["java", "-jar", "idcov.jar"]
