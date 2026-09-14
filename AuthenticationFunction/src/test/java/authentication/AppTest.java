package authentication;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppTest {

    @Mock
    private CognitoIdentityProviderClient cognitoClient;

    @Mock
    private SecretsManagerClient secretsClient;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private App app;

    private AutoCloseable mocks;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {

        mocks = MockitoAnnotations.openMocks(this);

        when(context.getLogger()).thenReturn(logger);

        GetSecretValueResponse secretResponse =
                GetSecretValueResponse.builder()
                        .secretString("""
                                {
                                  "USER_POOL_ID": "us-east-1_test",
                                  "CLIENT_ID": "client-test"
                                }
                                """)
                        .build();

        /*
         * IMPORTANTE:
         *
         * getSecretValue possui duas sobrecargas no AWS SDK:
         *
         * getSecretValue(GetSecretValueRequest)
         *
         * e
         *
         * getSecretValue(Consumer<GetSecretValueRequest.Builder>)
         *
         * Por isso usamos any(GetSecretValueRequest.class)
         * para evitar ambiguidade.
         */
        when(secretsClient.getSecretValue(
                any(GetSecretValueRequest.class)
        )).thenReturn(secretResponse);

        System.setProperty(
                "SECRET_NAME",
                "cognito_auth_configuration"
        );

        app = new App(cognitoClient, secretsClient);
    }

    @AfterEach
    void tearDown() throws Exception {

        System.clearProperty("SECRET_NAME");

        if (mocks != null) {
            mocks.close();
        }
    }

    // CREATE USER
    @Test
    void shouldCreateUserSuccessfully() throws Exception {

        String body = """
                {
                  "email": "cliente@email.com",
                  "password": "Senha@123",
                  "cpf": "123.456.789-09",
                  "completeName": "Cliente Teste"
                }
                """;

        APIGatewayProxyRequestEvent request =
                request(
                        "POST",
                        "/customers",
                        "/customers",
                        body
                );

        when(cognitoClient.adminGetUser(
                any(AdminGetUserRequest.class)
        )).thenThrow(
                UserNotFoundException.builder().build()
        );

        when(cognitoClient.adminCreateUser(
                any(AdminCreateUserRequest.class)
        )).thenReturn(
                AdminCreateUserResponse.builder().build()
        );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(201, response.getStatusCode());

        assertNotNull(response.getBody());

        verify(cognitoClient).adminGetUser(
                any(AdminGetUserRequest.class)
        );

        verify(cognitoClient).adminCreateUser(
                any(AdminCreateUserRequest.class)
        );

        verify(cognitoClient).adminSetUserPassword(
                any(AdminSetUserPasswordRequest.class)
        );
    }

    @Test
    void shouldReturnBadRequestWhenCreateUserBodyIsEmpty() {

        APIGatewayProxyRequestEvent request =
                request(
                        "POST",
                        "/customers",
                        "/customers",
                        ""
                );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(400, response.getStatusCode());

        assertEquals(
                "{\"error\":\"Body obrigatorio\"}",
                response.getBody()
        );
    }

    @Test
    void shouldReturnBadRequestWhenEmailIsMissing() {

        String body = """
                {
                  "password": "Senha@123",
                  "cpf": "12345678909",
                  "completeName": "Cliente Teste"
                }
                """;

        APIGatewayProxyRequestEvent request =
                request(
                        "POST",
                        "/customers",
                        "/customers",
                        body
                );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(400, response.getStatusCode());

        assertEquals(
                "{\"error\":\"Email obrigatorio\"}",
                response.getBody()
        );
    }

    @Test
    void shouldReturnBadRequestWhenPasswordIsMissing() {

        String body = """
                {
                  "email": "cliente@email.com",
                  "cpf": "12345678909",
                  "completeName": "Cliente Teste"
                }
                """;

        APIGatewayProxyRequestEvent request =
                request(
                        "POST",
                        "/customers",
                        "/customers",
                        body
                );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(400, response.getStatusCode());

        assertEquals(
                "{\"error\":\"Senha obrigatoria\"}",
                response.getBody()
        );
    }

    @Test
    void shouldReturnBadRequestWhenCpfIsMissing() {

        String body = """
                {
                  "email": "cliente@email.com",
                  "password": "Senha@123",
                  "completeName": "Cliente Teste"
                }
                """;

        APIGatewayProxyRequestEvent request =
                request(
                        "POST",
                        "/customers",
                        "/customers",
                        body
                );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(400, response.getStatusCode());

        assertEquals(
                "{\"error\":\"CPF obrigatorio\"}",
                response.getBody()
        );
    }

    @Test
    void shouldReturnBadRequestWhenCompleteNameIsMissing() {

        String body = """
                {
                  "email": "cliente@email.com",
                  "password": "Senha@123",
                  "cpf": "12345678909"
                }
                """;

        APIGatewayProxyRequestEvent request =
                request(
                        "POST",
                        "/customers",
                        "/customers",
                        body
                );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(400, response.getStatusCode());

        assertEquals(
                "{\"error\":\"Nome completo obrigatorio\"}",
                response.getBody()
        );
    }

    @Test
    void shouldReturnBadRequestWhenCreateUserCpfIsInvalid() {

        String body = """
                {
                  "email": "cliente@email.com",
                  "password": "Senha@123",
                  "cpf": "123456",
                  "completeName": "Cliente Teste"
                }
                """;

        APIGatewayProxyRequestEvent request =
                request(
                        "POST",
                        "/customers",
                        "/customers",
                        body
                );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(400, response.getStatusCode());

        assertEquals(
                "{\"error\":\"CPF invalido\"}",
                response.getBody()
        );
    }

    @Test
    void shouldReturnConflictWhenUserAlreadyExists() {

        String body = """
                {
                  "email": "cliente@email.com",
                  "password": "Senha@123",
                  "cpf": "12345678909",
                  "completeName": "Cliente Teste"
                }
                """;

        APIGatewayProxyRequestEvent request =
                request(
                        "POST",
                        "/customers",
                        "/customers",
                        body
                );

        when(cognitoClient.adminGetUser(
                any(AdminGetUserRequest.class)
        )).thenReturn(
                AdminGetUserResponse.builder().build()
        );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(409, response.getStatusCode());

        assertEquals(
                "{\"error\":\"Usuario ja cadastrado\"}",
                response.getBody()
        );
    }

    // GET USER BY CPF
    @Test
    void shouldGetUserByCpfSuccessfully() {

        APIGatewayProxyRequestEvent request =
                request(
                        "GET",
                        "/customers/12345678909",
                        "/customers/{cpf}",
                        null
                );

        AdminGetUserResponse userResponse =
                AdminGetUserResponse.builder()
                        .userAttributes(
                                AttributeType.builder()
                                        .name("custom:cpf")
                                        .value("12345678909")
                                        .build(),

                                AttributeType.builder()
                                        .name("email")
                                        .value("cliente@email.com")
                                        .build(),

                                AttributeType.builder()
                                        .name("name")
                                        .value("Cliente Teste")
                                        .build()
                        )
                        .build();

        when(cognitoClient.adminGetUser(
                any(AdminGetUserRequest.class)
        )).thenReturn(userResponse);

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(200, response.getStatusCode());

        assertNotNull(response.getBody());

        assertEquals(
                true,
                response.getBody().contains("12345678909")
        );

        assertEquals(
                true,
                response.getBody().contains("cliente@email.com")
        );

        assertEquals(
                true,
                response.getBody().contains("Cliente Teste")
        );
    }

    @Test
    void shouldGetUserByCpfUsingPathParameter() {

        APIGatewayProxyRequestEvent request =
                new APIGatewayProxyRequestEvent()
                        .withHttpMethod("GET")
                        .withPath("/customers/12345678909")
                        .withResource("/customers/{cpf}")
                        .withPathParameters(
                                Map.of(
                                        "cpf",
                                        "12345678909"
                                )
                        );

        AdminGetUserResponse userResponse =
                AdminGetUserResponse.builder()
                        .userAttributes(
                                AttributeType.builder()
                                        .name("custom:cpf")
                                        .value("12345678909")
                                        .build(),

                                AttributeType.builder()
                                        .name("email")
                                        .value("cliente@email.com")
                                        .build(),

                                AttributeType.builder()
                                        .name("name")
                                        .value("Cliente Teste")
                                        .build()
                        )
                        .build();

        when(cognitoClient.adminGetUser(
                any(AdminGetUserRequest.class)
        )).thenReturn(userResponse);

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(200, response.getStatusCode());

        verify(cognitoClient).adminGetUser(
                any(AdminGetUserRequest.class)
        );
    }

    @Test
    void shouldReturnBadRequestWhenGetCpfIsMissing() {

        APIGatewayProxyRequestEvent request =
                new APIGatewayProxyRequestEvent()
                        .withHttpMethod("GET")
                        .withPath("/customers/")
                        .withResource("/customers/{cpf}");

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(400, response.getStatusCode());

        assertEquals(
                "{\"error\":\"CPF obrigatorio\"}",
                response.getBody()
        );
    }

    @Test
    void shouldReturnBadRequestWhenGetCpfIsInvalid() {

        APIGatewayProxyRequestEvent request =
                request(
                        "GET",
                        "/customers/123",
                        "/customers/{cpf}",
                        null
                );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(400, response.getStatusCode());

        assertEquals(
                "{\"error\":\"CPF invalido\"}",
                response.getBody()
        );
    }

    @Test
    void shouldReturnNotFoundWhenUserDoesNotExist() {

        APIGatewayProxyRequestEvent request =
                request(
                        "GET",
                        "/customers/12345678909",
                        "/customers/{cpf}",
                        null
                );

        when(cognitoClient.adminGetUser(
                any(AdminGetUserRequest.class)
        )).thenThrow(
                UserNotFoundException.builder().build()
        );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(404, response.getStatusCode());

        assertEquals(
                "{\"error\":\"Usuario nao encontrado\"}",
                response.getBody()
        );
    }

    // AUTHENTICATION
    @Test
    void shouldAuthenticateSuccessfully() {

        String body = """
                {
                  "cpf": "123.456.789-09",
                  "password": "Senha@123"
                }
                """;

        APIGatewayProxyRequestEvent request =
                request(
                        "POST",
                        "/authentications",
                        "/authentications",
                        body
                );

        when(cognitoClient.adminGetUser(
                any(AdminGetUserRequest.class)
        )).thenReturn(
                AdminGetUserResponse.builder().build()
        );

        AuthenticationResultType authenticationResult =
                AuthenticationResultType.builder()
                        .tokenType("Bearer")
                        .accessToken("access-token")
                        .idToken("id-token")
                        .refreshToken("refresh-token")
                        .build();

        InitiateAuthResponse authResponse =
                InitiateAuthResponse.builder()
                        .authenticationResult(authenticationResult)
                        .build();

        when(cognitoClient.initiateAuth(
                any(InitiateAuthRequest.class)
        )).thenReturn(authResponse);

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(200, response.getStatusCode());

        assertNotNull(response.getBody());

        assertEquals(
                true,
                response.getBody().contains("Login realizado com sucesso")
        );

        assertEquals(
                true,
                response.getBody().contains("access-token")
        );

        verify(cognitoClient).initiateAuth(
                any(InitiateAuthRequest.class)
        );
    }

    @Test
    void shouldReturnBadRequestWhenAuthenticationBodyIsEmpty() {

        APIGatewayProxyRequestEvent request =
                request(
                        "POST",
                        "/authentications",
                        "/authentications",
                        ""
                );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(400, response.getStatusCode());

        assertEquals(
                "{\"error\":\"Body obrigatorio\"}",
                response.getBody()
        );
    }

    @Test
    void shouldReturnBadRequestWhenAuthenticationCpfIsMissing() {

        String body = """
                {
                  "password": "Senha@123"
                }
                """;

        APIGatewayProxyRequestEvent request =
                request(
                        "POST",
                        "/authentications",
                        "/authentications",
                        body
                );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(400, response.getStatusCode());

        assertEquals(
                "{\"error\":\"CPF obrigatorio\"}",
                response.getBody()
        );
    }

    @Test
    void shouldReturnBadRequestWhenAuthenticationPasswordIsMissing() {

        String body = """
                {
                  "cpf": "12345678909"
                }
                """;

        APIGatewayProxyRequestEvent request =
                request(
                        "POST",
                        "/authentications",
                        "/authentications",
                        body
                );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(400, response.getStatusCode());

        assertEquals(
                "{\"error\":\"Senha obrigatoria\"}",
                response.getBody()
        );
    }

    @Test
    void shouldReturnBadRequestWhenAuthenticationCpfIsInvalid() {

        String body = """
                {
                  "cpf": "123",
                  "password": "Senha@123"
                }
                """;

        APIGatewayProxyRequestEvent request =
                request(
                        "POST",
                        "/authentications",
                        "/authentications",
                        body
                );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(400, response.getStatusCode());

        assertEquals(
                "{\"error\":\"CPF invalido\"}",
                response.getBody()
        );
    }

    @Test
    void shouldReturnUnauthorizedWhenAuthenticationUserDoesNotExist() {

        String body = """
                {
                  "cpf": "12345678909",
                  "password": "Senha@123"
                }
                """;

        APIGatewayProxyRequestEvent request =
                request(
                        "POST",
                        "/authentications",
                        "/authentications",
                        body
                );

        when(cognitoClient.adminGetUser(
                any(AdminGetUserRequest.class)
        )).thenThrow(
                UserNotFoundException.builder().build()
        );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(401, response.getStatusCode());

        assertEquals(
                "{\"error\":\"Credenciais invalidas\"}",
                response.getBody()
        );
    }

    @Test
    void shouldReturnUnauthorizedWhenPasswordIsWrong() {

        String body = """
                {
                  "cpf": "12345678909",
                  "password": "SenhaErrada"
                }
                """;

        APIGatewayProxyRequestEvent request =
                request(
                        "POST",
                        "/authentications",
                        "/authentications",
                        body
                );

        when(cognitoClient.adminGetUser(
                any(AdminGetUserRequest.class)
        )).thenReturn(
                AdminGetUserResponse.builder().build()
        );

        when(cognitoClient.initiateAuth(
                any(InitiateAuthRequest.class)
        )).thenThrow(
                NotAuthorizedException.builder().build()
        );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(401, response.getStatusCode());

        assertEquals(
                "{\"error\":\"Credenciais invalidas\"}",
                response.getBody()
        );
    }

    // UNKNOWN ENDPOINT
    @Test
    void shouldReturnNotFoundWhenEndpointDoesNotExist() {

        APIGatewayProxyRequestEvent request =
                request(
                        "GET",
                        "/unknown",
                        "/unknown",
                        null
                );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(404, response.getStatusCode());

        assertEquals(
                "{\"error\":\"Endpoint não encontrado\"}",
                response.getBody()
        );
    }

    // SECRETS MANAGER
    @Test
    void shouldReturnInternalServerErrorWhenSecretsManagerFails() {

        when(secretsClient.getSecretValue(
                any(GetSecretValueRequest.class)
        )).thenThrow(
                new RuntimeException("Secrets Manager error")
        );

        APIGatewayProxyRequestEvent request =
                request(
                        "GET",
                        "/customers/12345678909",
                        "/customers/{cpf}",
                        null
                );

        APIGatewayProxyResponseEvent response =
                app.handleRequest(request, context);

        assertEquals(500, response.getStatusCode());

        assertEquals(
                "{\"error\":\"Secrets Manager error\"}",
                response.getBody()
        );
    }

    // HELPERS
    private APIGatewayProxyRequestEvent request(
            String method,
            String path,
            String resource,
            String body
    ) {

        APIGatewayProxyRequestEvent request =
                new APIGatewayProxyRequestEvent()
                        .withHttpMethod(method)
                        .withPath(path)
                        .withResource(resource)
                        .withBody(body);

        if (path != null
                && path.startsWith("/customers/")
                && path.length() > "/customers/".length()) {

            String cpf =
                    path.substring("/customers/".length());

            request.withPathParameters(
                    Map.of("cpf", cpf)
            );
        }

        return request;
    }
}