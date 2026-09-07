package authentication;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
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

    // construtor padrão (AWS Lambda usa este)
    public App() {
        this(
                CognitoIdentityProviderClient.create(),
                SecretsManagerClient.create()
        );
    }

    // construtor para testes
    public App(CognitoIdentityProviderClient cognitoClient,
               SecretsManagerClient secretsClient) {
        this.cognitoClient = cognitoClient;
        this.secretsClient = secretsClient;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {

        try {

            // ---------- Validação do body ----------
            if (request.getBody() == null || request.getBody().isBlank()) {
                return response(400, Map.of("error", "Body obrigatório"));
            }

            Map<String, String> body = objectMapper.readValue(request.getBody(), Map.class);

            String email = body.get("email");
            String password = body.get("password");
            String cpf = body.get("cpf");
            String nomeCompleto = body.get("nomeCompleto");

            // ---------- Validação dos campos ----------
            if (email == null || email.isBlank()) {
                return response(400, Map.of("error", "Email obrigatório"));
            }

            if (password == null || password.isBlank()) {
                return response(400, Map.of("error", "Senha obrigatória"));
            }



            // ---------- Carrega secrets ----------
            loadSecrets();

            // ---------- Verifica se usuário existe ----------
            Map<String, Object> responseBody = new HashMap<>();

            boolean userExists = true;

            try {

                cognitoClient.adminGetUser(AdminGetUserRequest.builder()
                        .userPoolId(USER_POOL_ID)
                        .username(email)
                        .build());

            } catch (UserNotFoundException e) {

                userExists = false;
            }

            // ---------- Cadastro ----------
            if (!userExists) {

                // CPF obrigatório no cadastro
                if (cpf == null || cpf.isBlank()) {
                    return response(400, Map.of("error", "CPF obrigatório"));
                }

                // Nome obrigatório no cadastro
                if (nomeCompleto == null || nomeCompleto.isBlank()) {
                    return response(400, Map.of("error", "Nome completo obrigatório"));
                }

                // ---------- Validação básica do CPF ----------
                String cpfSemMascara = cpf.replaceAll("\\D", "");

                if (cpfSemMascara.length() != 11) {
                    return response(400, Map.of("error", "CPF inválido"));
                }

                // ---------- Cria Usuário ----------
                AdminCreateUserRequest createUserRequest = AdminCreateUserRequest.builder()
                        .userPoolId(USER_POOL_ID)
                        .username(email)
                        .userAttributes(List.of(
                                AttributeType.builder().name("email").value(email).build(),
                                AttributeType.builder().name("email_verified").value("true").build(),
                                AttributeType.builder() .name("name") .value(nomeCompleto) .build(),
                                AttributeType.builder() .name("custom:cpf") .value(cpfSemMascara) .build()
                        ))
                        .messageAction("SUPPRESS")
                        .build();

                cognitoClient.adminCreateUser(createUserRequest);

                // ---------- Define a senha como permanente ----------
                cognitoClient.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                        .userPoolId(USER_POOL_ID)
                        .username(email)
                        .password(password)
                        .permanent(true)
                        .build()
                );

                responseBody.put("message", "Usuario cadastrado com sucesso");
                responseBody.put("email", email);
//                responseBody.put("password", password);
                responseBody.put("nomeCompleto", nomeCompleto);
                responseBody.put("cpf", cpfSemMascara);

                return response(201, responseBody);
            }

            // ---------- Login----------
            InitiateAuthResponse authResponse = cognitoClient.initiateAuth(
                    InitiateAuthRequest.builder()
                            .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                            .clientId(CLIENT_ID)
                            .authParameters(Map.of(
                                    "USERNAME", email,
                                    "PASSWORD", password
                            ))
                            .build()
            );

            AuthenticationResultType authResult = authResponse.authenticationResult();

            Map<String, String> authMap = new HashMap<>();
            authMap.put("TokenType", authResult.tokenType());
            authMap.put("AccessToken", authResult.accessToken());
            authMap.put("IdToken", authResult.idToken());
            authMap.put("RefreshToken", authResult.refreshToken());

            responseBody.put("message", "Login realizado com sucesso");
            responseBody.put("authentication", authMap);

            return response(200, responseBody);

        } catch (NotAuthorizedException e) {

            return response(401, Map.of("error", "Credenciais invalidas"));

        } catch (InvalidPasswordException e) {

            return response(400, Map.of("error", "Senha nao atende politica de seguranca do Cognito"));

        } catch (Exception e) {

            return response(500, Map.of("error", e.getMessage()));
        }
    }

    // ---------- Carrega secrets ----------
    private void loadSecrets() throws Exception {

        String secretName = System.getenv("SECRET_NAME");

        if (secretName == null) {
            secretName = System.getProperty("SECRET_NAME");
        }

        if (secretName == null || secretName.isBlank()) {
            throw new RuntimeException("SECRET_NAME environment variable not set");
        }

        GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();

        GetSecretValueResponse getSecretValueResponse =
                secretsClient.getSecretValue(getSecretValueRequest);

        String secretJson = getSecretValueResponse.secretString();

        Map<String, String> secrets = objectMapper.readValue(secretJson, Map.class);

        USER_POOL_ID = secrets.get("USER_POOL_ID");
        CLIENT_ID = secrets.get("CLIENT_ID");
    }

    // ---------- Response helper ----------
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
