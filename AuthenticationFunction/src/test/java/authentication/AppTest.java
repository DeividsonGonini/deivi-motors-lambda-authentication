/*


package authentication;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppTest {

    private APIGatewayProxyRequestEvent request(String body) {
        return new APIGatewayProxyRequestEvent().withBody(body);
    }

    private Context context() {
        return mock(Context.class);
    }

    private String secretsJson() {
        return """
                {
                  "USER_POOL_ID":"pool",
                  "CLIENT_ID":"client"
                }
                """;
    }

    private SecretsManagerClient mockSecrets() {

        SecretsManagerClient secrets = mock(SecretsManagerClient.class);

        when(secrets.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder()
                        .secretString(secretsJson())
                        .build());

        return secrets;
    }

    // BODY OBRIGATÓRIO
    @Test
    void deveRetornar400QuandoBodyVazio() {

        SecretsManagerClient secrets = mockSecrets();
        CognitoIdentityProviderClient cognito = mock(CognitoIdentityProviderClient.class);

        App app = new App(cognito, secrets);

        var response = app.handleRequest(request(""), context());

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Body obrigatório"));
    }

    // EMAIL OBRIGATÓRIO
    @Test
    void deveRetornar400QuandoEmailAusente() {

        SecretsManagerClient secrets = mockSecrets();
        CognitoIdentityProviderClient cognito = mock(CognitoIdentityProviderClient.class);

        App app = new App(cognito, secrets);

        String body = """
                {"password":"123456"}
                """;

        var response = app.handleRequest(request(body), context());

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Email obrigatório"));
    }

    // SENHA OBRIGATÓRIA
    @Test
    void deveRetornar400QuandoSenhaAusente() {

        SecretsManagerClient secrets = mockSecrets();
        CognitoIdentityProviderClient cognito = mock(CognitoIdentityProviderClient.class);

        App app = new App(cognito, secrets);

        String body = """
                {"email":"teste@email.com"}
                """;

        var response = app.handleRequest(request(body), context());

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Senha obrigatória"));
    }

    // CADASTRO DE USUÁRIO
    @Test
    void deveCadastrarUsuarioQuandoNaoExiste() {

        System.setProperty("SECRET_NAME", "fake-secret");

        SecretsManagerClient secrets = mockSecrets();
        CognitoIdentityProviderClient cognito = mock(CognitoIdentityProviderClient.class);

        when(cognito.adminGetUser(any(AdminGetUserRequest.class)))
                .thenThrow(UserNotFoundException.builder().build());

        App app = new App(cognito, secrets);

        String body = """
                {
                  "cpf": "89463277587",
                  "nomeCompleto": "Jorge Marcopolo",
                  "email":"novo@email.com",
                  "password":"123456"
                }
                """;

        var response = app.handleRequest(request(body), context());

        assertEquals(201, response.getStatusCode());
        assertTrue(response.getBody().contains("Usuario cadastrado com sucesso"));

        verify(cognito).adminCreateUser(any(AdminCreateUserRequest.class));
        verify(cognito).adminSetUserPassword(any(AdminSetUserPasswordRequest.class));
    }

    // LOGIN COM SUCESSO
    @Test
    void deveAutenticarUsuarioExistente() {

        System.setProperty("SECRET_NAME", "fake-secret");

        SecretsManagerClient secrets = mockSecrets();
        CognitoIdentityProviderClient cognito = mock(CognitoIdentityProviderClient.class);

        AuthenticationResultType result =
                AuthenticationResultType.builder()
                        .accessToken("access")
                        .idToken("id")
                        .refreshToken("refresh")
                        .tokenType("Bearer")
                        .build();

        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenReturn(InitiateAuthResponse.builder()
                        .authenticationResult(result)
                        .build());

        App app = new App(cognito, secrets);

        String body = """
                {
                  "email":"teste@email.com",
                  "password":"123456"
                }
                """;

        var response = app.handleRequest(request(body), context());

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("Login realizado com sucesso"));
        assertTrue(response.getBody().contains("AccessToken"));
    }

    // CREDENCIAIS INVÁLIDAS
    @Test
    void deveRetornar401QuandoCredenciaisInvalidas() {

        System.setProperty("SECRET_NAME", "fake-secret");

        SecretsManagerClient secrets = mockSecrets();
        CognitoIdentityProviderClient cognito = mock(CognitoIdentityProviderClient.class);

        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenThrow(NotAuthorizedException.builder().build());

        App app = new App(cognito, secrets);

        String body = """
                {
                  "email":"teste@email.com",
                  "password":"errada"
                }
                """;

        var response = app.handleRequest(request(body), context());

        assertEquals(401, response.getStatusCode());
        assertTrue(response.getBody().contains("Credenciais invalidas"));
    }
}
 */