# C / C++ runner. Pinned compiler version; no network is available when this image runs (docker run --network none).
FROM gcc:13
LABEL org.codesentinel.purpose="sandbox-runner" org.codesentinel.languages="c,cpp"
# Runs as an unprivileged uid chosen by the judge (--user 65534). Nothing is installed at judge time.
WORKDIR /work
