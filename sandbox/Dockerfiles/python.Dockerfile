# Python 3 runner. No pip installs happen at judge time.
FROM python:3.12-slim
LABEL org.codesentinel.purpose="sandbox-runner" org.codesentinel.languages="python"
ENV PYTHONDONTWRITEBYTECODE=1
WORKDIR /work
