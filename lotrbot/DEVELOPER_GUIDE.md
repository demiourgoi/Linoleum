# Developer guide

## Ollama investigation

### Ollama setup 

Install [ollama](https://ollama.com/download) and download the required model:

```bash
# https://ollama.com/library/qwen3 very low resource usage
ollama pull qwen3:0.6b
ollama run qwen3:0.6b
# Check it works using the [REST API](https://github.com/ollama/ollama?tab=readme-ov-file#rest-api) 
curl http://localhost:11434/api/generate -d '{
  "model": "qwen3:0.6b",
  "prompt":"I wanna know what love is"
}'
ollama stop qwen3:0.6b
```

### Experience

Trying a very lightweight model, specifically [qwen3:0.6b](https://ollama.com/library/qwen3), on a NVIDIA GeForce GTX 1650 4 GB on a laptop with Intel(R) Core(TM) i7-10750H CPU @ 2.60GH and 12 logical threads, the model is really slow, specially at startup. 

To run with ollama, use `curl http://localhost:11434/api/tags &> /dev/null || ollama serve` to ensure the Ollama server is running, before running `make run`