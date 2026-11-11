# Node.js runner. No npm installs happen at judge time.
FROM node:22-slim
LABEL org.codesentinel.purpose="sandbox-runner" org.codesentinel.languages="javascript"
WORKDIR /work
