FROM nextflow/nextflow:20.07.1

WORKDIR "/app"


RUN mkdir /idcov
ENV BASEPATH /idcov

COPY idcov_latest.jar /app/idcov.jar

ENV WORKFLOW_PATH /app/workflow
ENV REFS_PATH /refs

ENV PORT 8071

EXPOSE 8071

CMD ["java", "-jar", "idcov.jar"]
