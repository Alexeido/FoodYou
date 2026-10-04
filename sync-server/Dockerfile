# Servidor de sincronización de Food You. Imagen ligera, vale para Raspberry Pi (arm64).
FROM python:3.13-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app

# Los diarios viven en un volumen, no en la imagen.
ENV DB_PATH=/data/sync.db
VOLUME ["/data"]
EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
