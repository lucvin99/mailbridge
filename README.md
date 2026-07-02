# MailBridge

Plataforma web para gestão e envio de campanhas de email personalizadas, desenvolvida em Java com Spark Java Framework.

## Tecnologias

- Java 17
- Spark Java (servidor HTTP embebido)
- MariaDB (base de dados)
- Jakarta Mail (envio SMTP)
- Apache POI (importação de ficheiros Excel)
- Chart.js

## Pré-requisitos

- [Java 17 JDK]([https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
- [MariaDB](https://mariadb.org/download/) (versão 10.6 ou superior)

## Instalação

### 1. Base de dados

Executar o ficheiro:

```text
database/install.sql
```

#### Utilizando o HeidiSQL

1. Abrir o HeidiSQL.
2. Ligar à instância MariaDB.
3. Selecionar **File → Run SQL file...**
4. Escolher o ficheiro `database/install.sql`.
5. Executar o script.

#### Utilizando a linha de comandos

```bash
mysql -u root -p < database/install.sql
```

### 2. Configuração

Copiar o ficheiro `db.properties.example` para `db.properties` e configurar os dados da base de dados e do servidor SMTP.

Exemplo:

```properties
db.url=jdbc:mariadb://localhost:3306/mailbridge
db.user=root
db.password=A_TUA_PASSWORD

mail.host=smtp.gmail.com
mail.port=587
mail.user=O_TEU_EMAIL@gmail.com
mail.password=A_TUA_APP_PASSWORD
mail.test_mode=true
```

> Define `mail.test_mode=true` para testar os envios através do Mailpit, sem enviar emails reais.

## 3. Execução da aplicação

Após a configuração da base de dados e do ficheiro `db.properties`, existem duas formas de iniciar a aplicação.

### Método recomendado (Windows)

Executar o ficheiro:

```text
iniciar.bat
```

O script irá iniciar automaticamente a aplicação através do ficheiro JAR executável.

### Método alternativo (Linux e macOS)

Abrir um terminal na pasta do projeto e executar:

```bash
java -jar target/mailbridge-1.0-SNAPSHOT-shaded.jar
```

Após o arranque, a aplicação ficará disponível em:

```text
http://localhost:4567
```

## Compatibilidade

A aplicação foi desenvolvida em Java 17 e pode ser executada em qualquer sistema operativo compatível com Java.

- Windows: suportado através do ficheiro `iniciar.bat`.
- Linux: suportado através do comando `java -jar`.
- macOS: suportado através do comando `java -jar`.

O ficheiro `iniciar.bat` é apenas uma conveniência para utilizadores Windows. Os utilizadores Linux e macOS não necessitam de qualquer IDE ou do Maven, bastando executar o ficheiro JAR através da linha de comandos.

## Estrutura do projeto
