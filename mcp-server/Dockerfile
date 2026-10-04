# Servidor MCP de Food You. Imagen ligera, vale para Raspberry Pi (arm64).
FROM python:3.13-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY foodyou_mcp ./foodyou_mcp

# El historial de cambios (para deshacer) vive en un volumen.
ENV DATA_DIR=/data
VOLUME ["/data"]
EXPOSE 8000

CMD ["uvicorn", "foodyou_mcp.server:app", "--factory", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
