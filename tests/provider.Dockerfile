FROM python:3.12-slim
WORKDIR /app
COPY tests/mock_provider.py .
RUN mkdir /data && useradd --uid 10001 worker && chown worker /data
USER worker
CMD ["python","-u","mock_provider.py"]
