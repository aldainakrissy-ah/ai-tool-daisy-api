# AI Tool Daisy API

A Spring Boot application for AI-powered healthcare data analysis with dynamic questionnaire engine using OpenAI integration.

## Features

- **AI Analysis Engine**: Analyze medical text and PDF documents using OpenAI
- **Dynamic Questionnaire Engine**: Load, manage, and respond to healthcare questionnaires
- **Multi-language Support**: Support for English and Dutch questionnaires
- **PDF Processing**: Extract and analyze content from PDF documents
- **PostgreSQL Integration**: Persistent storage for questionnaires and responses

## Prerequisites

- Java 21
- Docker
- Docker Compose
- PostgreSQL

## Getting Started

### Running with Docker

1. Clone the repository:
```bash
git clone https://github.com/yourusername/ai-tool-daisy-api.git
cd ai-tool-daisy-api
```

2. Build and run the containers:
```bash
docker-compose up --build
```

The application will be available at `http://localhost:8080`

### Running Locally

1. Start PostgreSQL:
```bash
docker run -d \
  --name postgres \
  -e POSTGRES_DB=DAISY_DB \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=daisy \
  -p 5433:5432 \
  postgres:latest
```

2. Run the application:
```bash
./gradlew bootRun
```

## API Endpoints

### Health Check
- `GET /api/v1/health` - Application health status
- `GET /api/v1/endpoints` - List of all available endpoints

### AI Analysis
- `POST /api/v1/analyze/text` - Analyze medical text using AI
- `POST /api/v1/analyze/pdf` - Analyze medical PDF documents

### Questionnaire Management
- `GET /api/v1/questionnaires` - Get all active questionnaires
- `GET /api/v1/questionnaires/{id}` - Get specific questionnaire
- `POST /api/v1/questionnaires` - Create new questionnaire
- `PUT /api/v1/questionnaires/{id}` - Update questionnaire
- `DELETE /api/v1/questionnaires/{id}` - Delete questionnaire
- `POST /api/v1/questionnaires/load/{fileName}` - Load questionnaire from JSON
- `POST /api/v1/questionnaires/load-all` - Load all questionnaires

### Questionnaire Responses
- `POST /api/v1/questionnaire-responses/start` - Start questionnaire session
- `POST /api/v1/questionnaire-responses/{sessionId}/responses` - Save responses
- `POST /api/v1/questionnaire-responses/{sessionId}/complete` - Complete questionnaire
- `GET /api/v1/questionnaire-responses/{sessionId}` - Get session responses
- `GET /api/v1/questionnaire-responses/user/{userId}` - Get user responses

## Available Questionnaires

The system includes pre-loaded questionnaires:

1. **GAD-7** - Generalized Anxiety Disorder assessment
2. **PHQ-9** - Patient Health Questionnaire for depression
3. **Food Diary** - 24-hour food intake tracking
4. **Health Intake** - Comprehensive health assessment (Adult/Child)
5. **Ethos Scan** - Ethical assessment tools
6. **Fatigue FSS** - Fatigue Severity Scale
7. **Pain VAS** - Visual Analog Scale for pain
8. **Sleep Quality PSQI** - Pittsburgh Sleep Quality Index
9. **Social Support** - Social support assessment
10. **Physical Activity IPAQ** - International Physical Activity Questionnaire

## Environment Variables

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/DAISY_DB
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=daisy
SPRING_AI_OPENAI_API_KEY=your-openai-api-key
```

## Project Structure

```
ai-tool-daisy-api/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/ai/tool/analysis/
│   │   │       ├── ai_tool_daisy_api/          # Main application
│   │   │       └── phase-two-api/              # Questionnaire engine
│   │   │           ├── entity/                 # JPA entities
│   │   │           ├── pojo/                   # DTOs
│   │   │           ├── repository/             # Data repositories
│   │   │           ├── service/                # Business logic
│   │   │           ├── controller/             # REST controllers
│   │   │           ├── configuration/          # Spring configuration
│   │   │           └── exception/              # Exception handling
│   │   └── resources/
│   │       ├── application.properties
│   │       └── questionnaires/                 # JSON questionnaire files
├── docker-compose.yml
├── Dockerfile
└── build.gradle
```

## Technologies

- **Backend**: Spring Boot 3.5.6, Java 21
- **Database**: PostgreSQL with JPA/Hibernate
- **AI Integration**: Spring AI with OpenAI
- **Build Tool**: Gradle
- **Containerization**: Docker & Docker Compose
- **PDF Processing**: Apache PDFBox

## Database Schema

The questionnaire engine uses the following main entities:

- `questionnaires` - Questionnaire definitions
- `questionnaire_sections` - Questionnaire sections
- `questions` - Individual questions
- `question_columns` - Table question columns
- `questionnaire_responses` - User response sessions
- `question_responses` - Individual question answers

## Usage Examples

### Starting a Questionnaire Session

```bash
curl -X POST "http://localhost:8080/api/v1/questionnaire-responses/start" \
  -H "Content-Type: application/json" \
  -d '{
    "questionnaireId": "gad-7",
    "userId": "user123",
    "languageCode": "en"
  }'
```

### Saving Question Responses

```bash
curl -X POST "http://localhost:8080/api/v1/questionnaire-responses/{sessionId}/responses" \
  -H "Content-Type: application/json" \
  -d '[
    {
      "questionId": "gad7-1",
      "answerNumber": 2
    },
    {
      "questionId": "gad7-2", 
      "answerNumber": 1
    }
  ]'
```

### Loading Custom Questionnaire

```bash
curl -X POST "http://localhost:8080/api/v1/questionnaires/load/custom-questionnaire.json"
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit changes
4. Push to the branch
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.
