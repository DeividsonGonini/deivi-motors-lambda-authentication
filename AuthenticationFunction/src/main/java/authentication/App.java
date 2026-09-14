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
            log(context, "========== NOVA REQUISICAO ==========");
            log(context, "HTTP Method: " + request.getHttpMethod());
            log(context, "Path recebido pelo API Gateway: " + request.getPath());
            log(context, "Resource recebido pelo API Gateway: " + request.getResource());
            log(context, "Path Parameters: " + request.getPathParameters());

            loadSecrets();

            String method = request.getHttpMethod();
            String path = request.getPath();
            String resource = request.getResource();

            // POST /customers
            if ("POST".equalsIgnoreCase(method)
                    && ("/customers".equals(resource) || "/customers".equals(path))) {

                log(context, "Endpoint interno chamado: createUser()");
                return createUser(request, context);
            }

            // GET /customers/{cpf}
            if ("GET".equalsIgnoreCase(method)
                    && ("/customers/{cpf}".equals(resource) || path.startsWith("/customers/"))) {

                String cpf = null;

                if (request.getPathParameters() != null) {
                    cpf = request.getPathParameters().get("cpf");
                }

                if (cpf == null && path.startsWith("/customers/")) {
                    cpf = path.substring("/customers/".length());
                }

                log(context, "Endpoint interno chamado: getUserByCpf()");
                log(context, "CPF recebido para consulta: " + cpf);

                return getUserByCpf(cpf, context);
            }

            // POST /authentications
            if ("POST".equalsIgnoreCase(method)
                    && ("/authentications".equals(resource) || "/authentications".equals(path))) {

                log(context, "Endpoint interno chamado: authenticate()");
                return authenticate(request, context);
            }

            log(context, "Endpoint nao encontrado.");
            log(context, "Method: " + method);
            log(context, "Path: " + path);
            log(context, "Resource: " + resource);

            return response(404, Map.of("error", "Endpoint não encontrado"));

        } catch (NotAuthorizedException e) {
            log(context, "Falha de autenticacao: credenciais invalidas");
            return response(401, Map.of("error", "Credenciais invalidas"));

        } catch (InvalidPasswordException e) {
            log(context, "Senha rejeitada pela politica do Cognito");
            return response(400, Map.of(
                    "error",
                    "Senha nao atende politica de seguranca do Cognito"
            ));

        } catch (UsernameExistsException e) {
            log(context, "Tentativa de cadastro de usuario ja existente");
            return response(409, Map.of("error", "Usuario ja cadastrado"));

        } catch (Exception e) {
            log(context, "Erro interno: " + e.getMessage());
            return response(500, Map.of("error", e.getMessage()));
        }
    }

    // POST /customers
    private APIGatewayProxyResponseEvent createUser(
            APIGatewayProxyRequestEvent request,
            Context context
    ) throws Exception {

        if (request.getBody() == null || request.getBody().isBlank()) {
            return response(400, Map.of("error", "Body obrigatorio"));
        }

        Map<String, String> body = objectMapper.readValue(
                request.getBody(),
                new TypeReference<Map<String, String>>() {}
        );

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

        log(context, "Iniciando cadastro de usuario. CPF: " + cpfSemMascara);

        try {
            cognitoClient.adminGetUser(AdminGetUserRequest.builder()
                    .userPoolId(USER_POOL_ID)
                    .username(cpfSemMascara)
                    .build());

            log(context, "Usuario ja cadastrado. CPF: " + cpfSemMascara);

            return response(409, Map.of("error", "Usuario ja cadastrado"));

        } catch (UserNotFoundException ignored) {
            log(context, "Usuario nao encontrado no Cognito. Prosseguindo com cadastro. CPF: "
                    + cpfSemMascara);
        }

        AdminCreateUserRequest createUserRequest = AdminCreateUserRequest.builder()
                .userPoolId(USER_POOL_ID)
                .username(cpfSemMascara)
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
                .username(cpfSemMascara)
                .password(password)
                .permanent(true)
                .build());

        log(context, "Usuario cadastrado com sucesso. CPF: " + cpfSemMascara);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("message", "Usuario cadastrado com sucesso");
        responseBody.put("email", email);
        responseBody.put("completeName", completeName);
        responseBody.put("cpf", cpfSemMascara);

        return response(201, responseBody);
    }

    // GET /customers/{cpf}
    private APIGatewayProxyResponseEvent getUserByCpf(String cpf, Context context) {

        if (cpf == null || cpf.isBlank()) {
            return response(400, Map.of("error", "CPF obrigatorio"));
        }

        String cpfSemMascara = cpf.replaceAll("\\D", "");

        if (cpfSemMascara.length() != 11) {
            log(context, "CPF invalido para consulta: " + cpf);
            return response(400, Map.of("error", "CPF invalido"));
        }

        log(context, "Consultando usuario no Cognito. CPF: " + cpfSemMascara);

        try {

            AdminGetUserResponse userResponse = cognitoClient.adminGetUser(
                    AdminGetUserRequest.builder()
                            .userPoolId(USER_POOL_ID)
                            .username(cpfSemMascara)
                            .build()
            );

            String cpfUsuario = getAttributeGetCustomer(userResponse, "custom:cpf");
            String email = getAttributeGetCustomer(userResponse, "email");
            String completeName = getAttributeGetCustomer(userResponse, "name");

            log(context, "Usuario consultado com sucesso. CPF: " + cpfUsuario);

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("cpf", cpfUsuario);
            responseBody.put("email", email);
            responseBody.put("completeName", completeName);

            return response(200, responseBody);

        } catch (UserNotFoundException e) {

            log(context, "Usuario nao encontrado. CPF: " + cpfSemMascara);

            return response(
                    404,
                    Map.of("error", "Usuario nao encontrado")
            );
        }
    }

    private String getAttributeGetCustomer(AdminGetUserResponse user,String attributeName
    ) {
        return user.userAttributes().stream()
                .filter(attribute -> attribute.name().equals(attributeName))
                .map(AttributeType::value)
                .findFirst()
                .orElse(null);
    }


    private String getAttribute(UserType user, String attributeName) {
        return user.attributes().stream()
                .filter(attribute -> attribute.name().equals(attributeName))
                .map(AttributeType::value)
                .findFirst()
                .orElse(null);
    }

    // POST /authentications
    private APIGatewayProxyResponseEvent authenticate(
            APIGatewayProxyRequestEvent request,
            Context context
    ) throws Exception {

        if (request.getBody() == null || request.getBody().isBlank()) {
            return response(400, Map.of("error", "Body obrigatorio"));
        }

        Map<String, String> body = objectMapper.readValue(
                request.getBody(),
                new TypeReference<Map<String, String>>() {}
        );

        String cpf = body.get("cpf");
        String password = body.get("password");

        if (cpf == null || cpf.isBlank()) {
            return response(400, Map.of("error", "CPF obrigatorio"));
        }

        if (password == null || password.isBlank()) {
            return response(400, Map.of("error", "Senha obrigatoria"));
        }

        String cpfSemMascara = cpf.replaceAll("\\D", "");

        if (cpfSemMascara.length() != 11) {
            return response(400, Map.of("error", "CPF invalido"));
        }

        log(context, "Iniciando autenticacao do usuario. CPF: " + cpfSemMascara);

        try {
            cognitoClient.adminGetUser(AdminGetUserRequest.builder()
                    .userPoolId(USER_POOL_ID)
                    .username(cpfSemMascara)
                    .build());

        } catch (UserNotFoundException e) {
            log(context, "Usuario nao encontrado durante autenticacao. CPF: "
                    + cpfSemMascara);

            return response(401, Map.of("error", "Credenciais invalidas"));
        }

        InitiateAuthResponse authResponse = cognitoClient.initiateAuth(
                InitiateAuthRequest.builder()
                        .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                        .clientId(CLIENT_ID)
                        .authParameters(Map.of(
                                "USERNAME", cpfSemMascara,
                                "PASSWORD", password
                        ))
                        .build());

        AuthenticationResultType authResult = authResponse.authenticationResult();

        log(context, "Usuario autenticado com sucesso. CPF: " + cpfSemMascara);

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

    // Secrets Manager
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

        GetSecretValueResponse secretResponse =
                secretsClient.getSecretValue(request);

        Map<String, String> secrets = objectMapper.readValue(
                secretResponse.secretString(),
                new TypeReference<Map<String, String>>() {}
        );

        USER_POOL_ID = secrets.get("USER_POOL_ID");
        CLIENT_ID = secrets.get("CLIENT_ID");

        if (USER_POOL_ID == null || USER_POOL_ID.isBlank()) {
            throw new RuntimeException("USER_POOL_ID not found in secret");
        }

        if (CLIENT_ID == null || CLIENT_ID.isBlank()) {
            throw new RuntimeException("CLIENT_ID not found in secret");
        }

        log(null, "Secrets carregados com sucesso.");
    }

    // Logs
    private void log(Context context, String message) {
        if (context != null) {
            context.getLogger().log("[AUTHENTICATION-LAMBDA] " + message + "\n");
        } else {
            System.out.println("[AUTHENTICATION-LAMBDA] " + message);
        }
    }

    // Response
    private APIGatewayProxyResponseEvent response(int status,Map<String, Object> body) {

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
