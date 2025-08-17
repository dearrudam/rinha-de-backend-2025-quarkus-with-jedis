<p align="center">
    <img src="https://quarkus.io/assets/images/brand/quarkus_logo_vertical_450px_default.png" alt="Logo" width="400"/>
</p>

<h1 align="center">Rinha de Backend 2025 - Submissão 01 - Maximillian Arruda</h1>


<div align="center">

  <img src="https://img.shields.io/badge/java-21-blue" alt="Java version" />

<!-- ci -->

  <a href="https://github.com/dearrudam/rinha-de-backend-2025-quarkus-with-jedis/actions/workflows/ci.yml">
    <img src="https://github.com/dearrudam/rinha-de-backend-2025-quarkus-with-jedis/actions/workflows/ci.yml/badge.svg" alt="ci" />
  </a>

<!-- cd -->

  <a href="https://github.com/dearrudam/rinha-de-backend-2025-quarkus-with-jedis/actions/workflows/cd.yml">
    <img src="https://github.com/dearrudam/rinha-de-backend-2025-quarkus-with-jedis/actions/workflows/cd.yml/badge.svg" alt="docker builds" />
  </a>

</div>


### O Desafio

O objetivo é criar um serviço de backend que atue como intermediário de pagamento para dois processadores de pagamento externos.

Esses processadores têm taxas diferentes e estão sujeitos a instabilidade. Com isso, o backend deve implementar uma estratégia para maximizar o lucro, escolhendo o melhor processador para cada transação e, ao mesmo tempo, fornecer um resumo consistente das operações.

Mais informações sobre o desafio podem ser encontradas no repositório oficial da [Rinha de Backend 2025](https://github.com/zanfranceschi/rinha-de-backend-2025).

### Sobre essa implementação

Essa implementação utiliza o framework Quarkus, que é otimizado para Java e oferece suporte a desenvolvimento reativo, tornando-o ideal para aplicações de alta performance. 

Como o intuito desse desafio foi de aprender e praticar o uso do Quarkus, essa implementação não utiliza apenas o modo reativo que o framework’ oferece, mas também utiliza **Virtual Threads** nos processamento, permitindo que a implementação siga um padrão imperativo, o que é mais natural para programadores Java, o que facilita na leitura e a execução de debug do código caso necessário.

### Tecnologias Utilizadas

- **Language:** Java
- **Framework:** Quarkus
- **Database/Queue:** Redis
- **Load Balancer:** Nginx

### Diagrama de Arquitetura

TBD

### Resultados de desempenho
Se você quiser ver o desempenho desta implementação, verifique o arquivo [perf.md](perf.md).


### Pré-requisitos

- Java 21
- Maven 3.9.5
- Docker
- k6

### Executando o projeto

### Em modo de desenvolvimento

1. Execute os processadores de pagamento que serão utilizados pelo backend:

```bash
docker compose -f rinha-de-backend-2025/payment-processors/docker-compose.yml up -d
```

2. Execute o backend:

```bash
mvn quarkus:dev
```

3. Execute o script K6 de testes fornecido pela rinha de backend:

```bash
cd rinha-de-backend-2025/rinha-test 
k6 run rinha.js
```

Mais informações sobre a execução dos scripts de teste podem ser encontradas no repositório oficial da [Rinha de Backend 2025](https://github.com/zanfranceschi/rinha-de-backend-2025).

### Em modo de produção

1. Compile o projeto em modo nativo:

```bash
mvn clean package -Pnative
```

2. Execute o backend através do docker-compose.yml localizado na raiz do projeto. Ele ira realizar o build da imagem e subir todo o ambiente necessário para o backend:

```bash
docker compose up -d
```

3. Agora, basta executar o script K6 de testes fornecido pela rinha de backend:

```bash
cd rinha-de-backend-2025/rinha-test 
k6 run rinha.js
```

Mais informações sobre a execução dos scripts de teste podem ser encontradas no repositório oficial da [Rinha de Backend 2025](https://github.com/zanfranceschi/rinha-de-backend-2025).

### Agradecimentos

Gostaria de agradecer aos seguintes amigos desenvolvedores que me ajudaram a construir essa implementação:

- **Josimar Silva**: 
  
  Grande amigo da [ConfrariaDev](https://confrariadev.com/cdv/)! Muito obrigado por compartilhar uma versão do challenge em Rust, aprendi muito com as automações que vc implementou em seu desafio! Valeu!

  - Submissão do desafio: https://github.com/josimar-silva/rinha-de-backend-2025
    - Social media:
      - https://josimar-silva.com
      - https://www.linkedin.com/in/josimar-silvx/
      - https://github.com/josimar-silva

- **Jonael Lemos**
  
  Outro grande amigo da [ConfrariaDev](https://confrariadev.com/cdv/)! Muito obrigado por compartilhar e contribuir com o desenvolvimento do desafio, foi uma confusão de repos no github, mas no final achei melhor quebrar em repos diferentes. Obrigado!

    - Social media:
      - https://github.com/consultorjonaellemos