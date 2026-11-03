# Abrir Academia Jauja desde Docker Desktop

## Uso diario

1. Abre Docker Desktop y espera a que el motor esté iniciado.
2. En **Containers**, busca el grupo **academia-jauja**.
3. Pulsa **Start** (triángulo) en el grupo completo.
4. Abre http://localhost:3000 en el navegador. También puedes usar el enlace del puerto 3000 en el contenedor frontend.

No necesitas abrir terminales de Maven ni npm. Para cerrar la aplicación, pulsa Stop en el grupo. MySQL, los archivos subidos y los modelos de Ollama se conservan en volúmenes de Docker. No elimines los volúmenes si quieres conservar esos datos.

Los servicios tienen `restart: unless-stopped`: Docker los recupera al arrancar el motor si no los habías detenido manualmente. Si pulsaste Stop, vuelve a pulsar Start.

## Instalación o actualización desde la terminal de Visual Studio Code

Abre la carpeta `C:\Users\HOME\Downloads\Academia_Jauja_MySQL\academia-jauja` y ejecuta una sola vez:

```powershell
docker compose up --build -d
```

Ese comando crea/actualiza el grupo visible en Docker Desktop. En una instalación nueva de Ollama, instala también los modelos:

```powershell
docker compose exec ollama ollama pull qwen2.5:3b
docker compose exec ollama ollama pull qwen3-vl:2b
```

La base de Docker es independiente de un MySQL instalado directamente en Windows. Los datos de una base anterior de Windows requieren una migración para aparecer aquí.

## Comprobaciones

```powershell
docker compose ps
docker compose logs --tail 60 backend
```

Si cambia el código, reconstruye con `docker compose up --build -d`. Pulsar Start utiliza la última imagen ya construida.
