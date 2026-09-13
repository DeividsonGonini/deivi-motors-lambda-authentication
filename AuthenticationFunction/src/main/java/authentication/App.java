package authentication;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private String USER_POOL_ID;
    private String CLIENT_ID;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CognitoIdentityProviderClient cognitoClient;
    private final SecretsManagerClient secretsClient;

    public App() {
        this(CognitoIdentityProviderClient.create(), SecretsManagerClient.create());
    }

    public App(CognitoIdentityProviderClient cognitoClient, SecretsManagerClient secretsClient) {
        this.cognitoClient = cognitoClient;
        this.secretsClient = secretsClient;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            loadSecrets();

            String path = request.getPath();
            String method = request.getHttpMethod();

            // POST /user
            if ("POST".equalsIgnoreCase(method) && "/customers".equals(path)) {
                return createUser(request);
            }

            // GET /user/{cpf}
            if ("GET".equalsIgnoreCase(method) && path.startsWith("/customers/")) {
                String cpf = path.substring("/customers/".length());
                return getUserByCpf(cpf);
            }

            // POST /authentication
            if ("POST".equalsIgnoreCase(method) && "/authentications".equals(path)) {
                return authenticate(request);
            }

            return response(404, Map.of("error", "Endpoint não encontrado"));

        } catch (NotAuthorizedException e) {
            return response(401, Map.of("error", "Credenciais invalidas"));

        } catch (InvalidPasswordException e) {
            return response(400, Map.of("error", "Senha nao atende politica de seguranca do Cognito"));

        } catch (UsernameExistsException e) {
            return response(409, Map.of("error", "Usuario ja cadastrado"));

        } catch (Exception e) {
            return response(500, Map.of("error", e.getMessage()));
        }
    }

    // ============================================================
    // POST /user
    // ============================================================

    private APIGatewayProxyResponseEvent createUser(APIGatewayProxyRequestEvent request) throws Exception {

        if (request.getBody() == null || request.getBody().isBlank()) {
            return response(400, Map.of("error", "Body obrigatorio"));
        }

        Map<String, String> body = objectMapper.readValue(
                request.getBody(), new TypeReference<Map<String, String>>() {
                });

        String email = body.get("email");
        String password = body.get("password");
        String cpf = body.get("cpf");
        String completeName = body.get("completeName");

        if (email == null || email.isBlank()) {
            return response(400, Map.of("error", "Email obrigatorio"));
        }

        if (password == null || password.isBlank()) {
            return response(400, Map.of("error", "Senha obrigatoria"));
        }

        if (cpf == null || cpf.isBlank()) {
            return response(400, Map.of("error", "CPF obrigatorio"));
        }

        if (completeName == null || completeName.isBlank()) {
            return response(400, Map.of("error", "Nome completo obrigatorio"));
        }

        String cpfSemMascara = cpf.replaceAll("\\D", "");

        if (cpfSemMascara.length() != 11) {
            return response(400, Map.of("error", "CPF invalido"));
        }

        try {
            cognitoClient.adminGetUser(AdminGetUserRequest.builder()
                    .userPoolId(USER_POOL_ID)
                    .username(cpf)
                    .build());

            return response(409, Map.of("error", "Usuario ja cadastrado"));

        } catch (UserNotFoundException ignored) {
        }

        AdminCreateUserRequest createUserRequest = AdminCreateUserRequest.builder()
                .userPoolId(USER_POOL_ID)
                .username(cpf)
                .userAttributes(List.of(
                        AttributeType.builder().name("email").value(email).build(),
                        AttributeType.builder().name("email_verified").value("true").build(),
                        AttributeType.builder().name("name").value(completeName).build(),
                        AttributeType.builder().name("custom:cpf").value(cpfSemMascara).build()
                ))
                .messageAction("SUPPRESS")
                .build();

        cognitoClient.adminCreateUser(createUserRequest);

        cognitoClient.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                .userPoolId(USER_POOL_ID)
                .username(cpf)
                .password(password)
                .permanent(true)
                .build());

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("message", "Usuario cadastrado com sucesso");
        responseBody.put("email", email);
        responseBody.put("completeName", completeName);
        responseBody.put("cpf", cpfSemMascara);

        return response(201, responseBody);
    }

    // ============================================================
    // GET /user/{cpf}
    // ============================================================

    private APIGatewayProxyResponseEvent getUserByCpf(String cpf) {

        if (cpf == null || cpf.isBlank()) {
            return response(400, Map.of("error", "CPF obrigatorio"));
        }

        String cpfSemMascara = cpf.replaceAll("\\D", "");

        if (cpfSemMascara.length() != 11) {
            return response(400, Map.of("error", "CPF invalido"));
        }

        ListUsersResponse usersResponse = cognitoClient.listUsers(
                ListUsersRequest.builder()
                        .userPoolId(USER_POOL_ID)
                        .filter("custom:cpf = \"" + cpfSemMascara + "\"")
                        .limit(1)
                        .build()
        );

        if (usersResponse.users().isEmpty()) {
            return response(404, Map.of("error", "Usuario nao encontrado"));
        }

        UserType user = usersResponse.users().get(0);

        String email = getAttribute(user, "email");
        String completeName = getAttribute(user, "name");
        String cpfUsuario = getAttribute(user, "custom:cpf");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("cpf", cpfUsuario);
        responseBody.put("email", email);
        responseBody.put("completeName", completeName);

        return response(200, responseBody);
    }

    private String getAttribute(UserType user, String attributeName) {
        return user.attributes().stream()
                .filter(attribute -> attribute.name().equals(attributeName))
                .map(AttributeType::value)
                .findFirst()
                .orElse(null);
    }

    // ============================================================
    // POST /authentication
    // ============================================================

    private APIGatewayProxyResponseEvent authenticate(APIGatewayProxyRequestEvent request) throws Exception {

        if (request.getBody() == null || request.getBody().isBlank()) {
            return response(400, Map.of("error", "Body obrigatorio"));
        }

        Map<String, String> body = objectMapper.readValue(
                request.getBody(), new TypeReference<Map<String, String>>() {
                });

        String cpf = body.get("custom:cpf");
        String password = body.get("password");

        if (cpf == null || cpf.isBlank()) {
            return response(400, Map.of("error", "CPF obrigatorio"));
        }

        if (password == null || password.isBlank()) {
            return response(400, Map.of("error", "Senha obrigatoria"));
        }

        try {
            cognitoClient.adminGetUser(AdminGetUserRequest.builder()
                    .userPoolId(USER_POOL_ID)
                    .username(cpf)
                    .build());

        } catch (UserNotFoundException e) {
            return response(401, Map.of("error", "Credenciais invalidas"));
        }

        InitiateAuthResponse authResponse = cognitoClient.initiateAuth(
                InitiateAuthRequest.builder()
                        .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                        .clientId(CLIENT_ID)
                        .authParameters(Map.of("USERNAME", cpf, "PASSWORD", password))
                        .build());

        AuthenticationResultType authResult = authResponse.authenticationResult();

        Map<String, String> authentication = new HashMap<>();
        authentication.put("TokenType", authResult.tokenType());
        authentication.put("AccessToken", authResult.accessToken());
        authentication.put("IdToken", authResult.idToken());
        authentication.put("RefreshToken", authResult.refreshToken());

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("message", "Login realizado com sucesso");
        responseBody.put("authentication", authentication);

        return response(200, responseBody);
    }

    // ============================================================
    // Secrets Manager
    // ============================================================

    private void loadSecrets() throws Exception {
        String secretName = System.getenv("SECRET_NAME");

        if (secretName == null || secretName.isBlank()) {
            secretName = System.getProperty("SECRET_NAME");
        }

        if (secretName == null || secretName.isBlank()) {
            throw new RuntimeException("SECRET_NAME environment variable not set");
        }

        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();

        GetSecretValueResponse secretResponse = secretsClient.getSecretValue(request);

        Map<String, String> secrets = objectMapper.readValue(
                secretResponse.secretString(),
                new TypeReference<Map<String, String>>() {
                });

        USER_POOL_ID = secrets.get("USER_POOL_ID");
        CLIENT_ID = secrets.get("CLIENT_ID");

        if (USER_POOL_ID == null || USER_POOL_ID.isBlank()) {
            throw new RuntimeException("USER_POOL_ID not found in secret");
        }

        if (CLIENT_ID == null || CLIENT_ID.isBlank()) {
            throw new RuntimeException("CLIENT_ID not found in secret");
        }
    }

    // ============================================================
    // Response
    // ============================================================

    private APIGatewayProxyResponseEvent response(int status, Map<String, Object> body) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(status)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(objectMapper.writeValueAsString(body));

        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Falha ao gerar resposta\"}");
        }
    }
}